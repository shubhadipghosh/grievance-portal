package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);
}
