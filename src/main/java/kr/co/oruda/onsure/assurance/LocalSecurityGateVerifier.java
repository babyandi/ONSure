package kr.co.oruda.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LocalSecurityGateVerifier {
    public static final String CONTRACT = "ONSURE_SECURITY_FINDINGS_V1";
    private static final Set<String> SEVERITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");
    private static final Set<String> STATUSES = Set.of("OPEN", "CLOSED", "ACCEPTED_RISK");
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidationResult verify(Path findingsFile) {
        if (!Files.isRegularFile(findingsFile)) {
            return ValidationResult.fail(List.of("SECURITY_FINDINGS_SNAPSHOT_MISSING"));
        }
        List<String> violations = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(findingsFile.toFile());
            if (!CONTRACT.equals(root.path("contract").asText())) violations.add("SECURITY_FINDINGS_CONTRACT_MISMATCH");
            if (!"COMPLETE".equals(root.path("review_status").asText())) violations.add("SECURITY_REVIEW_INCOMPLETE");
            if (root.path("review_method").asText().isBlank()) violations.add("SECURITY_REVIEW_METHOD_MISSING");
            JsonNode findings = root.path("findings");
            if (!findings.isArray()) {
                violations.add("SECURITY_FINDINGS_INVALID");
            } else {
                Set<String> ids = new HashSet<>();
                for (JsonNode finding : findings) {
                    String id = finding.path("id").asText();
                    String severity = finding.path("severity").asText();
                    String status = finding.path("status").asText();
                    if (!id.matches("ONSURE-SEC-[0-9]{3,}") || !ids.add(id)) violations.add("SECURITY_FINDING_ID_INVALID");
                    if (!SEVERITIES.contains(severity)) violations.add("SECURITY_FINDING_SEVERITY_INVALID");
                    if (!STATUSES.contains(status)) violations.add("SECURITY_FINDING_STATUS_INVALID");
                    if (finding.path("summary").asText().isBlank()) violations.add("SECURITY_FINDING_SUMMARY_MISSING");
                    if (finding.path("resolution").asText().isBlank()) violations.add("SECURITY_FINDING_RESOLUTION_MISSING");
                    if (("CRITICAL".equals(severity) || "HIGH".equals(severity))
                            && !"CLOSED".equals(status)) {
                        violations.add("OPEN_BLOCKING_SECURITY_FINDING");
                    }
                }
            }
        } catch (Exception e) {
            violations.add("SECURITY_FINDINGS_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }
}
