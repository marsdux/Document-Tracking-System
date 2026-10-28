package doctrack.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * Password hashing using salted PBKDF2-HMAC-SHA256, built entirely on
 * javax.crypto / java.security (JDK standard library, no external
 * dependency needed).
 *
 * SECURITY NOTE: earlier versions of this class used a single, unsalted
 * SHA-256 digest, which is vulnerable to rainbow-table / precomputation
 * attacks and gives every user with the same password the same hash.
 * {@link #verifyLegacy(char[], String)} exists ONLY so accounts created
 * under that old scheme can be authenticated one last time and
 * transparently upgraded (see UserDAO.authenticate) -- it must not be
 * used for new passwords.
 */
public final class PasswordUtil {

    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() { }

    public static final class Hashed {
        public final String hashHex;
        public final String saltHex;
        Hashed(String hashHex, String saltHex) { this.hashHex = hashHex; this.saltHex = saltHex; }
    }

    /** Hashes a new password with a freshly generated random salt. */
    public static Hashed hashNew(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        String hash = pbkdf2(password, salt);
        return new Hashed(hash, toHex(salt));
    }

    /** Verifies a password against a stored salted PBKDF2 hash. */
    public static boolean verify(char[] password, String storedHashHex, String saltHex) {
        byte[] salt = fromHex(saltHex);
        String attempt = pbkdf2(password, salt);
        return constantTimeEquals(attempt, storedHashHex);
    }

    /**
     * Verifies against the OLD unsalted SHA-256 scheme. Only used as a
     * one-time upgrade path for accounts created before salting was added
     * (see UserDAO.authenticate) -- never called for newly created
     * passwords.
     */
    public static boolean verifyLegacy(char[] password, String storedHashHex) {
        byte[] bytes = charsToBytes(password);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            return constantTimeEquals(toHex(hashBytes), storedHashHex);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String pbkdf2(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return toHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2WithHmacSHA256 not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static byte[] charsToBytes(char[] chars) {
        java.nio.CharBuffer charBuffer = java.nio.CharBuffer.wrap(chars);
        java.nio.ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }
}
