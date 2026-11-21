<script setup lang="ts">
import { ref, computed } from 'vue'
import axios from 'axios'
import type { Issue, IssueStatus } from '@/types/issue'

const API_BASE_URL = 'http://localhost:8081'
const username = ref('')
const password = ref('')
const isAuthenticated = ref(false)
const loginError = ref('')
const issues = ref<Issue[]>([])
const actionMessage = ref('')
const busyId = ref<number | null>(null)

const getAuthHeader = () => ({
  headers: { Authorization: 'Basic ' + btoa(`${username.value}:${password.value}`) }
})

const formatPriority = (priority?: string) => {
  if (!priority || priority.includes('ERROR')) return 'UNCLASSIFIED'
  return priority
}

const pendingCount = computed(() => issues.value.filter(i => i.status === 'PENDING').length)
const inProgressCount = computed(() => issues.value.filter(i => i.status === 'IN_PROGRESS').length)
const resolvedCount = computed(() => issues.value.filter(i => i.status === 'RESOLVED').length)

const login = async () => {
  loginError.value = ''
  try {
    await axios.get(`${API_BASE_URL}/api/issues/admin`, getAuthHeader())
    isAuthenticated.value = true
    await fetchIssues()
  } catch {
    loginError.value = 'Invalid admin credentials. Please check username and password.'
  }
}

const fetchIssues = async () => {
  try {
    const response = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = response.data.slice().reverse()
  } catch (error) {
    console.error('Failed to fetch issues', error)
  }
}

const updateStatus = async (id: number, newStatus: IssueStatus) => {
  busyId.value = id
  actionMessage.value = ''
  try {
    await axios.patch(`${API_BASE_URL}/api/issues/${id}/status`, { status: newStatus }, getAuthHeader())
    actionMessage.value = `Updated issue #${id} to ${newStatus}.`
    await fetchIssues()
  } catch {
    actionMessage.value = `Failed to update status for issue #${id}.`
  } finally {
    busyId.value = null
  }
}

const deleteIssue = async (id: number) => {
  busyId.value = id
  actionMessage.value = ''
  try {
    await axios.delete(`${API_BASE_URL}/api/issues/${id}`, getAuthHeader())
    actionMessage.value = `Deleted issue #${id}.`
    await fetchIssues()
  } catch {
    actionMessage.value = `Could not delete issue #${id} (ensure DELETE /api/issues/{id} is enabled on backend).`
  } finally {
    busyId.value = null
  }
}
</script>

<template>
  <!-- Login Gate -->
  <div v-if="!isAuthenticated" class="min-h-[70vh] flex items-center justify-center">
    <div class="max-w-md w-full bg-white p-8 rounded-3xl shadow-xl border border-gray-100">
      <div class="text-center mb-6">
        <div class="w-12 h-12 bg-blue-100 text-blue-700 rounded-2xl flex items-center justify-center mx-auto mb-3 font-extrabold text-lg">
          CC
        </div>
        <h2 class="text-2xl font-extrabold text-gray-900">Authority Access</h2>
        <p class="text-sm text-gray-500 mt-1">Sign in to manage municipal triage and dispatch field teams.</p>
      </div>

      <form @submit.prevent="login" class="space-y-4">
        <div>
          <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Username</label>
          <input
            v-model="username"
            type="text"
            required
            placeholder="admin"
            class="w-full border border-gray-300 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
          />
        </div>
        <div>
          <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Password</label>
          <input
            v-model="password"
            type="password"
            required
            placeholder="••••••••••••"
            class="w-full border border-gray-300 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
          />
        </div>
        <button
          type="submit"
          class="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl shadow-md transition-all text-sm"
        >
          Sign In to Dashboard
        </button>
        <p v-if="loginError" class="text-xs text-rose-600 text-center font-medium mt-2">{{ loginError }}</p>
      </form>
    </div>
  </div>

  <!-- Authenticated Management Dashboard -->
  <div v-else class="space-y-6">
    <div class="bg-gradient-to-r from-slate-900 via-blue-900 to-indigo-900 rounded-3xl p-8 text-white shadow-xl flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
      <div>
        <h1 class="text-3xl font-extrabold tracking-tight mb-1">Municipal Triage Dashboard</h1>
        <p class="text-blue-200 text-sm">Review crowdsourced reports, update statuses, and clean up test items.</p>
      </div>
      <button
        @click="isAuthenticated = false"
        class="bg-white/10 hover:bg-white/20 text-white px-4 py-2 rounded-xl text-sm font-semibold transition-all"
      >
        Sign Out
      </button>
    </div>

    <!-- Summary Cards -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
        <div class="text-xs font-bold text-gray-400 uppercase">Total Reports</div>
        <div class="text-2xl font-extrabold text-gray-900 mt-1">{{ issues.length }}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
        <div class="text-xs font-bold text-amber-600 uppercase">Pending</div>
        <div class="text-2xl font-extrabold text-amber-700 mt-1">{{ pendingCount }}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
        <div class="text-xs font-bold text-blue-600 uppercase">In Progress</div>
        <div class="text-2xl font-extrabold text-blue-700 mt-1">{{ inProgressCount }}</div>
      </div>
      <div class="bg-white p-5 rounded-2xl border border-gray-100 shadow-xs">
        <div class="text-xs font-bold text-emerald-600 uppercase">Resolved</div>
        <div class="text-2xl font-extrabold text-emerald-700 mt-1">{{ resolvedCount }}</div>
      </div>
    </div>

    <div v-if="actionMessage" class="px-4 py-3 rounded-2xl bg-blue-50 border border-blue-200 text-sm font-medium text-blue-900">
      {{ actionMessage }}
    </div>

    <!-- Management Table -->
    <div class="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
      <div class="p-6 border-b border-gray-100 flex justify-between items-center">
        <h2 class="text-lg font-bold text-gray-900">Active Municipal Reports</h2>
        <button
          @click="fetchIssues"
          class="text-xs font-bold px-3.5 py-1.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-xl transition-all"
        >
          Refresh List
        </button>
      </div>

      <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Report</th>
              <th class="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Priority</th>
              <th class="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Reports</th>
              <th class="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Status</th>
              <th class="px-6 py-3 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody class="bg-white divide-y divide-gray-200">
            <tr v-for="issue in issues" :key="issue.id" class="hover:bg-gray-50/60 transition-colors">
              <td class="px-6 py-4">
                <div class="font-bold text-gray-900 text-sm">#{{ issue.id }} · {{ issue.title }}</div>
                <div class="text-xs text-gray-500 line-clamp-1 mt-0.5">{{ issue.description }}</div>
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-xs font-bold text-gray-700">
                {{ formatPriority(issue.priority) }}
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-xs font-bold text-indigo-700">
                {{ issue.reportCount || 1 }}
              </td>
              <td class="px-6 py-4 whitespace-nowrap">
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
              </td>
              <td class="px-6 py-4 whitespace-nowrap text-xs space-x-2">
                <button
                  :disabled="busyId === issue.id"
                  @click="updateStatus(issue.id, 'PENDING')"
                  class="px-2.5 py-1.5 rounded-lg bg-amber-50 hover:bg-amber-100 text-amber-700 font-bold disabled:opacity-50"
                >
                  Pending
                </button>
                <button
                  :disabled="busyId === issue.id"
                  @click="updateStatus(issue.id, 'IN_PROGRESS')"
                  class="px-2.5 py-1.5 rounded-lg bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold disabled:opacity-50"
                >
                  In Progress
                </button>
                <button
                  :disabled="busyId === issue.id"
                  @click="updateStatus(issue.id, 'RESOLVED')"
                  class="px-2.5 py-1.5 rounded-lg bg-emerald-50 hover:bg-emerald-100 text-emerald-700 font-bold disabled:opacity-50"
                >
                  Resolve
                </button>
                <button
                  :disabled="busyId === issue.id"
                  @click="deleteIssue(issue.id)"
                  class="px-2.5 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-700 font-bold disabled:opacity-50"
                >
                  Delete
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>