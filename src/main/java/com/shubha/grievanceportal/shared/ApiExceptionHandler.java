package com.shubha.grievanceportal.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one place errors become HTTP responses, all as RFC 9457 ProblemDetail.
 * The base class already covers Spring MVC's own exceptions: validation
 * failures, unreadable bodies, missing headers and type mismatches (all 400).
 *
 * <p>The service's exceptions are precise; hiding a grievance from an actor who
 * cannot see it is the canView gate's job, before these are ever thrown
 * (STATE_MACHINE.md, "Errors: precise in the service, gated at the API").
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(IllegalTransitionException.class)
    ProblemDetail illegalTransition(IllegalTransitionException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(NotAuthorizedException.class)
    ProblemDetail notAuthorized(NotAuthorizedException e) {
        return problem(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(ReasonRequiredException.class)
    ProblemDetail reasonRequired(ReasonRequiredException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(ReopenLimitReachedException.class)
    ProblemDetail reopenLimitReached(ReopenLimitReachedException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler({IneligibleAssigneeException.class, InvalidHoldDetailsException.class,
            InvalidReferenceException.class})
    ProblemDetail unprocessable(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, e);
    }

    @ExceptionHandler(InvalidPhoneException.class)
    ProblemDetail invalidPhone(InvalidPhoneException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitedException e) {
        // Whole seconds, rounded up, so a client that waits exactly this long is let through.
        long seconds = Math.max(1, (e.getRetryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, e));
    }

    // Security exceptions get fixed details: Spring Security's messages are not
    // written for end users, and some (UsernameNotFoundException) would reveal
    // whether an account exists. SecurityConfig routes the filter chain's own
    // 401s and 403s here too.

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "The sign-in details are incorrect.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthenticated() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    // Includes a missing or invalid CSRF token.
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied.");
    }

    // @Version caught a concurrent change; the client should reload and retry.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentModification(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was changed by someone else. Reload and try again.");
    }

    // Anything unexpected: log it, never return its message or stack trace.
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private static ProblemDetail problem(HttpStatus status, RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }
}
