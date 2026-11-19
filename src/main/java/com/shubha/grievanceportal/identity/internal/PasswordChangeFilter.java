package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.shared.PasswordChangeRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;

/**
 * Holds a session signed in with an admin-generated password to the endpoints
 * needed to replace it. Registered as a plain servlet filter, so it runs after
 * Spring Security has authenticated the request and checked CSRF.
 */
@Component
class PasswordChangeFilter extends OncePerRequestFilter {

    /** Session attribute set at login; cleared once the user chooses a password. */
    static final String MUST_CHANGE_PASSWORD = PasswordChangeFilter.class.getName() + ".MUST_CHANGE_PASSWORD";

    private static final Set<String> ALLOWED = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/password", "/api/v1/auth/logout");

    private final HandlerExceptionResolver resolver;

    PasswordChangeFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(MUST_CHANGE_PASSWORD) != null
                && !ALLOWED.contains(request.getRequestURI())) {
            resolver.resolveException(request, response, null, new PasswordChangeRequiredException());
            return;
        }
        chain.doFilter(request, response);
    }
}
