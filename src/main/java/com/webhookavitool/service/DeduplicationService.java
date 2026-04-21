package com.webhookavitool.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeduplicationService {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final ConcurrentHashMap<String, Instant> lastSuccessfulSend = new ConcurrentHashMap<>();

    public boolean isDuplicateWithinWindow(String event, String message, String account) {
        String key = buildKey(event, message, account);
        Instant last = lastSuccessfulSend.get(key);
        if (last == null) {
            return false;
        }
        return Duration.between(last, Instant.now()).compareTo(WINDOW) < 0;
    }

    public void markTelegramSent(String event, String message, String account) {
        lastSuccessfulSend.put(buildKey(event, message, account), Instant.now());
        pruneExpired();
    }

    private void pruneExpired() {
        Instant cutoff = Instant.now().minus(WINDOW);
        lastSuccessfulSend.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    private static String buildKey(String event, String message, String account) {
        String acc = account == null ? "" : account;
        return event + "\u0001" + message + "\u0001" + acc;
    }
}
