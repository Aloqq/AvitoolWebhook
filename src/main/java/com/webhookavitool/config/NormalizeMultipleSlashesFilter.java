package com.webhookavitool.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Postman/прокси иногда дают URL вида http://host:3000//webhook — Spring отдаёт 404.
 * Схлопываем повторяющиеся слэши в пути.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class NormalizeMultipleSlashesFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri == null || !uri.contains("//")) {
            filterChain.doFilter(request, response);
            return;
        }
        String normalized = uri.replaceAll("/+", "/");
        if (normalized.isEmpty()) {
            normalized = "/";
        }
        String finalNorm = normalized;
        filterChain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return finalNorm;
            }

            @Override
            public String getServletPath() {
                return finalNorm;
            }

            @Override
            public String getPathInfo() {
                return null;
            }
        }, response);
    }
}
