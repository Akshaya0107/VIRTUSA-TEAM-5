import { defineStore } from 'pinia'
import { api } from '../api.js'
function savedUser() { try { return JSON.parse(sessionStorage.getItem('user') || 'null') } catch { return null } }
export const useAuth = defineStore('auth', {
  state: () => ({ user: savedUser(), token: sessionStorage.getItem('accessToken'), expiresAt: sessionStorage.getItem('expiresAt') }),
  getters: { signedIn: s => Boolean(s.token && s.expiresAt && Date.parse(s.expiresAt) > Date.now()) },
  actions: {
    async login(form) {
      const { data } = await api.post('/auth/login', form)
      this.token = data.accessToken; this.expiresAt = data.expiresAt; this.setUser(data.user)
      sessionStorage.setItem('accessToken', data.accessToken); sessionStorage.setItem('expiresAt', data.expiresAt)
    },
    setUser(user) { this.user = user; sessionStorage.setItem('user', JSON.stringify(user)) },
    clear() { this.user = null; this.token = null; this.expiresAt = null; ['user', 'accessToken', 'expiresAt'].forEach(k => sessionStorage.removeItem(k)) },
    async logout() { try { await api.post('/auth/logout') } finally { this.clear() } }
  }
})
