package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Performs a deterministic multi-domain review over source, profiles and atomic requirements. */
public final class StructuredReviewEngine {
    public static final String CONTRACT = "ONSURE_REVIEW_RESULT_V1";
    private static final List<String> DOMAINS = List.of(
            "REQUIREMENT", "ARCHITECTURE", "POLICY", "CODE", "AI",
            "SECURITY", "PERFORMANCE", "TEST", "MERGE");
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> review(
            Path sourceRoot,
            Path programProfile,
            Path behaviorProfile,
            Path atomicRequirements,
            Path output) throws Exception {
        Path root = sourceRoot.toAbsolutePath().normalize();
        String sourceDigest = Hashing.tree(root);
        JsonNode program = mapper.readTree(programProfile.toFile());
        JsonNode behavior = mapper.readTree(behaviorProfile.toFile());
        JsonNode trace = mapper.readTree(atomicRequirements.toFile());
        if (!"ONSURE_PROGRAM_PROFILE_V1".equals(program.path("contract").asText())) {
            throw new IllegalArgumentException("REVIEW_PROGRAM_PROFILE_INVALID");
        }
        if (!"ONSURE_BEHAVIOR_PROFILE_V1".equals(behavior.path("contract").asText())) {
            throw new IllegalArgumentException("REVIEW_BEHAVIOR_PROFILE_INVALID");
        }
        if (!"ONSURE_ATOMIC_REQUIREMENTS_REGISTER_V1".equals(trace.path("contract").asText())) {
            throw new IllegalArgumentException("REVIEW_ATOMIC_REQUIREMENTS_INVALID");
        }

        List<Map<String, Object>> findings = new ArrayList<>();
        int unmapped = 0;
        for (JsonNode requirement : trace.path("requirements")) {
            String mapping = requirement.path("mapping_state").asText();
            if (Set.of("UNMAPPED", "PARTIALLY_MAPPED", "CONFLICT").contains(mapping)) {
                unmapped++;
                addFinding(findings, "REQUIREMENT", "HIGH",
                        "Atomic requirement is not fully traced",
                        requirement.path("document").asText() + ":"
                                + requirement.path("line").asInt(),
                        List.of(requirement.path("requirement_id").asText(),
                                requirement.path("source_digest").asText()));
            }
        }
        for (JsonNode unknown : program.path("unknowns")) {
            addFinding(findings, "ARCHITECTURE", "MEDIUM",
                    "Program architecture contains an unresolved unknown",
                    "program-profile:" + unknown.asText(),
                    List.of(program.path("profile_id").asText()));
        }
        for (JsonNode conflict : program.path("conflicts")) {
            addFinding(findings, "ARCHITECTURE", "HIGH",
                    "Program profile contains a conflicting implementation signal",
                    "program-profile:" + conflict.asText(),
                    List.of(program.path("profile_id").asText()));
        }
        for (JsonNode violation : behavior.path("policy_violations")) {
            addFinding(findings, "AI", "HIGH",
                    "Observed AI or tool behavior violated a policy scenario",
                    "behavior-profile:" + violation.asText(),
                    List.of(behavior.path("profile_id").asText()));
        }
        if (!behavior.path("variability").path("stable").asBoolean(false)) {
            addFinding(findings, "AI", "MEDIUM",
                    "Repeated behavior produced nondeterministic outputs",
                    "behavior-profile:variability",
                    List.of(behavior.path("profile_id").asText()));
        }

        reviewSource(root, findings);
        if (findings.stream().noneMatch(value -> "TEST".equals(value.get("domain")))) {
            boolean testFound;
            try (var stream = Files.walk(root)) {
                testFound = stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                        .anyMatch(name -> name.contains("test") || name.contains("spec"));
            }
            if (!testFound) {
                addFinding(findings, "TEST", "HIGH",
                        "No executable test source was discovered",
                        root.toString(), List.of("SOURCE_TREE_SHA256:" + sourceDigest));
            }
        }

        boolean blocking = findings.stream().anyMatch(value ->
                Set.of("CRITICAL", "HIGH").contains(value.get("severity")));
        String decision = blocking ? "FAIL" : findings.isEmpty() ? "PASS_NONFINAL" : "HOLD";
        String mergeDecision = blocking ? "CHANGES_REQUIRED" : "MERGE_READY_CANDIDATE";
        String reviewId = "REVIEW-" + Hashing.sha256(
                sourceDigest + "|" + program.path("profile_id").asText() + "|"
                        + behavior.path("profile_id").asText() + "|" + findings)
                .substring(0, 20);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("review_id", reviewId);
        result.put("source_digest", sourceDigest);
        result.put("domains", DOMAINS);
        result.put("findings", findings);
        result.put("unmapped_requirement_count", unmapped);
        result.put("decision", decision);
        result.put("merge_decision", mergeDecision);
        result.put("created_at", Instant.now().toString());
        result.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        result.put("independent_review", "NOT_RUN");
        result.put("final_claim_allowed", false);
        write(output, result);
        return Map.copyOf(result);
    }

    private void reviewSource(Path root, List<Map<String, Object>> findings) throws Exception {
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> {
                        try { return Files.size(path) <= 1_000_000; }
                        catch (Exception ignored) { return false; }
                    }).toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.matches(".*\\.(java|kt|py|js|ts|json|ya?ml|xml|properties|sh|md)$")) {
                    continue;
                }
                String content;
                try { content = Files.readString(file, StandardCharsets.UTF_8); }
                catch (Exception ignored) { continue; }
                String location = Hashing.relative(root, file);
                for (Rule rule : RULES) {
                    if (rule.matches(content)) {
                        addFinding(findings, rule.domain(), rule.severity(), rule.title(),
                                location, List.of("FILE_SHA256:" + Hashing.file(file)));
                    }
                }
            }
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule("SECURITY", "CRITICAL", "Unbounded command execution pattern",
                    value -> value.contains("Runtime.getRuntime().exec")
                            || value.contains("ProcessBuilder(\"sh\", \"-c\"")),
            new Rule("SECURITY", "HIGH", "Potential hard-coded credential or secret",
                    value -> value.matches("(?s).*(?i)(password|api[_-]?key|secret)\\s*[:=]\\s*[\"'][^\"']{8,}[\"'].*")),
            new Rule("CODE", "HIGH", "Unresolved defect marker in executable source",
                    value -> value.contains("TODO_BUG") || value.contains("FIXME_CRITICAL")),
            new Rule("AI", "HIGH", "Agent self-approval marker",
                    value -> value.contains("SELF_APPROVE")),
            new Rule("AI", "HIGH", "Prompt-injection bypass marker",
                    value -> value.contains("PROMPT_INJECTION_BYPASS")),
            new Rule("PERFORMANCE", "MEDIUM", "Unbounded in-memory read pattern",
                    value -> value.contains("readAllBytes()") || value.contains("Files.readAllBytes")),
            new Rule("POLICY", "HIGH", "Final or production claim embedded in mutable code",
                    value -> value.contains("production_go=true")
                            || value.contains("final_lock_allowed=true")));

    private static void addFinding(
            List<Map<String, Object>> findings, String domain, String severity,
            String title, String location, List<String> evidenceRefs) {
        String id = "RF-" + Hashing.sha256(domain + "|" + title + "|" + location)
                .substring(0, 16);
        if (findings.stream().anyMatch(value -> id.equals(value.get("finding_id")))) return;
        findings.add(Map.of(
                "finding_id", id,
                "domain", domain,
                "severity", severity,
                "title", title,
                "location", location,
                "evidence_refs", evidenceRefs,
                "state", "OPEN"));
    }

    private void write(Path output, Object value) throws Exception {
        Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Rule(
            String domain, String severity, String title,
            java.util.function.Predicate<String> predicate) {
        boolean matches(String value) { return predicate.test(value); }
    }
}
