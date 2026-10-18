<template>
  <header class="site-header">
    <div class="wrap header-inner">
      <router-link class="brand" to="/" aria-label="电商售后智能客服平台首页">
        <span class="brand-mark" aria-hidden="true"></span>
        <span>电商售后</span>
      </router-link>

      <nav class="nav" aria-label="主导航">
        <router-link to="/" class="nav-link" :class="{ 'is-active': route.path === '/' }">
          首页
        </router-link>
        <router-link
          to="/workspace"
          class="nav-link"
          :class="{ 'is-active': route.path.startsWith('/workspace') }"
        >
          工作台
        </router-link>
        <router-link :to="{ path: '/', hash: '#rules' }" class="nav-link">
          规则
        </router-link>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<style scoped>
.site-header {
  background: var(--bg);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.brand-mark {
  width: 6px;
  height: 6px;
  background: var(--accent);
}

.nav {
  display: flex;
  gap: 24px;
}

.nav-link {
  font-size: 13px;
  color: var(--muted);
  transition: color 0.2s ease;
}

.nav-link:hover,
.nav-link.is-active {
  color: var(--ink);
}

.nav-link.is-active {
  font-weight: 500;
}

@media (max-width: 640px) {
  .header-inner {
    height: 56px;
  }

  .brand {
    font-size: 13px;
  }

  .nav {
    gap: 16px;
  }

  .nav-link {
    font-size: 12px;
  }
}
</style>
