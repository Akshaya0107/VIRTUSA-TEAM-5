package com.example.reminder.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {
    private final long base,cap;
    public RetryPolicy(@Value("${app.retry.base-seconds:30}") long base,@Value("${app.retry.cap-seconds:3600}") long cap) {
        if(base<1||cap<base||cap>86400) throw new IllegalArgumentException("Invalid retry delay configuration");
        this.base=base;this.cap=cap;
    }
    public Duration delay(int attempt) { return Duration.ofSeconds(Math.min(cap,base*(1L<<Math.min(20,Math.max(0,attempt-1))))); }
}
