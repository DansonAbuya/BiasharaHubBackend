package com.biasharahub.controller;

import com.biasharahub.dto.request.CreateReportRequest;
import com.biasharahub.dto.response.ReportDto;
import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reports: customers can report a product, order, or business.
 * Admin can list open reports and resolve them.
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportDto> createReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateReportRequest request) {
        if (user == null) return ResponseEntity.status(401).build();
        ReportDto report = reportService.createReport(user, request);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/open")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<List<ReportDto>> listOpenReports() {
        return ResponseEntity.ok(reportService.listOpenReports());
    }

    @PatchMapping("/{reportId}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ASSISTANT_ADMIN')")
    public ResponseEntity<ReportDto> resolveReport(
            @PathVariable UUID reportId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        String resolution = body.get("resolutionNotes");
        String status = body.get("status");
        ReportDto updated = reportService.resolveReport(reportId, resolution, status, admin);
        return ResponseEntity.ok(updated);
    }
}
