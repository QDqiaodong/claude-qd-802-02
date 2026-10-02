package com.linen.laundry.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linen.laundry.common.ApiResponse;
import com.linen.laundry.common.WashProcess;
import com.linen.laundry.domain.WashBatch;
import com.linen.laundry.dto.BatchAdvanceRequest;
import com.linen.laundry.dto.BatchCreateRequest;
import com.linen.laundry.dto.ReviewResolveRequest;
import com.linen.laundry.dto.StepStat;
import com.linen.laundry.service.WashBatchService;

/**
 * 洗涤批次与工序流转（隧道视图）。
 */
@RestController
@RequestMapping("/api/batches")
public class WashBatchController {

    private final WashBatchService batchService;

    public WashBatchController(WashBatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public ApiResponse<List<WashBatch>> list(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String stepCode,
                                             @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(batchService.list(status, stepCode, categoryId));
    }

    /** 工序字典，前端隧道按此顺序铺开 */
    @GetMapping("/steps")
    public ApiResponse<List<String>> steps() {
        return ApiResponse.success(WashProcess.STEP_NAMES);
    }

    /** 各工序站位统计 */
    @GetMapping("/step-stats")
    public ApiResponse<List<StepStat>> stepStats() {
        return ApiResponse.success(batchService.stepStats());
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.success(batchService.summary());
    }

    @GetMapping("/{id}")
    public ApiResponse<WashBatch> detail(@PathVariable Long id) {
        return ApiResponse.success(batchService.get(id));
    }

    @PostMapping
    public ApiResponse<WashBatch> create(@RequestBody BatchCreateRequest request) {
        return ApiResponse.success("排产成功", batchService.create(request));
    }

    /** 推进到下一道工序（不允许跳步；实际值超出批次快照容差则转待复核） */
    @PostMapping("/{id}/advance")
    public ApiResponse<WashBatch> advance(@PathVariable Long id,
                                          @RequestBody(required = false) BatchAdvanceRequest request) {
        return ApiResponse.success("工序已推进", batchService.advance(id, request));
    }

    /** 主管复核参数偏差：按本批次放行或退回重做，结论只影响该批次 */
    @PostMapping("/reviews/{reviewId}/resolve")
    public ApiResponse<WashBatch> resolveReview(@PathVariable Long reviewId,
                                                @RequestBody ReviewResolveRequest request) {
        return ApiResponse.success("复核完成", batchService.resolveReview(reviewId, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        batchService.delete(id);
        return ApiResponse.success("批次已删除", null);
    }
}
