<template>
  <footer class="footer">
    <div class="footer-inner">
      <span>AI 应用生成平台</span>
      <span class="footer-divider"></span>
      <span>&copy; {{ new Date().getFullYear() }}</span>
    </div>
  </footer>
</template>

<script setup lang="ts">
</script>

<style scoped>
.footer {
  background: var(--bg-header);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid var(--border-color);
  padding: 20px 24px;
}

.footer-inner {
  max-width: 1280px;
  margin: 0 auto;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.footer-divider {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--text-secondary);
}
</style>
