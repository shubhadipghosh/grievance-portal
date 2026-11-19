package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.CurrentUser;
import com.shubha.grievanceportal.identity.OtpSendResult;
import com.shubha.grievanceportal.identity.OtpService;
import com.shubha.grievanceportal.identity.OtpVerifyResult;
import com.shubha.grievanceportal.identity.UserType;
import com.shubha.grievanceportal.shared.InvalidPhoneException;
import com.shubha.grievanceportal.shared.RateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Login and session endpoints. A login stores a {@link CurrentUser} in the
 * session and nothing else; permissions are looked up per request.
 * POST /api/v1/auth/logout is handled by the filter chain (shared.SecurityConfig).
 *
 * <p>The request IP is always the servlet request's remote address, never a
 * client-supplied header: trusting X-Forwarded-For would let a caller choose a
 * fresh IP per request and walk past the per-IP OTP cap. Behind a reverse proxy,
 * configure server.forward-headers-strategy so the container resolves the
 * client address from the proxy's headers, trusting only that proxy.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final OtpService otp;
    private final StaffLoginService staffLogin;
    private final PasswordChangeService passwordChange;
    private final CsrfTokenRepository csrfTokens;
    private final SecurityContextRepository securityContexts = new HttpSessionSecurityContextRepository();

    AuthController(OtpService otp, StaffLoginService staffLogin, PasswordChangeService passwordChange,
                   CsrfTokenRepository csrfTokens) {
        this.otp = otp;
        this.staffLogin = staffLogin;
        this.passwordChange = passwordChange;
        this.csrfTokens = csrfTokens;
    }

    record OtpSendRequest(@NotBlank String phone) {
    }

    record OtpVerifyRequest(@NotBlank String phone, @NotBlank String code) {
    }

    // app_user.email is VARCHAR(255).
    record StaffLoginRequest(@NotBlank @Size(max = 255) String email, @NotBlank String password) {
    }

    // BCrypt reads at most 72 bytes; a longer password would be silently truncated.
    record PasswordChangeRequest(@NotBlank String currentPassword,
                                 @NotBlank @Size(min = 12, max = 72) String newPassword) {
    }

    /**
     * The signed-in user, and whether they must change their password before
     * anything else (PasswordChangeFilter refuses every other endpoint until then).
     */
    record SignedIn(UUID id, UserType type, boolean mustChangePassword) {
    }

    @PostMapping("/otp/send")
    ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpSendRequest body, HttpServletRequest request) {
        return switch (otp.sendLoginOtp(body.phone(), request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT))) {
            case OtpSendResult.Sent _ -> ResponseEntity.noContent().build();
            case OtpSendResult.RateLimited limited -> throw new RateLimitedException(limited.retryAfter());
            case OtpSendResult.InvalidPhone _ -> throw new InvalidPhoneException();
        };
    }

    @PostMapping("/otp/verify")
    CurrentUser verifyOtp(@Valid @RequestBody OtpVerifyRequest body, HttpServletRequest request,
                          HttpServletResponse response) {
        return switch (otp.verifyLoginOtp(body.phone(), body.code(), request.getRemoteAddr())) {
            // OtpService only verifies ACTIVE citizens.
            case OtpVerifyResult.Verified verified ->
                    establishSession(new CurrentUser(verified.userId(), UserType.CITIZEN), request, response);
            case OtpVerifyResult.Failed _ -> throw new BadCredentialsException("OTP verification failed");
        };
    }

    @PostMapping("/staff/login")
    SignedIn staffLogin(@Valid @RequestBody StaffLoginRequest body, HttpServletRequest request,
                        HttpServletResponse response) {
        return switch (staffLogin.login(body.email(), body.password())) {
            case StaffLoginService.Result.Authenticated authenticated -> {
                CurrentUser user = establishSession(new CurrentUser(authenticated.userId(), UserType.STAFF),
                        request, response);
                if (authenticated.mustChangePassword()) {
                    request.getSession().setAttribute(PasswordChangeFilter.MUST_CHANGE_PASSWORD, true);
                }
                yield new SignedIn(user.id(), user.type(), authenticated.mustChangePassword());
            }
            case StaffLoginService.Result.Failed _ -> throw new BadCredentialsException("Staff login failed");
            case StaffLoginService.Result.RateLimited limited -> throw new RateLimitedException(limited.retryAfter());
        };
    }

    /** 401 without a session: SecurityConfig requires authentication here. */
    @GetMapping("/me")
    SignedIn me(@AuthenticationPrincipal CurrentUser user, HttpSession session) {
        return new SignedIn(user.id(), user.type(),
                session.getAttribute(PasswordChangeFilter.MUST_CHANGE_PASSWORD) != null);
    }

    /** Staff only. Keeps this session and ends the user's others. */
    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@AuthenticationPrincipal CurrentUser user,
                                        @Valid @RequestBody PasswordChangeRequest body, HttpSession session) {
        passwordChange.change(user.id(), body.currentPassword(), body.newPassword(), session.getId());
        session.removeAttribute(PasswordChangeFilter.MUST_CHANGE_PASSWORD);
        return ResponseEntity.noContent().build();
    }

    private CurrentUser establishSession(CurrentUser user, HttpServletRequest request, HttpServletResponse response) {
        // Session fixation: never promote a session id that existed before this login.
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        // No authorities: permissions are never cached in the session.
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        // A new session gets a new CSRF token, as Spring Security's own login filters do.
        csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
        return user;
    }
}
