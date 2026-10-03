<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">配送调度看板</span>
        <el-date-picker
          v-model="date"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :disabled-date="disabledFuture"
          style="width: 150px"
          @change="load"
        />
        <el-button type="primary" @click="load">刷新</el-button>
        <span class="ll-sub">
          {{ date }} 口径：收脏按收脏时间，送净 / 短少 / 赔付按送净时间；无司机或车牌的单归入「未指派 / 无车牌」
        </span>
      </div>
    </div>

    <div class="ll-panel" style="padding: 10px 14px; margin-bottom: 12px;">
      <span class="ll-sub">当日合计：</span>
      <span class="ll-sub">收脏 {{ total.dirtyQty || 0 }} 件</span> ·
      <span class="ll-sub">送净 {{ total.cleanQty || 0 }} 件</span> ·
      <span class="ll-sub">短少 {{ total.shortageQty || 0 }} 件</span> ·
      <span class="ll-sub">赔付 ¥{{ money(total.compensation) }}</span> ·
      <span class="ll-sub">涉及 {{ total.orderCount || 0 }} 单（收脏 {{ total.pickupOrderCount || 0 }} / 送净 {{ total.deliverOrderCount || 0 }}，当日收当日送不重复计）</span>
    </div>

    <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px;">
      <div v-for="(row, index) in board" :key="index" class="ll-driver-card">
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span class="ll-driver-name">{{ row.driver }}</span>
          <span class="ll-plate">{{ row.plate }}</span>
        </div>
        <div class="ll-sub" style="margin: 6px 0 10px;">服务片区：{{ row.districts || '—' }}</div>

        <div style="display: flex; gap: 16px; margin-bottom: 8px;">
          <div>
            <div class="ll-sub">收送单量</div>
            <div style="font-size: 20px; font-weight: 700; color: #006064;">{{ row.orderCount }}</div>
            <div class="ll-sub">收 {{ row.pickupOrderCount || 0 }} / 送 {{ row.deliverOrderCount || 0 }}</div>
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
        <div class="ll-sub" style="margin-top: 4px;">
          送达率 {{ rawDeliverRate(row) }}%（当日送净件 / 当日收脏件；跨日可能低于或高于 100%）
        </div>
      </div>
    </div>

    <div v-if="!board.length" class="ll-panel" style="padding: 24px; text-align: center;" >
      <span class="ll-sub">当日暂无收送数据</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { orderApi } from '../api'

const board = ref([])
const date = ref(today())

function today() {
  const now = new Date()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${m}-${d}`
}

// 不允许选未来日期：当天还没发生的收送没有统计意义
function disabledFuture(d) {
  return d.getTime() > Date.now()
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

// 跨日口径下当日送净可能来自往日收脏，比率允许超过 100%；进度条展示截断到 0~100
function rawDeliverRate(row) {
  if (!row.dirtyQty) return row.cleanQty ? 100 : 0
  return Math.round((row.cleanQty / row.dirtyQty) * 100)
}

function deliverRate(row) {
  return Math.min(100, Math.max(0, rawDeliverRate(row)))
}

const total = computed(() =>
  board.value.reduce(
    (acc, row) => ({
      orderCount: acc.orderCount + (row.orderCount || 0),
      pickupOrderCount: acc.pickupOrderCount + (row.pickupOrderCount || 0),
      deliverOrderCount: acc.deliverOrderCount + (row.deliverOrderCount || 0),
      dirtyQty: acc.dirtyQty + (row.dirtyQty || 0),
      cleanQty: acc.cleanQty + (row.cleanQty || 0),
      shortageQty: acc.shortageQty + (row.shortageQty || 0),
      compensation: Number((acc.compensation + Number(row.compensation || 0)).toFixed(2))
    }),
    { orderCount: 0, pickupOrderCount: 0, deliverOrderCount: 0, dirtyQty: 0, cleanQty: 0, shortageQty: 0, compensation: 0 }
  )
)

async function load() {
  board.value = await orderApi.dispatchBoard(date.value)
}

onMounted(load)
</script>
