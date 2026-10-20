package com.shubha.grievanceportal.grievance.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without the "stub-auth" profile the header stub must not work: a forgotten
 * stub fails safe, with the API locked rather than open.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GrievanceApiLockedWithoutStubAuthTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiRejectsUnauthenticatedRequestsEvenWithActorHeader() throws Exception {
        UUID anyone = UUID.randomUUID();
        mvc.perform(get("/api/v1/grievances/{id}", UUID.randomUUID()).header(GrievanceController.ACTOR_HEADER, anyone))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/grievances").header(GrievanceController.ACTOR_HEADER, anyone)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> {
                    int code = result.getResponse().getStatus();
                    if (code != 401 && code != 403) {
                        throw new AssertionError("expected 401 or 403, got " + code);
                    }
                });
    }
}
