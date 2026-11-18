<script setup lang="ts">
import { ref } from 'vue'
import axios from 'axios'
import type { Issue, IssueStatus } from '@/types/issue'

interface AdminCredentials {
  username: string
  password: string
}

const credentialsForm = ref<AdminCredentials>({ username: '', password: '' })
const credentials = ref<AdminCredentials | null>(null)
const issues = ref<Issue[]>([])
const statusDrafts = ref<Record<number, IssueStatus>>({})
const isLoading = ref(false)
const updatingIssueId = ref<number | null>(null)
const loginError = ref('')
const updateMessage = ref('')
const updateError = ref('')

const signIn = async () => {
  isLoading.value = true
  loginError.value = ''
  updateMessage.value = ''

  try {
    const response = await axios.get<Issue[]>('http://localhost:8081/api/issues/admin', {
      auth: credentialsForm.value
    })
    issues.value = response.data
    statusDrafts.value = {}
    for (const issue of issues.value) {
      statusDrafts.value[issue.id] = issue.status
    }
    credentials.value = { ...credentialsForm.value }
    credentialsForm.value.password = ''
  } catch (error) {
    console.error('Error loading admin issues:', error)
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      loginError.value = 'The admin username or password was not accepted.'
    } else if (axios.isAxiosError(error) && error.response?.status === 403) {
      loginError.value = 'This account is not authorized to access the admin dashboard.'
    } else {
      loginError.value = 'Unable to load the admin dashboard. Please try again.'
    }
  } finally {
    isLoading.value = false
  }
}

const signOut = () => {
  credentials.value = null
  credentialsForm.value.password = ''
  issues.value = []
  statusDrafts.value = {}
  loginError.value = ''
  updateError.value = ''
  updateMessage.value = ''
}

const updateStatus = async (issue: Issue) => {
  const selectedStatus = statusDrafts.value[issue.id]
  if (!credentials.value || !selectedStatus) return

  updatingIssueId.value = issue.id
  updateError.value = ''
  updateMessage.value = ''

  try {
    const response = await axios.patch<Issue>(
      `http://localhost:8081/api/issues/${issue.id}/status`,
      { status: selectedStatus },
      { auth: credentials.value }
    )
    const issueIndex = issues.value.findIndex((currentIssue) => currentIssue.id === issue.id)
    if (issueIndex !== -1) {
      issues.value[issueIndex] = response.data
    }
    statusDrafts.value[issue.id] = response.data.status
    updateMessage.value = `Updated "${response.data.title}" to ${response.data.status.replace('_', ' ')}.`
  } catch (error) {
    console.error('Error updating issue status:', error)
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const message = 'Your admin credentials were rejected. Sign in again.'
      signOut()
      loginError.value = message
    } else if (axios.isAxiosError(error) && error.response?.status === 404) {
      updateError.value = 'This issue no longer exists. Sign out and back in to reload the reports.'
    } else {
      updateError.value = 'Unable to update the issue status. Please try again.'
    }
  } finally {
    updatingIssueId.value = null
  }
}
</script>

<template>
  <section class="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
    <div class="flex flex-wrap justify-between items-start gap-4 mb-6">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">Admin Dashboard</h2>
        <p class="text-gray-600 mt-1">Review reports and update their status.</p>
      </div>
      <button
        v-if="credentials"
        type="button"
        class="border border-gray-300 text-gray-700 hover:bg-gray-50 font-semibold py-2 px-4 rounded-md"
        @click="signOut"
      >
        Sign out
      </button>
    </div>

    <form v-if="!credentials" class="max-w-md space-y-4" @submit.prevent="signIn">
      <div>
        <label for="admin-username" class="block text-sm font-medium text-gray-700 mb-1">Admin username</label>
        <input
          id="admin-username"
          v-model="credentialsForm.username"
          type="text"
          autocomplete="username"
          required
          class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
      </div>
      <div>
        <label for="admin-password" class="block text-sm font-medium text-gray-700 mb-1">Admin password</label>
        <input
          id="admin-password"
          v-model="credentialsForm.password"
          type="password"
          autocomplete="current-password"
          required
          class="w-full border border-gray-300 rounded-md px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
      </div>
      <p v-if="loginError" role="alert" class="text-sm text-red-700">{{ loginError }}</p>
      <button
        type="submit"
        :disabled="isLoading"
        class="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded-md disabled:opacity-50"
      >
        {{ isLoading ? 'Signing in...' : 'Sign in' }}
      </button>
    </form>

    <div v-else>
      <p v-if="issues.length === 0" class="text-gray-500">No issue reports to display.</p>
      <div v-else class="space-y-4">
        <article
          v-for="issue in issues"
          :key="issue.id"
          class="border border-gray-200 rounded-md p-4 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4"
        >
          <div class="min-w-0">
            <h3 class="font-semibold text-gray-900">{{ issue.title }}</h3>
            <p class="text-sm text-gray-600 mt-1">{{ issue.description }}</p>
            <img
              v-if="issue.imageUrl"
              :src="`http://localhost:8081${issue.imageUrl}`"
              :alt="`Photo attached to ${issue.title}`"
              loading="lazy"
              class="mt-3 max-h-48 rounded-md object-cover"
            >
            <p class="text-xs text-gray-500 mt-2">
              Current status: {{ issue.status.replace('_', ' ') }} · Priority: {{ issue.priority }}
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-2 shrink-0">
            <label :for="`status-${issue.id}`" class="sr-only">New status for {{ issue.title }}</label>
            <select
              :id="`status-${issue.id}`"
              v-model="statusDrafts[issue.id]"
              class="border border-gray-300 rounded-md px-3 py-2"
            >
              <option value="PENDING">Pending</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="RESOLVED">Resolved</option>
            </select>
            <button
              type="button"
              :disabled="updatingIssueId === issue.id || statusDrafts[issue.id] === issue.status"
              class="bg-blue-600 hover:bg-blue-700 text-white font-semibold py-2 px-3 rounded-md disabled:opacity-50"
              @click="updateStatus(issue)"
            >
              {{ updatingIssueId === issue.id ? 'Saving...' : 'Save status' }}
            </button>
          </div>
        </article>
      </div>

      <p v-if="updateMessage" role="status" class="mt-4 text-sm text-green-700">{{ updateMessage }}</p>
      <p v-if="updateError && credentials" role="alert" class="mt-4 text-sm text-red-700">{{ updateError }}</p>
    </div>
  </section>
</template>
