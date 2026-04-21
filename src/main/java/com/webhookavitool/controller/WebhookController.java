package com.webhookavitool.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookavitool.dto.WebhookLogEntry;
import com.webhookavitool.dto.WebhookPayload;
import com.webhookavitool.service.WebhookAuthService;
import com.webhookavitool.service.WebhookFileLogger;
import com.webhookavitool.service.WebhookProcessingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final WebhookAuthService webhookAuthService;
    private final WebhookFileLogger webhookFileLogger;
    private final WebhookProcessingService webhookProcessingService;

    @PostMapping(path = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> webhook(
            @RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            jakarta.servlet.http.HttpServletRequest request) {

        String clientIp = resolveClientIp(request);
        String bearer = WebhookAuthService.extractBearerToken(authorization);
        boolean authorized = webhookAuthService.isAuthorized(bearer);

        JsonNode payloadNode = parsePayloadNode(body);

        if (!authorized) {
            webhookFileLogger.append(WebhookLogEntry.builder()
                    .timestampReceived(Instant.now())
                    .ip(clientIp)
                    .auth("failed")
                    .payload(payloadNode)
                    .processed(false)
                    .telegramSent(false)
                    .telegramError(null)
                    .build());
            return jsonError(HttpStatus.FORBIDDEN, "forbidden");
        }

        if (payloadNode == null) {
            webhookFileLogger.append(WebhookLogEntry.builder()
                    .timestampReceived(Instant.now())
                    .ip(clientIp)
                    .auth("ok")
                    .payload(buildInvalidBodyNode(body))
                    .processed(false)
                    .telegramSent(false)
                    .telegramError(null)
                    .build());
            return jsonError(HttpStatus.BAD_REQUEST, "invalid_json");
        }

        WebhookPayload payload;
        try {
            payload = objectMapper.treeToValue(payloadNode, WebhookPayload.class);
        } catch (Exception e) {
            webhookFileLogger.append(WebhookLogEntry.builder()
                    .timestampReceived(Instant.now())
                    .ip(clientIp)
                    .auth("ok")
                    .payload(payloadNode)
                    .processed(false)
                    .telegramSent(false)
                    .telegramError(null)
                    .build());
            return jsonError(HttpStatus.BAD_REQUEST, "invalid_payload");
        }

        Set<ConstraintViolation<WebhookPayload>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            webhookFileLogger.append(WebhookLogEntry.builder()
                    .timestampReceived(Instant.now())
                    .ip(clientIp)
                    .auth("ok")
                    .payload(payloadNode)
                    .processed(false)
                    .telegramSent(false)
                    .telegramError(null)
                    .build());
            return jsonError(HttpStatus.BAD_REQUEST, "validation_failed");
        }

        Instant receivedAt = Instant.now();
        webhookProcessingService.processAcceptedWebhook(payload, payloadNode, clientIp, receivedAt);
        log.info("Webhook accepted: event={}, ip={}", payload.getEvent(), clientIp);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "ok", true,
                        "queued", true,
                        "event", payload.getEvent()));
    }

    private static ResponseEntity<Map<String, Object>> jsonError(HttpStatus status, String error) {
        Map<String, Object> body = new HashMap<>();
        body.put("ok", false);
        body.put("error", error);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private JsonNode parsePayloadNode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode buildInvalidBodyNode(String body) {
        var node = objectMapper.createObjectNode();
        node.put("invalid_json", true);
        String preview = body.length() > 8000 ? body.substring(0, 8000) : body;
        node.put("raw_preview", preview);
        return node;
    }

    private static String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
