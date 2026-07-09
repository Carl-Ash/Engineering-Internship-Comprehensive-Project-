<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { addApp, listMyAppVoByPage, listGoodAppVoByPage } from '@/api/appController'
import { obfuscateCode } from '@/api/obfuscatorController'
import { getDeployUrl } from '@/config/env'
import AppCard from '@/components/AppCard.vue'
import { watch } from 'vue'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

const userPrompt = ref('')
const creating = ref(false)

const myApps = ref<API.AppVO[]>([])
const myAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

const featuredApps = ref<API.AppVO[]>([])
const featuredAppsPage = reactive({
  current: 1,
  pageSize: 6,
  total: 0,
})

const setPrompt = (prompt: string) => {
  userPrompt.value = prompt
}

const createApp = async () => {
  if (!userPrompt.value.trim()) {
    message.warning('请输入应用描述')
    return
  }
  if (!loginUserStore.loginUser.id) {
    message.warning('请先登录')
    await router.push('/user/login')
    return
  }
  creating.value = true
  try {
    const res = await addApp({ initPrompt: userPrompt.value.trim() })
    if (res.data.code === 0 && res.data.data) {
      message.success('应用创建成功')
      const appId = String(res.data.data)
      await router.push(`/app/chat/${appId}`)
    } else {
      message.error('创建失败：' + res.data.message)
    }
  } catch (error) {
    console.error('创建应用失败：', error)
    message.error('创建失败，请重试')
  } finally {
    creating.value = false
  }
}

const loadMyApps = async () => {
  if (!loginUserStore.loginUser.id) return
  try {
    const res = await listMyAppVoByPage({
      pageNum: myAppsPage.current,
      pageSize: myAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      myApps.value = res.data.data.records || []
      myAppsPage.total = Number(res.data.data.totalRow) || 0
    }
  } catch (error) {
    console.error('加载我的应用失败：', error)
  }
}

const loadFeaturedApps = async () => {
  try {
    const res = await listGoodAppVoByPage({
      pageNum: featuredAppsPage.current,
      pageSize: featuredAppsPage.pageSize,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (res.data.code === 0 && res.data.data) {
      featuredApps.value = res.data.data.records || []
      featuredAppsPage.total = Number(res.data.data.totalRow) || 0
    }
  } catch (error) {
    console.error('加载精选应用失败：', error)
  }
}

const viewChat = (appId: string | number | undefined) => {
  if (appId) router.push(`/app/chat/${appId}?view=1`)
}

const viewWork = (app: API.AppVO) => {
  if (app.deployKey) {
    const url = getDeployUrl(app.deployKey)
    window.open(url, '_blank')
  }
}

// 代码混淆
const obfuscatorOpen = ref(false)
const sourceCode = ref('')
const obfuscatedCode = ref('')
const obfuscating = ref(false)
const selectedLanguage = ref('python')
const selectedScheme = ref('easy')

const defaultExamples: Record<string, string> = {
  python: `def fibonacci(n):
    """Return the nth Fibonacci number."""
    if n <= 1:
        return n
    a, b = 0, 1
    for _ in range(n - 1):
        a, b = b, a + b
    return b

class Calculator:
    def __init__(self, name):
        self.name = name
        self.history = []

    def add(self, x, y):
        result = x + y
        self.history.append(('add', x, y, result))
        return result

    def multiply(self, x, y):
        result = x * y
        self.history.append(('multiply', x, y, result))
        return result

calc = Calculator("MyCalc")
print(calc.add(fibonacci(5), fibonacci(8)))`,
  c: `#include <stdio.h>
#include <string.h>

int fibonacci(int n) {
    if (n <= 1) return n;
    int a = 0, b = 1, temp;
    for (int i = 0; i < n - 1; i++) {
        temp = a + b;
        a = b;
        b = temp;
    }
    return b;
}

int main() {
    int secret = 42;
    char name[] = "world";
    printf("fib(%d) = %d\\n", 10, fibonacci(10));
    printf("Hello %s! Secret: %d\\n", name, secret * 2);
    return 0;
}`,
  javascript: `function fibonacci(n) {
    if (n <= 1) return n;
    let a = 0, b = 1;
    for (let i = 0; i < n - 1; i++) {
        [a, b] = [b, a + b];
    }
    return b;
}

class Calculator {
    constructor(name) {
        this.name = name;
        this.history = [];
    }

    add(x, y) {
        const result = x + y;
        this.history.push({ op: 'add', x, y, result });
        return result;
    }

    multiply(x, y) {
        const result = x * y;
        this.history.push({ op: 'multiply', x, y, result });
        return result;
    }
}

const calc = new Calculator('MyCalc');
console.log(calc.add(fibonacci(5), fibonacci(8)));
console.log('History:', JSON.stringify(calc.history, null, 2));`,
}

const languageOptions = [
  { label: 'Python', value: 'python' },
  { label: 'C', value: 'c' },
  { label: 'JavaScript', value: 'javascript' },
]
const schemeOptions: Record<string, { label: string; value: string }[]> = {
  python: [
    { label: '基础混淆 (AST)', value: 'easy' },
    { label: '强混淆 (字符串+控制流)', value: 'diff' },
    { label: '基础混淆 (多策略)', value: 'baseline' },
  ],
  c: [
    { label: '基础混淆 (重命名)', value: 'easy' },
    { label: '强混淆 (字符串加密)', value: 'diff' },
  ],
  javascript: [
    { label: '基础混淆 (Base64)', value: 'easy' },
    { label: '强混淆 (RC4+控制流)', value: 'diff' },
  ],
}

watch(selectedLanguage, () => {
  selectedScheme.value = schemeOptions[selectedLanguage.value]?.[0]?.value || 'easy'
  sourceCode.value = defaultExamples[selectedLanguage.value] || ''
  obfuscatedCode.value = ''
})

const toggleObfuscator = () => {
  if (!obfuscatorOpen.value) {
    sourceCode.value = defaultExamples[selectedLanguage.value] || ''
  }
  obfuscatorOpen.value = !obfuscatorOpen.value
}

const doObfuscate = async () => {
  if (!sourceCode.value.trim()) {
    message.warning('请输入源代码')
    return
  }
  obfuscating.value = true
  try {
    const res = await obfuscateCode({
      sourceCode: sourceCode.value,
      language: selectedLanguage.value,
      scheme: selectedScheme.value,
    })
    if (res.data.code === 0 && res.data.data) {
      obfuscatedCode.value = res.data.data.obfuscatedCode || ''
      message.success('混淆完成')
    } else {
      message.error('混淆失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('代码混淆失败：', error)
    message.error('混淆失败，请重试')
  } finally {
    obfuscating.value = false
  }
}

const copyResult = async () => {
  if (!obfuscatedCode.value) return
  try {
    await navigator.clipboard.writeText(obfuscatedCode.value)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

const clearObfuscator = () => {
  sourceCode.value = ''
  obfuscatedCode.value = ''
}

const openObfuscatorPanel = () => {
  obfuscatorOpen.value = true
  sourceCode.value = defaultExamples[selectedLanguage.value] || ''
  setTimeout(() => {
    document.getElementById('obfuscatorSection')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 100)
}

watch(() => route.query.obfuscator, (val) => {
  if (val === '1') {
    openObfuscatorPanel()
  } else {
    obfuscatorOpen.value = false
  }
})

onMounted(() => {
  loadMyApps()
  loadFeaturedApps()
  if (route.query.obfuscator === '1') {
    openObfuscatorPanel()
  }
})
</script>

<template>
  <div id="homePage">
    <!-- 浅色科技背景层 -->
    <div class="tech-bg">
      <div class="tech-dots"></div>
      <div class="tech-orb orb-1"></div>
      <div class="tech-orb orb-2"></div>
      <div class="tech-orb orb-3"></div>
      <div class="tech-scan"></div>
      <div class="tech-wave wave-1"></div>
      <div class="tech-wave wave-2"></div>
      <div class="tech-wave wave-3"></div>
      <div class="particles">
        <span v-for="i in 20" :key="i" class="particle" :style="{
          left: (i * 37 + 13) % 100 + '%',
          animationDelay: (i * 0.7) % 8 + 's',
          animationDuration: (6 + (i % 5)) + 's',
          width: (3 + (i % 3)) + 'px',
          height: (3 + (i % 3)) + 'px',
          opacity: 0.15 + (i % 3) * 0.15
        }"></span>
      </div>
    </div>

    <div class="container">
      <!-- 英雄区域 -->
      <div class="hero-section">
        <div class="hero-badge">AI 驱动 · 极速生成</div>
        <h1 class="hero-title">AI 应用生成平台</h1>
        <p class="hero-description">一句话描述需求，自动生成完整 Web 应用</p>
      </div>

      <!-- 输入框 -->
      <div class="input-section">
        <div class="prompt-input-wrap">
          <a-textarea
            v-model:value="userPrompt"
            placeholder="描述你想要创建的应用，例如：一个支持 Markdown 的个人博客..."
            :rows="3"
            :maxlength="1000"
            class="prompt-input"
            @press-enter="createApp"
          />
<a-button
            type="primary"
            size="large"
            class="submit-btn"
            @click="createApp"
            :loading="creating"
          >
            <template #icon>
              <span class="send-icon">↑</span>
            </template>
            开始生成
          </a-button>
        </div>
      </div>

      <!-- 快捷提示词 -->
      <div class="quick-actions">
        <button
          class="quick-chip"
          @click="setPrompt('创建一个现代化的个人博客网站，包含文章列表、文章详情、分类标签、搜索功能和评论系统。首页采用卡片式布局展示最新文章，侧边栏包含热门推荐和标签云。文章支持 Markdown 编写和代码高亮，拥有关于我和友情链接页面，整体采用极简白风格，响应式设计适配移动端。')"
        >
          <span class="chip-icon">📝</span>
          <span class="chip-text">个人博客</span>
        </button>
        <button
          class="quick-chip"
          @click="setPrompt('设计一个专业大气的中小型企业官网，包含公司介绍、产品服务展示、新闻动态、团队风采和联系我们等页面。首屏使用全屏轮播展示核心业务，产品页采用图文卡片布局，新闻页支持分类筛选，联系页集成百度地图和在线留言表单。整体风格商务简约，主色调使用企业蓝，适配PC端和移动端。')"
        >
          <span class="chip-icon">🏢</span>
          <span class="chip-text">企业官网</span>
        </button>
        <button
          class="quick-chip"
          @click="setPrompt('构建一个功能完善的在线商城网站，包含商品列表、商品详情、购物车、用户注册登录和订单管理模块。商品展示支持多级分类筛选和关键词搜索，商品详情页包含图片轮播、规格选择、数量加减和加入购物车功能。购物车支持修改数量和删除商品，结算页面包含收货地址和支付方式选择，整体采用现代电商风格设计。')"
        >
          <span class="chip-icon">🛒</span>
          <span class="chip-text">在线商城</span>
        </button>
        <button
          class="quick-chip"
          @click="setPrompt('制作一个精致的个人作品集展示网站，适用于设计师、摄影师或开发者展示项目作品。包含作品画廊首页、单作品详情页、个人简介和联系方式页面。首页使用瀑布流布局展示作品缩略图，支持按类别筛选。作品详情页包含大图预览、项目背景介绍、所用技术和相关链接。整体采用暗色主题搭配渐变色点缀，动画过渡流畅。')"
        >
          <span class="chip-icon">🎨</span>
          <span class="chip-text">作品集</span>
        </button>
      </div>

      <!-- 代码混淆工具 -->
      <div id="obfuscatorSection" class="section">
        <div class="obfuscator-header" @click="toggleObfuscator">
          <h2 class="section-title" style="margin-bottom:0;cursor:pointer">代码混淆工具</h2>
          <span class="obfuscator-toggle">{{ obfuscatorOpen ? '收起 ▲' : '展开 ▼' }}</span>
        </div>
        <div v-if="obfuscatorOpen" class="obfuscator-panel">
          <div class="obfuscator-toolbar">
            <div class="obfuscator-selects">
              <a-select
                v-model:value="selectedLanguage"
                size="small"
                style="width: 100px"
                :options="languageOptions"
              />
              <a-select
                v-model:value="selectedScheme"
                size="small"
                style="width: 180px"
                :options="schemeOptions[selectedLanguage]"
              />
            </div>
          </div>
          <div class="obfuscator-body">
            <div class="obfuscator-pane">
              <div class="obfuscator-pane-header">
                <span class="pane-label">源代码 ({{ selectedLanguage === 'c' ? 'C' : selectedLanguage === 'javascript' ? 'JavaScript' : 'Python' }})</span>
                <a-button size="small" type="link" @click="clearObfuscator">清空</a-button>
              </div>
              <a-textarea
                v-model:value="sourceCode"
                :placeholder="'粘贴 ' + (selectedLanguage === 'c' ? 'C' : selectedLanguage === 'javascript' ? 'JavaScript' : 'Python') + ' 源代码...'"
                :rows="12"
                class="obfuscator-input"
              />
            </div>
            <div class="obfuscator-pane">
              <div class="obfuscator-pane-header">
                <span class="pane-label">混淆结果</span>
                <a-button
                  size="small"
                  type="link"
                  @click="copyResult"
                  :disabled="!obfuscatedCode"
                >复制</a-button>
              </div>
              <a-textarea
                v-model:value="obfuscatedCode"
                :rows="12"
                class="obfuscator-output"
                readonly
                placeholder="混淆后的代码将显示在这里..."
              />
            </div>
          </div>
          <div class="obfuscator-actions">
            <a-button
              type="primary"
              size="large"
              @click="doObfuscate"
              :loading="obfuscating"
            >
              执行混淆
            </a-button>
          </div>
        </div>
      </div>

      <!-- 我的作品 -->
      <div class="section">
        <h2 class="section-title">我的作品</h2>
        <div class="app-grid">
          <AppCard
            v-for="app in myApps"
            :key="app.id"
            :app="app"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div v-if="myAppsPage.total > myAppsPage.pageSize" class="pagination-wrapper">
          <a-pagination
            v-model:current="myAppsPage.current"
            v-model:page-size="myAppsPage.pageSize"
            :total="myAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个应用`"
            @change="loadMyApps"
          />
        </div>
      </div>

      <!-- 精选案例 -->
      <div class="section">
        <h2 class="section-title">精选案例</h2>
        <div class="featured-grid">
          <AppCard
            v-for="app in featuredApps"
            :key="app.id"
            :app="app"
            :featured="true"
            @view-chat="viewChat"
            @view-work="viewWork"
          />
        </div>
        <div v-if="featuredAppsPage.total > featuredAppsPage.pageSize" class="pagination-wrapper">
          <a-pagination
            v-model:current="featuredAppsPage.current"
            v-model:page-size="featuredAppsPage.pageSize"
            :total="featuredAppsPage.total"
            :show-size-changer="false"
            :show-total="(total: number) => `共 ${total} 个案例`"
            @change="loadFeaturedApps"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  min-height: 100vh;
  background:
    linear-gradient(180deg,
      rgba(248, 250, 252, 0.82) 0%,
      rgba(240, 244, 255, 0.82) 25%,
      rgba(248, 246, 253, 0.82) 50%,
      rgba(240, 248, 246, 0.82) 75%,
      rgba(248, 250, 252, 0.82) 100%
    ),
    url('../../assets/backgrounds/hero-bg.png') center/cover no-repeat;
  position: relative;
  overflow: hidden;
}

/* ====== 浅色科技背景 ====== */
.tech-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.tech-dots {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(59, 130, 246, 0.08) 1px, transparent 1px);
  background-size: 32px 32px;
}

/* 光晕 */
.tech-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.3;
  animation: orbPulse 10s ease-in-out infinite alternate;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: rgba(59, 130, 246, 0.12);
  top: -15%;
  left: -10%;
  animation-delay: 0s;
}

.orb-2 {
  width: 500px;
  height: 500px;
  background: rgba(139, 92, 246, 0.1);
  top: 45%;
  right: -12%;
  animation-delay: -4s;
}

.orb-3 {
  width: 550px;
  height: 550px;
  background: rgba(16, 185, 129, 0.08);
  bottom: -18%;
  left: 15%;
  animation-delay: -7s;
}

@keyframes orbPulse {
  0% { transform: scale(0.85); opacity: 0.2; }
  100% { transform: scale(1.2); opacity: 0.4; }
}

/* 扫描线 */
.tech-scan {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(59, 130, 246, 0.08) 15%,
    rgba(59, 130, 246, 0.25) 50%,
    rgba(59, 130, 246, 0.08) 85%,
    transparent 100%
  );
  animation: scanDown 8s linear infinite;
  box-shadow: 0 0 8px rgba(59, 130, 246, 0.1);
}

@keyframes scanDown {
  0% { top: -2px; opacity: 0; }
  5% { opacity: 1; }
  95% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

/* 波动线条 */
.tech-wave {
  position: absolute;
  left: -10%;
  right: -10%;
  height: 1px;
  border-radius: 50%;
  opacity: 0.12;
  animation: waveDrift 12s ease-in-out infinite;
}

.wave-1 {
  top: 28%;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.2), transparent);
  height: 2px;
  animation-delay: 0s;
}

.wave-2 {
  top: 52%;
  background: linear-gradient(90deg, transparent, rgba(139, 92, 246, 0.18), transparent);
  height: 1px;
  animation-delay: -4s;
}

.wave-3 {
  top: 76%;
  background: linear-gradient(90deg, transparent, rgba(16, 185, 129, 0.15), transparent);
  height: 2px;
  animation-delay: -8s;
}

@keyframes waveDrift {
  0%, 100% {
    transform: translateX(0) scaleY(1);
    opacity: 0.08;
  }
  25% {
    transform: translateX(3%) scaleY(2.5);
    opacity: 0.18;
  }
  50% {
    transform: translateX(-2%) scaleY(1);
    opacity: 0.08;
  }
  75% {
    transform: translateX(2%) scaleY(3);
    opacity: 0.16;
  }
}

/* 浮动粒子 */
.particles {
  position: absolute;
  inset: 0;
}

.particle {
  position: absolute;
  bottom: -10px;
  border-radius: 50%;
  background: rgba(59, 130, 246, 0.5);
  animation: floatUp linear infinite;
  box-shadow: 0 0 4px rgba(59, 130, 246, 0.3);
}

.particle:nth-child(3n) {
  background: rgba(139, 92, 246, 0.5);
  box-shadow: 0 0 4px rgba(139, 92, 246, 0.3);
}

.particle:nth-child(3n + 2) {
  background: rgba(16, 185, 129, 0.45);
  box-shadow: 0 0 4px rgba(16, 185, 129, 0.25);
}

.particle:nth-child(5n) {
  background: rgba(59, 130, 246, 0.4);
  box-shadow: 0 0 6px rgba(59, 130, 246, 0.4);
}

@keyframes floatUp {
  0% {
    transform: translateY(0) scale(0);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  80% {
    opacity: 0.6;
  }
  100% {
    transform: translateY(-100vh) scale(1);
    opacity: 0;
  }
}

/* ====== 容器 ====== */
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 24px 80px;
  position: relative;
  z-index: 2;
  box-sizing: border-box;
}

/* ====== 英雄区域 ====== */
.hero-section {
  text-align: center;
  padding: 72px 0 40px;
}

.hero-badge {
  display: inline-block;
  padding: 6px 18px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
  color: #3b82f6;
  background: rgba(59, 130, 246, 0.08);
  border: 1px solid rgba(59, 130, 246, 0.15);
  margin-bottom: 24px;
  letter-spacing: 1px;
}

.hero-title {
  font-size: 52px;
  font-weight: 800;
  margin: 0 0 16px;
  line-height: 1.15;
  letter-spacing: -2px;
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 50%, #059669 100%);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: titleGradient 4s ease-in-out infinite;
}

@keyframes titleGradient {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.hero-description {
  font-size: 18px;
  margin: 0;
  color: var(--text-secondary);
}

/* ====== 输入区域 ====== */
.input-section {
  max-width: 720px;
  margin: 0 auto 36px;
  position: relative;
  z-index: 1;
}

.prompt-input-wrap {
  position: relative;
  background: var(--bg-card);
  border-radius: 16px;
  padding: 20px 20px 16px;
  border: 1px solid var(--border-color);
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.04),
    0 0 0 1px rgba(0, 0, 0, 0.02);
  transition: all 0.3s ease;
}

.prompt-input-wrap:focus-within {
  border-color: rgba(59, 130, 246, 0.35);
  box-shadow:
    0 8px 32px rgba(59, 130, 246, 0.08),
    0 0 0 3px rgba(59, 130, 246, 0.06);
}

.prompt-input-wrap :deep(.ant-input) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  color: var(--text-color) !important;
  font-size: 15px;
  line-height: 1.7;
  padding: 0;
  resize: none;
  min-height: 52px;
}

.prompt-input-wrap :deep(.ant-input)::placeholder {
  color: var(--text-secondary) !important;
}

.prompt-input-wrap :deep(.ant-input):focus {
  box-shadow: none !important;
}

.submit-btn {
  margin-top: 12px;
  width: 100%;
  height: 44px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 15px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border: none;
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.25);
  transition: all 0.3s ease;
}

.submit-btn:hover {
  background: linear-gradient(135deg, #2563eb, #4f46e5);
  box-shadow: 0 4px 20px rgba(59, 130, 246, 0.4);
  transform: translateY(-1px);
}

.submit-btn:active {
  transform: translateY(0);
}

.send-icon {
  font-size: 16px;
  font-weight: 700;
}

/* ====== 快捷标签 ====== */
.quick-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 72px;
  flex-wrap: wrap;
  position: relative;
  z-index: 1;
}

.quick-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 24px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-color);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.25s ease;
  white-space: nowrap;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.quick-chip:hover {
  border-color: rgba(59, 130, 246, 0.4);
  color: #3b82f6;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.1);
}

.chip-icon {
  font-size: 16px;
}

.chip-text {
  font-weight: 500;
}

/* ====== 区块 ====== */
.section {
  margin-bottom: 64px;
  position: relative;
  z-index: 1;
}

.section-title {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 28px;
  color: var(--text-color);
  padding-left: 16px;
  position: relative;
}

.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 22px;
  background: linear-gradient(180deg, #3b82f6, #8b5cf6);
  border-radius: 2px;
}

/* 网格 */
.app-grid,
.featured-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 32px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 40px;
}

/* ====== 代码混淆工具 ====== */
.obfuscator-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.obfuscator-toggle {
  font-size: 13px;
  color: var(--text-secondary);
  padding: 4px 12px;
  border-radius: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  transition: all 0.2s;
}

.obfuscator-header:hover .obfuscator-toggle {
  border-color: rgba(59, 130, 246, 0.4);
  color: #3b82f6;
}

.obfuscator-panel {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 24px;
  border: 1px solid var(--border-color);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.04);
  margin-top: 20px;
}

.obfuscator-toolbar {
  margin-bottom: 16px;
}

.obfuscator-selects {
  display: flex;
  gap: 10px;
}

.obfuscator-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.obfuscator-pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.pane-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color);
}

.obfuscator-input :deep(textarea),
.obfuscator-output :deep(textarea) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important;
  font-size: 13px !important;
  line-height: 1.6 !important;
  border-radius: 10px !important;
  background: #f8fafc !important;
  resize: vertical;
}

.obfuscator-output :deep(textarea) {
  background: #f0fdf4 !important;
}

.obfuscator-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

.obfuscator-actions .ant-btn-primary {
  min-width: 160px;
  height: 40px;
  border-radius: 10px;
  font-weight: 600;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border: none;
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.25);
}

.obfuscator-actions .ant-btn-primary:hover {
  background: linear-gradient(135deg, #2563eb, #4f46e5);
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 34px;
    letter-spacing: -1px;
  }
  .hero-description {
    font-size: 15px;
  }
  .app-grid,
  .featured-grid {
    grid-template-columns: 1fr;
  }
  .quick-actions {
    gap: 8px;
  }
  .quick-chip {
    padding: 8px 16px;
    font-size: 13px;
  }
  .obfuscator-body {
    grid-template-columns: 1fr;
  }
}
</style>
