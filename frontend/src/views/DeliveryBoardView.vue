<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">配送调度看板</span>
        <el-date-picker
          v-model="day"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled-date="disableFuture"
          style="width: 160px"
          @change="load"
        />
        <el-button-group>
          <el-button size="small" @click="jump(-1)">前一天</el-button>
          <el-button size="small" @click="goToday">今天</el-button>
          <el-button size="small" @click="jump(1)">后一天</el-button>
        </el-button-group>
        <el-button type="primary" @click="load">刷新</el-button>
        <span class="ll-sub">
          {{ dayLabel }}口径：收脏按收脏时间、送净及短少赔付按送净时间；当日收当日送只算一单
        </span>
      </div>
    </div>

    <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px;">
      <div v-for="(row, index) in board" :key="row.driver + '|' + row.plate" class="ll-driver-card">
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span class="ll-driver-name">{{ row.driver }}</span>
          <span class="ll-plate">{{ row.plate }}</span>
        </div>
        <div class="ll-sub" style="margin: 6px 0 10px;">服务片区：{{ row.districts || '—' }}</div>

        <div style="display: flex; gap: 16px; margin-bottom: 8px; flex-wrap: wrap;">
          <div>
            <div class="ll-sub">收脏单 / 送净单</div>
            <div style="font-size: 20px; font-weight: 700; color: #006064;">
              {{ row.pickupOrderCount || 0 }} / {{ row.deliverOrderCount || 0 }}
            </div>
          </div>
          <div>
            <div class="ll-sub">收脏</div>
            <div style="font-size: 20px; font-weight: 700;">{{ row.dirtyQty }}</div>
          </div>
          <div>
            <div class="ll-sub">送净</div>
            <div style="font-size: 20px; font-weight: 700; color: #2f9e69;">{{ row.cleanQty }}</div>
          </div>
          <div>
            <div class="ll-sub">短少</div>
            <div style="font-size: 20px; font-weight: 700;" :style="{ color: row.shortageQty > 0 ? '#d9534f' : '#5a6b6d' }">
              {{ row.shortageQty }}
            </div>
          </div>
          <div>
            <div class="ll-sub">赔付</div>
            <div style="font-size: 20px; font-weight: 700; color: #d98b1f;">¥{{ money(row.compensation) }}</div>
          </div>
        </div>

        <el-progress
          :percentage="deliverRate(row)"
          :stroke-width="12"
          :color="index % 2 === 0 ? '#006064' : '#2c8487'"
          striped
          :text-inside="true"
        />
        <div class="ll-sub" style="margin-top: 4px;">送达率 {{ deliverRate(row) }}%（当日送净件 / 当日收脏件，跨日单各自归日）</div>
      </div>
    </div>

    <div v-if="!board.length" class="ll-panel" style="padding: 24px; text-align: center;" >
      <span class="ll-sub">该日暂无收送数据</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { orderApi } from '../api'

const board = ref([])
const day = ref(formatDay(new Date()))

const dayLabel = computed(() => (day.value === formatDay(new Date()) ? '今日' : day.value))

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDay(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function shiftDay(dateText, delta) {
  const [y, m, d] = dateText.split('-').map(Number)
  return formatDay(new Date(y, m - 1, d + delta))
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

function deliverRate(row) {
  if (!row.dirtyQty) return 0
  return Math.round((row.cleanQty / row.dirtyQty) * 100)
}

function disableFuture(date) {
  // date-picker 传入的是当天 00:00，今天本身不禁用
  return date.getTime() > new Date().setHours(0, 0, 0, 0)
}

function jump(delta) {
  day.value = shiftDay(day.value, delta)
  load()
}

function goToday() {
  day.value = formatDay(new Date())
  load()
}

async function load() {
  board.value = await orderApi.dispatchBoard(day.value)
}

onMounted(load)
</script>
