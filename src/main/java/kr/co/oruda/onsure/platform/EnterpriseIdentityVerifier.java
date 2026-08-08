package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.LocalKeyRegistry;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import kr.co.oruda.onsure.assurance.ValidationResult;
import kr.co.oruda.onsure.platform.AuthenticatedWorkflowIdentity.AuthenticationMethod;
import kr.co.oruda.onsure.platform.AuthenticatedWorkflowIdentity.Role;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verifies a signed enterprise identity assertion and produces an
 * {@link AuthenticatedWorkflowIdentity} bound to {@link AuthenticationMethod#SIGNED_ENTERPRISE_IDENTITY}
 * (TENANT-IDENTITY: SIGNED_ENTERPRISE_IDENTITY_VERIFIER_NOT_IMPLEMENTED). The assertion is a
 * JSON document Ed25519-signed by a trusted enterprise IdP-bridge key (the same
 * LocalKeyRegistry/LocalReceiptCrypto primitive already used for approval receipts), not a
 * SAML/OIDC token parsed and verified end to end -- issuing that assertion from an actual SAML 2.0
 * or OIDC provider (docs/master/04_ARCHITECTURE_DATA_API_OLICENSE.md Sec.2) is a separate,
 * still-open integration.
 */
public final class EnterpriseIdentityVerifier {
    public static final String CONTRACT = "ONSURE_ENTERPRISE_IDENTITY_ASSERTION_V1";
    public static final String AUTHORITY = "ONSURE_ENTERPRISE_IDENTITY_SIGNING_KEY";
    private static final Duration MAX_ASSERTION_AGE = Duration.ofHours(12);

    private final ObjectMapper mapper = new ObjectMapper();

    public AuthenticatedWorkflowIdentity verify(Path assertionFile, Path trustedKeyRegistryFile, Instant now)
            throws Exception {
        Map<String, Object> value = readObject(assertionFile);
        if (!CONTRACT.equals(value.get("contract"))) {
            throw new IllegalArgumentException("ENTERPRISE_IDENTITY_CONTRACT_INVALID");
        }

        Instant issuedAt = parseInstant(value, "issued_at", "ENTERPRISE_IDENTITY_ISSUED_AT_INVALID");
        Instant expiresAt = parseInstant(value, "expires_at", "ENTERPRISE_IDENTITY_EXPIRES_AT_INVALID");
        Instant effectiveNow = now == null ? Instant.now() : now;
        if (!expiresAt.isAfter(issuedAt) || expiresAt.isAfter(issuedAt.plus(MAX_ASSERTION_AGE))) {
            throw new IllegalStateException("ENTERPRISE_IDENTITY_EXPIRY_WINDOW_INVALID");
        }
        if (issuedAt.isAfter(effectiveNow.plusSeconds(300))) {
            throw new IllegalStateException("ENTERPRISE_IDENTITY_ISSUED_IN_FUTURE");
        }
        if (!effectiveNow.isBefore(expiresAt)) {
            throw new IllegalStateException("ENTERPRISE_IDENTITY_EXPIRED");
        }

        String keyId = requireText(value, "key_id", "ENTERPRISE_IDENTITY_KEY_ID_MISSING");
        LocalKeyRegistry registry = new LocalKeyRegistry(trustedKeyRegistryFile);
        ValidationResult keyValidation = registry.validate(keyId, AUTHORITY, issuedAt);
        if (keyValidation.decision() != Decision.PASS) {
            throw new IllegalStateException("ENTERPRISE_IDENTITY_KEY_INVALID:" + keyValidation.violations());
        }
        LocalKeyRegistry.KeyRecord record = registry.load().stream()
                .filter(item -> keyId.equals(item.keyId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ENTERPRISE_IDENTITY_TRUSTED_KEY_MISSING"));
        Path publicKeyFile = Path.of(record.publicKeyFile()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(publicKeyFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(publicKeyFile)) {
            throw new IllegalStateException("ENTERPRISE_IDENTITY_PUBLIC_KEY_INVALID");
        }
        PublicKey publicKey = LocalReceiptCrypto.readPublicKey(publicKeyFile);
        if (!LocalReceiptCrypto.verify(value, publicKey)) {
            throw new IllegalStateException("ENTERPRISE_IDENTITY_SIGNATURE_INVALID");
        }

        return new AuthenticatedWorkflowIdentity(
                requireText(value, "organization_id", "ENTERPRISE_IDENTITY_ORGANIZATION_ID_MISSING"),
                requireText(value, "tenant_id", "ENTERPRISE_IDENTITY_TENANT_ID_MISSING"),
                requireText(value, "workspace_id", "ENTERPRISE_IDENTITY_WORKSPACE_ID_MISSING"),
                requireText(value, "actor_id", "ENTERPRISE_IDENTITY_ACTOR_ID_MISSING"),
                parseRoles(value),
                requireText(value, "data_region", "ENTERPRISE_IDENTITY_DATA_REGION_MISSING"),
                AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
    }

    private static Set<Role> parseRoles(Map<String, Object> value) {
        Object roles = value.get("roles");
        if (!(roles instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("ENTERPRISE_IDENTITY_ROLES_MISSING");
        }
        Set<Role> result = new LinkedHashSet<>();
        for (Object entry : list) {
            try {
                result.add(Role.valueOf(String.valueOf(entry)));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("ENTERPRISE_IDENTITY_ROLE_INVALID:" + entry);
            }
        }
        return result;
    }

    private static Instant parseInstant(Map<String, Object> value, String field, String code) {
        try {
            return Instant.parse(String.valueOf(value.get(field)));
        } catch (Exception invalid) {
            throw new IllegalStateException(code);
        }
    }

    private static String requireText(Map<String, Object> value, String field, String code) {
        Object text = value.get(field);
        if (!(text instanceof String string) || string.isBlank()) throw new IllegalStateException(code);
        return string;
    }

    private Map<String, Object> readObject(Path file) throws Exception {
        return objectMap(mapper.readTree(file.toFile()));
    }

    private static Map<String, Object> objectMap(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("JSON_OBJECT_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), jsonValue(field.getValue()));
        }
        return result;
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) return objectMap(node);
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.canConvertToInt() ? node.intValue() : node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        throw new IllegalArgumentException("JSON_VALUE_TYPE_UNSUPPORTED:" + node.getNodeType());
    }
}
