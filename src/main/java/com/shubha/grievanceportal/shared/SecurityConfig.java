package com.shubha.grievanceportal.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Session-based security. The identity module's login endpoints store a
 * principal in a Spring Session; this chain authenticates every later request
 * from that session.
 *
 * <p>The chain's own 401s and 403s go through {@link ApiExceptionHandler}, so
 * they are ProblemDetail like every other error, never a redirect or HTML page.
 */
@Configuration
class SecurityConfig {

    /**
     * The SESSION cookie's attributes, set here rather than through
     * server.servlet.session.cookie.*: Boot applies those properties only with
     * an embedded server, and builds the serializer from the servlet context
     * otherwise (a WAR deployment, or MockMvc), silently dropping all three.
     * A customizer runs on both paths.
     */
    @Bean
    DefaultCookieSerializerCustomizer sessionCookie(@Value("${app.secure-cookies}") boolean secure) {
        return serializer -> {
            serializer.setUseHttpOnlyCookie(true);
            serializer.setSameSite("Lax");
            serializer.setUseSecureCookie(secure);
        };
    }

    /**
     * Readable by JavaScript: Angular copies the XSRF-TOKEN cookie into the
     * X-XSRF-TOKEN header. Shared with the login endpoints, which issue a fresh
     * token when a session is established.
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${app.secure-cookies}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Lax"));
        return repository;
    }

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokens,
                                       @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, e) ->
                resolver.resolveException(request, response, null, e);
        AccessDeniedHandler denied = (request, response, e) ->
                resolver.resolveException(request, response, null, e);

        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Load balancers and uptime monitors probe without a session. Status only:
                        // show-details and show-components stay at their default, never.
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(denied))
                // Otherwise every unauthenticated call creates a session row to remember where it was going.
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    /**
     * The CSRF token is generated lazily, and its cookie is only written once
     * something reads it. Reading it on every request means any response, even
     * a 401 from /api/v1/auth/me, hands Angular a token before its first POST.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
