package com.enterpriserisk.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RiskControllerTest {

  @Autowired
  MockMvc mvc;

  @Test
  void rejectsRequestsWithoutToken() throws Exception {
    mvc.perform(get("/api/risk-status"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
  }

  @Test
  void returnsStatusForAuthenticatedSubject() throws Exception {
    mvc.perform(get("/api/risk-status").with(jwt().jwt(j -> j.subject("auth0|test-user"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("protected"))
        .andExpect(jsonPath("$.subject").value("auth0|test-user"));
  }

  @Test
  void healthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void unknownPathsAreDeniedEvenWhenAuthenticated() throws Exception {
    mvc.perform(get("/actuator/env").with(jwt())).andExpect(status().isForbidden());
  }

  @Test
  void corsAllowsOnlyConfiguredOrigins() throws Exception {
    mvc.perform(get("/api/risk-status").with(jwt()).header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/risk-status").with(jwt()).header("Origin", "http://localhost:5173"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }
}
