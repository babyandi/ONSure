package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ONSureCliTest {
    @TempDir Path temp;

    @Test
    void failReportProducesNonzeroProcessContract() throws Exception {
        Path target = temp.resolve("target");
        Files.createDirectories(target);
        Files.writeString(target.resolve("fixture.sh"),
                "#!/usr/bin/env bash\nprintf 'UNSAFE\\n'\n");
        Files.writeString(target.resolve("onsure-target.json"), """
                {
                  "contract":"ONSURE_TARGET_MANIFEST_V1",
                  "target_id":"cli-fail",
                  "target_type":"GENERAL_SOFTWARE",
                  "self_reported_final_decision":false,
                  "capabilities":[],
                  "fixtures":[{
                    "id":"known-failure",
                    "input":"x",
                    "expected":"SAFE",
                    "oracle":"EQUALS",
                    "command":["bash","fixture.sh"]
                  }]
                }
                """);
        String sourceReference = SourceReferenceBinding.treeReference(target);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = ONSureCli.run(new String[] {
                "validate", target.toString(), "cli-fail", "cli-fail",
                "GENERAL_SOFTWARE", GenericManifestTargetAdapter.ID, sourceReference,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_E2E", temp.resolve("runs").toString()
        }, new PrintStream(stdout), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(2, exit);
        assertTrue(stdout.toString().contains("\"decision\" : \"FAIL\""));
    }
}
