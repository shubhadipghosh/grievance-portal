package com.shubha.grievanceportal.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, UUID> {

    List<UserRoleGrant> findByUserIdAndRevokedAtIsNull(UUID userId);
}
