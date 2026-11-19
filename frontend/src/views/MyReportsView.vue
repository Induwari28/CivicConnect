<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import type { Issue } from '@/types/issue'

const API_BASE_URL = 'http://localhost:8081'

const trackedIssues = ref<Issue[]>([])
const loading = ref(true)
const manualIdInput = ref<number | null>(null)
const trackMessage = ref('')

const getTrackedIds = (): number[] => {
  try {
    const raw = localStorage.getItem('civicconnect_tracked_ids')
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

const saveTrackedIds = (ids: number[]) => {
  localStorage.setItem('civicconnect_tracked_ids', JSON.stringify(ids))
}

const formatPriority = (priority?: string) => {
  if (!priority || priority.includes('ERROR')) return 'UNCLASSIFIED'
  return priority.toUpperCase()
}

const fetchTrackedIssues = async () => {
  loading.value = true
  const ids = getTrackedIds()

  if (ids.length === 0) {
    trackedIssues.value = []
    loading.value = false
    return
  }

  try {
    const res = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    trackedIssues.value = res.data.filter(issue => ids.includes(issue.id)).reverse()
  } catch (err) {
    console.error('Failed to load tracked issues:', err)
  } finally {
    loading.value = false
  }
}

const trackManualId = async () => {
  if (!manualIdInput.value) return
  trackMessage.value = ''
  const targetId = Number(manualIdInput.value)

  try {
    const res = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    const found = res.data.find(i => i.id === targetId)

    if (!found) {
      trackMessage.value = `Issue #${targetId} was not found on the server.`
      return
    }

    const currentIds = getTrackedIds()
    if (!currentIds.includes(targetId)) {
      currentIds.push(targetId)
      saveTrackedIds(currentIds)
    }

    manualIdInput.value = null
    trackMessage.value = `Issue #${targetId} added to your tracker.`
    await fetchTrackedIssues()
  } catch {
    trackMessage.value = 'Failed to fetch issue data.'
  }
}

const untrackIssue = (id: number) => {
  const updatedIds = getTrackedIds().filter(i => i !== id)
  saveTrackedIds(updatedIds)
  trackedIssues.value = trackedIssues.value.filter(i => i.id !== id)
}

onMounted(fetchTrackedIssues)
</script>

<template>
  <div class="space-y-6">
    <div class="bg-gradient-to-r from-blue-900 via-indigo-900 to-slate-900 rounded-3xl p-8 text-white shadow-xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <span class="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase bg-blue-500/20 text-blue-300 border border-blue-400/30 inline-block mb-2">
          Citizen Portal
        </span>
        <h1 class="text-3xl font-extrabold tracking-tight">My Tracked Reports</h1>
        <p class="text-blue-100 text-sm mt-1 max-w-2xl">
          Track the live triage stage and resolution status of reports submitted or confirmed from this device.
        </p>
      </div>

      <router-link
        to="/"
        class="px-5 py-2.5 rounded-2xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs shadow-md transition-all whitespace-nowrap"
      >
        + Report New Issue
      </router-link>
    </div>

    <!-- Manual ID Lookup Bar -->
    <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs flex flex-col sm:flex-row items-center gap-3">
      <input
        v-model="manualIdInput"
        type="number"
        placeholder="Enter report ID (e.g. 10)..."
        class="flex-1 w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 outline-none"
      />
      <button
        type="button"
        @click="trackManualId"
        class="w-full sm:w-auto px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs shadow-xs transition-all"
      >
        Track ID
      </button>
    </div>
    <p v-if="trackMessage" class="text-xs text-center font-semibold text-blue-600">{{ trackMessage }}</p>

    <!-- Tracked Issues List -->
    <div v-if="loading" class="bg-white p-12 rounded-3xl text-center text-gray-400 border border-gray-100 font-medium animate-pulse">
      Loading your tracked reports...
    </div>

    <div v-else-if="trackedIssues.length === 0" class="bg-white p-12 rounded-3xl text-center text-gray-500 border border-gray-100 space-y-3">
      <div class="w-12 h-12 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center mx-auto text-xl font-bold">
        📋
      </div>
      <h3 class="text-base font-bold text-gray-900">No Tracked Reports Yet</h3>
      <p class="text-xs max-w-sm mx-auto text-gray-500">
        Reports you submit or track via ID will appear here with live municipal progress updates.
      </p>
    </div>

    <div v-else class="space-y-4">
      <div
        v-for="issue in trackedIssues"
        :key="issue.id"
        class="bg-white p-6 rounded-3xl border border-gray-100 shadow-xs space-y-3"
      >
        <div class="flex items-start justify-between gap-3 flex-wrap">
          <div>
            <span class="text-xs font-mono text-gray-400">Report #{{ issue.id }}</span>
            <h3 class="text-lg font-bold text-gray-900 mt-0.5">{{ issue.title }}</h3>
          </div>
          <div class="flex items-center gap-2">
            <span
              class="px-2.5 py-1 text-xs font-bold rounded-lg"
              :class="{
                'bg-amber-100 text-amber-800': issue.status === 'PENDING',
                'bg-blue-100 text-blue-800': issue.status === 'IN_PROGRESS',
                'bg-emerald-100 text-emerald-800': issue.status === 'RESOLVED'
              }"
            >
              {{ issue.status }}
            </span>
            <button
              type="button"
              @click="untrackIssue(issue.id)"
              class="text-xs text-gray-400 hover:text-rose-600 font-bold px-2 py-1"
            >
              Untrack
            </button>
          </div>
        </div>

        <p class="text-sm text-gray-600">{{ issue.description }}</p>

        <!-- Progress Step Indicator -->
        <div class="pt-2">
          <div class="flex justify-between text-[11px] font-semibold text-gray-500 mb-1.5">
            <span :class="issue.status !== '' ? 'text-blue-700 font-bold' : ''">1. Reported</span>
            <span :class="issue.status === 'IN_PROGRESS' || issue.status === 'RESOLVED' ? 'text-blue-700 font-bold' : ''">2. Crew Dispatched</span>
            <span :class="issue.status === 'RESOLVED' ? 'text-emerald-700 font-bold' : ''">3. Resolved</span>
          </div>
          <div class="grid grid-cols-3 gap-1.5 h-1.5">
            <div class="rounded-full bg-blue-500"></div>
            <div class="rounded-full" :class="issue.status === 'IN_PROGRESS' || issue.status === 'RESOLVED' ? 'bg-blue-500' : 'bg-gray-200'"></div>
            <div class="rounded-full" :class="issue.status === 'RESOLVED' ? 'bg-emerald-500' : 'bg-gray-200'"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>