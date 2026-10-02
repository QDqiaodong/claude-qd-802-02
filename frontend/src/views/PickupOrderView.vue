<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">收送单台账</span>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 130px" @change="load">
          <el-option label="已收待送" value="PICKED" />
          <el-option label="已送达" value="DELIVERED" />
          <el-option label="已结算" value="CLOSED" />
        </el-select>
        <el-input v-model="filters.driver" placeholder="司机" clearable style="width: 130px" @keyup.enter="load" />
        <el-checkbox v-model="filters.shortageOnly" @change="load">只看有短少</el-checkbox>
        <el-button type="primary" @click="load">刷新</el-button>
        <span class="ll-sub">今日收脏 {{ today.dirty_total || 0 }} 件 · 送净 {{ today.clean_total || 0 }} 件 · 短少 {{ today.shortage_total || 0 }} 件 · 赔付 ¥{{ money(today.compensation_total) }}</span>
      </div>
    </div>

    <div class="ll-panel" style="padding: 6px 8px;">
      <el-table :data="orders" size="small" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 6px 14px;">
              <div class="ll-sub" style="margin-bottom: 4px;">收送明细（按布草品类）</div>
              <el-table :data="row.lines || []" size="small" border>
                <el-table-column prop="categoryName" label="品类" width="110" />
                <el-table-column prop="spec" label="规格" width="150" />
                <el-table-column prop="dirtyQty" label="脏件数" width="90" />
                <el-table-column prop="cleanQty" label="净件数" width="90" />
                <el-table-column label="短少" width="80">
                  <template #default="{ row: line }">
                    <span :style="{ color: line.shortageQty > 0 ? '#d9534f' : '' }">{{ line.shortageQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="赔付金额" width="100">
                  <template #default="{ row: line }">¥{{ money(line.lossAmount) }}</template>
                </el-table-column>
              </el-table>
              <div v-if="!(row.lines || []).length" class="ll-sub">该单为整单登记，无品类明细</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="收送单号" width="150" />
        <el-table-column label="网点" min-width="180">
          <template #default="{ row }">
            {{ row.outletName }}<span class="ll-sub"> · {{ row.district }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="pickupTime" label="收脏时间" width="170" />
        <el-table-column prop="deliverTime" label="送净时间" width="170">
          <template #default="{ row }">{{ row.deliverTime || '—' }}</template>
        </el-table-column>
        <el-table-column label="脏 / 净" width="110">
          <template #default="{ row }">
            <span style="color: #006064; font-weight: 700;">{{ row.dirtyQty }}</span>
            / <span style="color: #2f9e69; font-weight: 700;">{{ row.cleanQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="短少 / 赔付" width="130">
          <template #default="{ row }">
            <span :style="{ color: row.shortageQty > 0 ? '#d9534f' : '' }">{{ row.shortageQty }} 件</span>
            <span class="ll-sub"> / ¥{{ money(row.compensation) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="driver" label="司机" width="90" />
        <el-table-column prop="plate" label="车牌" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PICKED'" size="small" type="primary" @click="openDeliver(row)">
              送净登记
            </el-button>
            <span v-else class="ll-sub">已完成</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="deliverVisible" :title="'送净登记 · ' + (currentOrder?.orderNo || '')" width="430px">
      <el-form label-width="96px" size="small">
        <el-form-item label="脏件数">
          <span style="font-weight: 700;">{{ currentOrder?.dirtyQty }} 件</span>
        </el-form-item>
        <el-form-item label="净件数">
          <el-input-number v-model="deliverForm.cleanQty" :min="0" :max="currentOrder?.dirtyQty || 0" />
        </el-form-item>
        <el-form-item label="短少">
          <span :style="{ color: deliverShortage > 0 ? '#d9534f' : '#2f9e69', fontWeight: 700 }">
            {{ deliverShortage }} 件
          </span>
        </el-form-item>
        <el-form-item label="赔付金额" v-if="deliverShortage > 0">
          <el-input-number v-model="deliverForm.compensation" :min="0" :step="10" :precision="2" />
        </el-form-item>
        <el-form-item label="司机 / 车牌">
          <el-input v-model="deliverForm.driver" style="width: 100px" />
          <el-input v-model="deliverForm.plate" style="width: 110px; margin-left: 6px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="deliverForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDeliver">登记送净</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { orderApi } from '../api'

const emit = defineEmits(['kpi-change'])

const orders = ref([])
const today = ref({})
const filters = reactive({ status: '', driver: '', shortageOnly: false })
const deliverVisible = ref(false)
const currentOrder = ref(null)
const submitting = ref(false)
const deliverForm = reactive({ cleanQty: 0, compensation: 0, driver: '', plate: '', remark: '' })

const deliverShortage = computed(() =>
  Math.max(0, (currentOrder.value?.dirtyQty || 0) - (deliverForm.cleanQty || 0))
)

function money(value) {
  return Number(value || 0).toFixed(2)
}

function statusName(status) {
  return { PICKED: '已收待送', DELIVERED: '已送达', CLOSED: '已结算' }[status] || status
}

function statusType(status) {
  return { PICKED: 'warning', DELIVERED: 'success', CLOSED: 'info' }[status] || 'info'
}

async function load() {
  const params = {}
  if (filters.status) params.status = filters.status
  if (filters.driver) params.driver = filters.driver
  if (filters.shortageOnly) params.shortageOnly = true
  const [list, sum] = await Promise.all([orderApi.list(params), orderApi.todaySummary()])
  orders.value = list
  today.value = sum
}

function openDeliver(row) {
  currentOrder.value = row
  deliverForm.cleanQty = row.dirtyQty
  deliverForm.compensation = 0
  deliverForm.driver = row.driver || ''
  deliverForm.plate = row.plate || ''
  deliverForm.remark = ''
  deliverVisible.value = true
}

async function submitDeliver() {
  submitting.value = true
  try {
    await orderApi.deliver(currentOrder.value.id, { ...deliverForm })
    ElMessage.success('送净已登记')
    deliverVisible.value = false
    await load()
    emit('kpi-change')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>
