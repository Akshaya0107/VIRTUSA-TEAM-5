import axios from 'axios'
export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api', timeout: 20000 })
api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use(r => r, e => {
  if (e.response?.status === 401 && !e.config?.url?.startsWith('/auth/')) window.dispatchEvent(new Event('session-expired'))
  return Promise.reject(e)
})
export function errorMessage(e) {
  const data = e.response?.data
  const fields = Object.values(data?.fields || {})
  return fields.length ? fields.join('. ') : data?.message || (e.response ? 'The request could not be completed.' : 'Could not reach the server. Check your connection and try again.')
}
