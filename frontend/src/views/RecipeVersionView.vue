<template>
  <div class="ll-page">
    <div class="ll-panel" style="padding: 12px 14px; margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; gap: 10px; align-items: center;">
        <span class="ll-section-title">洗涤配方版本台</span>
        <el-select
          v-model="categoryId"
          placeholder="选择布草品类"
          style="width: 230px"
          @change="load"
        >
          <el-option
            v-for="c in categories"
            :key="c.id"
            :label="c.name + '（' + c.spec + '）'"
            :value="c.id"
          />
        </el-select>
        <el-button type="primary" :disabled="!categoryId" @click="createDraft">新建草稿</el-button>
        <el-button @click="load">刷新</el-button>
        <span class="ll-sub">
          草稿可反复调整；发布前校验八道工序完整、参数合法、至少一台可用设备可承接；
          发布后不可改写，只能复制生成新版本；每个品类同时只有一个当前版本
        </span>
      </div>
    </div>

    <div class="ll-panel" style="padding: 8px 14px 14px;">
      <el-table :data="versions" v-loading="loading" size="small" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 6px 30px 12px;">
              <el-table :data="row.steps" size="small" border>
                <el-table-column label="工序" width="100">
                  <template #default="{ row: s }">{{ s.stepOrder }}. {{ s.stepName }}</template>
                </el-table-column>
                <el-table-column label="目标水温" width="130">
                  <template #default="{ row: s }">{{ s.targetTemp }}℃ ±{{ s.tempTolerance }}</template>
                </el-table-column>
                <el-table-column label="洗涤剂用量" width="150">
                  <template #default="{ row: s }">{{ s.targetDetergentMl }}ml ±{{ s.detergentTolerance }}</template>
                </el-table-column>
                <el-table-column label="适用设备类型">
                  <template #default="{ row: s }">{{ typeNames(s.machineTypes) || '不占设备' }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="96">
          <template #default="{ row }">
            V{{ row.versionNo }}
            <el-tag v-if="row.status === 'PUBLISHED'" type="success" size="small" effect="dark">当前</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可承接设备类型" width="130">
          <template #default="{ row }">{{ typeNames(row.machineTypes) }}</template>
        </el-table-column>
        <el-table-column label="最大装载" width="86">
          <template #default="{ row }">{{ row.maxLoadRatio }}%</template>
        </el-table-column>
        <el-table-column label="基线" width="70">
          <template #default="{ row }">{{ row.baseVersionId ? 'V' + versionNoOf(row.baseVersionId) : '—' }}</template>
        </el-table-column>
        <el-table-column label="创建" width="170">
          <template #default="{ row }">{{ row.createdBy || '—' }} · {{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="发布 / 停用" width="200">
          <template #default="{ row }">
            <div v-if="row.publishedBy">{{ row.publishedBy }} · {{ fmt(row.publishedAt) }}</div>
            <div v-if="row.disabledBy" class="ll-sub">停用于 {{ fmt(row.disabledAt) }}</div>
            <span v-if="!row.publishedBy && !row.disabledBy">—</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'DRAFT'">
              <el-button size="small" type="primary" link @click="openEditor(row)">编辑</el-button>
              <el-button size="small" type="success" link @click="publish(row)">发布</el-button>
              <el-button size="small" type="danger" link @click="removeDraft(row)">删除</el-button>
            </template>
            <template v-else>
              <el-button size="small" type="primary" link @click="copy(row)">复制为新草稿</el-button>
              <el-button
                v-if="row.status === 'PUBLISHED'"
                size="small"
                type="danger"
                link
                @click="openDisable(row)"
              >
                停用
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && !versions.length"
        description="该品类还没有配方版本，点击「新建草稿」开始维护"
        :image-size="60"
      />
    </div>

    <!-- 草稿编辑 -->
    <el-dialog
      v-model="editorVisible"
      :title="`编辑草稿 V${editor.versionNo}（${categoryName}）`"
      width="900px"
      top="4vh"
    >
      <el-form label-width="110px" size="small" style="margin-bottom: 4px;">
        <div style="display: flex; flex-wrap: wrap; gap: 4px 18px;">
          <el-form-item label="可承接设备类型">
            <el-checkbox-group v-model="editor.machineTypes">
              <el-checkbox v-for="t in machineTypeOptions" :key="t.value" :value="t.value">
                {{ t.label }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="最大装载比例">
            <el-input-number v-model="editor.maxLoadRatio" :min="1" :max="100" size="small" />
            <span class="ll-sub" style="margin-left: 6px;">%（批次重量 ≤ 设备容量 × 比例）</span>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="editor.remark" style="width: 220px" />
          </el-form-item>
        </div>
      </el-form>
      <el-table :data="editor.steps" size="small" border>
        <el-table-column label="工序" width="76">
          <template #default="{ row }">{{ row.stepOrder }}. {{ row.stepName }}</template>
        </el-table-column>
        <el-table-column label="目标水温℃" width="128">
          <template #default="{ row }">
            <el-input-number v-model="row.targetTemp" :min="0" :max="180" size="small" :controls="false" style="width: 96px" />
          </template>
        </el-table-column>
        <el-table-column label="容差±℃" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.tempTolerance" :min="0" :max="30" size="small" :controls="false" style="width: 80px" />
          </template>
        </el-table-column>
        <el-table-column label="洗涤剂ml" width="128">
          <template #default="{ row }">
            <el-input-number v-model="row.targetDetergentMl" :min="0" :max="5000" :step="50" size="small" :controls="false" style="width: 96px" />
          </template>
        </el-table-column>
        <el-table-column label="容差±ml" width="110">
          <template #default="{ row }">
            <el-input-number v-model="row.detergentTolerance" :min="0" :max="500" :step="10" size="small" :controls="false" style="width: 80px" />
          </template>
        </el-table-column>
        <el-table-column label="适用设备类型" min-width="170">
          <template #default="{ row }">
            <el-select
              v-model="row.machineTypeList"
              multiple
              collapse-tags
              size="small"
              placeholder="不占设备"
              style="width: 100%"
            >
              <el-option v-for="t in machineTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <div class="ll-sub" style="margin-top: 8px;">
        保存仅更新草稿；发布时后端重新校验八道工序完整、参数范围与可用设备。
        两人同时发布不同草稿时只有一份生效，另一份返回明确冲突。
      </div>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 停用当前版本 -->
    <el-dialog
      v-model="disableVisible"
      :title="`停用当前版本 V${disableTarget ? disableTarget.versionNo : ''}`"
      width="500px"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="停用当前版本前必须先选定接替版本"
        description="接替版本将立即成为该品类当前生效版本；在产批次仍按各自批次快照执行，不受本次停用影响。"
        style="margin-bottom: 12px;"
      />
      <el-form label-width="90px" size="small">
        <el-form-item label="接替版本" required>
          <el-select v-model="disableForm.successorVersionId" placeholder="选择接替版本" style="width: 100%">
            <el-option
              v-for="v in successorCandidates"
              :key="v.id"
              :label="`V${v.versionNo}（${v.statusName}）`"
              :value="v.id"
            />
          </el-select>
          <div class="ll-sub">草稿接替会先通过发布校验；已停用版本接替即重新启用</div>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="disableForm.operator" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disableVisible = false">取消</el-button>
        <el-button type="danger" :loading="disabling" @click="submitDisable">停用并切换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, recipeApi } from '../api'

const emit = defineEmits(['kpi-change'])

const machineTypeOptions = [
  { value: 'WASHER', label: '水洗机' },
  { value: 'DRYER', label: '烘干机' },
  { value: 'IRONER', label: '烫平机' }
]

const categories = ref([])
const categoryId = ref(null)
const versions = ref([])
const loading = ref(false)

const editorVisible = ref(false)
const saving = ref(false)
const editor = reactive({
  id: null,
  versionNo: null,
  machineTypes: [],
  maxLoadRatio: 90,
  remark: '',
  steps: []
})

const disableVisible = ref(false)
const disabling = ref(false)
const disableTarget = ref(null)
const disableForm = reactive({ successorVersionId: null, operator: '工艺主管' })

const categoryName = computed(() => {
  const c = categories.value.find((item) => item.id === categoryId.value)
  return c ? c.name : ''
})

const successorCandidates = computed(() =>
  versions.value.filter(
    (v) => disableTarget.value && v.id !== disableTarget.value.id && v.status !== 'PUBLISHED'
  )
)

function typeNames(types) {
  if (!types) return ''
  return types
    .split(',')
    .filter(Boolean)
    .map((t) => (machineTypeOptions.find((o) => o.value === t) || {}).label || t)
    .join('、')
}

function statusType(status) {
  return { DRAFT: 'info', PUBLISHED: 'success', DISABLED: 'warning' }[status] || 'info'
}

function versionNoOf(id) {
  const v = versions.value.find((item) => item.id === id)
  return v ? v.versionNo : '?'
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  if (!categoryId.value) return
  loading.value = true
  try {
    versions.value = await recipeApi.list({ categoryId: categoryId.value })
  } finally {
    loading.value = false
  }
}

async function createDraft() {
  const draft = await recipeApi.createDraft({ categoryId: categoryId.value, createdBy: '工艺员' })
  ElMessage.success(`已创建草稿 V${draft.versionNo}，可继续调整`)
  await load()
  openEditor(versions.value.find((v) => v.id === draft.id) || draft)
}

function openEditor(row) {
  editor.id = row.id
  editor.versionNo = row.versionNo
  editor.machineTypes = (row.machineTypes || '').split(',').filter(Boolean)
  editor.maxLoadRatio = row.maxLoadRatio
  editor.remark = row.remark || ''
  editor.steps = (row.steps || []).map((s) => ({
    stepCode: s.stepCode,
    stepName: s.stepName,
    stepOrder: s.stepOrder,
    targetTemp: s.targetTemp,
    tempTolerance: s.tempTolerance,
    targetDetergentMl: s.targetDetergentMl,
    detergentTolerance: s.detergentTolerance,
    machineTypeList: (s.machineTypes || '').split(',').filter(Boolean)
  }))
  editorVisible.value = true
}

async function saveDraft() {
  saving.value = true
  try {
    await recipeApi.saveDraft(editor.id, {
      machineTypes: editor.machineTypes,
      maxLoadRatio: editor.maxLoadRatio,
      remark: editor.remark,
      steps: editor.steps.map((s) => ({
        stepCode: s.stepCode,
        targetTemp: s.targetTemp,
        tempTolerance: s.tempTolerance,
        targetDetergentMl: s.targetDetergentMl,
        detergentTolerance: s.detergentTolerance,
        machineTypes: s.machineTypeList
      }))
    })
    ElMessage.success('草稿已保存')
    editorVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function publish(row) {
  await ElMessageBox.confirm(
    `发布前将校验：八道工序完整、参数范围合法、至少一台可用设备可承接。发布后 V${row.versionNo} 成为当前版本且不可再改写，确认发布？`,
    '发布配方版本',
    { type: 'warning', confirmButtonText: '校验并发布', cancelButtonText: '再想想' }
  )
  await recipeApi.publish(row.id, { operator: '工艺主管' })
  ElMessage.success(`V${row.versionNo} 已发布为当前版本`)
  await load()
  emit('kpi-change')
}

async function copy(row) {
  const draft = await recipeApi.copy(row.id)
  ElMessage.success(`已复制 V${row.versionNo} 生成新草稿 V${draft.versionNo}`)
  await load()
}

async function removeDraft(row) {
  await ElMessageBox.confirm(`确认删除草稿 V${row.versionNo}？删除后不可恢复。`, '删除草稿', {
    type: 'warning'
  })
  await recipeApi.removeDraft(row.id)
  ElMessage.success('草稿已删除')
  await load()
}

function openDisable(row) {
  disableTarget.value = row
  disableForm.successorVersionId = null
  disableVisible.value = true
}

async function submitDisable() {
  if (!disableForm.successorVersionId) {
    ElMessage.warning('停用当前版本前必须先选定接替版本，否则新批次无法排产')
    return
  }
  disabling.value = true
  try {
    await recipeApi.disable(disableTarget.value.id, { ...disableForm })
    ElMessage.success(`V${disableTarget.value.versionNo} 已停用，接替版本已生效`)
    disableVisible.value = false
    await load()
    emit('kpi-change')
  } finally {
    disabling.value = false
  }
}

onMounted(async () => {
  categories.value = await categoryApi.list()
  if (categories.value.length) {
    categoryId.value = categories.value[0].id
    await load()
  }
})
</script>
