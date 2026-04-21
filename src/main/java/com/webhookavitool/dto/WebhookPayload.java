package com.webhookavitool.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    @JsonProperty("event_id")
    private String eventId;

    @NotBlank
    private String event;

    private String level;

    private String status;

    @NotBlank
    private String message;

    @JsonProperty("error_code")
    private String errorCode;

    private String account;

    @JsonProperty("task_id")
    private Integer taskId;

    private String project;

    private String host;

    @NotBlank
    private String timestamp;

    private Map<String, Object> meta;
}
