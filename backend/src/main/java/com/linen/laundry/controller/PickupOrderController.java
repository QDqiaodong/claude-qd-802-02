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
import com.linen.laundry.domain.PickupOrder;
import com.linen.laundry.dto.DeliverRequest;
import com.linen.laundry.dto.DispatchRow;
import com.linen.laundry.dto.PickupRegisterRequest;
import com.linen.laundry.service.PickupOrderService;

/**
 * 收送单：收发时间、脏件数、净件数、司机车牌、异常短少与赔付。
 */
@RestController
@RequestMapping("/api/orders")
public class PickupOrderController {

    private final PickupOrderService orderService;

    public PickupOrderController(PickupOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<PickupOrder>> list(@RequestParam(required = false) Long outletId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String driver,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to,
                                               @RequestParam(required = false) Boolean shortageOnly) {
        if (outletId == null && status == null && driver == null && from == null && to == null
                && !Boolean.TRUE.equals(shortageOnly)) {
            return ApiResponse.success(orderService.list());
        }
        return ApiResponse.success(orderService.search(outletId, status, driver, from, to, shortageOnly));
    }

    @GetMapping("/today-summary")
    public ApiResponse<Map<String, Object>> todaySummary() {
        return ApiResponse.success(orderService.todaySummary());
    }

    /** 配送看板：按司机 / 车牌聚合 */
    @GetMapping("/dispatch-board")
    public ApiResponse<List<DispatchRow>> dispatchBoard() {
        return ApiResponse.success(orderService.dispatchBoard());
    }

    @GetMapping("/{id}")
    public ApiResponse<PickupOrder> detail(@PathVariable Long id) {
        return ApiResponse.success(orderService.get(id));
    }

    @PostMapping
    public ApiResponse<PickupOrder> create(@RequestBody PickupRegisterRequest request) {
        return ApiResponse.success("收送单已登记", orderService.register(request));
    }

    /** 送净登记（含短少赔付） */
    @PostMapping("/{id}/deliver")
    public ApiResponse<PickupOrder> deliver(@PathVariable Long id, @RequestBody DeliverRequest request) {
        return ApiResponse.success("送净已登记", orderService.deliver(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ApiResponse.success("收送单已删除", null);
    }
}
