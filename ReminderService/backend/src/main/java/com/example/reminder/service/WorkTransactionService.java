package com.example.reminder.service;

import com.example.reminder.entity.*;
import com.example.reminder.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkTransactionService {
    public static final List<ReminderStatus> WAITING=List.of(ReminderStatus.PENDING,ReminderStatus.RETRY);
    private final ReminderRepository reminders;private final EmailLogRepository logs;private final RetryPolicy retry;
    private final long leaseSeconds;
    public WorkTransactionService(ReminderRepository reminders,EmailLogRepository logs,RetryPolicy retry,
                                  @Value("${app.scheduler.lease-seconds:120}") long leaseSeconds) {
        if(leaseSeconds<60) throw new IllegalArgumentException("Lease must be at least 60 seconds");
        this.reminders=reminders;this.logs=logs;this.retry=retry;this.leaseSeconds=leaseSeconds;
    }
    @Transactional public Optional<EmailWork> claim(Long id,Instant now) {
        String token=UUID.randomUUID().toString();
        if(reminders.claim(id,WAITING,token,now.plusSeconds(leaseSeconds),now)!=1) return Optional.empty();
        Reminder r=reminders.findById(id).orElseThrow();
        logs.saveAndFlush(new EmailLog(r,r.getAttemptCount(),token,now));
        return Optional.of(EmailWork.from(r,now));
    }
    @Transactional public boolean finish(EmailWork work,DeliveryResult result,Instant now) {
        Instant finished=now.isBefore(work.startedAt())?work.startedAt():now;
        ReminderStatus state; Instant next=null;
        if(result.status()==EmailAttemptStatus.SMTP_ACCEPTED) state=ReminderStatus.SENT;
        else if(result.status()==EmailAttemptStatus.FAILED && work.attemptNumber()<=work.maxRetries()) {
            state=ReminderStatus.RETRY;next=finished.plus(retry.delay(work.attemptNumber()));
        } else state=ReminderStatus.FAILED;
        if(reminders.finish(work.reminderId(),work.claimToken(),state,next,finished)!=1) return false;
        EmailLog log=logs.findByClaimToken(work.claimToken()).orElseThrow();
        switch(result.status()) {
            case SMTP_ACCEPTED -> log.markSmtpAccepted(finished);
            case FAILED -> log.markFailed(finished,result.safeReason());
            case UNKNOWN -> log.markUnknown(finished,result.safeReason());
            default -> throw new IllegalStateException("Invalid final result");
        }
        logs.saveAndFlush(log); return true;
    }
    @Transactional public void recoverExpired(Long id,Instant now) {
        Reminder r=reminders.findById(id).orElse(null);
        if(r==null||r.getStatus()!=ReminderStatus.PROCESSING||r.getLeaseUntil().isAfter(now)) return;
        EmailLog log=logs.findByClaimToken(r.getClaimToken()).orElseThrow();
        finish(EmailWork.from(r,log.getStartedAt()),DeliveryResult.unknown("Worker lease expired; SMTP outcome is uncertain. Automatic resend stopped."),now);
    }
}
