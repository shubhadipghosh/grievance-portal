package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.identity.AppUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.shubha.grievanceportal.CsrfTokens.csrfToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET and PUT /api/v1/profile through the full filter chain. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, OtpServiceTest.TestBeans.class})
class ProfileApiTest {

    private static final String EMAIL = "profile-test-1@example.test";
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AppUserRepository users;

    @Autowired
    PasswordEncoder encoder;

    AppUser staff;
    Cookie session;

    @BeforeEach
    void signIn() throws Exception {
        jdbc.sql("DELETE FROM rate_limit").update();
        jdbc.sql("DELETE FROM app_user WHERE email LIKE 'profile-test-%'").update();
        staff = users.save(AppUser.staff("Test Staff", EMAIL, encoder.encode(PASSWORD)));
        session = mvc.perform(post("/api/v1/auth/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(EMAIL, PASSWORD))
                        .with(csrfToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
    }

    ResultActions update(String body) throws Exception {
        return mvc.perform(put("/api/v1/profile").cookie(session).with(csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void returnsTheSignedInUsersDetailsWithoutThePasswordHash() throws Exception {
        mvc.perform(get("/api/v1/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("STAFF"))
                .andExpect(jsonPath("$.fullName").value("Test Staff"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.preferredLocale").value("en"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void requiresASession() throws Exception {
        mvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void updatesNameAndLanguage() throws Exception {
        update("{\"fullName\": \"  Ravi Kumar \", \"preferredLocale\": \"bn\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.preferredLocale").value("bn"));

        AppUser saved = users.findById(staff.getId()).orElseThrow();
        assertThat(saved.getFullName()).isEqualTo("Ravi Kumar");
        assertThat(saved.getPreferredLocale()).isEqualTo("bn");
    }

    @Test
    void rejectsABlankNameOrUnsupportedLanguageAndChangesNothing() throws Exception {
        update("{\"fullName\": \"   \", \"preferredLocale\": \"en\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        update("{\"fullName\": \"Ravi\", \"preferredLocale\": \"fr\"}")
                .andExpect(status().isBadRequest());

        assertThat(users.findById(staff.getId()).orElseThrow().getFullName()).isEqualTo("Test Staff");
    }

    @Test
    void ignoresAttemptsToChangeTheLoginEmail() throws Exception {
        update("{\"fullName\": \"Ravi\", \"preferredLocale\": \"en\", \"email\": \"profile-test-2@example.test\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void updateWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(put("/api/v1/profile").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Ravi\", \"preferredLocale\": \"en\"}"))
                .andExpect(status().isForbidden());
    }
}
