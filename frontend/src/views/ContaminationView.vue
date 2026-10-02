<template>
  <div class="ll-page">
    <!-- 顶部：登记 + 说明 -->
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">医院布草污染事件追溯与隔离处置</span>
        <el-button type="primary" @click="openCreate">登记污染事件</el-button>
        <el-button @click="load">刷新</el-button>
        <el-tag type="warning" effect="plain">隔离中事件 {{ counts.confirmed }}</el-tag>
        <el-tag type="danger" effect="dark">待处置批次 {{ counts.pending }}</el-tag>
        <el-tag type="success" effect="plain">已放行 {{ counts.released }}</el-tag>
      </div>
      <div class="ll-sub" style="margin-top: 8px;">
        登记 → 确认（按设备 + 影响时段自动命中批次并锁定）→ 逐批发起复洗（生成可追溯新批次）→ 全部复洗完成后复核放行。
        并发下的确认 / 复洗 / 放行由后端行锁 + 唯一约束保证只落一份结果，重复点击不会产生重复隔离或重复复洗。
      </div>
    </div>

    <!-- 事件台账 -->
    <div class="ll-panel" style="padding: 8px 10px;">
      <el-table :data="events" size="small" @row-click="openDetail" :row-style="{ cursor: 'pointer' }">
        <el-table-column prop="eventNo" label="事件号" width="160" />
        <el-table-column label="设备" width="150">
          <template #default="{ row }">
            {{ row.machineName }} <span class="ll-sub">{{ row.machineCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="污染类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.contamType)" effect="dark">{{ row.contamTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发现时间" width="160">
          <template #default="{ row }">{{ fmt(row.foundTime) }}</template>
        </el-table-column>
        <el-table-column label="影响时段" width="200">
          <template #default="{ row }">{{ fmt(row.affectStart) }} ~ {{ fmt(row.affectEnd) }}</template>
        </el-table-column>
        <el-table-column label="命中 / 待处置" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="danger" effect="plain" v-if="row.status === 'CONFIRMED' && row.pendingCount > 0">
              {{ row.hitCount }} 命中 · {{ row.pendingCount }} 待处置
            </el-tag>
            <span v-else class="ll-sub">{{ row.hitCount }} 命中</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)" effect="plain">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              size="small"
              type="warning"
              :loading="actingId === row.id"
              @click.stop="confirmEvent(row)"
            >
              确认并隔离
            </el-button>
            <el-button
              v-if="row.status === 'CONFIRMED'"
              size="small"
              type="success"
              @click.stop="openDetail(row)"
            >
              去处置
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 登记污染事件 -->
    <el-dialog v-model="createVisible" title="登记医院布草污染事件" width="520px">
      <el-form label-width="104px" size="small">
        <el-form-item label="发生设备" required>
          <el-select v-model="form.machineId" placeholder="选择设备" style="width: 100%" filterable>
            <el-option
              v-for="m in machines"
              :key="m.id"
              :label="m.name + ' · ' + typeName(m.machineType) + ' · ' + m.location"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="污染类型" required>
          <el-select v-model="form.contamType" placeholder="选择污染类型" style="width: 100%">
            <el-option v-for="t in types" :key="t.code" :label="t.name" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="发现时间" required>
          <el-date-picker
            v-model="form.foundTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择发现时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="影响开始" required>
          <el-date-picker
            v-model="form.affectStart"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="影响开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="影响结束" required>
          <el-date-picker
            v-model="form.affectEnd"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="影响结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="污染描述">
          <el-input v-model="form.description" type="textarea" :rows="3"
                    placeholder="如：密封渗漏、疑似感染性布草交叉污染，需排查该机时段内全部过水批次" />
        </el-form-item>
        <el-form-item label="登记人">
          <el-input v-model="form.reporter" style="width: 200px" placeholder="如：感控员-郑雅芳" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">登记（待确认）</el-button>
      </template>
    </el-dialog>

    <!-- 事件详情：命中批次逐条解释 + 复洗处置 + 放行 -->
    <el-drawer v-model="detailVisible" :title="'污染事件 · ' + (detail?.eventNo || '')" size="760px">
      <div v-if="detail" class="ll-contam-detail">
        <el-descriptions :column="2" border size="small" class="ll-desc">
          <el-descriptions-item label="发生设备">
            {{ detail.machineName }}（{{ detail.machineCode }}）
          </el-descriptions-item>
          <el-descriptions-item label="污染类型">
            <el-tag size="small" :type="typeTag(detail.contamType)" effect="dark">{{ detail.contamTypeName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="发现时间">{{ fmt(detail.foundTime) }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ detail.reporter || '—' }}</el-descriptions-item>
          <el-descriptions-item label="影响时段" :span="2">
            {{ fmt(detail.affectStart) }} ~ {{ fmt(detail.affectEnd) }}
          </el-descriptions-item>
          <el-descriptions-item label="污染描述" :span="2">{{ detail.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="statusTag(detail.status)">{{ detail.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="确认 / 放行">
            <span v-if="detail.confirmedBy">{{ detail.confirmedBy }} @ {{ fmt(detail.confirmedAt) }}</span>
            <span v-if="detail.releasedBy" class="ll-sub">；放行 {{ detail.releasedBy }} @ {{ fmt(detail.releasedAt) }}</span>
            <span v-if="!detail.confirmedBy" class="ll-sub">待确认</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 待确认：确认按钮 -->
        <div v-if="detail.status === 'DRAFT'" class="ll-action-bar">
          <el-alert type="info" :closable="false" show-icon
                    title="确认后系统将按设备占用时间，找出影响窗口内实际经过该设备的批次并锁定隔离（不可重复生成）。" />
          <el-button type="warning" :loading="actingId === detail.id" @click="confirmEvent(detail)"
                     style="margin-top: 10px;">
            确认事件并生成隔离记录
          </el-button>
        </div>

        <!-- 已确认：命中批次与处置 -->
        <div v-if="detail.status === 'CONFIRMED'">
          <div class="ll-section-title" style="margin: 14px 0 8px;">
            命中批次（{{ detail.isolations?.length || 0 }}）
            <span class="ll-sub">· 待处置 {{ detail.pendingCount }} · 复洗完成 {{ detail.rewashedCount }}</span>
          </div>

          <el-empty v-if="!detail.isolations?.length" description="影响窗口内没有批次经过该设备，可直接放行" :image-size="60" />

          <div v-for="iso in detail.isolations" :key="iso.id" class="ll-iso-card"
               :class="{ 'is-released': iso.status === 'RELEASED' }">
            <div class="ll-iso-head">
              <div>
                <span class="ll-iso-batch">{{ iso.batchNo }}</span>
                <el-tag size="small" style="margin-left: 6px;">{{ iso.categoryName }} · {{ iso.weightKg }}kg</el-tag>
                <el-tag size="small" type="info" effect="plain" style="margin-left: 6px;">
                  命中时停留：{{ iso.hitStepName || iso.hitStepCode }}
                </el-tag>
              </div>
              <el-tag size="small" :type="iso.status === 'RELEASED' ? 'success' : 'danger'" effect="dark">
                {{ iso.statusName }}
              </el-tag>
            </div>
            <div class="ll-iso-reason">
              <span class="ll-iso-why">为什么命中：</span>{{ iso.hitReason }}
              <span v-if="iso.hitOverlap" class="ll-sub">（与影响窗口重叠 {{ iso.hitOverlap }}）</span>
            </div>
            <div class="ll-iso-cur ll-sub">
              当前批次状态：{{ batchStatusName(iso.batchStatus) }} · 现停留工序：
              {{ iso.currentStepName || iso.currentStep || '—' }}
            </div>

            <!-- 复洗记录 -->
            <div v-if="iso.rewashOrders?.length" class="ll-rewash-list">
              <div v-for="r in iso.rewashOrders" :key="r.id" class="ll-rewash-item">
                <div>
                  <el-tag size="small" :type="rewashTag(r.status)" effect="plain">{{ r.statusName }}</el-tag>
                  <span class="ll-rewash-no">{{ r.rewashNo }}</span>
                  <span class="ll-sub">原批次 {{ r.sourceBatchNo }} → 新批次</span>
                  <el-link type="primary" :underline="false" @click="goBatch">{{ r.rewashBatchNo }}</el-link>
                </div>
                <div class="ll-sub">
                  {{ r.machineName || '—' }} · {{ r.operator || '—' }}
                  <span v-if="r.rewashCurrentStepName">
                    · 复洗进度 {{ r.rewashCurrentStepName }}（{{ r.rewashCurrentStepOrder }}/8）
                  </span>
                </div>
                <div v-if="r.status === 'ACTIVE'" class="ll-rewash-actions">
                  <span class="ll-sub">复洗批次在批次隧道中正常推进，折叠完工后自动回写完成</span>
                  <el-button size="small" type="danger" plain @click="failRewash(r)">标记失败</el-button>
                  <el-button size="small" plain @click="deleteRewash(r)">删除复洗</el-button>
                </div>
              </div>
            </div>

            <!-- 发起复洗 -->
            <div v-if="iso.status === 'ISOLATED'" class="ll-rewash-actions">
              <el-select v-model="rewashMachine[iso.id]" placeholder="复洗水洗机（默认自动选择）"
                         size="small" style="width: 240px;" clearable>
                <el-option
                  v-for="m in rewashMachines"
                  :key="m.id"
                  :label="m.name + ' · ' + m.capacityKg + 'kg · ' + machineStatus(m.status)"
                  :value="m.id"
                  :disabled="m.status === 'FAULT'"
                />
              </el-select>
              <el-button size="small" type="primary"
                         :loading="rewashingId === iso.id"
                         @click="createRewash(iso)">
                {{ hasActiveRewash(iso) ? '查看复洗批次' : '发起复洗（生成新批次）' }}
              </el-button>
            </div>
          </div>

          <!-- 放行 -->
          <div class="ll-action-bar" style="margin-top: 14px;">
            <el-alert :type="canRelease ? 'success' : 'warning'" :closable="false" show-icon
                      :title="canRelease
                        ? '全部隔离批次均已完成复洗，可复核放行（放行后解除批次隔离，复洗成品已入库）。'
                        : '仍有批次未复洗 / 复洗在产 / 失败 / 被删除，必须全部完成后才能放行，当前事件保持隔离。'" />
            <el-button type="success" style="margin-top: 10px;" :disabled="!canRelease"
                       :loading="releasing" @click="openRelease">
              复核放行
            </el-button>
          </div>
        </div>

        <!-- 已放行 -->
        <el-alert v-if="detail.status === 'RELEASED'" type="success" :closable="false" show-icon
                  :title="'事件已于 ' + fmt(detail.releasedAt) + ' 由 ' + (detail.releasedBy || '—') + ' 复核放行'"
                  :description="detail.releaseNote || '全部隔离批次复洗完成，隔离解除。'"
                  style="margin-top: 14px;" />
      </div>
    </el-drawer>

    <!-- 放行确认 -->
    <el-dialog v-model="releaseVisible" title="复核放行污染事件" width="440px">
      <el-alert type="warning" :closable="false" show-icon
                title="放行前请确认：全部隔离批次均已有完成复洗，且无在产 / 失败 / 删除复洗。" />
      <el-form label-width="80px" size="small" style="margin-top: 12px;">
        <el-form-item label="放行人">
          <el-input v-model="releaseForm.operator" style="width: 200px" placeholder="如：感控主管" />
        </el-form-item>
        <el-form-item label="放行备注">
          <el-input v-model="releaseForm.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="releaseVisible = false">取消</el-button>
        <el-button type="success" :loading="releasing" @click="submitRelease">确认放行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contaminationApi, machineApi } from '../api'

const emit = defineEmits(['kpi-change'])
const router = useRouter()

const events = ref([])
const types = ref([])
const machines = ref([])
const createVisible = ref(false)
const submitting = ref(false)
const actingId = ref(null)
const detailVisible = ref(false)
const detail = ref(null)
const rewashingId = ref(null)
const releasing = ref(false)
const releaseVisible = ref(false)
const releaseForm = reactive({ operator: '', note: '' })
const rewashMachine = reactive({})

const rewashMachines = computed(() =>
  machines.value.filter((m) => m.machineType === 'WASHER')
)

const counts = computed(() => ({
  confirmed: events.value.filter((e) => e.status === 'CONFIRMED').length,
  released: events.value.filter((e) => e.status === 'RELEASED').length,
  pending: events.value.reduce((sum, e) => sum + (e.pendingCount || 0), 0)
}))

const canRelease = computed(() => {
  if (!detail.value || detail.value.status !== 'CONFIRMED') return false
  const list = detail.value.isolations || []
  if (!list.length) return true
  return list.every((iso) => {
    const orders = iso.rewashOrders || []
    return orders.some((r) => r.status === 'DONE') &&
      !orders.some((r) => ['ACTIVE', 'FAILED', 'DELETED'].includes(r.status))
  })
})

function hasActiveRewash(iso) {
  return (iso.rewashOrders || []).some((r) => ['ACTIVE', 'DONE'].includes(r.status))
}

function typeTag(code) {
  return { INFECTIOUS: 'danger', BLOOD: 'warning', CHEMICAL: 'warning', RADIOACTIVE: 'danger', OTHER: 'info' }[code] || 'info'
}
function statusTag(status) {
  return { DRAFT: 'info', CONFIRMED: 'danger', RELEASED: 'success' }[status] || 'info'
}
function rewashTag(status) {
  return { ACTIVE: 'warning', DONE: 'success', FAILED: 'danger', DELETED: 'info' }[status] || 'info'
}
function typeName(t) {
  return { WASHER: '水洗机', DRYER: '烘干机', IRONER: '烫平机' }[t] || t
}
function machineStatus(s) {
  return { RUNNING: '运行', IDLE: '待机', FAULT: '故障' }[s] || s
}
function batchStatusName(s) {
  return { RUNNING: '在产', DONE: '已完工', HOLD: '挂起', QUARANTINED: '污染隔离中' }[s] || s
}
function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '—'
}
function goBatch() {
  detailVisible.value = false
  router.push('/batches')
}

const form = reactive({
  machineId: null,
  contamType: 'INFECTIOUS',
  foundTime: '',
  affectStart: '',
  affectEnd: '',
  description: '',
  reporter: ''
})

async function load() {
  const [list, machineList] = await Promise.all([
    contaminationApi.listEvents(),
    machineApi.list()
  ])
  events.value = list
  machines.value = machineList
  if (detail.value) {
    const fresh = list.find((e) => e.id === detail.value.id)
    if (fresh) {
      detail.value = await contaminationApi.eventDetail(fresh.id)
    }
  }
}

async function openCreate() {
  if (!types.value.length) types.value = await contaminationApi.types()
  if (!machines.value.length) machines.value = await machineApi.list()
  Object.assign(form, {
    machineId: null, contamType: 'INFECTIOUS', foundTime: '',
    affectStart: '', affectEnd: '', description: '', reporter: ''
  })
  createVisible.value = true
}

async function submitCreate() {
  if (!form.machineId) return ElMessage.warning('请选择发生设备')
  if (!form.foundTime || !form.affectStart || !form.affectEnd) return ElMessage.warning('请填写发现时间与影响起止时间')
  submitting.value = true
  try {
    const ev = await contaminationApi.createEvent({ ...form })
    ElMessage.success('污染事件已登记，待确认')
    createVisible.value = false
    await load()
    openDetail(ev)
  } finally {
    submitting.value = false
  }
}

async function confirmEvent(row) {
  actingId.value = row.id
  try {
    const ev = await contaminationApi.confirmEvent(row.id, { operator: row.reporter })
    ElMessage.success(`已确认，命中并隔离 ${ev.hitCount || 0} 个批次`)
    await load()
    emit('kpi-change')
    openDetail(ev)
  } finally {
    actingId.value = null
  }
}

async function openDetail(row) {
  detail.value = await contaminationApi.eventDetail(row.id)
  detailVisible.value = true
}

async function createRewash(iso) {
  // 已有有效复洗：直接跳隧道查看，不再重复发起
  if (hasActiveRewash(iso)) {
    ElMessage.info('该隔离批次已有复洗批次，请到批次隧道查看推进')
    goBatch()
    return
  }
  rewashingId.value = iso.id
  try {
    await contaminationApi.createRewash(detail.value.id, iso.id, {
      machineId: rewashMachine[iso.id] || null,
      operator: detail.value.confirmedBy || detail.value.reporter,
      note: '污染复洗'
    })
    ElMessage.success('复洗已发起，已生成可追溯复洗批次')
    await load()
    emit('kpi-change')
  } finally {
    rewashingId.value = null
  }
}

async function failRewash(r) {
  await ElMessageBox.confirm(`确定将复洗 ${r.rewashNo} 标记为失败？原隔离批次将继续保持隔离，可重新发起复洗。`,
    '复洗失败', { type: 'warning' })
  await contaminationApi.failRewash(r.id, {})
  ElMessage.success('已标记失败，批次保持隔离')
  await load()
  emit('kpi-change')
}

async function deleteRewash(r) {
  await ElMessageBox.confirm(`确定删除复洗 ${r.rewashNo}？复洗记录保留可追溯，原隔离批次保持隔离，可重新发起。`,
    '删除复洗', { type: 'warning' })
  await contaminationApi.deleteRewash(r.id, {})
  ElMessage.success('复洗已删除，批次保持隔离')
  await load()
  emit('kpi-change')
}

function openRelease() {
  releaseForm.operator = detail.value.confirmedBy || ''
  releaseForm.note = ''
  releaseVisible.value = true
}

async function submitRelease() {
  releasing.value = true
  try {
    await contaminationApi.releaseEvent(detail.value.id, { ...releaseForm })
    ElMessage.success('污染事件已复核放行，隔离解除')
    releaseVisible.value = false
    detailVisible.value = false
    await load()
    emit('kpi-change')
  } finally {
    releasing.value = false
  }
}

onMounted(async () => {
  const [list, typeList, machineList] = await Promise.all([
    contaminationApi.listEvents(),
    contaminationApi.types(),
    machineApi.list()
  ])
  events.value = list
  types.value = typeList
  machines.value = machineList
})
</script>

<style scoped>
.ll-contam-detail {
  padding-right: 8px;
}
.ll-action-bar {
  background: #f7fafa;
  border: 1px solid #e2eaeb;
  border-radius: 8px;
  padding: 10px 12px;
}
.ll-iso-card {
  border: 1px solid #f0caca;
  border-left: 4px solid #d9534f;
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.ll-iso-card.is-released {
  border-color: #bfe3cf;
  border-left-color: #2f9e69;
  opacity: 0.9;
}
.ll-iso-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ll-iso-batch {
  font-weight: 700;
  color: var(--ll-deep);
}
.ll-iso-reason {
  font-size: 12px;
  color: var(--ll-ink);
  margin-top: 6px;
  line-height: 1.6;
  background: #fdf3f3;
  border-radius: 6px;
  padding: 6px 8px;
}
.ll-iso-why {
  font-weight: 700;
  color: #b3372f;
}
.ll-iso-cur {
  margin-top: 5px;
  font-size: 11px;
}
.ll-rewash-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ll-rewash-item {
  border: 1px dashed #cfd9da;
  border-radius: 6px;
  padding: 6px 8px;
  background: #fbfdfd;
  font-size: 12px;
}
.ll-rewash-no {
  font-weight: 700;
  margin: 0 6px 0 6px;
}
.ll-rewash-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
