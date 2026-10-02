package com.linen.laundry.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linen.laundry.common.ApiResponse;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.dto.DashboardOverview;
import com.linen.laundry.repository.OperationLogRepository;
import com.linen.laundry.service.DashboardService;

/**
 * 车间总览。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final OperationLogRepository logRepository;

    public DashboardController(DashboardService dashboardService, OperationLogRepository logRepository) {
        this.dashboardService = dashboardService;
        this.logRepository = logRepository;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverview> overview() {
        return ApiResponse.success(dashboardService.overview());
    }

    @GetMapping("/logs")
    public ApiResponse<List<OperationLog>> logs() {
        return ApiResponse.success(logRepository.findTop20ByOrderByIdDesc());
    }
}
