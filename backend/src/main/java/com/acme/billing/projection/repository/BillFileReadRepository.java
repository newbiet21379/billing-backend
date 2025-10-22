package com.acme.billing.projection.repository;

import com.acme.billing.projection.BillFileProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for BillFileProjection entities.
 * Provides efficient querying capabilities for file attachments.
 */
@Repository
public interface BillFileReadRepository extends JpaRepository<BillFileProjection, String> {

    /**
     * Find all files attached to a specific bill.
     */
    List<BillFileProjection> findByBillId(String billId);

    /**
     * Find all files attached to a specific bill with pagination.
     */
    Page<BillFileProjection> findByBillId(String billId, Pageable pageable);

    /**
     * Find files by content type.
     */
    List<BillFileProjection> findByContentType(String contentType);

    /**
     * Find files by content type with pagination.
     */
    Page<BillFileProjection> findByContentType(String contentType, Pageable pageable);

    /**
     * Find image files (content type starting with 'image/').
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.contentType LIKE 'image/%'")
    List<BillFileProjection> findImages();

    /**
     * Find image files with pagination.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.contentType LIKE 'image/%'")
    Page<BillFileProjection> findImages(Pageable pageable);

    /**
     * Find PDF files.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.contentType = 'application/pdf'")
    List<BillFileProjection> findPdfs();

    /**
     * Find PDF files with pagination.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.contentType = 'application/pdf'")
    Page<BillFileProjection> findPdfs(Pageable pageable);

    /**
     * Find files attached within a date range.
     */
    List<BillFileProjection> findByAttachedAtBetween(Instant startDate, Instant endDate);

    /**
     * Find files attached within a date range with pagination.
     */
    Page<BillFileProjection> findByAttachedAtBetween(Instant startDate, Instant endDate, Pageable pageable);

    /**
     * Find files by filename containing the search term (case-insensitive).
     */
    List<BillFileProjection> findByFilenameContainingIgnoreCase(String filename);

    /**
     * Find files by filename containing the search term (case-insensitive) with pagination.
     */
    Page<BillFileProjection> findByFilenameContainingIgnoreCase(String filename, Pageable pageable);

    /**
     * Find files by checksum (for duplicate detection).
     */
    List<BillFileProjection> findByChecksum(String checksum);

    /**
     * Find files larger than the specified size in bytes.
     */
    List<BillFileProjection> findByFileSizeGreaterThan(Long fileSize);

    /**
     * Find files larger than the specified size with pagination.
     */
    Page<BillFileProjection> findByFileSizeGreaterThan(Long fileSize, Pageable pageable);

    /**
     * Find files smaller than the specified size in bytes.
     */
    List<BillFileProjection> findByFileSizeLessThan(Long fileSize);

    /**
     * Find files smaller than the specified size with pagination.
     */
    Page<BillFileProjection> findByFileSizeLessThan(Long fileSize, Pageable pageable);

    /**
     * Find files with size within the specified range.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.fileSize BETWEEN :minSize AND :maxSize")
    List<BillFileProjection> findByFileSizeBetween(@Param("minSize") Long minSize, @Param("maxSize") Long maxSize);

    /**
     * Find files with size within the specified range with pagination.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.fileSize BETWEEN :minSize AND :maxSize")
    Page<BillFileProjection> findByFileSizeBetween(@Param("minSize") Long minSize, @Param("maxSize") Long maxSize, Pageable pageable);

    /**
     * Count files attached to a specific bill.
     */
    long countByBillId(String billId);

    /**
     * Count files by content type.
     */
    long countByContentType(String contentType);

    /**
     * Count image files.
     */
    @Query("SELECT COUNT(f) FROM BillFileProjection f WHERE f.contentType LIKE 'image/%'")
    long countImages();

    /**
     * Count PDF files.
     */
    @Query("SELECT COUNT(f) FROM BillFileProjection f WHERE f.contentType = 'application/pdf'")
    long countPdfs();

    /**
     * Find files attached in the last N days.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.attachedAt >= :since")
    List<BillFileProjection> findRecentFiles(@Param("since") Instant since);

    /**
     * Find files attached in the last N days with pagination.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.attachedAt >= :since")
    Page<BillFileProjection> findRecentFiles(@Param("since") Instant since, Pageable pageable);

    /**
     * Find files with multiple criteria.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE " +
           "(:billId IS NULL OR f.billId = :billId) AND " +
           "(:contentType IS NULL OR f.contentType = :contentType) AND " +
           "(:filenameSearch IS NULL OR UPPER(f.filename) LIKE UPPER(CONCAT('%', :filenameSearch, '%'))) AND " +
           "(:minSize IS NULL OR f.fileSize >= :minSize) AND " +
           "(:maxSize IS NULL OR f.fileSize <= :maxSize) AND " +
           "(:attachedAfter IS NULL OR f.attachedAt >= :attachedAfter) AND " +
           "(:attachedBefore IS NULL OR f.attachedAt <= :attachedBefore)")
    Page<BillFileProjection> findByMultipleCriteria(
            @Param("billId") String billId,
            @Param("contentType") String contentType,
            @Param("filenameSearch") String filenameSearch,
            @Param("minSize") Long minSize,
            @Param("maxSize") Long maxSize,
            @Param("attachedAfter") Instant attachedAfter,
            @Param("attachedBefore") Instant attachedBefore,
            Pageable pageable);

    /**
     * Find the most recently attached files.
     */
    @Query("SELECT f FROM BillFileProjection f ORDER BY f.attachedAt DESC")
    List<BillFileProjection> findMostRecentlyAttached(Pageable pageable);

    /**
     * Find files with storage path containing the search term.
     */
    List<BillFileProjection> findByStoragePathContaining(String storagePath);

    /**
     * Check if a file with the given checksum already exists.
     */
    boolean existsByChecksum(String checksum);

    /**
     * Calculate total storage space used by all files.
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM BillFileProjection f")
    Long calculateTotalStorageUsed();

    /**
     * Calculate total storage space used by files of a specific content type.
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM BillFileProjection f WHERE f.contentType = :contentType")
    Long calculateStorageUsedByContentType(@Param("contentType") String contentType);

    /**
     * Find large files (files larger than 5MB).
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.fileSize > 5242880") // 5MB in bytes
    List<BillFileProjection> findLargeFiles();

    /**
     * Find large files with pagination.
     */
    @Query("SELECT f FROM BillFileProjection f WHERE f.fileSize > 5242880") // 5MB in bytes
    Page<BillFileProjection> findLargeFiles(Pageable pageable);
}