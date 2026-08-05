package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrudaTargetAdapterContributorTest {
    @Test
    void missingContributorFailsClosed() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new OrudaTargetAdapter(List.of(contributor(false))).evidenceContributor());
        assertTrue(error.getMessage().contains("ORUDA_EVIDENCE_CONTRIBUTOR_MISSING"));
    }

    @Test
    void ambiguousContributorsFailClosed() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new OrudaTargetAdapter(List.of(contributor(true), contributor(true)))
                        .evidenceContributor());
        assertTrue(error.getMessage().contains("ORUDA_EVIDENCE_CONTRIBUTOR_AMBIGUOUS"));
    }

    @Test
    void exactlyOneSupportingContributorIsSelected() {
        TargetEvidenceContributor expected = contributor(true);
        assertSame(expected, new OrudaTargetAdapter(List.of(contributor(false), expected))
                .evidenceContributor());
    }

    private static TargetEvidenceContributor contributor(boolean supports) {
        return new TargetEvidenceContributor() {
            @Override
            public boolean supports(String adapterId) {
                return supports && OrudaTargetAdapter.ID.equals(adapterId);
            }

            @Override
            public ValidationResult persistAndVerify(ValidationContext context) {
                return ValidationResult.pass();
            }
        };
    }
}
