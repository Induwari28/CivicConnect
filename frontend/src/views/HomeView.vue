<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import type { Issue } from '@/types/issue'

const API_BASE_URL = 'http://localhost:8081'
const issues = ref<Issue[]>([])
const loading = ref(true)

const totalReports = computed(() => issues.value.length)
const resolvedReports = computed(() => issues.value.filter(i => i.status === 'RESOLVED').length)
const highPriorityReports = computed(() =>
  issues.value.filter(i => (i.priority || '').toUpperCase() === 'HIGH').length
)
const recentIssues = computed(() => issues.value.slice().reverse().slice(0, 3))

const quickModules = [
  {
    title: 'Community Issue Feed',
    description: 'Browse reports, filter by category or Moratuwa area, confirm hazards, and submit new issues.',
    to: '/feed',
    cta: 'Open Feed'
  },
  {
    title: 'Geospatial Intelligence Map',
    description: 'Inspect Moratuwa pins, 50m PostGIS clustering zones, and click coordinates to target new reports.',
    to: '/map',
    cta: 'Open Map'
  },
  {
    title: 'Analytics & Triage Insights',
    description: 'View category distribution, severity breakdowns, top-confirmed hotspots, and CSV exports.',
    to: '/analytics',
    cta: 'View Analytics'
  },
  {
    title: 'Municipal Services',
    description: 'Check council departments, SLA response targets, hotlines, and category-specific issues.',
    to: '/services',
    cta: 'Browse Services'
  },
  {
    title: 'My Tracked Reports',
    description: 'Monitor the live status of issues submitted or confirmed from your browser.',
    to: '/my-reports',
    cta: 'Track Reports'
  },
  {
    title: 'Authority Triage Portal',
    description: 'Sign in with admin credentials to update report statuses or remove test entries.',
    to: '/admin',
    cta: 'Admin Sign In'
  }
]

onMounted(async () => {
  try {
    const res = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = res.data
  } catch (err) {
    console.error('Failed to load home summary:', err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-8">
    <!-- Hero Section -->
    <div class="bg-gradient-to-r from-blue-900 via-indigo-900 to-emerald-800 rounded-3xl p-8 md:p-12 text-white shadow-2xl">
      <span class="px-3 py-1 bg-emerald-500/20 border border-emerald-400/30 text-emerald-300 rounded-full text-xs font-bold tracking-wide uppercase mb-4 inline-block">
        Moratuwa Municipal Council · Smart City Portal
      </span>
      <h1 class="text-3xl md:text-5xl font-extrabold tracking-tight mb-4">
        Welcome to CivicConnect
      </h1>
      <p class="text-blue-100 text-sm md:text-base max-w-2xl leading-relaxed mb-6">
        Report local infrastructure issues, confirm neighborhood hazards, visualize 50m spatial clusters across Moratuwa, and track municipal resolution progress in real time.
      </p>
      <div class="flex flex-wrap gap-3">
        <RouterLink
          to="/feed"
          class="bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold px-6 py-3 rounded-2xl shadow-lg transition-all text-sm"
        >
          Go to Issue Feed
        </RouterLink>
        <RouterLink
          to="/map"
          class="bg-white/10 hover:bg-white/20 text-white font-semibold px-6 py-3 rounded-2xl border border-white/20 transition-all text-sm"
        >
          Explore Smart Map
        </RouterLink>
      </div>
    </div>

    <!-- Live City Snapshot -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <div class="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs">
        <div class="text-xs font-bold text-gray-400 uppercase">Active Logged Reports</div>
        <div class="text-3xl font-extrabold text-blue-700 mt-1">{{ loading ? '...' : totalReports }}</div>
      </div>
      <div class="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs">
        <div class="text-xs font-bold text-rose-600 uppercase">High Priority</div>
        <div class="text-3xl font-extrabold text-rose-700 mt-1">{{ loading ? '...' : highPriorityReports }}</div>
      </div>
      <div class="bg-white p-6 rounded-2xl border border-gray-100 shadow-xs">
        <div class="text-xs font-bold text-emerald-600 uppercase">Resolved</div>
        <div class="text-3xl font-extrabold text-emerald-700 mt-1">{{ loading ? '...' : resolvedReports }}</div>
      </div>
    </div>

    <!-- Platform Modules Grid -->
    <div class="space-y-4">
      <h2 class="text-xl font-extrabold text-gray-900">Platform Modules</h2>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        <div
          v-for="mod in quickModules"
          :key="mod.to"
          class="bg-white p-6 rounded-3xl border border-gray-100 shadow-xs hover:shadow-md transition-all flex flex-col justify-between gap-4"
        >
          <div>
            <h3 class="text-lg font-bold text-gray-900 mb-1.5">{{ mod.title }}</h3>
            <p class="text-xs text-gray-600 leading-relaxed">{{ mod.description }}</p>
          </div>
          <RouterLink
            :to="mod.to"
            class="inline-flex items-center justify-center px-4 py-2 rounded-xl bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold text-xs transition-all self-start"
          >
            {{ mod.cta }} →
          </RouterLink>
        </div>
      </div>
    </div>

    <!-- Latest Reports Preview -->
    <div class="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm space-y-4">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-extrabold text-gray-900">Latest Community Reports</h2>
        <RouterLink to="/feed" class="text-xs font-bold text-blue-600 hover:text-blue-800">
          See All →
        </RouterLink>
      </div>

      <div v-if="loading" class="text-sm text-gray-400 py-4">Loading recent reports...</div>
      <div v-else-if="recentIssues.length === 0" class="text-sm text-gray-400 py-4">
        No reports submitted yet.
      </div>
      <div v-else class="divide-y divide-gray-100">
        <div
          v-for="issue in recentIssues"
          :key="issue.id"
          class="py-3 flex items-center justify-between gap-4 flex-wrap"
        >
          <div>
            <div class="text-sm font-bold text-gray-900">#{{ issue.id }} · {{ issue.title }}</div>
            <div class="text-xs text-gray-500 line-clamp-1">{{ issue.description }}</div>
          </div>
          <span class="px-2.5 py-1 text-xs font-bold rounded-lg bg-amber-100 text-amber-800">
            {{ issue.status }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>