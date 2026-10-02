<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">客户网点地图热区</span>
        <el-input v-model="filters.keyword" placeholder="网点名称 / 编号 / 联系人" clearable style="width: 210px"
                  @keyup.enter="load" @clear="load" />
        <el-select v-model="filters.outletType" placeholder="网点类型" clearable style="width: 130px" @change="load">
          <el-option label="酒店" value="HOTEL" />
          <el-option label="医院" value="HOSPITAL" />
        </el-select>
        <el-select v-model="filters.settleType" placeholder="结算方式" clearable style="width: 130px" @change="load">
          <el-option label="月结" value="MONTHLY" />
          <el-option label="计件" value="COUNT" />
          <el-option label="现结" value="CASH" />
        </el-select>
        <el-checkbox v-model="filters.warnOnly" @change="load">只看待收预警</el-checkbox>
        <el-button type="primary" @click="load">刷新</el-button>
        <span class="ll-sub">色块深浅 = 脏布草待收量；点色块在右侧抽屉登记收发件数</span>
      </div>
      <div style="display: flex; align-items: center; gap: 10px; margin-top: 10px;">
        <span class="ll-sub">待收量</span>
        <span class="ll-sub">浅</span>
        <span v-for="(c, i) in heatColors" :key="i" class="ll-legend-box" :style="{ background: c }"></span>
        <span class="ll-sub">深</span>
        <el-divider direction="vertical" />
        <span class="ll-sub">共 {{ outlets.length }} 家网点，待收合计 {{ pendingTotal }} 件，预警 {{ warnCount }} 家</span>
        <el-divider direction="vertical" />
        <span class="ll-sub">点击色块 → 右侧抽屉登记收脏 / 送净件数</span>
      </div>
    </div>

    <div class="ll-map-wrap">
      <svg class="ll-map-svg" viewBox="0 0 1000 560" preserveAspectRatio="none">
        <defs>
          <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M40 0 L0 0 0 40" fill="none" stroke="#cfe0de" stroke-width="1" />
          </pattern>
        </defs>
        <rect width="1000" height="560" fill="url(#grid)" />
        <path d="M -20 470 C 180 430, 320 520, 520 470 S 820 400, 1020 430 L 1020 520 L -20 520 Z"
              fill="#bcdfe4" stroke="#8fc6cd" stroke-width="2" />
        <text x="24" y="505" fill="#4d8b90" font-size="13">钱塘江</text>
        <g stroke="#ffffff" stroke-width="14" opacity="0.95">
          <path d="M 60 90 L 940 90" />
          <path d="M 60 330 L 940 330" />
          <path d="M 300 40 L 300 530" />
          <path d="M 660 40 L 660 530" />
        </g>
        <g stroke="#e0c98a" stroke-width="3" stroke-dasharray="14 10">
          <path d="M 60 90 L 940 90" />
          <path d="M 60 330 L 940 330" />
        </g>
        <g fill="#9ab3b4" font-size="12">
          <text x="120" y="80">滨江大道</text>
          <text x="700" y="80">钱江路</text>
          <text x="120" y="320">环城北路</text>
          <text x="700" y="320">德胜快速路</text>
        </g>
        <rect x="14" y="14" width="972" height="532" fill="none" stroke="#a9c4c5" stroke-width="2" stroke-dasharray="8 6" />
      </svg>

      <div
        v-for="o in outlets"
        :key="o.id"
        class="ll-outlet-block"
        :class="{ 'is-selected': current && current.id === o.id }"
        :style="blockStyle(o)"
        @click="openDrawer(o)"
      >
        <span v-if="isWarn(o)" class="ll-outlet-warn">⚠ 预警</span>
        <div class="ll-outlet-name">{{ o.name }}</div>
        <div class="ll-outlet-meta">
          <span>{{ o.district }}</span>
          <span>待收 {{ o.dirtyPending }}</span>
        </div>
      </div>
    </div>

    <!-- 右侧滑出抽屉：登记收发件数 -->
    <el-drawer v-model="drawer" :title="current ? current.name + ' · 收发登记' : ''" size="440px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
          <el-descriptions-item label="网点编号">{{ current.code }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ current.outletType === 'HOTEL' ? '酒店' : '医院' }}</el-descriptions-item>
          <el-descriptions-item label="片区">{{ current.district }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ current.contact }} / {{ current.phone }}</el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ settleName(current.settleType) }}</el-descriptions-item>
          <el-descriptions-item label="月结额度">¥{{ money(current.creditLimit) }}</el-descriptions-item>
          <el-descriptions-item label="脏布草待收">
            <span :style="{ color: isWarn(current) ? '#d9534f' : '#006064', fontWeight: 700 }">
              {{ current.dirtyPending }} 件
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="预警阈值">{{ current.warnThreshold }} 件</el-descriptions-item>
        </el-descriptions>

        <div class="ll-section-title" style="margin-bottom: 8px;">登记收发件数</div>
        <el-form label-width="88px" size="small">
          <el-form-item label="脏件数（收）">
            <el-input-number v-model="form.dirtyQty" :min="1" :max="9999" />
          </el-form-item>
          <el-form-item label="净件数（送）">
            <el-input-number v-model="form.cleanQty" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="短少">
            <span :style="{ color: shortage > 0 ? '#d9534f' : '#2f9e69', fontWeight: 700 }">
              {{ shortage }} 件
            </span>
            <span class="ll-sub" style="margin-left: 8px;">净件数不得大于脏件数</span>
          </el-form-item>
          <el-form-item label="赔付金额" v-if="shortage > 0">
            <el-input-number v-model="form.compensation" :min="0" :step="10" :precision="2" />
            <span class="ll-sub" style="margin-left: 8px;">短少必须登记赔付</span>
          </el-form-item>
          <el-form-item label="司机">
            <el-input v-model="form.driver" placeholder="如 马海涛" style="width: 150px" />
          </el-form-item>
          <el-form-item label="车牌">
            <el-input v-model="form.plate" placeholder="如 浙A7K521" style="width: 150px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="异常情况说明" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">登记收送单</el-button>
            <el-button @click="drawer = false">关闭</el-button>
          </el-form-item>
        </el-form>

        <div class="ll-section-title" style="margin: 12px 0 8px;">最近收送单</div>
        <el-table :data="recentOrders" size="small" max-height="240">
          <el-table-column prop="orderNo" label="单号" width="130" />
          <el-table-column label="脏 / 净" width="100">
            <template #default="{ row }">{{ row.dirtyQty }} / {{ row.cleanQty }}</template>
          </el-table-column>
          <el-table-column label="短少" width="70">
            <template #default="{ row }">
              <span :style="{ color: row.shortageQty > 0 ? '#d9534f' : '' }">{{ row.shortageQty }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="driver" label="司机" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { outletApi } from '../api'

const emit = defineEmits(['kpi-change'])

const outlets = ref([])
const recentOrders = ref([])
const drawer = ref(false)
const current = ref(null)
const submitting = ref(false)
const filters = reactive({ keyword: '', outletType: '', settleType: '', warnOnly: false })

const form = reactive({ dirtyQty: 120, cleanQty: 120, compensation: 0, driver: '', plate: '', remark: '' })

const heatColors = ['#d5eaea', '#a3cfd0', '#6cb0b2', '#2c8487', '#004e52']

const maxPending = computed(() => Math.max(1, ...outlets.value.map((o) => o.dirtyPending || 0)))
const pendingTotal = computed(() => outlets.value.reduce((sum, o) => sum + (o.dirtyPending || 0), 0))
const warnCount = computed(() => outlets.value.filter(isWarn).length)
const shortage = computed(() => Math.max(0, (form.dirtyQty || 0) - (form.cleanQty || 0)))

function heatLevel(o) {
  const ratio = (o.dirtyPending || 0) / maxPending.value
  return Math.min(4, Math.floor(ratio * 5))
}

function blockStyle(o) {
  const level = heatLevel(o)
  return {
    left: `calc(6% + ${o.mapX}% * 0.88)`,
    top: `calc(8% + ${o.mapY}% * 0.84)`,
    background: heatColors[level],
    color: level >= 2 ? '#ffffff' : '#104142'
  }
}

function isWarn(o) {
  return o.warnThreshold > 0 && o.dirtyPending >= o.warnThreshold
}

function settleName(type) {
  return { MONTHLY: '月结', COUNT: '计件', CASH: '现结' }[type] || type
}

function statusName(status) {
  return { PICKED: '已收待送', DELIVERED: '已送达', CLOSED: '已结算' }[status] || status
}

function statusType(status) {
  return { PICKED: 'warning', DELIVERED: 'success', CLOSED: 'info' }[status] || 'info'
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

async function load() {
  outlets.value = await outletApi.list({
    keyword: filters.keyword || undefined,
    outletType: filters.outletType || undefined,
    settleType: filters.settleType || undefined,
    warnOnly: filters.warnOnly || undefined
  })
}

async function openDrawer(outlet) {
  current.value = outlet
  drawer.value = true
  form.dirtyQty = Math.min(outlet.dirtyPending || 100, 9999) || 100
  form.cleanQty = form.dirtyQty
  form.compensation = 0
  form.remark = ''
  recentOrders.value = await outletApi.orders(outlet.id)
}

async function submit() {
  submitting.value = true
  try {
    await outletApi.register(current.value.id, {
      dirtyQty: form.dirtyQty,
      cleanQty: form.cleanQty,
      compensation: shortage.value > 0 ? form.compensation : 0,
      driver: form.driver,
      plate: form.plate,
      remark: form.remark
    })
    ElMessage.success('收发件数已登记')
    drawer.value = false
    await load()
    emit('kpi-change')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
