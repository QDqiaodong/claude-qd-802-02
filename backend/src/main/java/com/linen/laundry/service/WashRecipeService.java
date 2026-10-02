package com.linen.laundry.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linen.laundry.common.BizException;
import com.linen.laundry.common.WashProcess;
import com.linen.laundry.domain.LinenCategory;
import com.linen.laundry.domain.Machine;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.domain.WashRecipeStep;
import com.linen.laundry.domain.WashRecipeVersion;
import com.linen.laundry.dto.RecipeDraftCreateRequest;
import com.linen.laundry.dto.RecipeDraftSaveRequest;
import com.linen.laundry.mapper.LinenCategoryMapper;
import com.linen.laundry.mapper.MachineMapper;
import com.linen.laundry.mapper.WashRecipeMapper;
import com.linen.laundry.repository.OperationLogRepository;

/**
 * 洗涤配方版本台：按布草品类维护预洗至折叠八道工序的目标水温、洗涤剂用量与适用设备类型。
 *
 * 规则要点：
 * 1. 草稿可反复调整；发布前校验八道工序完整、参数范围合法、至少一台可用（非故障）设备能承接；
 * 2. 发布后不可原地改写，只能复制生成新版本；
 * 3. 每个品类同时只有一个当前版本：发布走「锁品类行 + 基线比对 + CAS + 唯一索引」，
 *    两人同时发布不同草稿时只有一份生效，另一份得到明确冲突结果；
 * 4. 停用当前版本必须先选定接替版本，否则该品类新批次无法排产；
 * 5. 已建批次只认建批时冻结的快照，版本发布 / 停用不影响在产批次。
 */
@Service
public class WashRecipeService {

    private static final List<String> MACHINE_TYPES = List.of("WASHER", "DRYER", "IRONER");
    private static final Map<String, String> MACHINE_TYPE_NAMES = Map.of(
            "WASHER", "水洗机", "DRYER", "烘干机", "IRONER", "烫平机");

    private final WashRecipeMapper recipeMapper;
    private final LinenCategoryMapper categoryMapper;
    private final MachineMapper machineMapper;
    private final OperationLogRepository logRepository;

    public WashRecipeService(WashRecipeMapper recipeMapper, LinenCategoryMapper categoryMapper,
                             MachineMapper machineMapper, OperationLogRepository logRepository) {
        this.recipeMapper = recipeMapper;
        this.categoryMapper = categoryMapper;
        this.machineMapper = machineMapper;
        this.logRepository = logRepository;
    }

    public List<WashRecipeVersion> list(Long categoryId) {
        List<WashRecipeVersion> versions = recipeMapper.selectAll(categoryId);
        versions.forEach(this::fillTransient);
        return versions;
    }

    public WashRecipeVersion get(Long id) {
        WashRecipeVersion version = recipeMapper.selectById(id);
        if (version == null) {
            throw new BizException("配方版本不存在：id=" + id);
        }
        fillTransient(version);
        return version;
    }

    /** 品类当前生效版本（排产 / 前端排产提示用）；未发布过返回 null */
    public WashRecipeVersion current(Long categoryId) {
        WashRecipeVersion version = recipeMapper.findPublished(categoryId);
        if (version != null) {
            fillTransient(version);
        }
        return version;
    }

    // ==================== 草稿：新建 / 复制 / 保存 / 删除 ====================

    @Transactional
    public WashRecipeVersion createDraft(RecipeDraftCreateRequest req) {
        if (req == null || req.getCategoryId() == null) {
            throw new BizException("请选择布草品类");
        }
        // 锁品类行：版本号递增与基线读取串行化
        LinenCategory category = categoryMapper.selectByIdForUpdate(req.getCategoryId());
        if (category == null) {
            throw new BizException("布草品类不存在：id=" + req.getCategoryId());
        }
        WashRecipeVersion base = null;
        if (req.getBaseVersionId() != null) {
            base = recipeMapper.selectById(req.getBaseVersionId());
            if (base == null || !base.getCategoryId().equals(category.getId())) {
                throw new BizException("复制来源版本不存在或不属于该品类");
            }
        }
        WashRecipeVersion current = recipeMapper.findPublished(category.getId());

        WashRecipeVersion draft = new WashRecipeVersion();
        draft.setCategoryId(category.getId());
        draft.setVersionNo(recipeMapper.nextVersionNo(category.getId()));
        draft.setStatus("DRAFT");
        draft.setMachineTypes(base != null ? base.getMachineTypes() : "WASHER");
        draft.setMaxLoadRatio(base != null ? base.getMaxLoadRatio() : 90);
        // 基线 = 创建草稿时的当前生效版本，发布时据此检测“他人已抢先发布”的冲突
        draft.setBaseVersionId(current != null ? current.getId() : null);
        draft.setRemark(base != null ? "复制自 V" + base.getVersionNo() : "新建草稿");
        draft.setCreatedBy(blankTo(req.getCreatedBy(), "工艺员"));
        draft.setCreatedAt(LocalDateTime.now());
        recipeMapper.insertVersion(draft);

        if (base != null) {
            for (WashRecipeStep step : recipeMapper.selectSteps(base.getId())) {
                step.setId(null);
                step.setVersionId(draft.getId());
                recipeMapper.insertStep(step);
            }
        } else {
            insertDefaultSteps(draft.getId());
        }
        log("RECIPE", draft.getId(), "新建配方草稿",
                category.getName() + " V" + draft.getVersionNo()
                        + (base != null ? "（复制自 V" + base.getVersionNo() + "）" : ""));
        return get(draft.getId());
    }

    /** 复制既有版本生成新草稿（发布后不可原地改写，只能复制） */
    @Transactional
    public WashRecipeVersion copy(Long id) {
        WashRecipeVersion source = recipeMapper.selectById(id);
        if (source == null) {
            throw new BizException("配方版本不存在：id=" + id);
        }
        RecipeDraftCreateRequest req = new RecipeDraftCreateRequest();
        req.setCategoryId(source.getCategoryId());
        req.setBaseVersionId(id);
        return createDraft(req);
    }

    @Transactional
    public WashRecipeVersion updateDraft(Long id, RecipeDraftSaveRequest req) {
        WashRecipeVersion draft = recipeMapper.selectByIdForUpdate(id);
        if (draft == null) {
            throw new BizException("配方版本不存在：id=" + id);
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new BizException("配方版本发布后不可原地改写，请复制生成新版本后再调整");
        }
        if (req == null) {
            throw new BizException("请填写草稿内容");
        }
        draft.setMachineTypes(joinTypes(req.getMachineTypes()));
        draft.setMaxLoadRatio(req.getMaxLoadRatio());
        draft.setRemark(req.getRemark());
        int rows = recipeMapper.updateDraft(draft);
        if (rows == 0) {
            throw new BizException("草稿状态已变化（可能已被发布或删除），保存失败，请刷新重试");
        }
        recipeMapper.deleteSteps(id);
        if (req.getSteps() != null) {
            for (RecipeDraftSaveRequest.StepPayload payload : req.getSteps()) {
                if (payload == null || payload.getStepCode() == null || payload.getStepCode().isBlank()) {
                    continue;
                }
                WashRecipeStep step = new WashRecipeStep();
                step.setVersionId(id);
                step.setStepCode(payload.getStepCode());
                step.setStepName(WashProcess.nameOf(payload.getStepCode()));
                step.setStepOrder(WashProcess.orderOf(payload.getStepCode()));
                step.setTargetTemp(payload.getTargetTemp());
                step.setTempTolerance(payload.getTempTolerance());
                step.setTargetDetergentMl(payload.getTargetDetergentMl());
                step.setDetergentTolerance(payload.getDetergentTolerance());
                step.setMachineTypes(joinTypes(payload.getMachineTypes()));
                recipeMapper.insertStep(step);
            }
        }
        log("RECIPE", id, "保存配方草稿", "V" + draft.getVersionNo() + " 草稿已更新");
        return get(id);
    }

    @Transactional
    public void deleteDraft(Long id) {
        WashRecipeVersion draft = recipeMapper.selectByIdForUpdate(id);
        if (draft == null) {
            throw new BizException("配方版本不存在：id=" + id);
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new BizException("仅草稿可删除；已发布版本只能停用，已停用版本保留追溯");
        }
        recipeMapper.deleteSteps(id);
        recipeMapper.deleteDraft(id);
        log("RECIPE", id, "删除配方草稿", "V" + draft.getVersionNo());
    }

    // ==================== 发布：校验 + 并发冲突检测 ====================

    @Transactional
    public WashRecipeVersion publish(Long id, String operator) {
        WashRecipeVersion probe = recipeMapper.selectById(id);
        if (probe == null) {
            throw new BizException("配方版本不存在：id=" + id);
        }
        // 先锁品类行（固定锁顺序：品类 → 版本，避免与停用-接替交叉死锁），同品类并发发布在此串行
        categoryMapper.selectByIdForUpdate(probe.getCategoryId());
        WashRecipeVersion draft = recipeMapper.selectByIdForUpdate(id);
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new BizException("仅草稿可发布，当前状态：" + statusName(draft.getStatus()));
        }
        WashRecipeVersion current = recipeMapper.findPublished(draft.getCategoryId());

        // 冲突检测：草稿基线必须仍是当前生效版本，否则说明他人已抢先发布
        Long baseId = draft.getBaseVersionId();
        boolean conflict = (baseId == null && current != null)
                || (baseId != null && (current == null || !current.getId().equals(baseId)));
        if (conflict) {
            throw new BizException("发布冲突：该品类当前配方已被他人发布为 V"
                    + (current == null ? "?" : current.getVersionNo())
                    + "，本草稿基线已过期。请基于最新当前版本复制新草稿后再发布，本草稿保持不变。");
        }

        List<WashRecipeStep> steps = recipeMapper.selectSteps(id);
        validateForPublish(draft, steps);

        String operatorName = blankTo(operator, draft.getCreatedBy());
        try {
            if (current != null) {
                recipeMapper.supersede(current.getId(), operatorName);
            }
            int rows = recipeMapper.publishDraft(id, operatorName);
            if (rows == 0) {
                throw new BizException("草稿状态已变化，发布失败，请刷新重试");
            }
        } catch (DuplicateKeyException dup) {
            // 唯一索引 uk_recipe_current 兜底：并发下只可能一份生效
            throw new BizException("发布冲突：该品类已存在当前生效版本，本次发布未生效，请刷新后重试");
        }
        log("RECIPE", id, "发布配方版本",
                "V" + draft.getVersionNo() + " 发布为当前版本"
                        + (current != null ? "，V" + current.getVersionNo() + " 转入已停用" : ""));
        return get(id);
    }

    // ==================== 停用：必须先选定接替版本 ====================

    @Transactional
    public WashRecipeVersion disable(Long id, Long successorVersionId, String operator) {
        WashRecipeVersion probe = recipeMapper.selectById(id);
        if (probe == null) {
            throw new BizException("配方版本不存在：id=" + id);
        }
        if (successorVersionId == null) {
            throw new BizException("停用当前版本前必须先选定接替版本，否则该品类新批次将无法排产");
        }
        // 先锁品类行（与发布保持同一锁顺序），停用-接替与并发发布互斥
        categoryMapper.selectByIdForUpdate(probe.getCategoryId());
        WashRecipeVersion version = recipeMapper.selectByIdForUpdate(id);
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BizException("仅当前生效版本需要停用操作（草稿可直接删除，已停用版本无需再停用）");
        }
        WashRecipeVersion successor = recipeMapper.selectByIdForUpdate(successorVersionId);
        if (successor == null || !successor.getCategoryId().equals(version.getCategoryId())) {
            throw new BizException("接替版本不存在或不属于该品类");
        }
        if (successor.getId().equals(version.getId())) {
            throw new BizException("接替版本不能是当前版本自身");
        }
        if ("DRAFT".equals(successor.getStatus())) {
            // 草稿直接接替：必须先通过发布校验
            validateForPublish(successor, recipeMapper.selectSteps(successor.getId()));
        } else if (!"DISABLED".equals(successor.getStatus())) {
            throw new BizException("接替版本状态非法：" + statusName(successor.getStatus()));
        }

        String operatorName = blankTo(operator, "工艺主管");
        try {
            recipeMapper.supersede(version.getId(), operatorName);
            int rows = recipeMapper.activate(successor.getId(), operatorName);
            if (rows == 0) {
                throw new BizException("接替版本状态已变化，停用失败，请刷新重试");
            }
        } catch (DuplicateKeyException dup) {
            throw new BizException("停用冲突：该品类当前版本刚被他人变更，请刷新后重试");
        }
        log("RECIPE", id, "停用配方版本",
                "V" + version.getVersionNo() + " 停用，V" + successor.getVersionNo() + " 接替为当前版本");
        return get(successor.getId());
    }

    // ==================== 发布校验 ====================

    /**
     * 发布前校验：八道工序完整、参数范围合法、至少一台可用设备能承接。
     * 任一不满足即抛业务异常，草稿保持可继续调整。
     */
    private void validateForPublish(WashRecipeVersion version, List<WashRecipeStep> steps) {
        // 1) 八道工序完整（预洗至折叠，一道不缺、不重、编码合法）
        if (steps == null || steps.size() != WashProcess.size()) {
            throw new BizException("发布校验失败：配方必须完整覆盖 " + WashProcess.size()
                    + " 道工序（预洗至折叠），当前只有 " + (steps == null ? 0 : steps.size()) + " 道");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (WashRecipeStep step : steps) {
            String code = step.getStepCode();
            if (!WashProcess.STEP_CODES.contains(code)) {
                throw new BizException("发布校验失败：工序编码非法：" + code);
            }
            if (!seen.add(code)) {
                throw new BizException("发布校验失败：工序【" + WashProcess.nameOf(code) + "】重复");
            }
            String name = WashProcess.nameOf(code);
            range(step.getTargetTemp(), 0, 180, "工序【" + name + "】目标水温（℃）");
            range(step.getTempTolerance(), 0, 30, "工序【" + name + "】水温容差（±℃）");
            range(step.getTargetDetergentMl(), 0, 5000, "工序【" + name + "】目标洗涤剂（ml）");
            range(step.getDetergentTolerance(), 0, 500, "工序【" + name + "】洗涤剂容差（±ml）");
            validateTypes(step.getMachineTypes(), true, "工序【" + name + "】适用设备类型");
        }
        // 2) 版本级参数
        validateTypes(version.getMachineTypes(), false, "可承接设备类型");
        range(version.getMaxLoadRatio(), 1, 100, "最大装载比例（%）");
        // 3) 至少一台可用设备能承接（非故障且类型匹配）
        Set<String> allowed = parseTypes(version.getMachineTypes());
        boolean anyCapable = machineMapper.selectAll().stream()
                .anyMatch(m -> !"FAULT".equals(m.getStatus()) && allowed.contains(m.getMachineType()));
        if (!anyCapable) {
            throw new BizException("发布校验失败：没有可承接的可用设备（需类型属于【"
                    + typeNames(version.getMachineTypes()) + "】且非故障），请先维修设备或调整可承接类型");
        }
    }

    private void range(Integer value, int min, int max, String label) {
        if (value == null || value < min || value > max) {
            throw new BizException("发布校验失败：" + label + "必须在 " + min + "~" + max + " 之间");
        }
    }

    private void validateTypes(String types, boolean allowEmpty, String label) {
        Set<String> parsed = parseTypes(types);
        if (!allowEmpty && parsed.isEmpty()) {
            throw new BizException("发布校验失败：" + label + "不能为空");
        }
        for (String t : parsed) {
            if (!MACHINE_TYPES.contains(t)) {
                throw new BizException("发布校验失败：" + label + "含非法类型 " + t
                        + "（可选：WASHER 水洗机 / DRYER 烘干机 / IRONER 烫平机）");
            }
        }
    }

    // ==================== 辅助 ====================

    private void insertDefaultSteps(Long versionId) {
        int[] temps = {35, 70, 45, 40, 0, 75, 160, 0};
        String[] stepTypes = {"WASHER", "WASHER", "WASHER", "WASHER", "WASHER", "DRYER", "IRONER", ""};
        for (int i = 0; i < WashProcess.size(); i++) {
            WashRecipeStep step = new WashRecipeStep();
            step.setVersionId(versionId);
            step.setStepCode(WashProcess.codeAt(i + 1));
            step.setStepName(WashProcess.STEP_NAMES.get(i));
            step.setStepOrder(i + 1);
            step.setTargetTemp(temps[i]);
            step.setTempTolerance(5);
            step.setTargetDetergentMl(800);
            step.setDetergentTolerance(100);
            step.setMachineTypes(stepTypes[i]);
            recipeMapper.insertStep(step);
        }
    }

    private void fillTransient(WashRecipeVersion version) {
        version.setStatusName(statusName(version.getStatus()));
    }

    private String statusName(String status) {
        return switch (status == null ? "" : status) {
            case "DRAFT" -> "草稿";
            case "PUBLISHED" -> "当前生效";
            case "DISABLED" -> "已停用";
            default -> status;
        };
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

    private String joinTypes(List<String> types) {
        if (types == null) {
            return "";
        }
        return types.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(","));
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

    private String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
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
