<script setup lang="ts">
import { ref, computed } from 'vue'
import axios from 'axios'
import type { Issue, IssueStatus } from '@/types/issue'

const API_BASE_URL = 'http://localhost:8081'
const ANNOUNCEMENTS_KEY = 'civicconnect_announcements_v1'

interface AnnouncementItem {
  id: number
  tag: 'HIGH' | 'MEDIUM' | 'LOW'
  date: string
  title: string
  summary: string
}

const DEFAULT_ANNOUNCEMENTS: AnnouncementItem[] = [
  {
    id: 1,
    tag: 'HIGH',
    date: 'Nov 2026',
    title: 'Monsoon Drainage & Canal Clearing — Moratuwa',
    summary: 'Field crews are inspecting low-lying roads and culverts across Katubedda, Molpe, and Soysapura.'
  },
  {
    id: 2,
    tag: 'MEDIUM',
    date: 'Nov 2026',
    title: 'LED Streetlight Maintenance Schedule',
    summary: 'Faulty street lamps confirmed by residents are prioritized for evening repair batches.'
  }
]

const username = ref('')
const password = ref('')
const isAuthenticated = ref(false)
const loginError = ref('')
const issues = ref<Issue[]>([])
const actionMessage = ref('')
const busyId = ref<number | null>(null)

const loadAnnouncements = (): AnnouncementItem[] => {
  try {
    const raw = localStorage.getItem(ANNOUNCEMENTS_KEY)
    if (!raw) return DEFAULT_ANNOUNCEMENTS.map(item => ({ ...item }))
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return DEFAULT_ANNOUNCEMENTS.map(item => ({ ...item }))
    return parsed
  } catch {
    return DEFAULT_ANNOUNCEMENTS.map(item => ({ ...item }))
  }
}

const saveAnnouncements = (items: AnnouncementItem[]) => {
  localStorage.setItem(ANNOUNCEMENTS_KEY, JSON.stringify(items))
  announcements.value = items
  window.dispatchEvent(new StorageEvent('storage', { key: ANNOUNCEMENTS_KEY }))
}

const announcements = ref<AnnouncementItem[]>(loadAnnouncements())
const noticeTitle = ref('')
const noticeSummary = ref('')
const noticeTag = ref<'HIGH' | 'MEDIUM' | 'LOW'>('HIGH')
const editingNoticeId = ref<number | null>(null)
const noticeMessage = ref('')

const getAuthHeader = () => ({
  headers: { Authorization: 'Basic ' + btoa(`${username.value}:${password.value}`) }
})

const formatPriority = (priority?: string) => {
  if (!priority || priority.includes('ERROR')) return 'UNCLASSIFIED'
  return priority
}

const normalizeAndSortIssues = (items: Issue[]): Issue[] => {
  const uniqueById = new Map<number, Issue>()
  for (const item of items) {
    if (typeof item.id === 'number') {
      uniqueById.set(item.id, item)
    }
  }
  return Array.from(uniqueById.values()).sort((a, b) => b.id - a.id)
}

const pendingCount = computed(() => issues.value.filter(i => i.status === 'PENDING').length)
const inProgressCount = computed(() => issues.value.filter(i => i.status === 'IN_PROGRESS').length)
const resolvedCount = computed(() => issues.value.filter(i => i.status === 'RESOLVED').length)

const login = async () => {
  loginError.value = ''
  try {
    await axios.get(`${API_BASE_URL}/api/issues/admin`, getAuthHeader())
    isAuthenticated.value = true
    announcements.value = loadAnnouncements()
    await fetchIssues()
  } catch {
    loginError.value = 'Invalid admin credentials. Please check username and password.'
  }
}

const fetchIssues = async () => {
  try {
    const response = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = normalizeAndSortIssues(response.data)
  } catch (error) {
    console.error('Failed to fetch issues', error)
    actionMessage.value = 'Failed to load municipal reports.'
  }
}

const updateStatus = async (id: number, newStatus: IssueStatus) => {
  if (busyId.value !== null) return
  busyId.value = id
  actionMessage.value = ''
  try {
    const response = await axios.patch<Issue>(
      `${API_BASE_URL}/api/issues/${id}/status`,
      { status: newStatus },
      getAuthHeader()
    )
    const updatedIssue = response.data
    issues.value = issues.value.map(item =>
      item.id === id
        ? { ...item, ...(updatedIssue && typeof updatedIssue === 'object' ? updatedIssue : {}), status: updatedIssue?.status ?? newStatus }
        : item
    )
    actionMessage.value = `Updated issue #${id} to ${newStatus}.`
  } catch {
    actionMessage.value = `Failed to update status for issue #${id}.`
  } finally {
    busyId.value = null
  }
}

const deleteIssue = async (id: number) => {
  if (busyId.value !== null) return
  busyId.value = id
  actionMessage.value = ''
  try {
    await axios.delete(`${API_BASE_URL}/api/issues/${id}`, getAuthHeader())
    issues.value = issues.value.filter(item => item.id !== id)
    actionMessage.value = `Deleted issue #${id}.`
  } catch {
    actionMessage.value = `Could not delete issue #${id} (ensure DELETE /api/issues/{id} is enabled on backend).`
  } finally {
    busyId.value = null
  }
}

const formatShortDate = () =>
  new Date().toLocaleDateString('en-US', { month: 'short', year: 'numeric' })

const resetNoticeForm = () => {
  noticeTitle.value = ''
  noticeSummary.value = ''
  noticeTag.value = 'HIGH'
  editingNoticeId.value = null
}

const saveAnnouncement = () => {
  const title = noticeTitle.value.trim()
  const summary = noticeSummary.value.trim()
  if (!title || !summary) return

  if (editingNoticeId.value !== null) {
    const updated = announcements.value.map(item =>
      item.id === editingNoticeId.value
        ? { ...item, title, summary, tag: noticeTag.value }
        : item
    )
    saveAnnouncements(updated)
    noticeMessage.value = `Updated announcement #${editingNoticeId.value}.`
    resetNoticeForm()
    return
  }

  const nextId = announcements.value.reduce((max, item) => Math.max(max, item.id), 0) + 1
  const created: AnnouncementItem = {
    id: nextId,
    tag: noticeTag.value,
    date: formatShortDate(),
    title,
    summary
  }
  saveAnnouncements([created, ...announcements.value])
  noticeMessage.value = 'Published new city announcement to the live feed.'
  resetNoticeForm()
}

const startEditAnnouncement = (item: AnnouncementItem) => {
  editingNoticeId.value = item.id
  noticeTitle.value = item.title
  noticeSummary.value = item.summary
  noticeTag.value = item.tag
  noticeMessage.value = ''
}

const deleteAnnouncement = (id: number) => {
  const remaining = announcements.value.filter(item => item.id !== id)
  saveAnnouncements(remaining)
  if (editingNoticeId.value === id) {
    resetNoticeForm()
  }
  noticeMessage.value = `Removed announcement #${id}.`
}

const restoreDefaultAnnouncements = () => {
  saveAnnouncements(DEFAULT_ANNOUNCEMENTS.map(item => ({ ...item })))
  resetNoticeForm()
  noticeMessage.value = 'Restored default Moratuwa announcements.'
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
                  type="button"
                  :disabled="busyId !== null"
                  @click="updateStatus(issue.id, 'PENDING')"
                  class="px-2.5 py-1.5 rounded-lg bg-amber-50 hover:bg-amber-100 text-amber-700 font-bold disabled:opacity-50"
                >
                  Pending
                </button>
                <button
                  type="button"
                  :disabled="busyId !== null"
                  @click="updateStatus(issue.id, 'IN_PROGRESS')"
                  class="px-2.5 py-1.5 rounded-lg bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold disabled:opacity-50"
                >
                  In Progress
                </button>
                <button
                  type="button"
                  :disabled="busyId !== null"
                  @click="updateStatus(issue.id, 'RESOLVED')"
                  class="px-2.5 py-1.5 rounded-lg bg-emerald-50 hover:bg-emerald-100 text-emerald-700 font-bold disabled:opacity-50"
                >
                  Resolve
                </button>
                <button
                  type="button"
                  :disabled="busyId !== null"
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

    <!-- City Announcements Manager -->
    <div class="bg-white rounded-3xl shadow-sm border border-gray-100 p-6 space-y-5">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-gray-100 pb-4">
        <div>
          <h2 class="text-lg font-bold text-gray-900">City Announcements Manager</h2>
          <p class="text-xs text-gray-500 mt-0.5">Publish, edit, or remove notices shown in the Community Live Feed sidebar.</p>
        </div>
        <button
          type="button"
          @click="restoreDefaultAnnouncements"
          class="text-xs font-bold px-3.5 py-1.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-xl transition-all self-start sm:self-auto"
        >
          Restore Defaults
        </button>
      </div>

      <div v-if="noticeMessage" class="px-4 py-2.5 rounded-2xl bg-emerald-50 border border-emerald-200 text-xs font-semibold text-emerald-900">
        {{ noticeMessage }}
      </div>

      <form @submit.prevent="saveAnnouncement" class="grid grid-cols-1 md:grid-cols-6 gap-3">
        <input
          v-model="noticeTitle"
          type="text"
          required
          placeholder="Announcement headline (e.g. Katubedda Drainage Work)"
          class="md:col-span-3 border border-gray-300 rounded-xl px-4 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
        />
        <select
          v-model="noticeTag"
          class="md:col-span-1 border border-gray-300 rounded-xl px-3 py-2.5 text-sm font-bold text-gray-700 bg-white focus:ring-2 focus:ring-blue-500 outline-none"
        >
          <option value="HIGH">HIGH</option>
          <option value="MEDIUM">MEDIUM</option>
          <option value="LOW">LOW</option>
        </select>
        <div class="md:col-span-2 flex gap-2">
          <button
            type="submit"
            class="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-bold py-2.5 px-4 rounded-xl text-sm transition-all"
          >
            {{ editingNoticeId !== null ? 'Update Notice' : 'Publish Notice' }}
          </button>
          <button
            v-if="editingNoticeId !== null"
            type="button"
            @click="resetNoticeForm"
            class="bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold py-2.5 px-3 rounded-xl text-sm transition-all"
          >
            Cancel
          </button>
        </div>
        <textarea
          v-model="noticeSummary"
          required
          rows="2"
          placeholder="Write brief notice details for residents..."
          class="md:col-span-6 border border-gray-300 rounded-xl px-4 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
        ></textarea>
      </form>

      <div class="space-y-3">
        <div
          v-for="notice in announcements"
          :key="notice.id"
          class="p-4 rounded-2xl border border-gray-100 bg-gray-50/70 flex flex-col sm:flex-row sm:items-center justify-between gap-3"
        >
          <div class="space-y-1">
            <div class="flex items-center gap-2 text-[11px] font-bold">
              <span
                class="px-2 py-0.5 rounded-md"
                :class="{
                  'bg-rose-100 text-rose-800': notice.tag === 'HIGH',
                  'bg-blue-100 text-blue-800': notice.tag === 'MEDIUM',
                  'bg-emerald-100 text-emerald-800': notice.tag === 'LOW'
                }"
              >
                {{ notice.tag }}
              </span>
              <span class="text-gray-400">{{ notice.date }}</span>
            </div>
            <div class="text-sm font-bold text-gray-900">{{ notice.title }}</div>
            <p class="text-xs text-gray-600">{{ notice.summary }}</p>
          </div>

          <div class="flex items-center gap-2 shrink-0">
            <button
              type="button"
              @click="startEditAnnouncement(notice)"
              class="px-3 py-1.5 rounded-xl text-xs font-bold bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200"
            >
              Edit
            </button>
            <button
              type="button"
              @click="deleteAnnouncement(notice.id)"
              class="px-3 py-1.5 rounded-xl text-xs font-bold bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200"
            >
              Delete
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>