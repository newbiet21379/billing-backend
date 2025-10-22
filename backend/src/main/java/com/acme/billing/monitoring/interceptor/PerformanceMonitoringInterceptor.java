package com.acme.billing.monitoring.interceptor;

import com.acme.billing.metrics.BillProcessingMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Performance monitoring interceptor for API endpoints.
 *
 * This interceptor tracks:
 * - Request duration and response times
 * - Request counts by endpoint and method
 * - Request sizes and response sizes
 * - Error rates and status code distribution
 * - Slow request detection and logging
 * - Metrics integration with Micrometer
 */
@Component
public class PerformanceMonitoringInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringInterceptor.class);
    private static final String STOPWATCH_ATTRIBUTE = "stopwatch";
    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final String REQUEST_SIZE_ATTRIBUTE = "requestSize";

    private final BillProcessingMetrics metrics;
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000; // 1 second
    private static final long VERY_SLOW_REQUEST_THRESHOLD_MS = 5000; // 5 seconds

    public PerformanceMonitoringInterceptor(BillProcessingMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Record start time
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);

        // Create and start stopwatch
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        request.setAttribute(STOPWATCH_ATTRIBUTE, stopWatch);

        // Record request size if content-length header is available
        String contentLength = request.getHeader("Content-Length");
        if (contentLength != null && !contentLength.isEmpty()) {
            try {
                long requestSize = Long.parseLong(contentLength);
                request.setAttribute(REQUEST_SIZE_ATTRIBUTE, requestSize);
            } catch (NumberFormatException e) {
                logger.debug("Invalid Content-Length header: {}", contentLength);
            }
        }

        // Log slow query preparation if applicable
        String query = request.getQueryString();
        if (query != null && query.length() > 500) {
            logger.warn("Large query string detected - correlation_id: {}, query_length: {}, endpoint: {}",
                MDC.get("correlationId"), query.length(), getRequestPath(request));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        try {
            // Calculate request duration
            StopWatch stopWatch = (StopWatch) request.getAttribute(STOPWATCH_ATTRIBUTE);
            Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);

            if (stopWatch != null && stopWatch.isRunning()) {
                stopWatch.stop();
            }

            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;

            // Extract request information
            String method = request.getMethod();
            String endpoint = getEndpointName(request);
            String path = getRequestPath(request);
            int statusCode = response.getStatus();

            // Record metrics
            recordMetrics(request, response, endpoint, method, statusCode, duration, ex);

            // Log performance warnings for slow requests
            logSlowRequests(method, path, duration, statusCode, ex);

            // Log request summary
            logRequestSummary(method, path, statusCode, duration, request, response);

            // Log exceptions if present
            if (ex != null) {
                logRequestException(method, path, ex);
            }

        } catch (Exception e) {
            logger.error("Error in performance monitoring interceptor: {}", e.getMessage(), e);
        }
    }

    /**
     * Records various metrics for the request.
     */
    private void recordMetrics(HttpServletRequest request, HttpServletResponse response,
                             String endpoint, String method, int statusCode, long duration, Exception ex) {

        // Record API request metrics
        metrics.recordApiRequest(endpoint, method, getStatusCodeCategory(statusCode));

        Timer.Sample timerSample = metrics.startApiRequestTimer(endpoint, method);
        metrics.recordApiRequestTime(timerSample, endpoint, method);

        // Record error-specific metrics
        if (ex != null || statusCode >= 400) {
            metrics.recordFailure("api_requests");
        } else {
            metrics.recordSuccess("api_requests");
        }

        // Record database query time if available
        String dbTimeHeader = response.getHeader("X-DB-Query-Time");
        if (dbTimeHeader != null) {
            try {
                double dbTimeMs = Double.parseDouble(dbTimeHeader);
                metrics.recordDatabaseQueryTime(metrics.startDatabaseQueryTimer(endpoint), endpoint);
            } catch (NumberFormatException e) {
                logger.debug("Invalid DB query time header: {}", dbTimeHeader);
            }
        }

        // Record file upload metrics for file operations
        if (endpoint.contains("file") && method.equals("POST")) {
            Long requestSize = (Long) request.getAttribute(REQUEST_SIZE_ATTRIBUTE);
            if (requestSize != null) {
                String contentType = request.getContentType();
                metrics.recordFileAttached(contentType, requestSize);
            }
        }
    }

    /**
     * Logs warnings for slow requests.
     */
    private void logSlowRequests(String method, String path, long duration, int statusCode, Exception ex) {
        if (duration > VERY_SLOW_REQUEST_THRESHOLD_MS) {
            logger.warn("VERY SLOW REQUEST - correlation_id: {}, {} {} took {}ms (status: {}), exception: {}",
                MDC.get("correlationId"), method, path, duration, statusCode, ex != null ? ex.getClass().getSimpleName() : "none");
        } else if (duration > SLOW_REQUEST_THRESHOLD_MS) {
            logger.warn("SLOW REQUEST - correlation_id: {}, {} {} took {}ms (status: {})",
                MDC.get("correlationId"), method, path, duration, statusCode);
        }
    }

    /**
     * Logs a summary of the request.
     */
    private void logRequestSummary(String method, String path, int statusCode, long duration,
                                 HttpServletRequest request, HttpServletResponse response) {
        Long requestSize = (Long) request.getAttribute(REQUEST_SIZE_ATTRIBUTE);
        String userAgent = request.getHeader("User-Agent");
        String clientIp = getClientIp(request);

        // Create log message with key metrics
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("Request summary - correlation_id: ").append(MDC.get("correlationId"))
                  .append(" method: ").append(method)
                  .append(" path: ").append(path)
                  .append(" status: ").append(statusCode)
                  .append(" duration: ").append(duration).append("ms");

        if (requestSize != null) {
            logMessage.append(" request_size: ").append(requestSize).append(" bytes");
        }

        if (userAgent != null && userAgent.length() > 200) {
            logMessage.append(" user_agent: ").append(userAgent.substring(0, 200)).append("...");
        } else if (userAgent != null) {
            logMessage.append(" user_agent: ").append(userAgent);
        }

        logMessage.append(" client_ip: ").append(clientIp);

        // Use appropriate log level based on status code
        if (statusCode >= 500) {
            logger.error(logMessage.toString());
        } else if (statusCode >= 400) {
            logger.warn(logMessage.toString());
        } else {
            logger.info(logMessage.toString());
        }
    }

    /**
     * Logs exceptions that occurred during request processing.
     */
    private void logRequestException(String method, String path, Exception ex) {
        logger.error("Request exception - correlation_id: {}, {} {} failed with {}: {}",
            MDC.get("correlationId"), method, path, ex.getClass().getSimpleName(), ex.getMessage(), ex);
    }

    /**
     * Extracts endpoint name from request for metrics grouping.
     */
    private String getEndpointName(HttpServletRequest request) {
        String path = getRequestPath(request);

        // Group similar endpoints
        if (path.startsWith("/api/commands/bills")) {
            if (path.matches("/api/commands/bills/[^/]+/file")) {
                return "bills.attach-file";
            } else if (path.matches("/api/commands/bills/[^/]+/approve")) {
                return "bills.approve";
            } else {
                return "bills.commands";
            }
        } else if (path.startsWith("/api/queries/bills")) {
            if (path.matches("/api/queries/bills/[^/]+")) {
                return "bills.get-by-id";
            } else {
                return "bills.list";
            }
        } else if (path.startsWith("/api/")) {
            return path.split("/")[2]; // Get the first path segment after /api/
        } else if (path.startsWith("/actuator/")) {
            return "actuator." + path.split("/")[2];
        } else {
            return "other";
        }
    }

    /**
     * Gets the request path without query parameters.
     */
    private String getRequestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null ? path : "/";
    }

    /**
     * Gets the status code category for metrics.
     */
    private String getStatusCodeCategory(int statusCode) {
        return switch (statusCode / 100) {
            case 2 -> "success";
            case 3 -> "redirect";
            case 4 -> "client_error";
            case 5 -> "server_error";
            default -> "unknown";
        };
    }

    /**
     * Extracts client IP address, considering proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}