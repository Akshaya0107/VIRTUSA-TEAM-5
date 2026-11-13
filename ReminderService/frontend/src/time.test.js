import { describe, expect, it } from 'vitest'
import { localInput, localToInstant } from './time.js'
describe('time-zone conversion', () => {
  it('stores an India local time as the correct UTC instant', () => expect(localToInstant('2099-01-01T09:00', 'Asia/Kolkata')).toBe('2099-01-01T03:30:00Z'))
  it('round-trips an instant for editing', () => expect(localInput('2099-01-01T03:30:00Z', 'Asia/Kolkata')).toBe('2099-01-01T09:00'))
  it('rejects nonexistent and ambiguous daylight-saving times', () => {
    expect(() => localToInstant('2026-03-08T02:30', 'America/New_York')).toThrow()
    expect(() => localToInstant('2026-11-01T01:30', 'America/New_York')).toThrow()
  })
})
