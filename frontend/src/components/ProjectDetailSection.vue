<script setup lang="ts">
defineProps<{ plain?: boolean; title: string; bordered?: boolean }>()
</script>

<template>
  <section v-if="plain" class="detail-section">
    <h3>{{ title }}</h3>
    <div class="detail-section-body"><slot /></div>
  </section>
  <a-card v-else :title="title" :bordered="bordered"><slot /></a-card>
</template>

<style scoped>
.detail-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: #172033;
}
.detail-section-body {
  font-size: 14px;
  line-height: 1.85;
  color: #475569;
  overflow-wrap: anywhere;
}
</style>
