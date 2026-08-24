package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.Report;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateReportRequest;
import com.datn.foodshare.domain.request.UpdateReportStatusRequest;
import com.datn.foodshare.domain.response.ReportResponse;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.repository.ReportRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import com.datn.foodshare.util.constant.ReportReferenceType;
import com.datn.foodshare.util.constant.ReportStatus;
import com.datn.foodshare.util.constant.ReportType;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReportService reportService;

    private static final Long USER_ID = 100L;
    private static final Long REPORT_ID = 1L;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, userRepository, eventPublisher);
    }

    @Test
    void createReport_success() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));

            when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
                Report report = inv.getArgument(0);
                report.setId(REPORT_ID);
                return report;
            });

            CreateReportRequest request = new CreateReportRequest();
            request.setTitle("Lừa đảo");
            request.setContent("Bán hàng không đúng mô tả");
            request.setReportType(ReportType.COMPLAINT);
            request.setReferenceType(ReportReferenceType.ORDER);
            request.setReferenceId(50L);

            ReportResponse response = reportService.createReport(request);

            assertNotNull(response);
            assertEquals("Lừa đảo", response.getTitle());
            assertEquals(ReportStatus.PENDING, response.getReportStatus());
            verify(reportRepository).save(any(Report.class));
        }
    }

    @Test
    void getMyReportDetail_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));

            Report report = testReport();
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

            ReportResponse response = reportService.getMyReportDetail(REPORT_ID);

            assertNotNull(response);
            assertEquals(REPORT_ID, response.getId());
        }
    }

    @Test
    void getMyReportDetail_rejectsOtherUser() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(USER_ID));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));

            Report report = testReport();
            User otherUser = new User();
            otherUser.setId(999L);
            report.setReporter(otherUser);

            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

            assertThrows(PermissionException.class, () -> reportService.getMyReportDetail(REPORT_ID));
        }
    }

    @Test
    void adminUpdateReportStatus_success_publishesEvent() {
        Report report = testReport();
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateReportStatusRequest request = new UpdateReportStatusRequest();
        request.setReportStatus(ReportStatus.RESOLVED);
        request.setResponse("Đã xử lý và khóa tài khoản vi phạm");

        ReportResponse response = reportService.adminUpdateReportStatus(REPORT_ID, request);

        assertEquals(ReportStatus.RESOLVED, response.getReportStatus());
        assertNotNull(response.getResolvedAt());
        assertEquals("Đã xử lý và khóa tài khoản vi phạm", response.getResponse());

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        NotificationEvent event = eventCaptor.getValue();
        assertEquals(USER_ID, event.getUser().getId());
        assertEquals(NotificationReferenceType.REPORT, event.getReferenceType());
        assertEquals(REPORT_ID, event.getReferenceId());
    }

    @Test
    void adminGetReportDetail_changesStatusToReviewing() {
        Report report = testReport(); // status is PENDING by default
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse response = reportService.adminGetReportDetail(REPORT_ID);

        assertEquals(ReportStatus.REVIEWING, response.getReportStatus());
        assertEquals(ReportStatus.REVIEWING, report.getReportStatus());
        verify(reportRepository).save(report);
    }

    @Test
    void adminGetReportDetail_keepsStatusIfNotPending() {
        Report report = testReport();
        report.setReportStatus(ReportStatus.RESOLVED);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        ReportResponse response = reportService.adminGetReportDetail(REPORT_ID);

        assertEquals(ReportStatus.RESOLVED, response.getReportStatus());
        verify(reportRepository, never()).save(any(Report.class));
    }

    @Test
    void adminGetAllReports_withoutStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Report> page = new PageImpl<>(List.of(testReport()), pageable, 1);
        when(reportRepository.findAllWithReporter(pageable)).thenReturn(page);

        Page<ReportResponse> result = reportService.adminGetAllReports(null, pageable);

        assertEquals(1, result.getTotalElements());
        verify(reportRepository).findAllWithReporter(pageable);
    }

    @Test
    void adminGetAllReports_withStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Report> page = new PageImpl<>(List.of(testReport()), pageable, 1);
        when(reportRepository.findByReportStatus(ReportStatus.PENDING, pageable)).thenReturn(page);

        Page<ReportResponse> result = reportService.adminGetAllReports(ReportStatus.PENDING, pageable);

        assertEquals(1, result.getTotalElements());
        verify(reportRepository).findByReportStatus(ReportStatus.PENDING, pageable);
    }

    private User testUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setFullName("Test User");
        return user;
    }

    private Report testReport() {
        Report report = new Report();
        report.setId(REPORT_ID);
        report.setReporter(testUser());
        report.setTitle("Test Title");
        report.setContent("Test Content");
        report.setReportType(ReportType.ISSUE);
        report.setReportStatus(ReportStatus.PENDING);
        report.setReferenceType(ReportReferenceType.ORDER);
        report.setReferenceId(50L);
        return report;
    }
}
