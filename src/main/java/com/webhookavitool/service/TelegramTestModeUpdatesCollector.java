package com.webhookavitool.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookavitool.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * В тестовом режиме периодически вызывает getUpdates и запоминает chat_id отправителей сообщений боту.
 * <p>
 * Важно: если у этого же бота настроен webhook у другого сервиса, getUpdates обычно не вернёт апдейты.
 * Для автосбора используйте отдельного тестового бота или снимите webhook.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "test-mode", havingValue = "true")
public class TelegramTestModeUpdatesCollector {

    private static final String GET_UPDATES = "https://api.telegram.org/bot%s/getUpdates?timeout=0";

    private final TelegramProperties telegramProperties;
    private final TelegramTestChatStore telegramTestChatStore;
    private final RestTemplate telegramRestTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${telegram.test-collect-interval-ms:60000}")
    public void pollUpdates() {
        String token = telegramProperties.getBotToken();
        if (!StringUtils.hasText(token)) {
            return;
        }

        long last = telegramTestChatStore.getLastAcknowledgedUpdateId();
        String url = GET_UPDATES.formatted(token);
        if (last > 0) {
            url = url + "&offset=" + (last + 1);
        }

        try {
            ResponseEntity<String> response = telegramRestTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.debug("getUpdates non-2xx: {}", response.getStatusCode());
                return;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("ok").asBoolean(false)) {
                log.debug("getUpdates ok=false: {}", root.path("description").asText());
                return;
            }
            JsonNode results = root.path("result");
            if (!results.isArray() || results.isEmpty()) {
                return;
            }

            long maxUpdateId = last;
            List<String> discovered = new ArrayList<>();
            for (JsonNode upd : results) {
                long uid = upd.path("update_id").asLong(0);
                if (uid > maxUpdateId) {
                    maxUpdateId = uid;
                }
                String chatId = extractChatId(upd);
                if (StringUtils.hasText(chatId)) {
                    discovered.add(chatId);
                }
            }

            telegramTestChatStore.recordUpdates(maxUpdateId, discovered);
            if (!discovered.isEmpty()) {
                log.info("Telegram test-mode: collected {} new chat id(s), last update_id={}", discovered.size(), maxUpdateId);
            }
        } catch (RestClientException e) {
            log.debug("getUpdates request failed: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("getUpdates parse failed: {}", e.getMessage());
        }
    }

    private static String extractChatId(JsonNode upd) {
        JsonNode msg = upd.path("message");
        if (msg.isMissingNode() || msg.isNull()) {
            msg = upd.path("edited_message");
        }
        if (msg.isMissingNode() || msg.isNull()) {
            msg = upd.path("channel_post");
        }
        if (msg.isMissingNode() || msg.isNull()) {
            JsonNode cq = upd.path("callback_query");
            if (!cq.isMissingNode() && !cq.isNull()) {
                msg = cq.path("message");
            }
        }
        if (msg.isMissingNode() || msg.isNull()) {
            return null;
        }
        JsonNode chat = msg.path("chat");
        if (chat.isMissingNode() || chat.isNull()) {
            return null;
        }
        if (chat.path("id").isIntegralNumber()) {
            return String.valueOf(chat.path("id").longValue());
        }
        return chat.path("id").asText(null);
    }
}
