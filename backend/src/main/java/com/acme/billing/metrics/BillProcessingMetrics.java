package com.acme.billing.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business metrics for bill processing workflows.
 *
 * Tracks:
 * - Bill creation and processing success rates
 * - OCR processing performance and accuracy
 * - File upload performance and size distribution
 * - Approval workflow efficiency
 * - API endpoint performance
 * - Event processing throughput
 */
@Component
public class BillProcessingMetrics implements MeterBinder {

    // Counters for tracking event occurrences
    private Counter billsCreatedCounter;
    private Counter billsApprovedCounter;
    private Counter filesAttachedCounter;
    private Counter ocrProcessingCounter;
    private Counter ocrSuccessCounter;
    private Counter ocrFailureCounter;
    private Counter apiRequestCounter;
    private Counter commandProcessingCounter;
    private Counter eventProcessingCounter;
    private Counter queryProcessingCounter;

    // Timers for measuring durations
    private Timer billCreationTimer;
    private Timer fileUploadTimer;
    private Timer ocrProcessingTimer;
    private Timer approvalTimer;
    private Timer apiRequestTimer;
    private Timer databaseQueryTimer;

    // Gauges for tracking current state
    private AtomicLong activeBills = new AtomicLong(0);
    private AtomicLong pendingApprovals = new AtomicLong(0);
    private AtomicLong storageUsageBytes = new AtomicLong(0);

    // Distribution summaries for file sizes and processing times
    private DistributionSummary fileSizeDistribution;
    private DistributionSummary ocrProcessingTimeDistribution;

    // Custom metrics for success rates
    private final ConcurrentHashMap<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> totalCounts = new ConcurrentHashMap<>();

    @Override
    public void bindTo(MeterRegistry registry) {
        // Initialize counters
        billsCreatedCounter = Counter.builder("bills.created.total")
            .description("Total number of bills created")
            .register(registry);

        billsApprovedCounter = Counter.builder("bills.approved.total")
            .description("Total number of bills approved")
            .register(registry);

        filesAttachedCounter = Counter.builder("files.attached.total")
            .description("Total number of files attached to bills")
            .tag("type", "unknown")
            .register(registry);

        ocrProcessingCounter = Counter.builder("ocr.processing.total")
            .description("Total OCR processing attempts")
            .register(registry);

        ocrSuccessCounter = Counter.builder("ocr.processing.success")
            .description("Successful OCR processing attempts")
            .register(registry);

        ocrFailureCounter = Counter.builder("ocr.processing.failure")
            .description("Failed OCR processing attempts")
            .tag("error_type", "unknown")
            .register(registry);

        apiRequestCounter = Counter.builder("api.requests.total")
            .description("Total API requests")
            .tag("endpoint", "unknown")
            .tag("method", "unknown")
            .tag("status", "unknown")
            .register(registry);

        commandProcessingCounter = Counter.builder("axon.commands.processed.total")
            .description("Total commands processed")
            .tag("command_type", "unknown")
            .tag("status", "unknown")
            .register(registry);

        eventProcessingCounter = Counter.builder("axon.events.processed.total")
            .description("Total events processed")
            .tag("event_type", "unknown")
            .register(registry);

        queryProcessingCounter = Counter.builder("axon.queries.processed.total")
            .description("Total queries processed")
            .tag("query_type", "unknown")
            .register(registry);

        // Initialize timers
        billCreationTimer = Timer.builder("bills.creation.duration")
            .description("Time taken to create bills")
            .register(registry);

        fileUploadTimer = Timer.builder("files.upload.duration")
            .description("Time taken to upload files")
            .tag("file_type", "unknown")
            .register(registry);

        ocrProcessingTimer = Timer.builder("ocr.processing.duration")
            .description("Time taken for OCR processing")
            .tag("file_type", "unknown")
            .register(registry);

        approvalTimer = Timer.builder("bills.approval.duration")
            .description("Time taken for bill approval")
            .register(registry);

        apiRequestTimer = Timer.builder("api.requests.duration")
            .description("API request processing time")
            .tag("endpoint", "unknown")
            .tag("method", "unknown")
            .register(registry);

        databaseQueryTimer = Timer.builder("database.query.duration")
            .description("Database query execution time")
            .tag("query_type", "unknown")
            .register(registry);

        // Initialize gauges
        Gauge.builder("bills.active.count")
            .description("Number of currently active bills")
            .register(registry, activeBills, AtomicLong::get);

        Gauge.builder("bills.pending_approval.count")
            .description("Number of bills pending approval")
            .register(registry, pendingApprovals, AtomicLong::get);

        Gauge.builder("storage.usage.bytes")
            .description("Current storage usage in bytes")
            .register(registry, storageUsageBytes, AtomicLong::get);

        // Initialize distribution summaries
        fileSizeDistribution = DistributionSummary.builder("files.size.bytes")
            .description("Distribution of uploaded file sizes")
            .tag("file_type", "unknown")
            .register(registry);

        ocrProcessingTimeDistribution = DistributionSummary.builder("ocr.processing.time.seconds")
            .description("Distribution of OCR processing times")
            .tag("file_type", "unknown")
            .register(registry);
    }

    // Bill processing metrics
    public void recordBillCreated() {
        billsCreatedCounter.increment();
        activeBills.incrementAndGet();
    }

    public void recordBillApproved() {
        billsApprovedCounter.increment();
        activeBills.decrementAndGet();
        pendingApprovals.decrementAndGet();
    }

    public void incrementPendingApprovals() {
        pendingApprovals.incrementAndGet();
    }

    public Timer.Sample startBillCreationTimer() {
        return Timer.start();
    }

    public void recordBillCreationTime(Timer.Sample sample) {
        if (billCreationTimer != null) {
            sample.stop(billCreationTimer);
        }
    }

    // File upload metrics
    public void recordFileAttached(String contentType, long fileSizeBytes) {
        filesAttachedCounter.increment(Tags.of("type", getSimpleContentType(contentType)));
        fileSizeDistribution.record(fileSizeBytes);
        storageUsageBytes.addAndGet(fileSizeBytes);
    }

    public Timer.Sample startFileUploadTimer(String contentType) {
        return Timer.start();
    }

    public void recordFileUploadTime(Timer.Sample sample, String contentType) {
        if (fileUploadTimer != null) {
            sample.stop(fileUploadTimer.tag("file_type", getSimpleContentType(contentType)));
        }
    }

    // OCR processing metrics
    public void recordOcrProcessingStarted() {
        ocrProcessingCounter.increment();
    }

    public void recordOcrProcessingSuccess(String contentType, double processingTimeSeconds) {
        ocrSuccessCounter.increment();
        ocrProcessingTimeDistribution.record(processingTimeSeconds, Tags.of("file_type", getSimpleContentType(contentType)));
    }

    public void recordOcrProcessingFailure(String errorType, String contentType) {
        ocrFailureCounter.increment(Tags.of("error_type", errorType, "file_type", getSimpleContentType(contentType)));
    }

    public Timer.Sample startOcrProcessingTimer(String contentType) {
        return Timer.start();
    }

    public void recordOcrProcessingTime(Timer.Sample sample, String contentType) {
        if (ocrProcessingTimer != null) {
            sample.stop(ocrProcessingTimer.tag("file_type", getSimpleContentType(contentType)));
        }
    }

    // API request metrics
    public void recordApiRequest(String endpoint, String method, String status) {
        apiRequestCounter.increment(Tags.of("endpoint", endpoint, "method", method, "status", status));
    }

    public Timer.Sample startApiRequestTimer(String endpoint, String method) {
        return Timer.start();
    }

    public void recordApiRequestTime(Timer.Sample sample, String endpoint, String method) {
        if (apiRequestTimer != null) {
            sample.stop(apiRequestTimer.tag("endpoint", endpoint, "method", method));
        }
    }

    // Axon framework metrics
    public void recordCommandProcessed(String commandType, String status) {
        commandProcessingCounter.increment(Tags.of("command_type", commandType, "status", status));
    }

    public void recordEventProcessed(String eventType) {
        eventProcessingCounter.increment(Tags.of("event_type", eventType));
    }

    public void recordQueryProcessed(String queryType) {
        queryProcessingCounter.increment(Tags.of("query_type", queryType));
    }

    // Database metrics
    public Timer.Sample startDatabaseQueryTimer(String queryType) {
        return Timer.start();
    }

    public void recordDatabaseQueryTime(Timer.Sample sample, String queryType) {
        if (databaseQueryTimer != null) {
            sample.stop(databaseQueryTimer.tag("query_type", queryType));
        }
    }

    // Custom success rate calculations
    public void recordSuccess(String operation) {
        successCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
        totalCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordFailure(String operation) {
        totalCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
    }

    public double getSuccessRate(String operation) {
        AtomicLong success = successCounts.get(operation);
        AtomicLong total = totalCounts.get(operation);

        if (total == null || total.get() == 0) {
            return 0.0;
        }

        return (double) success.get() / total.get();
    }

    // Utility methods
    private String getSimpleContentType(String contentType) {
        if (contentType == null) return "unknown";

        return switch (contentType.toLowerCase()) {
            case "application/pdf" -> "pdf";
            case "image/jpeg", "image/jpg" -> "jpeg";
            case "image/png" -> "png";
            case "image/tiff" -> "tiff";
            case "image/bmp" -> "bmp";
            default -> "other";
        };
    }

    // Reset methods for testing
    public void reset() {
        activeBills.set(0);
        pendingApprovals.set(0);
        storageUsageBytes.set(0);
        successCounts.clear();
        totalCounts.clear();
    }

    // Current state getters
    public long getActiveBillsCount() {
        return activeBills.get();
    }

    public long getPendingApprovalsCount() {
        return pendingApprovals.get();
    }

    public long getStorageUsageBytes() {
        return storageUsageBytes.get();
    }
}