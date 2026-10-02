import { createRouter, createWebHistory } from 'vue-router'

import OutletMapView from '../views/OutletMapView.vue'
import PickupOrderView from '../views/PickupOrderView.vue'
import CategoryLedgerView from '../views/CategoryLedgerView.vue'
import BatchTunnelView from '../views/BatchTunnelView.vue'
import RecipeVersionView from '../views/RecipeVersionView.vue'
import MachineWallView from '../views/MachineWallView.vue'
import DeliveryBoardView from '../views/DeliveryBoardView.vue'
import ContaminationView from '../views/ContaminationView.vue'

const routes = [
  { path: '/', redirect: '/outlets' },
  { path: '/outlets', name: 'outlet-map', component: OutletMapView, meta: { group: 'pickup' } },
  { path: '/orders', name: 'pickup-orders', component: PickupOrderView, meta: { group: 'pickup' } },
  { path: '/categories', name: 'category-ledger', component: CategoryLedgerView, meta: { group: 'sorting' } },
  { path: '/batches', name: 'batch-tunnel', component: BatchTunnelView, meta: { group: 'washing' } },
  { path: '/recipes', name: 'recipe-versions', component: RecipeVersionView, meta: { group: 'washing' } },
  { path: '/contamination', name: 'contamination', component: ContaminationView, meta: { group: 'washing' } },
  { path: '/machines', name: 'machine-wall', component: MachineWallView, meta: { group: 'finishing' } },
  { path: '/delivery', name: 'delivery-board', component: DeliveryBoardView, meta: { group: 'delivery' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
