// Local Compose-only check. Temporarily uses an unreachable SMTP port; restores defaults.
import { spawn } from 'node:child_process'
import { randomBytes, randomUUID } from 'node:crypto'
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'
const base='http://127.0.0.1:8088', password=randomBytes(24).toString('base64url'), email=`failure-${randomUUID()}@example.test`
async function compose(env) { await new Promise((resolve,reject)=>{const child=spawn('docker',['compose','up','-d','--no-deps','--force-recreate','backend'],{env,stdio:'pipe'});child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(new Error(`Compose returned ${code}`)))}) }
async function ready() {for(let i=0;i<90;i++){await new Promise(r=>setTimeout(r,1000));try {if((await fetch(base+'/api/health')).ok)return}catch{}}throw new Error('Backend readiness timeout')}
async function call(method,path,body,token){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});assert.ok(r.ok,`${path}: ${r.status}`);return r.status===204?null:r.json()}
let result
try {
  await compose({...process.env,SMTP_HOST:'127.0.0.1',SMTP_PORT:'19999',MAX_RETRIES:'1',RETRY_BASE_SECONDS:'1',SCHEDULER_POLL_MS:'1000'});await ready()
  await call('POST','/api/auth/register',{email,password,displayName:'Failure fixture',timeZone:'UTC'})
  const token=(await call('POST','/api/auth/login',{email,password})).accessToken
  const reminder=await call('POST','/api/reminders',{title:'SMTP refusal fixture',recipientEmail:'capture@example.test',message:'This is expected to fail before SMTP submission.',scheduledAt:new Date(Date.now()+5000).toISOString(),timeZone:'UTC'},token)
  let state
  for(let i=0;i<45;i++){await new Promise(r=>setTimeout(r,1000));state=await call('GET',`/api/reminders/${reminder.id}`,null,token);if(state.status==='FAILED')break}
  assert.equal(state.status,'FAILED');assert.equal(state.attemptCount,2);assert.equal(state.retryCount,1)
  const logs=await call('GET',`/api/email-logs/${reminder.id}`,null,token);assert.equal(logs.totalElements,2);assert.ok(logs.content.every(l=>l.status==='FAILED'))
  await call('POST','/api/auth/logout',null,token)
  result={checkedAt:new Date().toISOString(),database:'MySQL in Compose',smtpFailure:'Connection refused before submission',maxRetries:1,attemptCount:state.attemptCount,retryCount:state.retryCount,finalStatus:state.status,failedLogs:logs.totalElements}
} finally {await compose(process.env);await ready()}
result.defaultConfigurationRestored=true
await fs.writeFile('documentation/verification/smtp-failure.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2))
