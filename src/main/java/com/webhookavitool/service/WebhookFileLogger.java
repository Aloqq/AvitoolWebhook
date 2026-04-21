package com.webhookavitool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.webhookavitool.dto.WebhookLogEntry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
@Component
public class WebhookFileLogger {

    private static final Path LOG_PATH = Path.of("logs", "webhook.log");

    private final ObjectMapper logMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @PostConstruct
    void ensureLogDir() throws IOException {
        Files.createDirectories(LOG_PATH.getParent());
    }

    public synchronized void append(WebhookLogEntry entry) {
        try {
            String line = logMapper.writeValueAsString(entry) + System.lineSeparator();
            Files.writeString(LOG_PATH, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write webhook log entry", e);
        }
    }
}
