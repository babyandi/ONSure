package kr.co.oruda.onsure.web;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workbench")
public final class WorkbenchStatusController {
    public static final String CONTRACT = "ONSURE_WEB_WORKBENCH_STATUS_V1";

    @GetMapping("/status")
    public WorkbenchStatus status() {
        return new WorkbenchStatus(
                CONTRACT,
                "READ_ONLY_CANDIDATE_NONFINAL",
                "SPRING_BOOT_JAVA17_MAVEN",
                true,
                false,
                false,
                false,
                List.of(
                        new Surface("chat", "PLANNED_NOT_CONNECTED"),
                        new Surface("programProfile", "PLANNED_NOT_CONNECTED"),
                        new Surface("learning", "PLANNED_NOT_CONNECTED"),
                        new Surface("verification", "PLANNED_NOT_CONNECTED"),
                        new Surface("findings", "PLANNED_NOT_CONNECTED"),
                        new Surface("improvement", "PLANNED_NOT_CONNECTED"),
                        new Surface("evidence", "PLANNED_NOT_CONNECTED"),
                        new Surface("gitAndPr", "PLANNED_NOT_CONNECTED")),
                Map.of(
                        "mutation", "BLOCKED",
                        "merge", "BLOCKED",
                        "release", "BLOCKED",
                        "finalDecision", "BLOCKED"));
    }

    public record Surface(String id, String state) {}

    public record WorkbenchStatus(
            String contract,
            String state,
            String foundationProfile,
            boolean browserSurfaceAvailable,
            boolean independentVerificationComplete,
            boolean finalClaimAllowed,
            boolean productionGo,
            List<Surface> surfaces,
            Map<String, String> authority) {}
}
