<script setup lang="ts">
defineProps<{ current: number }>()

const steps = ['登录', '选任务', '提交申请', '录取', '入学', '周报结项']
</script>

<template>
  <ol class="steps" aria-label="演示路径">
    <li
      v-for="(label, index) in steps"
      :key="label"
      :class="{ done: index + 1 < current, current: index + 1 === current }"
    >
      <span>{{ index + 1 }}</span>{{ label }}
    </li>
  </ol>
</template>
