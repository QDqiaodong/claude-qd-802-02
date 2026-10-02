package com.linen.laundry.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linen.laundry.common.BizException;
import com.linen.laundry.domain.Machine;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.domain.RepairOrder;
import com.linen.laundry.dto.MachineFaultRequest;
import com.linen.laundry.mapper.MachineMapper;
import com.linen.laundry.mapper.RepairOrderMapper;
import com.linen.laundry.mapper.WashBatchMapper;
import com.linen.laundry.repository.OperationLogRepository;

/**
 * 设备台账：状态灯墙轮转、故障报修与维修单。
 */
@Service
public class MachineService {

    private final MachineMapper machineMapper;
    private final RepairOrderMapper repairMapper;
    private final WashBatchMapper batchMapper;
    private final OperationLogRepository logRepository;

    public MachineService(MachineMapper machineMapper, RepairOrderMapper repairMapper,
                          WashBatchMapper batchMapper, OperationLogRepository logRepository) {
        this.machineMapper = machineMapper;
        this.repairMapper = repairMapper;
        this.batchMapper = batchMapper;
        this.logRepository = logRepository;
    }

    public List<Machine> list() {
        List<Machine> machines = machineMapper.selectAll();
        machines.forEach(this::fillContamName);
        return machines;
    }

    public Machine get(Long id) {
        Machine machine = machineMapper.selectById(id);
        if (machine == null) {
            throw new BizException("设备不存在：id=" + id);
        }
        fillContamName(machine);
        return machine;
    }

    /** 灯墙污染角标的类型列存的是污染类型编码，统一转中文名 */
    private void fillContamName(Machine machine) {
        if (machine.getActiveContamTypeName() != null) {
            machine.setActiveContamTypeName(
                    com.linen.laundry.common.ContaminationType.nameOf(machine.getActiveContamTypeName()));
        }
    }

    public Map<String, Object> summary() {
        return machineMapper.summary();
    }

    public List<RepairOrder> repairs() {
        return repairMapper.selectAll();
    }

    /**
     * 灯墙点灯轮转：运行 → 待机 → 故障 → 运行。
     * 置为故障必须同时登记维修单；故障设备存在未完工维修单时不允许直接恢复运行。
     */
    @Transactional
    public Machine nextStatus(Long id, MachineFaultRequest fault) {
        Machine machine = get(id);
        String current = machine.getStatus();
        String target = "RUNNING".equals(current) ? "IDLE"
                : "IDLE".equals(current) ? "FAULT"
                : "RUNNING";

        if ("FAULT".equals(target)) {
            if (fault == null || isBlank(fault.getFaultDesc()) || isBlank(fault.getRepairMan())) {
                throw new BizException("设备置为故障必须填写故障描述与维修人，并登记维修单");
            }
            if (fault.getCost() == null || fault.getCost().compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("维修费用不能为负数");
            }
            machineMapper.updateStatus(id, "FAULT");
            repairMapper.insert(newRepair(id, fault));
            log("MACHINE", id, "故障报修", machine.getName() + "：" + fault.getFaultDesc());
            return get(id);
        }

        if ("RUNNING".equals(target) && "FAULT".equals(current)) {
            long open = machine.getRepairs() == null ? 0
                    : machine.getRepairs().stream().filter(r -> "OPEN".equals(r.getStatus())).count();
            if (open > 0) {
                throw new BizException("【" + machine.getName() + "】存在 " + open
                        + " 张未完工维修单，请先完工再恢复运行");
            }
        }
        if ("IDLE".equals(target) && "RUNNING".equals(current)) {
            List<com.linen.laundry.domain.WashBatch> running = batchMapper.tunnel("RUNNING", null, null);
            boolean busy = running.stream().anyMatch(b -> id.equals(b.getMachineId()));
            if (busy) {
                throw new BizException("【" + machine.getName() + "】尚有在产批次，不能切到待机");
            }
        }
        machineMapper.updateStatus(id, target);
        log("MACHINE", id, "切换状态", machine.getName() + " " + nameOf(current) + " → " + nameOf(target));
        return get(id);
    }

    /** 单独报修（不切状态时也可用） */
    @Transactional
    public RepairOrder reportFault(Long id, MachineFaultRequest fault) {
        Machine machine = get(id);
        if (fault == null || isBlank(fault.getFaultDesc())) {
            throw new BizException("故障描述不能为空");
        }
        if (fault.getCost() == null || fault.getCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("维修费用不能为负数");
        }
        RepairOrder repair = newRepair(id, fault);
        repairMapper.insert(repair);
        if (!"FAULT".equals(machine.getStatus())) {
            machineMapper.updateStatus(id, "FAULT");
        }
        log("MACHINE", id, "登记维修单", machine.getName() + "：" + fault.getFaultDesc());
        return repair;
    }

    /** 维修完工：无其它未完工维修单时设备回到待机 */
    @Transactional
    public RepairOrder finishRepair(Long repairId) {
        RepairOrder repair = repairMapper.selectById(repairId);
        if (repair == null) {
            throw new BizException("维修单不存在：id=" + repairId);
        }
        if ("DONE".equals(repair.getStatus())) {
            throw new BizException("该维修单已完工");
        }
        repairMapper.finish(repairId);
        Machine machine = machineMapper.selectById(repair.getMachineId());
        if (machine != null) {
            long open = machine.getRepairs() == null ? 0
                    : machine.getRepairs().stream().filter(r -> "OPEN".equals(r.getStatus()) && !r.getId().equals(repairId)).count();
            if (open == 0 && "FAULT".equals(machine.getStatus())) {
                machineMapper.updateStatus(machine.getId(), "IDLE");
            }
        }
        log("MACHINE", repair.getMachineId(), "维修完工", "维修单 #" + repairId + " 完工");
        return repairMapper.selectById(repairId);
    }

    @Transactional
    public Machine create(Machine machine) {
        if (isBlank(machine.getCode()) || isBlank(machine.getName())) {
            throw new BizException("设备编号与名称不能为空");
        }
        if (machine.getCapacityKg() == null || machine.getCapacityKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("设备容量必须大于 0");
        }
        if (isBlank(machine.getStatus())) {
            machine.setStatus("IDLE");
        }
        if (machine.getRunHours() == null) {
            machine.setRunHours(BigDecimal.ZERO);
        }
        machineMapper.insert(machine);
        log("MACHINE", machine.getId(), "新增设备", machine.getName());
        return machine;
    }

    @Transactional
    public void delete(Long id) {
        Machine machine = get(id);
        boolean busy = batchMapper.tunnel("RUNNING", null, null).stream()
                .anyMatch(b -> id.equals(b.getMachineId()));
        if (busy) {
            throw new BizException("【" + machine.getName() + "】尚有在产批次，不能删除");
        }
        machineMapper.delete(id);
        log("MACHINE", id, "删除设备", machine.getName());
    }

    private RepairOrder newRepair(Long machineId, MachineFaultRequest fault) {
        RepairOrder repair = new RepairOrder();
        repair.setMachineId(machineId);
        repair.setFaultDesc(fault.getFaultDesc());
        repair.setRepairMan(fault.getRepairMan() == null ? "待指派" : fault.getRepairMan());
        repair.setCost(fault.getCost() == null ? BigDecimal.ZERO : fault.getCost());
        repair.setReportTime(LocalDateTime.now());
        repair.setStatus("OPEN");
        return repair;
    }

    private String nameOf(String status) {
        if ("RUNNING".equals(status)) {
            return "运行";
        }
        if ("IDLE".equals(status)) {
            return "待机";
        }
        if ("FAULT".equals(status)) {
            return "故障";
        }
        return status;
    }

    private void log(String bizType, Long bizId, String action, String detail) {
        OperationLog entity = new OperationLog();
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setActionName(action);
        entity.setDetail(detail);
        entity.setCreatedAt(LocalDateTime.now());
        logRepository.save(entity);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
