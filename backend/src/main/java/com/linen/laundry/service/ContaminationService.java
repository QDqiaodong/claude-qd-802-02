package com.linen.laundry.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linen.laundry.common.BizException;
import com.linen.laundry.common.ContaminationType;
import com.linen.laundry.common.WashProcess;
import com.linen.laundry.domain.BatchIsolation;
import com.linen.laundry.domain.ContaminationEvent;
import com.linen.laundry.domain.Machine;
import com.linen.laundry.domain.OperationLog;
import com.linen.laundry.domain.RewashOrder;
import com.linen.laundry.domain.WashBatch;
import com.linen.laundry.dto.ContaminationActionRequest;
import com.linen.laundry.dto.ContaminationEventRequest;
import com.linen.laundry.mapper.BatchIsolationMapper;
import com.linen.laundry.mapper.BatchStepMapper;
import com.linen.laundry.mapper.ContaminationEventMapper;
import com.linen.laundry.mapper.MachineMapper;
import com.linen.laundry.mapper.RewashOrderMapper;
import com.linen.laundry.mapper.WashBatchMapper;
import com.linen.laundry.repository.OperationLogRepository;

/**
 * 医院布草污染事件追溯与隔离处置闭环。
 *
 * 并发一致性（不靠前端隐藏按钮）：
 * 1. 确认事件 / 发起复洗 / 复核放行都先 SELECT … FOR UPDATE 锁定事件（或隔离记录），串行化同资源的并发请求；
 * 2. 状态机守卫：只允许 DRAFT→CONFIRMED、CONFIRMED→RELEASED，重复请求走幂等返回；
 * 3. 数据库唯一约束兜底：batch_isolation UNIQUE(event_id, batch_id) 防重复隔离；
 *    rewash_order 生成列 + UNIQUE(isolation_id, active_flag) 防重复有效复洗；
 * 4. 放行前逐批复核：必须恰好一份 DONE 复洗，且无 ACTIVE / FAILED / DELETED 复洗，否则带原因拒绝。
 */
@Service
public class ContaminationService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final ContaminationEventMapper eventMapper;
    private final BatchIsolationMapper isolationMapper;
    private final RewashOrderMapper rewashMapper;
    private final BatchStepMapper stepMapper;
    private final WashBatchMapper batchMapper;
    private final MachineMapper machineMapper;
    private final OperationLogRepository logRepository;

    public ContaminationService(ContaminationEventMapper eventMapper,
                                BatchIsolationMapper isolationMapper,
                                RewashOrderMapper rewashMapper,
                                BatchStepMapper stepMapper,
                                WashBatchMapper batchMapper,
                                MachineMapper machineMapper,
                                OperationLogRepository logRepository) {
        this.eventMapper = eventMapper;
        this.isolationMapper = isolationMapper;
        this.rewashMapper = rewashMapper;
        this.stepMapper = stepMapper;
        this.batchMapper = batchMapper;
        this.machineMapper = machineMapper;
        this.logRepository = logRepository;
    }

    // ==================== 事件登记 / 列表 / 详情 ====================

    public List<ContaminationEvent> list() {
        List<ContaminationEvent> events = eventMapper.selectAll();
        events.forEach(this::fillTransient);
        return events;
    }

    public ContaminationEvent get(Long id) {
        ContaminationEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new BizException("污染事件不存在：id=" + id);
        }
        fillTransient(event);
        if (event.getIsolations() != null) {
            event.getIsolations().forEach(this::fillIsolationTransient);
        }
        return event;
    }

    @Transactional
    public ContaminationEvent create(ContaminationEventRequest req) {
        validate(req);
        Machine machine = machineMapper.selectById(req.getMachineId());
        if (machine == null) {
            throw new BizException("发生设备不存在：id=" + req.getMachineId());
        }
        ContaminationEvent event = new ContaminationEvent();
        event.setMachineId(machine.getId());
        event.setContamType(req.getContamType());
        event.setFoundTime(req.getFoundTime());
        event.setAffectStart(req.getAffectStart());
        event.setAffectEnd(req.getAffectEnd());
        event.setDescription(req.getDescription());
        event.setReporter(blankTo(req.getReporter(), "车间人员"));
        event.setStatus("DRAFT");
        event.setCreatedAt(LocalDateTime.now());
        // event_no 有 NOT NULL UNIQUE 约束：先放临时唯一值，拿到主键后回填规范事件号
        event.setEventNo("CR-TMP-" + System.nanoTime());
        eventMapper.insert(event);
        // 用自增主键回填规范事件号，保证并发下编号唯一且可追溯
        event.setEventNo(eventNoOf(event.getId(), event.getCreatedAt()));
        eventMapper.updateEventNo(event.getId(), event.getEventNo());
        log("CONTAMINATION", event.getId(), "登记污染事件",
                event.getEventNo() + " " + machine.getName() + " " + ContaminationType.nameOf(req.getContamType()));
        return get(event.getId());
    }

    // ==================== 确认事件：命中批次 → 落不可重复隔离记录 ====================

    @Transactional
    public ContaminationEvent confirm(Long id, ContaminationActionRequest req) {
        // 锁事件行：同一事件的并发确认在此串行
        ContaminationEvent event = eventMapper.selectByIdForUpdate(id);
        if (event == null) {
            throw new BizException("污染事件不存在：id=" + id);
        }
        // 幂等：已确认 / 已放行的重复确认请求直接返回，不再生成隔离记录
        if (!"DRAFT".equals(event.getStatus())) {
            return get(id);
        }
        Machine machine = machineMapper.selectById(event.getMachineId());
        String machineName = machine == null ? ("设备#" + event.getMachineId()) : machine.getName();

        List<HitCandidate> hits = findHitBatches(event);

        String operator = req == null ? null : req.getOperator();
        LocalDateTime now = LocalDateTime.now();
        for (HitCandidate hit : hits) {
            // 锁定命中批次行：与批次推进互斥，避免“正在收口完工的同时被隔离”产生状态交叉
            WashBatch hitBatch = batchMapper.selectByIdForUpdate(hit.batchId);
            int wasDone = hitBatch != null && "DONE".equals(hitBatch.getStatus()) ? 1 : 0;
            BatchIsolation isolation = new BatchIsolation();
            isolation.setEventId(event.getId());
            isolation.setBatchId(hit.batchId);
            isolation.setHitStepCode(hit.stepCode);
            isolation.setHitStepName(hit.stepName);
            isolation.setHitMachineId(hit.machineId);
            isolation.setHitOverlap(hit.overlapText);
            isolation.setHitReason(hit.reason);
            isolation.setStatus("ISOLATED");
            isolation.setCreatedAt(now);
            try {
                isolationMapper.insert(isolation);
            } catch (DuplicateKeyException dup) {
                // 唯一约束兜底：并发下已被其他事务落过的隔离记录直接跳过，不重复落库
                continue;
            }
            // 无论批次当前停在哪道工序（含已完工批次），统一转入污染隔离；记录隔离前是否完工供放行时恢复
            batchMapper.markQuarantined(hit.batchId, wasDone);
        }

        int rows = eventMapper.compareAndSetStatus(id, "DRAFT", "CONFIRMED",
                blankTo(operator, event.getReporter()), now, null, null, null);
        if (rows == 0) {
            // 并发下已被另一请求确认：抛出触发回滚，由唯一约束保证无重复，前端重复点击只会看到已确认结果
            throw new BizException("污染事件 " + event.getEventNo() + " 已被确认，请勿重复操作");
        }
        log("CONTAMINATION", id, "确认污染事件",
                event.getEventNo() + " 命中并隔离 " + hits.size() + " 个批次（" + machineName + "）");
        return get(id);
    }

    // ==================== 发起复洗：新建可追溯批次并关联原批次 ====================

    @Transactional
    public RewashOrder createRewash(Long eventId, Long isolationId, ContaminationActionRequest req) {
        // 锁事件，确保放行与发起复洗互斥
        ContaminationEvent event = eventMapper.selectByIdForUpdate(eventId);
        if (event == null) {
            throw new BizException("污染事件不存在：id=" + eventId);
        }
        if ("RELEASED".equals(event.getStatus())) {
            throw new BizException("污染事件 " + event.getEventNo() + " 已放行，无需再复洗");
        }
        BatchIsolation isolation = isolationMapper.selectByIdForUpdate(isolationId);
        if (isolation == null || !isolation.getEventId().equals(eventId)) {
            throw new BizException("隔离记录不存在或不属于该污染事件");
        }
        if ("RELEASED".equals(isolation.getStatus())) {
            throw new BizException("该批次已随事件放行，无需复洗");
        }

        // 幂等：已有一份有效复洗（在产 / 完成）则直接返回，重复点击 / 并发请求不再新建复洗批次
        RewashOrder existing = rewashMapper.findActiveByIsolationId(isolationId);
        if (existing != null) {
            return decorateRewash(rewashMapper.selectById(existing.getId()));
        }

        WashBatch source = batchMapper.selectById(isolation.getBatchId());
        if (source == null) {
            throw new BizException("原隔离批次不存在，无法发起复洗");
        }

        Machine machine = chooseRewashMachine(req == null ? null : req.getMachineId(), source);
        String operator = req != null && !isBlank(req.getOperator()) ? req.getOperator()
                : blankTo(source.getOperator(), "处置员");

        // 先落复洗单（ACTIVE，active_flag=1），唯一索引 (isolation_id, active_flag) 兜底并发
        RewashOrder rewash = new RewashOrder();
        rewash.setIsolationId(isolationId);
        rewash.setEventId(eventId);
        rewash.setSourceBatchId(source.getId());
        rewash.setMachineId(machine.getId());
        rewash.setOperator(operator);
        rewash.setReason(req != null ? req.getNote() : null);
        rewash.setStatus("ACTIVE");
        rewash.setCreatedAt(LocalDateTime.now());
        // rewash_no 有 NOT NULL UNIQUE 约束：先放临时唯一值，拿到主键后回填规范单号
        rewash.setRewashNo("RW-TMP-" + System.nanoTime());
        try {
            rewashMapper.insert(rewash);
        } catch (DuplicateKeyException dup) {
            // 并发下另一请求已创建有效复洗：返回那一份，不产生重复复洗批次
            RewashOrder winner = rewashMapper.findActiveByIsolationId(isolationId);
            if (winner != null) {
                return decorateRewash(rewashMapper.selectById(winner.getId()));
            }
            throw new BizException("该隔离批次已存在有效复洗，请勿重复发起");
        }
        rewash.setRewashNo(rewashNoOf(rewash.getId(), rewash.getCreatedAt()));
        rewashMapper.updateRewashNo(rewash.getId(), rewash.getRewashNo());

        // 复洗必须形成可追溯的新批次，并关联原批次；配方版本与快照沿用原批次冻结内容（复洗按原批次配方执行）
        WashBatch rb = new WashBatch();
        rb.setBatchNo(rewashBatchNoOf(rewash));
        rb.setCategoryId(source.getCategoryId());
        rb.setWeightKg(source.getWeightKg());
        rb.setCurrentStep(WashProcess.codeAt(1));
        rb.setCurrentStepOrder(1);
        rb.setDetergentMl(source.getDetergentMl() == null ? 800 : source.getDetergentMl());
        rb.setWaterTemp(WashProcess.defaultTemp(WashProcess.codeAt(1)));
        rb.setMachineId(machine.getId());
        rb.setOperator(operator);
        rb.setStatus("RUNNING");
        rb.setStartTime(LocalDateTime.now());
        rb.setRecipeVersionId(source.getRecipeVersionId());
        rb.setRecipeVersionNo(source.getRecipeVersionNo());
        rb.setRecipeSnapshot(source.getRecipeSnapshot());
        rb.setRewashOrderId(rewash.getId());
        rb.setRemark("污染复洗批次，源于 " + source.getBatchNo()
                + "（事件 " + event.getEventNo() + "）");
        batchMapper.insert(rb);

        com.linen.laundry.domain.BatchStep firstStep = new com.linen.laundry.domain.BatchStep();
        firstStep.setBatchId(rb.getId());
        firstStep.setStepCode(WashProcess.codeAt(1));
        firstStep.setStepName(WashProcess.nameOf(WashProcess.codeAt(1)));
        firstStep.setStepOrder(1);
        firstStep.setWaterTemp(rb.getWaterTemp());
        firstStep.setDetergentMl(rb.getDetergentMl());
        firstStep.setMachineId(machine.getId());
        firstStep.setOperator(operator);
        firstStep.setStatus("CURRENT");
        firstStep.setStartTime(LocalDateTime.now());
        batchMapper.insertStep(firstStep);

        rewashMapper.bindBatch(rewash.getId(), rb.getId(), machine.getId());

        if ("IDLE".equals(machine.getStatus())) {
            machineMapper.updateStatus(machine.getId(), "RUNNING");
        }
        log("REWASH", rewash.getId(), "发起复洗",
                rewash.getRewashNo() + " 原批次 " + source.getBatchNo() + " → 新批次 " + rb.getBatchNo());
        return decorateRewash(rewashMapper.selectById(rewash.getId()));
    }

    // ==================== 复洗失败 / 删除复洗（保持隔离，允许重新处置） ====================

    @Transactional
    public RewashOrder failRewash(Long rewashId, ContaminationActionRequest req) {
        RewashOrder rewash = rewashMapper.selectById(rewashId);
        if (rewash == null) {
            throw new BizException("复洗单不存在：id=" + rewashId);
        }
        if (!"ACTIVE".equals(rewash.getStatus())) {
            throw new BizException("仅在产的复洗可标记失败，当前状态：" + ContaminationType.rewashStatusName(rewash.getStatus()));
        }
        int rows = rewashMapper.markFailed(rewashId, LocalDateTime.now());
        if (rows == 0) {
            throw new BizException("复洗单状态已变化，标记失败失败，请刷新重试");
        }
        if (rewash.getRewashBatchId() != null) {
            batchMapper.markRewashHeld(rewash.getRewashBatchId());
        }
        log("REWASH", rewashId, "复洗失败",
                rewash.getRewashNo() + " 原隔离批次继续保持隔离，可重新发起复洗");
        return decorateRewash(rewashMapper.selectById(rewashId));
    }

    @Transactional
    public RewashOrder deleteRewash(Long rewashId, ContaminationActionRequest req) {
        RewashOrder rewash = rewashMapper.selectById(rewashId);
        if (rewash == null) {
            throw new BizException("复洗单不存在：id=" + rewashId);
        }
        if ("DONE".equals(rewash.getStatus())) {
            throw new BizException("已完成的复洗不能删除（须保留可追溯记录）；如需处置请走重新复洗");
        }
        if ("DELETED".equals(rewash.getStatus())) {
            return rewash;
        }
        int rows = rewashMapper.markDeleted(rewashId, LocalDateTime.now());
        if (rows == 0) {
            throw new BizException("复洗单状态已变化，删除失败，请刷新重试");
        }
        if (rewash.getRewashBatchId() != null) {
            batchMapper.markRewashHeld(rewash.getRewashBatchId());
        }
        log("REWASH", rewashId, "删除复洗",
                rewash.getRewashNo() + " 已置为删除，原隔离批次保持隔离，可重新发起复洗");
        return decorateRewash(rewashMapper.selectById(rewashId));
    }

    // ==================== 复核放行：全部有效复洗完成才放行 ====================

    @Transactional
    public ContaminationEvent release(Long id, ContaminationActionRequest req) {
        // 锁事件行：放行与确认 / 发起复写互斥
        ContaminationEvent event = eventMapper.selectByIdForUpdate(id);
        if (event == null) {
            throw new BizException("污染事件不存在：id=" + id);
        }
        if ("RELEASED".equals(event.getStatus())) {
            // 幂等：重复放行请求直接返回，不会重复处理
            return get(id);
        }
        if (!"CONFIRMED".equals(event.getStatus())) {
            throw new BizException("仅已确认（隔离中）的事件才能复核放行，当前状态："
                    + ContaminationType.eventStatusName(event.getStatus()));
        }

        List<BatchIsolation> isolations = isolationMapper.selectByEventId(id);
        if (isolations.isEmpty()) {
            // 无命中批次：确认后可直接放行
            return doRelease(event, req);
        }

        List<String> blockers = new ArrayList<>();
        for (BatchIsolation isolation : isolations) {
            if ("RELEASED".equals(isolation.getStatus())) {
                continue;
            }
            String blocker = evaluateReleaseBlocker(isolation);
            if (blocker != null) {
                blockers.add(blocker);
            }
        }
        if (!blockers.isEmpty()) {
            throw new BizException("仍有隔离批次未满足放行条件，事件保持隔离：" + String.join("；", blockers));
        }

        return doRelease(event, req);
    }

    /**
     * 逐批判定放行条件：必须恰好一份 DONE 复洗，且没有 ACTIVE / FAILED / DELETED 复洗。
     * 返回 null 表示该批可放行；否则返回阻断原因。
     */
    private String evaluateReleaseBlocker(BatchIsolation isolation) {
        List<RewashOrder> orders = rewashMapper.selectByIsolationId(isolation.getId());
        long active = orders.stream().filter(o -> "ACTIVE".equals(o.getStatus())).count();
        long done = orders.stream().filter(o -> "DONE".equals(o.getStatus())).count();
        long failed = orders.stream().filter(o -> "FAILED".equals(o.getStatus())).count();
        long deleted = orders.stream().filter(o -> "DELETED".equals(o.getStatus())).count();

        String batchNo = isolation.getBatchNo();
        if (active > 0) {
            return "批次 " + batchNo + " 有 " + active + " 缸复洗仍在生产中";
        }
        if (failed > 0) {
            return "批次 " + batchNo + " 有 " + failed + " 缸复洗失败，需重新复洗";
        }
        if (deleted > 0) {
            return "批次 " + batchNo + " 有 " + deleted + " 缸复洗被删除，需重新复洗";
        }
        if (orders.isEmpty() || done == 0) {
            return "批次 " + batchNo + " 尚未完成任何复洗";
        }
        if (done > 1) {
            // 有效复洗唯一索引下不会发生，保留作为数据异常护栏
            return "批次 " + batchNo + " 存在多份完成复洗，需人工核查";
        }
        return null;
    }

    private ContaminationEvent doRelease(ContaminationEvent event, ContaminationActionRequest req) {
        LocalDateTime now = LocalDateTime.now();
        String operator = req == null ? null : req.getOperator();
        String note = req == null ? null : req.getNote();

        List<BatchIsolation> isolations = isolationMapper.selectByEventId(event.getId());
        for (BatchIsolation isolation : isolations) {
            if ("ISOLATED".equals(isolation.getStatus())) {
                isolationMapper.markReleased(isolation.getId(), now);
                // 解除批次隔离，恢复在产；不停留在隔离态，也不按正常完工补入库（复洗完成时已入库）
                batchMapper.releaseQuarantine(isolation.getBatchId());
            }
        }

        int rows = eventMapper.compareAndSetStatus(event.getId(), "CONFIRMED", "RELEASED",
                null, null, blankTo(operator, event.getConfirmedBy()), now, note);
        if (rows == 0) {
            throw new BizException("污染事件状态已变化，放行失败，请刷新重试");
        }
        log("CONTAMINATION", event.getId(), "复核放行",
                event.getEventNo() + " 全部 " + isolations.size() + " 个隔离批次复洗完成，已放行");
        return get(event.getId());
    }

    // ==================== 命中判定：按设备占用时间找出窗口内实际经过的批次 ====================

    private List<HitCandidate> findHitBatches(ContaminationEvent event) {
        Long machineId = event.getMachineId();
        LocalDateTime start = event.getAffectStart();
        LocalDateTime end = event.getAffectEnd();

        // 路径 A：某道工序实际占用该设备且与窗口重叠（同一批次可能多道工序命中）
        List<Map<String, Object>> stepRows = stepMapper.findOverlappingSteps(machineId, start, end);
        // 路径 B：批次绑定在该设备但无工序重叠记录（刚排产等兜底）
        List<Map<String, Object>> boundRows = stepMapper.findMachineBoundBatches(machineId, start, end);

        Map<Long, HitCandidate> byBatch = new LinkedHashMap<>();
        for (Map<String, Object> row : stepRows) {
            Long batchId = asLong(row.get("batch_id"));
            HitCandidate c = byBatch.computeIfAbsent(batchId, k -> {
                HitCandidate hc = new HitCandidate();
                hc.batchId = batchId;
                hc.machineId = machineId;
                return hc;
            });
            String code = (String) row.get("step_code");
            String name = (String) row.get("step_name");
            int order = asInt(row.get("step_order"));
            LocalDateTime s = toTime(row.get("step_start"));
            LocalDateTime e = toTime(row.get("step_end"));
            c.overlaps.add(new Overlap(order, code, name, s, e));
        }
        for (Map<String, Object> row : boundRows) {
            Long batchId = asLong(row.get("batch_id"));
            if (byBatch.containsKey(batchId)) {
                continue;
            }
            HitCandidate c = new HitCandidate();
            c.batchId = batchId;
            c.machineId = machineId;
            String code = (String) row.get("step_code");
            c.boundOnly = true;
            c.boundStepCode = code;
            c.boundStepOrder = asInt(row.get("step_order"));
            LocalDateTime s = toTime(row.get("step_start"));
            LocalDateTime e = toTime(row.get("step_end"));
            if (s != null && e != null) {
                c.overlaps.add(new Overlap(c.boundStepOrder, code, null, s, e));
            }
            byBatch.put(batchId, c);
        }

        // 已处于隔离中的批次（被其它事件命中）也允许加入本事件隔离；但正常情况下不重复解释同一事件
        List<HitCandidate> result = new ArrayList<>();
        for (HitCandidate c : byBatch.values()) {
            buildReason(c, event, machineId);
            result.add(c);
        }
        return result;
    }

    private void buildReason(HitCandidate c, ContaminationEvent event, Long machineId) {
        WashBatch batch = batchMapper.selectById(c.batchId);
        Machine machine = machineMapper.selectById(machineId);
        String mName = machine == null ? ("设备#" + machineId) : machine.getName();
        String window = event.getAffectStart().format(HM) + "—" + event.getAffectEnd().format(HM);

        if (c.boundOnly) {
            c.stepCode = c.boundStepCode;
            c.stepName = c.boundStepCode == null ? null : WashProcess.nameOf(c.boundStepCode);
            Overlap o = c.overlaps.isEmpty() ? null : c.overlaps.get(0);
            c.overlapText = o == null ? null : durationText(overlapSeconds(o, event));
            c.reason = "该批次在污染窗口（" + window + "）内排放在【" + mName + "】上生产"
                    + (c.stepName != null ? "，当时停留在「" + c.stepName + "」" : "")
                    + "，按设备占用追溯判定经过污染设备，需隔离复洗";
            return;
        }

        // 取批次在该设备上、与窗口重叠的工序（按工序号）
        c.overlaps.sort(Comparator.comparingInt(o -> o.order));
        Overlap latest = c.overlaps.get(c.overlaps.size() - 1);
        c.stepCode = latest.code;
        c.stepName = latest.name != null ? latest.name : WashProcess.nameOf(latest.code);
        long totalSeconds = 0;
        for (Overlap o : c.overlaps) {
            totalSeconds += overlapSeconds(o, event);
        }
        c.overlapText = durationText(totalSeconds);

        StringBuilder sb = new StringBuilder();
        sb.append("该批次在污染窗口（").append(window).append("）内于【").append(mName).append("】经过 ");
        if (c.overlaps.size() == 1) {
            Overlap o = c.overlaps.get(0);
            sb.append("「").append(o.displayName()).append("」工序，占用时段 ")
              .append(o.s.format(HM)).append("—").append(o.e.format(HM))
              .append("，与影响窗口重叠 ").append(durationText(overlapSeconds(o, event)));
        } else {
            sb.append(c.overlaps.size()).append(" 道工序：");
            for (int i = 0; i < c.overlaps.size(); i++) {
                Overlap o = c.overlaps.get(i);
                if (i > 0) {
                    sb.append("、");
                }
                sb.append("「").append(o.displayName()).append("」(")
                  .append(durationText(overlapSeconds(o, event))).append(")");
            }
            sb.append("，累计重叠 ").append(durationText(totalSeconds));
        }
        sb.append("，存在交叉污染风险，判定命中并隔离");
        c.reason = sb.length() > 480 ? sb.substring(0, 480) : sb.toString();

        // 命中时批次停留工序 / 设备：用批次当前值，便于详情展示“命中时停在哪”
        if (batch != null) {
            c.stepCode = batch.getCurrentStep();
            c.stepName = WashProcess.nameOf(batch.getCurrentStep());
            c.machineId = batch.getMachineId() != null ? batch.getMachineId() : machineId;
        }
    }

    private long overlapSeconds(Overlap o, ContaminationEvent event) {
        LocalDateTime s = o.s.isBefore(event.getAffectStart()) ? event.getAffectStart() : o.s;
        LocalDateTime e = o.e.isAfter(event.getAffectEnd()) ? event.getAffectEnd() : o.e;
        long sec = Duration.between(s, e).getSeconds();
        return Math.max(0, sec);
    }

    // ==================== 复洗设备选择 ====================

    private Machine chooseRewashMachine(Long preferredId, WashBatch source) {
        if (preferredId != null) {
            Machine m = machineMapper.selectById(preferredId);
            if (m == null) {
                throw new BizException("指定的复洗设备不存在：id=" + preferredId);
            }
            if ("FAULT".equals(m.getStatus())) {
                throw new BizException("复洗设备【" + m.getName() + "】处于故障状态，不能复洗");
            }
            if (!"WASHER".equals(m.getMachineType())) {
                throw new BizException("复洗必须使用水洗机");
            }
            if (source.getWeightKg() != null && m.getCapacityKg() != null
                    && source.getWeightKg().compareTo(m.getCapacityKg()) > 0) {
                throw new BizException("复洗重量 " + source.getWeightKg() + "kg 超出设备【"
                        + m.getName() + "】容量 " + m.getCapacityKg() + "kg");
            }
            return m;
        }
        // 自动选择：非故障水洗机，容量足够，优先待机、容量最接近、医感专区（名称/位置含医感）
        List<Machine> washers = machineMapper.selectAll().stream()
                .filter(m -> "WASHER".equals(m.getMachineType()))
                .filter(m -> !"FAULT".equals(m.getStatus()))
                .filter(m -> source.getWeightKg() == null || m.getCapacityKg() == null
                        || source.getWeightKg().compareTo(m.getCapacityKg()) <= 0)
                .sorted(Comparator
                        .comparing((Machine m) -> "IDLE".equals(m.getStatus()) ? 0 : 1)
                        .thenComparing(m -> m.getLocation() != null && m.getLocation().contains("医感") ? 0 : 1)
                        .thenComparing(Machine::getCapacityKg,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (washers.isEmpty()) {
            throw new BizException("当前没有可用的非故障水洗机容纳 " + source.getWeightKg()
                    + "kg 复洗，请先维修设备或指定设备");
        }
        return washers.get(0);
    }

    // ==================== 辅助 ====================

    private void fillTransient(ContaminationEvent event) {
        event.setContamTypeName(ContaminationType.nameOf(event.getContamType()));
        event.setStatusName(ContaminationType.eventStatusName(event.getStatus()));
    }

    private RewashOrder decorateRewash(RewashOrder order) {
        if (order != null) {
            order.setStatusName(ContaminationType.rewashStatusName(order.getStatus()));
            if (order.getRewashCurrentStepName() != null) {
                order.setRewashCurrentStepName(WashProcess.nameOf(order.getRewashCurrentStepName()));
            }
        }
        return order;
    }

    private void fillIsolationTransient(BatchIsolation isolation) {
        isolation.setStatusName(ContaminationType.isolationStatusName(isolation.getStatus()));
        if (isolation.getCurrentStep() != null) {
            isolation.setCurrentStepName(WashProcess.nameOf(isolation.getCurrentStep()));
        }
        if (isolation.getRewashOrders() != null) {
            isolation.getRewashOrders().forEach(o -> {
                o.setStatusName(ContaminationType.rewashStatusName(o.getStatus()));
                if (o.getRewashCurrentStepName() != null) {
                    o.setRewashCurrentStepName(WashProcess.nameOf(o.getRewashCurrentStepName()));
                }
            });
        }
    }

    private void validate(ContaminationEventRequest req) {
        if (req == null) {
            throw new BizException("请填写污染事件内容");
        }
        if (req.getMachineId() == null) {
            throw new BizException("请选择发生设备");
        }
        if (!ContaminationType.isValid(req.getContamType())) {
            throw new BizException("请选择污染类型");
        }
        if (req.getFoundTime() == null || req.getAffectStart() == null || req.getAffectEnd() == null) {
            throw new BizException("请填写发现时间与影响起止时间");
        }
        if (req.getAffectEnd().isBefore(req.getAffectStart()) || req.getAffectEnd().isEqual(req.getAffectStart())) {
            throw new BizException("影响结束时间必须晚于开始时间");
        }
        if (req.getFoundTime().isBefore(req.getAffectStart())) {
            throw new BizException("发现时间不能早于影响开始时间");
        }
    }

    private String eventNoOf(Long id, LocalDateTime createdAt) {
        LocalDateTime base = createdAt == null ? LocalDateTime.now() : createdAt;
        return "CR" + base.format(NO_DATE) + "-" + String.format("%03d", id);
    }

    private String rewashNoOf(Long id, LocalDateTime createdAt) {
        LocalDateTime base = createdAt == null ? LocalDateTime.now() : createdAt;
        return "RW" + base.format(NO_DATE) + "-" + String.format("%03d", id);
    }

    /** 复洗产生的新批次号：PC 日期-RW 复洗单序号，可一眼回溯到复洗单 / 原批次 */
    private String rewashBatchNoOf(RewashOrder rewash) {
        LocalDateTime base = rewash.getCreatedAt() == null ? LocalDateTime.now() : rewash.getCreatedAt();
        return "PC" + base.format(NO_DATE) + "-RW" + String.format("%03d", rewash.getId());
    }

    private String durationText(long seconds) {
        if (seconds <= 0) {
            return "0分钟";
        }
        long minutes = Math.max(1, seconds / 60);
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) {
            return m + "分钟";
        }
        return m == 0 ? h + "小时" : h + "小时" + m + "分钟";
    }

    private Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        return ((Number) v).longValue();
    }

    private int asInt(Object v) {
        if (v == null) {
            return 0;
        }
        return ((Number) v).intValue();
    }

    /** MyBatis 以 Map 返回时，时间列可能是 LocalDateTime / java.sql.Timestamp / 字符串，统一归一化 */
    private LocalDateTime toTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.util.Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
        }
        String s = v.toString().trim();
        try {
            return s.length() <= 19 ? LocalDateTime.parse(s.replace(' ', 'T'))
                    : LocalDateTime.parse(s.substring(0, 19).replace(' ', 'T'));
        } catch (Exception parseError) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankTo(String s, String fallback) {
        return isBlank(s) ? fallback : s;
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

    /** 命中候选（按批次聚合） */
    private static class HitCandidate {
        Long batchId;
        Long machineId;
        String stepCode;
        String stepName;
        String overlapText;
        String reason;
        boolean boundOnly;
        String boundStepCode;
        int boundStepOrder;
        List<Overlap> overlaps = new ArrayList<>();
    }

    private record Overlap(int order, String code, String name, LocalDateTime s, LocalDateTime e) {
        String displayName() {
            return name != null ? name : WashProcess.nameOf(code);
        }
    }
}
