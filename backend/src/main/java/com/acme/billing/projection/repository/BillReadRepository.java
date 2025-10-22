package com.acme.billing.projection.repository;

import com.acme.billing.domain.BillStatus;
import com.acme.billing.projection.BillProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for BillProjection entities.
 * Provides efficient querying capabilities for the read model.
 */
@Repository
public interface BillReadRepository extends JpaRepository<BillProjection, String>, JpaSpecificationExecutor<BillProjection> {

    /**
     * Find bills by their status.
     */
    List<BillProjection> findByStatus(BillStatus status);

    /**
     * Find bills by status with pagination.
     */
    Page<BillProjection> findByStatus(BillStatus status, Pageable pageable);

    /**
     * Find bills by approver ID.
     */
    List<BillProjection> findByApproverId(String approverId);

    /**
     * Find bills by approver ID with pagination.
     */
    Page<BillProjection> findByApproverId(String approverId, Pageable pageable);

    /**
     * Find bills created within a date range.
     */
    List<BillProjection> findByCreatedAtBetween(Instant startDate, Instant endDate);

    /**
     * Find bills created within a date range with pagination.
     */
    Page<BillProjection> findByCreatedAtBetween(Instant startDate, Instant endDate, Pageable pageable);

    /**
     * Find bills updated within a date range.
     */
    List<BillProjection> findByUpdatedAtBetween(Instant startDate, Instant endDate);

    /**
     * Find bills that have OCR results.
     */
    @Query("SELECT b FROM BillProjection b WHERE b.ocrExtractedText IS NOT NULL OR b.ocrExtractedTotal IS NOT NULL")
    List<BillProjection> findWithOcrResults();

    /**
     * Find bills with OCR results with pagination.
     */
    @Query("SELECT b FROM BillProjection b WHERE b.ocrExtractedText IS NOT NULL OR b.ocrExtractedTotal IS NOT NULL")
    Page<BillProjection> findWithOcrResults(Pageable pageable);

    /**
     * Find bills by title containing the search term (case-insensitive).
     */
    List<BillProjection> findByTitleContainingIgnoreCase(String title);

    /**
     * Find bills by OCR extracted title containing the search term (case-insensitive).
     */
    List<BillProjection> findByOcrExtractedTitleContainingIgnoreCase(String ocrTitle);

    /**
     * Find bills where the total amount is within the specified range.
     */
    @Query("SELECT b FROM BillProjection b WHERE (b.ocrExtractedTotal BETWEEN :minTotal AND :maxTotal) OR (b.total BETWEEN :minTotal AND :maxTotal)")
    List<BillProjection> findByTotalBetween(@Param("minTotal") BigDecimal minTotal, @Param("maxTotal") BigDecimal maxTotal);

    /**
     * Find bills where the total amount is within the specified range with pagination.
     */
    @Query("SELECT b FROM BillProjection b WHERE (b.ocrExtractedTotal BETWEEN :minTotal AND :maxTotal) OR (b.total BETWEEN :minTotal AND :maxTotal)")
    Page<BillProjection> findByTotalBetween(@Param("minTotal") BigDecimal minTotal, @Param("maxTotal") BigDecimal maxTotal, Pageable pageable);

    /**
     * Count bills by status.
     */
    long countByStatus(BillStatus status);

    /**
     * Count bills by approver ID.
     */
    long countByApproverId(String approverId);

    /**
     * Find bills created by multiple criteria with pagination.
     */
    @Query("SELECT b FROM BillProjection b WHERE " +
           "(:status IS NULL OR b.status = :status) AND " +
           "(:approverId IS NULL OR b.approverId = :approverId) AND " +
           "(:hasOcr IS NULL OR (:hasOcr = true AND (b.ocrExtractedText IS NOT NULL OR b.ocrExtractedTotal IS NOT NULL))) AND " +
           "(:minTotal IS NULL OR b.getEffectiveTotal() >= :minTotal) AND " +
           "(:maxTotal IS NULL OR b.getEffectiveTotal() <= :maxTotal) AND " +
           "(:titleSearch IS NULL OR (UPPER(b.title) LIKE UPPER(CONCAT('%', :titleSearch, '%')) OR UPPER(b.ocrExtractedTitle) LIKE UPPER(CONCAT('%', :titleSearch, '%'))))")
    Page<BillProjection> findByMultipleCriteria(
            @Param("status") BillStatus status,
            @Param("approverId") String approverId,
            @Param("hasOcr") Boolean hasOcr,
            @Param("minTotal") BigDecimal minTotal,
            @Param("maxTotal") BigDecimal maxTotal,
            @Param("titleSearch") String titleSearch,
            Pageable pageable);

    /**
     * Find bills that are pending approval (status = PROCESSED).
     */
    List<BillProjection> findPendingApproval();

    /**
     * Find bills that are pending approval with pagination.
     */
    Page<BillProjection> findPendingApproval(Pageable pageable);

    /**
     * Find recent bills updated in the last N days.
     */
    @Query("SELECT b FROM BillProjection b WHERE b.updatedAt >= :since")
    List<BillProjection> findRecentBills(@Param("since") Instant since);

    /**
     * Find recent bills updated in the last N days with pagination.
     */
    @Query("SELECT b FROM BillProjection b WHERE b.updatedAt >= :since")
    Page<BillProjection> findRecentBills(@Param("since") Instant since, Pageable pageable);

    /**
     * Check if a bill with the given ID exists.
     */
    boolean existsById(String billId);

    /**
     * Find the most recently updated bill.
     */
    @Query("SELECT b FROM BillProjection b ORDER BY b.updatedAt DESC")
    List<BillProjection> findMostRecentlyUpdated(Pageable pageable);

    /**
     * Find bills with their version greater than the specified version.
     */
    List<BillProjection> findByVersionGreaterThan(Long version);

    /**
     * Find bills with their version greater than the specified version with pagination.
     */
    Page<BillProjection> findByVersionGreaterThan(Long version, Pageable pageable);
}