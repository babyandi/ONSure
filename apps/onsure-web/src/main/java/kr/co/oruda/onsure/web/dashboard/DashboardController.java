package kr.co.oruda.onsure.web.dashboard;

import java.time.Instant;
import kr.co.oruda.onsure.web.core.CoreReadProjectionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {
    private final CoreReadProjectionService coreRead;

    public DashboardController(CoreReadProjectionService coreRead) {
        this.coreRead = coreRead;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        var projects = coreRead.projects();
        model.addAttribute("productName", "ONSure Enterprise Web");
        model.addAttribute("status", "SELF_VALIDATION_NONFINAL");
        model.addAttribute("coreAvailability", projects.availability().name());
        model.addAttribute("coreUnavailableReason", projects.reason());
        model.addAttribute("projects", projects.value());
        model.addAttribute("portfolioEvidenceAvailable", false);
        model.addAttribute("assuranceProjectionAvailable", false);
        model.addAttribute("generatedAt", Instant.now());
        return "dashboard";
    }

    @GetMapping("/healthz")
    @ResponseBody
    HealthProjection health() {
        return new HealthProjection("UP", "ONSURE_WEB_VERTICAL_SLICE_NONFINAL");
    }

    record HealthProjection(String status, String authority) {}
}
