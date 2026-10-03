package com.linen.laundry.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.linen.laundry.common.BizException;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.domain.Outlet;
import com.linen.laundry.domain.PickupOrder;
import com.linen.laundry.dto.DeliverRequest;
import com.linen.laundry.dto.DispatchRow;
import com.linen.laundry.dto.PickupRegisterRequest;
import com.linen.laundry.mapper.OutletMapper;
import com.linen.laundry.mapper.PickupOrderMapper;
import com.linen.laundry.repository.OperationLogRepository;

/**
 * 收送单：收发时间、脏件数、净件数、司机车牌、短少与赔付。
 */
@Service
public class PickupOrderService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PickupOrderMapper orderMapper;
    private final OutletMapper outletMapper;
    private final OperationLogRepository logRepository;
    private final DashboardService dashboardService;

    public PickupOrderService(PickupOrderMapper orderMapper, OutletMapper outletMapper,
                              OperationLogRepository logRepository, DashboardService dashboardService) {
        this.orderMapper = orderMapper;
        this.outletMapper = outletMapper;
        this.logRepository = logRepository;
        this.dashboardService = dashboardService;
    }

    public List<PickupOrder> list() {
        return orderMapper.selectAll();
    }

    public List<PickupOrder> search(Long outletId, String status, String driver,
                                    String from, String to, Boolean shortageOnly) {
        return orderMapper.search(outletId, status, driver, from, to, shortageOnly);
    }

    public PickupOrder get(Long id) {
        PickupOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("收送单不存在：id=" + id);
        }
        return order;
    }

    /**
     * 网点抽屉登记收发件数。规则：净件数不得大于脏件数；短少必须登记赔付金额。
     */
    @Transactional
    public PickupOrder register(PickupRegisterRequest request) {
        if (request.getOutletId() == null) {
            throw new BizException("请选择要登记的网点");
        }
        Outlet outlet = outletMapper.selectById(request.getOutletId());
        if (outlet == null) {
            throw new BizException("网点不存在：id=" + request.getOutletId());
        }
        int dirty = request.getDirtyQty() == null ? 0 : request.getDirtyQty();
        int clean = request.getCleanQty() == null ? 0 : request.getCleanQty();
        if (dirty <= 0) {
            throw new BizException("脏件数必须大于 0");
        }
        if (clean < 0) {
            throw new BizException("净件数不能为负数");
        }
        if (clean > dirty) {
            throw new BizException("净件数 " + clean + " 不得大于脏件数 " + dirty + "，请核对收发件数");
        }
        int shortage = dirty - clean;
        BigDecimal compensation = request.getCompensation() == null
                ? BigDecimal.ZERO
                : request.getCompensation();
        if (shortage > 0 && compensation.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("短少 " + shortage + " 件，必须登记赔付金额");
        }
        if (shortage == 0 && compensation.compareTo(BigDecimal.ZERO) != 0) {
            throw new BizException("无短少时不应登记赔付金额");
        }

        PickupOrder order = new PickupOrder();
        order.setOrderNo(nextOrderNo());
        order.setOutletId(outlet.getId());
        order.setPickupTime(LocalDateTime.now());
        order.setDirtyQty(dirty);
        order.setCleanQty(clean);
        order.setShortageQty(shortage);
        order.setCompensation(compensation);
        order.setDriver(request.getDriver());
        order.setPlate(request.getPlate());
        order.setStatus(clean > 0 ? "DELIVERED" : "PICKED");
        order.setDeliverTime(clean > 0 ? LocalDateTime.now() : null);
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        outletMapper.reducePending(outlet.getId(), dirty);
        log("ORDER", order.getId(), "登记收送", outlet.getName() + " 脏 " + dirty + " / 净 " + clean
                + (shortage > 0 ? "，短少 " + shortage + " 件赔付 " + compensation : ""));
        evictOverviewAfterCommit();
        return get(order.getId());
    }

    /** 送净登记：净件数不得大于脏件数 */
    @Transactional
    public PickupOrder deliver(Long id, DeliverRequest request) {
        PickupOrder order = get(id);
        if ("DELIVERED".equals(order.getStatus())) {
            throw new BizException("该收送单已送净，无需重复登记");
        }
        int dirty = order.getDirtyQty() == null ? 0 : order.getDirtyQty();
        int clean = request.getCleanQty() == null ? 0 : request.getCleanQty();
        if (clean <= 0) {
            throw new BizException("净件数必须大于 0");
        }
        if (clean > dirty) {
            throw new BizException("净件数 " + clean + " 不得大于脏件数 " + dirty + "，请核对收发件数");
        }
        int shortage = dirty - clean;
        BigDecimal compensation = request.getCompensation() == null ? BigDecimal.ZERO : request.getCompensation();
        if (shortage > 0 && compensation.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("短少 " + shortage + " 件，必须登记赔付金额");
        }
        if (shortage == 0 && compensation.compareTo(BigDecimal.ZERO) != 0) {
            throw new BizException("无短少时不应登记赔付金额");
        }
        order.setCleanQty(clean);
        order.setShortageQty(shortage);
        order.setCompensation(compensation);
        order.setDeliverTime(LocalDateTime.now());
        order.setStatus("DELIVERED");
        if (request.getDriver() != null) {
            order.setDriver(request.getDriver());
        }
        if (request.getPlate() != null) {
            order.setPlate(request.getPlate());
        }
        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }
        orderMapper.updateDeliver(order);
        log("ORDER", id, "送净登记", "净 " + clean + "，短少 " + shortage);
        evictOverviewAfterCommit();
        return get(id);
    }

    /**
     * 指定自然日的配送看板（不传 = 当天）。
     */
    public List<DispatchRow> dispatchBoard(LocalDate day) {
        return orderMapper.dispatchBoard(day == null ? LocalDate.now() : day);
    }

    /**
     * 指定自然日的收送汇总（不传 = 当天）。台账顶部与首页 KPI 共用同一 SQL 口径：
     * 收脏按收脏时间、送净 / 短少 / 赔付按送净时间。
     */
    public Map<String, Object> daySummary(LocalDate day) {
        return orderMapper.daySummary(day == null ? LocalDate.now() : day);
    }

    /** 解析 yyyy-MM-dd 查询参数；为空取当天；格式非法直接给中文业务错误。 */
    public static LocalDate parseDay(String day) {
        if (day == null || day.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(day.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("日期格式应为 yyyy-MM-dd，收到：" + day);
        }
    }

    @Transactional
    public void delete(Long id) {
        PickupOrder order = get(id);
        orderMapper.deleteLines(id);
        orderMapper.delete(id);
        log("ORDER", id, "删除收送单", order.getOrderNo());
        evictOverviewAfterCommit();
    }

    /**
     * 写操作事务提交后再清首页 overview 缓存：保证刷新台账与首页时拿到同一批最新数据；
     * 非事务上下文（理论上不会走到）直接清除。Redis 不可用时由 DashboardService 内部吞掉异常。
     */
    private void evictOverviewAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dashboardService.evictOverviewCache();
                }
            });
        } else {
            dashboardService.evictOverviewCache();
        }
    }

    private String nextOrderNo() {
        return "RS" + LocalDateTime.now().format(NO_DATE) + "-"
                + String.format("%03d", orderMapper.maxId() + 1);
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
}
