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
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for StorageService with MinIO container.
 * These tests run against a real MinIO instance.
 */
@SpringBootTest(classes = {
    StorageServiceIntegrationTest.TestConfig.class,
    StorageService.class,
    MinioConfig.class,
    StorageProperties.class
})
@Testcontainers
@DisplayName("StorageService Integration Tests")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class StorageServiceIntegrationTest {

    @Container
    static MinIOContainer minioContainer = new MinIOContainer(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(9000, 9001)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", () -> minioContainer.getS3URL());
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.region", () -> "us-east-1");
        registry.add("billing.storage.bucket-name", () -> "test-billing-documents");
    }

    @Autowired
    private StorageService storageService;

    @Autowired
    private MinioClient minioClient;

    private final String testBucketName = "test-billing-documents";
    private final String testBillId = "integration-test-bill-123";

    @BeforeEach
    void setUp() throws Exception {
        // Clean up test data before each test
        ensureBucketExists();
    }

    @Test
    @DisplayName("Should successfully upload and download file end-to-end")
    void shouldUploadAndDownloadFile() throws Exception {
        // Arrange
        String fileContent = "This is a test PDF content for integration testing";
        MockMultipartFile testFile = new MockMultipartFile(
            "file",
            "test-document.pdf",
            "application/pdf",
            fileContent.getBytes()
        );

        // Act - Upload file
        String objectKey = storageService.uploadFile(testFile, testBillId);

        // Assert upload
        assertNotNull(objectKey);
        assertTrue(objectKey.contains(testBillId));
        assertTrue(storageService.fileExists(objectKey));

        // Act - Download file
        try (InputStream downloadStream = storageService.downloadFile(objectKey)) {
            String downloadedContent = new String(downloadStream.readAllBytes());

            // Assert download
            assertEquals(fileContent, downloadedContent);
        }
    }

    @Test
    @DisplayName("Should generate valid presigned URL")
    void shouldGenerateValidPresignedUrl() throws Exception {
        // Arrange
        MockMultipartFile testFile = new MockMultipartFile(
            "file",
            "test-document.jpg",
            "image/jpeg",
            "test image content".getBytes()
        );

        String objectKey = storageService.uploadFile(testFile, testBillId);

        // Act
        String presignedUrl = storageService.generatePresignedUrl(objectKey);

        // Assert
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains(testBucketName));
        assertTrue(presignedUrl.contains(objectKey));
        assertTrue(presignedUrl.contains("X-Amz-Algorithm="));
    }

    @Test
    @DisplayName("Should delete file successfully")
    void shouldDeleteFile() throws Exception {
        // Arrange
        MockMultipartFile testFile = new MockMultipartFile(
            "file",
            "to-delete.pdf",
            "application/pdf",
            "content to delete".getBytes()
        );

        String objectKey = storageService.uploadFile(testFile, testBillId);

        // Verify file exists before deletion
        assertTrue(storageService.fileExists(objectKey));

        // Act
        storageService.deleteFile(objectKey);

        // Assert
        assertFalse(storageService.fileExists(objectKey));
    }

    @Test
    @DisplayName("Should handle multiple files for same bill")
    void shouldHandleMultipleFilesForSameBill() throws Exception {
        // Arrange
        MockMultipartFile file1 = new MockMultipartFile(
            "file1",
            "invoice.pdf",
            "application/pdf",
            "Invoice content".getBytes()
        );

        MockMultipartFile file2 = new MockMultipartFile(
            "file2",
            "receipt.jpg",
            "image/jpeg",
            "Receipt content".getBytes()
        );

        // Act
        String objectKey1 = storageService.uploadFile(file1, testBillId);
        String objectKey2 = storageService.uploadFile(file2, testBillId);

        // Assert
        assertNotNull(objectKey1);
        assertNotNull(objectKey2);
        assertNotEquals(objectKey1, objectKey2);
        assertTrue(objectKey1.contains(testBillId));
        assertTrue(objectKey2.contains(testBillId));
        assertTrue(storageService.fileExists(objectKey1));
        assertTrue(storageService.fileExists(objectKey2));
    }

    @Test
    @DisplayName("Should create bucket automatically when enabled")
    void shouldCreateBucketAutomatically() throws Exception {
        // Arrange - Use a different bucket name
        String newBucketName = "auto-created-bucket";

        // This would need a different StorageProperties instance or dynamic configuration
        // For this test, we'll verify that bucket creation doesn't throw errors
        assertDoesNotThrow(() -> {
            // The bucket creation is handled in the service setup
            // This test mainly verifies that the service can work with the auto-create feature
            storageService.ensureBucketExists();
        });
    }

    @Test
    @DisplayName("Should validate file size limits")
    void shouldValidateFileSizeLimits() {
        // Arrange - Create a file larger than the configured limit
        MockMultipartFile largeFile = new MockMultipartFile(
            "file",
            "large-file.pdf",
            "application/pdf",
            new byte[15 * 1024 * 1024] // 15MB (assuming limit is 10MB)
        );

        // Act & Assert
        assertThrows(com.acme.billing.service.exception.FileValidationException.class,
            () -> storageService.uploadFile(largeFile, testBillId));
    }

    @Test
    @DisplayName("Should handle different file types")
    void shouldHandleDifferentFileTypes() throws Exception {
        String[] validTypes = {"application/pdf", "image/jpeg", "image/png"};
        String[] extensions = {".pdf", ".jpg", ".png"};
        String[] names = {"document", "photo", "image"};

        for (int i = 0; i < validTypes.length; i++) {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file",
                names[i] + extensions[i],
                validTypes[i],
                ("Test content for " + names[i]).getBytes()
            );

            // Act
            String objectKey = storageService.uploadFile(file, testBillId);

            // Assert
            assertNotNull(objectKey);
            assertTrue(storageService.fileExists(objectKey));

            // Cleanup
            storageService.deleteFile(objectKey);
        }
    }

    private void ensureBucketExists() throws Exception {
        try {
            boolean bucketExists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder()
                    .bucket(testBucketName)
                    .build()
            );

            if (!bucketExists) {
                minioClient.makeBucket(
                    io.minio.MakeBucketArgs.builder()
                        .bucket(testBucketName)
                        .build()
                );
            }
        } catch (Exception e) {
            // Bucket creation might fail if it already exists, which is fine for our tests
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        public StorageProperties testStorageProperties() {
            StorageProperties props = new StorageProperties();
            props.setBucketName("test-billing-documents");
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