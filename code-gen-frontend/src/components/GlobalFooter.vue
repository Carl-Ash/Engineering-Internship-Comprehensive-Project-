<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  background: var(--bg-card, #efefef);
  text-align: center;
  padding: 20px;
}

.footer-content {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}
</style>