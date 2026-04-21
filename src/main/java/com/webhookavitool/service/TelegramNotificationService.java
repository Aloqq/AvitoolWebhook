package com.webhookavitool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookavitool.config.TelegramProperties;
import com.webhookavitool.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private static final String TELEGRAM_API = "https://api.telegram.org/bot%s/sendMessage";

    private final RestTemplate telegramRestTemplate;
    private final TelegramProperties telegramProperties;
    private final TelegramTestChatStore telegramTestChatStore;
    private final ObjectMapper objectMapper;

    /**
     * @return {@code null} если всем получателям отправка успешна, иначе краткое описание ошибок
     */
    public String send(WebhookPayload payload) {
        String token = telegramProperties.getBotToken();
        LinkedHashSet<String> chatIds = new LinkedHashSet<>(telegramProperties.resolveRecipientChatIds());
        if (telegramProperties.isTestMode()) {
            chatIds.addAll(telegramTestChatStore.snapshotChatIds());
        }

        if (!StringUtils.hasText(token) || chatIds.isEmpty()) {
            String hint = telegramProperties.isTestMode()
                    ? " В тестовом режиме дождитесь сбора chat_id через getUpdates или отключите test-mode."
                    : "";
            return "Telegram is not configured (задайте TELEGRAM_BOT_TOKEN и TELEGRAM_CHAT_ID в окружении или telegram.* в config/application-secrets.yml)."
                    + hint;
        }

        String text = buildMessage(payload);
        String url = TELEGRAM_API.formatted(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<String> errors = new ArrayList<>();
        for (String chatId : chatIds) {
            String err = sendToChat(url, headers, chatId, text);
            if (err != null) {
                errors.add(chatId + ": " + err);
            }
        }
        return errors.isEmpty() ? null : String.join("; ", errors);
    }

    private String sendToChat(String url, HttpHeaders headers, String chatId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "MarkdownV2");
        body.put("disable_web_page_preview", true);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = telegramRestTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return "HTTP " + response.getStatusCode();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("ok").asBoolean(false)) {
                return root.path("description").asText("Unknown Telegram API error");
            }
            return null;
        } catch (RestClientException e) {
            log.warn("Telegram request failed for chat {}: {}", chatId, e.getMessage());
            return "request failed: " + e.getMessage();
        } catch (Exception e) {
            log.warn("Telegram response parse failed for chat {}: {}", chatId, e.getMessage());
            return "parse failed: " + e.getMessage();
        }
    }

    /**
     * MarkdownV2: жирный заголовок + блок {@code ```log ... ```}.
     * Ограждение — три обычных backtick без обратного слэша; ключи и значения внутри блока полностью
     * {@link #escapeMarkdownV2}, иначе {@code _} даёт курсив и «съедает» подчёркивания.
     */
    private static String buildMessage(WebhookPayload p) {
        StringBuilder sb = new StringBuilder();
        sb.append("*🚨 ").append(escapeMarkdownV2(orDash(p.getEvent()))).append("*\n\n");
        sb.append("```log\n");
        fenceLine(sb, "event_id", p.getEventId());
        fenceLine(sb, "event", p.getEvent());
        fenceLine(sb, "level", p.getLevel());
        fenceLine(sb, "status", p.getStatus());
        fenceLine(sb, "message", p.getMessage());
        fenceLine(sb, "error_code", p.getErrorCode());
        fenceLine(sb, "account", p.getAccount());
        fenceLine(sb, "task_id", p.getTaskId() != null ? p.getTaskId().toString() : null);
        fenceLine(sb, "project", p.getProject());
        fenceLine(sb, "host", p.getHost());
        fenceLine(sb, "timestamp", p.getTimestamp());
        sb.append("```");

        String out = sb.toString();
        if (out.length() > 4096) {
            return out.substring(0, 4080) + "\n```";
        }
        return out;
    }

    private static void fenceLine(StringBuilder sb, String key, String raw) {
        sb.append(escapeMarkdownV2(key)).append(": '").append(inFenceValue(raw)).append("'\n");
    }

    /** Подготовка значения поля внутри fenced pre (без конфликта с закрытием блока). */
    private static String inFenceValue(String raw) {
        String v = orDash(raw).replace('\r', ' ').replace('\n', ' ');
        v = v.replace("```", "'''");
        return escapeMarkdownV2(v);
    }

    /** Экранирование для текста вне code/pre (заголовок жирным). */
    private static String escapeMarkdownV2(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("|", "\\|")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }

    private static String orDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
