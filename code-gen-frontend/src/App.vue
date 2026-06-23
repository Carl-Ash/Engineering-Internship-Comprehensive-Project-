<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
</script>

<template>
  <BasicLayout />
</template>

<style>
:root {
  --bg-page: #f8fafc;
  --bg-card: #ffffff;
  --bg-header: rgba(255, 255, 255, 0.85);
  --text-color: #1e293b;
  --text-secondary: #94a3b8;
  --border-color: #e2e8f0;
  --primary-color: #3b82f6;
  --primary-color-rgb: 59, 130, 246;
  --primary-gradient: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  --hero-gradient-start: rgba(59, 130, 246, 0.15);
  --hero-gradient-mid: rgba(139, 92, 246, 0.12);
  --hero-gradient-end: rgba(16, 185, 129, 0.08);
}

* {
  box-sizing: border-box;
}

html {
  overflow: hidden;
  height: 100%;
}

body {
  margin: 0;
  padding: 0;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell',
    'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  overflow: hidden;
  height: 100%;
  background: var(--bg-page);
  color: var(--text-color);
}

::selection {
  background: rgba(var(--primary-color-rgb), 0.2);
  color: #1e40af;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: var(--text-secondary);
}
</style>
