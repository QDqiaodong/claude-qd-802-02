package com.linen.laundry.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linen.laundry.common.ApiResponse;
import com.linen.laundry.common.ContaminationType;
import com.linen.laundry.domain.ContaminationEvent;
import com.linen.laundry.domain.RewashOrder;
import com.linen.laundry.dto.ContaminationActionRequest;
import com.linen.laundry.dto.ContaminationEventRequest;
import com.linen.laundry.service.ContaminationService;

/**
 * 医院布草污染事件追溯与隔离处置闭环。
 */
@RestController
@RequestMapping("/api/contamination")
public class ContaminationController {

    private final ContaminationService service;

    public ContaminationController(ContaminationService service) {
        this.service = service;
    }

    /** 污染类型字典 */
    @GetMapping("/types")
    public ApiResponse<List<Map<String, String>>> types() {
        return ApiResponse.success(ContaminationType.TYPE_CODES.stream()
                .map(code -> Map.of("code", code, "name", ContaminationType.nameOf(code)))
                .toList());
    }

    @GetMapping("/events")
    public ApiResponse<List<ContaminationEvent>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/events/{id}")
    public ApiResponse<ContaminationEvent> detail(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    /** 车间人员登记污染事件（待确认） */
    @PostMapping("/events")
    public ApiResponse<ContaminationEvent> create(@RequestBody ContaminationEventRequest request) {
        return ApiResponse.success("污染事件已登记", service.create(request));
    }

    /**
     * 确认事件：按设备 + 影响时段命中批次，生成不可重复的隔离记录并锁批次。
     * 行锁 + 状态守卫 + 唯一约束，重复点击 / 并发请求只落一份有效结果。
     */
    @PostMapping("/events/{id}/confirm")
    public ApiResponse<ContaminationEvent> confirm(@PathVariable Long id,
                                                   @RequestBody(required = false) ContaminationActionRequest request) {
        return ApiResponse.success("污染事件已确认，命中批次已隔离", service.confirm(id, request));
    }

    /**
     * 复核放行：仅当全部关联复洗批次完成（无在产 / 失败 / 删除）才放行，否则带原因拒绝。
     */
    @PostMapping("/events/{id}/release")
    public ApiResponse<ContaminationEvent> release(@PathVariable Long id,
                                                   @RequestBody(required = false) ContaminationActionRequest request) {
        return ApiResponse.success("污染事件已复核放行", service.release(id, request));
    }

    /** 为隔离批次发起复洗：生成可追溯的新批次并关联原批次，同一隔离批次至多一份有效复洗 */
    @PostMapping("/events/{eventId}/isolations/{isolationId}/rewash")
    public ApiResponse<RewashOrder> createRewash(@PathVariable Long eventId,
                                                 @PathVariable Long isolationId,
                                                 @RequestBody(required = false) ContaminationActionRequest request) {
        return ApiResponse.success("复洗已发起，已生成可追溯复洗批次",
                service.createRewash(eventId, isolationId, request));
    }

    /** 标记复洗失败：原隔离批次继续保持隔离，可重新发起 */
    @PostMapping("/rewash/{rewashId}/fail")
    public ApiResponse<RewashOrder> failRewash(@PathVariable Long rewashId,
                                               @RequestBody(required = false) ContaminationActionRequest request) {
        return ApiResponse.success("复洗已标记失败，批次保持隔离", service.failRewash(rewashId, request));
    }

    /** 删除复洗（保留可追溯记录）：原隔离批次保持隔离，可重新发起 */
    @PostMapping("/rewash/{rewashId}/delete")
    public ApiResponse<RewashOrder> deleteRewash(@PathVariable Long rewashId,
                                                 @RequestBody(required = false) ContaminationActionRequest request) {
        return ApiResponse.success("复洗已删除，批次保持隔离", service.deleteRewash(rewashId, request));
    }
}
