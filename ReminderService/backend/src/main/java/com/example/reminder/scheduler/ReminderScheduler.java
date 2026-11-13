package com.example.reminder.scheduler;

import com.example.reminder.repository.ReminderRepository;
import com.example.reminder.service.*;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.scheduler.enabled",havingValue="true",matchIfMissing=true)
public class ReminderScheduler {
    private final ReminderRepository reminders;private final WorkTransactionService transactions;
    private final EmailDelivery delivery;private final Clock clock;
    private final org.slf4j.Logger logger=org.slf4j.LoggerFactory.getLogger(getClass());
    private volatile boolean stopping;
    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void shutdown() { stopping=true; }
    public ReminderScheduler(ReminderRepository reminders,WorkTransactionService transactions,EmailDelivery delivery,Clock clock) {
        this.reminders=reminders;this.transactions=transactions;this.delivery=delivery;this.clock=clock;
    }
    @Scheduled(fixedDelayString="${app.scheduler.poll-ms:10000}")
    public void tick() {
        if(stopping) return;
        var batch=PageRequest.of(0,50);
        for(Long id:reminders.expiredIds(clock.instant(),batch)) {
            if(stopping) break;
            try { transactions.recoverExpired(id,clock.instant()); } catch(RuntimeException e) { logger.error("Lease recovery failed for reminder {} ({})",id,e.getClass().getSimpleName()); }
        }
        for(Long id:reminders.dueIds(WorkTransactionService.WAITING,clock.instant(),batch)) {
            if(stopping) break;
            try {
                var claimed=transactions.claim(id,clock.instant());
                if(claimed.isEmpty()) continue;
                var work=claimed.get(); DeliveryResult result;
                try { result=delivery.send(work); }
                catch(RuntimeException e) { result=DeliveryResult.unknown("Unexpected sender failure; submission outcome uncertain"); }
                transactions.finish(work,result,clock.instant());
            } catch(RuntimeException e) { logger.error("Worker failed for reminder {} ({}); lease recovery will handle unfinished work",id,e.getClass().getSimpleName()); }
        }
    }
}
