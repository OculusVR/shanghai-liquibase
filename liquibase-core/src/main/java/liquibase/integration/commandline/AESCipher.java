package liquibase.integration.commandline;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.SecureRandom;

/**
 * Created by x on 1/14/15.
 */
public class AESCipher {
    private SecureRandom rand = new SecureRandom();
    private static final Integer IV_LENGTH = 16;
    private static final String IV_SEPARATOR = "#";
    private Key key;

    public AESCipher(String keyStr) {
        this.key = stringToKey(keyStr);
    }

    private static final byte[] DEFAULT_IV = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public String encrypt(String message) {
        if (message == null) {
            throw new IllegalArgumentException("message is null");
        }

        if (key == null) {
            throw new IllegalStateException("key is null");
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = generateIV(IV_LENGTH);
            IvParameterSpec ivspec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivspec);
            return new String(Hex.encodeHex(iv)) + IV_SEPARATOR + new String(Hex.encodeHex(cipher.doFinal(message.getBytes("UTF-8"))));
        } catch (Exception e) {
            throw new RuntimeException("Encrypt exception: ", e);
        }
    }

    public String decrypt(String encryptedMessage) {
        if (encryptedMessage == null) {
            throw new IllegalArgumentException("encryptedMessage is null");
        }

        if (key == null) {
            throw new IllegalStateException("key is null");
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            String[] info = encryptedMessage.split(IV_SEPARATOR);
            if (info.length != 2) {
                info = new String[2];
                info[0] = new String(Hex.encodeHex(DEFAULT_IV));
                info[1] = encryptedMessage;
            }
            IvParameterSpec ivspec = new IvParameterSpec(Hex.decodeHex(info[0].toCharArray()));
            cipher.init(Cipher.DECRYPT_MODE, key, ivspec);
            return new String(cipher.doFinal(Hex.decodeHex(info[1].toCharArray())), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Decrypt exception : ", e);
        }
    }

    public Key stringToKey(String keyStr) {
        if (keyStr == null || "".equals(keyStr)) {
            return null;
        }
        try {
            byte[] bytes = Hex.decodeHex(keyStr.toCharArray());
            return new SecretKeySpec(bytes, 0, bytes.length, "AES");
        } catch (DecoderException decodeException) {
            throw new RuntimeException("Key String to AES key: ", decodeException);
        }
    }

    private byte[] generateIV(int length) {
        byte[] bytes = new byte[length];
        rand.nextBytes(bytes);

        return bytes;
    }
}
