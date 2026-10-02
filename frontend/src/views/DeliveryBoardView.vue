<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">配送调度看板</span>
        <el-button type="primary" @click="load">刷新</el-button>
        <span class="ll-sub">按司机 / 车牌聚合收送量，短少件数与赔付金额一并跟踪</span>
      </div>
    </div>

    <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 12px;">
      <div v-for="(row, index) in board" :key="row.driver + row.plate" class="ll-driver-card">
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span class="ll-driver-name">{{ row.driver }}</span>
          <span class="ll-plate">{{ row.plate }}</span>
        </div>
        <div class="ll-sub" style="margin: 6px 0 10px;">服务片区：{{ row.districts || '—' }}</div>

        <div style="display: flex; gap: 16px; margin-bottom: 8px;">
          <div>
            <div class="ll-sub">收送单量</div>
            <div style="font-size: 20px; font-weight: 700; color: #006064;">{{ row.orderCount }}</div>
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
        <div class="ll-sub" style="margin-top: 4px;">送达率 {{ deliverRate(row) }}%（净件 / 脏件）</div>
      </div>
    </div>

    <div v-if="!board.length" class="ll-panel" style="padding: 24px; text-align: center;" >
      <span class="ll-sub">暂无配送数据</span>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { orderApi } from '../api'

const board = ref([])

function money(value) {
  return Number(value || 0).toFixed(2)
}

function deliverRate(row) {
  if (!row.dirtyQty) return 0
  return Math.round((row.cleanQty / row.dirtyQty) * 100)
}

async function load() {
  board.value = await orderApi.dispatchBoard()
}

onMounted(load)
</script>
