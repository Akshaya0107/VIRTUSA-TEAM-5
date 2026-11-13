package com.example.reminder.config;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
