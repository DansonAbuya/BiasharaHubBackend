package com.biasharahub.service;

import com.biasharahub.dto.request.CreateReportRequest;
import com.biasharahub.dto.response.ReportDto;
import com.biasharahub.entity.Report;
import com.biasharahub.entity.User;
import com.biasharahub.repository.ReportRepository;
import com.biasharahub.repository.UserRepository;
import com.biasharahub.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public static final String TARGET_PRODUCT = "product";
    public static final String TARGET_ORDER = "order";
    public static final String TARGET_BUSINESS = "business";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_DISMISSED = "dismissed";

    @Transactional
    public ReportDto createReport(AuthenticatedUser reporter, CreateReportRequest request) {
        User user = userRepository.findById(reporter.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String targetType = request.getTargetType();
        if (targetType == null || !List.of(TARGET_PRODUCT, TARGET_ORDER, TARGET_BUSINESS).contains(targetType.toLowerCase())) {
            throw new IllegalArgumentException("Target type must be product, order, or business");
        }
        Report report = Report.builder()
                .reporter(user)
                .targetType(targetType.toLowerCase())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .description(request.getDescription())
                .status(STATUS_OPEN)
                .build();
        report = reportRepository.save(report);
        return toDto(report);
    }

    public List<ReportDto> listOpenReports() {
        return reportRepository.findByStatusOrderByCreatedAtDesc(STATUS_OPEN)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReportDto resolveReport(UUID reportId, String resolution, String status, AuthenticatedUser resolver) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));
        report.setResolvedAt(Instant.now());
        report.setResolvedByUserId(resolver.userId());
        report.setResolutionNotes(resolution);
        report.setStatus(status != null ? status.toLowerCase() : STATUS_RESOLVED);
        report = reportRepository.save(report);
        return toDto(report);
    }

    private ReportDto toDto(Report r) {
        return ReportDto.builder()
                .reportId(r.getReportId())
                .reporterUserId(r.getReporter().getUserId())
                .reporterEmail(r.getReporter().getEmail())
                .targetType(r.getTargetType())
                .targetId(r.getTargetId())
                .reason(r.getReason())
                .description(r.getDescription())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .resolvedByUserId(r.getResolvedByUserId())
                .resolutionNotes(r.getResolutionNotes())
                .build();
    }
}
