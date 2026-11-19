<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import axios from 'axios'
import type { Issue } from '@/types/issue'

const router = useRouter()
const API_BASE_URL = 'http://localhost:8081'

const map = ref<L.Map | null>(null)
const markerLayer = ref<L.LayerGroup | null>(null)
const clickMarker = ref<L.CircleMarker | null>(null)

const issues = ref<Issue[]>([])
const selectedIssue = ref<Issue | null>(null)
const priorityFilter = ref('ALL')
const statusFilter = ref('ALL')
const showRadiusCircles = ref(true)
const addressMap = ref<Record<number, string>>({})
const confirmingId = ref<number | null>(null)
const clickedCoord = ref<{ lat: number; lng: number; address: string } | null>(null)

const zones = [
  { name: 'All Moratuwa', coords: [6.7950, 79.9000] as [number, number], zoom: 14 },
  { name: 'Katubedda / Campus', coords: [6.7969, 79.9018] as [number, number], zoom: 16 },
  { name: 'Molpe', coords: [6.7930, 79.9011] as [number, number], zoom: 16 },
  { name: 'Soysapura', coords: [6.8120, 79.8870] as [number, number], zoom: 15 }
]

const formatPriority = (priority?: string) => {
  if (!priority || priority.includes('ERROR')) return 'UNCLASSIFIED'
  return priority.toUpperCase()
}

const getMarkerColor = (issue: Issue) => {
  const p = formatPriority(issue.priority)
  if (p === 'HIGH') return '#e11d48'
  if (p === 'MEDIUM') return '#d97706'
  if (p === 'LOW') return '#2563eb'
  return '#4b5563'
}

const filteredIssues = computed(() => {
  return issues.value.filter(issue => {
    const p = formatPriority(issue.priority)
    const matchesPriority = priorityFilter.value === 'ALL' || p === priorityFilter.value
    const matchesStatus = statusFilter.value === 'ALL' || issue.status === statusFilter.value
    return matchesPriority && matchesStatus && issue.latitude != null && issue.longitude != null
  })
})

const flyToZone = (coords: [number, number], zoom: number) => {
  if (!map.value) return
  map.value.flyTo(coords, zoom, { duration: 1.1 })
}

const resolveAddress = async (lat: number, lon: number): Promise<string> => {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}`
    )
    const data = await res.json()
    const parts = (data.display_name || '')
      .split(',')
      .map((s: string) => s.trim())
      .filter(Boolean)
    return parts.slice(0, 3).join(', ') || 'Moratuwa Area'
  } catch {
    return 'Moratuwa Area'
  }
}

const renderMarkers = () => {
  if (!map.value || !markerLayer.value) return
  markerLayer.value.clearLayers()

  filteredIssues.value.forEach(issue => {
    const color = getMarkerColor(issue)
    const count = issue.reportCount || 1

    const marker = L.circleMarker([issue.latitude, issue.longitude], {
      radius: count > 1 ? 12 : 9,
      fillColor: color,
      color: '#ffffff',
      weight: 2.5,
      opacity: 1,
      fillOpacity: 0.95
    })

    marker.bindTooltip(
      `${issue.title} (${formatPriority(issue.priority)}, ${count} report(s))`,
      { direction: 'top', offset: [0, -8] }
    )

    marker.on('click', async () => {
      selectedIssue.value = issue
      clickedCoord.value = null
      if (clickMarker.value && map.value) {
        map.value.removeLayer(clickMarker.value)
        clickMarker.value = null
      }
      if (!addressMap.value[issue.id]) {
        addressMap.value[issue.id] = await resolveAddress(issue.latitude, issue.longitude)
      }
    })

    marker.addTo(markerLayer.value!)

    if (showRadiusCircles.value && (count > 1 || formatPriority(issue.priority) === 'HIGH')) {
      L.circle([issue.latitude, issue.longitude], {
        radius: 50,
        color,
        fillColor: color,
        fillOpacity: 0.12,
        weight: 1.5,
        dashArray: '4 4'
      }).addTo(markerLayer.value!)
    }
  })
}

const confirmSelectedIssue = async (id: number) => {
  confirmingId.value = id
  try {
    await axios.post(`${API_BASE_URL}/api/issues/${id}/confirm`)
    const res = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = res.data
    const updated = issues.value.find(i => i.id === id)
    if (updated) selectedIssue.value = updated
    renderMarkers()
  } catch (err) {
    console.error('Failed to confirm issue:', err)
  } finally {
    confirmingId.value = null
  }
}

const navigateToReportAtLocation = () => {
  if (!clickedCoord.value) return
  router.push({
    path: '/',
    query: {
      lat: clickedCoord.value.lat.toFixed(5),
      lng: clickedCoord.value.lng.toFixed(5)
    }
  })
}

watch([filteredIssues, showRadiusCircles], renderMarkers)

onMounted(async () => {
  map.value = L.map('map', { zoomControl: true }).setView([6.7950, 79.9000], 14)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map.value)

  markerLayer.value = L.layerGroup().addTo(map.value)

  map.value.on('click', async (e: L.LeafletMouseEvent) => {
    const { lat, lng } = e.latlng
    selectedIssue.value = null

    if (clickMarker.value && map.value) {
      map.value.removeLayer(clickMarker.value)
    }

    clickMarker.value = L.circleMarker([lat, lng], {
      radius: 8,
      fillColor: '#10b981',
      color: '#ffffff',
      weight: 2,
      opacity: 1,
      fillOpacity: 0.9
    }).addTo(map.value)

    clickedCoord.value = {
      lat,
      lng,
      address: 'Resolving address...'
    }

    clickedCoord.value.address = await resolveAddress(lat, lng)
  })

  try {
    const res = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = res.data
    renderMarkers()
  } catch (err) {
    console.error('Error fetching map issues:', err)
  }
})
</script>

<template>
  <div class="space-y-6">
    <div class="bg-gradient-to-r from-blue-900 via-indigo-900 to-slate-900 rounded-3xl p-8 text-white shadow-xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <span class="px-2.5 py-0.5 rounded-full text-xs font-bold uppercase bg-blue-500/20 text-blue-300 border border-blue-400/30 inline-block mb-2">
          Moratuwa Spatial Grid
        </span>
        <h1 class="text-3xl font-extrabold tracking-tight">Geospatial Intelligence Map</h1>
        <p class="text-blue-100 text-sm mt-1 max-w-2xl">
          Interactive Moratuwa map with 50m PostGIS clustering buffers, priority filters, and click-to-target coordinates.
        </p>
      </div>

      <div class="bg-white/10 backdrop-blur-md px-4 py-2 rounded-2xl border border-white/20 text-xs font-bold">
        Showing {{ filteredIssues.length }} of {{ issues.length }} Nodes
      </div>
    </div>

    <div class="bg-white p-4 rounded-2xl shadow-xs border border-gray-100 flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-1.5 flex-wrap">
        <span class="text-xs font-bold text-gray-400 uppercase tracking-wider mr-1">Jump to:</span>
        <button
          v-for="zone in zones"
          :key="zone.name"
          type="button"
          @click="flyToZone(zone.coords, zone.zoom)"
          class="px-3 py-1.5 text-xs font-bold rounded-xl bg-gray-50 hover:bg-blue-50 hover:text-blue-700 text-gray-700 border border-gray-200 transition-all"
        >
          {{ zone.name }}
        </button>
      </div>

      <div class="flex items-center gap-4 text-xs font-semibold text-gray-600">
        <span class="inline-flex items-center gap-1.5">
          <span class="w-3 h-3 rounded-full bg-rose-600 inline-block"></span>High
        </span>
        <span class="inline-flex items-center gap-1.5">
          <span class="w-3 h-3 rounded-full bg-amber-600 inline-block"></span>Medium
        </span>
        <span class="inline-flex items-center gap-1.5">
          <span class="w-3 h-3 rounded-full bg-blue-600 inline-block"></span>Low
        </span>
      </div>
    </div>

    <div class="bg-white p-4 rounded-2xl shadow-xs border border-gray-100 flex flex-wrap items-center gap-3">
      <select
        v-model="priorityFilter"
        class="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2 text-xs font-bold text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
      >
        <option value="ALL">All Priorities</option>
        <option value="HIGH">High Priority</option>
        <option value="MEDIUM">Medium Priority</option>
        <option value="LOW">Low Priority</option>
        <option value="UNCLASSIFIED">Unclassified</option>
      </select>

      <select
        v-model="statusFilter"
        class="bg-gray-50 border border-gray-200 rounded-xl px-3 py-2 text-xs font-bold text-gray-700 outline-none focus:ring-2 focus:ring-blue-500"
      >
        <option value="ALL">All Statuses</option>
        <option value="PENDING">Pending</option>
        <option value="IN_PROGRESS">In Progress</option>
        <option value="RESOLVED">Resolved</option>
      </select>

      <label class="inline-flex items-center gap-2 text-xs font-medium text-gray-700 cursor-pointer select-none px-2">
        <input
          type="checkbox"
          v-model="showRadiusCircles"
          class="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
        />
        <span>50m Cluster Zones</span>
      </label>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div class="lg:col-span-2 bg-white p-5 rounded-3xl shadow-sm border border-gray-100">
        <div id="map" class="w-full h-[580px] rounded-2xl border border-gray-200 z-0"></div>
        <p class="text-[11px] text-gray-400 mt-2 text-center">
          Click any issue marker to inspect details, or click anywhere on the map to target new coordinates.
        </p>
      </div>

      <div class="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 flex flex-col justify-between">
        <div v-if="selectedIssue" class="space-y-4">
          <div class="flex items-center justify-between gap-2">
            <span class="text-xs font-bold px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700">
              {{ formatPriority(selectedIssue.priority) }} · {{ selectedIssue.status }}
            </span>
            <span class="text-xs font-extrabold px-2.5 py-1 rounded-lg bg-indigo-50 text-indigo-700 border border-indigo-200">
              {{ selectedIssue.reportCount || 1 }} Report(s)
            </span>
          </div>

          <h2 class="text-xl font-extrabold text-gray-900">{{ selectedIssue.title }}</h2>

          <div class="text-xs text-gray-500">
            <strong>Area:</strong> {{ addressMap[selectedIssue.id] || 'Moratuwa Area' }}
          </div>

          <p class="text-sm text-gray-600 leading-relaxed">{{ selectedIssue.description }}</p>

          <img
            v-if="selectedIssue.imageUrl"
            :src="`${API_BASE_URL}${selectedIssue.imageUrl}`"
            class="w-full h-44 object-cover rounded-2xl border border-gray-200"
          />

          <div class="p-3.5 rounded-2xl bg-gray-50 text-xs font-mono text-gray-600 space-y-1">
            <div>GPS: {{ selectedIssue.latitude.toFixed(5) }}, {{ selectedIssue.longitude.toFixed(5) }}</div>
          </div>

          <div class="pt-2 space-y-2">
            <button
              type="button"
              :disabled="confirmingId === selectedIssue.id"
              @click="confirmSelectedIssue(selectedIssue.id)"
              class="w-full py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs shadow-xs transition-all disabled:opacity-50"
            >
              {{ confirmingId === selectedIssue.id ? 'Confirming...' : '+ Confirm / Me Too' }}
            </button>
            <a
              :href="`https://www.google.com/maps/dir/?api=1&destination=${selectedIssue.latitude},${selectedIssue.longitude}`"
              target="_blank"
              rel="noopener noreferrer"
              class="w-full py-2.5 rounded-xl bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold text-xs flex items-center justify-center transition-all"
            >
              Open in Google Maps
            </a>
          </div>
        </div>

        <div v-else-if="clickedCoord" class="space-y-4">
          <div class="flex items-center justify-between">
            <span class="text-xs font-bold px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-700 border border-emerald-200">
              Location Selected
            </span>
            <button
              type="button"
              @click="clickedCoord = null"
              class="text-xs text-gray-400 hover:text-gray-600 font-bold"
            >
              Clear
            </button>
          </div>

          <h3 class="text-lg font-bold text-gray-900">Pinned Coordinates</h3>
          <p class="text-xs text-gray-500">{{ clickedCoord.address }}</p>

          <div class="p-3.5 rounded-2xl bg-gray-50 text-xs font-mono text-gray-600 space-y-1">
            <div>Lat: {{ clickedCoord.lat.toFixed(5) }}</div>
            <div>Lng: {{ clickedCoord.lng.toFixed(5) }}</div>
          </div>

          <button
            type="button"
            @click="navigateToReportAtLocation"
            class="w-full py-3 rounded-2xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs shadow-md transition-all"
          >
            + Report Issue Here
          </button>
        </div>

        <div v-else class="py-20 text-center text-gray-400 space-y-2 my-auto">
          <p class="text-base font-bold text-gray-700">Select a Map Pin</p>
          <p class="text-xs max-w-xs mx-auto leading-relaxed text-gray-500">
            Click any issue node on the map to inspect details and confirm, or click any spot in Moratuwa to pick coordinates.
          </p>
        </div>
      </div>
    </div>
  </div>
</template>