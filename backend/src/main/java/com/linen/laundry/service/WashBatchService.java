package com.linen.laundry.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linen.laundry.common.BizException;
import com.linen.laundry.common.WashProcess;
import com.linen.laundry.domain.BatchDeviationReview;
import com.linen.laundry.domain.BatchIsolation;
import com.linen.laundry.domain.BatchStep;
import com.linen.laundry.domain.LinenCategory;
import com.linen.laundry.domain.Machine;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.domain.RewashOrder;
import com.linen.laundry.domain.WashBatch;
import com.linen.laundry.domain.WashRecipeStep;
import com.linen.laundry.domain.WashRecipeVersion;
import com.linen.laundry.dto.BatchAdvanceRequest;
import com.linen.laundry.dto.BatchCreateRequest;
import com.linen.laundry.dto.RecipeSnapshot;
import com.linen.laundry.dto.ReviewResolveRequest;
import com.linen.laundry.dto.StepStat;
import com.linen.laundry.mapper.BatchIsolationMapper;
import com.linen.laundry.mapper.BatchReviewMapper;
import com.linen.laundry.mapper.LinenCategoryMapper;
import com.linen.laundry.mapper.MachineMapper;
import com.linen.laundry.mapper.RewashOrderMapper;
import com.linen.laundry.mapper.WashBatchMapper;
import com.linen.laundry.mapper.WashRecipeMapper;
import com.linen.laundry.repository.OperationLogRepository;

/**
 * 洗涤批次与工序流转：预洗→主洗→漂洗→中和→脱水→烘干→烫平→折叠，必须按顺序推进。
 *
 * 配方版本约束：
 * 1. 排产必须引用品类当前生效配方版本，建批时把版本号与八道工序标准参数冻结为批次快照
 *    （wash_batch.recipe_snapshot），在产批次按快照执行，不受后来发布 / 停用影响；
 * 2. 排产同时受设备故障、设备容量、版本可承接设备类型与最大装载比例限制，缺一不可；
 * 3. 推进工序时实际录入值超出快照容差 → 批次转 REVIEW 待复核，由主管填原因后
 *    按本批次放行或退回重做，结论只影响该批次；
 * 4. 污染隔离批次（QUARANTINED）无论停在哪道工序都不能推进，也不能按正常批次完工。
 */
@Service
public class WashBatchService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, String> MACHINE_TYPE_NAMES = Map.of(
            "WASHER", "水洗机", "DRYER", "烘干机", "IRONER", "烫平机");

    private final WashBatchMapper batchMapper;
    private final LinenCategoryMapper categoryMapper;
    private final MachineMapper machineMapper;
    private final BatchIsolationMapper isolationMapper;
    private final RewashOrderMapper rewashMapper;
    private final WashRecipeMapper recipeMapper;
    private final BatchReviewMapper reviewMapper;
    private final OperationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public WashBatchService(WashBatchMapper batchMapper, LinenCategoryMapper categoryMapper,
                            MachineMapper machineMapper, BatchIsolationMapper isolationMapper,
                            RewashOrderMapper rewashMapper, WashRecipeMapper recipeMapper,
                            BatchReviewMapper reviewMapper, OperationLogRepository logRepository,
                            ObjectMapper objectMapper) {
        this.batchMapper = batchMapper;
        this.categoryMapper = categoryMapper;
        this.machineMapper = machineMapper;
        this.isolationMapper = isolationMapper;
        this.rewashMapper = rewashMapper;
        this.recipeMapper = recipeMapper;
        this.reviewMapper = reviewMapper;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    public List<WashBatch> list(String status, String stepCode, Long categoryId) {
        List<WashBatch> batches;
        if ((status == null || status.isBlank()) && (stepCode == null || stepCode.isBlank()) && categoryId == null) {
            batches = batchMapper.selectAll();
        } else {
            batches = batchMapper.tunnel(status, stepCode, categoryId);
        }
        batches.forEach(this::attachRecipe);
        return batches;
    }

    public WashBatch get(Long id) {
        WashBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BizException("洗涤批次不存在：id=" + id);
        }
        attachRecipe(batch);
        batch.setReviews(reviewMapper.selectByBatchId(id));
        return batch;
    }

    /**
     * 排产：引用品类当前生效配方版本并冻结批次快照。
     * 设备故障、设备容量、版本可承接设备类型、版本最大装载比例四道限制同时生效，不能绕过任何一条。
     */
    @Transactional
    public WashBatch create(BatchCreateRequest request) {
        if (request.getCategoryId() == null) {
            throw new BizException("请选择布草品类");
        }
        LinenCategory category = categoryMapper.selectById(request.getCategoryId());
        if (category == null) {
            throw new BizException("布草品类不存在：id=" + request.getCategoryId());
        }
        WashRecipeVersion recipe = recipeMapper.findPublished(category.getId());
        if (recipe == null) {
            throw new BizException("品类【" + category.getName()
                    + "】当前没有已发布的洗涤配方版本，无法排产，请先在「洗涤配方版本台」发布配方");
        }
        List<WashRecipeStep> recipeSteps = recipeMapper.selectSteps(recipe.getId());
        if (request.getMachineId() == null) {
            throw new BizException("请选择水洗机");
        }
        Machine machine = machineMapper.selectById(request.getMachineId());
        if (machine == null) {
            throw new BizException("设备不存在：id=" + request.getMachineId());
        }
        // 限制一：设备故障不允许排产
        if ("FAULT".equals(machine.getStatus())) {
            throw new BizException("设备【" + machine.getName() + "】处于故障状态，不允许排产，请先报修并完工");
        }
        // 限制二：配方版本声明的可承接设备类型
        if (!parseTypes(recipe.getMachineTypes()).contains(machine.getMachineType())) {
            throw new BizException("配方 V" + recipe.getVersionNo() + " 限定可承接设备类型为【"
                    + typeNames(recipe.getMachineTypes()) + "】，设备【" + machine.getName() + "】（"
                    + MACHINE_TYPE_NAMES.getOrDefault(machine.getMachineType(), machine.getMachineType())
                    + "）不能承接该品类洗涤");
        }
        BigDecimal weight = request.getWeightKg();
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("批次重量必须大于 0");
        }
        // 限制三：设备容量
        if (machine.getCapacityKg() != null && weight.compareTo(machine.getCapacityKg()) > 0) {
            throw new BizException("批次重量 " + weight + "kg 超出设备【" + machine.getName() + "】容量 "
                    + machine.getCapacityKg() + "kg");
        }
        // 限制四：配方版本最大装载比例（在容量限制之内再收紧）
        if (machine.getCapacityKg() != null && recipe.getMaxLoadRatio() != null) {
            BigDecimal maxLoad = machine.getCapacityKg()
                    .multiply(BigDecimal.valueOf(recipe.getMaxLoadRatio()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
            if (weight.compareTo(maxLoad) > 0) {
                throw new BizException("批次重量 " + weight + "kg 超出配方 V" + recipe.getVersionNo()
                        + " 最大装载比例 " + recipe.getMaxLoadRatio() + "%（设备【" + machine.getName()
                        + "】容量 " + machine.getCapacityKg() + "kg，装载上限 " + maxLoad + "kg）");
            }
        }
        if (request.getWaterTemp() != null && (request.getWaterTemp() < 0 || request.getWaterTemp() > 180)) {
            throw new BizException("水温必须在 0~180℃ 之间");
        }

        // 冻结批次快照：版本号 + 可承接设备类型 + 最大装载比例 + 八道工序标准参数
        RecipeSnapshot snapshot = RecipeSnapshot.of(recipe, recipeSteps);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new BizException("配方快照序列化失败，请重试");
        }

        RecipeSnapshot.StepStd firstStd = snapshot.stdOf(WashProcess.codeAt(1));
        WashBatch batch = new WashBatch();
        batch.setBatchNo(nextBatchNo());
        batch.setCategoryId(category.getId());
        batch.setWeightKg(weight);
        batch.setCurrentStep(WashProcess.codeAt(1));
        batch.setCurrentStepOrder(1);
        batch.setDetergentMl(request.getDetergentMl() != null ? request.getDetergentMl()
                : firstStd != null ? firstStd.getTargetDetergentMl() : 800);
        batch.setWaterTemp(request.getWaterTemp() != null ? request.getWaterTemp()
                : firstStd != null && firstStd.getTargetTemp() != null
                        ? firstStd.getTargetTemp() : WashProcess.defaultTemp(WashProcess.codeAt(1)));
        batch.setMachineId(machine.getId());
        batch.setOperator(request.getOperator());
        batch.setStatus("RUNNING");
        batch.setRecipeVersionId(recipe.getId());
        batch.setRecipeVersionNo(recipe.getVersionNo());
        batch.setRecipeSnapshot(snapshotJson);
        batch.setRemark(request.getRemark());
        batchMapper.insert(batch);

        BatchStep first = newStep(batch.getId(), WashProcess.codeAt(1), request.getOperator(),
                batch.getWaterTemp(), batch.getDetergentMl(), "CURRENT");
        first.setMachineId(machine.getId());
        batchMapper.insertStep(first);

        if ("IDLE".equals(machine.getStatus())) {
            machineMapper.updateStatus(machine.getId(), "RUNNING");
        }
        log("BATCH", batch.getId(), "新建批次", batch.getBatchNo() + " / " + category.getName()
                + " / " + weight + "kg / 配方 V" + recipe.getVersionNo());
        return get(batch.getId());
    }

    /**
     * 推进工序：必须按顺序，不能跳步。
     * 实际录入值超出批次快照容差时不直接推进，批次转 REVIEW 待复核并落复核记录。
     */
    @Transactional
    public WashBatch advance(Long id, BatchAdvanceRequest request) {
        // 行锁串行化推进，与污染事件确认 / 放行、偏差复核互斥
        WashBatch batch = batchMapper.selectByIdForUpdate(id);
        if (batch == null) {
            throw new BizException("洗涤批次不存在：id=" + id);
        }
        if ("QUARANTINED".equals(batch.getStatus())) {
            BatchIsolation active = isolationMapper.findActiveByBatchId(id);
            String hint = active == null
                    ? "该复洗批次已中止（失败 / 删除），不能继续推进，请在污染事件中重新处置"
                    : "该批次因污染事件 " + "(#" + active.getEventId() + ") 处于污染隔离，待复洗放行后才能继续推进";
            throw new BizException("批次【" + batch.getBatchNo() + "】已被污染隔离，不能推进工序或按正常完工处理：" + hint);
        }
        if ("REVIEW".equals(batch.getStatus())) {
            throw new BizException("批次【" + batch.getBatchNo()
                    + "】存在待复核的参数偏差，需主管复核（按本批次放行或退回重做）后才能继续推进");
        }
        if ("DONE".equals(batch.getStatus())) {
            throw new BizException("批次【" + batch.getBatchNo() + "】已完成，不能再推进工序");
        }
        String current = batch.getCurrentStep();
        int currentOrder = WashProcess.orderOf(current);
        if (currentOrder <= 0) {
            throw new BizException("当前工序编码非法：" + current);
        }
        String next = WashProcess.nextOf(current);
        String target = request == null || request.getTargetStep() == null || request.getTargetStep().isBlank()
                ? next
                : request.getTargetStep();
        if (next != null && !next.equals(target)) {
            throw new BizException("工序必须按顺序推进，不能跳步：当前【"
                    + WashProcess.nameOf(current) + "】，下一道应为【" + WashProcess.nameOf(next)
                    + "】，不能直接跳到【" + WashProcess.nameOf(target) + "】");
        }

        // 本工序实际录入值（收口工序）；留空按批次当前记录值
        Integer actualTemp = request != null && request.getActualWaterTemp() != null
                ? request.getActualWaterTemp() : batch.getWaterTemp();
        Integer actualDetergent = request != null && request.getActualDetergentMl() != null
                ? request.getActualDetergentMl() : batch.getDetergentMl();

        RecipeSnapshot snapshot = parseSnapshot(batch.getRecipeSnapshot());
        RecipeSnapshot.StepStd nextStd = snapshot != null && next != null ? snapshot.stdOf(next) : null;
        Integer nextTemp = next == null ? null
                : request != null && request.getWaterTemp() != null ? request.getWaterTemp()
                : nextStd != null && nextStd.getTargetTemp() != null
                        ? nextStd.getTargetTemp() : WashProcess.defaultTemp(next);
        Integer nextDetergent = next == null ? null
                : request != null && request.getDetergentMl() != null ? request.getDetergentMl()
                : nextStd != null && nextStd.getTargetDetergentMl() != null
                        ? nextStd.getTargetDetergentMl() : batch.getDetergentMl();
        String operator = request != null && request.getOperator() != null
                ? request.getOperator()
                : batch.getOperator();

        // 配方容差校验：实际录入值超出批次快照容差 → 进入待复核，不直接推进
        if (snapshot != null) {
            RecipeSnapshot.StepStd std = snapshot.stdOf(current);
            if (std != null && outOfTolerance(std, actualTemp, actualDetergent)) {
                batchMapper.recordStepActuals(id, current, actualTemp, actualDetergent);
                batch.setWaterTemp(actualTemp);
                batch.setDetergentMl(actualDetergent);
                batch.setStatus("REVIEW");
                batchMapper.freezeForReview(batch);

                BatchDeviationReview review = new BatchDeviationReview();
                review.setBatchId(id);
                review.setStepCode(current);
                review.setStepName(WashProcess.nameOf(current));
                review.setStepOrder(currentOrder);
                review.setStdWaterTemp(std.getTargetTemp());
                review.setStdDetergentMl(std.getTargetDetergentMl());
                review.setActualWaterTemp(actualTemp);
                review.setActualDetergentMl(actualDetergent);
                review.setTargetStep(next);
                review.setNextWaterTemp(nextTemp);
                review.setNextDetergentMl(nextDetergent);
                review.setOperator(operator);
                review.setStatus("PENDING");
                review.setCreatedAt(LocalDateTime.now());
                reviewMapper.insert(review);

                log("BATCH", id, "参数偏差待复核",
                        batch.getBatchNo() + "「" + WashProcess.nameOf(current) + "」实际水温 "
                                + actualTemp + "℃ / 洗涤剂 " + actualDetergent + "ml 超出配方 V"
                                + batch.getRecipeVersionNo() + " 容差（标准 " + std.getTargetTemp() + "±"
                                + std.getTempTolerance() + "℃ / " + std.getTargetDetergentMl() + "±"
                                + std.getDetergentTolerance() + "ml），批次转待复核");
                return batchMapper.selectById(id);
            }
        }
        return doAdvance(batch, actualTemp, actualDetergent, nextTemp, nextDetergent, operator);
    }

    /**
     * 主管复核：填写原因后选择按本批次放行（RELEASE）或退回重做（REDO），结论只影响该批次。
     */
    @Transactional
    public WashBatch resolveReview(Long reviewId, ReviewResolveRequest request) {
        if (request == null || request.getDecision() == null || request.getDecision().isBlank()) {
            throw new BizException("请选择复核结论：按本批次放行或退回重做");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BizException("请填写复核原因");
        }
        String decision = request.getDecision().trim().toUpperCase();
        if (!"RELEASE".equals(decision) && !"REDO".equals(decision)) {
            throw new BizException("复核结论只能是 RELEASE（按本批次放行）或 REDO（退回重做）");
        }
        BatchDeviationReview review = reviewMapper.selectByIdForUpdate(reviewId);
        if (review == null) {
            throw new BizException("复核记录不存在：id=" + reviewId);
        }
        if (!"PENDING".equals(review.getStatus())) {
            throw new BizException("该偏差已复核（" + reviewStatusName(review.getStatus()) + "），请勿重复处理");
        }
        WashBatch batch = batchMapper.selectByIdForUpdate(review.getBatchId());
        if (batch == null) {
            throw new BizException("洗涤批次不存在：id=" + review.getBatchId());
        }
        if (!"REVIEW".equals(batch.getStatus())) {
            throw new BizException("批次【" + batch.getBatchNo() + "】当前不在待复核状态，请刷新后确认");
        }
        String reviewer = request.getReviewer() == null || request.getReviewer().isBlank()
                ? "车间主管" : request.getReviewer();

        if ("REDO".equals(decision)) {
            int rows = reviewMapper.resolve(reviewId, "REDONE", request.getReason(), reviewer);
            if (rows == 0) {
                throw new BizException("复核记录状态已变化，请刷新重试");
            }
            // 退回重做：批次回到在产，停留在原工序重新执行，再次推进时重新录入并校验
            batchMapper.updateStatus(batch.getId(), "RUNNING");
            log("BATCH", batch.getId(), "偏差退回重做",
                    batch.getBatchNo() + "「" + review.getStepName() + "」退回重做：" + request.getReason());
            return batchMapper.selectById(batch.getId());
        }

        // 按本批次放行：仅本批次按原推进意图继续，配方版本与其他批次不受影响
        int rows = reviewMapper.resolve(reviewId, "RELEASED", request.getReason(), reviewer);
        if (rows == 0) {
            throw new BizException("复核记录状态已变化，请刷新重试");
        }
        batch.setStatus("RUNNING");
        WashBatch result = doAdvance(batch, review.getActualWaterTemp(), review.getActualDetergentMl(),
                review.getNextWaterTemp(), review.getNextDetergentMl(),
                review.getOperator() != null ? review.getOperator() : batch.getOperator());
        log("BATCH", batch.getId(), "偏差放行",
                batch.getBatchNo() + "「" + review.getStepName() + "」按本批次放行：" + request.getReason());
        return result;
    }

    /**
     * 执行推进（收口当前工序 → 进入下一工序 / 完工）。
     * 正常推进与「复核放行」共用本方法，保证两条路径的收口行为一致。
     */
    private WashBatch doAdvance(WashBatch batch, Integer actualTemp, Integer actualDetergent,
                                Integer nextTemp, Integer nextDetergent, String operator) {
        Long id = batch.getId();
        String current = batch.getCurrentStep();
        String next = WashProcess.nextOf(current);

        // 收口当前工序：回填实际录入值
        batchMapper.finishStep(id, current, actualTemp, actualDetergent);

        if (next == null) {
            // 最后一道折叠完成：批次收口
            batch.setStatus("DONE");
            batch.setFinishTime(LocalDateTime.now());
            batchMapper.advance(batch);
            int pieces = batch.getWeightKg() == null ? 0
                    : batch.getWeightKg().divide(BigDecimal.valueOf(1.2), 0, RoundingMode.HALF_UP).intValue();
            boolean isRewash = batch.getRewashOrderId() != null;
            // 原隔离批次放行后若继续走到折叠，不再重复入库（复洗完成时已回补在库）
            boolean isReleasedSource = !isRewash
                    && batchMapper.countDoneRewashFromSource(id) > 0;
            if (pieces > 0 && batch.getCategoryId() != null && !isReleasedSource) {
                categoryMapper.addStock(batch.getCategoryId(), pieces);
            }
            if (batch.getMachineId() != null) {
                machineMapper.addRunHours(batch.getMachineId(), BigDecimal.valueOf(0.8));
                Machine machine = machineMapper.selectById(batch.getMachineId());
                if (machine != null && "RUNNING".equals(machine.getStatus())) {
                    machineMapper.updateStatus(machine.getId(), "IDLE");
                }
            }
            if (isRewash) {
                // 复洗批次完工：回写复洗单 DONE（CAS，仅在产可完成）
                RewashOrder rewash = rewashMapper.findByRewashBatchId(id);
                if (rewash != null && "ACTIVE".equals(rewash.getStatus())) {
                    rewashMapper.markDone(rewash.getId(), LocalDateTime.now());
                }
                log("REWASH", batch.getRewashOrderId(), "复洗批次完工",
                        batch.getBatchNo() + " 折叠完成，入库 " + pieces + " 件，原批次 "
                                + (rewash == null ? "" : rewash.getSourceBatchNo()));
                return batchMapper.selectById(id);
            }
            log("BATCH", id, "批次完工", batch.getBatchNo() + " 折叠完成，入库 " + pieces + " 件");
            return batchMapper.selectById(id);
        }

        int nextOrder = WashProcess.orderOf(next);
        batch.setCurrentStep(next);
        batch.setCurrentStepOrder(nextOrder);
        batch.setWaterTemp(nextTemp);
        batch.setDetergentMl(nextDetergent);
        batch.setOperator(operator);
        batch.setStatus("RUNNING");
        batch.setFinishTime(null);
        batchMapper.advance(batch);
        BatchStep nextStep = newStep(id, next, operator, nextTemp, nextDetergent, "CURRENT");
        // 水洗 1~5 道占用批次绑定水洗机；烘干 / 烫平工序默认设备在排产时未指定，留空（污染命中仍可由“绑定设备”路径兜底）
        nextStep.setMachineId(nextOrder <= 5 ? batch.getMachineId() : null);
        batchMapper.insertStep(nextStep);

        log("BATCH", id, "推进工序", batch.getBatchNo() + " → " + WashProcess.nameOf(next));
        return batchMapper.selectById(id);
    }

    public List<StepStat> stepStats() {
        List<StepStat> stats = batchMapper.stepStats();
        List<StepStat> full = new ArrayList<>();
        for (int order = 1; order <= WashProcess.size(); order++) {
            String code = WashProcess.codeAt(order);
            StepStat stat = new StepStat();
            stat.setStepCode(code);
            stat.setStepName(WashProcess.nameOf(code));
            stat.setStepOrder(order);
            stat.setBatchCount(0);
            stat.setTotalWeight(0);
            for (StepStat s : stats) {
                if (code.equals(s.getStepCode())) {
                    stat.setBatchCount(s.getBatchCount());
                    stat.setTotalWeight(s.getTotalWeight() == null ? 0 : s.getTotalWeight().intValue());
                }
            }
            full.add(stat);
        }
        return full;
    }

    public Map<String, Object> summary() {
        return batchMapper.summary();
    }

    @Transactional
    public void delete(Long id) {
        WashBatch batch = batchMapper.selectByIdForUpdate(id);
        if (batch == null) {
            throw new BizException("洗涤批次不存在：id=" + id);
        }
        // 隔离中的原批次不能删除（须走复洗闭环保留追溯）
        if ("QUARANTINED".equals(batch.getStatus()) && batch.getRewashOrderId() == null) {
            throw new BizException("批次【" + batch.getBatchNo() + "】处于污染隔离中，不能删除，请在污染事件中发起复洗");
        }
        // 复洗批次不能直接删除（须走“删除复洗”以回写复洗单状态，保持隔离闭环一致）
        if (batch.getRewashOrderId() != null) {
            throw new BizException("复洗批次【" + batch.getBatchNo() + "】不能直接删除，请在污染事件中删除对应复洗单");
        }
        reviewMapper.deleteByBatchId(id);
        batchMapper.deleteSteps(id);
        batchMapper.delete(id);
        log("BATCH", id, "删除批次", batch.getBatchNo());
    }

    // ==================== 辅助 ====================

    /** 解析批次快照并挂载到批次对象（隧道 / 详情展示标准参数用） */
    private void attachRecipe(WashBatch batch) {
        if (batch == null) {
            return;
        }
        RecipeSnapshot snapshot = parseSnapshot(batch.getRecipeSnapshot());
        if (snapshot != null) {
            batch.setRecipeSteps(snapshot.getSteps());
        }
    }

    private RecipeSnapshot parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RecipeSnapshot.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 实际录入值是否超出快照标准 ± 容差（水温或洗涤剂任一超差即 true） */
    private boolean outOfTolerance(RecipeSnapshot.StepStd std, Integer actualTemp, Integer actualDetergent) {
        if (actualTemp != null && std.getTargetTemp() != null && std.getTempTolerance() != null
                && Math.abs(actualTemp - std.getTargetTemp()) > std.getTempTolerance()) {
            return true;
        }
        return actualDetergent != null && std.getTargetDetergentMl() != null
                && std.getDetergentTolerance() != null
                && Math.abs(actualDetergent - std.getTargetDetergentMl()) > std.getDetergentTolerance();
    }

    private Set<String> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String typeNames(String types) {
        Set<String> parsed = parseTypes(types);
        if (parsed.isEmpty()) {
            return "无";
        }
        return parsed.stream()
                .map(t -> MACHINE_TYPE_NAMES.getOrDefault(t, t))
                .collect(Collectors.joining("、"));
    }

    private String reviewStatusName(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "待复核";
            case "RELEASED" -> "已放行";
            case "REDONE" -> "已退回重做";
            default -> status;
        };
    }

    private BatchStep newStep(Long batchId, String stepCode, String operator,
                              Integer waterTemp, Integer detergent, String status) {
        BatchStep step = new BatchStep();
        step.setBatchId(batchId);
        step.setStepCode(stepCode);
        step.setStepName(WashProcess.nameOf(stepCode));
        step.setStepOrder(WashProcess.orderOf(stepCode));
        step.setWaterTemp(waterTemp);
        step.setDetergentMl(detergent);
        step.setOperator(operator);
        step.setStatus(status);
        step.setStartTime(LocalDateTime.now());
        return step;
    }

    private String nextBatchNo() {
        return "PC" + LocalDateTime.now().format(NO_DATE) + "-"
                + String.format("%03d", (int) (System.currentTimeMillis() % 900) + 100);
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
