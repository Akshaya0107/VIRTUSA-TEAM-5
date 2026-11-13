import fs from 'node:fs/promises'
const file = 'documentation/verification/postman-report.private.json'
const report = JSON.parse(await fs.readFile(file, 'utf8'))
const summary = { checkedAt: new Date().toISOString(), runner: 'Newman (Postman collection)', stats: report.run.stats, failureCount: report.run.failures.length, requests: report.run.executions.map(e => ({ name: e.item.name, status: e.response?.code, assertions: e.assertions?.map(a => ({ name: a.assertion, passed: !a.error })) })) }
await fs.writeFile('documentation/verification/postman-summary.json', JSON.stringify(summary, null, 2))
await fs.unlink(file)
console.log(`Saved sanitized Postman summary: ${report.run.stats.requests.total} requests, ${report.run.stats.assertions.total} assertions, ${summary.failureCount} failures. Removed raw test-token report.`)
