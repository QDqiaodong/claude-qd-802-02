<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">水洗机 / 烘干机设备状态灯墙</span>
        <el-button @click="load">刷新</el-button>
        <span class="ll-sub">点灯轮转状态：运行 → 待机 → 故障；点故障灯弹出维修单表单</span>
      </div>
      <div style="display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px;">
        <el-tag type="success" effect="plain">运行 {{ summary.running_count || 0 }} 台</el-tag>
        <el-tag type="info" effect="plain">待机 {{ summary.idle_count || 0 }} 台</el-tag>
        <el-tag type="danger" effect="plain">故障 {{ summary.fault_count || 0 }} 台</el-tag>
        <el-tag effect="plain">累计运行 {{ summary.run_hours_total || 0 }} 小时</el-tag>
      </div>
    </div>

    <div class="ll-wall">
      <div v-for="m in machines" :key="m.id" class="ll-lamp" @click="onLampClick(m)">
        <div class="ll-bulb" :class="bulbClass(m)">
          <span>{{ statusName(m.status) }}</span>
          <span
            v-if="m.openRepairCount > 0"
            style="position: absolute; top: -2px; right: -2px; background: #ffb300; color: #3a2600;
                   border-radius: 9px; padding: 0 6px; font-size: 11px; line-height: 18px;"
          >
            {{ m.openRepairCount }} 单待修
          </span>
          <!-- 污染来源角标 -->
          <span
            v-if="m.activeContaminationCount > 0"
            class="ll-contam-badge"
          >
            ☣ 污染 {{ m.activeContaminationCount }}
          </span>
        </div>
        <div class="ll-lamp-name">{{ m.name }}</div>
        <div class="ll-lamp-meta">{{ m.model }}<br />{{ typeName(m.machineType) }} · 容量 {{ m.capacityKg }}kg</div>
        <!-- 污染来源与待处置数量 -->
        <div v-if="m.activeContaminationCount > 0" class="ll-lamp-contam">
          ☣ {{ m.activeEventNo }} · {{ contamTypeName(m.activeContamTypeName) }}
          <div v-if="m.activeAffectRange">影响 {{ m.activeAffectRange }}</div>
          <div class="ll-lamp-contam-pending">待处置隔离批次 <b>{{ m.quarantinedBatchCount || 0 }}</b> 缸</div>
        </div>
        <div class="ll-lamp-hours">{{ m.location }} · 累计 {{ m.runHours }}h</div>
      </div>
    </div>
    <div class="ll-wall-legend">
      <span><i style="background: #2fbf71"></i>运行</span>
      <span><i style="background: #8a9496"></i>待机</span>
      <span><i style="background: #e34d4d"></i>故障</span>
      <span><i style="background: #ff5d5d"></i>☣ 角标 = 存在未放行污染事件，灯下列出污染来源与待处置数量</span>
      <span>上次保养：{{ machines.map((m) => m.lastMaintainAt).filter(Boolean).sort().slice(-1)[0] || '—' }}</span>
    </div>

    <!-- 未放行污染事件：灯墙直接显示污染来源与待处置数量 -->
    <div v-if="contaminatedMachines.length" class="ll-panel" style="padding: 12px 14px; margin-top: 14px;">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
        <span class="ll-section-title">☣ 设备污染来源与待处置隔离批次</span>
        <el-button size="small" type="danger" plain @click="goContamination">前往污染追溯处置</el-button>
      </div>
      <el-table :data="contaminatedMachines" size="small">
        <el-table-column label="污染设备" width="160">
          <template #default="{ row }">{{ row.name }}（{{ row.code }}）</template>
        </el-table-column>
        <el-table-column label="污染事件" width="160">
          <template #default="{ row }">{{ row.activeEventNo }}</template>
        </el-table-column>
        <el-table-column label="污染类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="danger" effect="plain">{{ contamTypeName(row.activeContamTypeName) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="影响时段" width="120">
          <template #default="{ row }">{{ row.activeAffectRange || '—' }}</template>
        </el-table-column>
        <el-table-column label="待处置隔离批次" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="danger" effect="dark">{{ row.quarantinedBatchCount || 0 }} 缸</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default>
            <el-button size="small" type="primary" @click="goContamination">去隔离处置 / 复洗放行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="ll-panel" style="padding: 12px 14px; margin-top: 14px;">
      <div class="ll-section-title" style="margin-bottom: 8px;">设备维修单</div>
      <el-table :data="repairs" size="small">
        <el-table-column prop="id" label="单号" width="70" />
        <el-table-column prop="machineName" label="设备" width="140" />
        <el-table-column prop="faultDesc" label="故障描述" min-width="240" show-overflow-tooltip />
        <el-table-column prop="repairMan" label="维修人" width="110" />
        <el-table-column label="费用" width="90">
          <template #default="{ row }">¥{{ money(row.cost) }}</template>
        </el-table-column>
        <el-table-column prop="reportTime" label="报修时间" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'OPEN' ? 'danger' : 'success'">
              {{ row.status === 'OPEN' ? '待修' : '已完工' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN'" size="small" type="primary" @click="finish(row)">完工</el-button>
            <span v-else class="ll-sub">{{ row.finishTime }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 置为故障：登记维修单 -->
    <el-dialog v-model="faultVisible" :title="'故障报修 · ' + (current?.name || '')" width="460px">
      <el-form label-width="90px" size="small">
        <el-form-item label="设备">
          <span>{{ current?.code }} · {{ current?.model }}</span>
        </el-form-item>
        <el-form-item label="故障描述" required>
          <el-input v-model="faultForm.faultDesc" type="textarea" :rows="3"
                    placeholder="如：主轴密封圈渗漏，脱水时异响" />
        </el-form-item>
        <el-form-item label="维修人" required>
          <el-input v-model="faultForm.repairMan" style="width: 180px" placeholder="如：张伟民" />
        </el-form-item>
        <el-form-item label="维修费用">
          <el-input-number v-model="faultForm.cost" :min="0" :step="100" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="faultVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFault">登记维修单并置故障</el-button>
      </template>
    </el-dialog>

    <!-- 故障灯：查看维修单 / 追加报修 / 完工 -->
    <el-drawer v-model="repairVisible" :title="'故障设备 · ' + (current?.name || '')" size="480px">
      <el-alert type="error" :closable="false" show-icon style="margin-bottom: 12px;"
                title="设备处于故障状态，故障设备不允许排产" />
      <div class="ll-section-title" style="margin-bottom: 8px;">该设备维修记录</div>
      <el-table :data="currentRepairs" size="small" max-height="220">
        <el-table-column prop="faultDesc" label="故障描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="repairMan" label="维修人" width="90" />
        <el-table-column label="费用" width="80">
          <template #default="{ row }">¥{{ money(row.cost) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'OPEN' ? 'danger' : 'success'">
              {{ row.status === 'OPEN' ? '待修' : '完工' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button v-if="row.status === 'OPEN'" size="small" type="primary" @click="finish(row)">完工</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="ll-section-title" style="margin: 14px 0 8px;">追加报修</div>
      <el-form label-width="90px" size="small">
        <el-form-item label="故障描述">
          <el-input v-model="extraForm.faultDesc" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="维修人">
          <el-input v-model="extraForm.repairMan" style="width: 170px" />
        </el-form-item>
        <el-form-item label="维修费用">
          <el-input-number v-model="extraForm.cost" :min="0" :step="100" :precision="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitExtra">提交报修</el-button>
          <el-button @click="rotate(current)">尝试恢复运行</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { machineApi } from '../api'

const emit = defineEmits(['kpi-change'])
const router = useRouter()

const machines = ref([])
const repairs = ref([])
const summary = ref({})
const current = ref(null)
const currentRepairs = ref([])
const faultVisible = ref(false)
const repairVisible = ref(false)
const submitting = ref(false)

const faultForm = reactive({ faultDesc: '', repairMan: '', cost: 0 })
const extraForm = reactive({ faultDesc: '', repairMan: '', cost: 0 })

// 设备灯墙：存在未放行污染事件的设备（污染来源 + 待处置数量）
const contaminatedMachines = computed(() =>
  machines.value.filter((m) => m.activeContaminationCount > 0)
)

function contamTypeName(code) {
  return {
    INFECTIOUS: '感染性污染',
    BLOOD: '血渍/体液污染',
    CHEMICAL: '化学污染',
    RADIOACTIVE: '放射性污染',
    OTHER: '其他污染'
  }[code] || code
}

function goContamination() {
  router.push('/contamination')
}

function bulbClass(m) {
  return { 'is-running': m.status === 'RUNNING', 'is-idle': m.status === 'IDLE', 'is-fault': m.status === 'FAULT' }
}

function statusName(status) {
  return { RUNNING: '运行', IDLE: '待机', FAULT: '故障' }[status] || status
}

function typeName(type) {
  return { WASHER: '水洗机', DRYER: '烘干机', IRONER: '烫平机' }[type] || type
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

async function load() {
  const [list, sum, repairList] = await Promise.all([
    machineApi.list(),
    machineApi.summary(),
    machineApi.repairs()
  ])
  machines.value = list
  summary.value = sum
  repairs.value = repairList
}

async function onLampClick(m) {
  current.value = m
  if (m.status === 'FAULT') {
    currentRepairs.value = m.repairs || []
    repairVisible.value = true
    return
  }
  if (m.status === 'IDLE') {
    faultForm.faultDesc = ''
    faultForm.repairMan = ''
    faultForm.cost = 0
    faultVisible.value = true
    return
  }
  await rotate(m)
}

async function rotate(m) {
  try {
    const updated = await machineApi.nextStatus(m.id, {})
    ElMessage.success(`${m.name} 状态切换为「${statusName(updated.status)}」`)
    repairVisible.value = false
    await load()
    emit('kpi-change')
  } catch (e) {
    // 后端业务提示已由拦截器弹出
  }
}

async function submitFault() {
  submitting.value = true
  try {
    await machineApi.nextStatus(current.value.id, { ...faultForm })
    ElMessage.success(`${current.value.name} 已置为故障，维修单已登记`)
    faultVisible.value = false
    await load()
    emit('kpi-change')
  } finally {
    submitting.value = false
  }
}

async function submitExtra() {
  submitting.value = true
  try {
    await machineApi.reportFault(current.value.id, { ...extraForm })
    ElMessage.success('报修已提交')
    extraForm.faultDesc = ''
    extraForm.cost = 0
    await load()
    const fresh = machines.value.find((m) => m.id === current.value.id)
    if (fresh) {
      current.value = fresh
      currentRepairs.value = fresh.repairs || []
    }
  } finally {
    submitting.value = false
  }
}

async function finish(row) {
  await machineApi.finishRepair(row.id)
  ElMessage.success('维修单已完工，设备回到待机')
  await load()
  emit('kpi-change')
  if (current.value) {
    const fresh = machines.value.find((m) => m.id === current.value.id)
    if (fresh) {
      current.value = fresh
      currentRepairs.value = fresh.repairs || []
    }
  }
}

onMounted(load)
</script>
