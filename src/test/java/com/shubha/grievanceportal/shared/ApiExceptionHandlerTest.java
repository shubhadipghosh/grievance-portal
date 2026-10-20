package com.shubha.grievanceportal.shared;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every exception type maps to its status, as ProblemDetail. No Spring context needed. */
class ApiExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        record Body(@NotBlank String name) {
        }

        @GetMapping("/throw/{type}")
        void throwIt(@PathVariable String type) throws Exception {
            throw switch (type) {
                case "notFound" -> new NotFoundException("Grievance", "GRV-2026-000001");
                case "illegalTransition" -> new IllegalTransitionException(Phase.A, Phase.B);
                case "notAuthorized" -> new NotAuthorizedException("no");
                case "reasonRequired" -> new ReasonRequiredException("reason");
                case "reopenLimit" -> new ReopenLimitReachedException(UUID.randomUUID());
                case "ineligibleAssignee" -> new IneligibleAssigneeException(UUID.randomUUID(), "not staff");
                case "invalidHold" -> new InvalidHoldDetailsException("note");
                case "invalidReference" -> new InvalidReferenceException("category", UUID.randomUUID());
                case "optimisticLock" -> new OptimisticLockingFailureException("version 3 != 4");
                case "invalidPhone" -> new InvalidPhoneException();
                case "rateLimited" -> new RateLimitedException(Duration.ofMillis(90_001));
                case "badCredentials" -> new BadCredentialsException("user admin@x is locked");
                case "unauthenticated" -> new InsufficientAuthenticationException("Full authentication is required");
                case "accessDenied" -> new AccessDeniedException("no");
                case "unexpected" -> new IllegalStateException("db password is hunter2");
                default -> throw new IllegalArgumentException("unknown type " + type);
            };
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Body body) {
        }

        enum Phase { A, B }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @ParameterizedTest
    @CsvSource({
            "notFound,           404",
            "illegalTransition,  409",
            "notAuthorized,      403",
            "reasonRequired,     400",
            "reopenLimit,        409",
            "ineligibleAssignee, 422",
            "invalidHold,        422",
            "invalidReference,   422",
            "optimisticLock,     409",
            "invalidPhone,       400",
            "rateLimited,        429",
            "badCredentials,     401",
            "unauthenticated,    401",
            "accessDenied,       403",
            "unexpected,         500",
    })
    void exceptionMapsToStatusAsProblemDetail(String type, int expectedStatus) throws Exception {
        mvc.perform(get("/throw/" + type))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void rateLimitedSaysWhenToRetryInWholeSecondsRoundedUp() throws Exception {
        mvc.perform(get("/throw/rateLimited"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "91"));
    }

    @Test
    void securityExceptionMessagesAreNotEchoed() throws Exception {
        mvc.perform(get("/throw/badCredentials"))
                .andExpect(content().string(not(containsString("locked"))))
                .andExpect(content().string(not(containsString("admin@x"))));
    }

    @Test
    void validationFailureIs400ProblemDetail() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unexpectedErrorsLeakNeitherMessageNorStackTrace() throws Exception {
        mvc.perform(get("/throw/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com.shubha"))));
    }
}
