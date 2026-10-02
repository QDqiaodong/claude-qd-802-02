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
import com.linen.laundry.domain.Machine;
import com.linen.laundry.domain.RepairOrder;
import com.linen.laundry.dto.MachineFaultRequest;
import com.linen.laundry.service.MachineService;

/**
 * 设备状态灯墙与故障报修。
 */
@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public ApiResponse<List<Machine>> list(@RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return ApiResponse.success(machineService.list());
        }
        return ApiResponse.success(machineService.list().stream()
                .filter(m -> status.equals(m.getStatus()))
                .toList());
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.success(machineService.summary());
    }

    @GetMapping("/{id}")
    public ApiResponse<Machine> detail(@PathVariable Long id) {
        return ApiResponse.success(machineService.get(id));
    }

    /** 点灯轮转状态：运行 → 待机 → 故障（故障需带维修单） */
    @PostMapping("/{id}/next-status")
    public ApiResponse<Machine> nextStatus(@PathVariable Long id,
                                           @RequestBody(required = false) MachineFaultRequest fault) {
        return ApiResponse.success("状态已切换", machineService.nextStatus(id, fault));
    }

    /** 故障报修 */
    @PostMapping("/{id}/repairs")
    public ApiResponse<RepairOrder> reportFault(@PathVariable Long id, @RequestBody MachineFaultRequest fault) {
        return ApiResponse.success("维修单已登记", machineService.reportFault(id, fault));
    }

    @GetMapping("/repairs")
    public ApiResponse<List<RepairOrder>> repairs() {
        return ApiResponse.success(machineService.repairs());
    }

    /** 维修完工 */
    @PostMapping("/repairs/{repairId}/finish")
    public ApiResponse<RepairOrder> finishRepair(@PathVariable Long repairId) {
        return ApiResponse.success("维修已完工", machineService.finishRepair(repairId));
    }

    @PostMapping
    public ApiResponse<Machine> create(@RequestBody Machine machine) {
        return ApiResponse.success("设备已入台账", machineService.create(machine));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        machineService.delete(id);
        return ApiResponse.success("设备已删除", null);
    }
}
