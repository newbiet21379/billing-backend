package com.acme.billing.web;

import com.acme.billing.service.StorageService;
import com.acme.billing.service.exception.FileRetrievalException;
import com.acme.billing.service.exception.FileStorageException;
import com.acme.billing.service.exception.FileValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for file storage operations.
 * Provides endpoints for uploading, downloading, and managing files in MinIO storage.
 */
@Slf4j
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "File storage operations using MinIO")
public class StorageController {

    private final StorageService storageService;

    @PostMapping("/upload")
    @Operation(
        summary = "Upload a file",
        description = "Uploads a file to MinIO storage with validation. Returns the object key of the uploaded file."
    )
    public ResponseEntity<FileUploadResponse> uploadFile(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Bill ID for file organization", required = true)
            @RequestParam("billId") String billId) {

        try {
            String objectKey = storageService.uploadFile(file, billId);

            FileUploadResponse response = FileUploadResponse.builder()
                .objectKey(objectKey)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .message("File uploaded successfully")
                .build();

            return ResponseEntity.ok(response);

        } catch (FileValidationException e) {
            log.warn("File validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(FileUploadResponse.builder()
                    .error("File validation failed: " + e.getMessage())
                    .build());

        } catch (FileStorageException e) {
            log.error("File storage failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(FileUploadResponse.builder()
                    .error("File storage failed: " + e.getMessage())
                    .build());
        }
    }

    @GetMapping("/download/{objectKey:.+}")
    @Operation(
        summary = "Download a file",
        description = "Downloads a file from MinIO storage using the object key."
    )
    public ResponseEntity<?> downloadFile(
            @Parameter(description = "Object key of the file to download", required = true)
            @PathVariable String objectKey) {

        try {
            var inputStream = storageService.downloadFile(objectKey);

            InputStreamResource resource = new InputStreamResource(inputStream);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + getFilenameFromObjectKey(objectKey) + "\"")
                .body(resource);

        } catch (FileRetrievalException e) {
            log.warn("File retrieval failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Unexpected error during file download", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error downloading file: " + e.getMessage());
        }
    }

    @GetMapping("/presigned-url/{objectKey:.+}")
    @Operation(
        summary = "Generate presigned URL",
        description = "Generates a time-limited presigned URL for secure file access."
    )
    public ResponseEntity<PresignedUrlResponse> generatePresignedUrl(
            @Parameter(description = "Object key of the file", required = true)
            @PathVariable String objectKey) {

        try {
            String presignedUrl = storageService.generatePresignedUrl(objectKey);

            PresignedUrlResponse response = PresignedUrlResponse.builder()
                .objectKey(objectKey)
                .presignedUrl(presignedUrl)
                .message("Presigned URL generated successfully")
                .build();

            return ResponseEntity.ok(response);

        } catch (FileRetrievalException e) {
            log.warn("Failed to generate presigned URL: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(PresignedUrlResponse.builder()
                    .error("File not found: " + e.getMessage())
                    .build());

        } catch (Exception e) {
            log.error("Unexpected error generating presigned URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PresignedUrlResponse.builder()
                    .error("Error generating presigned URL: " + e.getMessage())
                    .build());
        }
    }

    @DeleteMapping("/{objectKey:.+}")
    @Operation(
        summary = "Delete a file",
        description = "Deletes a file from MinIO storage using the object key."
    )
    public ResponseEntity<DeleteFileResponse> deleteFile(
            @Parameter(description = "Object key of the file to delete", required = true)
            @PathVariable String objectKey) {

        try {
            storageService.deleteFile(objectKey);

            DeleteFileResponse response = DeleteFileResponse.builder()
                .objectKey(objectKey)
                .message("File deleted successfully")
                .build();

            return ResponseEntity.ok(response);

        } catch (FileStorageException e) {
            log.error("File deletion failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DeleteFileResponse.builder()
                    .error("File deletion failed: " + e.getMessage())
                    .build());

        } catch (Exception e) {
            log.error("Unexpected error during file deletion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(DeleteFileResponse.builder()
                    .error("Unexpected error: " + e.getMessage())
                    .build());
        }
    }

    @GetMapping("/exists/{objectKey:.+}")
    @Operation(
        summary = "Check if file exists",
        description = "Checks if a file exists in MinIO storage."
    )
    public ResponseEntity<FileExistsResponse> checkFileExists(
            @Parameter(description = "Object key of the file to check", required = true)
            @PathVariable String objectKey) {

        boolean exists = storageService.fileExists(objectKey);

        FileExistsResponse response = FileExistsResponse.builder()
            .objectKey(objectKey)
            .exists(exists)
            .message(exists ? "File exists" : "File does not exist")
            .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/bucket/ensure")
    @Operation(
        summary = "Ensure bucket exists",
        description = "Ensures the storage bucket exists, creates it if configured to do so."
    )
    public ResponseEntity<BucketResponse> ensureBucketExists() {
        try {
            storageService.ensureBucketExists();

            BucketResponse response = BucketResponse.builder()
                .message("Bucket ensured successfully")
                .build();

            return ResponseEntity.ok(response);

        } catch (FileStorageException e) {
            log.error("Failed to ensure bucket exists", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BucketResponse.builder()
                    .error("Failed to ensure bucket: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Extracts filename from object key for Content-Disposition header.
     */
    private String getFilenameFromObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return "download";
        }

        int lastSlashIndex = objectKey.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < objectKey.length() - 1) {
            String filename = objectKey.substring(lastSlashIndex + 1);
            return URLEncoder.encode(filename, StandardCharsets.UTF_8);
        }

        return URLEncoder.encode(objectKey, StandardCharsets.UTF_8);
    }

    // Response DTOs
    @lombok.Data
    @lombok.Builder
    public static class FileUploadResponse {
        private String objectKey;
        private String originalFilename;
        private String contentType;
        private long size;
        private String message;
        private String error;
    }

    @lombok.Data
    @lombok.Builder
    public static class PresignedUrlResponse {
        private String objectKey;
        private String presignedUrl;
        private String message;
        private String error;
    }

    @lombok.Data
    @lombok.Builder
    public static class DeleteFileResponse {
        private String objectKey;
        private String message;
        private String error;
    }

    @lombok.Data
    @lombok.Builder
    public static class FileExistsResponse {
        private String objectKey;
        private boolean exists;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    public static class BucketResponse {
        private String message;
        private String error;
    }
}