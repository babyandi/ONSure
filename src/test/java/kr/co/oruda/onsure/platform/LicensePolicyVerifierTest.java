package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import kr.co.oruda.onsure.platform.LicensePolicyVerifier.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicensePolicyVerifierTest {
    @TempDir Path temp;

    @Test
    void currentRepositoryDependenciesSatisfyTheLicensePolicy() throws Exception {
        VerificationResult result = LicensePolicyVerifier.verify(
                Path.of("pom.xml"),
                Path.of("contracts/approved-dependency-manifest.v1.json"),
                Path.of("contracts/dependency-license-policy.v1.json"));
        assertTrue(result.passed(), "violations: " + result.violations());
    }

    @Test
    void forbiddenLicenseFailsClosed() throws Exception {
        Path pom = writePom("""
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>copyleft-lib</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = writeManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"copyleft-lib","version":"1.0.0","license":"GPL-3.0"}
                ]}
                """);
        Path policy = writePolicy("""
                {"contract":"ONSURE_DEPENDENCY_LICENSE_POLICY_V1",
                 "decisions":[{"spdx_id":"GPL-3.0","decision":"FORBIDDEN","reason":"copyleft"}],
                 "default_decision_for_unlisted_license":"FORBIDDEN"}
                """);

        VerificationResult result = LicensePolicyVerifier.verify(pom, manifest, policy);
        assertFalse(result.passed());
        assertEquals(1, result.violations().size());
        assertEquals("LICENSE_FORBIDDEN", result.violations().get(0).code());
        assertEquals("org.example:copyleft-lib", result.violations().get(0).coordinate());
    }

    @Test
    void unlistedLicenseFallsBackToDefaultDecisionAndFailsClosed() throws Exception {
        Path pom = writePom("""
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>mystery-lib</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = writeManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"mystery-lib","version":"1.0.0","license":"Some-Unlisted-License"}
                ]}
                """);
        Path policy = writePolicy("""
                {"contract":"ONSURE_DEPENDENCY_LICENSE_POLICY_V1",
                 "decisions":[{"spdx_id":"Apache-2.0","decision":"ALLOWED","reason":"permissive"}],
                 "default_decision_for_unlisted_license":"FORBIDDEN"}
                """);

        VerificationResult result = LicensePolicyVerifier.verify(pom, manifest, policy);
        assertFalse(result.passed());
        assertEquals("LICENSE_FORBIDDEN", result.violations().get(0).code());
    }

    @Test
    void allowedLicensePasses() throws Exception {
        Path pom = writePom("""
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>permissive-lib</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = writeManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"permissive-lib","version":"1.0.0","license":"Apache-2.0"}
                ]}
                """);
        Path policy = writePolicy("""
                {"contract":"ONSURE_DEPENDENCY_LICENSE_POLICY_V1",
                 "decisions":[{"spdx_id":"Apache-2.0","decision":"ALLOWED","reason":"permissive"}],
                 "default_decision_for_unlisted_license":"FORBIDDEN"}
                """);

        VerificationResult result = LicensePolicyVerifier.verify(pom, manifest, policy);
        assertTrue(result.passed());
    }

    private Path writePom(String xml) throws Exception {
        Path pom = temp.resolve("pom-" + System.nanoTime() + ".xml");
        Files.writeString(pom, xml);
        return pom;
    }

    private Path writeManifest(String json) throws Exception {
        Path manifest = temp.resolve("manifest-" + System.nanoTime() + ".json");
        Files.writeString(manifest, json);
        return manifest;
    }

    private Path writePolicy(String json) throws Exception {
        Path policy = temp.resolve("policy-" + System.nanoTime() + ".json");
        Files.writeString(policy, json);
        return policy;
    }
}
