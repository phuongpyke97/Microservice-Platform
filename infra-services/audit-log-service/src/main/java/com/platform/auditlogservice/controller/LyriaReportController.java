package com.platform.auditlogservice.controller;

import com.platform.auditlogservice.dto.response.LyriaDailyStatResponse;
import com.platform.auditlogservice.dto.response.LyriaRequestLogResponse;
import com.platform.auditlogservice.dto.response.LyriaSummaryResponse;
import com.platform.auditlogservice.service.LyriaReportService;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/lyria")
public class LyriaReportController {

    private final LyriaReportService reportService;

    public LyriaReportController(LyriaReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public ApiResponse<LyriaSummaryResponse> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(reportService.getSummary(startDate, endDate));
    }

    @GetMapping("/daily")
    public ApiResponse<List<LyriaDailyStatResponse>> getDailyStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(reportService.getDailyStats(startDate, endDate));
    }

    @GetMapping("/requests")
    public ApiResponse<PageResponse<LyriaRequestLogResponse>> getRequestLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String msisdn,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(reportService.getRequestLogs(startDate, endDate, msisdn, status, pageable));
    }

    @PostMapping("/reconcile")
    public ApiResponse<Void> reconcile(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            reportService.reconcileDailyStats(current);
            current = current.plusDays(1);
        }
        return ApiResponse.success(null);
    }
}
