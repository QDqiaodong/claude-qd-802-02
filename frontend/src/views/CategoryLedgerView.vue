<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">布草品类折叠台账 · 快速盘点</span>
        <el-button @click="toggleAll">{{ allOpen ? '全部收起' : '全部展开' }}</el-button>
        <el-button @click="load">刷新</el-button>
        <el-tag type="danger" effect="plain" v-if="shortageCount > 0">
          {{ shortageCount }} 个品类低于最低周转量
        </el-tag>
        <span class="ll-sub">卡片上用 −/+ 改在库数，底部即时显示差异件数与损益金额，确认后「保存盘点」落库</span>
      </div>
    </div>

    <el-collapse v-model="activeNames" style="margin-bottom: 76px;">
      <el-collapse-item v-for="c in categories" :key="c.id" :name="c.id">
        <template #title>
          <div style="display: flex; align-items: center; gap: 10px; width: 100%; padding-right: 12px;">
            <span class="ll-cat-name" style="min-width: 74px;">{{ c.name }}</span>
            <span class="ll-cat-chip">{{ c.spec }}</span>
            <span class="ll-cat-chip">单价 ¥{{ c.unitPrice }}</span>
            <span class="ll-cat-chip">报废率 {{ c.scrapRate }}%</span>
            <span class="ll-cat-chip">最低周转 {{ c.minTurnover }}</span>
            <span
              class="ll-cat-chip"
              :class="{ 'is-warn': gapOf(c) > 0 }"
            >
              {{ gapOf(c) > 0 ? '缺口 ' + gapOf(c) + ' 件' : '周转充足' }}
            </span>
            <span style="flex: 1"></span>
            <span class="ll-sub">在库</span>
            <el-button size="small" circle @click.stop="step(c, -10)">−</el-button>
            <span class="ll-stock-num" :class="{ 'is-changed': diffOf(c) !== 0 }">{{ draft[c.id] }}</span>
            <el-button size="small" circle type="primary" @click.stop="step(c, 10)">+</el-button>
          </div>
        </template>

        <div style="display: flex; flex-wrap: wrap; gap: 18px; padding: 4px 6px 10px;">
          <div>
            <div class="ll-sub">在库金额</div>
            <div style="font-size: 16px; font-weight: 700; color: #006064;">
              ¥{{ money((draft[c.id] || 0) * c.unitPrice) }}
            </div>
          </div>
          <div>
            <div class="ll-sub">本月预计报废（按报废率）</div>
            <div style="font-size: 16px; font-weight: 700; color: #b3372f;">
              {{ Math.round((draft[c.id] || 0) * c.scrapRate / 100) }} 件
            </div>
          </div>
          <div>
            <div class="ll-sub">洗涤要点</div>
            <div style="font-size: 13px;">{{ c.remark || '—' }}</div>
          </div>
          <div>
            <div class="ll-sub">归属</div>
            <div style="font-size: 13px;">{{ ownerName(c.ownerType) }}</div>
          </div>
        </div>

        <div style="padding: 0 6px 10px;">
          <div class="ll-sub" style="margin-bottom: 4px;">该品类近期洗涤批次（关联映射带出）</div>
          <el-table v-if="c.recentBatches && c.recentBatches.length" :data="c.recentBatches" size="small">
            <el-table-column prop="batchNo" label="批次号" width="140" />
            <el-table-column prop="weightKg" label="重量 kg" width="90" />
            <el-table-column label="当前工序" width="100">
              <template #default="{ row }">{{ row.currentStepOrder }} / 8</template>
            </el-table-column>
            <el-table-column prop="machineName" label="设备" width="120" />
            <el-table-column prop="operator" label="操作员" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'DONE' ? 'info' : 'success'">
                  {{ row.status === 'DONE' ? '已完工' : '在产' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <span v-else class="ll-sub">暂无洗涤批次</span>
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- 盘点结果条 -->
    <div class="ll-count-bar">
      <span class="ll-sub">本次盘点</span>
      <span>调整品类 <span class="ll-count-figure" style="color: #006064;">{{ changedList.length }}</span> 个</span>
      <span>差异件数
        <span class="ll-count-figure" :style="{ color: diffQty === 0 ? '#5a6b6d' : (diffQty > 0 ? '#2f9e69' : '#d9534f') }">
          {{ diffQty > 0 ? '+' : '' }}{{ diffQty }}
        </span> 件
      </span>
      <span>损益金额
        <span class="ll-count-figure" :style="{ color: gainLoss === 0 ? '#5a6b6d' : (gainLoss > 0 ? '#2f9e69' : '#d9534f') }">
          ¥{{ money(gainLoss) }}
        </span>
      </span>
      <span style="flex: 1"></span>
      <el-button @click="resetCount">撤销调整</el-button>
      <el-button type="primary" :loading="saving" :disabled="!changedList.length" @click="saveCount">
        保存盘点
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi } from '../api'

const emit = defineEmits(['kpi-change'])

const categories = ref([])
const draft = ref({})
const activeNames = ref([])
const saving = ref(false)
const allOpen = ref(false)

function gapOf(c) {
  return Math.max(0, (c.minTurnover || 0) - (draft.value[c.id] ?? c.stockQty ?? 0))
}

function diffOf(c) {
  return (draft.value[c.id] ?? c.stockQty ?? 0) - (c.stockQty || 0)
}

const changedList = computed(() => categories.value.filter((c) => diffOf(c) !== 0))

const shortageCount = computed(() => categories.value.filter((c) => gapOf(c) > 0).length)

const diffQty = computed(() => changedList.value.reduce((sum, c) => sum + diffOf(c), 0))

const gainLoss = computed(() =>
  changedList.value.reduce((sum, c) => sum + diffOf(c) * Number(c.unitPrice || 0), 0)
)

function money(value) {
  return Number(value || 0).toFixed(2)
}

function ownerName(type) {
  return { HOTEL: '酒店专用', HOSPITAL: '医感专用', COMMON: '通用' }[type] || type
}

function step(c, delta) {
  const current = draft.value[c.id] ?? c.stockQty ?? 0
  draft.value[c.id] = Math.max(0, current + delta)
}

function resetCount() {
  const map = {}
  categories.value.forEach((c) => { map[c.id] = c.stockQty })
  draft.value = map
}

function toggleAll() {
  allOpen.value = !allOpen.value
  activeNames.value = allOpen.value ? categories.value.map((c) => c.id) : []
}

async function load() {
  const list = await categoryApi.list()
  categories.value = list
  const map = {}
  list.forEach((c) => { map[c.id] = c.stockQty })
  draft.value = map
  activeNames.value = allOpen.value ? list.map((c) => c.id) : [list[0]?.id].filter(Boolean)
}

async function saveCount() {
  saving.value = true
  try {
    const result = await categoryApi.saveCount(
      changedList.value.map((c) => ({ categoryId: c.id, stockQty: draft.value[c.id] }))
    )
    let message = `盘点已保存：调整 ${result.changedCount} 个品类，差异 ${result.diffQty} 件，损益 ¥${money(result.gainLoss)}`
    if (result.warnings && result.warnings.length) {
      ElMessageBox.alert(result.warnings.join('<br/>'), '周转缺口提醒', {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '知道了'
      })
    }
    ElMessage.success(message)
    await load()
    emit('kpi-change')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
