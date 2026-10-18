package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.Ward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WardRepository extends JpaRepository<Ward, UUID> {

    Optional<Ward> findByCode(String code);
}
