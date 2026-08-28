package kr.co.oruda.onsure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "server.address=127.0.0.1",
        "server.port=0"
})
@AutoConfigureMockMvc
class WorkbenchStatusControllerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void statusIsExplicitlyNonfinalAndFailClosed() throws Exception {
        mvc.perform(get("/api/v1/workbench/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract").value("ONSURE_WEB_WORKBENCH_STATUS_V1"))
                .andExpect(jsonPath("$.state").value("READ_ONLY_CANDIDATE_NONFINAL"))
                .andExpect(jsonPath("$.foundationProfile").value("SPRING_BOOT_JAVA17_MAVEN"))
                .andExpect(jsonPath("$.independentVerificationComplete").value(false))
                .andExpect(jsonPath("$.finalClaimAllowed").value(false))
                .andExpect(jsonPath("$.productionGo").value(false))
                .andExpect(jsonPath("$.authority.mutation").value("BLOCKED"))
                .andExpect(jsonPath("$.authority.merge").value("BLOCKED"))
                .andExpect(jsonPath("$.authority.release").value("BLOCKED"))
                .andExpect(jsonPath("$.authority.finalDecision").value("BLOCKED"));
    }
}
