package com.webhookavitool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.webhookavitool.dto.WebhookLogEntry;
import com.webhookavitool.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingService {

    private final DeduplicationService deduplicationService;
    private final TelegramNotificationService telegramNotificationService;
    private final WebhookFileLogger webhookFileLogger;

    @Async
    public void processAcceptedWebhook(WebhookPayload payload, JsonNode rawPayload, String clientIp,
                                         Instant timestampReceived) {
        boolean processed = true;
        boolean telegramSent = false;
        String telegramError = null;

        try {
            boolean duplicate = deduplicationService.isDuplicateWithinWindow(
                    payload.getEvent(), payload.getMessage(), payload.getAccount());

            if (duplicate) {
                telegramSent = false;
                telegramError = null;
            } else {
                telegramError = telegramNotificationService.send(payload);
                if (telegramError == null) {
                    telegramSent = true;
                    deduplicationService.markTelegramSent(
                            payload.getEvent(), payload.getMessage(), payload.getAccount());
                } else {
                    telegramSent = false;
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error while processing webhook", e);
            processed = false;
            telegramSent = false;
            telegramError = e.getMessage();
        }

        WebhookLogEntry entry = WebhookLogEntry.builder()
                .timestampReceived(timestampReceived)
                .ip(clientIp)
                .auth("ok")
                .payload(rawPayload)
                .processed(processed)
                .telegramSent(telegramSent)
                .telegramError(telegramError)
                .build();

        webhookFileLogger.append(entry);
    }
}
