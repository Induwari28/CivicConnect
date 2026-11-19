<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const departments = [
  {
    name: 'Roads & Drainage Department',
    icon: '🛣️',
    description: 'Maintenance of municipal roads, clearing stormwater drains, flood mitigation, and pothole patchworks.',
    leadTime: '24–48 Hours',
    hotline: '+94 11 264 5221',
    category: 'Roads & Drainage'
  },
  {
    name: 'Electrical & Street Lighting',
    icon: '💡',
    description: 'Repairs of faulty streetlamps, power outages on major public corridors, and traffic signal upkeep.',
    leadTime: '12–24 Hours',
    hotline: '+94 11 264 5222',
    category: 'Streetlights & Power'
  },
  {
    name: 'Solid Waste & Sanitation',
    icon: '🚛',
    description: 'Household garbage collection schedules, public dumping clearing, and commercial waste inspections.',
    leadTime: 'Daily Route',
    hotline: '+94 11 264 5223',
    category: 'Waste & Sanitation'
  },
  {
    name: 'Public Health & Environment',
    icon: '🌿',
    description: 'Dengue vector inspection, canal debris cleaning, public park upkeep, and noise/environmental complaints.',
    leadTime: '2–3 Days',
    hotline: '+94 11 264 5224',
    category: 'Public Safety'
  }
]

const openCategoryInFeed = (category: string) => {
  router.push({ path: '/', query: { category } })
}
</script>

<template>
  <div class="space-y-6">
    <div class="bg-gradient-to-r from-blue-900 via-indigo-900 to-emerald-800 rounded-3xl p-8 md:p-10 text-white shadow-xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
      <div>
        <span class="px-3 py-1 bg-emerald-500/20 border border-emerald-400/30 text-emerald-300 rounded-full text-xs font-bold tracking-wide uppercase mb-3 inline-block">
          Moratuwa Municipal Council
        </span>
        <h1 class="text-3xl md:text-4xl font-extrabold tracking-tight mb-2">Municipal Public Services</h1>
        <p class="text-blue-100 text-sm md:text-base max-w-2xl">
          Directory of municipal council departments, service level commitments, and active field dispatch lines.
        </p>
      </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div
        v-for="dept in departments"
        :key="dept.name"
        class="bg-white p-7 rounded-3xl border border-gray-100 shadow-sm hover:shadow-md transition-all flex flex-col justify-between"
      >
        <div>
          <div class="flex items-center justify-between mb-4">
            <span class="text-3xl p-3 bg-gray-50 rounded-2xl border border-gray-100">{{ dept.icon }}</span>
            <span class="text-xs font-bold px-3 py-1 rounded-full bg-blue-50 text-blue-700 border border-blue-100">
              Avg SLA: {{ dept.leadTime }}
            </span>
          </div>

          <h3 class="text-xl font-bold text-gray-900 mb-2">{{ dept.name }}</h3>
          <p class="text-sm text-gray-600 leading-relaxed mb-6">{{ dept.description }}</p>
        </div>

        <div class="pt-4 border-t border-gray-100 flex items-center justify-between gap-3 flex-wrap">
          <div class="text-xs font-mono text-gray-500">
            Hotline: <strong class="text-gray-800">{{ dept.hotline }}</strong>
          </div>
          <button
            type="button"
            @click="openCategoryInFeed(dept.category)"
            class="px-4 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-700 text-white transition-all shadow-xs"
          >
            View {{ dept.category }} Issues →
          </button>
        </div>
      </div>
    </div>

    <div class="bg-white p-6 rounded-3xl border border-gray-100 shadow-xs flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
      <div>
        <h4 class="font-extrabold text-gray-900 text-base">Notice a Public Safety Hazard?</h4>
        <p class="text-xs text-gray-500 mt-0.5">Crowdsource reports via CivicConnect to alert field response units directly.</p>
      </div>
      <router-link
        to="/"
        class="px-5 py-2.5 rounded-xl text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white transition-all shadow-xs whitespace-nowrap"
      >
        Go to Live Feed & Report
      </router-link>
    </div>
  </div>
</template>