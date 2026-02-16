package com.biasharahub.controller;

import com.biasharahub.security.AuthenticatedUser;
import com.biasharahub.service.AccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Micro-accounting endpoints: daily sales/expenses, KRA-ready reports, export.
 */
@RestController
@RequestMapping("/accounting")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'SUPER_ADMIN', 'ASSISTANT_ADMIN')")
public class AccountingController {

    private final AccountingService accountingService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (user == null) return ResponseEntity.status(401).build();
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(accountingService.getDailySummary(user, from, to));
    }

    @GetMapping(value = "/kra-export", produces = "text/csv")
    public ResponseEntity<String> getKraExport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (user == null) return ResponseEntity.status(401).build();
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        List<Map<String, String>> rows = accountingService.getKraExport(user, from, to);
        StringBuilder sb = new StringBuilder();
        sb.append("Item,Value\n");
        for (Map<String, String> row : rows) {
            String item = row.get("Item");
            String value = row.get("Amount");
            if (item != null || value != null) {
                sb.append("\"").append(item != null ? item.replace("\"", "\"\"") : "").append("\",");
                sb.append("\"").append(value != null ? value.replace("\"", "\"\"") : "").append("\"\n");
            } else {
                sb.append("\n");
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"biasharahub-income-" + from + "-to-" + to + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }
}
