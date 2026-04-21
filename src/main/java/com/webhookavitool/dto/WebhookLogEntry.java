package com.webhookavitool.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class WebhookLogEntry {

    Instant timestampReceived;
    String ip;
    String auth;
    JsonNode payload;
    boolean processed;
    boolean telegramSent;
    String telegramError;
}
