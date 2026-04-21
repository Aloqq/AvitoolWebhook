package com.webhookavitool.service;

import com.webhookavitool.config.WebhookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class WebhookAuthService {

    private final WebhookProperties webhookProperties;

    public boolean isAuthorized(String bearerToken) {
        String expected = webhookProperties.getToken();
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(bearerToken)) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = bearerToken.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }

    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String h = authorizationHeader.trim();
        if (!h.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return null;
        }
        return h.substring("Bearer ".length()).trim();
    }
}
