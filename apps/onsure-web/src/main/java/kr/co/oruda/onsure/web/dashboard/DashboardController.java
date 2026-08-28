package kr.co.oruda.onsure.web.dashboard;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    private static final List<String> ASSURANCE_STATES = List.of(
        "DECLARED",
        "DESIGNED",
        "IMPLEMENTED",
        "CONNECTED",
        "TESTED",
        "EVIDENCED",
        "INDEPENDENTLY_VERIFIED",
        "OPERATING_EFFECTIVELY"
    );

    @GetMapping("/")
    String dashboard(Model model) {
        model.addAttribute("productName", "ONSure Enterprise Web");
        model.addAttribute("assuranceStates", ASSURANCE_STATES);
        model.addAttribute("status", "SELF_VALIDATION_NONFINAL");
        model.addAttribute("portfolioEvidenceAvailable", false);
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
