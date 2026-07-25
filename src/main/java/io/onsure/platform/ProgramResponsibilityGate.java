package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.assurance.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Verifies that a program implementation still matches its source-backed role contract. */
public final class ProgramResponsibilityGate {
    public static final String PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING =
            "PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING";
    public static final String PROGRAM_ROLE_DEFINITION_EVIDENCE_INVALID =
            "PROGRAM_ROLE_DEFINITION_EVIDENCE_INVALID";
    public static final String PROGRAM_REQUIRED_RESPONSIBILITY_MISSING =
            "PROGRAM_REQUIRED_RESPONSIBILITY_MISSING";
    public static final String PROGRAM_RESPONSIBILITY_BOUNDARY_VIOLATION =
            "PROGRAM_RESPONSIBILITY_BOUNDARY_VIOLATION";
    public static final String PROGRAM_UNAPPROVED_RESPONSIBILITY_ADDED =
            "PROGRAM_UNAPPROVED_RESPONSIBILITY_ADDED";
    public static final String PROGRAM_RESPONSIBILITY_DUPLICATE_OWNER =
            "PROGRAM_RESPONSIBILITY_DUPLICATE_OWNER";
    public static final String PROGRAM_SHARED_RESPONSIBILITY_CONTRACT_INVALID =
            "PROGRAM_SHARED_RESPONSIBILITY_CONTRACT_INVALID";
    public static final String PROGRAM_CHARACTER_DRIFT = "PROGRAM_CHARACTER_DRIFT";
    public static final String PROGRAM_HIDDEN_SIDE_EFFECT = "PROGRAM_HIDDEN_SIDE_EFFECT";
    public static final String PROGRAM_IO_CONTRACT_BYPASS = "PROGRAM_IO_CONTRACT_BYPASS";

    public ValidationResult verify(List<ProgramContract> contracts, List<ProgramRun> runs,
            List<SharedResponsibilityContract> sharedContracts) {
        List<String> violations = new ArrayList<>();
        Map<String, ProgramContract> contractByProgram = new HashMap<>();
        for (ProgramContract contract : contracts) {
            contractByProgram.put(contract.programId(), contract);
            validateContractEvidence(contract, violations);
        }
        validateDuplicateOwners(contracts, sharedContracts, violations);
        for (ProgramRun run : runs) {
            ProgramContract contract = contractByProgram.get(run.programId());
            if (contract == null) {
                violations.add(PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING + ":" + run.programId());
                continue;
            }
            validateRun(contract, run, violations);
        }
        if (violations.isEmpty()) return ValidationResult.pass();
        return new ValidationResult(containsBlockingViolation(violations) ? Decision.BLOCKED : Decision.INCONCLUSIVE,
                violations);
    }

    private static void validateContractEvidence(ProgramContract contract, List<String> violations) {
        if (contract.sourceEvidence().isEmpty()) {
            violations.add(PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING + ":" + contract.programId());
            return;
        }
        for (String support : requiredSupports(contract)) {
            if (contract.sourceEvidence().stream().noneMatch(evidence -> evidence.supports().contains(support))) {
                violations.add(PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING + ":" + contract.programId() + ":" + support);
            }
        }
        for (SourceEvidence evidence : contract.sourceEvidence()) {
            if (!isDigest(evidence.sourceSha256())
                    || !isDigest(evidence.claimHash())
                    || !sha256(evidence.claim()).equals(evidence.claimHash())
                    || evidence.supports().isEmpty()) {
                violations.add(PROGRAM_ROLE_DEFINITION_EVIDENCE_INVALID + ":" + contract.programId()
                        + ":" + evidence.sourceId());
            }
        }
    }

    private static List<String> requiredSupports(ProgramContract contract) {
        List<String> supports = new ArrayList<>();
        supports.add("program_character:" + contract.character());
        contract.requiredResponsibilities().forEach(value -> supports.add("required_responsibility:" + value));
        contract.allowedResponsibilities().forEach(value -> supports.add("allowed_responsibility:" + value));
        contract.forbiddenResponsibilities().forEach(value -> supports.add("forbidden_responsibility:" + value));
        return supports;
    }

    private static void validateRun(ProgramContract contract, ProgramRun run, List<String> violations) {
        if (!Objects.equals(contract.character(), run.character())) {
            violations.add(PROGRAM_CHARACTER_DRIFT + ":" + run.programId());
        }
        for (String required : contract.requiredResponsibilities()) {
            if (!run.responsibilities().contains(required)) {
                violations.add(PROGRAM_REQUIRED_RESPONSIBILITY_MISSING + ":" + run.programId() + ":" + required);
            }
        }
        for (String actual : run.responsibilities()) {
            if (contract.forbiddenResponsibilities().contains(actual)) {
                violations.add(PROGRAM_RESPONSIBILITY_BOUNDARY_VIOLATION + ":" + run.programId() + ":" + actual);
            } else if (!contract.allowedResponsibilities().contains(actual)) {
                violations.add(PROGRAM_UNAPPROVED_RESPONSIBILITY_ADDED + ":" + run.programId() + ":" + actual);
            }
        }
        validateSubset(run.programId(), run.inputs(), contract.allowedInputs(), "input", violations);
        validateSubset(run.programId(), run.outputs(), contract.allowedOutputs(), "output", violations);
        for (String sideEffect : run.sideEffects()) {
            if (!contract.allowedSideEffects().contains(sideEffect)) {
                violations.add(PROGRAM_HIDDEN_SIDE_EFFECT + ":" + run.programId() + ":" + sideEffect);
            }
        }
    }

    private static void validateSubset(String programId, Set<String> actual, Set<String> allowed,
            String kind, List<String> violations) {
        for (String value : actual) {
            if (!allowed.contains(value)) {
                violations.add(PROGRAM_IO_CONTRACT_BYPASS + ":" + programId + ":" + kind + ":" + value);
            }
        }
    }

    private static void validateDuplicateOwners(List<ProgramContract> contracts,
            List<SharedResponsibilityContract> sharedContracts, List<String> violations) {
        Map<String, Set<String>> ownersByResponsibility = new HashMap<>();
        for (ProgramContract contract : contracts) {
            for (String responsibility : contract.allowedResponsibilities()) {
                ownersByResponsibility.computeIfAbsent(responsibility, ignored -> new LinkedHashSet<>())
                        .add(contract.programId());
            }
        }
        for (Map.Entry<String, Set<String>> entry : ownersByResponsibility.entrySet()) {
            if (entry.getValue().size() < 2) continue;
            if (!hasValidSharedContract(entry.getKey(), entry.getValue(), sharedContracts)) {
                violations.add(PROGRAM_RESPONSIBILITY_DUPLICATE_OWNER + ":" + entry.getKey() + ":"
                        + String.join(",", entry.getValue()));
            }
        }
    }

    private static boolean hasValidSharedContract(String responsibility, Set<String> owners,
            List<SharedResponsibilityContract> sharedContracts) {
        for (SharedResponsibilityContract contract : sharedContracts) {
            if (!Objects.equals(contract.responsibility(), responsibility)) continue;
            if (!contract.programIds().equals(owners)) continue;
            if (contract.scopeSplit().isBlank()
                    || contract.finalDecisionOwner().isBlank()
                    || !owners.contains(contract.finalDecisionOwner())
                    || !isDigest(contract.handoffReceiptHash())
                    || contract.approvedBy().isBlank()
                    || contract.sourceEvidence().isEmpty()) {
                return false;
            }
            List<String> evidenceViolations = new ArrayList<>();
            validateContractEvidence(new ProgramContract(
                    "shared:" + responsibility,
                    "shared_responsibility_contract",
                    Set.of(responsibility),
                    Set.of(responsibility),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    contract.sourceEvidence()), evidenceViolations);
            if (evidenceViolations.isEmpty()) return true;
        }
        return false;
    }

    private static boolean containsBlockingViolation(List<String> violations) {
        return violations.stream().anyMatch(value -> !value.startsWith(PROGRAM_ROLE_DEFINITION_EVIDENCE_MISSING));
    }

    private static boolean isDigest(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record SourceEvidence(String sourceId, String sourceSha256, String claim, String claimHash,
            Set<String> supports) {
        public SourceEvidence {
            requireText(sourceId, "sourceId");
            requireText(sourceSha256, "sourceSha256");
            requireText(claim, "claim");
            requireText(claimHash, "claimHash");
            supports = supports == null ? Set.of() : Set.copyOf(supports);
        }
    }

    public record ProgramContract(String programId, String character, Set<String> allowedResponsibilities,
            Set<String> requiredResponsibilities, Set<String> forbiddenResponsibilities, Set<String> allowedInputs,
            Set<String> allowedOutputs, Set<String> allowedSideEffects, List<SourceEvidence> sourceEvidence) {
        public ProgramContract {
            requireText(programId, "programId");
            requireText(character, "character");
            allowedResponsibilities = copySet(allowedResponsibilities);
            requiredResponsibilities = copySet(requiredResponsibilities);
            forbiddenResponsibilities = copySet(forbiddenResponsibilities);
            allowedInputs = copySet(allowedInputs);
            allowedOutputs = copySet(allowedOutputs);
            allowedSideEffects = copySet(allowedSideEffects);
            sourceEvidence = sourceEvidence == null ? List.of() : List.copyOf(sourceEvidence);
        }
    }

    public record ProgramRun(String programId, String character, Set<String> responsibilities, Set<String> inputs,
            Set<String> outputs, Set<String> sideEffects) {
        public ProgramRun {
            requireText(programId, "programId");
            requireText(character, "character");
            responsibilities = copySet(responsibilities);
            inputs = copySet(inputs);
            outputs = copySet(outputs);
            sideEffects = copySet(sideEffects);
        }
    }

    public record SharedResponsibilityContract(String responsibility, Set<String> programIds, String scopeSplit,
            String finalDecisionOwner, String handoffReceiptHash, String approvedBy,
            List<SourceEvidence> sourceEvidence) {
        public SharedResponsibilityContract {
            requireText(responsibility, "responsibility");
            programIds = copySet(programIds);
            scopeSplit = scopeSplit == null ? "" : scopeSplit;
            finalDecisionOwner = finalDecisionOwner == null ? "" : finalDecisionOwner;
            handoffReceiptHash = handoffReceiptHash == null ? "" : handoffReceiptHash;
            approvedBy = approvedBy == null ? "" : approvedBy;
            sourceEvidence = sourceEvidence == null ? List.of() : List.copyOf(sourceEvidence);
        }
    }

    private static Set<String> copySet(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(new HashSet<>(values));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
