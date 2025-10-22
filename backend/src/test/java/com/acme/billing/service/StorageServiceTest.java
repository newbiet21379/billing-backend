package com.acme.billing.service;

import com.acme.billing.config.StorageProperties;
import com.acme.billing.service.exception.FileRetrievalException;
import com.acme.billing.service.exception.FileStorageException;
import com.acme.billing.service.exception.FileValidationException;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StorageService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StorageService Tests")
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private StorageService storageService;

    private final String testBucketName = "test-bucket";
    private final String testBillId = "test-bill-123";

    @BeforeEach
    void setUp() {
        when(storageProperties.getBucketName()).thenReturn(testBucketName);
        when(storageProperties.getMaxFileSize()).thenReturn("10MB");
        when(storageProperties.getPresignedUrlExpirationMinutes()).thenReturn(60);
        when(storageProperties.isAutoCreateBucket()).thenReturn(true);
        when(storageProperties.getAllowedContentTypes()).thenReturn(
            java.util.List.of("application/pdf", "image/jpeg", "image/png")
        );
    }

    @Test
    @DisplayName("Should successfully upload valid file")
    void shouldUploadValidFile() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            "test content".getBytes()
        );

        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        // Act
        String objectKey = storageService.uploadFile(file, testBillId);

        // Assert
        assertNotNull(objectKey);
        assertTrue(objectKey.contains("bills/" + testBillId));
        assertTrue(objectKey.contains("test.pdf"));
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("Should throw FileValidationException for empty file")
    void shouldThrowExceptionForEmptyFile() {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file",
            "empty.pdf",
            "application/pdf",
            new byte[0]
        );

        // Act & Assert
        FileValidationException exception = assertThrows(
            FileValidationException.class,
            () -> storageService.uploadFile(emptyFile, testBillId)
        );

        assertEquals("Cannot upload empty file", exception.getMessage());
        verify(minioClient, never()).putObject(any());
    }

    @Test
    @DisplayName("Should throw FileValidationException for invalid content type")
    void shouldThrowExceptionForInvalidContentType() {
        // Arrange
        MockMultipartFile invalidFile = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "test content".getBytes()
        );

        // Act & Assert
        FileValidationException exception = assertThrows(
            FileValidationException.class,
            () -> storageService.uploadFile(invalidFile, testBillId)
        );

        assertTrue(exception.getMessage().contains("not allowed"));
        verify(minioClient, never()).putObject(any());
    }

    @Test
    @DisplayName("Should throw FileValidationException for oversized file")
    void shouldThrowExceptionForOversizedFile() {
        // Arrange
        when(storageProperties.getMaxFileSize()).thenReturn("1KB");

        MockMultipartFile largeFile = new MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            new byte[2048] // 2KB > 1KB limit
        );

        // Act & Assert
        FileValidationException exception = assertThrows(
            FileValidationException.class,
            () -> storageService.uploadFile(largeFile, testBillId)
        );

        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
        verify(minioClient, never()).putObject(any());
    }

    @Test
    @DisplayName("Should throw FileStorageException when MinIO upload fails")
    void shouldThrowExceptionWhenMinIOUploadFails() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            "test content".getBytes()
        );

        when(minioClient.putObject(any(PutObjectArgs.class)))
            .thenThrow(new ErrorResponseException(null, null, null, "S3 Error", null));

        // Act & Assert
        FileStorageException exception = assertThrows(
            FileStorageException.class,
            () -> storageService.uploadFile(file, testBillId)
        );

        assertTrue(exception.getMessage().contains("MinIO error"));
    }

    @Test
    @DisplayName("Should successfully download file")
    void shouldDownloadFile() throws Exception {
        // Arrange
        String objectKey = "bills/test-bill-123/test.pdf";
        InputStream expectedStream = new ByteArrayInputStream("test content".getBytes());

        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(expectedStream);

        // Act
        InputStream resultStream = storageService.downloadFile(objectKey);

        // Assert
        assertNotNull(resultStream);
        verify(minioClient, times(1)).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("Should throw FileRetrievalException when file not found")
    void shouldThrowExceptionWhenFileNotFound() throws Exception {
        // Arrange
        String objectKey = "nonexistent/file.pdf";

        when(minioClient.getObject(any(GetObjectArgs.class)))
            .thenThrow(new ErrorResponseException(null, null, null, "Not Found", null));

        // Act & Assert
        FileRetrievalException exception = assertThrows(
            FileRetrievalException.class,
            () -> storageService.downloadFile(objectKey)
        );

        assertTrue(exception.getMessage().contains("File not found"));
    }

    @Test
    @DisplayName("Should generate presigned URL successfully")
    void shouldGeneratePresignedUrl() throws Exception {
        // Arrange
        String objectKey = "bills/test-bill-123/test.pdf";
        String expectedUrl = "https://minio-server/presigned-url";

        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
            .thenReturn(expectedUrl);

        // Act
        String resultUrl = storageService.generatePresignedUrl(objectKey);

        // Assert
        assertEquals(expectedUrl, resultUrl);
        verify(minioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    @DisplayName("Should throw FileRetrievalException when presigned URL generation fails")
    void shouldThrowExceptionWhenPresignedUrlGenerationFails() throws Exception {
        // Arrange
        String objectKey = "bills/test-bill-123/test.pdf";

        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
            .thenThrow(new ErrorResponseException(null, null, null, "Error", null));

        // Act & Assert
        FileRetrievalException exception = assertThrows(
            FileRetrievalException.class,
            () -> storageService.generatePresignedUrl(objectKey)
        );

        assertTrue(exception.getMessage().contains("Error generating presigned URL"));
    }

    @Test
    @DisplayName("Should delete file successfully")
    void shouldDeleteFile() throws Exception {
        // Arrange
        String objectKey = "bills/test-bill-123/test.pdf";

        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        // Act
        storageService.deleteFile(objectKey);

        // Assert
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("Should throw FileStorageException when file deletion fails")
    void shouldThrowExceptionWhenFileDeletionFails() throws Exception {
        // Arrange
        String objectKey = "bills/test-bill-123/test.pdf";

        doThrow(new ErrorResponseException(null, null, null, "Error", null))
            .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        // Act & Assert
        FileStorageException exception = assertThrows(
            FileStorageException.class,
            () -> storageService.deleteFile(objectKey)
        );

        assertTrue(exception.getMessage().contains("Error during file deletion"));
    }

    @Test
    @DisplayName("Should return true when file exists")
    void shouldReturnTrueWhenFileExists() throws Exception {
        // Arrange
        String objectKey = "bills/test-bill-123/test.pdf";

        when(minioClient.statObject(any(StatObjectArgs.class)))
            .thenReturn(new ObjectStat(null, null, null, null, null, null, null, null, null));

        // Act
        boolean result = storageService.fileExists(objectKey);

        // Assert
        assertTrue(result);
        verify(minioClient, times(1)).statObject(any(StatObjectArgs.class));
    }

    @Test
    @DisplayName("Should return false when file does not exist")
    void shouldReturnFalseWhenFileDoesNotExist() throws Exception {
        // Arrange
        String objectKey = "nonexistent/file.pdf";

        ErrorResponseException notFoundException = new ErrorResponseException(null, null, 404, "Not Found", null);
        when(minioClient.statObject(any(StatObjectArgs.class)))
            .thenThrow(notFoundException);

        // Act
        boolean result = storageService.fileExists(objectKey);

        // Assert
        assertFalse(result);
        verify(minioClient, times(1)).statObject(any(StatObjectArgs.class));
    }

    @Test
    @DisplayName("Should create bucket when auto-create is enabled")
    void shouldCreateBucketWhenAutoCreateEnabled() throws Exception {
        // Arrange
        when(storageProperties.isAutoCreateBucket()).thenReturn(true);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        // Act
        assertDoesNotThrow(() -> storageService.ensureBucketExists());

        // Assert
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, times(1)).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("Should not create bucket when it already exists")
    void shouldNotCreateBucketWhenAlreadyExists() throws Exception {
        // Arrange
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        // Act
        assertDoesNotThrow(() -> storageService.ensureBucketExists());

        // Assert
        verify(minioClient, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("Should parse file size correctly")
    void shouldParseFileSizeCorrectly() {
        // This test verifies the private parseFileSize method through upload validation
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test.pdf",
            "application/pdf",
            new byte[10 * 1024 * 1024] // 10MB
        );

        when(storageProperties.getMaxFileSize()).thenReturn("10MB");

        // Should not throw exception
        assertDoesNotThrow(() -> storageService.uploadFile(file, testBillId));
    }
}