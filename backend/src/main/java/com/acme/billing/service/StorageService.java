package com.acme.billing.service;

import com.acme.billing.config.StorageProperties;
import com.acme.billing.service.exception.FileRetrievalException;
import com.acme.billing.service.exception.FileStorageException;
import com.acme.billing.service.exception.FileValidationException;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling file storage operations using MinIO S3-compatible storage.
 * Provides methods for uploading, downloading, deleting files and generating presigned URLs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;
    private final StorageProperties storageProperties;

    /**
     * Uploads a file to MinIO storage with validation and metadata.
     *
     * @param file The multipart file to upload
     * @param billId The bill ID for file organization
     * @return The object key in storage
     * @throws FileValidationException if file validation fails
     * @throws FileStorageException if upload fails
     */
    public String uploadFile(MultipartFile file, String billId) throws FileValidationException, FileStorageException {
        try {
            // Validate file
            validateFile(file);

            // Generate unique file key
            String objectKey = generateObjectKey(file.getOriginalFilename(), billId);

            log.info("Uploading file: {} with size: {} bytes", file.getOriginalFilename(), file.getSize());

            // Upload file with metadata
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(storageProperties.getBucketName())
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .userMetadata(buildUserMetadata(file))
                    .build()
            );

            log.info("Successfully uploaded file: {} to bucket: {} with key: {}",
                file.getOriginalFilename(), storageProperties.getBucketName(), objectKey);

            return objectKey;

        } catch (ErrorResponseException e) {
            log.error("MinIO error during file upload: {}", e.getMessage());
            throw new FileStorageException("MinIO error during file upload: " + e.getMessage(), e);
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException |
                 ServerException | XmlParserException e) {
            log.error("Error during file upload", e);
            throw new FileStorageException("Error during file upload", e);
        }
    }

    /**
     * Downloads a file from MinIO storage as InputStream.
     *
     * @param objectKey The object key of the file to download
     * @return InputStream of the file content
     * @throws FileRetrievalException if download fails
     */
    public InputStream downloadFile(String objectKey) throws FileRetrievalException {
        try {
            log.debug("Downloading file with key: {}", objectKey);

            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(storageProperties.getBucketName())
                    .object(objectKey)
                    .build()
            );

        } catch (ErrorResponseException e) {
            if (e.response().code() == 404) {
                log.warn("File not found: {}", objectKey);
                throw new FileRetrievalException("File not found: " + objectKey, e);
            }
            log.error("MinIO error during file download: {}", e.getMessage());
            throw new FileRetrievalException("MinIO error during file download: " + e.getMessage(), e);
        } catch (InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException |
                 ServerException | XmlParserException e) {
            log.error("Error during file download", e);
            throw new FileRetrievalException("Error during file download", e);
        }
    }

    /**
     * Generates a presigned URL for secure file access.
     *
     * @param objectKey The object key of the file
     * @return Presigned URL string
     * @throws FileRetrievalException if URL generation fails
     */
    public String generatePresignedUrl(String objectKey) throws FileRetrievalException {
        try {
            log.debug("Generating presigned URL for key: {}", objectKey);

            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(storageProperties.getBucketName())
                    .object(objectKey)
                    .expiry(storageProperties.getPresignedUrlExpirationMinutes(), TimeUnit.MINUTES)
                    .build()
            );

        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            log.error("Error generating presigned URL", e);
            throw new FileRetrievalException("Error generating presigned URL", e);
        }
    }

    /**
     * Deletes a file from MinIO storage.
     *
     * @param objectKey The object key of the file to delete
     * @throws FileStorageException if deletion fails
     */
    public void deleteFile(String objectKey) throws FileStorageException {
        try {
            log.info("Deleting file with key: {}", objectKey);

            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(storageProperties.getBucketName())
                    .object(objectKey)
                    .build()
            );

            log.info("Successfully deleted file: {}", objectKey);

        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            log.error("Error during file deletion", e);
            throw new FileStorageException("Error during file deletion", e);
        }
    }

    /**
     * Checks if a file exists in storage.
     *
     * @param objectKey The object key to check
     * @return true if file exists, false otherwise
     */
    public boolean fileExists(String objectKey) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(storageProperties.getBucketName())
                    .object(objectKey)
                    .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.response().code() == 404) {
                return false;
            }
            log.error("Error checking file existence", e);
            return false;
        } catch (Exception e) {
            log.error("Error checking file existence", e);
            return false;
        }
    }

    /**
     * Ensures the bucket exists, creates it if configured to do so.
     */
    public void ensureBucketExists() throws FileStorageException {
        try {
            boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(storageProperties.getBucketName())
                    .build()
            );

            if (!bucketExists && storageProperties.isAutoCreateBucket()) {
                log.info("Creating bucket: {}", storageProperties.getBucketName());
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(storageProperties.getBucketName())
                        .build()
                );
                log.info("Successfully created bucket: {}", storageProperties.getBucketName());
            }

        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException |
                 NoSuchAlgorithmException | ServerException | XmlParserException e) {
            log.error("Error ensuring bucket exists", e);
            throw new FileStorageException("Error ensuring bucket exists", e);
        }
    }

    /**
     * Validates the uploaded file against configured constraints.
     */
    private void validateFile(MultipartFile file) throws FileValidationException {
        if (file.isEmpty()) {
            throw new FileValidationException("Cannot upload empty file");
        }

        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new FileValidationException("File must have a name");
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !storageProperties.getAllowedContentTypes().contains(contentType)) {
            throw new FileValidationException(
                String.format("Content type '%s' is not allowed. Allowed types: %s",
                    contentType, storageProperties.getAllowedContentTypes())
            );
        }

        // Validate file size
        try {
            long maxFileSize = parseFileSize(storageProperties.getMaxFileSize());
            if (file.getSize() > maxFileSize) {
                throw new FileValidationException(
                    String.format("File size %d bytes exceeds maximum allowed size %d bytes",
                        file.getSize(), maxFileSize)
                );
            }
        } catch (Exception e) {
            throw new FileValidationException("Invalid max file size configuration", e);
        }
    }

    /**
     * Generates a unique object key for the file.
     */
    private String generateObjectKey(String originalFilename, String billId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);

        return String.format("bills/%s/%s-%s%s",
            billId != null ? billId : "unknown", timestamp, uuid, extension);
    }

    /**
     * Builds user metadata for the uploaded file.
     */
    private java.util.Map<String, String> buildUserMetadata(MultipartFile file) {
        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("original-filename", file.getOriginalFilename());
        metadata.put("content-type", file.getContentType());
        metadata.put("file-size", String.valueOf(file.getSize()));
        metadata.put("upload-timestamp", LocalDateTime.now().toString());
        return metadata;
    }

    /**
     * Extracts file extension from filename.
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * Parses file size string (e.g., "10MB") to bytes.
     */
    private long parseFileSize(String size) {
        try {
            String trimmedSize = size.trim().toUpperCase();
            if (trimmedSize.endsWith("KB")) {
                return Long.parseLong(trimmedSize.substring(0, trimmedSize.length() - 2)) * 1024;
            } else if (trimmedSize.endsWith("MB")) {
                return Long.parseLong(trimmedSize.substring(0, trimmedSize.length() - 2)) * 1024 * 1024;
            } else if (trimmedSize.endsWith("GB")) {
                return Long.parseLong(trimmedSize.substring(0, trimmedSize.length() - 2)) * 1024 * 1024 * 1024;
            } else {
                return Long.parseLong(trimmedSize);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid file size format: " + size, e);
        }
    }
}