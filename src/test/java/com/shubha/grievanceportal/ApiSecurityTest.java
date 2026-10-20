package com.shubha.grievanceportal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.shubha.grievanceportal.CsrfTokens.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API is closed by default. Walks every registered handler mapping, so a
 * new controller is covered without anyone remembering to add it here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiSecurityTest {

    /** The only endpoints reachable without a session: the ones that create one. */
    private static final Set<String> OPEN = Set.of(
            "/api/v1/auth/otp/send",
            "/api/v1/auth/otp/verify",
            "/api/v1/auth/staff/login");

    @Autowired
    MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyApiEndpointExceptLoginAnswers401ProblemWithoutASession() throws Exception {
        List<String> checked = new ArrayList<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/") || OPEN.contains(pattern)) {
                    continue;
                }
                String url = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    // With a CSRF token, so this checks authentication rather than CSRF.
                    MvcResult result = mvc.perform(request(HttpMethod.valueOf(method.name()), url).with(csrfToken()))
                            .andReturn();
                    assertThat(result.getResponse().getStatus()).as(method + " " + pattern).isEqualTo(401);
                    assertThat(result.getResponse().getContentType()).as(method + " " + pattern)
                            .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    checked.add(method + " " + pattern);
                }
            }
        }
        // Guards against the walk silently finding nothing.
        assertThat(checked).contains("GET /api/v1/auth/me", "POST /api/v1/grievances");
    }

    @Test
    void healthIsOpenForProbesAndShowsOnlyTheStatus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsStayClosed() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void postWithoutCsrfTokenIs403ProblemNotARedirect() throws Exception {
        mvc.perform(post("/api/v1/grievances").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
