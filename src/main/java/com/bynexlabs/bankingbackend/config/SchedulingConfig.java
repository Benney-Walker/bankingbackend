package com.bynexlabs.bankingbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Turns on @Scheduled jobs; currently the outbox retry job in OutboxPublisher.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
