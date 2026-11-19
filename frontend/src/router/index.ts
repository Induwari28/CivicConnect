import { createRouter, createWebHistory } from 'vue-router'
import IssueFeedView from '@/views/IssueFeedView.vue'
import SmartMapView from '@/views/SmartMapView.vue'
import AnalyticsView from '@/views/AnalyticsView.vue'
import ServicesView from '@/views/ServicesView.vue'
import AdminDashboardView from '@/views/AdminDashboardView.vue'
import MyReportsView from '@/views/MyReportsView.vue'

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
      component: SmartMapView
    },
    {
      path: '/analytics',
      name: 'analytics',
      component: AnalyticsView
    },
    {
      path: '/services',
      name: 'services',
      component: ServicesView
    },
    {
      path: '/my-reports',
      name: 'my-reports',
      component: MyReportsView
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminDashboardView
    }
  ]
})

export default router