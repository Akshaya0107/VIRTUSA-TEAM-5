<script setup>import { useRoute } from 'vue-router'; import ReminderEditor from '../components/ReminderEditor.vue'; const route = useRoute()</script>
<template><ReminderEditor :key="route.params.id" :id="route.params.id" /></template>
