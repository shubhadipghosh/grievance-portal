package com.shubha.grievanceportal;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

/**
 * A valid CSRF token, sent the way Angular sends it: the XSRF-TOKEN cookie
 * echoed in the X-XSRF-TOKEN header.
 *
 * <p>Use this, not spring-security-test's {@code csrf()}. That one permanently
 * swaps the shared CsrfFilter's token repository for a session-backed test
 * double, so every later test in the same cached context stops getting the
 * XSRF-TOKEN cookie and starts creating sessions.
 */
public final class CsrfTokens {

    private CsrfTokens() {
    }

    public static RequestPostProcessor csrfToken() {
        return request -> {
            String token = UUID.randomUUID().toString();
            Cookie[] existing = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] cookies = Arrays.copyOf(existing, existing.length + 1);
            cookies[existing.length] = new Cookie("XSRF-TOKEN", token);
            request.setCookies(cookies);
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}
