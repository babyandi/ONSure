package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ContractConsistencyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void receiptSchemaExactlyMatchesJavaRecord() throws Exception {
        JsonNode schema = schema("receipt-envelope.v1.schema.json");
        Set<String> recordFields = Arrays.stream(ReceiptEnvelope.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertExactFields(schema, recordFields);
    }

    @Test
    void localAgentSchemaUsesRuntimeContractAndNonfinalAuthorityFields() throws Exception {
        JsonNode schema = schema("local-agent-receipt.v1.schema.json");
        Set<String> fields = Set.of(
                "contract", "authority", "authority_class", "assurance_class",
                "independent_authority", "run_id", "assurance_run_id", "run_started_at",
                "input_digest", "decision", "created_at", "execution_mode", "role_policy",
                "evidence_scope", "key_id", "signature_algorithm", "signature");
        assertExactFields(schema, fields);
        assertEquals(LocalAgentMain.CONTRACT,
                schema.path("properties").path("contract").path("const").asText());
        assertEquals("INTERNAL_SELF_VALIDATION",
                schema.path("properties").path("authority_class").path("const").asText());
        assertEquals("SELF_VALIDATION_NONFINAL",
                schema.path("properties").path("assurance_class").path("const").asText());
        assertFalse(schema.path("properties").path("independent_authority").path("const").asBoolean(true));
    }

    @Test
    void runContextSourceLockAndNonfinalReceiptContractsMatchRuntimeConstants() throws Exception {
        JsonNode runContext = schema("local-run-context.v1.schema.json");
        assertExactFields(runContext, Set.of("contract", "run_id", "started_at"));
        assertEquals(LocalRunContext.CONTRACT,
                runContext.path("properties").path("contract").path("const").asText());

        JsonNode sourceLock = schema("source-lock.v1.schema.json");
        assertExactFields(sourceLock, Set.of(
                "digest_contract", "commit_sha", "tree_sha256", "policy_sha256", "worktree_clean"));
        assertEquals(LocalSourceLockVerifier.DIGEST_CONTRACT,
                sourceLock.path("properties").path("digest_contract").path("const").asText());

        JsonNode finalReceipt = schema("local-final-receipt.v1.schema.json");
        assertExactFields(finalReceipt, Set.of(
                "contract", "decision", "assurance_class", "independent_otester",
                "independent_oaudit", "final_lock_allowed", "production_go", "commercial_go",
                "execution_mode", "assurance_run_id", "run_started_at", "verified_at",
                "receipt_dir", "source_commit_sha", "source_tree_sha256", "policy_sha256",
                "fixture_contract_snapshot", "fixture_contract_snapshot_sha256",
                "security_findings_snapshot", "security_findings_snapshot_sha256",
                "key_registry_snapshot", "key_registry_snapshot_sha256", "ledger",
                "ledger_chain_head", "evidence_lock_sha256"));
        assertEquals(LocalFinalReceiptVerifier.CONTRACT,
                finalReceipt.path("properties").path("contract").path("const").asText());
        assertEquals("SELF_VALIDATION_NONFINAL",
                finalReceipt.path("properties").path("assurance_class").path("const").asText());
        assertFalse(finalReceipt.path("properties").path("final_lock_allowed").path("const").asBoolean(true));
    }

    @Test
    void securityFindingContractMatchesGateConstant() throws Exception {
        JsonNode schema = schema("security-findings.v1.schema.json");
        assertExactFields(schema, Set.of("contract", "review_status", "review_method", "findings"));
        assertEquals(LocalSecurityGateVerifier.CONTRACT,
                schema.path("properties").path("contract").path("const").asText());
    }

    @Test
    void assuranceLaneStatusAndTransitionsMatchStateMachine() throws Exception {
        JsonNode lanes = mapper.readTree(Path.of("contracts/assurance-lanes.v1.json").toFile());
        JsonNode stateMachine = mapper.readTree(Path.of("contracts/state-machine.v1.json").toFile());
        assertEquals("IMPLEMENTATION_READY", lanes.path("mode").asText());
        assertEquals("HOLD", lanes.path("execution_gate").asText());

        List<String> laneTransitions = StreamSupport.stream(
                lanes.path("state_transitions").spliterator(), false).map(JsonNode::asText).toList();
        List<String> machineTransitions = StreamSupport.stream(
                stateMachine.path("transitions").spliterator(), false)
                .map(node -> node.path("from").asText() + "->" + node.path("to").asText())
                .toList();
        assertEquals(machineTransitions, laneTransitions);
    }

    private JsonNode schema(String filename) throws Exception {
        return mapper.readTree(Path.of("contracts", filename).toFile());
    }

    private static void assertExactFields(JsonNode schema, Set<String> expected) {
        assertFalse(schema.path("additionalProperties").asBoolean(true));
        assertEquals(expected, textSet(schema.path("required")));
        Set<String> properties = new LinkedHashSet<>();
        schema.path("properties").fieldNames().forEachRemaining(properties::add);
        assertEquals(expected, properties);
    }

    private static Set<String> textSet(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
