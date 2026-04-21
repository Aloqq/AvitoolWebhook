package com.webhookavitool.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Чтобы открытие http://host:3000/ в браузере не выглядело как «сломалось» (404) и было понятно, куда слать webhook.
 */
@RestController
public class RootController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "webhook-avitool");
        body.put("hint", "События принимает только POST /webhook с JSON и заголовком Authorization: Bearer …");
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("webhook", "POST /webhook");
        endpoints.put("health", "GET /health");
        body.put("endpoints", endpoints);
        return ResponseEntity.ok(body);
    }
}
