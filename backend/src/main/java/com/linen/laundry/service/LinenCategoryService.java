package com.linen.laundry.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linen.laundry.common.BizException;
import com.linen.laundry.domain.LinenCategory;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.dto.CountItem;
import com.linen.laundry.dto.CountResult;
import com.linen.laundry.mapper.LinenCategoryMapper;
import com.linen.laundry.repository.OperationLogRepository;

/**
 * 布草品类台账：在库数、报废率、最低周转量与快速盘点损益。
 */
@Service
public class LinenCategoryService {

    private final LinenCategoryMapper categoryMapper;
    private final OperationLogRepository logRepository;

    public LinenCategoryService(LinenCategoryMapper categoryMapper, OperationLogRepository logRepository) {
        this.categoryMapper = categoryMapper;
        this.logRepository = logRepository;
    }

    public List<LinenCategory> list() {
        return categoryMapper.selectAll();
    }

    public LinenCategory get(Long id) {
        LinenCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException("布草品类不存在：id=" + id);
        }
        return category;
    }

    /** 在库数低于最低周转量的品类，带出缺口数 */
    public List<LinenCategory> shortage() {
        List<LinenCategory> categories = categoryMapper.selectShortage();
        for (LinenCategory c : categories) {
            c.setGap(Math.max(0, nullToInt(c.getMinTurnover()) - nullToInt(c.getStockQty())));
        }
        return categories;
    }

    @Transactional
    public LinenCategory create(LinenCategory category) {
        if (isBlank(category.getCode()) || isBlank(category.getName())) {
            throw new BizException("品类编号与品类名称不能为空");
        }
        if (categoryMapper.selectByCode(category.getCode()) != null) {
            throw new BizException("品类编号已存在：" + category.getCode());
        }
        if (category.getUnitPrice() == null || category.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("洗涤单价必须大于 0");
        }
        if (category.getScrapRate() != null
                && (category.getScrapRate().compareTo(BigDecimal.ZERO) < 0
                    || category.getScrapRate().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BizException("报废率必须在 0~100 之间");
        }
        if (category.getStockQty() == null) {
            category.setStockQty(0);
        }
        if (category.getMinTurnover() == null) {
            category.setMinTurnover(0);
        }
        categoryMapper.insert(category);
        log("CATEGORY", category.getId(), "新增品类", category.getName());
        return category;
    }

    /** 单品类在库数步进调整 */
    @Transactional
    public LinenCategory adjustStock(Long id, int delta) {
        LinenCategory category = get(id);
        int target = nullToInt(category.getStockQty()) + delta;
        if (target < 0) {
            throw new BizException("【" + category.getName() + "】在库数不能为负数，当前在库 " + category.getStockQty() + " 件");
        }
        categoryMapper.updateStock(id, target);
        log("CATEGORY", id, "调整在库", category.getName() + " " + delta);
        return get(id);
    }

    /**
     * 保存盘点：按提交的在库数落库，返回差异件数与损益金额。
     */
    @Transactional
    public CountResult saveCount(List<CountItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException("盘点明细不能为空");
        }
        int changed = 0;
        int diffQty = 0;
        BigDecimal gainLoss = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();

        for (CountItem item : items) {
            if (item.getCategoryId() == null || item.getStockQty() == null) {
                continue;
            }
            LinenCategory category = get(item.getCategoryId());
            int oldStock = nullToInt(category.getStockQty());
            int diff = item.getStockQty() - oldStock;
            if (diff == 0) {
                continue;
            }
            if (item.getStockQty() < 0) {
                throw new BizException("【" + category.getName() + "】盘点在库数不能为负数");
            }
            BigDecimal unitPrice = category.getUnitPrice() == null ? BigDecimal.ZERO : category.getUnitPrice();
            categoryMapper.updateStock(category.getId(), item.getStockQty());
            changed++;
            diffQty += diff;
            gainLoss = gainLoss.add(unitPrice.multiply(BigDecimal.valueOf(diff)));
            int minTurnover = nullToInt(category.getMinTurnover());
            if (item.getStockQty() < minTurnover) {
                warnings.add("【" + category.getName() + "】低于最低周转量 " + minTurnover
                        + " 件，缺口 " + (minTurnover - item.getStockQty()) + " 件");
            }
        }
        if (changed == 0) {
            throw new BizException("本次盘点没有任何差异，无需保存");
        }
        log("CATEGORY", null, "保存盘点", "差异 " + diffQty + " 件，损益 " + gainLoss);

        CountResult result = new CountResult();
        result.setChangedCount(changed);
        result.setDiffQty(diffQty);
        result.setGainLoss(gainLoss.setScale(2, RoundingMode.HALF_UP));
        result.setWarnings(warnings);
        return result;
    }

    @Transactional
    public void delete(Long id) {
        LinenCategory category = get(id);
        categoryMapper.delete(id);
        log("CATEGORY", id, "删除品类", category.getName());
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

    private int nullToInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
