package com.example.reminder.service;

import com.example.reminder.entity.EmailAttemptStatus;

public record DeliveryResult(EmailAttemptStatus status, String safeReason) {
    public DeliveryResult {
        if(status==null || status==EmailAttemptStatus.STARTED) throw new IllegalArgumentException("A final outcome is required");
        if(status!=EmailAttemptStatus.SMTP_ACCEPTED && (safeReason==null || safeReason.isBlank())) throw new IllegalArgumentException("A safe reason is required");
    }
    public static DeliveryResult accepted() { return new DeliveryResult(EmailAttemptStatus.SMTP_ACCEPTED,null); }
    public static DeliveryResult failed(String reason) { return new DeliveryResult(EmailAttemptStatus.FAILED,reason); }
    public static DeliveryResult unknown(String reason) { return new DeliveryResult(EmailAttemptStatus.UNKNOWN,reason); }
}
