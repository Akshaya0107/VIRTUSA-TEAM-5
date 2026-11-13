package com.example.reminder;

import com.example.reminder.entity.EmailAttemptStatus;
import com.example.reminder.service.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.net.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailDeliveryTest {
    private final JavaMailSender sender=mock(JavaMailSender.class);
    private final MimeMessage message=new MimeMessage(Session.getInstance(new Properties()));
    private final EmailWork work=new EmailWork(1L,UUID.randomUUID().toString(),1,3,"Test title","recipient@example.test","Learner","<script>alert(1)</script>",Instant.parse("2099-01-01T03:30:00Z"),"Asia/Kolkata",Instant.now());
    private SmtpEmailDelivery service() {
        when(sender.createMimeMessage()).thenReturn(message);
        ClassLoaderTemplateResolver resolver=new ClassLoaderTemplateResolver();resolver.setPrefix("templates/");resolver.setSuffix(".html");resolver.setTemplateMode("HTML");
        TemplateEngine templates=new org.thymeleaf.spring6.SpringTemplateEngine();templates.setTemplateResolver(resolver);
        return new SmtpEmailDelivery(sender,templates,"sender@example.test");
    }
    @Test void buildsPlainAndEscapedHtmlAlternativesAndReportsAcceptance() throws Exception {
        assertEquals(EmailAttemptStatus.SMTP_ACCEPTED,service().send(work).status());
        verify(sender).send(message);message.saveChanges();
        String html=findHtml(message);assertTrue(html.contains("&lt;script&gt;"));assertFalse(html.contains("<script>"));
    }
    @Test void authenticationFailureIsDefiniteAndDoesNotExposeRawError() {
        doThrow(new MailAuthenticationException("sensitive-provider-detail")).when(sender).send(any(MimeMessage.class));
        var result=service().send(work);assertEquals(EmailAttemptStatus.FAILED,result.status());assertFalse(result.safeReason().contains("sensitive-provider-detail"));
    }
    @Test void connectionFailureIsRetryableButTimeoutIsUncertain() {
        var service=service();doThrow(new MailSendException("connect",new ConnectException("refused"))).when(sender).send(any(MimeMessage.class));
        assertEquals(EmailAttemptStatus.FAILED,service.send(work).status());
        doThrow(new MailSendException("timeout",new SocketTimeoutException("late response"))).when(sender).send(any(MimeMessage.class));
        assertEquals(EmailAttemptStatus.UNKNOWN,service.send(work).status());
    }
    @Test void retryDelayIsExponentialAndCapped() {
        RetryPolicy p=new RetryPolicy(30,120);assertEquals(30,p.delay(1).toSeconds());assertEquals(60,p.delay(2).toSeconds());assertEquals(120,p.delay(3).toSeconds());assertEquals(120,p.delay(10).toSeconds());
    }
    private String findHtml(Part part) throws Exception {
        if(part.isMimeType("text/html")) return (String)part.getContent();
        if(part.getContent() instanceof Multipart m) for(int i=0;i<m.getCount();i++) {String value=findHtml(m.getBodyPart(i));if(!value.isEmpty())return value;}
        return "";
    }
}
