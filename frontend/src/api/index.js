import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000
})

// 统一拆包：后端返回 {ok, message, data}，成功直接给 data，失败弹业务提示
http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && body.ok === false) {
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body ? body.data : null
  },
  (err) => {
    const message = err?.response?.data?.message || err.message || '请求失败'
    ElMessage.error(message)
    return Promise.reject(err)
  }
)

export const dashboardApi = {
  overview: () => http.get('/dashboard/overview'),
  logs: () => http.get('/dashboard/logs')
}

export const outletApi = {
  list: (params) => http.get('/outlets', { params }),
  summary: () => http.get('/outlets/summary'),
  detail: (id) => http.get(`/outlets/${id}`),
  orders: (id) => http.get(`/outlets/${id}/orders`),
  register: (id, payload) => http.post(`/outlets/${id}/pickup`, payload),
  create: (payload) => http.post('/outlets', payload),
  update: (id, payload) => http.put(`/outlets/${id}`, payload),
  remove: (id) => http.delete(`/outlets/${id}`)
}

export const categoryApi = {
  list: () => http.get('/categories'),
  shortage: () => http.get('/categories/shortage'),
  detail: (id) => http.get(`/categories/${id}`),
  adjustStock: (id, delta) => http.put(`/categories/${id}/stock`, null, { params: { delta } }),
  saveCount: (items) => http.post('/categories/count', items),
  create: (payload) => http.post('/categories', payload),
  remove: (id) => http.delete(`/categories/${id}`)
}

export const orderApi = {
  list: (params) => http.get('/orders', { params }),
  // 日汇总 / 配送看板统一口径：脏件按收脏日、净件/短少/赔付按送净日；可传 date=yyyy-MM-dd
  todaySummary: (date) => http.get('/orders/today-summary', { params: date ? { date } : {} }),
  dispatchBoard: (date) => http.get('/orders/dispatch-board', { params: date ? { date } : {} }),
  detail: (id) => http.get(`/orders/${id}`),
  create: (payload) => http.post('/orders', payload),
  deliver: (id, payload) => http.post(`/orders/${id}/deliver`, payload),
  remove: (id) => http.delete(`/orders/${id}`)
}

export const batchApi = {
  list: (params) => http.get('/batches', { params }),
  steps: () => http.get('/batches/steps'),
  stepStats: () => http.get('/batches/step-stats'),
  summary: () => http.get('/batches/summary'),
  detail: (id) => http.get(`/batches/${id}`),
  create: (payload) => http.post('/batches', payload),
  advance: (id, payload) => http.post(`/batches/${id}/advance`, payload || {}),
  resolveReview: (reviewId, payload) => http.post(`/batches/reviews/${reviewId}/resolve`, payload),
  remove: (id) => http.delete(`/batches/${id}`)
}

export const recipeApi = {
  list: (params) => http.get('/recipes', { params }),
  current: (categoryId) => http.get('/recipes/current', { params: { categoryId } }),
  detail: (id) => http.get(`/recipes/${id}`),
  createDraft: (payload) => http.post('/recipes/drafts', payload),
  saveDraft: (id, payload) => http.put(`/recipes/drafts/${id}`, payload),
  removeDraft: (id) => http.delete(`/recipes/drafts/${id}`),
  copy: (id) => http.post(`/recipes/${id}/copy`),
  publish: (id, payload) => http.post(`/recipes/${id}/publish`, payload || {}),
  disable: (id, payload) => http.post(`/recipes/${id}/disable`, payload)
}

export const machineApi = {
  list: () => http.get('/machines'),
  summary: () => http.get('/machines/summary'),
  detail: (id) => http.get(`/machines/${id}`),
  nextStatus: (id, payload) => http.post(`/machines/${id}/next-status`, payload || {}),
  reportFault: (id, payload) => http.post(`/machines/${id}/repairs`, payload),
  repairs: () => http.get('/machines/repairs'),
  finishRepair: (repairId) => http.post(`/machines/repairs/${repairId}/finish`),
  create: (payload) => http.post('/machines', payload),
  remove: (id) => http.delete(`/machines/${id}`)
}

export const contaminationApi = {
  types: () => http.get('/contamination/types'),
  listEvents: () => http.get('/contamination/events'),
  eventDetail: (id) => http.get(`/contamination/events/${id}`),
  createEvent: (payload) => http.post('/contamination/events', payload),
  confirmEvent: (id, payload) => http.post(`/contamination/events/${id}/confirm`, payload || {}),
  releaseEvent: (id, payload) => http.post(`/contamination/events/${id}/release`, payload || {}),
  createRewash: (eventId, isolationId, payload) =>
    http.post(`/contamination/events/${eventId}/isolations/${isolationId}/rewash`, payload || {}),
  failRewash: (rewashId, payload) => http.post(`/contamination/rewash/${rewashId}/fail`, payload || {}),
  deleteRewash: (rewashId, payload) => http.post(`/contamination/rewash/${rewashId}/delete`, payload || {})
}
