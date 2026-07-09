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
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-top: 1px solid var(--border-color);
  padding: 18px 24px;
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
  font-weight: 500;
}

.footer-divider {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  opacity: 0.6;
}
</style>
