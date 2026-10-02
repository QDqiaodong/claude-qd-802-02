package com.linen.laundry.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linen.laundry.common.ApiResponse;
import com.linen.laundry.domain.Outlet;
import com.linen.laundry.domain.PickupOrder;
import com.linen.laundry.dto.PickupRegisterRequest;
import com.linen.laundry.service.OutletService;
import com.linen.laundry.service.PickupOrderService;

/**
 * 客户网点：档案、预警、网点维度的收送单与收发登记。
 */
@RestController
@RequestMapping("/api/outlets")
public class OutletController {

    private final OutletService outletService;
    private final PickupOrderService orderService;

    public OutletController(OutletService outletService, PickupOrderService orderService) {
        this.outletService = outletService;
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<Outlet>> list(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String outletType,
                                          @RequestParam(required = false) String settleType,
                                          @RequestParam(required = false) Boolean warnOnly) {
        return ApiResponse.success(outletService.list(keyword, outletType, settleType, warnOnly));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.success(outletService.summary());
    }

    @GetMapping("/{id}")
    public ApiResponse<Outlet> detail(@PathVariable Long id) {
        return ApiResponse.success(outletService.get(id));
    }

    /** 网点抽屉里的最近收送单 */
    @GetMapping("/{id}/orders")
    public ApiResponse<List<PickupOrder>> orders(@PathVariable Long id) {
        return ApiResponse.success(orderService.search(id, null, null, null, null, null));
    }

    /** 网点抽屉登记收发件数 */
    @PostMapping("/{id}/pickup")
    public ApiResponse<PickupOrder> pickup(@PathVariable Long id, @RequestBody PickupRegisterRequest request) {
        request.setOutletId(id);
        return ApiResponse.success("登记成功", orderService.register(request));
    }

    @PostMapping
    public ApiResponse<Outlet> create(@RequestBody Outlet outlet) {
        return ApiResponse.success("网点已创建", outletService.create(outlet));
    }

    @PutMapping("/{id}")
    public ApiResponse<Outlet> update(@PathVariable Long id, @RequestBody Outlet outlet) {
        return ApiResponse.success("网点已更新", outletService.update(id, outlet));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        outletService.delete(id);
        return ApiResponse.success("网点已删除", null);
    }
}
