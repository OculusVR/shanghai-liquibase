package liquibase.integration.commandline;

import liquibase.exception.CommandLineParsingException;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by x on 1/14/15.
 */
public class JunboMain {
    public static void main(String[] args) throws ConfigurationException, IOException, CommandLineParsingException, InterruptedException {
        if (args.length > 3 || args.length < 2) {
            printUsage();
            return;
        }

        String env = args[0];
        File confDir = new File("conf/" + env);
        if (!confDir.exists() || !confDir.isDirectory()) {
            throw new RuntimeException("env " + env + " conf not found");
        }

        ExecutorService threadPool = Executors.newFixedThreadPool(10);
        AESCipher aesCipher = new AESCipher(args[1]);
        Main.runByJunbo = true;
        boolean notConfigured = true;

        for (File confFile : confDir.listFiles()) {
            Configuration conf = new PropertiesConfiguration(confFile);
            String changeLogFileName = confFile.getName().split("\\.")[0] + ".xml";
            String jdbcDriver = conf.getString("jdbc_driver");
            String username = conf.getString("login_username");
            String password;
            if (conf.containsKey("login_password")) {
                password = conf.getString("login_password");
            } else if (conf.containsKey("login_password.encrypted")) {
                password = aesCipher.decrypt(conf.getString("login_password.encrypted"));
            } else {
                throw new RuntimeException("login password not found in " + confFile.getName());
            }

            Iterator<String> iterator = conf.getKeys();
            while (iterator.hasNext()) {
                String jdbcUrlKey = iterator.next();
                if (!jdbcUrlKey.startsWith("jdbc_url")) {
                    continue;
                }
                String[] jdbcUrlAndSchema = conf.getString(jdbcUrlKey).split(";");

                final List<String> liquibaseArgs = new ArrayList<String>(8);
                liquibaseArgs.add("--driver=" + jdbcDriver);
                liquibaseArgs.add("--changeLogFile=" + "changelogs/silkcloud/" + changeLogFileName);
                liquibaseArgs.add("--username=" + username);
                liquibaseArgs.add("--password=" + password);
                liquibaseArgs.add("--url=" + jdbcUrlAndSchema[0]);
                liquibaseArgs.add("--defaultSchemaName=" + jdbcUrlAndSchema[1]);
                if (args.length == 3 && args[2].equalsIgnoreCase("--debug")) {
                    liquibaseArgs.add("--logLevel=debug");
                }
                liquibaseArgs.add("update");

                // configure first to avoid thread-safe problems
                if (notConfigured) {
                    Main.main(liquibaseArgs.toArray(new String[0]));
                    notConfigured = false;
                    Main.isConfigured = true;
                } else {
                    threadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Main.main(liquibaseArgs.toArray(new String[0]));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        }

        threadPool.shutdown();
        threadPool.awaitTermination(1, TimeUnit.HOURS);
        System.exit(0);
    }

    private static void printUsage() {
        System.out.println("<env> <key> [--debug]");
    }
}
