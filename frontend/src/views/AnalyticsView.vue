<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import type { Issue } from '@/types/issue'

const API_BASE_URL = 'http://localhost:8081'
const issues = ref<Issue[]>([])
const loading = ref(true)

const categories = ['Roads & Drainage', 'Streetlights & Power', 'Waste & Sanitation', 'Public Safety']
const priorities = ['HIGH', 'MEDIUM', 'LOW', 'UNCLASSIFIED']

const normalizePriority = (p?: string) => {
  if (!p || p.includes('ERROR')) return 'UNCLASSIFIED'
  return p.toUpperCase()
}

const totalCount = computed(() => issues.value.length)
const resolvedCount = computed(() => issues.value.filter(i => i.status === 'RESOLVED').length)
const inProgressCount = computed(() => issues.value.filter(i => i.status === 'IN_PROGRESS').length)
const pendingCount = computed(() => issues.value.filter(i => i.status === 'PENDING').length)
const highPriorityCount = computed(() => issues.value.filter(i => normalizePriority(i.priority) === 'HIGH').length)
const totalConfirmations = computed(() =>
  issues.value.reduce((sum, item) => sum + (item.reportCount || 1), 0)
)
const resolutionRate = computed(() =>
  totalCount.value > 0 ? Math.round((resolvedCount.value / totalCount.value) * 100) : 0
)

const categoryBreakdown = computed(() => {
  let matchedTotal = 0
  const rows = categories.map(cat => {
    const count = issues.value.filter(i =>
      i.title.toLowerCase().includes(cat.toLowerCase()) ||
      i.description.toLowerCase().includes(cat.toLowerCase())
    ).length
    matchedTotal += count
    const pct = totalCount.value > 0 ? Math.min(100, Math.round((count / totalCount.value) * 100)) : 0
    return { category: cat, count, pct, barClass: 'bg-blue-600' }
  })

  const otherCount = Math.max(0, totalCount.value - matchedTotal)
  const otherPct = totalCount.value > 0 ? Math.min(100, Math.round((otherCount / totalCount.value) * 100)) : 0
  rows.push({
    category: 'General / Uncategorized',
    count: otherCount,
    pct: otherPct,
    barClass: 'bg-slate-400'
  })

  return rows
})

const priorityBreakdown = computed(() => {
  const colorMap: Record<string, string> = {
    HIGH: 'bg-rose-600',
    MEDIUM: 'bg-amber-500',
    LOW: 'bg-blue-500',
    UNCLASSIFIED: 'bg-gray-400'
  }

  return priorities.map(p => {
    const count = issues.value.filter(i => normalizePriority(i.priority) === p).length
    const pct = totalCount.value > 0 ? Math.round((count / totalCount.value) * 100) : 0
    return { priority: p, count, pct, barClass: colorMap[p] || 'bg-gray-500' }
  })
})

const topHotspots = computed(() => {
  return issues.value
    .slice()
    .sort((a, b) => (b.reportCount || 1) - (a.reportCount || 1))
    .slice(0, 5)
})

const exportCsv = () => {
  const headers = ['ID', 'Title', 'Priority', 'Status', 'ReportCount', 'Latitude', 'Longitude']
  const lines = issues.value.map(item => [
    item.id,
    `"${(item.title || '').replace(/"/g, '""')}"`,
    normalizePriority(item.priority),
    item.status,
    item.reportCount || 1,
    item.latitude ?? '',
    item.longitude ?? ''
  ].join(','))

  const csvContent = [headers.join(','), ...lines].join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', 'civicconnect-analytics.csv')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  try {
    const res = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = res.data
  } catch (err) {
    console.error('Failed to load analytics:', err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header Banner -->
    <div class="bg-gradient-to-r from-slate-900 via-blue-900 to-indigo-900 rounded-3xl p-8 text-white shadow-xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <span class="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase bg-blue-500/20 text-blue-300 border border-blue-400/30 inline-block mb-2">
          Moratuwa Triage Telemetry
        </span>
        <h1 class="text-3xl font-extrabold tracking-tight">Civic Analytics & Triage Insights</h1>
        <p class="text-blue-100 text-sm md:text-base mt-1">
          Live breakdown across categories, AI priority levels, workflow resolution, and community confirmations.
        </p>
      </div>

      <button
        type="button"
        @click="exportCsv"
        :disabled="issues.length === 0"
        class="px-5 py-2.5 rounded-2xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs shadow-md transition-all disabled:opacity-50"
      >
        Export CSV Report
      </button>
    </div>

    <div v-if="loading" class="bg-white p-10 rounded-3xl text-center text-gray-400 border border-gray-100">
      Loading telemetry data...
    </div>

    <template v-else>
      <!-- KPI Cards -->
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
          <div class="text-xs font-bold text-gray-400 uppercase">Total Issues</div>
          <div class="text-3xl font-extrabold text-gray-900 mt-1">{{ totalCount }}</div>
          <div class="text-[11px] text-gray-500 mt-1">
            {{ pendingCount }} pending · {{ inProgressCount }} active
          </div>
        </div>
        <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
          <div class="text-xs font-bold text-rose-600 uppercase">High Priority</div>
          <div class="text-3xl font-extrabold text-rose-700 mt-1">{{ highPriorityCount }}</div>
          <div class="text-[11px] text-gray-500 mt-1">Escalated or critical severity</div>
        </div>
        <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
          <div class="text-xs font-bold text-indigo-600 uppercase">Total Confirmations</div>
          <div class="text-3xl font-extrabold text-indigo-700 mt-1">{{ totalConfirmations }}</div>
          <div class="text-[11px] text-gray-500 mt-1">Crowdsourced citizen backing</div>
        </div>
        <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
          <div class="text-xs font-bold text-emerald-600 uppercase">Resolved</div>
          <div class="text-3xl font-extrabold text-emerald-700 mt-1">{{ resolvedCount }}</div>
          <div class="text-[11px] text-emerald-700 font-semibold mt-1">{{ resolutionRate }}% resolution rate</div>
        </div>
      </div>

      <!-- Category & Priority Charts -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div class="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm space-y-5">
          <h2 class="text-lg font-extrabold text-gray-900">Distribution by Category</h2>
          <div v-for="row in categoryBreakdown" :key="row.category" class="space-y-1.5">
            <div class="flex justify-between text-sm font-semibold text-gray-700">
              <span>{{ row.category }}</span>
              <span>{{ row.count }} ({{ row.pct }}%)</span>
            </div>
            <div class="w-full h-2.5 bg-gray-100 rounded-full overflow-hidden">
              <div class="h-full rounded-full transition-all" :class="row.barClass" :style="{ width: `${row.pct}%` }"></div>
            </div>
          </div>
        </div>

        <div class="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm space-y-5">
          <h2 class="text-lg font-extrabold text-gray-900">Severity Breakdown</h2>
          <div v-for="row in priorityBreakdown" :key="row.priority" class="space-y-1.5">
            <div class="flex justify-between text-sm font-semibold text-gray-700">
              <span>{{ row.priority }}</span>
              <span>{{ row.count }} ({{ row.pct }}%)</span>
            </div>
            <div class="w-full h-2.5 bg-gray-100 rounded-full overflow-hidden">
              <div class="h-full rounded-full transition-all" :class="row.barClass" :style="{ width: `${row.pct}%` }"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Top Confirmed Hotspots -->
      <div class="bg-white p-6 rounded-3xl border border-gray-100 shadow-sm space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-extrabold text-gray-900">Top Confirmed Hotspots</h2>
          <router-link to="/map" class="text-xs font-bold text-blue-600 hover:text-blue-800">
            Open Smart Map →
          </router-link>
        </div>

        <div class="divide-y divide-gray-100">
          <div
            v-for="item in topHotspots"
            :key="item.id"
            class="py-3.5 flex items-center justify-between gap-4 flex-wrap"
          >
            <div>
              <div class="text-sm font-bold text-gray-900">#{{ item.id }} · {{ item.title }}</div>
              <div class="text-xs text-gray-500">
                Priority: <strong>{{ normalizePriority(item.priority) }}</strong> · Status: <strong>{{ item.status }}</strong>
              </div>
            </div>
            <span class="px-3 py-1 rounded-xl text-xs font-extrabold bg-indigo-50 text-indigo-700 border border-indigo-200">
              {{ item.reportCount || 1 }} Confirmation(s)
            </span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>