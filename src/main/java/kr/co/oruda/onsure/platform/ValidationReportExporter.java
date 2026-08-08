package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.RcaRecord;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationReport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Produces commercial user-facing Markdown and HTML validation reports. */
public final class ValidationReportExporter {
    public void export(ValidationReport report, Path runRoot) throws Exception {
        write(runRoot.resolve("validation-report.md"), markdown(report));
        write(runRoot.resolve("validation-report.html"), html(report));
    }

    String markdown(ValidationReport report) {
        Map<String, RcaRecord> rcaByFinding = report.rcaRecords().stream()
                .collect(Collectors.toMap(RcaRecord::findingId, Function.identity(), (a, b) -> a));
        StringBuilder out = new StringBuilder();
        out.append("# ONSURE Validation Report\n\n");
        out.append("- Target: ").append(report.target().targetName()).append(" (`")
                .append(report.target().targetId()).append("`)\n");
        out.append("- Type: ").append(report.target().targetType()).append("\n");
        out.append("- Job: `").append(report.jobId()).append("`\n");
        out.append("- Decision: **").append(report.decision()).append("**\n");
        out.append("- Generated: ").append(report.generatedAt()).append("\n\n");
        out.append("## Summary\n\n");
        report.summary().forEach((key, value) -> {
            if (!"remediation_plans".equals(key)) out.append("- ").append(key).append(": ").append(value).append("\n");
        });
        out.append("\n## Findings and RCA\n\n");
        if (report.findings().isEmpty()) out.append("No findings.\n");
        for (Finding finding : report.findings()) {
            out.append("### ").append(finding.severity()).append(" — ").append(finding.title()).append("\n\n");
            out.append("- Finding ID: `").append(finding.findingId()).append("`\n");
            out.append("- Category: ").append(finding.category()).append("\n");
            out.append("- Location: `").append(finding.location()).append("`\n");
            out.append("- Description: ").append(finding.description()).append("\n");
            RcaRecord rca = rcaByFinding.get(finding.findingId());
            if (rca != null) {
                out.append("- Root cause: ").append(rca.rootCause()).append("\n");
                out.append("- Improvement: ").append(rca.remediation()).append("\n");
                out.append("- Revalidation: ").append(rca.revalidationRequirement()).append("\n");
            }
            out.append('\n');
        }
        out.append("## Regression Lock\n\n");
        if (report.regressionLock() == null) out.append("NOT_AVAILABLE\n");
        else out.append("- Source: `").append(report.regressionLock().sourceDigest()).append("`\n")
                .append("- Result: `").append(report.regressionLock().resultDigest()).append("`\n")
                .append("- Lock: `").append(report.regressionLock().lockDigest()).append("`\n");
        return out.toString();
    }

    String html(ValidationReport report) {
        String markdown = markdown(report);
        String escaped = escape(markdown);
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>ONSURE Validation Report</title>"
                + "<style>body{font-family:Arial,sans-serif;max-width:1000px;margin:40px auto;line-height:1.5;}"
                + "pre{white-space:pre-wrap;background:#f6f7f9;padding:24px;border-radius:8px;}"
                + "</style></head><body><pre>" + escaped + "</pre></body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void write(Path file, String value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, value, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
