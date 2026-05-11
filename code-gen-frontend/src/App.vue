<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

import {healthCheck} from '@/api/healthController.ts'

healthCheck().then((res) => {
  console.log(res)
})
</script>

<template>
  <a-config-provider :locale="zhCN">
    <BasicLayout/>
  </a-config-provider>
</template>

<style>
</style>
