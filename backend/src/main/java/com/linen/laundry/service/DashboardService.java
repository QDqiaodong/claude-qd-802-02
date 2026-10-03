package com.linen.laundry.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linen.laundry.dto.DashboardOverview;
import com.linen.laundry.mapper.LinenCategoryMapper;
import com.linen.laundry.mapper.MachineMapper;
import com.linen.laundry.mapper.OutletMapper;
import com.linen.laundry.mapper.PickupOrderMapper;
import com.linen.laundry.mapper.WashBatchMapper;

/**
 * 车间总览 KPI（Redis 缓存 30 秒，Redis 不可用时直接查库）。
 */
@Service
public class DashboardService {

    private static final String CACHE_KEY = "linen-laundry:dashboard:overview";

    private final OutletMapper outletMapper;
    private final LinenCategoryMapper categoryMapper;
    private final PickupOrderMapper orderMapper;
    private final WashBatchMapper batchMapper;
    private final MachineMapper machineMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DashboardService(OutletMapper outletMapper, LinenCategoryMapper categoryMapper,
                            PickupOrderMapper orderMapper, WashBatchMapper batchMapper,
                            MachineMapper machineMapper, StringRedisTemplate redisTemplate) {
        this.outletMapper = outletMapper;
        this.categoryMapper = categoryMapper;
        this.orderMapper = orderMapper;
        this.batchMapper = batchMapper;
        this.machineMapper = machineMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public DashboardOverview overview() {
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, DashboardOverview.class);
            }
        } catch (Exception ignored) {
            // Redis 不可用时降级为直查数据库
        }
        DashboardOverview overview = build();
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(overview),
                    Duration.ofSeconds(30));
        } catch (Exception ignored) {
            // 缓存写失败不影响主流程
        }
        return overview;
    }

    /**
     * 收送单发生登记 / 送净 / 删除后，在事务提交外调用：清掉 overview 缓存，
     * 避免首页 30 秒缓存与台账顶部日汇总出现数值不一致。Redis 不可用直接忽略。
     */
    public void evictOverviewCache() {
        try {
            redisTemplate.delete(CACHE_KEY);
        } catch (Exception ignored) {
            // 缓存删失败不影响主流程，最多等 30 秒自然过期
        }
    }

    private DashboardOverview build() {
        Map<String, Object> outlet = outletMapper.summary();
        // 与台账顶部共用同一条 daySummary SQL：收脏按收脏时间、送净及短少赔付按送净时间
        Map<String, Object> order = orderMapper.daySummary(LocalDate.now());
        Map<String, Object> batch = batchMapper.summary();
        Map<String, Object> machine = machineMapper.summary();

        DashboardOverview overview = new DashboardOverview();
        overview.setOutletCount(intOf(outlet.get("outlet_count")));
        overview.setWarningOutletCount(intOf(outlet.get("warn_count")));
        overview.setPendingDirty(intOf(outlet.get("pending_total")));
        overview.setTodayDirty(intOf(order.get("dirty_total")));
        overview.setTodayClean(intOf(order.get("clean_total")));
        overview.setRunningBatch(intOf(batch.get("running_batch")));
        overview.setDoneBatch(intOf(batch.get("done_batch")));
        overview.setQuarantinedBatch(intOf(batch.get("quarantined_batch")));
        overview.setActiveContaminationCount(batchMapper.countActiveContaminationEvents());
        overview.setMachineCount(intOf(machine.get("machine_total")));
        overview.setFaultMachineCount(intOf(machine.get("fault_count")));
        overview.setShortageCategoryCount(categoryMapper.selectShortage().size());
        overview.setCompensationTotal(decimalOf(order.get("compensation_total")));
        return overview;
    }

    private int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private BigDecimal decimalOf(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}
