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
     * If false (default), every accepted POST may trigger Telegram. Set true to suppress repeats
     * for the same event + message + account within deduplicationWindowSeconds.
     */
    private boolean deduplicationEnabled = false;

    /**
     * Suppress Telegram for same event + message + account within this many seconds after last successful send.
     */
    private int deduplicationWindowSeconds = 60;
}
