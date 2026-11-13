<script setup>
defineProps({ page: Number, totalPages: Number, totalElements: Number, busy: Boolean })
defineEmits(['change'])
</script>
<template><div class="pagination"><span>{{ totalElements || 0 }} records · Page {{ (page || 0) + 1 }} of {{ Math.max(totalPages || 0, 1) }}</span><div><button class="button ghost small" :disabled="busy || !page" @click="$emit('change', page - 1)">Previous</button><button class="button ghost small" :disabled="busy || page + 1 >= totalPages" @click="$emit('change', page + 1)">Next</button></div></div></template>
