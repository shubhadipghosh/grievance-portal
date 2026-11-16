package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own profile. Deliberately not under /api/v1/auth,
 * which SecurityConfig leaves open to anonymous callers.
 */
@RestController
@RequestMapping("/api/v1/profile")
class ProfileController {

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    // app_user.full_name is VARCHAR(150). Locales are those category_translation is seeded in (V9).
    record ProfileUpdateRequest(@NotBlank @Size(max = 150) String fullName,
                                @NotNull @Pattern(regexp = "en|bn") String preferredLocale) {
    }

    @GetMapping
    ProfileService.Profile get(@AuthenticationPrincipal CurrentUser user) {
        return profiles.get(user.id());
    }

    @PutMapping
    ProfileService.Profile update(@AuthenticationPrincipal CurrentUser user,
                                  @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.update(user.id(), request.fullName(), request.preferredLocale());
    }
}
