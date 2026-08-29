package kr.co.oruda.onsure.web.dashboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import kr.co.oruda.onsure.web.config.WebSecurityConfiguration;
import kr.co.oruda.onsure.web.core.CoreReadProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({WebSecurityConfiguration.class, CoreReadProjectionService.class})
class DashboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthIsPublicAndExplicitlyNonfinal() throws Exception {
        mockMvc.perform(get("/healthz"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\",\"authority\":\"ONSURE_WEB_VERTICAL_SLICE_NONFINAL\"}"));
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void authenticatedDashboardFailsClosedWhenCoreRootsAreNotConfigured() throws Exception {
        mockMvc.perform(get("/").with(user("reviewer")))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attribute("status", "SELF_VALIDATION_NONFINAL"))
            .andExpect(model().attribute("coreAvailability", "NOT_AVAILABLE"))
            .andExpect(model().attribute("coreUnavailableReason", "CORE_READ_ROOTS_NOT_CONFIGURED"))
            .andExpect(model().attribute("portfolioEvidenceAvailable", false))
            .andExpect(model().attribute("assuranceProjectionAvailable", false));
    }
}
