package com.webhookavitool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "webhook")
public class WebhookProperties {

    /**
     * Expected raw token value (without "Bearer " prefix). Compared with Authorization header.
     */
    private String token = "";

    /**
     * If false, every accepted webhook may trigger Telegram (no duplicate suppression).
     */
    private boolean deduplicationEnabled = true;

    /**
     * Suppress Telegram for same event + message + account within this many seconds after last successful send.
     */
    private int deduplicationWindowSeconds = 60;
}
