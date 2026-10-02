package com.linen.laundry.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linen.laundry.common.BizException;
import com.linen.laundry.domain.Outlet;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.mapper.OutletMapper;
import com.linen.laundry.repository.OperationLogRepository;

/**
 * 客户网点：网点档案、月结额度、脏布草待收量预警。
 */
@Service
public class OutletService {

    private final OutletMapper outletMapper;
    private final OperationLogRepository logRepository;

    public OutletService(OutletMapper outletMapper, OperationLogRepository logRepository) {
        this.outletMapper = outletMapper;
        this.logRepository = logRepository;
    }

    public List<Outlet> list(String keyword, String outletType, String settleType, Boolean warnOnly) {
        if (isBlank(keyword) && isBlank(outletType) && isBlank(settleType) && !Boolean.TRUE.equals(warnOnly)) {
            return outletMapper.selectAll();
        }
        return outletMapper.search(keyword, outletType, settleType, warnOnly);
    }

    public Outlet get(Long id) {
        Outlet outlet = outletMapper.selectById(id);
        if (outlet == null) {
            throw new BizException("网点不存在：id=" + id);
        }
        return outlet;
    }

    public Map<String, Object> summary() {
        return outletMapper.summary();
    }

    @Transactional
    public Outlet create(Outlet outlet) {
        if (isBlank(outlet.getCode()) || isBlank(outlet.getName())) {
            throw new BizException("网点编号与网点名称不能为空");
        }
        if (outletMapper.selectByCode(outlet.getCode()) != null) {
            throw new BizException("网点编号已存在：" + outlet.getCode());
        }
        if (!"HOTEL".equals(outlet.getOutletType()) && !"HOSPITAL".equals(outlet.getOutletType())) {
            throw new BizException("网点类型只能是 HOTEL（酒店）或 HOSPITAL（医院）");
        }
        if ("MONTHLY".equals(outlet.getSettleType())
                && (outlet.getCreditLimit() == null || outlet.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BizException("月结网点必须设置大于 0 的月结额度");
        }
        if (outlet.getDirtyPending() != null && outlet.getDirtyPending() < 0) {
            throw new BizException("脏布草待收量不能为负数");
        }
        if (outlet.getCreditLimit() == null) {
            outlet.setCreditLimit(BigDecimal.ZERO);
        }
        if (outlet.getDirtyPending() == null) {
            outlet.setDirtyPending(0);
        }
        if (outlet.getWarnThreshold() == null) {
            outlet.setWarnThreshold(0);
        }
        if (isBlank(outlet.getStatus())) {
            outlet.setStatus("ACTIVE");
        }
        outlet.setCreatedAt(LocalDateTime.now());
        outletMapper.insert(outlet);
        log("OUTLET", outlet.getId(), "新增网点", outlet.getName());
        return outlet;
    }

    @Transactional
    public Outlet update(Long id, Outlet outlet) {
        Outlet exists = get(id);
        outlet.setId(id);
        if (isBlank(outlet.getName())) {
            outlet.setName(exists.getName());
        }
        if (outlet.getCreditLimit() == null) {
            outlet.setCreditLimit(exists.getCreditLimit());
        }
        if ("MONTHLY".equals(outlet.getSettleType())
                && (outlet.getCreditLimit() == null || outlet.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BizException("月结网点必须设置大于 0 的月结额度");
        }
        outletMapper.update(outlet);
        log("OUTLET", id, "修改网点", outlet.getName());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        Outlet exists = get(id);
        if (outletMapper.countOrders(id) > 0) {
            throw new BizException("网点【" + exists.getName() + "】已存在收送单，不能删除");
        }
        outletMapper.delete(id);
        log("OUTLET", id, "删除网点", exists.getName());
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
