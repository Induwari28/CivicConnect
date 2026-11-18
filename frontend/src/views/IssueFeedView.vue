<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'

const issues = ref([])
const loading = ref(true)
const isSubmitting = ref(false)

// Form state with default coordinates set near the campus
const newIssue = ref({
  title: '',
  description: '',
  latitude: 6.7969, 
  longitude: 79.9018
})

const fetchIssues = async () => {
  try {
    const response = await axios.get('http://localhost:8081/api/issues')
    // Reverse the array so the newest issues appear at the top
    issues.value = response.data.reverse()
  } catch (error) {
    console.error("Error fetching issues:", error)
  } finally {
    loading.value = false
  }
}

const submitIssue = async () => {
  isSubmitting.value = true
  try {
    await axios.post('http://localhost:8081/api/issues', newIssue.value)
    // Clear the text fields but keep the coordinates
    newIssue.value.title = ''
    newIssue.value.description = ''
    // Refresh the feed immediately to show the new post
    await fetchIssues()
  } catch (error) {
    console.error("Error submitting issue:", error)
  } finally {
    isSubmitting.value = false
  }
}

onMounted(() => {
  fetchIssues()
})
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
    
    <!-- Report Form Section (Left) -->
    <div class="md:col-span-1 bg-white p-6 rounded-lg shadow-sm border border-gray-200 h-fit">
      <h2 class="text-xl font-bold text-gray-800 mb-4">Report an Issue</h2>
      <form @submit.prevent="submitIssue" class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Title</label>
          <input v-model="newIssue.title" type="text" required placeholder="e.g. Fallen Tree"
                 class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent">
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <textarea v-model="newIssue.description" required rows="3" placeholder="Describe the problem..."
                    class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"></textarea>
        </div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Latitude</label>
            <input v-model.number="newIssue.latitude" type="number" step="any" required 
                   class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent">
          </div>
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Longitude</label>
            <input v-model.number="newIssue.longitude" type="number" step="any" required 
                   class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent">
          </div>
        </div>
        <button type="submit" :disabled="isSubmitting" 
                class="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded-md transition-colors disabled:opacity-50">
          {{ isSubmitting ? 'Submitting...' : 'Submit Report' }}
        </button>
      </form>
    </div>

    <!-- Live Feed Section (Right) -->
    <div class="md:col-span-2 bg-white p-6 rounded-lg shadow-sm border border-gray-200">
      <h2 class="text-2xl font-bold text-gray-800 mb-6">Live Issue Feed</h2>
      
      <div v-if="loading" class="text-gray-500 font-medium">Loading citizen reports...</div>
      <div v-else-if="issues.length === 0" class="text-gray-500 font-medium">No issues reported yet.</div>
      
      <div v-else class="space-y-4">
        <div v-for="issue in issues" :key="issue.id" class="p-4 border border-gray-100 rounded-md bg-gray-50 hover:shadow-md transition-shadow">
          <div class="flex justify-between items-start mb-2">
            <h3 class="text-lg font-semibold text-blue-700">{{ issue.title }}</h3>
            <span class="px-2 py-1 text-xs font-bold rounded-full" 
                  :class="issue.status === 'PENDING' ? 'bg-yellow-100 text-yellow-800' : 'bg-green-100 text-green-800'">
              {{ issue.status }}
            </span>
          </div>
          <p class="text-gray-600 text-sm mb-3">{{ issue.description }}</p>
          <p class="text-xs text-gray-400 font-mono">Location: {{ issue.latitude }}, {{ issue.longitude }}</p>
        </div>
      </div>
    </div>

  </div>
</template>