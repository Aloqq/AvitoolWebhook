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
}
