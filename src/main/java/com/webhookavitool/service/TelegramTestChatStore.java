package com.webhookavitool.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Сохраняет chat_id из getUpdates для тестового режима рассылки.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramTestChatStore {

    private static final Path STATE_PATH = Path.of("logs", "telegram_test_chats.json");

    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();

    private long lastAcknowledgedUpdateId;
    private final LinkedHashSet<String> chatIds = new LinkedHashSet<>();

    @PostConstruct
    void load() {
        if (!Files.isRegularFile(STATE_PATH)) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(STATE_PATH);
            State state = objectMapper.readValue(bytes, State.class);
            if (state.getChatIds() != null) {
                chatIds.addAll(state.getChatIds());
            }
            lastAcknowledgedUpdateId = Math.max(0, state.getLastAcknowledgedUpdateId());
        } catch (IOException e) {
            log.warn("Could not load {}, starting empty: {}", STATE_PATH, e.getMessage());
        }
    }

    public long getLastAcknowledgedUpdateId() {
        lock.lock();
        try {
            return lastAcknowledgedUpdateId;
        } finally {
            lock.unlock();
        }
    }

    public Set<String> snapshotChatIds() {
        lock.lock();
        try {
            return Set.copyOf(chatIds);
        } finally {
            lock.unlock();
        }
    }

    public void recordUpdates(long maxUpdateId, Collection<String> discoveredChatIds) {
        if (maxUpdateId <= 0 && (discoveredChatIds == null || discoveredChatIds.isEmpty())) {
            return;
        }
        lock.lock();
        try {
            if (discoveredChatIds != null) {
                for (String id : discoveredChatIds) {
                    if (id != null && !id.isBlank()) {
                        chatIds.add(id.trim());
                    }
                }
            }
            if (maxUpdateId > lastAcknowledgedUpdateId) {
                lastAcknowledgedUpdateId = maxUpdateId;
            }
            persistLocked();
        } catch (IOException e) {
            log.warn("Failed to persist Telegram test chat store: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void persistLocked() throws IOException {
        Files.createDirectories(STATE_PATH.getParent());
        State state = new State();
        state.setLastAcknowledgedUpdateId(lastAcknowledgedUpdateId);
        state.setChatIds(List.copyOf(chatIds));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(STATE_PATH.toFile(), state);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class State {
        private long lastAcknowledgedUpdateId;
        private List<String> chatIds;
    }
}
