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
}
