<script setup>
defineProps({ status: String })
const labels = { PENDING: 'Pending', PROCESSING: 'Processing', SENT: 'SMTP accepted', RETRY: 'Retry scheduled', FAILED: 'Failed', CANCELLED: 'Cancelled', SMTP_ACCEPTED: 'SMTP accepted', STARTED: 'Started', UNKNOWN: 'Needs review' }
</script>
<template><span class="badge" :class="status?.toLowerCase()">{{ labels[status] || status }}</span></template>
