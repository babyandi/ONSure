package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.JobStatus;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationJob;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationReport;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// FR-META-043 AI·개발자·운영자 표시 일관성
class ValidationReportExporterTest {
    @TempDir Path temp;
    private static final String QUALIFIER =
            "Assurance Ceiling: SELF_VALIDATION_NONFINAL "
                    + "(self_validation_nonfinal=true, final_claim_allowed=false)";

    @Test
    void markdownNeverRendersDecisionWithoutTheSelfValidationNonfinalQualifier() {
        String markdown = new ValidationReportExporter().markdown(report(Decision.PASS));
        List<String> lines = List.of(markdown.split("\n"));
        int decisionLine = lines.indexOf("- Decision: **PASS**");
        assertTrue(decisionLine >= 0, "Decision line must be present verbatim");
        assertTrue(
                lines.get(decisionLine + 1).startsWith("- Assurance Ceiling: SELF_VALIDATION_NONFINAL"),
                "qualifier must be bound immediately after the Decision line, not merely present somewhere");
        assertTrue(markdown.contains(QUALIFIER));
    }

    @Test
    void qualifierIsUnconditionalAcrossEveryDecisionValue() {
        for (Decision decision : Decision.values()) {
            String markdown = new ValidationReportExporter().markdown(report(decision));
            assertTrue(
                    markdown.contains(QUALIFIER),
                    "decision " + decision + " must still carry the non-final qualifier");
        }
    }

    @Test
    void htmlCarriesTheSameQualifierAsMarkdownSinceBothMustShareOneOntology() {
        String html = new ValidationReportExporter().html(report(Decision.PASS));
        assertTrue(html.contains(QUALIFIER), "HTML report must not drop the qualifier markdown carries");
        assertTrue(html.contains("Decision: **PASS**"));
    }

    @Test
    void exportedFilesOnDiskBothCarryTheQualifierNextToARealPassDecision() throws Exception {
        Path runRoot = temp.resolve("run-1");
        new ValidationReportExporter().export(report(Decision.PASS), runRoot);

        String md = Files.readString(runRoot.resolve("validation-report.md"));
        String html = Files.readString(runRoot.resolve("validation-report.html"));
        assertTrue(md.contains(QUALIFIER));
        assertTrue(html.contains(QUALIFIER));
    }

    private static ValidationReport report(Decision decision) {
        Instant now = Instant.parse("2026-08-16T00:00:00Z");
        ValidationTarget target = new ValidationTarget(
                "target-1", "Target", TargetType.GENERAL_SOFTWARE, Path.of("."),
                "0123456789abcdef0123456789abcdef01234567",
                "ONSURE_GENERIC_MANIFEST_V1", "default", "isolated");
        ValidationJob job = new ValidationJob(
                "job-1", "target-1", JobStatus.COMPLETED, now, now, now, null);
        return new ValidationReport(
                "ONSURE_VALIDATION_REPORT_V1", "report-1", job.jobId(), target,
                decision, now, List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of());
    }
}
