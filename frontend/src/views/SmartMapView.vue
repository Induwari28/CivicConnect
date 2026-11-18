<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted } from 'vue'
import axios from 'axios'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Issue } from '@/types/issue'

// Fix for Leaflet marker icons not loading correctly in Vite/Vue projects
import icon from 'leaflet/dist/images/marker-icon.png'
import iconShadow from 'leaflet/dist/images/marker-shadow.png'
const DefaultIcon = L.icon({
    iconUrl: icon,
    shadowUrl: iconShadow,
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    popupAnchor: [1, -34]
})
L.Marker.prototype.options.icon = DefaultIcon

const mapContainer = ref<HTMLDivElement | null>(null)
const issues = ref<Issue[]>([])
const loading = ref(true)
const loadError = ref('')
let map: L.Map | null = null
let markersLayer: L.LayerGroup | null = null

const isValidLocation = (issue: Issue) =>
  Number.isFinite(issue.latitude) &&
  Number.isFinite(issue.longitude) &&
  issue.latitude >= -90 &&
  issue.latitude <= 90 &&
  issue.longitude >= -180 &&
  issue.longitude <= 180

const createPopup = (issue: Issue) => {
  const content = document.createElement('div')
  const title = document.createElement('strong')
  title.className = 'block text-blue-700 text-lg'
  title.textContent = issue.title

  const description = document.createElement('p')
  description.className = 'my-2 text-gray-600'
  description.textContent = issue.description

  const details = document.createElement('p')
  details.className = 'text-sm text-gray-700'
  details.textContent = `Status: ${issue.status} · Priority: ${issue.priority}`

  content.append(title, description, details)
  return content
}

const fetchIssues = async () => {
  const currentMap = map
  const currentMarkersLayer = markersLayer
  if (!currentMarkersLayer || !currentMap) return

  loading.value = true
  loadError.value = ''
  try {
    const response = await axios.get<Issue[]>('http://localhost:8081/api/issues')
    issues.value = response.data.filter(isValidLocation)
    currentMarkersLayer.clearLayers()

    issues.value.forEach((issue) => {
      L.marker([issue.latitude, issue.longitude])
        .bindPopup(createPopup(issue))
        .addTo(currentMarkersLayer)
    })

    if (issues.value.length > 0) {
      const bounds = L.latLngBounds(
        issues.value.map((issue) => L.latLng(issue.latitude, issue.longitude))
      )
      currentMap.fitBounds(bounds, { padding: [24, 24], maxZoom: 14 })
    }
  } catch (error) {
    console.error('Error fetching map data:', error)
    loadError.value = 'Unable to load reported issues. Please try again.'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!mapContainer.value) return

  map = L.map(mapContainer.value).setView([6.7969, 79.9018], 14)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map)
  markersLayer = L.layerGroup().addTo(map)

  void fetchIssues()
})

onBeforeUnmount(() => {
  map?.remove()
  map = null
  markersLayer = null
})
</script>

<template>
  <div class="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
    <h2 class="text-2xl font-bold text-gray-800 mb-4">Geospatial Intelligence Map</h2>
    <p class="text-gray-600 mb-4">Interactive view of all reported civic issues.</p>
    <div class="mb-3 min-h-6">
      <p v-if="loading" role="status" class="text-sm text-gray-500">Loading reported issues...</p>
      <div v-else-if="loadError" role="alert" class="flex items-center gap-3 text-sm text-red-700">
        <span>{{ loadError }}</span>
        <button type="button" class="font-semibold underline" @click="fetchIssues">Retry</button>
      </div>
      <p v-else-if="issues.length === 0" role="status" class="text-sm text-gray-500">
        No issues with valid locations have been reported yet.
      </p>
      <p v-else role="status" class="text-sm text-gray-500">
        Showing {{ issues.length }} reported {{ issues.length === 1 ? 'issue' : 'issues' }}.
      </p>
    </div>
    <div
      ref="mapContainer"
      role="region"
      aria-label="Map of civic issues"
      class="w-full h-[600px] rounded-md border border-gray-300 z-0"
    ></div>
  </div>
</template>

<style scoped>
/* Ensures the map stays below the Vue Router navigation bar */
.leaflet-container {
  z-index: 1;
}
</style>