package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByPhone(String phone);

    Optional<AppUser> findByEmail(String email);
}
