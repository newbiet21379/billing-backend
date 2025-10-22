package com.acme.billing.projection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA projection entity for Bill file attachments read model.
 * This entity represents file attachments associated with bills in the query side.
 */
@Entity
@Table(name = "bill_file_projection", indexes = {
    @Index(name = "idx_file_bill_id", columnList = "billId"),
    @Index(name = "idx_file_attached_at", columnList = "attachedAt"),
    @Index(name = "idx_file_content_type", columnList = "contentType"),
    @Index(name = "idx_file_checksum", columnList = "checksum")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillFileProjection {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "bill_id", nullable = false, length = 100)
    private String billId;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "attached_at", nullable = false)
    private Instant attachedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        version++;
    }

    /**
     * Returns the file size in a human-readable format.
     */
    public String getFormattedFileSize() {
        if (fileSize == null) {
            return "Unknown";
        }

        long bytes = fileSize;
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Checks if this is an image file based on content type.
     */
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    /**
     * Checks if this is a PDF file.
     */
    public boolean isPdf() {
        return "application/pdf".equals(contentType);
    }

    /**
     * Returns the file extension.
     */
    public String getFileExtension() {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}