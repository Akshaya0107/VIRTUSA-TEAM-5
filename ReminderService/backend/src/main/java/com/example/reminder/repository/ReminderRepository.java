package com.example.reminder.repository;

import com.example.reminder.entity.Reminder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    Page<Reminder> findAllByUser_IdAndDeletedAtIsNull(Long userId, Pageable pageable);

    Optional<Reminder> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);

    @org.springframework.data.jpa.repository.Query("select r.status, count(r) from Reminder r where r.user.id=:owner and r.deletedAt is null group by r.status")
    java.util.List<Object[]> statusCounts(@org.springframework.data.repository.query.Param("owner") Long owner);

    java.util.List<Reminder> findTop5ByUser_IdAndDeletedAtIsNullAndStatusInOrderByNextAttemptAtAscIdAsc(Long owner, java.util.Collection<com.example.reminder.entity.ReminderStatus> statuses);

    @org.springframework.data.jpa.repository.Query("select r.id from Reminder r where r.deletedAt is null and r.status in :statuses and r.nextAttemptAt<=:now order by r.nextAttemptAt,r.id")
    java.util.List<Long> dueIds(@org.springframework.data.repository.query.Param("statuses") java.util.Collection<com.example.reminder.entity.ReminderStatus> statuses,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now, Pageable page);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true,flushAutomatically=true)
    @org.springframework.data.jpa.repository.Query("update Reminder r set r.status=com.example.reminder.entity.ReminderStatus.PROCESSING, r.attemptCount=r.attemptCount+1, r.nextAttemptAt=null, r.claimToken=:token, r.leaseUntil=:lease, r.version=r.version+1, r.updatedAt=:now where r.id=:id and r.deletedAt is null and r.status in :statuses and r.nextAttemptAt<=:now and r.attemptCount<=r.maxRetries")
    int claim(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<com.example.reminder.entity.ReminderStatus> statuses,
            @org.springframework.data.repository.query.Param("token") String token,
            @org.springframework.data.repository.query.Param("lease") java.time.Instant lease,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true,flushAutomatically=true)
    @org.springframework.data.jpa.repository.Query("update Reminder r set r.status=:status, r.nextAttemptAt=:next, r.claimToken=null, r.leaseUntil=null, r.version=r.version+1, r.updatedAt=:now where r.id=:id and r.status=com.example.reminder.entity.ReminderStatus.PROCESSING and r.claimToken=:token")
    int finish(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("token") String token,
            @org.springframework.data.repository.query.Param("status") com.example.reminder.entity.ReminderStatus status,
            @org.springframework.data.repository.query.Param("next") java.time.Instant next,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Query("select r.id from Reminder r where r.status=com.example.reminder.entity.ReminderStatus.PROCESSING and r.leaseUntil<=:now order by r.leaseUntil,r.id")
    java.util.List<Long> expiredIds(@org.springframework.data.repository.query.Param("now") java.time.Instant now, Pageable page);
}
