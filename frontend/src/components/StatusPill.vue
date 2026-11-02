<script setup lang="ts">
defineProps<{ kind: string; label: string }>()
</script>

<template>
  <span class="pill" :data-kind="kind">{{ label }}</span>
</template>
