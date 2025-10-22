package com.acme.billing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for file storage operations.
 * Binds to "billing.storage" prefix in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "billing.storage")
public class StorageProperties {

    /**
     * Name of the S3 bucket for storing files
     */
    private String bucketName = "billing-documents";

    /**
     * Maximum allowed file size for uploads
     */
    private String maxFileSize = "10MB";

    /**
     * List of allowed content types for file uploads
     */
    private List<String> allowedContentTypes = List.of(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/jpg"
    );

    /**
     * Duration for presigned URL expiration (in minutes)
     */
    private int presignedUrlExpirationMinutes = 60;

    /**
     * Enable automatic creation of bucket if it doesn't exist
     */
    private boolean autoCreateBucket = true;

    /**
     * Enable multipart uploads for large files
     */
    private boolean multipartUploadEnabled = true;

    /**
     * Part size for multipart uploads (in MB)
     */
    private long multipartPartSizeMB = 5;
}