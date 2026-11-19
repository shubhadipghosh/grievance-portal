package com.shubha.grievanceportal.identity.internal;

import com.shubha.grievanceportal.identity.AppUser;
import com.shubha.grievanceportal.identity.UserType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByPhone(String phone);

    Optional<AppUser> findByEmail(String email);

    /** Row-locked, so concurrent status changes serialize and each one sees the true from-status. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findForUpdate(UUID id);

    Page<AppUser> findByUserType(UserType type, Pageable pageable);

    /** pattern: lower-case, already wrapped in % and with !, % and _ escaped by a !. */
    @Query("""
            select u from AppUser u
            where u.userType = :type
              and (lower(u.fullName) like :pattern escape '!'
                   or lower(u.email) like :pattern escape '!'
                   or u.phone like :pattern escape '!')""")
    Page<AppUser> search(UserType type, String pattern, Pageable pageable);
}
