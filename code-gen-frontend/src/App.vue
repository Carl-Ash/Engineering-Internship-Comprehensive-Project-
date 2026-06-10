<script setup lang="ts">
import { computed } from 'vue'
import BasicLayout from '@/layouts/BasicLayout.vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

const themeConfig = computed(() => ({
  token: {
    colorPrimary: themeStore.primaryColor,
  },
}))
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <BasicLayout />
  </a-config-provider>
</template>

<style>
/* ===== CSS 变量默认值 ===== */
:root {
  --bg-page: #f0f2f5;
  --bg-card: #ffffff;
  --bg-header: #ffffff;
  --text-color: #1a1a1a;
  --text-secondary: #8c8c8c;
  --border-color: #f0f0f0;
  --primary-color: #1890ff;
}

body {
  margin: 0;
  background: var(--bg-page);
  color: var(--text-color);
  transition: background 0.3s, color 0.3s;
}

/* ===== 暗夜黑主题覆盖 ===== */
html.theme-dark {
  --border-color: #30363d;

  /* Card */
  .ant-card {
    background: var(--bg-card) !important;
    border-color: var(--border-color) !important;
  }
  .ant-card-head {
    border-color: var(--border-color) !important;
    color: var(--text-color) !important;
  }

  /* Table */
  .ant-table {
    background: var(--bg-card) !important;
    color: var(--text-color) !important;
  }
  .ant-table-thead > tr > th {
    background: #1c2333 !important;
    color: var(--text-color) !important;
    border-color: var(--border-color) !important;
  }
  .ant-table-tbody > tr > td {
    border-color: var(--border-color) !important;
  }
  .ant-table-tbody > tr:hover > td {
    background: #1c2333 !important;
  }
  .ant-table-tbody > tr.ant-table-row-selected > td {
    background: #1c2333 !important;
  }
  .ant-table-placeholder:hover > td {
    background: var(--bg-card) !important;
  }

  /* Table pagination */
  .ant-pagination-item,
  .ant-pagination-prev .ant-pagination-item-link,
  .ant-pagination-next .ant-pagination-item-link,
  .ant-pagination-options .ant-select-selector {
    background: var(--bg-card) !important;
    border-color: var(--border-color) !important;
    color: var(--text-color) !important;
  }
  .ant-pagination-item a {
    color: var(--text-color) !important;
  }
  .ant-pagination-item-active {
    border-color: var(--primary-color) !important;
  }
  .ant-pagination-item-active a {
    color: var(--primary-color) !important;
  }
  .ant-pagination-options .ant-select-selection-item {
    color: var(--text-color) !important;
  }
  .ant-pagination-options .ant-select-arrow {
    color: var(--text-secondary) !important;
  }

  /* Input */
  .ant-input,
  .ant-input-affix-wrapper {
    background: #0d1117 !important;
    border-color: var(--border-color) !important;
    color: var(--text-color) !important;
  }
  .ant-input::placeholder {
    color: var(--text-secondary) !important;
  }
  .ant-input-affix-wrapper .ant-input {
    background: transparent !important;
  }
  .ant-input-prefix,
  .ant-input-suffix {
    color: var(--text-secondary) !important;
  }

  /* Select */
  .ant-select-selector {
    background: #0d1117 !important;
    border-color: var(--border-color) !important;
  }
  .ant-select-selection-item,
  .ant-select-selection-placeholder {
    color: var(--text-color) !important;
  }
  .ant-select-arrow {
    color: var(--text-secondary) !important;
  }
  .ant-select-dropdown {
    background: #1c2333 !important;
  }
  .ant-select-item {
    color: var(--text-color) !important;
  }
  .ant-select-item-option-active {
    background: #30363d !important;
  }
  .ant-select-item-option-selected {
    background: var(--primary-color) + '22' !important;
    font-weight: 500 !important;
  }

  /* Modal */
  .ant-modal-content {
    background: var(--bg-card) !important;
  }
  .ant-modal-header {
    background: var(--bg-card) !important;
    border-color: var(--border-color) !important;
  }
  .ant-modal-title {
    color: var(--text-color) !important;
  }
  .ant-modal-footer {
    border-color: var(--border-color) !important;
  }
  .ant-modal-close {
    color: var(--text-secondary) !important;
  }

  /* Form */
  .ant-form-item-label > label {
    color: var(--text-color) !important;
  }

  /* Descriptions */
  .ant-descriptions {
    color: var(--text-color) !important;
  }
  .ant-descriptions-item-label {
    color: var(--text-secondary) !important;
  }
  .ant-descriptions-item-content {
    color: var(--text-color) !important;
  }

  /* Divider */
  .ant-divider {
    border-color: var(--border-color) !important;
  }
  .ant-divider-vertical {
    border-color: var(--border-color) !important;
  }

  /* Tag */
  .ant-tag {
    border-color: transparent !important;
  }

  /* Menu */
  .ant-menu {
    color: var(--text-color) !important;
  }
  .ant-menu-horizontal {
    border-color: var(--border-color) !important;
  }

  /* Dropdown */
  .ant-dropdown-menu {
    background: var(--bg-card) !important;
  }
  .ant-dropdown-menu-item {
    color: var(--text-color) !important;
  }
  .ant-dropdown-menu-item:hover {
    background: #30363d !important;
  }
  .ant-dropdown-menu-item-divider {
    background: var(--border-color) !important;
  }

  /* Popover / Popconfirm */
  .ant-popover-inner {
    background: var(--bg-card) !important;
  }
  .ant-popover-title {
    color: var(--text-color) !important;
    border-color: var(--border-color) !important;
  }
  .ant-popover-inner-content {
    color: var(--text-color) !important;
  }
  .ant-popover-arrow-content {
    background: var(--bg-card) !important;
  }

  /* Image placeholder */
  .ant-image-placeholder {
    background: var(--bg-card) !important;
  }

  /* Empty */
  .ant-empty-description {
    color: var(--text-secondary) !important;
  }

  /* Button default */
  .ant-btn:not(.ant-btn-primary):not(.ant-btn-dangerous) {
    background: var(--bg-card) !important;
    border-color: var(--border-color) !important;
    color: var(--text-color) !important;
  }
  .ant-btn:not(.ant-btn-primary):not(.ant-btn-dangerous):hover {
    border-color: var(--primary-color) !important;
    color: var(--primary-color) !important;
  }
  .ant-btn-link {
    color: var(--primary-color) !important;
  }

  /* Tooltip */
  .ant-tooltip-inner {
    background: #1c2333 !important;
  }
  .ant-tooltip-arrow-content {
    background: #1c2333 !important;
  }

  /* Alert */
  .ant-alert {
    background: var(--bg-card) !important;
    border-color: var(--border-color) !important;
  }

  /* Textarea */
  textarea.ant-input {
    background: #0d1117 !important;
  }

  /* Result */
  .ant-result-title,
  .ant-result-subtitle {
    color: var(--text-color) !important;
  }

  /* ===== 登录/注册页 ===== */
  .login-card {
    background: linear-gradient(145deg, #1c2333 0%, #0d1117 100%) !important;
    border-color: var(--border-color) !important;
  }
  .login-card::before {
    display: none !important;
  }
  .login-card .title {
    color: var(--text-color) !important;
  }
  .login-card .desc,
  .login-card .footer-tips,
  .login-card .tips-text {
    color: var(--text-secondary) !important;
  }
  .login-card .footer-tips {
    border-color: var(--border-color) !important;
  }
  .login-card .site-form-item-icon {
    color: var(--text-secondary) !important;
  }
  .login-card .register-link {
    color: var(--primary-color) !important;
  }
}

/* ===== 暖阳主题微调 ===== */
html.theme-warm {
  .ant-table-thead > tr > th {
    background: #f5f0ea !important;
  }
  .ant-table-tbody > tr:hover > td {
    background: #fdf8f0 !important;
  }
}

/* ===== 全局过渡 ===== */
* {
  transition: background-color 0.25s, color 0.25s, border-color 0.25s;
}
</style>
