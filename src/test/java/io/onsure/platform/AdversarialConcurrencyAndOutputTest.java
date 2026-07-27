package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.learning.OfficialLearningLedger;
import io.onsure.learning.OfficialLearningLedger.LearningCandidate;
import io.onsure.platform.ProductCatalog.Project;
import io.onsure.platform.ProductCatalog.Workspace;
import io.onsure.platform.TargetAdapter.FixtureDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdversarialConcurrencyAndOutputTest {
    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);

    @TempDir Path temp;

    @Test
    void fixtureOutputFloodIsRejectedBeforeEvidenceCanBeAccepted() throws Exception {
        Path target = temp.resolve("output-flood-target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("flood.sh"), """
                #!/usr/bin/env bash
                set -euo pipefail
                head -c 70000 /dev/zero | tr '\\0' 'A'
                """);
        FixtureDefinition fixture = new FixtureDefinition(
                "output-flood", "", "SAFE", "", "EQUALS",
                List.of("bash", "flood.sh"), 10, Map.of());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new FixtureHarness("ONSURE_OUTPUT_FLOOD_TEST").execute(fixture, target));
        assertTrue(failure.getMessage().contains("fixture output limit exceeded"));
    }

    @Test
    void concurrentCatalogUpdatesDoNotLoseProjectsOrRevision() throws Exception {
        Path catalogRoot = temp.resolve("catalog");
        new ProductCatalog(catalogRoot).registerWorkspace(
                new Workspace("workspace-1", "Concurrent Workspace", Instant.now()));
        int writers = 12;
        runConcurrently(writers, index -> new ProductCatalog(catalogRoot).registerProject(
                new Project("project-" + index, "workspace-1", "Project " + index, Instant.now())));

        ProductCatalog catalog = new ProductCatalog(catalogRoot);
        assertEquals(writers + 1L, catalog.revision());
        JsonNode projects = new ObjectMapper().findAndRegisterModules()
                .readTree(catalogRoot.resolve("projects.json").toFile());
        assertEquals(writers, projects.size());
        HashSet<String> ids = new HashSet<>();
        projects.forEach(project -> ids.add(project.path("projectId").asText()));
        assertEquals(writers, ids.size());
    }

    @Test
    void concurrentLearningLedgerAppendsPreserveHashChainAndEveryCandidate() throws Exception {
        Path ledgerFile = temp.resolve("concurrent-learning-ledger.jsonl");
        int writers = 12;
        runConcurrently(writers, index -> new OfficialLearningLedger(ledgerFile).registerCandidate(
                new LearningCandidate(
                        "candidate-" + index,
                        "VALIDATOR_RULE_CANDIDATE",
                        A,
                        B,
                        "dataset-v1",
                        true,
                        "learner-" + index)));

        OfficialLearningLedger ledger = new OfficialLearningLedger(ledgerFile);
        assertTrue(ledger.verifyChain().valid(), ledger.verifyChain().violations().toString());
        assertEquals(writers, Files.readAllLines(ledgerFile).size());
    }

    private void runConcurrently(int count, ThrowingIndexedOperation operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < count; index++) {
                int value = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    operation.run(value);
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingIndexedOperation {
        void run(int index) throws Exception;
    }
}
