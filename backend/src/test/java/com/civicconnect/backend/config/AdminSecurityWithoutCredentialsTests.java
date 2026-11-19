package com.civicconnect.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.civicconnect.backend.repository.IssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.admin.username=",
        "app.admin.password="
})
class AdminSecurityWithoutCredentialsTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueRepository issueRepository;

    @Test
    void publicIssueFeedStartsWhileAdminEndpointRejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/issues"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/issues/admin"))
                .andExpect(status().isUnauthorized());
    }
}
