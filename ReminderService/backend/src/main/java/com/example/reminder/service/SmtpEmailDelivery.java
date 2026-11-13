package com.example.reminder.service;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class SmtpEmailDelivery implements EmailDelivery {
    private final JavaMailSender sender;private final TemplateEngine templates;private final String from;
    public SmtpEmailDelivery(JavaMailSender sender,TemplateEngine templates,@Value("${app.mail.from:noreply@example.test}") String from) {
        this.sender=sender;this.templates=templates;this.from=from;
    }
    @Override public DeliveryResult send(EmailWork work) {
        MimeMessage message;
        try {
            message=sender.createMimeMessage();
            MimeMessageHelper helper=new MimeMessageHelper(message,true,"UTF-8");
            helper.setFrom(from);helper.setTo(work.recipientEmail());helper.setSubject(work.title());
            String scheduled=DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm z").withLocale(Locale.ENGLISH)
                    .withZone(ZoneId.of(work.timeZone())).format(work.scheduledAt());
            Context context=new Context(Locale.ENGLISH);
            context.setVariable("name",work.recipientName()==null?"there":work.recipientName());
            context.setVariable("title",work.title());context.setVariable("message",work.message());context.setVariable("scheduled",scheduled);
            helper.setText(work.title()+"\n\n"+work.message()+"\n\nScheduled: "+scheduled,templates.process("reminder-email",context));
            message.setHeader("X-Reminder-Attempt",work.claimToken());
        } catch(Exception e) { return DeliveryResult.failed("Email construction or template rendering failed"); }
        try { sender.send(message);return DeliveryResult.accepted(); }
        catch(MailAuthenticationException e) { return DeliveryResult.failed("SMTP authentication failed; check provider credentials"); }
        catch(MailException e) {
            var causes=new ArrayDeque<Throwable>();causes.add(e);Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            boolean definite=false;
            while(!causes.isEmpty() && seen.size()<30) {
                Throwable t=causes.removeFirst();if(!seen.add(t)) continue;
                if(t instanceof ConnectException || t instanceof UnknownHostException) definite=true;
                if(t instanceof SendFailedException s) {
                    if(s.getValidSentAddresses()!=null && s.getValidSentAddresses().length>0) return DeliveryResult.unknown("SMTP reported a partial send; acceptance is uncertain");
                    if(s.getInvalidAddresses()!=null && s.getInvalidAddresses().length>0) definite=true;
                }
                if(t instanceof org.eclipse.angus.mail.smtp.SMTPSendFailedException s && s.getReturnCode()>=400 && s.getReturnCode()<600) definite=true;
                if(t instanceof org.eclipse.angus.mail.smtp.SMTPAddressFailedException) definite=true;
                if(t.getCause()!=null) causes.add(t.getCause());
                if(t instanceof MessagingException m && m.getNextException()!=null) causes.add(m.getNextException());
                if(t instanceof MailSendException m) Collections.addAll(causes,m.getMessageExceptions());
            }
            return definite?DeliveryResult.failed("SMTP connection or explicit recipient/submission rejection"):
                    DeliveryResult.unknown("SMTP outcome uncertain after transport failure; automatic resend stopped");
        }
    }
}
