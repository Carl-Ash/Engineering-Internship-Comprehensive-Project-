<template>
  <div class="page-container" :style="{ maxWidth: maxWidth + 'px' }">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  maxWidth?: number
}>(), {
  maxWidth: 1280,
})
</script>

<style scoped>
.page-container {
  margin: 24px auto;
  padding: 28px;
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-color);
}
</style>
