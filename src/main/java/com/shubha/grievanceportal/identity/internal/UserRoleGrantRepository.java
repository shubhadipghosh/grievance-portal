package com.shubha.grievanceportal.identity.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleGrantRepository extends JpaRepository<UserRoleGrant, UUID> {

    List<UserRoleGrant> findByUserIdAndRevokedAtIsNull(UUID userId);

    @Query("""
            select g from UserRoleGrant g
            join fetch g.role
            left join fetch g.department
            left join fetch g.ward
            left join fetch g.grantedBy
            left join fetch g.revokedBy
            where g.user.id = :userId
            order by g.grantedAt, g.id""")
    List<UserRoleGrant> findWithDetailsByUserId(UUID userId);

    /** Row-locked, so two concurrent revocations cannot both pass the already-revoked check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from UserRoleGrant g join fetch g.user where g.id = :id")
    Optional<UserRoleGrant> findForUpdate(UUID id);

    // Suspension is reversible, so it gates on user status rather than revoking grants.
    // Native: a JPQL path through the nullable department/ward associations
    // risks an inner join that drops exactly the unscoped (NULL) grants.
    @Query(value = """
            select exists (
                select 1
                from user_role_grant g
                join role_permission rp on rp.role_id = g.role_id
                join app_user u on u.id = g.user_id
                where g.user_id = :userId
                  and u.status = 'ACTIVE'
                  and g.revoked_at is null
                  and rp.permission_code in (:codes)
                  and (g.department_id is null or g.department_id = :departmentId)
                  and (g.ward_id is null or g.ward_id = :wardId))
            """, nativeQuery = true)
    boolean hasPermissionInScope(@Param("userId") UUID userId,
                                 @Param("codes") Collection<String> codes,
                                 @Param("departmentId") UUID departmentId,
                                 @Param("wardId") UUID wardId);

    interface ScopeRow {
        UUID getDepartmentId();

        UUID getWardId();
    }

    // Same conditions as hasPermissionInScope, minus the scope match.
    @Query(value = """
            select distinct g.department_id as departmentId, g.ward_id as wardId
            from user_role_grant g
            join role_permission rp on rp.role_id = g.role_id
            join app_user u on u.id = g.user_id
            where g.user_id = :userId
              and u.status = 'ACTIVE'
              and g.revoked_at is null
              and rp.permission_code = :code
            """, nativeQuery = true)
    List<ScopeRow> findScopesWithPermission(@Param("userId") UUID userId, @Param("code") String code);

    // Same conditions again, for any permission at all.
    @Query(value = """
            select distinct g.department_id as departmentId, g.ward_id as wardId
            from user_role_grant g
            join app_user u on u.id = g.user_id
            where g.user_id = :userId
              and u.status = 'ACTIVE'
              and g.revoked_at is null
            """, nativeQuery = true)
    List<ScopeRow> findActiveScopes(@Param("userId") UUID userId);

    // Same scope match as hasPermissionInScope, by role instead of permission.
    @Query(value = """
            select distinct u.full_name
            from user_role_grant g
            join role r on r.id = g.role_id
            join app_user u on u.id = g.user_id
            where r.code = :roleCode
              and u.status = 'ACTIVE'
              and g.revoked_at is null
              and u.full_name is not null
              and (g.department_id is null or g.department_id = :departmentId)
              and (g.ward_id is null or g.ward_id = :wardId)
            order by u.full_name
            """, nativeQuery = true)
    List<String> findNamesWithRoleInScope(@Param("roleCode") String roleCode,
                                          @Param("departmentId") UUID departmentId,
                                          @Param("wardId") UUID wardId);

    interface StaffRow {
        UUID getId();

        String getFullName();
    }

    // Same conditions as hasPermissionInScope, for every staff user at once.
    @Query(value = """
            select distinct u.id as id, u.full_name as fullName
            from user_role_grant g
            join role_permission rp on rp.role_id = g.role_id
            join app_user u on u.id = g.user_id
            where rp.permission_code = :code
              and u.user_type = 'STAFF'
              and u.status = 'ACTIVE'
              and g.revoked_at is null
              and (g.department_id is null or g.department_id = :departmentId)
              and (g.ward_id is null or g.ward_id = :wardId)
            order by u.full_name
            """, nativeQuery = true)
    List<StaffRow> findStaffWithPermissionInScope(@Param("code") String code,
                                                  @Param("departmentId") UUID departmentId,
                                                  @Param("wardId") UUID wardId);
}
