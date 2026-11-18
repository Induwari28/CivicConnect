<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

const issues = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const response = await axios.get('http://localhost:8081/api/issues')
    issues.value = response.data
  } catch (error) {
    console.error("Error fetching issues:", error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
    <h2 class="text-2xl font-bold text-gray-800 mb-6">Live Issue Feed</h2>
    
    <div v-if="loading" class="text-gray-500">Loading citizen reports...</div>
    
    <div v-else-if="issues.length === 0" class="text-gray-500">No issues reported yet.</div>
    
    <div v-else class="space-y-4">
      <div v-for="issue in issues" :key="issue.id" class="p-4 border border-gray-100 rounded-md bg-gray-50 hover:bg-gray-100 transition-colors">
        <div class="flex justify-between items-start mb-2">
          <h3 class="text-lg font-semibold text-blue-700">{{ issue.title }}</h3>
          <span class="px-2 py-1 text-xs font-bold rounded-full" 
                :class="issue.status === 'PENDING' ? 'bg-yellow-100 text-yellow-800' : 'bg-green-100 text-green-800'">
            {{ issue.status }}
          </span>
        </div>
        <p class="text-gray-600 text-sm mb-2">{{ issue.description }}</p>
        <p class="text-xs text-gray-400 font-mono">Location: {{ issue.latitude }}, {{ issue.longitude }}</p>
      </div>
    </div>
  </div>
</template>