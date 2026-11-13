package com.example.reminder.repository;

import com.example.reminder.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    Page<EmailLog> findAllByReminder_IdAndReminder_User_Id(
            Long reminderId, Long userId, Pageable pageable);

    Page<EmailLog> findAllByReminder_User_Id(Long userId, Pageable pageable);

    java.util.Optional<EmailLog> findByClaimToken(String claimToken);
}
