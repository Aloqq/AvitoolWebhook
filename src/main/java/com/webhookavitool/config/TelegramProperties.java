package com.webhookavitool.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

    private String botToken = "";
    private String chatId = "";
    private List<String> notifyChatIds = new ArrayList<>();

    /**
     * Если true — к настроенным получателям добавляются chat_id пользователей, накопленные опросом getUpdates.
     */
    private boolean testMode = false;

    /** Интервал опроса getUpdates в тестовом режиме (мс). */
    private long testCollectIntervalMs = 60_000L;

    /**
     * Объединяет {@code chat-id} и {@code notify-chat-ids} без дублей, порядок: сначала основной chat-id.
     */
    public List<String> resolveRecipientChatIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(chatId)) {
            ids.add(chatId.trim());
        }
        if (notifyChatIds != null) {
            for (String id : notifyChatIds) {
                if (StringUtils.hasText(id)) {
                    ids.add(id.trim());
                }
            }
        }
        return List.copyOf(ids);
    }
}
