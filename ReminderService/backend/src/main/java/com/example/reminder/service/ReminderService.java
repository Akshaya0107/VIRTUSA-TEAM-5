package com.example.reminder.service;

import com.example.reminder.dto.ApiModels.*;
import com.example.reminder.entity.*;
import com.example.reminder.exception.ApiException;
import com.example.reminder.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {
    private final ReminderRepository reminders; private final UserRepository users; private final EmailLogRepository logs;
    private final Clock clock; private final int retries;
    public ReminderService(ReminderRepository reminders,UserRepository users,EmailLogRepository logs,Clock clock,
                           @Value("${app.retry.max-retries:3}") int retries) {
        this.reminders=reminders;this.users=users;this.logs=logs;this.clock=clock;this.retries=retries;
        if(retries<0||retries>10) throw new IllegalStateException("Maximum retries must be 0-10");
    }
    @Transactional public ReminderView create(Long owner,ReminderRequest r) {
        checkSchedule(r); User user=users.findById(owner).orElseThrow(()->notFound());
        return ReminderView.from(reminders.saveAndFlush(new Reminder(user,r.title(),r.recipientEmail(),r.recipientName(),r.message(),r.scheduledAt(),r.timeZone(),retries)));
    }
    @Transactional(readOnly=true) public PageView<ReminderView> list(Long owner,int page,int size) {
        return PageView.from(reminders.findAllByUser_IdAndDeletedAtIsNull(owner,page(page,size,"scheduledAt",false)).map(ReminderView::from));
    }
    @Transactional(readOnly=true) public ReminderView get(Long owner,Long id) { return ReminderView.from(owned(owner,id)); }
    @Transactional public ReminderView update(Long owner,Long id,ReminderRequest r) {
        checkSchedule(r); Reminder entity=owned(owner,id);
        if(r.version()==null) throw new ApiException(HttpStatus.BAD_REQUEST,"version is required when editing");
        if(!r.version().equals(entity.getVersion())) throw new ApiException(HttpStatus.CONFLICT,"Reminder changed; reload before editing");
        entity.updateDetails(r.title(),r.recipientEmail(),r.recipientName(),r.message(),r.scheduledAt(),r.timeZone());
        return ReminderView.from(reminders.saveAndFlush(entity));
    }
    @Transactional public ReminderView cancel(Long owner,Long id) {
        Reminder r=owned(owner,id);r.cancel();return ReminderView.from(reminders.saveAndFlush(r));
    }
    @Transactional public void delete(Long owner,Long id) { owned(owner,id).softDelete(clock.instant()); }
    @Transactional(readOnly=true) public PageView<LogView> history(Long owner,Long reminder,int page,int size) {
        var paging=page(page,size,"startedAt",true);
        if(reminder!=null && reminders.findById(reminder).filter(r->r.getUser().getId().equals(owner)).isEmpty()) throw notFound();
        return PageView.from((reminder==null?logs.findAllByReminder_User_Id(owner,paging):logs.findAllByReminder_IdAndReminder_User_Id(reminder,owner,paging)).map(LogView::from));
    }
    @Transactional(readOnly=true) public DashboardView dashboard(Long owner) {
        EnumMap<ReminderStatus,Long> counts=new EnumMap<>(ReminderStatus.class);
        for(Object[] row:reminders.statusCounts(owner)) counts.put((ReminderStatus)row[0],(Long)row[1]);
        var upcoming=reminders.findTop5ByUser_IdAndDeletedAtIsNullAndStatusInOrderByNextAttemptAtAscIdAsc(owner,List.of(ReminderStatus.PENDING,ReminderStatus.RETRY)).stream().map(ReminderView::from).toList();
        var recent=logs.findAllByReminder_User_Id(owner,PageRequest.of(0,5,Sort.by(Sort.Direction.DESC,"startedAt","id"))).stream().map(LogView::from).toList();
        return new DashboardView(counts.values().stream().mapToLong(Long::longValue).sum(),n(counts,ReminderStatus.PENDING),n(counts,ReminderStatus.PROCESSING),n(counts,ReminderStatus.SENT),n(counts,ReminderStatus.RETRY),n(counts,ReminderStatus.FAILED),n(counts,ReminderStatus.CANCELLED),upcoming,recent);
    }
    private long n(Map<ReminderStatus,Long> c,ReminderStatus s) { return c.getOrDefault(s,0L); }
    private Reminder owned(Long owner,Long id) { return reminders.findByIdAndUser_IdAndDeletedAtIsNull(id,owner).orElseThrow(()->notFound()); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"Record not found"); }
    private void checkSchedule(ReminderRequest r) { ZoneId.of(r.timeZone()); if(!r.scheduledAt().isAfter(clock.instant())) throw new ApiException(HttpStatus.BAD_REQUEST,"Scheduled time must be in the future"); }
    private Pageable page(int page,int size,String field,boolean desc) {
        if(page<0 || size<1 || size>100) throw new ApiException(HttpStatus.BAD_REQUEST,"Use page >= 0 and size between 1 and 100");
        return PageRequest.of(page,size,Sort.by(desc?Sort.Direction.DESC:Sort.Direction.ASC,field,"id"));
    }
}
