package com.acme.billing.monitoring.logging;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that manages correlation IDs for request tracing.
 *
 * This filter:
 * - Generates or extracts correlation IDs from incoming requests
 * - Adds correlation IDs to the MDC for logging
 * - Adds correlation IDs to response headers for client tracing
 * - Ensures MDC cleanup after request processing
 */
@Component
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String SPAN_ID_MDC_KEY = "spanId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // Extract or generate correlation ID
            String correlationId = extractOrGenerateCorrelationId(httpRequest);

            // Add to MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

            // Add to response headers
            httpResponse.addHeader(CORRELATION_ID_HEADER_NAME, correlationId);

            // Add additional trace information if available from tracing headers
            addTraceInformation(httpRequest);

            // Log request start with correlation ID
            logRequestStart(httpRequest, correlationId);

            // Continue with the request
            chain.doFilter(request, response);

            // Log request completion
            logRequestCompletion(httpRequest, correlationId, httpResponse.getStatus());

        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(SPAN_ID_MDC_KEY);
        }
    }

    /**
     * Extracts correlation ID from request headers or generates a new one.
     */
    private String extractOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER_NAME);

        if (!StringUtils.hasText(correlationId)) {
            // Check other common correlation ID headers
            correlationId = request.getHeader("X-Request-ID");
            if (!StringUtils.hasText(correlationId)) {
                correlationId = request.getHeader("X-Trace-ID");
            }
            if (!StringUtils.hasText(correlationId)) {
                correlationId = request.getHeader("Traceparent");
            }
        }

        if (!StringUtils.hasText(correlationId)) {
            correlationId = generateCorrelationId();
        }

        return correlationId;
    }

    /**
     * Generates a new correlation ID.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Adds trace information from distributed tracing headers.
     */
    private void addTraceInformation(HttpServletRequest request) {
        // Handle W3C Trace Context headers
        String traceparent = request.getHeader("traceparent");
        if (StringUtils.hasText(traceparent)) {
            parseTraceparent(traceparent);
        }

        // Handle AWS X-Ray headers
        String awsTraceId = request.getHeader("X-Amzn-Trace-Id");
        if (StringUtils.hasText(awsTraceId)) {
            parseAwsTraceId(awsTraceId);
        }

        // Handle Jaeger headers
        String uberTraceId = request.getHeader("uber-trace-id");
        if (StringUtils.hasText(uberTraceId)) {
            parseUberTraceId(uberTraceId);
        }
    }

    /**
     * Parse W3C traceparent header format: version-traceId-parentId-flags
     */
    private void parseTraceparent(String traceparent) {
        try {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2) {
                String traceId = parts[1];
                MDC.put(TRACE_ID_MDC_KEY, traceId);
            }
            if (parts.length >= 3) {
                String spanId = parts[2];
                MDC.put(SPAN_ID_MDC_KEY, spanId);
            }
        } catch (Exception e) {
            // Log at debug level to avoid noise
            org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class)
                .debug("Failed to parse traceparent header: {}", traceparent, e);
        }
    }

    /**
     * Parse AWS X-Ray header format.
     */
    private void parseAwsTraceId(String awsTraceId) {
        try {
            String[] parts = awsTraceId.split(";");
            for (String part : parts) {
                if (part.startsWith("Root=")) {
                    MDC.put(TRACE_ID_MDC_KEY, part.substring(5));
                } else if (part.startsWith("Parent=")) {
                    MDC.put(SPAN_ID_MDC_KEY, part.substring(7));
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class)
                .debug("Failed to parse AWS X-Ray header: {}", awsTraceId, e);
        }
    }

    /**
     * Parse Jaeger uber-trace-id header format.
     */
    private void parseUberTraceId(String uberTraceId) {
        try {
            String[] parts = uberTraceId.split(":");
            if (parts.length >= 2) {
                String traceId = parts[1];
                MDC.put(TRACE_ID_MDC_KEY, traceId);
            }
            if (parts.length >= 2) {
                String spanId = parts[2];
                MDC.put(SPAN_ID_MDC_KEY, spanId);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class)
                .debug("Failed to parse Jaeger header: {}", uberTraceId, e);
        }
    }

    /**
     * Logs the start of request processing.
     */
    private void logRequestStart(HttpServletRequest request, String correlationId) {
        org.slf4j.LoggerFactory.getLogger("REQUEST_LOGGER")
            .info("Request started - correlation_id: {} {} {} from {}",
                correlationId,
                request.getMethod(),
                request.getRequestURI(),
                getClientIp(request));
    }

    /**
     * Logs the completion of request processing.
     */
    private void logRequestCompletion(HttpServletRequest request, String correlationId, int status) {
        org.slf4j.LoggerFactory.getLogger("REQUEST_LOGGER")
            .info("Request completed - correlation_id: {} {} {} status: {}",
                correlationId,
                request.getMethod(),
                request.getRequestURI(),
                status);
    }

    /**
     * Extracts client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialize filter if needed
        org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class)
            .info("CorrelationIdFilter initialized");
    }

    @Override
    public void destroy() {
        // Cleanup resources if needed
        org.slf4j.LoggerFactory.getLogger(CorrelationIdFilter.class)
            .info("CorrelationIdFilter destroyed");
    }
}