<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Navigation Bar -->
    <nav class="bg-blue-700 text-white shadow-md">
      <div class="max-w-6xl mx-auto px-4 py-3 flex justify-between items-center gap-4 flex-wrap">
        <RouterLink to="/" class="text-xl font-bold tracking-wide">CivicConnect</RouterLink>
        <div class="flex flex-wrap gap-4 md:gap-6 text-sm">
          <RouterLink to="/" class="hover:text-blue-200 transition-colors" active-class="font-semibold underline underline-offset-4">Home</RouterLink>
          <RouterLink to="/feed" class="hover:text-blue-200 transition-colors" active-class="font-semibold underline underline-offset-4">Issue Feed</RouterLink>
          <RouterLink to="/map" class="hover:text-blue-200 transition-colors" active-class="font-semibold underline underline-offset-4">Smart Map</RouterLink>
          <RouterLink to="/analytics" class="hover:text-blue-200 transition-colors" active-class="font-semibold underline underline-offset-4">Analytics</RouterLink>
          <RouterLink to="/services" class="hover:text-blue-200 transition-colors" active-class="font-semibold underline underline-offset-4">Services</RouterLink>
          <RouterLink to="/my-reports" class="hover:text-blue-200 transition-colors" active-class="font-semibold underline underline-offset-4">My Reports</RouterLink>
          <RouterLink to="/admin" class="hover:text-blue-200 transition-colors" active-class="font-semibold underline underline-offset-4">Admin</RouterLink>
        </div>
      </div>
    </nav>

    <!-- Main Content Area -->
    <main class="max-w-6xl mx-auto px-4 py-8">
      <RouterView />
    </main>
  </div>
</template>