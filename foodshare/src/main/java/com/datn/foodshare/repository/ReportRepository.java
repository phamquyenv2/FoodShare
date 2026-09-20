package com.datn.foodshare.repository;

import com.datn.foodshare.domain.entity.Report;
import com.datn.foodshare.util.constant.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter WHERE r.reporter.id = :reporterId")
    Page<Report> findByReporterId(@Param("reporterId") Long reporterId, Pageable pageable);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter")
    Page<Report> findAllWithReporter(Pageable pageable);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter WHERE r.reportStatus = :status")
    Page<Report> findByReportStatus(@Param("status") ReportStatus status, Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.referenceType = :refType AND r.referenceId = :refId AND (r.reportType = com.datn.foodshare.util.constant.ReportType.REFUND OR (r.reportType = com.datn.foodshare.util.constant.ReportType.COMPLAINT AND LOWER(r.title) LIKE '%hoàn tiền%')) ORDER BY r.createdAt DESC")
    java.util.List<Report> findRefundReportsByOrder(
            @Param("refType") com.datn.foodshare.util.constant.ReportReferenceType refType,
            @Param("refId") Long refId);

    @Query("SELECT COUNT(r) > 0 FROM Report r WHERE r.referenceType = :refType AND r.referenceId = :refId AND (r.reportType = com.datn.foodshare.util.constant.ReportType.REFUND OR (r.reportType = com.datn.foodshare.util.constant.ReportType.COMPLAINT AND LOWER(r.title) LIKE '%hoàn tiền%')) AND r.reportStatus IN :statuses")
    boolean existsActiveRefundReport(
            @Param("refType") com.datn.foodshare.util.constant.ReportReferenceType refType,
            @Param("refId") Long refId,
            @Param("statuses") java.util.Collection<ReportStatus> statuses);
}
