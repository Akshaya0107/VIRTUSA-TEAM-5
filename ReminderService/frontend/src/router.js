import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from './stores/auth.js'
const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true, title: 'Welcome back' } },
  { path: '/register', component: () => import('./views/RegisterView.vue'), meta: { public: true, title: 'Create account' } },
  { path: '/dashboard', component: () => import('./views/DashboardView.vue'), meta: { title: 'Overview' } },
  { path: '/reminders', component: () => import('./views/ReminderListView.vue'), meta: { title: 'My reminders' } },
  { path: '/reminders/new', component: () => import('./views/CreateReminderView.vue'), meta: { title: 'Create reminder' } },
  { path: '/reminders/:id/edit', component: () => import('./views/EditReminderView.vue'), meta: { title: 'Edit reminder' } },
  { path: '/history', component: () => import('./views/HistoryView.vue'), meta: { title: 'Email history' } },
  { path: '/settings', component: () => import('./views/SettingsView.vue'), meta: { title: 'Profile & settings' } },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]
const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })
router.beforeEach(to => {
  const auth = useAuth()
  if (!to.meta.public && !auth.signedIn) { auth.clear(); return { path: '/login', query: { redirect: to.fullPath } } }
  if (to.meta.public && auth.signedIn) return '/dashboard'
})
export default router
