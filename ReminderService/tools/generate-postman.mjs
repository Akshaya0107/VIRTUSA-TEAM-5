import fs from 'node:fs/promises'
const json = value => ({ mode: 'raw', raw: JSON.stringify(value, null, 2), options: { raw: { language: 'json' } } })
const records = []
function add(name, method, path, body, status = 200, tests = [], publicRequest = false) {
  const script = [`pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`, ...tests]
  records.push({ name, request: { method, header: [{ key: 'Content-Type', value: 'application/json' }], ...(body ? { body: json(body) } : {}), url: '{{baseUrl}}' + path, ...(publicRequest ? { auth: { type: 'noauth' } } : {}) }, event: [{ listen: 'test', script: { type: 'text/javascript', exec: script } }] })
}
add('Register', 'POST', '/api/auth/register', { email: '{{email}}', password: '{{password}}', displayName: 'Postman learner', timeZone: 'Asia/Kolkata' }, 201, ["pm.test('No password exposed',()=>pm.expect(pm.response.json()).not.to.have.property('passwordHash'))"], true)
add('Login', 'POST', '/api/auth/login', { email: '{{email}}', password: '{{password}}' }, 200, ["pm.collectionVariables.set('token',pm.response.json().accessToken)"], true)
add('Profile', 'GET', '/api/profile')
add('Update profile', 'PUT', '/api/profile', { displayName: 'Updated learner', timeZone: 'Asia/Kolkata' })
const body = { title: 'Postman reminder', recipientEmail: 'capture@example.test', recipientName: 'Learner', message: 'API collection fixture', scheduledAt: '{{scheduledAt}}', timeZone: 'Asia/Kolkata' }
add('Create reminder', 'POST', '/api/reminders', body, 201, ["const r=pm.response.json();pm.collectionVariables.set('reminderId',r.id);pm.collectionVariables.set('version',r.version)"])
add('List reminders', 'GET', '/api/reminders?page=0&size=20')
add('Get reminder', 'GET', '/api/reminders/{{reminderId}}')
add('Update reminder', 'PUT', '/api/reminders/{{reminderId}}', { ...body, title: 'Updated Postman reminder', version: '{{version}}' }, 200, ["pm.collectionVariables.set('version',pm.response.json().version)"])
add('Dashboard', 'GET', '/api/dashboard')
add('All email history', 'GET', '/api/reminders/history')
add('Reminder email history', 'GET', '/api/email-logs/{{reminderId}}')
add('Cancel reminder', 'POST', '/api/reminders/{{reminderId}}/cancel')
add('Delete reminder', 'DELETE', '/api/reminders/{{reminderId}}', null, 204)
add('Logout all sessions', 'POST', '/api/auth/logout', null, 204)
add('Revoked token rejected', 'GET', '/api/profile', null, 401)
const collection = { info: { name: 'Smart Reminder Service', schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json', description: 'Local fixture collection. Register/login creates unique test accounts and never includes real SMTP/database credentials. SMTP scheduling is tested separately by tools/verify-live.mjs.' }, auth: { type: 'bearer', bearer: [{ key: 'token', value: '{{token}}', type: 'string' }] }, variable: [{ key: 'baseUrl', value: 'http://localhost:8088' }], event: [{ listen: 'prerequest', script: { type: 'text/javascript', exec: ["if(pm.info.requestName==='Register'){pm.collectionVariables.set('email','postman-'+Date.now()+'@example.test');pm.collectionVariables.set('password','LocalCollectionTest-42!');pm.collectionVariables.set('scheduledAt',new Date(Date.now()+3600000).toISOString());}"] } }], item: records }
await fs.mkdir('documentation/postman', { recursive: true })
await fs.writeFile('documentation/postman/SmartReminder.postman_collection.json', JSON.stringify(collection, null, 2))
console.log('Generated Postman collection with 15 requests and assertions.')
