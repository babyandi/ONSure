package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.assurance.ValidationResult;
import io.onsure.platform.ProgramResponsibilityGate.ProgramContract;
import io.onsure.platform.ProgramResponsibilityGate.ProgramRun;
import io.onsure.platform.ProgramResponsibilityGate.SharedResponsibilityContract;
import io.onsure.platform.ProgramResponsibilityGate.SourceEvidence;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProgramResponsibilityGateTest {
    private final ProgramResponsibilityGate gate = new ProgramResponsibilityGate();

    @Test
    void validProgramContractAndRunPass() {
        ValidationResult result = gate.verify(List.of(obuilderContract()), List.of(obuilderRun()), List.of());

        assertEquals(Decision.PASS, result.decision());
    }

    @Test
    void missingRoleEvidenceReturnsInconclusiveInsteadOfInventingProgramMeaning() {
        ProgramContract contract = new ProgramContract(
                "OBuilder", "build_candidate_assembly",
                Set.of("build_candidate_publishing"), Set.of("build_candidate_publishing"),
                Set.of("release_approval"), Set.of("page_spec"), Set.of("build_candidate"),
                Set.of(), List.of());

        ValidationResult result = gate.verify(List.of(contract), List.of(obuilderRun()), List.of());

        assertEquals(Decision.INCONCLUSIVE, result.decision());
        assertContains(result, ProgramResponsibilityGate.PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING);
    }

    @Test
    void blockingViolationsAreNotHiddenByUnknownEvidence() {
        ProgramContract contract = new ProgramContract(
                "OBuilder", "build_candidate_assembly",
                Set.of("build_candidate_publishing"), Set.of("build_candidate_publishing"),
                Set.of("release_approval"), Set.of("page_spec"), Set.of("build_candidate"),
                Set.of(), List.of());
        ProgramRun run = new ProgramRun(
                "OBuilder", "release_governance",
                Set.of("build_candidate_publishing", "release_approval"),
                Set.of("page_spec", "production_secret"), Set.of("build_candidate"),
                Set.of("notify_customer"));

        ValidationResult result = gate.verify(List.of(contract), List.of(run), List.of());

        assertEquals(Decision.BLOCKED, result.decision());
        assertContains(result, ProgramResponsibilityGate.PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING);
        assertContains(result, ProgramResponsibilityGate.PROGRAM_CHARACTER_DRIFT);
        assertContains(result, ProgramResponsibilityGate.PROGRAM_RESPONSIBILITY_BOUNDARY_VIOLATION);
        assertContains(result, ProgramResponsibilityGate.PROGRAM_IO_CONTRACT_BYPASS);
        assertContains(result, ProgramResponsibilityGate.PROGRAM_HIDDEN_SIDE_EFFECT);
    }

    @Test
    void invalidEvidenceIsBlockedEvenWhenEvidenceFieldsExist() {
        SourceEvidence invalid = new SourceEvidence(
                "authority.md", "f".repeat(64), "changed claim", "0".repeat(64),
                Set.of("program_character:build_candidate_assembly"));
        ProgramContract contract = new ProgramContract(
                "OBuilder", "build_candidate_assembly",
                Set.of("build_candidate_publishing"), Set.of("build_candidate_publishing"),
                Set.of("release_approval"), Set.of("page_spec"), Set.of("build_candidate"),
                Set.of(), List.of(invalid));

        ValidationResult result = gate.verify(List.of(contract), List.of(obuilderRun()), List.of());

        assertEquals(Decision.BLOCKED, result.decision());
        assertContains(result, ProgramResponsibilityGate.PROGRAM_ROLE_DEFINITION_EVIDENCE_INVALID);
    }

    @Test
    void unapprovedResponsibilityIsBlockedEvenIfItLooksUseful() {
        ProgramRun run = new ProgramRun(
                "OBuilder", "build_candidate_assembly",
                Set.of("build_candidate_publishing", "release_approval_dashboard"),
                Set.of("page_spec"), Set.of("build_candidate"), Set.of());

        ValidationResult result = gate.verify(List.of(obuilderContract()), List.of(run), List.of());

        assertEquals(Decision.BLOCKED, result.decision());
        assertContains(result, ProgramResponsibilityGate.PROGRAM_UNAPPROVED_RESPONSIBILITY_ADDED);
    }

    @Test
    void duplicateResponsibilityOwnersAreBlockedWithoutStrictSharedContract() {
        ProgramContract release = contract(
                "ORelease", "release_governance",
                Set.of("build_candidate_publishing", "release_approval"),
                Set.of("release_approval"), Set.of(),
                Set.of("build_candidate"), Set.of("release_receipt"), Set.of());

        ValidationResult result = gate.verify(List.of(obuilderContract(), release), List.of(obuilderRun()), List.of());

        assertEquals(Decision.BLOCKED, result.decision());
        assertContains(result, ProgramResponsibilityGate.PROGRAM_RESPONSIBILITY_DUPLICATE_OWNER);
    }

    @Test
    void sharedResponsibilityContractMustHaveRealApprovalAndHandoffEvidence() {
        ProgramContract release = contract(
                "ORelease", "release_governance",
                Set.of("build_candidate_publishing", "release_approval"),
                Set.of("release_approval"), Set.of(),
                Set.of("build_candidate"), Set.of("release_receipt"), Set.of());
        SharedResponsibilityContract weak = new SharedResponsibilityContract(
                "build_candidate_publishing", Set.of("OBuilder", "ORelease"),
                "", "", "", "", List.of());

        ValidationResult result = gate.verify(
                List.of(obuilderContract(), release), List.of(obuilderRun()), List.of(weak));

        assertEquals(Decision.BLOCKED, result.decision());
        assertContains(result, ProgramResponsibilityGate.PROGRAM_RESPONSIBILITY_DUPLICATE_OWNER);
    }

    @Test
    void strictSharedResponsibilityContractAllowsExplicitJointOwnership() {
        ProgramContract release = contract(
                "ORelease", "release_governance",
                Set.of("build_candidate_publishing", "release_approval"),
                Set.of("release_approval"), Set.of(),
                Set.of("build_candidate"), Set.of("release_receipt"), Set.of());
        SharedResponsibilityContract shared = new SharedResponsibilityContract(
                "build_candidate_publishing", Set.of("OBuilder", "ORelease"),
                "OBuilder creates candidate bytes; ORelease consumes candidate bytes for release decision.",
                "ORelease", "a".repeat(64), "architecture-board",
                List.of(evidence("shared build candidate publishing is split by producer and final owner",
                        Set.of(
                                "program_character:shared_responsibility_contract",
                                "required_responsibility:build_candidate_publishing",
                                "allowed_responsibility:build_candidate_publishing"))));

        ValidationResult result = gate.verify(
                List.of(obuilderContract(), release), List.of(obuilderRun()), List.of(shared));

        assertFalse(result.violations().stream()
                .anyMatch(value -> value.startsWith(ProgramResponsibilityGate.PROGRAM_RESPONSIBILITY_DUPLICATE_OWNER)));
    }

    private static ProgramContract obuilderContract() {
        return contract(
                "OBuilder", "build_candidate_assembly",
                Set.of("build_candidate_publishing"), Set.of("build_candidate_publishing"),
                Set.of("release_approval"), Set.of("page_spec"), Set.of("build_candidate"), Set.of());
    }

    private static ProgramContract contract(String programId, String character, Set<String> allowed,
            Set<String> required, Set<String> forbidden, Set<String> inputs, Set<String> outputs,
            Set<String> sideEffects) {
        return new ProgramContract(
                programId, character, allowed, required, forbidden, inputs, outputs, sideEffects,
                List.of(evidence(programId + " is defined by authority", supports(character, allowed, required, forbidden))));
    }

    private static ProgramRun obuilderRun() {
        return new ProgramRun(
                "OBuilder", "build_candidate_assembly",
                Set.of("build_candidate_publishing"),
                Set.of("page_spec"), Set.of("build_candidate"), Set.of());
    }

    private static Set<String> supports(String character, Set<String> allowed, Set<String> required,
            Set<String> forbidden) {
        Set<String> out = new java.util.LinkedHashSet<>();
        out.add("program_character:" + character);
        allowed.forEach(value -> out.add("allowed_responsibility:" + value));
        required.forEach(value -> out.add("required_responsibility:" + value));
        forbidden.forEach(value -> out.add("forbidden_responsibility:" + value));
        return out;
    }

    private static SourceEvidence evidence(String claim, Set<String> supports) {
        return new SourceEvidence(
                "authority.md",
                "b".repeat(64),
                claim,
                ProgramResponsibilityGate.sha256(claim),
                supports);
    }

    private static void assertContains(ValidationResult result, String code) {
        assertTrue(result.violations().stream().anyMatch(value -> value.startsWith(code)),
                () -> "missing " + code + " in " + result.violations());
    }
}
