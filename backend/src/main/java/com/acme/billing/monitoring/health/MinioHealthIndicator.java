package com.acme.billing.monitoring.health;

import io.minio.*;
import io.minio.errors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Health indicator for MinIO object storage connectivity.
 *
 * Checks if MinIO is reachable and can perform basic operations
 * like listing buckets and performing a simple upload/delete test.
 */
@Component
public class MinioHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(MinioHealthIndicator.class);
    private static final String HEALTH_CHECK_BUCKET = "health-check-bucket";
    private static final String HEALTH_CHECK_OBJECT = "health-check.txt";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioHealthIndicator(
            MinioClient minioClient,
            @Value("${billing.storage.bucket-name}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    @Override
    public Health health() {
        try {
            logger.debug("Checking MinIO connectivity");

            // Check basic connectivity by listing buckets
            ListBucketsArgs listArgs = ListBucketsArgs.builder().build();
            var buckets = minioClient.listBuckets(listArgs);

            boolean mainBucketExists = buckets.stream()
                .anyMatch(bucket -> bucket.name().equals(bucketName));

            Health.Builder healthBuilder = Health.up()
                .withDetail("service", "MinIO Object Storage")
                .withDetail("bucketName", bucketName)
                .withDetail("bucketExists", mainBucketExists)
                .withDetail("totalBuckets", buckets.size())
                .withDetail("timestamp", System.currentTimeMillis());

            // Perform a write test if main bucket exists
            if (mainBucketExists) {
                boolean writeTestPassed = performWriteTest();
                healthBuilder.withDetail("writeTest", writeTestPassed);

                if (!writeTestPassed) {
                    healthBuilder.status("WARNING");
                }
            } else {
                healthBuilder.withDetail("writeTest", "SKIPPED - No bucket");
            }

            return healthBuilder.build();

        } catch (ServerException e) {
            logger.warn("MinIO server error during health check: {}", e.getMessage());
            return Health.down()
                .withDetail("service", "MinIO Object Storage")
                .withDetail("error", "Server error: " + e.getMessage())
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        } catch (InsufficientDataException | ErrorResponseException |
                 InternalException | InvalidKeyException | InvalidResponseException |
                 NoSuchAlgorithmException | XmlParserException e) {
            logger.warn("MinIO connection error during health check: {}", e.getMessage());
            return Health.down()
                .withDetail("service", "MinIO Object Storage")
                .withDetail("error", "Connection error: " + e.getMessage())
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        } catch (Exception e) {
            logger.warn("Unexpected error during MinIO health check: {}", e.getMessage());
            return Health.down()
                .withDetail("service", "MinIO Object Storage")
                .withDetail("error", "Unexpected error: " + e.getMessage())
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        }
    }

    /**
     * Performs a simple write test by uploading and deleting a small object.
     */
    private boolean performWriteTest() {
        try {
            String testData = "Health check test at " + Instant.now() + " - " +
                            ThreadLocalRandom.current().nextInt(1000);

            // Upload test object
            PutObjectArgs putArgs = PutObjectArgs.builder()
                .bucket(bucketName)
                .object(HEALTH_CHECK_OBJECT)
                .stream(new ByteArrayInputStream(testData.getBytes()),
                       testData.length(), -1)
                .contentType("text/plain")
                .build();

            minioClient.putObject(putArgs);

            // Verify object exists
            StatObjectArgs statArgs = StatObjectArgs.builder()
                .bucket(bucketName)
                .object(HEALTH_CHECK_OBJECT)
                .build();

            minioClient.statObject(statArgs);

            // Clean up test object
            RemoveObjectArgs removeArgs = RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(HEALTH_CHECK_OBJECT)
                .build();

            minioClient.removeObject(removeArgs);

            logger.debug("MinIO write test completed successfully");
            return true;

        } catch (Exception e) {
            logger.debug("MinIO write test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the specified bucket exists and is accessible.
     */
    public boolean isBucketAccessible(String bucket) {
        try {
            BucketExistsArgs args = BucketExistsArgs.builder().bucket(bucket).build();
            return minioClient.bucketExists(args);
        } catch (Exception e) {
            logger.warn("Failed to check bucket accessibility: {}", e.getMessage());
            return false;
        }
    }
}