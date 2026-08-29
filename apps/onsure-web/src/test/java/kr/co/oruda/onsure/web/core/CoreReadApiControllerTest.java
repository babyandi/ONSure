package kr.co.oruda.onsure.web.core;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.co.oruda.onsure.web.config.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CoreReadApiController.class)
@Import({WebSecurityConfiguration.class, CoreReadProjectionService.class})
class CoreReadApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void coreReadApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/web/v1/projects"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void projectsAreExplicitlyUnavailableWhenCoreRootsAreMissing() throws Exception {
        mockMvc.perform(get("/api/web/v1/projects").with(user("reviewer")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availability").value("NOT_AVAILABLE"))
            .andExpect(jsonPath("$.reason").value("CORE_READ_ROOTS_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.value").doesNotExist());
    }

    @Test
    void assuranceDoesNotInventCanonicalStateWhenCoreRootsAreMissing() throws Exception {
        mockMvc.perform(get("/api/web/v1/projects/P1/targets/T1/assurance").with(user("reviewer")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availability").value("NOT_AVAILABLE"))
            .andExpect(jsonPath("$.reason").value("CORE_READ_ROOTS_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.value").doesNotExist());
    }
}
