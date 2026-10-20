package com.shubha.grievanceportal.identity.internal;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Append-only: extends the bare {@link Repository} so no delete or bulk
 * methods are exposed. Only insert and read.
 */
public interface UserStatusHistoryRepository extends Repository<UserStatusHistory, UUID> {

    UserStatusHistory save(UserStatusHistory entry);

    // idx_user_status_hist
    List<UserStatusHistory> findByUserIdOrderByOccurredAt(UUID userId);
}
