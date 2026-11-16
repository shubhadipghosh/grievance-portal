package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The signed-in user's own details. Only name and language are editable here:
 * phone and email are login identities and change through their own flows.
 */
@Service
class ProfileService {

    private final AppUserRepository users;

    ProfileService(AppUserRepository users) {
        this.users = users;
    }

    /** fullName is null for a citizen who has not given one yet. */
    record Profile(UserType type, String fullName, String phone, String email, String preferredLocale) {
    }

    @Transactional(readOnly = true)
    Profile get(UUID userId) {
        return toProfile(users.findById(userId).orElseThrow());
    }

    @Transactional
    Profile update(UUID userId, String fullName, String preferredLocale) {
        AppUser user = users.findById(userId).orElseThrow();
        user.setFullName(fullName.strip());
        user.setPreferredLocale(preferredLocale);
        return toProfile(user);
    }

    private static Profile toProfile(AppUser user) {
        return new Profile(user.getUserType(), user.getFullName(), user.getPhone(), user.getEmail(),
                user.getPreferredLocale());
    }
}
