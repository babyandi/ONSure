package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps explicit, product-neutral npm validation scripts into the seven-group profile. */
public final class NodeScriptValidationPack implements ValidationPack {
    private static final long MAX_PACKAGE_JSON_BYTES = 5L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofMinutes(15);
    private static final Map<String, StepKind> FUNCTIONAL = Map.of(
            "test:negative", StepKind.NEGATIVE_TEST,
            "test:retry", StepKind.RETRY_TEST,
            "test:blocking", StepKind.BLOCKING_TEST);
    private static final Map<String, StepKind> END_TO_END = Map.ofEntries(
            Map.entry("test:e2e-request", StepKind.E2E_REQUEST_FLOW),
            Map.entry("render", StepKind.E2E_RENDER_OR_PRODUCE),
            Map.entry("test:readback", StepKind.E2E_ARTIFACT_READBACK),
            Map.entry("test:tester", StepKind.E2E_TESTER_CHECK),
            Map.entry("test:audit", StepKind.E2E_AUDIT_CHECK),
            Map.entry("test:exposure", StepKind.E2E_EXPOSURE_DECISION));
    private static final Map<String, StepKind> OPERATIONS = Map.of(
            "test:interruption", StepKind.INTERRUPTION_TEST,
            "test:resume", StepKind.RESUME_TEST,
            "test:rollback", StepKind.ROLLBACK_TEST,
            "test:rerun", StepKind.RERUN_TEST);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String id() {
        return "node-scripts";
    }

    @Override
    public Contribution detect(Path sourceRoot) throws Exception {
        Path packageFile = sourceRoot.resolve("package.json").normalize();
        if (!packageFile.startsWith(sourceRoot)
                || !Files.isRegularFile(packageFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(packageFile)) return Contribution.none();
        if (Files.size(packageFile) > MAX_PACKAGE_JSON_BYTES) {
            throw new IllegalArgumentException("NODE_PACKAGE_JSON_TOO_LARGE");
        }
        JsonNode scripts = mapper.readTree(Files.readString(packageFile)).path("scripts");
        if (!scripts.isObject()) return Contribution.none();

        List<Step> steps = new ArrayList<>();
        String base = scripts.hasNonNull("test") ? "node.tests"
                : scripts.hasNonNull("build") ? "node.build" : "validator.meta-check";
        addDeclared(steps, scripts, FUNCTIONAL, Phase.COMPONENT_AND_NEGATIVE, List.of(base));

        List<String> functionalGate = new ArrayList<>();
        functionalGate.add(base);
        functionalGate.add(declaredOrPlaceholder(scripts, "test:negative", "functional.negative-paths"));
        functionalGate.add(declaredOrPlaceholder(scripts, "test:retry", "functional.retry-paths"));
        functionalGate.add(declaredOrPlaceholder(scripts, "test:blocking", "functional.blocking-paths"));
        addDeclared(steps, scripts, END_TO_END, Phase.END_TO_END_LINEAGE, functionalGate);
        addDeclared(steps, scripts, OPERATIONS, Phase.OPERATIONAL_RESILIENCE, List.of("evidence.verify"));
        return steps.isEmpty() ? Contribution.none()
                : new Contribution(Set.of("NODE_VALIDATION_SCRIPTS"), steps);
    }

    private static void addDeclared(List<Step> target, JsonNode scripts,
            Map<String, StepKind> mappings, Phase phase, List<String> dependencies) {
        Map<String, StepKind> ordered = new LinkedHashMap<>();
        mappings.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        for (Map.Entry<String, StepKind> entry : ordered.entrySet()) {
            if (!scripts.hasNonNull(entry.getKey())) continue;
            target.add(new Step(stepId(entry.getKey()), phase, entry.getValue(), true,
                    List.of("npm", "--offline", "run", entry.getKey()), Path.of(""),
                    TIMEOUT, dependencies));
        }
    }

    private static String declaredOrPlaceholder(JsonNode scripts, String script, String placeholder) {
        return scripts.hasNonNull(script) ? stepId(script) : placeholder;
    }

    private static String stepId(String script) {
        return "node-scripts." + script.replace(':', '-');
    }
}
