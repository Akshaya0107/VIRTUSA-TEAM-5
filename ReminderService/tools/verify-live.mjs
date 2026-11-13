// Use only against a local test deployment with a capture SMTP server.
import assert from 'node:assert/strict'
import { randomUUID, randomBytes } from 'node:crypto'
import fs from 'node:fs/promises'
const base = process.env.BASE_URL || 'http://127.0.0.1:18080'
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(base)) throw new Error('This fixture runner is limited to local HTTP deployments.')
const run = randomUUID(), password = randomBytes(24).toString('base64url'), email = `qa-${run}@example.test`
const checks = []
async function request(method, path, body, token, expected = 200) {
  const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) })
  const text = await response.text(); const data = text ? JSON.parse(text) : null
  assert.equal(response.status, expected, `${method} ${path}: expected ${expected}, got ${response.status}; ${data?.message || ''}`)
  checks.push(`${method} ${path} → ${expected}`); return data
}
const register = address => request('POST', '/api/auth/register', { email: address, password, displayName: 'QA verification', timeZone: 'Asia/Kolkata' }, null, 201)
await register(email)
const login = await request('POST', '/api/auth/login', { email, password })
const token = login.accessToken
await request('GET', '/api/profile', null, token)
await request('PUT', '/api/profile', { displayName: 'QA updated', timeZone: 'Asia/Kolkata' }, token)
await request('GET', '/api/reminders', null, null, 401)
await register(`other-${run}@example.test`)
const other = (await request('POST', '/api/auth/login', { email: `other-${run}@example.test`, password })).accessToken
const body = { title: `Live verification ${run}`, recipientEmail: 'capture@example.test', recipientName: 'QA fixture', message: 'Local capture test. No external delivery intended.', scheduledAt: new Date(Date.now() + 3600000).toISOString(), timeZone: 'Asia/Kolkata' }
let reminder = await request('POST', '/api/reminders', body, token, 201)
const id = reminder.id
await request('GET', `/api/reminders/${id}`, null, token)
await request('GET', `/api/reminders/${id}`, null, other, 404)
reminder = await request('PUT', `/api/reminders/${id}`, { ...body, title: 'Updated QA reminder', version: reminder.version }, token)
await request('PUT', `/api/reminders/${id}`, { ...body, version: 0 }, token, 409)
await request('POST', `/api/reminders/${id}/cancel`, null, token)
await request('DELETE', `/api/reminders/${id}`, null, token, 204)
await request('GET', `/api/reminders/${id}`, null, token, 404)
const sending = await request('POST', '/api/reminders', { ...body, scheduledAt: new Date(Date.now() + 10000).toISOString() }, token, 201)
let sent
const deadline = Date.now() + 70000
do {
  await new Promise(resolve => setTimeout(resolve, 1000))
  sent = await request('GET', `/api/reminders/${sending.id}`, null, token)
  if (['FAILED', 'CANCELLED'].includes(sent.status)) throw new Error(`Scheduled send ended as ${sent.status}; inspect safe email history.`)
} while (sent.status !== 'SENT' && Date.now() < deadline)
assert.equal(sent.status, 'SENT', 'Scheduler did not complete the reminder before the deadline')
const history = await request('GET', `/api/email-logs/${sending.id}`, null, token)
assert.equal(history.totalElements, 1); assert.equal(history.content[0].status, 'SMTP_ACCEPTED')
await request('GET', `/api/email-logs/${sending.id}`, null, other, 404)
await new Promise(resolve => setTimeout(resolve, 4000))
assert.equal((await request('GET', `/api/email-logs/${sending.id}`, null, token)).totalElements, 1)
await request('GET', '/api/reminders/history', null, token)
const dashboard = await request('GET', '/api/dashboard', null, token); assert.equal(dashboard.sent, 1)
await request('POST', '/api/auth/logout', null, token, 204)
await request('GET', '/api/profile', null, token, 401)
const result = { checkedAt: new Date().toISOString(), baseUrl: base, schedulerStatus: sent.status, attemptCount: sent.attemptCount, logOutcome: history.content[0].status, checkCount: checks.length, checks }
await fs.mkdir('documentation/verification', { recursive: true })
const name = base.endsWith(':18080') ? 'live-demo.json' : 'live-compose.json'
await fs.writeFile(`documentation/verification/${name}`, JSON.stringify(result, null, 2))
console.log(JSON.stringify({ ...result, checks: undefined }, null, 2))
