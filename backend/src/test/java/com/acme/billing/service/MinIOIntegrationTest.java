package com.acme.billing.service;

import com.acme.billing.config.MinioConfig;
import com.acme.billing.config.StorageProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration test to verify MinIO connectivity.
 * This test doesn't require Testcontainers and can run against local MinIO.
 */
@SpringBootTest(classes = {
    MinIOIntegrationTest.TestConfig.class,
    StorageService.class,
    MinioConfig.class,
    StorageProperties.class
})
@DisplayName("MinIO Integration Tests")
@EnabledIfEnvironmentVariable(named = "MINIO_ENDPOINT", matches = ".*")
class MinIOIntegrationTest {

    @Autowired
    private StorageService storageService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private StorageProperties storageProperties;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Use environment variables or defaults
        registry.add("minio.endpoint", () ->
            System.getenv().getOrDefault("MINIO_ENDPOINT", "http://localhost:9000"));
        registry.add("minio.access-key", () ->
            System.getenv().getOrDefault("MINIO_ACCESS_KEY", "minioadmin"));
        registry.add("minio.secret-key", () ->
            System.getenv().getOrDefault("MINIO_SECRET_KEY", "minioadmin"));
        registry.add("minio.region", () ->
            System.getenv().getOrDefault("MINIO_REGION", "us-east-1"));

        // Test-specific bucket
        registry.add("billing.storage.bucket-name", () -> "test-integration");
    }

    @BeforeEach
    void setUp() throws Exception {
        // Ensure test bucket exists
        storageService.ensureBucketExists();
    }

    @Test
    @DisplayName("Should verify MinIO client configuration")
    void shouldVerifyMinioClientConfiguration() {
        assertNotNull(minioClient, "MinIO client should be configured");
        assertNotNull(storageService, "Storage service should be injected");
        assertEquals("test-integration", storageProperties.getBucketName());
    }

    @Test
    @DisplayName("Should upload and download small text file")
    void shouldUploadAndDownloadSmallFile() throws Exception {
        // Arrange
        String fileContent = "Hello MinIO! This is a test file content.";
        MockMultipartFile testFile = new MockMultipartFile(
            "file",
            "test-document.txt",
            "text/plain",
            fileContent.getBytes(StandardCharsets.UTF_8)
        );

        // Note: This will fail validation because text/plain is not allowed by default
        // For now, let's create a PDF-like file
        MockMultipartFile pdfFile = new MockMultipartFile(
            "file",
            "test-document.pdf",
            "application/pdf",
            fileContent.getBytes(StandardCharsets.UTF_8)
        );

        String billId = "test-bill-123";

        // Act - Upload file
        String objectKey = storageService.uploadFile(pdfFile, billId);

        // Assert upload
        assertNotNull(objectKey, "Object key should not be null");
        assertTrue(objectKey.contains(billId), "Object key should contain bill ID");
        assertTrue(storageService.fileExists(objectKey), "File should exist after upload");

        // Act - Download file
        try (InputStream downloadStream = storageService.downloadFile(objectKey)) {
            String downloadedContent = new String(downloadStream.readAllBytes(), StandardCharsets.UTF_8);

            // Assert download
            assertEquals(fileContent, downloadedContent, "Downloaded content should match original");
        }
    }

    @Test
    @DisplayName("Should generate presigned URL")
    void shouldGeneratePresignedUrl() throws Exception {
        // Arrange
        String fileContent = "Test content for presigned URL";
        MockMultipartFile testFile = new MockMultipartFile(
            "file",
            "presigned-test.pdf",
            "application/pdf",
            fileContent.getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = storageService.uploadFile(testFile, "presigned-bill");

        // Act
        String presignedUrl = storageService.generatePresignedUrl(objectKey);

        // Assert
        assertNotNull(presignedUrl, "Presigned URL should not be null");
        assertTrue(presignedUrl.contains("test-integration"), "URL should contain bucket name");
        assertTrue(presignedUrl.contains(objectKey), "URL should contain object key");
        assertTrue(presignedUrl.contains("X-Amz-"), "URL should contain AWS signature parameters");
    }

    @Test
    @DisplayName("Should handle file deletion")
    void shouldHandleFileDeletion() throws Exception {
        // Arrange
        String fileContent = "Content to be deleted";
        MockMultipartFile testFile = new MockMultipartFile(
            "file",
            "delete-test.pdf",
            "application/pdf",
            fileContent.getBytes(StandardCharsets.UTF_8)
        );

        String objectKey = storageService.uploadFile(testFile, "delete-bill");

        // Verify file exists
        assertTrue(storageService.fileExists(objectKey), "File should exist before deletion");

        // Act - Delete file
        storageService.deleteFile(objectKey);

        // Assert - File should no longer exist
        assertFalse(storageService.fileExists(objectKey), "File should not exist after deletion");
    }

    @Test
    @DisplayName("Should handle file existence check for non-existent file")
    void shouldHandleFileExistenceCheckForNonExistentFile() {
        // Act
        boolean exists = storageService.fileExists("non-existent/file/path.pdf");

        // Assert
        assertFalse(exists, "Non-existent file should return false");
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public StorageProperties testStorageProperties() {
            StorageProperties props = new StorageProperties();
            props.setBucketName("test-integration");
            props.setMaxFileSize("10MB");
            props.setAllowedContentTypes(java.util.List.of(
                "application/pdf", "image/jpeg", "image/png"
            ));
            props.setPresignedUrlExpirationMinutes(60);
            props.setAutoCreateBucket(true);
            return props;
        }
    }
}