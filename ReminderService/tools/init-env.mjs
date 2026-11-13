import fs from 'node:fs/promises'
import { randomBytes } from 'node:crypto'
import { fileURLToPath } from 'node:url'
const root = new URL('../', import.meta.url), destination = new URL('.env', root)
const template = await fs.readFile(new URL('.env.example', root), 'utf8')
const text = template.replace('replace-with-generated-root-password', randomBytes(48).toString('base64')).replace('replace-with-generated-app-password', randomBytes(48).toString('base64')).replace('replace-with-generated-signing-secret', randomBytes(48).toString('base64'))
await fs.writeFile(destination, text, { flag: 'wx', mode: 0o600 })
console.log('Generated local .env without displaying secrets. Existing files are never overwritten.')
