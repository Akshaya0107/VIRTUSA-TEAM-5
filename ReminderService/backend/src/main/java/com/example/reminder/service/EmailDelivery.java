package com.example.reminder.service;

public interface EmailDelivery {
    DeliveryResult send(EmailWork work);
}
