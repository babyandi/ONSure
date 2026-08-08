package kr.co.oruda.onsure.platform;

/** Package-local cryptographic provider bridge for services that must remain Java 17 compatible. */
final class MessageDigest {
    private MessageDigest() {}

    static java.security.MessageDigest getInstance(String algorithm)
            throws java.security.NoSuchAlgorithmException {
        return java.security.MessageDigest.getInstance(algorithm);
    }
}
