<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import type { Issue } from '@/types/issue'

const route = useRoute()
const API_BASE_URL = 'http://localhost:8081'
const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
const GEO_CACHE_KEY = 'civicconnect_nominatim_cache_v1'
const TRACKED_IDS_KEY = 'civicconnect_tracked_ids'
const ANNOUNCEMENTS_KEY = 'civicconnect_announcements_v1'

export interface AnnouncementItem {
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

type PendingAdminAction = {
  execute: (authorization: string) => Promise<void>
  onError: (error: unknown) => void
}

const issues = ref<Issue[]>([])
const addressMap = ref<Record<number, string>>({})
const inFlightKeys = new Set<string>()
const confirmingId = ref<number | null>(null)
const deletingId = ref<number | null>(null)
const adminAuthorization = ref<string | null>(null)
const adminUsername = ref('')
const adminPassword = ref('')
const adminLoginError = ref('')
const adminActionError = ref('')
const isAdminLoginOpen = ref(false)
const isAuthenticatingAdmin = ref(false)
const pendingAdminAction = ref<PendingAdminAction | null>(null)
const selectedIssue = ref<Issue | null>(null)
const editingIssue = ref<Issue | null>(null)
const editForm = ref({
  title: '',
  description: ''
})
const isSavingEdit = ref(false)
const editError = ref('')
const copiedGps = ref(false)
const loading = ref(true)
const isSubmitting = ref(false)
const isLocating = ref(false)
const locationError = ref('')
const selectedImage = ref<File | null>(null)
const imageError = ref('')
const submitError = ref('')
const imageInput = ref<HTMLInputElement | null>(null)
const showReportModal = ref(false)

const categories = ['ALL', 'Roads & Drainage', 'Streetlights & Power', 'Waste & Sanitation', 'Public Safety']
const selectedCategory = ref('ALL')
const areaFilter = ref('ALL')
const searchQuery = ref('')
const priorityFilter = ref('ALL')
const statusFilter = ref('ALL')
const sortBy = ref<'NEWEST' | 'MOST_CONFIRMED' | 'PRIORITY'>('NEWEST')
const availableAreas = computed(() => [
  'ALL',
  ...Array.from(new Set(Object.values(addressMap.value))).sort((a, b) => a.localeCompare(b))
])

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

const announcements = ref<AnnouncementItem[]>(loadAnnouncements())

const syncAnnouncementsFromStorage = () => {
  announcements.value = loadAnnouncements()
}

const handleStorageEvent = (event: StorageEvent) => {
  if (event.key === ANNOUNCEMENTS_KEY) {
    syncAnnouncementsFromStorage()
  }
}

const newIssue = ref({
  category: 'Roads & Drainage',
  title: '',
  description: '',
  latitude: null as number | null,
  longitude: null as number | null
})

const loadGeoCache = (): Record<string, string> => {
  try {
    const raw = localStorage.getItem(GEO_CACHE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

const saveGeoCache = (cache: Record<string, string>) => {
  try {
    localStorage.setItem(GEO_CACHE_KEY, JSON.stringify(cache))
  } catch {
    // Ignore storage quota errors
  }
}

const geoCache = ref<Record<string, string>>(loadGeoCache())

const trackLocalId = (id: number) => {
  try {
    const raw = localStorage.getItem(TRACKED_IDS_KEY)
    const ids: number[] = raw ? JSON.parse(raw) : []
    if (!ids.includes(id)) {
      ids.push(id)
      localStorage.setItem(TRACKED_IDS_KEY, JSON.stringify(ids))
    }
  } catch (err) {
    console.error('Failed to track issue ID in localStorage:', err)
  }
}

const formatPriority = (priority?: string) => {
  if (!priority || priority.includes('ERROR')) return 'UNCLASSIFIED'
  return priority.toUpperCase()
}

const priorityRank = (priority?: string) => {
  const p = formatPriority(priority)
  if (p === 'HIGH') return 3
  if (p === 'MEDIUM') return 2
  if (p === 'LOW') return 1
  return 0
}

const getStatusStep = (status: Issue['status']) => {
  if (status === 'RESOLVED') return 3
  if (status === 'IN_PROGRESS') return 2
  return 1
}

const openIssueModal = (issue: Issue) => {
  copiedGps.value = false
  selectedIssue.value = issue
}

const openEditModal = (issue: Issue) => {
  editError.value = ''
  editingIssue.value = issue
  editForm.value = {
    title: issue.title,
    description: issue.description
  }
}

const createAdminAuthorization = () => {
  const credentials = new TextEncoder().encode(`${adminUsername.value}:${adminPassword.value}`)
  const binaryCredentials = Array.from(credentials, byte => String.fromCharCode(byte)).join('')
  return `Basic ${btoa(binaryCredentials)}`
}

const runAdminAction = async (action: PendingAdminAction) => {
  if (!adminAuthorization.value) {
    pendingAdminAction.value = action
    adminLoginError.value = ''
    isAdminLoginOpen.value = true
    return
  }

  try {
    await action.execute(adminAuthorization.value)
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      adminAuthorization.value = null
      pendingAdminAction.value = action
      adminLoginError.value = 'Admin credentials were rejected. Please sign in again.'
      isAdminLoginOpen.value = true
      return
    }
    action.onError(error)
  }
}

const loginForAdminAction = async () => {
  adminLoginError.value = ''
  isAuthenticatingAdmin.value = true
  let authorization: string
  try {
    authorization = createAdminAuthorization()
  } catch {
    adminLoginError.value = 'Credentials could not be encoded. Check the username and password.'
    isAuthenticatingAdmin.value = false
    return
  }

  try {
    await axios.get(`${API_BASE_URL}/api/issues/admin`, {
      headers: { Authorization: authorization }
    })
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      adminLoginError.value = 'Invalid admin credentials. Please check username and password.'
    } else {
      console.error('Failed to authenticate admin:', error)
      adminLoginError.value = 'Could not verify admin access. Check that the backend is available.'
    }
    isAuthenticatingAdmin.value = false
    return
  }

  isAuthenticatingAdmin.value = false
  adminAuthorization.value = authorization
  adminPassword.value = ''
  isAdminLoginOpen.value = false
  const action = pendingAdminAction.value
  pendingAdminAction.value = null
  if (action) {
    await runAdminAction(action)
  }
}

const cancelAdminLogin = () => {
  pendingAdminAction.value = null
  isAdminLoginOpen.value = false
  adminLoginError.value = ''
  adminPassword.value = ''
}

const saveEditedIssue = async () => {
  if (!editingIssue.value) return
  const issueId = editingIssue.value.id
  const title = editForm.value.title
  const description = editForm.value.description
  editError.value = ''
  await runAdminAction({
    execute: async (authorization) => {
      isSavingEdit.value = true
      try {
        await axios.put(`${API_BASE_URL}/api/issues/${issueId}`, { title, description }, {
          headers: { Authorization: authorization }
        })
        editingIssue.value = null
        await fetchIssues()
        if (selectedIssue.value?.id === issueId) {
          const updated = issues.value.find(i => i.id === issueId)
          if (updated) selectedIssue.value = updated
        }
      } finally {
        isSavingEdit.value = false
      }
    },
    onError: (error) => {
      console.error('Failed to update issue:', error)
      editError.value = 'Failed to save changes. Please check your inputs and try again.'
    }
  })
}

const deleteIssue = async (id: number) => {
  adminActionError.value = ''
  await runAdminAction({
    execute: async (authorization) => {
      deletingId.value = id
      try {
        await axios.delete(`${API_BASE_URL}/api/issues/${id}`, {
          headers: { Authorization: authorization }
        })
        if (selectedIssue.value?.id === id) {
          selectedIssue.value = null
        }
        if (editingIssue.value?.id === id) {
          editingIssue.value = null
        }
        issues.value = issues.value.filter(i => i.id !== id)
      } finally {
        deletingId.value = null
      }
    },
    onError: (error) => {
      console.error('Failed to delete issue:', error)
      adminActionError.value = 'Failed to delete the issue. Please try again.'
    }
  })
}

const copyCoordinates = async (issue: Issue) => {
  const text = `${issue.latitude.toFixed(5)}, ${issue.longitude.toFixed(5)}`
  try {
    await navigator.clipboard.writeText(text)
    copiedGps.value = true
    setTimeout(() => {
      copiedGps.value = false
    }, 1800)
  } catch {
    copiedGps.value = false
  }
}

const getCoordKey = (lat: number, lon: number) => `${lat.toFixed(4)},${lon.toFixed(4)}`

const getFallbackAreaName = (lat: number, lon: number) => {
  if (Math.abs(lat - 6.7969) < 0.004 && Math.abs(lon - 79.9018) < 0.004) {
    return 'Katubedda / University of Moratuwa, Moratuwa'
  }
  if (Math.abs(lat - 6.793) < 0.004 && Math.abs(lon - 79.9011) < 0.004) {
    return 'Molpe, Moratuwa'
  }
  return 'Moratuwa Area'
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

const resolveAddresses = async (list: Issue[]) => {
  for (const item of list) {
    if (addressMap.value[item.id] || item.latitude == null || item.longitude == null) {
      continue
    }

    const key = getCoordKey(item.latitude, item.longitude)

    if (geoCache.value[key]) {
      addressMap.value[item.id] = geoCache.value[key]
      continue
    }

    addressMap.value[item.id] = getFallbackAreaName(item.latitude, item.longitude)

    if (inFlightKeys.has(key)) {
      continue
    }

    inFlightKeys.add(key)
    try {
      await sleep(1100)
      const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${encodeURIComponent(
        item.latitude
      )}&lon=${encodeURIComponent(item.longitude)}`
      const res = await fetch(url, {
        headers: {
          Accept: 'application/json'
        }
      })

      if (!res.ok) {
        continue
      }

      const data = await res.json()
      const parts: string[] = (data.display_name || '')
        .split(',')
        .map((s: string) => s.trim())
        .filter(Boolean)
      const resolved = parts.slice(0, 3).join(', ') || getFallbackAreaName(item.latitude, item.longitude)

      geoCache.value[key] = resolved
      saveGeoCache(geoCache.value)

      for (const target of issues.value) {
        if (
          target.latitude != null &&
          target.longitude != null &&
          getCoordKey(target.latitude, target.longitude) === key
        ) {
          addressMap.value[target.id] = resolved
        }
      }
    } catch {
      // Keep fallback label silently on rate limits or CORS blocks
    } finally {
      inFlightKeys.delete(key)
    }
  }
}

const useMyLocation = () => {
  locationError.value = ''
  if (!navigator.geolocation) {
    locationError.value = 'Geolocation is not supported in this browser.'
    return
  }
  isLocating.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      newIssue.value.latitude = pos.coords.latitude
      newIssue.value.longitude = pos.coords.longitude
      isLocating.value = false
    },
    () => {
      locationError.value = 'Unable to retrieve GPS location.'
      isLocating.value = false
    },
    { enableHighAccuracy: true }
  )
}

const handleImageSelection = (event: Event) => {
  const input = event.currentTarget as HTMLInputElement
  const file = input.files?.[0] ?? null
  imageError.value = ''
  selectedImage.value = null

  if (file && file.size > MAX_IMAGE_SIZE_BYTES) {
    imageError.value = 'Image must be under 5 MB.'
    return
  }
  selectedImage.value = file
}

const fetchIssues = async () => {
  try {
    const res = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = res.data.slice().reverse()
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}

const submitIssue = async () => {
  if (newIssue.value.latitude === null || newIssue.value.longitude === null) return
  isSubmitting.value = true
  submitError.value = ''
  try {
    const formattedTitle =
      newIssue.value.category && !newIssue.value.title.startsWith('[')
        ? `[${newIssue.value.category}] ${newIssue.value.title}`
        : newIssue.value.title

    const formData = new FormData()
    formData.append('title', formattedTitle)
    formData.append('description', newIssue.value.description)
    formData.append('latitude', newIssue.value.latitude.toString())
    formData.append('longitude', newIssue.value.longitude.toString())
    if (selectedImage.value) formData.append('image', selectedImage.value)

    const res = await axios.post<Issue>(`${API_BASE_URL}/api/issues`, formData)
    if (res.data?.id) {
      trackLocalId(res.data.id)
    }

    newIssue.value = {
      category: 'Roads & Drainage',
      title: '',
      description: '',
      latitude: null,
      longitude: null
    }
    selectedImage.value = null
    showReportModal.value = false
    if (imageInput.value) imageInput.value.value = ''
    await fetchIssues()
  } catch {
    submitError.value = 'Failed to submit report. Please try again.'
  } finally {
    isSubmitting.value = false
  }
}

const confirmIssue = async (id: number) => {
  confirmingId.value = id
  try {
    await axios.post(`${API_BASE_URL}/api/issues/${id}/confirm`)
    trackLocalId(id)
    await fetchIssues()
    if (selectedIssue.value?.id === id) {
      const updated = issues.value.find(i => i.id === id)
      if (updated) selectedIssue.value = updated
    }
  } catch (err) {
    console.error('Failed to confirm issue:', err)
  } finally {
    confirmingId.value = null
  }
}

const filteredIssues = computed(() => {
  const list = issues.value.filter(issue => {
    const titleLower = issue.title.toLowerCase()
    const descLower = issue.description.toLowerCase()
    const queryLower = searchQuery.value.toLowerCase()

    const matchesSearch = titleLower.includes(queryLower) || descLower.includes(queryLower)
    const matchesCategory =
      selectedCategory.value === 'ALL' || titleLower.includes(selectedCategory.value.toLowerCase())
    const matchesPriority =
      priorityFilter.value === 'ALL' || formatPriority(issue.priority) === priorityFilter.value
    const matchesStatus = statusFilter.value === 'ALL' || issue.status === statusFilter.value
    const matchesArea = areaFilter.value === 'ALL' || addressMap.value[issue.id] === areaFilter.value

    return matchesSearch && matchesCategory && matchesPriority && matchesStatus && matchesArea
  })

  if (sortBy.value === 'MOST_CONFIRMED') {
    return list.slice().sort((a, b) => (b.reportCount || 1) - (a.reportCount || 1))
  }
  if (sortBy.value === 'PRIORITY') {
    return list.slice().sort((a, b) => priorityRank(b.priority) - priorityRank(a.priority))
  }
  return list
})

const applyRouteQueries = () => {
  const qCategory = typeof route.query.category === 'string' ? route.query.category : ''
  if (qCategory && categories.includes(qCategory)) {
    selectedCategory.value = qCategory
  }

  const qLat = typeof route.query.lat === 'string' ? Number(route.query.lat) : NaN
  const qLng = typeof route.query.lng === 'string' ? Number(route.query.lng) : NaN
  if (!Number.isNaN(qLat) && !Number.isNaN(qLng)) {
    newIssue.value.latitude = qLat
    newIssue.value.longitude = qLng
    showReportModal.value = true
  }
}

watch(
  issues,
  (list) => {
    if (list.length > 0) {
      resolveAddresses(list)
    }
  },
  { immediate: true }
)

onMounted(async () => {
  syncAnnouncementsFromStorage()
  window.addEventListener('storage', handleStorageEvent)
  applyRouteQueries()
  await fetchIssues()
})
</script>

<template>
  <div class="space-y-6">
    <div class="bg-gradient-to-r from-blue-900 via-indigo-900 to-emerald-800 rounded-3xl p-8 md:p-10 text-white shadow-2xl flex flex-col md:flex-row justify-between items-start md:items-center relative overflow-hidden">
      <div class="max-w-xl z-10">
        <span class="px-3 py-1 bg-emerald-500/20 border border-emerald-400/30 text-emerald-300 rounded-full text-xs font-bold tracking-wide uppercase mb-3 inline-block">
          Moratuwa Municipal Council
        </span>
        <h1 class="text-3xl md:text-4xl font-extrabold tracking-tight mb-3">CivicConnect Smart Platform</h1>
        <p class="text-blue-100 text-sm md:text-base leading-relaxed">
          Report local infrastructure issues, confirm neighborhood hazards, and track AI-assisted priority triage in real time.
        </p>
      </div>
      <button
        type="button"
        @click="showReportModal = true"
        class="mt-6 md:mt-0 z-10 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold px-7 py-3.5 rounded-2xl shadow-xl transition-all transform hover:-translate-y-0.5 flex items-center space-x-2.5"
      >
        <span class="text-lg leading-none">+</span>
        <span>Report an Issue</span>
      </button>
    </div>

    <p
      v-if="adminActionError"
      role="alert"
      class="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
    >
      {{ adminActionError }}
    </p>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <div class="lg:col-span-2 space-y-6">
        <div class="bg-white p-5 rounded-2xl shadow-xs border border-gray-100 space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-5 gap-3">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="Search reports..."
              class="md:col-span-2 bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 outline-none"
            />
            <select
              v-model="priorityFilter"
              class="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2.5 text-sm font-semibold text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Priorities</option>
              <option value="HIGH">High Priority</option>
              <option value="MEDIUM">Medium Priority</option>
              <option value="LOW">Low Priority</option>
              <option value="UNCLASSIFIED">Unclassified</option>
            </select>
            <select
              v-model="statusFilter"
              class="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2.5 text-sm font-semibold text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Statuses</option>
              <option value="PENDING">Pending</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="RESOLVED">Resolved</option>
            </select>
            <select
              v-model="areaFilter"
              class="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2.5 text-sm font-semibold text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option v-for="area in availableAreas" :key="area" :value="area">
                {{ area === 'ALL' ? 'All Areas' : area }}
              </option>
            </select>
          </div>

          <div class="flex flex-wrap items-center justify-between gap-3">
            <div class="flex flex-wrap gap-2">
              <button
                v-for="cat in categories"
                :key="cat"
                type="button"
                @click="selectedCategory = cat"
                class="px-3 py-1.5 rounded-xl text-xs font-bold transition-all border"
                :class="selectedCategory === cat
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-gray-50 text-gray-600 border-gray-200 hover:bg-gray-100'"
              >
                {{ cat === 'ALL' ? 'All Categories' : cat }}
              </button>
            </div>

            <div class="flex items-center gap-2 text-xs font-semibold text-gray-500">
              <span>Sort:</span>
              <select
                v-model="sortBy"
                class="bg-gray-50 border border-gray-200 rounded-xl px-3 py-1.5 text-xs font-bold text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="NEWEST">Newest First</option>
                <option value="MOST_CONFIRMED">Most Confirmed</option>
                <option value="PRIORITY">Highest Priority</option>
              </select>
            </div>
          </div>
        </div>

        <div class="bg-white p-6 rounded-3xl shadow-sm border border-gray-100">
          <div class="flex justify-between items-center mb-6 pb-4 border-b border-gray-100">
            <div>
              <h2 class="text-xl font-extrabold text-gray-900">Community Live Feed</h2>
              <p class="text-xs text-gray-500 mt-0.5">Real-time geospatial issue tracking</p>
            </div>
            <span class="text-xs font-bold px-3.5 py-1.5 bg-blue-50 text-blue-700 rounded-full border border-blue-100">
              {{ filteredIssues.length }} Reports Shown
            </span>
          </div>

          <div v-if="loading" class="text-gray-400 py-12 text-center font-medium animate-pulse">
            Syncing municipal database...
          </div>
          <div v-else-if="filteredIssues.length === 0" class="text-gray-400 py-12 text-center font-medium">
            No reports match your current filters.
          </div>

          <div v-else class="space-y-5">
            <div
              v-for="issue in filteredIssues"
              :key="issue.id"
              class="p-6 border border-gray-100 rounded-2xl bg-gradient-to-br from-white to-gray-50/50 hover:shadow-md transition-all"
            >
              <div class="flex justify-between items-start mb-3 gap-3 flex-wrap">
                <div>
                  <button
                    type="button"
                    @click="openIssueModal(issue)"
                    class="text-lg font-bold text-gray-900 hover:text-blue-700 text-left transition-colors"
                  >
                    {{ issue.title }}
                  </button>
                  <div class="text-xs text-gray-500 mt-1">
                    <span class="font-semibold text-gray-700">{{ addressMap[issue.id] || 'Moratuwa Area' }}</span>
                    <span class="mx-1.5 text-gray-300">·</span>
                    <span class="font-mono text-gray-400">{{ issue.latitude.toFixed(4) }}, {{ issue.longitude.toFixed(4) }}</span>
                  </div>
                </div>

                <div class="flex items-center space-x-2 flex-wrap gap-y-1">
                  <span class="px-2.5 py-1 text-xs font-extrabold rounded-xl bg-indigo-50 text-indigo-700 border border-indigo-200">
                    {{ issue.reportCount || 1 }} Report(s)
                  </span>
                  <span
                    class="px-2.5 py-1 text-xs font-bold rounded-xl border"
                    :class="{
                      'bg-rose-50 text-rose-700 border-rose-200': formatPriority(issue.priority) === 'HIGH',
                      'bg-amber-50 text-amber-700 border-amber-200': formatPriority(issue.priority) === 'MEDIUM',
                      'bg-blue-50 text-blue-700 border-blue-200': formatPriority(issue.priority) === 'LOW',
                      'bg-gray-100 text-gray-700 border-gray-200': formatPriority(issue.priority) === 'UNCLASSIFIED'
                    }"
                  >
                    AI: {{ formatPriority(issue.priority) }}
                  </span>
                  <span
                    class="px-2.5 py-1 text-xs font-bold rounded-xl"
                    :class="{
                      'bg-amber-100 text-amber-800': issue.status === 'PENDING',
                      'bg-blue-100 text-blue-800': issue.status === 'IN_PROGRESS',
                      'bg-emerald-100 text-emerald-800': issue.status === 'RESOLVED'
                    }"
                  >
                    {{ issue.status }}
                  </span>
                </div>
              </div>

              <p class="text-gray-600 text-sm mb-4 leading-relaxed">{{ issue.description }}</p>

              <img
                v-if="issue.imageUrl"
                :src="`${API_BASE_URL}${issue.imageUrl}`"
                class="h-60 w-full rounded-xl object-cover border border-gray-200 mb-4"
              />

              <div class="mb-4 space-y-1.5">
                <div class="flex items-center justify-between text-[11px] font-semibold text-gray-500">
                  <span>
                    Stage:
                    <strong class="text-gray-800">
                      {{ issue.status === 'RESOLVED' ? 'Resolved' : issue.status === 'IN_PROGRESS' ? 'In Progress' : 'Pending Review' }}
                    </strong>
                  </span>
                  <span v-if="(issue.reportCount || 1) < 3 && issue.status === 'PENDING'" class="text-indigo-600">
                    {{ 3 - (issue.reportCount || 1) }} more confirmation(s) for auto-HIGH boost
                  </span>
                  <span v-else-if="(issue.reportCount || 1) >= 3" class="text-rose-600 font-bold">
                    Escalated by community confirmations
                  </span>
                </div>
                <div class="grid grid-cols-3 gap-1.5 h-1.5">
                  <div class="rounded-full bg-amber-400"></div>
                  <div class="rounded-full" :class="getStatusStep(issue.status) >= 2 ? 'bg-blue-500' : 'bg-gray-200'"></div>
                  <div class="rounded-full" :class="getStatusStep(issue.status) >= 3 ? 'bg-emerald-500' : 'bg-gray-200'"></div>
                </div>
              </div>

              <div class="flex items-center justify-between pt-3 border-t border-gray-100 gap-2 flex-wrap">
                <div class="flex items-center gap-3 text-xs text-gray-400">
                  <span>Report #{{ issue.id }}</span>
                  <button
                    type="button"
                    @click="openIssueModal(issue)"
                    class="text-blue-600 hover:text-blue-800 font-semibold"
                  >
                    View Details
                  </button>
                </div>

                <div class="flex items-center gap-2 flex-wrap">
                  <button
                    type="button"
                    @click="openEditModal(issue)"
                    class="px-3 py-1.5 rounded-xl text-xs font-bold bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200 transition-all"
                  >
                    Edit
                  </button>

                  <button
                    type="button"
                    :disabled="deletingId === issue.id"
                    @click="deleteIssue(issue.id)"
                    class="px-3 py-1.5 rounded-xl text-xs font-bold bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 transition-all disabled:opacity-50"
                  >
                    {{ deletingId === issue.id ? 'Deleting...' : 'Delete' }}
                  </button>

                  <button
                    type="button"
                    :disabled="confirmingId === issue.id"
                    @click="confirmIssue(issue.id)"
                    class="px-3.5 py-1.5 rounded-xl text-xs font-bold bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 transition-all disabled:opacity-50"
                  >
                    {{ confirmingId === issue.id ? 'Confirming...' : '+ Confirm / Me Too' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="lg:col-span-1 space-y-6">
        <div class="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 space-y-5">
          <h3 class="text-lg font-bold text-gray-900">Platform Analytics</h3>
          <div class="grid grid-cols-2 gap-3">
            <div class="p-4 bg-blue-50 rounded-2xl border border-blue-100 text-center">
              <span class="block text-2xl font-extrabold text-blue-700">{{ issues.length }}</span>
              <span class="text-xs font-semibold text-blue-600 uppercase tracking-wide">Total Reports</span>
            </div>
            <div class="p-4 bg-emerald-50 rounded-2xl border border-emerald-100 text-center">
              <span class="block text-2xl font-extrabold text-emerald-700">
                {{ issues.filter(i => i.status === 'RESOLVED').length }}
              </span>
              <span class="text-xs font-semibold text-emerald-600 uppercase tracking-wide">Resolved</span>
            </div>
          </div>

          <div class="space-y-3 pt-1">
            <div class="p-4 bg-gray-50 rounded-2xl border border-gray-100 text-sm text-gray-700">
              <span class="font-bold block mb-1 text-gray-900">AI Triage & Crowdsourcing</span>
              Reports confirmed by 3+ residents automatically escalate to HIGH priority so urgent issues get attention first.
            </div>
            <div class="p-4 bg-gray-50 rounded-2xl border border-gray-100 text-sm text-gray-700">
              <span class="font-bold block mb-1 text-gray-900">PostGIS Spatial Clustering</span>
              Nearby reports within 50 meters are grouped cleanly to keep the Moratuwa map readable.
            </div>
          </div>
        </div>

        <div class="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 space-y-4">
          <div class="flex items-center justify-between gap-2">
            <h3 class="text-lg font-bold text-gray-900">City Announcements</h3>
            <button
              type="button"
              @click="syncAnnouncementsFromStorage"
              class="text-[11px] font-bold px-2.5 py-0.5 rounded-full bg-amber-50 hover:bg-amber-100 text-amber-700 border border-amber-200 transition-colors"
            >
              Notices ({{ announcements.length }})
            </button>
          </div>

          <div v-if="announcements.length === 0" class="p-4 rounded-2xl border border-dashed border-gray-200 text-xs text-gray-500 text-center">
            No active city announcements right now. Admins can publish notices from the Admin page.
          </div>

          <div v-else class="space-y-3">
            <div
              v-for="item in announcements"
              :key="item.id"
              class="p-4 rounded-2xl border border-gray-100 bg-gray-50/70 space-y-1.5"
            >
              <div class="flex items-center justify-between text-[11px] font-bold">
                <span
                  class="px-2 py-0.5 rounded-md"
                  :class="{
                    'bg-rose-100 text-rose-800': item.tag === 'HIGH',
                    'bg-blue-100 text-blue-800': item.tag === 'MEDIUM',
                    'bg-emerald-100 text-emerald-800': item.tag === 'LOW'
                  }"
                >
                  {{ item.tag }}
                </span>
                <span class="text-gray-400">{{ item.date }}</span>
              </div>
              <div class="text-sm font-bold text-gray-900">{{ item.title }}</div>
              <p class="text-xs text-gray-600 leading-relaxed">{{ item.summary }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="selectedIssue"
      class="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4"
      @click.self="selectedIssue = null"
    >
      <div class="bg-white rounded-3xl max-w-xl w-full p-7 shadow-2xl border border-gray-100 space-y-4">
        <div class="flex justify-between items-start gap-4">
          <div>
            <span class="text-xs font-bold uppercase px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700">
              {{ selectedIssue.status }} · AI: {{ formatPriority(selectedIssue.priority) }}
            </span>
            <h2 class="text-2xl font-extrabold text-gray-900 mt-2">{{ selectedIssue.title }}</h2>
            <p class="text-xs text-gray-500 mt-0.5">
              {{ addressMap[selectedIssue.id] || 'Moratuwa Area' }} · Report #{{ selectedIssue.id }}
            </p>
          </div>
          <button
            type="button"
            @click="selectedIssue = null"
            class="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 text-gray-600 font-bold flex items-center justify-center"
          >
            &times;
          </button>
        </div>

        <img
          v-if="selectedIssue.imageUrl"
          :src="`${API_BASE_URL}${selectedIssue.imageUrl}`"
          class="h-64 w-full rounded-2xl object-cover border border-gray-200"
        />

        <p class="text-gray-700 text-sm leading-relaxed">{{ selectedIssue.description }}</p>

        <div class="bg-gray-50 rounded-2xl p-4 flex flex-wrap items-center justify-between gap-2 text-xs font-mono text-gray-600">
          <span>{{ selectedIssue.latitude.toFixed(5) }}, {{ selectedIssue.longitude.toFixed(5) }}</span>
          <div class="flex items-center gap-3 font-sans">
            <span class="font-bold text-indigo-700">{{ selectedIssue.reportCount || 1 }} Report(s)</span>
            <button
              type="button"
              @click="copyCoordinates(selectedIssue)"
              class="px-2.5 py-1 rounded-lg bg-white border border-gray-200 hover:bg-gray-100 text-gray-700 font-semibold"
            >
              {{ copiedGps ? 'Copied!' : 'Copy GPS' }}
            </button>
          </div>
        </div>

        <div class="flex items-center justify-between pt-2 gap-3 flex-wrap">
          <div class="flex items-center gap-2 flex-wrap">
            <button
              type="button"
              @click="openEditModal(selectedIssue)"
              class="px-3.5 py-2.5 rounded-xl bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200 font-bold text-sm"
            >
              Edit
            </button>
            <button
              type="button"
              :disabled="deletingId === selectedIssue.id"
              @click="deleteIssue(selectedIssue.id)"
              class="px-3.5 py-2.5 rounded-xl bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 font-bold text-sm disabled:opacity-50"
            >
              {{ deletingId === selectedIssue.id ? 'Deleting...' : 'Delete' }}
            </button>
            <button
              type="button"
              :disabled="confirmingId === selectedIssue.id"
              @click="confirmIssue(selectedIssue.id)"
              class="px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-sm disabled:opacity-50"
            >
              {{ confirmingId === selectedIssue.id ? 'Confirming...' : '+ Confirm / Me Too' }}
            </button>
          </div>
          <button
            type="button"
            @click="selectedIssue = null"
            class="bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold px-5 py-2.5 rounded-xl text-sm"
          >
            Close
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="editingIssue"
      class="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4"
      @click.self="editingIssue = null"
    >
      <div class="bg-white rounded-3xl max-w-lg w-full p-7 shadow-2xl border border-gray-100 space-y-4">
        <div class="flex justify-between items-center">
          <h2 class="text-xl font-extrabold text-gray-900">Edit Report #{{ editingIssue.id }}</h2>
          <button
            type="button"
            @click="editingIssue = null"
            class="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 text-gray-600 font-bold flex items-center justify-center"
          >
            &times;
          </button>
        </div>

        <form @submit.prevent="saveEditedIssue" class="space-y-4">
          <div>
            <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Issue Title</label>
            <input
              v-model="editForm.title"
              type="text"
              required
              class="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 outline-none"
            />
          </div>

          <div>
            <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Description</label>
            <textarea
              v-model="editForm.description"
              required
              rows="3"
              class="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 outline-none"
            ></textarea>
          </div>

          <p v-if="editError" class="text-xs text-rose-600">{{ editError }}</p>

          <div class="flex space-x-3 pt-2">
            <button
              type="button"
              @click="editingIssue = null"
              class="flex-1 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold py-3 rounded-xl text-sm"
            >
              Cancel
            </button>
            <button
              type="submit"
              :disabled="isSavingEdit"
              class="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl text-sm disabled:opacity-50"
            >
              {{ isSavingEdit ? 'Saving...' : 'Save Changes' }}
            </button>
          </div>
        </form>
      </div>
    </div>

    <div v-if="showReportModal" class="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-3xl max-w-lg w-full p-8 shadow-2xl border border-gray-100">
        <div class="flex justify-between items-center mb-6">
          <div>
            <h2 class="text-xl font-extrabold text-gray-900">Report Municipal Issue</h2>
            <p class="text-xs text-gray-500">Provide details for AI triage & PostGIS mapping</p>
          </div>
          <button
            type="button"
            @click="showReportModal = false"
            class="w-8 h-8 rounded-full bg-gray-100 hover:bg-gray-200 text-gray-500 font-bold flex items-center justify-center"
          >
            &times;
          </button>
        </div>

        <form @submit.prevent="submitIssue" class="space-y-4">
          <div>
            <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Category</label>
            <select
              v-model="newIssue.category"
              class="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 outline-none"
            >
              <option v-for="cat in categories.filter(c => c !== 'ALL')" :key="cat" :value="cat">
                {{ cat }}
              </option>
            </select>
          </div>
          <div>
            <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Issue Title</label>
            <input
              v-model="newIssue.title"
              type="text"
              required
              placeholder="e.g. Fallen Tree on Main Road"
              class="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 outline-none"
            />
          </div>
          <div>
            <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Description</label>
            <textarea
              v-model="newIssue.description"
              required
              rows="3"
              placeholder="Describe the hazard and impact..."
              class="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-3 text-sm focus:bg-white focus:ring-2 focus:ring-blue-500 outline-none"
            ></textarea>
          </div>
          <div>
            <label class="block text-xs font-bold text-gray-700 uppercase mb-1">Photo Evidence (Optional)</label>
            <input
              ref="imageInput"
              type="file"
              accept="image/*"
              @change="handleImageSelection"
              class="w-full text-xs text-gray-500 file:mr-4 file:py-2.5 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />
            <p v-if="imageError" class="text-xs text-rose-600 mt-1">{{ imageError }}</p>
          </div>
          <div>
            <button
              type="button"
              @click="useMyLocation"
              :disabled="isLocating"
              class="w-full border-2 border-dashed border-blue-300 bg-blue-50/50 hover:bg-blue-50 text-blue-700 font-bold py-3 rounded-xl transition-all text-sm"
            >
              {{ isLocating ? 'Detecting GPS...' : 'Detect My Current Location' }}
            </button>
            <p v-if="newIssue.latitude" class="text-xs text-emerald-600 mt-2 font-mono text-center font-bold">
              Location locked: {{ newIssue.latitude.toFixed(4) }}, {{ newIssue.longitude?.toFixed(4) }}
            </p>
            <p v-if="locationError" class="text-xs text-rose-600 mt-1 text-center">{{ locationError }}</p>
          </div>

          <div class="flex space-x-3 pt-4">
            <button
              type="button"
              @click="showReportModal = false"
              class="flex-1 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold py-3 rounded-xl text-sm"
            >
              Cancel
            </button>
            <button
              type="submit"
              :disabled="isSubmitting || newIssue.latitude === null"
              class="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl text-sm disabled:opacity-50 shadow-md"
            >
              {{ isSubmitting ? 'Analyzing & Saving...' : 'Submit Report' }}
            </button>
          </div>
          <p v-if="submitError" class="text-xs text-rose-600 text-center">{{ submitError }}</p>
        </form>
      </div>
    </div>

    <div
      v-if="isAdminLoginOpen"
      class="fixed inset-0 bg-black/50 backdrop-blur-xs flex items-center justify-center z-[60] p-4"
      @click.self="cancelAdminLogin"
    >
      <div class="bg-white rounded-3xl max-w-md w-full p-7 shadow-2xl border border-gray-100 space-y-5">
        <div>
          <h2 class="text-xl font-extrabold text-gray-900">Admin verification</h2>
          <p class="text-sm text-gray-500 mt-1">
            Admin credentials are required to edit or delete an issue. They are kept only for this page session.
          </p>
        </div>

        <form @submit.prevent="loginForAdminAction" class="space-y-4">
          <div>
            <label for="issue-admin-username" class="block text-xs font-bold text-gray-700 uppercase mb-1">
              Username
            </label>
            <input
              id="issue-admin-username"
              v-model="adminUsername"
              type="text"
              autocomplete="username"
              required
              class="w-full border border-gray-300 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
            />
          </div>
          <div>
            <label for="issue-admin-password" class="block text-xs font-bold text-gray-700 uppercase mb-1">
              Password
            </label>
            <input
              id="issue-admin-password"
              v-model="adminPassword"
              type="password"
              autocomplete="current-password"
              required
              class="w-full border border-gray-300 rounded-xl px-4 py-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
            />
          </div>

          <p v-if="adminLoginError" role="alert" class="text-xs text-rose-600">
            {{ adminLoginError }}
          </p>

          <div class="flex gap-3 pt-1">
            <button
              type="button"
              @click="cancelAdminLogin"
              class="flex-1 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold py-3 rounded-xl text-sm"
            >
              Cancel
            </button>
            <button
              type="submit"
              :disabled="isAuthenticatingAdmin"
              class="flex-1 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl text-sm disabled:opacity-50"
            >
              {{ isAuthenticatingAdmin ? 'Verifying...' : 'Continue' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>