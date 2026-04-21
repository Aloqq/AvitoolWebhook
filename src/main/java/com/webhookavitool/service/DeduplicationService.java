package com.webhookavitool.service;

import com.webhookavitool.config.WebhookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final WebhookProperties webhookProperties;

    private final ConcurrentHashMap<String, Instant> lastSuccessfulSend = new ConcurrentHashMap<>();

    public boolean isDuplicateWithinWindow(String event, String message, String account) {
        if (!webhookProperties.isDeduplicationEnabled()) {
            return false;
        }
        Duration window = windowDuration();
        String key = buildKey(event, message, account);
        Instant last = lastSuccessfulSend.get(key);
        if (last == null) {
            return false;
        }
        return Duration.between(last, Instant.now()).compareTo(window) < 0;
    }

    public void markTelegramSent(String event, String message, String account) {
        if (!webhookProperties.isDeduplicationEnabled()) {
            return;
        }
        lastSuccessfulSend.put(buildKey(event, message, account), Instant.now());
        pruneExpired();
    }

    private void pruneExpired() {
        Instant cutoff = Instant.now().minus(windowDuration());
        lastSuccessfulSend.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    private Duration windowDuration() {
        int s = webhookProperties.getDeduplicationWindowSeconds();
        if (s < 1) {
            s = 1;
        }
        return Duration.ofSeconds(s);
    }

    private static String buildKey(String event, String message, String account) {
        String acc = account == null ? "" : account;
        return event + "\u0001" + message + "\u0001" + acc;
    }
}
