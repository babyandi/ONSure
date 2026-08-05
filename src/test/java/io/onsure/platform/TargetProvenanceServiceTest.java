package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetProvenanceServiceTest {
    @TempDir Path temp;

    @Test
    void distinguishesRealRepositoryScopeFromTrackedFixtureAndBindsManifest() throws Exception {
        Path repository = repository();
        Path app = repository.resolve("apps/customer-app");
        Path fixture = repository.resolve("fixtures/universal/java");
        TargetProvenanceService service = new TargetProvenanceService(Files.createDirectory(temp.resolve("workspace")));

        Map<String, Object> real = service.capture(app, Hashing.tree(app), "AUTO");
        Map<String, Object> fixtureOnly = service.capture(fixture, Hashing.tree(fixture), "AUTO");

        assertEquals("REAL_REPOSITORY", real.get("target_classification"));
        assertEquals("apps/customer-app", real.get("repository_scope"));
        assertEquals("GIT_REMOTE_ORIGIN_HASH", real.get("repository_identity_basis"));
        assertTrue((Boolean) real.get("worktree_clean"));
        assertTrue((Boolean) real.get("real_target_universality_eligible"));
        assertEquals("NOT_RUN", real.get("runtime_validation"));
        assertFalse((Boolean) real.get("final_claim_allowed"));
        assertNotEquals(real.get("snapshot_source_sha256"), real.get("snapshot_manifest_sha256"));

        assertEquals("FIXTURE", fixtureOnly.get("target_classification"));
        assertEquals("GIT_SCOPE_FIXTURE_SEGMENT", fixtureOnly.get("classification_basis"));
        assertTrue((Boolean) fixtureOnly.get("fixture_only"));
        assertFalse((Boolean) fixtureOnly.get("real_target_universality_eligible"));
        assertEquals("PROHIBITED_FIXTURE_ONLY", fixtureOnly.get("universality_claim_state"));
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(fixture, Hashing.tree(fixture), "REAL_REPOSITORY"));
    }

    @Test
    void persistsDigestBoundContractAndRejectsSourceOrReceiptDrift() throws Exception {
        Path repository = repository();
        Path app = repository.resolve("apps/customer-app");
        TargetProvenanceService service = new TargetProvenanceService(Files.createDirectory(temp.resolve("workspace")));
        String registrationDigest = Hashing.tree(app);
        Map<String, Object> captured = service.capture(app, registrationDigest, "REAL_REPOSITORY");
        service.persist("customer", captured);

        assertEquals(captured, service.load("customer"));
        assertEquals(captured, service.requireCurrent("customer", app, registrationDigest));

        Map<String, Object> tampered = new LinkedHashMap<>(captured);
        tampered.put("snapshot_file_count", 999);
        assertThrows(IllegalArgumentException.class, () -> TargetProvenanceService.validate(tampered));

        Files.writeString(app.resolve("app.txt"), "changed\n");
        IllegalArgumentException drift = assertThrows(IllegalArgumentException.class,
                () -> service.requireCurrent("customer", app, registrationDigest));
        assertTrue(drift.getMessage().startsWith("TARGET_PROVENANCE_BINDING_DRIFT:"));
    }

    @Test
    void unversionedDirectoryCannotBecomeRealRepositoryByDeclaration() throws Exception {
        Path source = Files.createDirectory(temp.resolve("unversioned"));
        Files.writeString(source.resolve("main.py"), "print('safe')\n");
        TargetProvenanceService service = new TargetProvenanceService(Files.createDirectory(temp.resolve("workspace")));

        Map<String, Object> automatic = service.capture(source, Hashing.tree(source), "AUTO");
        assertEquals("UNKNOWN", automatic.get("target_classification"));
        assertFalse((Boolean) automatic.get("real_target_universality_eligible"));
        assertThrows(IllegalArgumentException.class,
                () -> service.capture(source, Hashing.tree(source), "REAL_REPOSITORY"));
    }

    @Test
    void usesExactExecutionSnapshotSetWithTrackedBuildAndIgnoredFiles() throws Exception {
        Path repository = repository();
        Path app = repository.resolve("apps/customer-app");
        Files.createDirectories(app.resolve("build"));
        Files.createDirectories(app.resolve("dist"));
        Files.writeString(app.resolve("build/tracked.bin"), "tracked but excluded\n");
        Files.writeString(app.resolve("dist/tracked.js"), "tracked but excluded\n");
        Files.writeString(app.resolve(".gitignore"), "ignored-runtime.txt\n");
        git(repository, "add", ".");
        git(repository, "commit", "-q", "-m", "tracked generated files");
        Files.writeString(app.resolve("ignored-runtime.txt"), "ignored but snapshotted\n");
        TargetProvenanceService service = new TargetProvenanceService(
                Files.createDirectory(temp.resolve("snapshot-workspace")));

        Map<String, Object> provenance = service.capture(app, Hashing.sha256("registration"), "AUTO");
        var snapshot = ValidationSourceSnapshot.create(app, temp.resolve("execution-snapshot"));

        assertEquals(snapshot.sourceDigestBefore(), provenance.get("snapshot_source_sha256"));
        assertEquals(snapshot.fileCount(), ((Number) provenance.get("snapshot_file_count")).intValue());
        assertTrue(Files.exists(snapshot.snapshotRoot().resolve("ignored-runtime.txt")));
        assertFalse(Files.exists(snapshot.snapshotRoot().resolve("build/tracked.bin")));
        assertFalse(Files.exists(snapshot.snapshotRoot().resolve("dist/tracked.js")));
        TargetProvenanceService.verifyRunBinding(provenance, app,
                snapshot.sourceDigestBefore(), snapshot.snapshotDigest());
    }

    private Path repository() throws Exception {
        Path repository = Files.createDirectory(temp.resolve("repository"));
        Files.createDirectories(repository.resolve("apps/customer-app"));
        Files.createDirectories(repository.resolve("fixtures/universal/java"));
        Files.writeString(repository.resolve("apps/customer-app/app.txt"), "actual application\n");
        Files.writeString(repository.resolve("fixtures/universal/java/pom.xml"), "<project/>\n");
        git(repository, "init", "-q");
        git(repository, "config", "user.email", "test@onsure.invalid");
        git(repository, "config", "user.name", "ONSure Test");
        git(repository, "add", ".");
        git(repository, "commit", "-q", "-m", "fixture");
        git(repository, "remote", "add", "origin", "https://example.invalid/customer/repository.git");
        return repository;
    }

    private static void git(Path root, String... arguments) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("git failed: " + output);
    }
}
