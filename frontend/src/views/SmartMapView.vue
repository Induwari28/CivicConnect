<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

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

const mapContainer = ref(null)

onMounted(async () => {
  // 1. Initialize map centered near University of Moratuwa
  const map = L.map(mapContainer.value).setView([6.7969, 79.9018], 14)

  // 2. Load the OpenStreetMap tiles
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map)

  // 3. Fetch the spatial data from Spring Boot
  try {
    const response = await axios.get('http://localhost:8081/api/issues')
    const issues = response.data

    // 4. Plot each issue as a marker on the map
    issues.forEach(issue => {
      const marker = L.marker([issue.latitude, issue.longitude]).addTo(map)
      
      // Add a popup bubble when you click the marker
      marker.bindPopup(`
        <div class="font-sans">
          <strong class="text-blue-700 text-lg">${issue.title}</strong><br/>
          <span class="text-gray-600">${issue.description}</span><br/><br/>
          <span class="bg-yellow-100 text-yellow-800 px-2 py-1 text-xs font-bold rounded-full border border-yellow-200">
            Status: ${issue.status}
          </span>
        </div>
      `)
    })
  } catch (error) {
    console.error("Error fetching map data:", error)
  }
})
</script>

<template>
  <div class="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
    <h2 class="text-2xl font-bold text-gray-800 mb-4">Geospatial Intelligence Map</h2>
    <p class="text-gray-600 mb-4">Interactive view of all reported civic issues.</p>
    
    <!-- This div holds the Leaflet map -->
    <div ref="mapContainer" class="w-full h-[600px] rounded-md border border-gray-300 z-0"></div>
  </div>
</template>

<style scoped>
/* Ensures the map stays below the Vue Router navigation bar */
.leaflet-container {
  z-index: 1;
}
</style>