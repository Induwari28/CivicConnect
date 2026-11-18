import { createRouter, createWebHistory } from 'vue-router'
import IssueFeedView from '../views/IssueFeedView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'feed',
      component: IssueFeedView
    },
    {
      path: '/map',
      name: 'map',
      component: () => import('../views/SmartMapView.vue')
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('../views/AdminDashboardView.vue')
    }
  ]
})

export default router