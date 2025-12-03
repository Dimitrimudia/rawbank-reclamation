package com.rawbank.reclamations.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

@Component
@Order(1)
public class LoggingMdcFilter implements Filter {

    private static final String HDR_REQUEST_ID = "X-Request-Id";
    private static final String HDR_USER_ID = "X-User-Id";
    private static final String HDR_DEVICE_ID = "X-Device-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String requestId = headerOrGenerate(req, HDR_REQUEST_ID);
        String userId = safe(req.getHeader(HDR_USER_ID));
        String deviceId = safe(req.getHeader(HDR_DEVICE_ID));
        String userAgent = safe(req.getHeader("User-Agent"));
        String ip = safe(req.getRemoteAddr());
        String method = req.getMethod();
        String path = req.getRequestURI();
        String query = safe(req.getQueryString());
        String host = localHostname();

        put("trackingId", requestId);
        put("userId", userId);
        put("deviceId", deviceId);
        put("userAgent", userAgent);
        put("clientIp", ip);
        put("httpMethod", method);
        put("httpPath", path);
        put("httpQuery", query);
        put("serverHost", host);
        put("timestamp", Instant.now().toString());

        try {
            chain.doFilter(request, response);
        } finally {
            put("httpStatus", String.valueOf(res.getStatus()));
            MDC.clear();
        }
    }

    private static void put(String key, String value) {
        if (value != null && !value.isEmpty()) {
            MDC.put(key, value);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String headerOrGenerate(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return (v == null || v.isEmpty()) ? UUID.randomUUID().toString() : v;
    }

    private static String localHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
