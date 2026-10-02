<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">洗涤批次隧道式流转</span>
        <el-button type="primary" @click="openCreate">新建批次（排产）</el-button>
        <el-button @click="load">刷新</el-button>
        <el-button v-if="reviewTotal > 0" type="warning" plain @click="focusFirstReview">
          参数偏差待复核 {{ reviewTotal }} 缸
        </el-button>
        <el-button v-if="quarantinedTotal > 0" type="danger" plain @click="goContamination">
          污染隔离待处置 {{ quarantinedTotal }} 缸
        </el-button>
        <span class="ll-sub">
          点卡片底部「下一工序」录入实际值推进；实际水温 / 洗涤剂超出批次配方快照容差时批次转待复核，
          由主管填原因后按本批次放行或退回重做
        </span>
      </div>
      <div style="display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px;">
        <el-tag
          v-for="s in stepStats"
          :key="s.stepCode"
          size="small"
          :type="s.batchCount > 0 ? 'primary' : 'info'"
          effect="plain"
        >
          {{ s.stepName }} {{ s.batchCount }} 缸 / {{ s.totalWeight }}kg
        </el-tag>
        <el-tag v-if="quarantinedTotal > 0" size="small" type="danger" effect="dark">
          污染隔离 {{ quarantinedTotal }} 缸（隧道内红框展示，不计入在产站位）
        </el-tag>
        <span class="ll-sub">在产 {{ summary.runningBatch || 0 }} 缸 · 在产重量 {{ summary.runningWeight || 0 }}kg · 已完工 {{ summary.doneBatch || 0 }} 缸</span>
      </div>
    </div>

    <div class="ll-tunnel-viewport" ref="viewportRef">
      <div class="ll-tunnel-track" :style="{ transform: `translateX(${offset}px)` }">
        <div v-for="(lane, index) in lanes" :key="lane.stepCode" class="ll-tunnel-lane">
          <div class="ll-lane-head" :class="{ 'is-focus': focusIndex === index }">
            <span>{{ index + 1 }}. {{ lane.stepName }}</span>
            <span class="ll-lane-count">{{ lane.batches.length }} 缸</span>
          </div>

          <div v-if="!lane.batches.length" class="ll-sub" style="padding: 10px 6px;">— 无批次停留 —</div>

          <div
            v-for="b in lane.batches"
            :key="b.id"
            class="ll-batch-card"
            :class="{
              'is-done': b.status === 'DONE',
              'is-review': b.status === 'REVIEW',
              'is-quarantine': b.status === 'QUARANTINED' && !b.rewashOrderId,
              'is-rewash': b.rewashOrderId
            }"
          >
            <div class="ll-batch-no">
              <span>
                {{ b.batchNo }}
                <el-tag v-if="b.recipeVersionNo" size="small" type="warning" effect="plain">配方V{{ b.recipeVersionNo }}</el-tag>
              </span>
              <el-tag size="small" :type="batchTagType(b)">{{ batchStatusName(b.status) }}</el-tag>
            </div>

            <!-- 污染来源横幅 -->
            <div v-if="b.quarantineEventNo" class="ll-contam-flag" @click="goContamination">
              <span>☣ 污染来源 {{ b.quarantineEventNo }}</span>
            </div>
            <div v-if="b.rewashStatus && b.sourceBatchNo" class="ll-rewash-flag">
              复洗自 {{ b.sourceBatchNo }} · {{ rewashStatusName(b.rewashStatus) }}
            </div>
            <div v-else-if="b.rewashBatchNo && b.quarantineEventNo" class="ll-rewash-flag is-out">
              已发起复洗 → {{ b.rewashBatchNo }}
            </div>

            <div class="ll-batch-meta">
              {{ b.categoryName }} · {{ b.weightKg }}kg · {{ b.waterTemp }}℃ / {{ b.detergentMl }}ml
              <template v-if="currentStd(b)">
                <br />标准 {{ currentStd(b).targetTemp }}±{{ currentStd(b).tempTolerance }}℃
                / {{ currentStd(b).targetDetergentMl }}±{{ currentStd(b).detergentTolerance }}ml
              </template>
              <br />{{ b.machineName || '未指定设备' }} · {{ b.operator || '未指派' }}
            </div>

            <div class="ll-step-dots">
              <span
                v-for="(step, i) in stepNames"
                :key="step"
                class="ll-step-dot"
                :class="{
                  'is-done': i + 1 < b.currentStepOrder || b.status === 'DONE',
                  'is-current': i + 1 === b.currentStepOrder && b.status !== 'DONE'
                }"
                :title="step"
              >
                {{ i + 1 < b.currentStepOrder || b.status === 'DONE' ? '✓' : step.charAt(0) }}
              </span>
            </div>

            <el-button
              v-if="b.status === 'QUARANTINED'"
              type="danger"
              size="small"
              style="width: 100%"
              @click="goContamination"
            >
              ☣ 污染隔离 · 去处置
            </el-button>
            <el-button
              v-else-if="b.status === 'REVIEW'"
              type="warning"
              size="small"
              style="width: 100%"
              @click="openReview(b)"
            >
              ⚠ 参数偏差 · 去复核
            </el-button>
            <template v-else-if="b.status !== 'DONE'">
              <el-button
                type="primary"
                size="small"
                style="width: 100%"
                @click="openAdvance(b)"
              >
                下一工序 → {{ nextStepName(b) }}
              </el-button>
            </template>
            <el-button v-else size="small" style="width: 100%" disabled>
              {{ b.rewashOrderId ? '复洗完成，已入库' : '折叠完成，已入库' }}
            </el-button>
            <el-button size="small" link style="width: 100%; margin-top: 2px;" @click="openDetail(b)">
              批次详情（配方快照 / 复核记录）
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建批次 -->
    <el-dialog v-model="createVisible" title="新建洗涤批次（排产）" width="480px">
      <el-form label-width="92px" size="small">
        <el-form-item label="布草品类">
          <el-select v-model="createForm.categoryId" placeholder="选择品类" style="width: 100%" @change="onCategoryChange">
            <el-option v-for="c in categories" :key="c.id" :label="c.name + '（' + c.spec + '）'" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentRecipe" label="配方版本">
          <el-tag size="small" type="success" effect="plain">
            V{{ currentRecipe.versionNo }}（当前生效）· 可承接 {{ recipeTypeNames }} · 装载 ≤{{ currentRecipe.maxLoadRatio }}%
          </el-tag>
          <div class="ll-sub">建批时冻结该版本快照，后续发布 / 停用不影响本批次</div>
        </el-form-item>
        <el-form-item v-else-if="createForm.categoryId" label="配方版本">
          <el-tag size="small" type="danger">该品类无已发布配方，无法排产</el-tag>
        </el-form-item>
        <el-form-item label="重量 kg">
          <el-input-number v-model="createForm.weightKg" :min="1" :max="200" :step="0.5" :precision="1" />
          <div v-if="selectedMachineLimit" class="ll-sub">
            选中设备容量 {{ selectedMachineLimit.capacity }}kg · 配方装载上限 {{ selectedMachineLimit.maxLoad }}kg
          </div>
        </el-form-item>
        <el-form-item label="水洗机">
          <el-select v-model="createForm.machineId" placeholder="选择设备" style="width: 100%">
            <el-option
              v-for="m in washers"
              :key="m.id"
              :label="machineOptionLabel(m)"
              :value="m.id"
              :disabled="m.status === 'FAULT' || !machineTypeAllowed(m)"
            />
          </el-select>
          <div class="ll-sub">故障设备与配方类型不符的设备不可排产</div>
        </el-form-item>
        <el-form-item label="操作员">
          <el-input v-model="createForm.operator" style="width: 160px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" style="width: 100%" />
        </el-form-item>
        <div class="ll-sub">首道工序水温 / 洗涤剂按配方快照标准带入，推进时录入实际值并校验容差。</div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="!currentRecipe" @click="submitCreate">排产</el-button>
      </template>
    </el-dialog>

    <!-- 推进工序：录入实际值，对照配方快照标准 -->
    <el-dialog v-model="advanceVisible" :title="`推进工序 · ${advanceForm.batchNo}`" width="520px">
      <template v-if="advanceForm.batch">
        <el-alert
          v-if="advanceStd"
          type="info"
          :closable="false"
          show-icon
          :title="`当前工序「${advanceStd.stepName}」配方标准：水温 ${advanceStd.targetTemp}±${advanceStd.tempTolerance}℃ · 洗涤剂 ${advanceStd.targetDetergentMl}±${advanceStd.detergentTolerance}ml`"
          style="margin-bottom: 12px;"
        />
        <el-form label-width="110px" size="small">
          <el-form-item :label="`实际水温 ℃`">
            <el-input-number v-model="advanceForm.actualWaterTemp" :min="0" :max="180" />
            <span class="ll-sub" style="margin-left: 8px;">{{ advanceStd ? `标准 ${advanceStd.targetTemp}±${advanceStd.tempTolerance}` : '本工序实际水温' }}</span>
          </el-form-item>
          <el-form-item label="实际洗涤剂 ml">
            <el-input-number v-model="advanceForm.actualDetergentMl" :min="0" :max="5000" :step="50" />
            <span class="ll-sub" style="margin-left: 8px;">{{ advanceStd ? `标准 ${advanceStd.targetDetergentMl}±${advanceStd.detergentTolerance}` : '本工序实际用量' }}</span>
          </el-form-item>
          <template v-if="!isLastStep">
            <el-divider content-position="left">下一工序「{{ nextStepName(advanceForm.batch) }}」计划参数</el-divider>
            <el-form-item label="计划水温 ℃">
              <el-input-number v-model="advanceForm.waterTemp" :min="0" :max="180" />
              <span v-if="nextStd" class="ll-sub" style="margin-left: 8px;">标准 {{ nextStd.targetTemp }}±{{ nextStd.tempTolerance }}</span>
            </el-form-item>
            <el-form-item label="计划洗涤剂 ml">
              <el-input-number v-model="advanceForm.detergentMl" :min="0" :max="5000" :step="50" />
              <span v-if="nextStd" class="ll-sub" style="margin-left: 8px;">标准 {{ nextStd.targetDetergentMl }}±{{ nextStd.detergentTolerance }}</span>
            </el-form-item>
          </template>
          <el-form-item label="操作员">
            <el-input v-model="advanceForm.operator" style="width: 160px" />
          </el-form-item>
        </el-form>
        <div class="ll-sub">
          实际值超出配方容差时批次不会直接前进，而是转「待复核」，由主管填写原因后按本批次放行或退回重做。
        </div>
      </template>
      <template #footer>
        <el-button @click="advanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="advancing" @click="submitAdvance">
          {{ isLastStep ? '折叠完成 · 收口' : '推进到下一工序' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 参数偏差复核（主管） -->
    <el-dialog v-model="reviewVisible" title="参数偏差复核" width="560px">
      <template v-if="reviewForm.review">
        <el-descriptions :column="2" size="small" border style="margin-bottom: 12px;">
          <el-descriptions-item label="批次">{{ reviewForm.review.batchNo || reviewForm.batch.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="超差工序">{{ reviewForm.review.stepOrder }}. {{ reviewForm.review.stepName }}</el-descriptions-item>
          <el-descriptions-item label="标准水温">{{ reviewForm.review.stdWaterTemp }}℃</el-descriptions-item>
          <el-descriptions-item label="实际水温">
            <span style="color: #d9534f; font-weight: 700;">{{ reviewForm.review.actualWaterTemp }}℃</span>
          </el-descriptions-item>
          <el-descriptions-item label="标准洗涤剂">{{ reviewForm.review.stdDetergentMl }}ml</el-descriptions-item>
          <el-descriptions-item label="实际洗涤剂">
            <span style="color: #d9534f; font-weight: 700;">{{ reviewForm.review.actualDetergentMl }}ml</span>
          </el-descriptions-item>
          <el-descriptions-item label="配方版本">V{{ reviewForm.batch.recipeVersionNo }}</el-descriptions-item>
          <el-descriptions-item label="操作员">{{ reviewForm.review.operator || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px" size="small">
          <el-form-item label="复核原因" required>
            <el-input
              v-model="reviewForm.reason"
              type="textarea"
              :rows="2"
              placeholder="必填：说明偏差原因与处置依据"
            />
          </el-form-item>
          <el-form-item label="复核人">
            <el-input v-model="reviewForm.reviewer" style="width: 160px" />
          </el-form-item>
        </el-form>
        <div class="ll-sub">
          「按本批次放行」仅本批次按原推进意图继续；「退回重做」批次回到在产重新执行本工序。结论只影响该批次，不回写配方版本。
        </div>
      </template>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="danger" :loading="resolving" @click="resolveReview('REDO')">退回重做</el-button>
        <el-button type="primary" :loading="resolving" @click="resolveReview('RELEASE')">按本批次放行</el-button>
      </template>
    </el-dialog>

    <!-- 批次详情抽屉：配方快照 + 工序标准/实际对照 + 复核记录 -->
    <el-drawer v-model="detailVisible" :title="`批次详情 · ${detail ? detail.batchNo : ''}`" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" size="small" border style="margin-bottom: 12px;">
          <el-descriptions-item label="品类">{{ detail.categoryName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="batchTagType(detail)">{{ batchStatusName(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="配方版本">
            <el-tag v-if="detail.recipeVersionNo" size="small" type="warning" effect="plain">V{{ detail.recipeVersionNo }}</el-tag>
            <span v-else>—（无配方快照的历史批次）</span>
          </el-descriptions-item>
          <el-descriptions-item label="重量">{{ detail.weightKg }}kg</el-descriptions-item>
          <el-descriptions-item label="设备">{{ detail.machineName || '未指定' }}</el-descriptions-item>
          <el-descriptions-item label="操作员">{{ detail.operator || '未指派' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.recipeVersionNo" label="可承接类型">{{ snapshotInfo.machineTypes }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.recipeVersionNo" label="最大装载">{{ snapshotInfo.maxLoadRatio }}%</el-descriptions-item>
        </el-descriptions>

        <div class="ll-section-title" style="margin-bottom: 8px;">八道工序：标准参数（快照） vs 实际录入</div>
        <el-table :data="detailRows" size="small" border>
          <el-table-column label="工序" width="86">
            <template #default="{ row }">{{ row.stepOrder }}. {{ row.stepName }}</template>
          </el-table-column>
          <el-table-column label="标准水温" width="110">
            <template #default="{ row }">
              <span v-if="row.hasStd">{{ row.targetTemp }}±{{ row.tempTolerance }}℃</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="实际水温" width="90">
            <template #default="{ row }">
              <span :class="{ 'll-out-value': row.tempOut }">{{ row.actualTemp == null ? '—' : row.actualTemp + '℃' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="标准洗涤剂" width="120">
            <template #default="{ row }">
              <span v-if="row.hasStd">{{ row.targetDetergentMl }}±{{ row.detergentTolerance }}ml</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="实际洗涤剂" width="96">
            <template #default="{ row }">
              <span :class="{ 'll-out-value': row.detOut }">{{ row.actualDetergentMl == null ? '—' : row.actualDetergentMl + 'ml' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.recStatus === 'DONE'" size="small" type="info">已完成</el-tag>
              <el-tag v-else-if="row.recStatus === 'CURRENT'" size="small" type="primary">进行中</el-tag>
              <span v-else class="ll-sub">未开始</span>
            </template>
          </el-table-column>
        </el-table>

        <template v-if="detail.reviews && detail.reviews.length">
          <div class="ll-section-title" style="margin: 14px 0 8px;">偏差复核记录</div>
          <el-table :data="detail.reviews" size="small" border>
            <el-table-column label="工序" width="80">
              <template #default="{ row }">{{ row.stepOrder }}. {{ row.stepName }}</template>
            </el-table-column>
            <el-table-column label="标准 → 实际" width="170">
              <template #default="{ row }">
                {{ row.stdWaterTemp }}℃/{{ row.stdDetergentMl }}ml →
                <span style="color: #d9534f;">{{ row.actualWaterTemp }}℃/{{ row.actualDetergentMl }}ml</span>
              </template>
            </el-table-column>
            <el-table-column label="结论" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="reviewTagType(row.status)">{{ reviewStatusName(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="原因 / 复核人" min-width="150">
              <template #default="{ row }">
                <div>{{ row.reason || '待主管复核' }}</div>
                <div class="ll-sub">{{ row.reviewer || '' }} {{ row.decidedAt ? fmtTime(row.decidedAt) : '' }}</div>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { batchApi, categoryApi, machineApi, recipeApi } from '../api'

const emit = defineEmits(['kpi-change'])
const router = useRouter()

const LANE_WIDTH = 216

const batches = ref([])
const stepNames = ref([])
const stepStats = ref([])
const summary = ref({})
const categories = ref([])
const machines = ref([])
const focusIndex = ref(0)
const viewportRef = ref(null)
const viewportWidth = ref(1000)

// ------- 新建批次 -------
const createVisible = ref(false)
const creating = ref(false)
const currentRecipe = ref(null)
const createForm = reactive({
  categoryId: null,
  weightKg: 60,
  machineId: null,
  operator: '王建国',
  remark: ''
})

// ------- 推进工序 -------
const advanceVisible = ref(false)
const advancing = ref(false)
const advanceForm = reactive({
  batch: null,
  batchNo: '',
  actualWaterTemp: 0,
  actualDetergentMl: 0,
  waterTemp: 0,
  detergentMl: 0,
  operator: ''
})

// ------- 偏差复核 -------
const reviewVisible = ref(false)
const resolving = ref(false)
const reviewForm = reactive({
  batch: null,
  review: null,
  reason: '',
  reviewer: '车间主管'
})

// ------- 批次详情 -------
const detailVisible = ref(false)
const detail = ref(null)

const washers = computed(() => machines.value.filter((m) => m.machineType === 'WASHER'))

// 隧道展示在产 + 待复核 + 污染隔离批次
const tunnelBatches = computed(() =>
  batches.value.filter((b) => b.status === 'RUNNING' || b.status === 'REVIEW' || b.status === 'QUARANTINED')
)

const quarantinedTotal = computed(() => batches.value.filter((b) => b.status === 'QUARANTINED').length)
const reviewTotal = computed(() => batches.value.filter((b) => b.status === 'REVIEW').length)

const lanes = computed(() => {
  const names = stepNames.value.length
    ? stepNames.value
    : ['预洗', '主洗', '漂洗', '中和', '脱水', '烘干', '烫平', '折叠']
  return names.map((stepName, index) => ({
    stepName,
    stepCode: ['PRE_WASH', 'MAIN_WASH', 'RINSE', 'NEUTRALIZE', 'EXTRACT', 'DRY', 'IRON', 'FOLD'][index],
    batches: tunnelBatches.value.filter((b) => b.currentStepOrder === index + 1)
  }))
})

function batchStatusName(status) {
  return {
    RUNNING: '在产',
    DONE: '已完工',
    HOLD: '挂起',
    QUARANTINED: '污染隔离',
    REVIEW: '待复核'
  }[status] || status
}
function batchTagType(b) {
  if (b.status === 'DONE') return 'info'
  if (b.status === 'QUARANTINED') return 'danger'
  if (b.status === 'REVIEW') return 'warning'
  return 'success'
}
function rewashStatusName(s) {
  return { ACTIVE: '复洗在产', DONE: '复洗完成', FAILED: '复洗失败', DELETED: '已删除' }[s] || s
}
function reviewStatusName(s) {
  return { PENDING: '待复核', RELEASED: '已放行', REDONE: '已退回重做' }[s] || s
}
function reviewTagType(s) {
  return { PENDING: 'warning', RELEASED: 'success', REDONE: 'danger' }[s] || 'info'
}
function goContamination() {
  router.push('/contamination')
}

const visibleLanes = computed(() => Math.max(1, Math.floor(viewportWidth.value / LANE_WIDTH)))

const offset = computed(() => {
  const maxIndex = Math.max(0, lanes.value.length - visibleLanes.value)
  const centered = focusIndex.value - Math.floor((visibleLanes.value - 1) / 2)
  return -Math.min(maxIndex, Math.max(0, centered)) * LANE_WIDTH
})

function nextStepName(batch) {
  const names = stepNames.value.length ? stepNames.value : []
  return names[batch.currentStepOrder] || '折叠完工'
}

function machineStatusName(status) {
  return { RUNNING: '运行', IDLE: '待机', FAULT: '故障' }[status] || status
}

/** 批次当前停留工序的配方标准（来自批次快照） */
function currentStd(batch) {
  const steps = batch.recipeSteps || []
  return steps.find((s) => s.stepOrder === batch.currentStepOrder) || null
}

async function load(keepFocus = false) {
  // 不带状态过滤：隧道需要同时展示在产 / 待复核 / 污染隔离批次
  const params = {}
  const [list, names, stats, sum] = await Promise.all([
    batchApi.list(params),
    batchApi.steps(),
    batchApi.stepStats(),
    batchApi.summary()
  ])
  batches.value = list
  stepNames.value = names
  stepStats.value = stats
  summary.value = sum
  if (!keepFocus) {
    focusToDefault()
  }
}

function focusToDefault() {
  if (!batches.value.length) {
    focusIndex.value = 0
    return
  }
  const running = batches.value.filter((b) => b.status === 'RUNNING' || b.status === 'REVIEW')
  const target = running.length ? running : batches.value
  const maxOrder = Math.max(...target.map((b) => b.currentStepOrder || 1))
  focusIndex.value = maxOrder - 1
}

function focusFirstReview() {
  const first = batches.value.find((b) => b.status === 'REVIEW')
  if (first) {
    focusIndex.value = (first.currentStepOrder || 1) - 1
  }
}

// ==================== 推进工序 ====================

const isLastStep = computed(() => (advanceForm.batch ? advanceForm.batch.currentStepOrder >= 8 : false))
const advanceStd = computed(() => (advanceForm.batch ? currentStd(advanceForm.batch) : null))
const nextStd = computed(() => {
  if (!advanceForm.batch) return null
  const steps = advanceForm.batch.recipeSteps || []
  return steps.find((s) => s.stepOrder === advanceForm.batch.currentStepOrder + 1) || null
})

function openAdvance(batch) {
  advanceForm.batch = batch
  advanceForm.batchNo = batch.batchNo
  advanceForm.actualWaterTemp = batch.waterTemp
  advanceForm.actualDetergentMl = batch.detergentMl
  const std = batch.recipeSteps
    ? batch.recipeSteps.find((s) => s.stepOrder === batch.currentStepOrder + 1)
    : null
  advanceForm.waterTemp = std ? std.targetTemp : batch.waterTemp
  advanceForm.detergentMl = std ? std.targetDetergentMl : batch.detergentMl
  advanceForm.operator = batch.operator
  advanceVisible.value = true
}

async function submitAdvance() {
  const batch = advanceForm.batch
  advancing.value = true
  try {
    const payload = {
      operator: advanceForm.operator,
      actualWaterTemp: advanceForm.actualWaterTemp,
      actualDetergentMl: advanceForm.actualDetergentMl
    }
    if (!isLastStep.value) {
      payload.waterTemp = advanceForm.waterTemp
      payload.detergentMl = advanceForm.detergentMl
    }
    const updated = await batchApi.advance(batch.id, payload)
    advanceVisible.value = false
    if (updated.status === 'REVIEW') {
      ElMessage.warning(`${batch.batchNo} 实际参数超出配方容差，已转「待复核」，需主管处理后才能继续`)
    } else {
      ElMessage.success(`${batch.batchNo} 已推进到「${stepNames.value[(updated.currentStepOrder || 1) - 1] || '完工'}」`)
    }
    focusIndex.value = Math.min(lanes.value.length - 1, (updated.currentStepOrder || 1) - 1)
    await load(true)
    emit('kpi-change')
  } finally {
    advancing.value = false
  }
}

// ==================== 偏差复核 ====================

async function openReview(batch) {
  const full = await batchApi.detail(batch.id)
  const pending = (full.reviews || []).find((r) => r.status === 'PENDING')
  if (!pending) {
    ElMessage.info('该批次暂无待复核记录，请刷新确认状态')
    await load(true)
    return
  }
  reviewForm.batch = full
  reviewForm.review = pending
  reviewForm.reason = ''
  reviewVisible.value = true
}

async function resolveReview(decision) {
  if (!reviewForm.reason.trim()) {
    ElMessage.warning('请填写复核原因')
    return
  }
  resolving.value = true
  try {
    await batchApi.resolveReview(reviewForm.review.id, {
      decision,
      reason: reviewForm.reason,
      reviewer: reviewForm.reviewer
    })
    ElMessage.success(decision === 'RELEASE' ? '已按本批次放行，批次继续推进' : '已退回重做，批次回到在产')
    reviewVisible.value = false
    await load(true)
    emit('kpi-change')
  } finally {
    resolving.value = false
  }
}

// ==================== 批次详情 ====================

const detailRows = computed(() => {
  const d = detail.value
  if (!d) return []
  const stds = d.recipeSteps || []
  const records = d.steps || []
  const names = stepNames.value.length
    ? stepNames.value
    : ['预洗', '主洗', '漂洗', '中和', '脱水', '烘干', '烫平', '折叠']
  const codes = ['PRE_WASH', 'MAIN_WASH', 'RINSE', 'NEUTRALIZE', 'EXTRACT', 'DRY', 'IRON', 'FOLD']
  return codes.map((code, i) => {
    const std = stds.find((s) => s.stepCode === code)
    const rec = records.find((r) => r.stepCode === code)
    const actualTemp = rec ? rec.waterTemp : null
    const actualDet = rec ? rec.detergentMl : null
    return {
      stepCode: code,
      stepName: std ? std.stepName : names[i],
      stepOrder: i + 1,
      hasStd: !!std,
      targetTemp: std ? std.targetTemp : null,
      tempTolerance: std ? std.tempTolerance : null,
      targetDetergentMl: std ? std.targetDetergentMl : null,
      detergentTolerance: std ? std.detergentTolerance : null,
      actualTemp,
      actualDetergentMl: actualDet,
      recStatus: rec ? rec.status : null,
      tempOut: std && actualTemp != null && Math.abs(actualTemp - std.targetTemp) > std.tempTolerance,
      detOut: std && actualDet != null && Math.abs(actualDet - std.targetDetergentMl) > std.detergentTolerance
    }
  })
})

const snapshotInfo = computed(() => {
  const d = detail.value
  if (!d || !d.recipeSnapshot) {
    return { machineTypes: '', maxLoadRatio: '' }
  }
  try {
    const snap = JSON.parse(d.recipeSnapshot)
    const map = { WASHER: '水洗机', DRYER: '烘干机', IRONER: '烫平机' }
    return {
      machineTypes: String(snap.machineTypes || '')
        .split(',')
        .filter(Boolean)
        .map((t) => map[t] || t)
        .join('、'),
      maxLoadRatio: snap.maxLoadRatio
    }
  } catch (e) {
    return { machineTypes: '', maxLoadRatio: '' }
  }
})

async function openDetail(batch) {
  detail.value = await batchApi.detail(batch.id)
  detailVisible.value = true
}

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(5, 16) : ''
}

// ==================== 新建批次 ====================

const recipeTypeNames = computed(() => {
  if (!currentRecipe.value) return ''
  const map = { WASHER: '水洗机', DRYER: '烘干机', IRONER: '烫平机' }
  return String(currentRecipe.value.machineTypes || '')
    .split(',')
    .filter(Boolean)
    .map((t) => map[t] || t)
    .join('、')
})

const selectedMachineLimit = computed(() => {
  const m = machines.value.find((item) => item.id === createForm.machineId)
  if (!m || !currentRecipe.value) return null
  // 装载上限 = 设备容量 × 配方最大装载比例（保留两位小数向下取整，与后端一致）
  const maxLoad = Math.floor(Number(m.capacityKg) * currentRecipe.value.maxLoadRatio) / 100
  return { capacity: m.capacityKg, maxLoad }
})

function machineTypeAllowed(m) {
  if (!currentRecipe.value) return true
  return String(currentRecipe.value.machineTypes || '').split(',').includes(m.machineType)
}

function machineOptionLabel(m) {
  const base = `${m.name} · 容量 ${m.capacityKg}kg · ${machineStatusName(m.status)}`
  if (m.status === 'FAULT') return base + '（故障不可排产）'
  if (!machineTypeAllowed(m)) return base + '（配方类型不符）'
  return base
}

async function onCategoryChange() {
  createForm.machineId = null
  currentRecipe.value = createForm.categoryId
    ? await recipeApi.current(createForm.categoryId)
    : null
}

async function openCreate() {
  const [cats, machs] = await Promise.all([categoryApi.list(), machineApi.list()])
  categories.value = cats
  machines.value = machs
  createVisible.value = true
  if (createForm.categoryId) {
    await onCategoryChange()
  }
}

async function submitCreate() {
  creating.value = true
  try {
    await batchApi.create({ ...createForm })
    ElMessage.success('排产成功，批次已进入预洗工序')
    createVisible.value = false
    focusIndex.value = 0
    await load()
    emit('kpi-change')
  } finally {
    creating.value = false
  }
}

function measure() {
  if (viewportRef.value) {
    viewportWidth.value = viewportRef.value.clientWidth
  }
}

onMounted(async () => {
  await load()
  await nextTick(measure)
  window.addEventListener('resize', measure)
})
onUnmounted(() => {
  window.removeEventListener('resize', measure)
})
</script>

<style scoped>
/* 待复核批次：橙框警示 */
.ll-batch-card.is-review {
  border: 1px solid #eccb8d;
  border-left: 4px solid #d98b1f;
  background: #fffaf0;
  box-shadow: 0 0 0 1px rgba(217, 139, 31, 0.12), 0 2px 8px rgba(217, 139, 31, 0.16);
}

/* 超差实际值标红 */
.ll-out-value {
  color: #d9534f;
  font-weight: 700;
}
</style>
