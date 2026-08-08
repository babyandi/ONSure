package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.LocalKeyRegistry;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnterpriseIdentityVerifierTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verifiesAValidlySignedAssertionAndProducesTheBoundIdentity() throws Exception {
        Fixture fixture = trustedKey();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Path assertionFile = signedAssertion(fixture, now, now.plus(1, ChronoUnit.HOURS), List.of("OPERATOR", "APPROVER"));

        AuthenticatedWorkflowIdentity identity = new EnterpriseIdentityVerifier()
                .verify(assertionFile, fixture.registryFile(), now);

        assertEquals("acme-corp", identity.organizationId());
        assertEquals("tenant-001", identity.tenantId());
        assertEquals("workspace-001", identity.workspaceId());
        assertEquals("actor-jane", identity.actorId());
        assertEquals("US", identity.dataRegion());
        assertEquals(AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY,
                identity.authenticationMethod());
        assertTrue(identity.roles().contains(AuthenticatedWorkflowIdentity.Role.OPERATOR));
        assertTrue(identity.roles().contains(AuthenticatedWorkflowIdentity.Role.APPROVER));
    }

    @Test
    void expiredAssertionIsRejected() throws Exception {
        Fixture fixture = trustedKey();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Path assertionFile = signedAssertion(
                fixture, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), List.of("VIEWER"));

        assertThrows(IllegalStateException.class,
                () -> new EnterpriseIdentityVerifier().verify(assertionFile, fixture.registryFile(), now));
    }

    @Test
    void excessivelyLongValidityWindowIsRejected() throws Exception {
        Fixture fixture = trustedKey();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Path assertionFile = signedAssertion(fixture, now, now.plus(30, ChronoUnit.DAYS), List.of("VIEWER"));

        assertThrows(IllegalStateException.class,
                () -> new EnterpriseIdentityVerifier().verify(assertionFile, fixture.registryFile(), now));
    }

    @Test
    void tamperedPayloadFailsSignatureVerification() throws Exception {
        Fixture fixture = trustedKey();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Path assertionFile = signedAssertion(fixture, now, now.plus(1, ChronoUnit.HOURS), List.of("VIEWER"));

        Map<String, Object> tampered = mapper.readValue(assertionFile.toFile(), Map.class);
        tampered.put("roles", List.of("ADMIN"));
        mapper.writeValue(assertionFile.toFile(), tampered);

        assertThrows(IllegalStateException.class,
                () -> new EnterpriseIdentityVerifier().verify(assertionFile, fixture.registryFile(), now));
    }

    @Test
    void untrustedKeyIdIsRejected() throws Exception {
        Fixture fixture = trustedKey();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Path assertionFile = signedAssertion(fixture, now, now.plus(1, ChronoUnit.HOURS), List.of("VIEWER"));

        Map<String, Object> value = mapper.readValue(assertionFile.toFile(), Map.class);
        value.put("key_id", "never-registered-key");
        mapper.writeValue(assertionFile.toFile(), value);

        assertThrows(IllegalStateException.class,
                () -> new EnterpriseIdentityVerifier().verify(assertionFile, fixture.registryFile(), now));
    }

    @Test
    void invalidRoleNameIsRejected() throws Exception {
        Fixture fixture = trustedKey();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Path assertionFile = signedAssertion(fixture, now, now.plus(1, ChronoUnit.HOURS), List.of("SUPERUSER"));

        assertThrows(IllegalStateException.class,
                () -> new EnterpriseIdentityVerifier().verify(assertionFile, fixture.registryFile(), now));
    }

    private Path signedAssertion(Fixture fixture, Instant issuedAt, Instant expiresAt, List<String> roles)
            throws Exception {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", EnterpriseIdentityVerifier.CONTRACT);
        value.put("organization_id", "acme-corp");
        value.put("tenant_id", "tenant-001");
        value.put("workspace_id", "workspace-001");
        value.put("actor_id", "actor-jane");
        value.put("roles", roles);
        value.put("data_region", "US");
        value.put("issued_at", issuedAt.toString());
        value.put("expires_at", expiresAt.toString());
        value.put("key_id", fixture.keyId());
        value.put("signature", LocalReceiptCrypto.sign(value, fixture.keyPair().getPrivate()));
        Path assertionFile = temp.resolve("assertion-" + System.nanoTime() + ".json");
        mapper.writeValue(assertionFile.toFile(), value);
        return assertionFile;
    }

    private Fixture trustedKey() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        KeyPair pair = LocalReceiptCrypto.generate();
        Path authority = temp.resolve("authority-" + System.nanoTime());
        Path publicKeyFile = authority.resolve("identity-public.key");
        LocalReceiptCrypto.writePublicKey(publicKeyFile, pair.getPublic());
        Path registryFile = authority.resolve("trusted-key-registry.json");
        String keyId = "enterprise-idp-key-001";
        assertEquals(Decision.PASS, new LocalKeyRegistry(registryFile).register(
                new LocalKeyRegistry.KeyRecord(
                        keyId, EnterpriseIdentityVerifier.AUTHORITY, publicKeyFile.toString(),
                        now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());
        return new Fixture(pair, keyId, registryFile);
    }

    private record Fixture(KeyPair keyPair, String keyId, Path registryFile) {}
}
