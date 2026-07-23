package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.RevalidationDelta;
import io.onsure.platform.ValidationModel.ValidationReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Compares baseline and current reports after remediation. */
public final class RevalidationService {
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public RevalidationDelta compare(ValidationReport baseline, ValidationReport current) {
        Set<String> before = fingerprints(baseline);
        Set<String> after = fingerprints(current);
        Set<String> resolved = new LinkedHashSet<>(before);
        resolved.removeAll(after);
        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        Set<String> unchanged = new LinkedHashSet<>(before);
        unchanged.retainAll(after);
        boolean sourceChanged = !baseline.regressionLock().sourceDigest()
                .equals(current.regressionLock().sourceDigest());
        boolean resultChanged = !baseline.regressionLock().resultDigest()
                .equals(current.regressionLock().resultDigest());
        return new RevalidationDelta(
                baseline.jobId(), current.jobId(), resolved.stream().sorted().toList(),
                added.stream().sorted().toList(), unchanged.stream().sorted().toList(),
                sourceChanged, resultChanged);
    }

    public RevalidationDelta compareAndWrite(
            ValidationReport baseline, ValidationReport current, Path output) throws Exception {
        RevalidationDelta delta = compare(baseline, current);
        Files.createDirectories(output.toAbsolutePath().normalize().getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), delta);
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
        return delta;
    }

    private static Set<String> fingerprints(ValidationReport report) {
        Set<String> values = new LinkedHashSet<>();
        for (Finding finding : report.findings()) values.add(finding.fingerprint());
        return values;
    }
}
