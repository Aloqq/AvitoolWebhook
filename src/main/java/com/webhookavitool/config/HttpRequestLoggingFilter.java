package com.webhookavitool.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Одна строка в лог на каждый запрос — чтобы отличить «запрос не дошёл» от «ответ есть, но не смотрите Postman».
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long ms = System.currentTimeMillis() - start;
            log.info("HTTP {} {} -> {} ({} ms) from {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    ms,
                    request.getRemoteAddr());
        }
    }
}
