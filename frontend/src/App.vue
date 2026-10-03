<template>
  <div class="ll-app">
    <!-- 顶部品牌条 + KPI -->
    <header class="ll-brand">
      <div class="ll-brand-left">
        <span class="ll-brand-title">布草洗涤厂 · 收送与洗涤批次管理</span>
        <span class="ll-brand-sub">客户网点 → 收送单 → 洗涤批次 → 工序流转</span>
      </div>
      <div class="ll-kpi-strip" v-loading="loadingKpi">
        <div class="ll-kpi">
          <div class="ll-kpi-value">{{ kpi.outletCount || 0 }}</div>
          <div class="ll-kpi-label">合作网点 / 预警 {{ kpi.warningOutletCount || 0 }}</div>
        </div>
        <div class="ll-kpi">
          <div class="ll-kpi-value">{{ kpi.todayDirty || 0 }}</div>
          <div class="ll-kpi-label">今日收脏（件）</div>
        </div>
        <div class="ll-kpi">
          <div class="ll-kpi-value">{{ kpi.todayClean || 0 }}</div>
          <div class="ll-kpi-label">今日送净（件）</div>
        </div>
        <div class="ll-kpi">
          <div class="ll-kpi-value">{{ kpi.pendingDirty || 0 }}</div>
          <div class="ll-kpi-label">待收脏布草（件）</div>
        </div>
        <div class="ll-kpi">
          <div class="ll-kpi-value">{{ kpi.runningBatch || 0 }}</div>
          <div class="ll-kpi-label">在产批次</div>
        </div>
        <div class="ll-kpi" :class="{ 'is-alert': kpi.quarantinedBatch > 0 }">
          <div class="ll-kpi-value">{{ kpi.quarantinedBatch || 0 }}</div>
          <div class="ll-kpi-label">污染隔离批次 / {{ kpi.activeContaminationCount || 0 }} 事件</div>
        </div>
        <div class="ll-kpi">
          <div class="ll-kpi-value">{{ kpi.faultMachineCount || 0 }}</div>
          <div class="ll-kpi-label">故障设备 / 共 {{ kpi.machineCount || 0 }} 台</div>
        </div>
        <div class="ll-kpi">
          <div class="ll-kpi-value">{{ kpi.shortageCategoryCount || 0 }}</div>
          <div class="ll-kpi-label">周转缺口品类</div>
        </div>
        <div class="ll-kpi">
          <div class="ll-kpi-value">¥{{ money(kpi.compensationTotal) }}</div>
          <div class="ll-kpi-label">今日短少赔付（元）</div>
        </div>
      </div>
    </header>

    <!-- 上排：车间工序分组 -->
    <nav class="ll-tabs-row">
      <div
        v-for="group in groups"
        :key="group.key"
        class="ll-group-tab"
        :class="{ 'is-active': group.key === activeGroup }"
        @click="go(group.children[0].path)"
      >
        <div class="ll-group-name">{{ group.name }}</div>
        <div class="ll-group-desc">{{ group.desc }}</div>
      </div>
    </nav>

    <!-- 下排：当前分组下的子功能 -->
    <nav class="ll-sub-row">
      <span class="ll-sub-arrow">{{ activeGroupName }} ›</span>
      <div
        v-for="child in activeChildren"
        :key="child.path"
        class="ll-sub-tab"
        :class="{ 'is-active': child.path === route.path }"
        @click="go(child.path)"
      >
        {{ child.name }}
      </div>
    </nav>

    <main>
      <router-view v-slot="{ Component }">
        <keep-alive>
          <component :is="Component" @kpi-change="loadKpi" />
        </keep-alive>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dashboardApi } from './api'

const route = useRoute()
const router = useRouter()

const groups = [
  {
    key: 'pickup',
    name: '收送',
    desc: '网点收脏 / 送净调度',
    children: [
      { name: '网点地图热区', path: '/outlets' },
      { name: '收送单台账', path: '/orders' }
    ]
  },
  {
    key: 'sorting',
    name: '分拣',
    desc: '品类分笼 / 在库盘点',
    children: [{ name: '布草品类盘点', path: '/categories' }]
  },
  {
    key: 'washing',
    name: '洗涤',
    desc: '批次排产 / 工序流转 / 医感追溯',
    children: [
      { name: '批次隧道流转', path: '/batches' },
      { name: '洗涤配方版本台', path: '/recipes' },
      { name: '医院布草污染追溯', path: '/contamination' }
    ]
  },
  {
    key: 'finishing',
    name: '后整理',
    desc: '烘干烫平 / 设备保障',
    children: [{ name: '设备状态灯墙', path: '/machines' }]
  },
  {
    key: 'delivery',
    name: '配送',
    desc: '司机车辆 / 送达签收',
    children: [{ name: '配送调度看板', path: '/delivery' }]
  }
]

const kpi = ref({})
const loadingKpi = ref(false)

const activeGroup = computed(() => route.meta.group || 'pickup')
const activeGroupName = computed(() => {
  const g = groups.find((item) => item.key === activeGroup.value)
  return g ? g.name : ''
})
const activeChildren = computed(() => {
  const g = groups.find((item) => item.key === activeGroup.value)
  return g ? g.children : []
})

function go(path) {
  if (route.path !== path) {
    router.push(path)
  }
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

async function loadKpi() {
  loadingKpi.value = true
  try {
    kpi.value = await dashboardApi.overview()
  } catch (e) {
    kpi.value = {}
  } finally {
    loadingKpi.value = false
  }
}

onMounted(loadKpi)
</script>
