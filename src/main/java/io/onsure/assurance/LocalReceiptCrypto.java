package io.onsure.assurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

public final class LocalReceiptCrypto {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private LocalReceiptCrypto() {}

    public static KeyPair generate() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    public static void writePrivateKey(Path path, PrivateKey key) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, Base64.getEncoder().encodeToString(key.getEncoded()), StandardCharsets.US_ASCII);
    }

    public static void writePublicKey(Path path, PublicKey key) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, Base64.getEncoder().encodeToString(key.getEncoded()), StandardCharsets.US_ASCII);
    }

    public static PrivateKey readPrivateKey(Path path) throws Exception {
        byte[] encoded = Base64.getDecoder().decode(Files.readString(path).trim());
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static PublicKey readPublicKey(Path path) throws Exception {
        byte[] encoded = Base64.getDecoder().decode(Files.readString(path).trim());
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }

    public static byte[] canonicalPayload(Map<String, Object> receipt) throws Exception {
        TreeMap<String, Object> copy = new TreeMap<>(receipt);
        copy.remove("signature");
        return MAPPER.writeValueAsBytes(copy);
    }

    public static String sign(Map<String, Object> receipt, PrivateKey key) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(canonicalPayload(receipt));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public static boolean verify(Map<String, Object> receipt, PublicKey key) throws Exception {
        Object value = receipt.get("signature");
        if (!(value instanceof String text) || text.isBlank()) return false;
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(key);
        signature.update(canonicalPayload(receipt));
        return signature.verify(Base64.getDecoder().decode(text));
    }
}