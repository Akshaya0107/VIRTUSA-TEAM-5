import { spawn } from 'node:child_process'
import { randomUUID, randomBytes } from 'node:crypto'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const base = 'http://127.0.0.1:8088', password = randomBytes(24).toString('base64url'), email = `restart-${randomUUID()}@example.test`
async function call(method, path, body, token) {
  const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) })
  assert.ok(response.ok, `${path}: ${response.status}`); const text = await response.text(); return text ? JSON.parse(text) : null
}
await call('POST', '/api/auth/register', { email, password, displayName: 'Restart fixture', timeZone: 'UTC' })
const token = (await call('POST', '/api/auth/login', { email, password })).accessToken
const reminder = await call('POST', '/api/reminders', { title: 'Restart persistence fixture', recipientEmail: 'capture@example.test', message: 'No mail: this fixture is cancelled after restart.', scheduledAt: new Date(Date.now() + 3600000).toISOString(), timeZone: 'UTC' }, token)
await new Promise((resolve, reject) => { const child = spawn('docker', ['compose', 'restart', 'backend'], { stdio: 'pipe' }); child.on('error', reject); child.on('exit', code => code === 0 ? resolve() : reject(new Error(`Docker restart returned ${code}`))) })
let ready = false
for (let i = 0; i < 60; i++) { await new Promise(r => setTimeout(r, 1000)); try { ready = (await fetch(base + '/api/health')).ok; if (ready) break } catch {} }
assert.ok(ready, 'Backend did not become ready')
const restored = await call('GET', `/api/reminders/${reminder.id}`, null, token)
assert.equal(restored.id, reminder.id); assert.equal(restored.status, 'PENDING')
await call('POST', `/api/reminders/${reminder.id}/cancel`, null, token)
await call('DELETE', `/api/reminders/${reminder.id}`, null, token)
await call('POST', '/api/auth/logout', null, token)
const report = { checkedAt: new Date().toISOString(), backendRestarted: true, reminderPersisted: true, existingJwtStillValid: true, fixtureCancelledAndSoftDeleted: true }
await fs.writeFile('documentation/verification/restart.json', JSON.stringify(report, null, 2)); console.log(JSON.stringify(report, null, 2))
