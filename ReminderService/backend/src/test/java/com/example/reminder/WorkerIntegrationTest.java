package com.example.reminder;

import com.example.reminder.entity.*;
import com.example.reminder.repository.*;
import com.example.reminder.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class WorkerIntegrationTest {
    @Autowired UserRepository users;
    @Autowired ReminderRepository reminders;
    @Autowired EmailLogRepository logs;
    @Autowired WorkTransactionService tx;
    private Reminder due(Instant now,int retries) {
        var user=users.saveAndFlush(new User(UUID.randomUUID()+"@example.test","synthetic-hash","Worker test","UTC"));
        return reminders.saveAndFlush(new Reminder(user,"Due reminder","recipient@example.test",null,"Fixture",now.minusSeconds(1),"UTC",retries));
    }
    @Test void atomicClaimAllowsOnlyOneWorkerAndOutcomeIsIdempotent() throws Exception {
        Instant now=Instant.now();Reminder r=due(now,3);var pool=Executors.newFixedThreadPool(4);
        try {
            CountDownLatch ready=new CountDownLatch(4),start=new CountDownLatch(1);
            List<Future<Optional<EmailWork>>> futures=new ArrayList<>();
            for(int i=0;i<4;i++) futures.add(pool.submit(()->{ready.countDown();start.await();return tx.claim(r.getId(),now);}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            List<EmailWork> claimed=new ArrayList<>();for(var f:futures) f.get(15,TimeUnit.SECONDS).ifPresent(claimed::add);
            assertEquals(1,claimed.size());EmailWork work=claimed.get(0);
            assertTrue(tx.finish(work,DeliveryResult.accepted(),now.plusSeconds(1)));
            assertFalse(tx.finish(work,DeliveryResult.accepted(),now.plusSeconds(2)));
            assertEquals(ReminderStatus.SENT,reminders.findById(r.getId()).orElseThrow().getStatus());
            assertEquals(EmailAttemptStatus.SMTP_ACCEPTED,logs.findByClaimToken(work.claimToken()).orElseThrow().getStatus());
        } finally {pool.shutdownNow();}
    }
    @Test void retriesDefiniteFailuresUntilConfiguredBudgetIsExhausted() {
        Instant now=Instant.now();Reminder r=due(now,2);
        for(int attempt=1;attempt<=3;attempt++) {
            EmailWork work=tx.claim(r.getId(),now).orElseThrow();assertEquals(attempt,work.attemptNumber());
            assertTrue(tx.finish(work,DeliveryResult.failed("Synthetic connection failure"),now));
            Reminder state=reminders.findById(r.getId()).orElseThrow();
            if(attempt<3) {assertEquals(ReminderStatus.RETRY,state.getStatus());assertTrue(tx.claim(r.getId(),now).isEmpty());now=state.getNextAttemptAt();}
            else {assertEquals(ReminderStatus.FAILED,state.getStatus());assertNull(state.getNextAttemptAt());assertEquals(2,state.getRetryCount());}
        }
        assertTrue(tx.claim(r.getId(),now.plusSeconds(10000)).isEmpty());
    }
    @Test void expiredLeaseBecomesUnknownAndLateWorkerCannotOverwriteIt() {
        Instant now=Instant.now();Reminder r=due(now,3);EmailWork work=tx.claim(r.getId(),now).orElseThrow();
        tx.recoverExpired(r.getId(),now.plusSeconds(121));
        assertEquals(ReminderStatus.FAILED,reminders.findById(r.getId()).orElseThrow().getStatus());
        assertEquals(EmailAttemptStatus.UNKNOWN,logs.findByClaimToken(work.claimToken()).orElseThrow().getStatus());
        assertFalse(tx.finish(work,DeliveryResult.accepted(),now.plusSeconds(122)));
        assertTrue(tx.claim(r.getId(),now.plusSeconds(10000)).isEmpty());
    }
    @Test void cancelledAndFutureRemindersCannotBeClaimed() {
        Instant now=Instant.now();Reminder cancelled=due(now,3);cancelled.cancel();reminders.saveAndFlush(cancelled);
        assertTrue(tx.claim(cancelled.getId(),now).isEmpty());
        Reminder future=due(now.plusSeconds(3600),3);assertTrue(tx.claim(future.getId(),now).isEmpty());
    }
}
