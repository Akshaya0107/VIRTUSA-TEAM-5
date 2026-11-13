import { Temporal } from '@js-temporal/polyfill'
export const browserZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
export function localToInstant(value, zone) {
  try { return Temporal.PlainDateTime.from(value).toZonedDateTime(zone, { disambiguation: 'reject' }).toInstant().toString() }
  catch { throw new Error('Choose a valid time and IANA time zone. Times skipped or repeated during daylight-saving changes must be changed to an unambiguous time.') }
}
export function localInput(value, zone) { return Temporal.Instant.from(value).toZonedDateTimeISO(zone).toPlainDateTime().toString({ smallestUnit: 'minute' }) }
export function formatTime(value, zone = browserZone()) {
  if (!value) return '—'
  try { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: zone }).format(new Date(value)) }
  catch { return value }
}
export function shortDate(value, zone) { return formatTime(value, zone) }
