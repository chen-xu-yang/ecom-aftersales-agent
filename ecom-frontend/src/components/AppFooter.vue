<template>
  <footer class="site-footer">
    <div class="wrap footer-inner">
      <span>© {{ currentYear }} 电商售后智能客服平台</span>
      <span>三份规则 · 两个入口 · 课程演示</span>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.site-footer {
  margin-top: auto;
  padding: 28px 0 36px;
}

.footer-inner {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  color: var(--muted);
  font-size: var(--step-small);
}

@media (max-width: 640px) {
  .site-footer {
    padding: 20px 0 24px;
  }

  .footer-inner {
    flex-direction: column;
    gap: 6px;
  }
}
</style>
