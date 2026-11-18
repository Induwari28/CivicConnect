<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'
import type { Issue } from '@/types/issue'

const API_BASE_URL = 'http://localhost:8081'
const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])

const issues = ref<Issue[]>([])
const loading = ref(true)
const isSubmitting = ref(false)
const isLocating = ref(false)
const locationError = ref('')
const selectedImage = ref<File | null>(null)
const imageError = ref('')
const submitError = ref('')
const imageInput = ref<HTMLInputElement | null>(null)

const newIssue = ref<{
  title: string
  description: string
  latitude: number | null
  longitude: number | null
}>({
  title: '',
  description: '',
  latitude: null,
  longitude: null
})

const useMyLocation = () => {
  locationError.value = ''

  if (!navigator.geolocation) {
    locationError.value = 'Geolocation is not supported by this browser.'
    return
  }

  isLocating.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      newIssue.value.latitude = position.coords.latitude
      newIssue.value.longitude = position.coords.longitude
      isLocating.value = false
    },
    (error) => {
      switch (error.code) {
        case GeolocationPositionError.PERMISSION_DENIED:
          locationError.value = 'Location permission was denied. Allow location access and try again.'
          break
        case GeolocationPositionError.POSITION_UNAVAILABLE:
          locationError.value = 'Your location is currently unavailable. Please try again.'
          break
        case GeolocationPositionError.TIMEOUT:
          locationError.value = 'The location request timed out. Please try again.'
          break
        default:
          locationError.value = 'Unable to get your location. Please try again.'
      }
      isLocating.value = false
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
  )
}

const handleImageSelection = (event: Event) => {
  const input = event.currentTarget
  if (!(input instanceof HTMLInputElement)) return

  const image = input.files?.[0] ?? null
  imageError.value = ''
  selectedImage.value = null

  if (image && image.size > MAX_IMAGE_SIZE_BYTES) {
    imageError.value = 'Choose an image that is 5 MB or smaller.'
    input.value = ''
    return
  }
  if (image?.type && !ALLOWED_IMAGE_TYPES.has(image.type)) {
    imageError.value = 'Choose a JPEG, PNG, GIF, or WebP image.'
    input.value = ''
    return
  }

  selectedImage.value = image
}

const fetchIssues = async () => {
  try {
    const response = await axios.get<Issue[]>(`${API_BASE_URL}/api/issues`)
    issues.value = response.data.reverse()
  } catch (error) {
    console.error("Error fetching issues:", error)
  } finally {
    loading.value = false
  }
}

const submitIssue = async () => {
  const { latitude, longitude } = newIssue.value
  if (latitude === null || longitude === null) return

  isSubmitting.value = true
  submitError.value = ''
  try {
    const issuePayload = {
      title: newIssue.value.title,
      description: newIssue.value.description,
      latitude,
      longitude
    }

    if (selectedImage.value) {
      const formData = new FormData()
      Object.entries(issuePayload).forEach(([key, value]) => formData.append(key, value.toString()))
      formData.append('image', selectedImage.value)
      await axios.post(`${API_BASE_URL}/api/issues`, formData)
    } else {
      await axios.post(`${API_BASE_URL}/api/issues`, issuePayload)
    }
    newIssue.value.title = ''
    newIssue.value.description = ''
    selectedImage.value = null
    imageError.value = ''
    if (imageInput.value) imageInput.value.value = ''
    await fetchIssues()
  } catch (error) {
    console.error("Error submitting issue:", error)
    if (axios.isAxiosError(error) && error.response?.status === 413) {
      submitError.value = 'The selected image exceeds the 5 MB upload limit.'
    } else if (axios.isAxiosError(error) && error.response?.status === 400) {
      submitError.value = 'The report or image was rejected. Check the selected file and required fields.'
    } else if (axios.isAxiosError(error) && error.response?.status === 415) {
      submitError.value = 'The backend rejected the upload format. Restart it to load the multipart upload endpoint.'
    } else {
      submitError.value = 'Unable to submit this report. Please try again.'
    }
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
        <div>
          <label for="issue-image" class="block text-sm font-medium text-gray-700 mb-1">Photo (optional)</label>
          <input
            id="issue-image"
            ref="imageInput"
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp"
            :disabled="isSubmitting"
            @change="handleImageSelection"
            class="w-full text-sm text-gray-600 file:mr-3 file:rounded-md file:border-0 file:bg-blue-50 file:px-3 file:py-2 file:font-semibold file:text-blue-700 hover:file:bg-blue-100"
          >
          <p v-if="imageError" role="alert" class="mt-1 text-sm text-red-600">{{ imageError }}</p>
          <p v-else-if="selectedImage" class="mt-1 text-sm text-gray-500">
            {{ selectedImage.name }} · {{ (selectedImage.size / 1024 / 1024).toFixed(2) }} MB
          </p>
          <p v-else class="mt-1 text-xs text-gray-500">JPEG, PNG, GIF, or WebP; up to 5 MB.</p>
        </div>
        <div>
          <button type="button" @click="useMyLocation" :disabled="isLocating"
                  class="w-full border border-blue-600 text-blue-700 hover:bg-blue-50 font-semibold py-2 px-4 rounded-md transition-colors disabled:opacity-50">
            {{ isLocating ? 'Getting Location...' : 'Use My Location' }}
          </button>
          <p v-if="locationError" role="alert" class="mt-2 text-sm text-red-600">{{ locationError }}</p>
          <p v-else-if="newIssue.latitude !== null && newIssue.longitude !== null" role="status" class="mt-2 text-sm text-gray-500">
            Location set: {{ newIssue.latitude }}, {{ newIssue.longitude }}
          </p>
          <p v-else class="mt-2 text-sm text-gray-500">Set your location before submitting the report.</p>
        </div>
        <button type="submit" :disabled="isSubmitting || isLocating || newIssue.latitude === null || newIssue.longitude === null"
                class="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded-md transition-colors disabled:opacity-50">
          {{ isSubmitting ? 'Submitting...' : 'Submit Report' }}
        </button>
        <p v-if="submitError" role="alert" class="text-sm text-red-600">{{ submitError }}</p>
      </form>
    </div>

    <div class="md:col-span-2 bg-white p-6 rounded-lg shadow-sm border border-gray-200">
      <h2 class="text-2xl font-bold text-gray-800 mb-6">Live Issue Feed</h2>
      
      <div v-if="loading" class="text-gray-500 font-medium">Loading citizen reports...</div>
      <div v-else-if="issues.length === 0" class="text-gray-500 font-medium">No issues reported yet.</div>
      
      <div v-else class="space-y-4">
        <div v-for="issue in issues" :key="issue.id" class="p-4 border border-gray-100 rounded-md bg-gray-50 hover:shadow-md transition-shadow">
          <div class="flex justify-between items-start mb-2">
            <h3 class="text-lg font-semibold text-blue-700">{{ issue.title }}</h3>
            <div class="space-x-2">
              <span class="px-2 py-1 text-xs font-bold rounded-full"
                    :class="issue.status === 'PENDING' ? 'bg-yellow-100 text-yellow-800' : 'bg-green-100 text-green-800'">
                {{ issue.status }}
              </span>
              <!-- New Dynamic AI Priority Badge -->
              <span class="px-2 py-1 text-xs font-bold rounded-full border"
                    :class="{
                      'bg-red-100 text-red-800 border-red-200': issue.priority === 'HIGH',
                      'bg-orange-100 text-orange-800 border-orange-200': issue.priority === 'MEDIUM',
                      'bg-blue-100 text-blue-800 border-blue-200': issue.priority === 'LOW',
                      'bg-gray-100 text-gray-800 border-gray-200': issue.priority === 'UNCLASSIFIED'
                    }">
                {{ issue.priority }}
              </span>
            </div>
          </div>
          <p class="text-gray-600 text-sm mb-3">{{ issue.description }}</p>
          <img
            v-if="issue.imageUrl"
            :src="`${API_BASE_URL}${issue.imageUrl}`"
            :alt="`Photo attached to ${issue.title}`"
            loading="lazy"
            class="mb-3 max-h-64 w-full rounded-md object-cover"
          >
          <p class="text-xs text-gray-400 font-mono">Location: {{ issue.latitude }}, {{ issue.longitude }}</p>
        </div>
      </div>
    </div>

  </div>
</template>