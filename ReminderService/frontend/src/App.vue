<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from './stores/auth.js'
const auth = useAuth(), route = useRoute(), router = useRouter(), menu = ref(false)
const mobileQuery = window.matchMedia('(max-width: 650px)'), mobile = ref(mobileQuery.matches)
function resized(event) { mobile.value = event.matches; if (!event.matches) menu.value = false }
const links = [{ to: '/dashboard', icon: '◫', label: 'Overview' }, { to: '/reminders', icon: '◷', label: 'My reminders' }, { to: '/history', icon: '↗', label: 'Email history' }, { to: '/settings', icon: '⚙', label: 'Settings' }]
async function logout() { try { await auth.logout() } catch { /* Local session is still cleared on connection failure. */ } finally { router.replace('/login') } }
function expired() { auth.clear(); router.replace('/login') }
onMounted(() => { window.addEventListener('session-expired', expired); mobileQuery.addEventListener('change', resized) })
onUnmounted(() => { window.removeEventListener('session-expired', expired); mobileQuery.removeEventListener('change', resized) })
</script>
<template>
  <RouterView v-if="route.meta.public" />
  <div v-else class="app-shell">
    <aside id="workspace-navigation" class="sidebar" :class="{ open: menu }" :inert="mobile && !menu">
      <RouterLink to="/dashboard" class="brand"><span class="brand-mark">s<span>•</span></span><span>smart<span class="brand-light">reminder</span></span></RouterLink>
      <p class="nav-label">WORKSPACE</p>
      <nav aria-label="Main navigation"><RouterLink v-for="link in links" :key="link.to" :to="link.to" @click="menu = false"><span class="nav-icon">{{ link.icon }}</span>{{ link.label }}</RouterLink></nav>
      <div class="sidebar-note"><span class="note-icon">✦</span><strong>A little ahead of life.</strong><p>Make room for what matters. We’ll remember the rest.</p><RouterLink to="/reminders/new" class="button sidebar-button">+ New reminder</RouterLink></div>
      <div class="sidebar-bottom"><span class="avatar">{{ auth.user?.displayName?.slice(0, 1).toUpperCase() || 'S' }}</span><div><strong>{{ auth.user?.displayName }}</strong><small>Your personal workspace</small></div><button class="icon-button" aria-label="Sign out" title="Sign out on all devices" @click="logout">↪</button></div>
    </aside>
    <div v-if="menu" class="mobile-scrim" @click="menu = false"></div>
    <div class="workspace"><header class="topbar"><div class="breadcrumb"><button class="icon-button mobile-toggle" aria-label="Toggle navigation" aria-controls="workspace-navigation" :aria-expanded="menu" @click="menu = !menu">☰</button><span>Workspace</span><span class="divider">/</span><strong>{{ route.meta.title }}</strong></div><RouterLink to="/settings" class="top-profile"><span class="live-dot"></span> Personal account <span class="avatar small-avatar">{{ auth.user?.displayName?.slice(0, 1) }}</span></RouterLink></header><main class="page"><RouterView /></main><footer class="footer">Smart Reminder <span>Thoughtfully timed. Reliably recorded.</span></footer></div>
  </div>
</template>

