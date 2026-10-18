import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '电商售后智能客服平台',
      description: '售后客服引用规则原文作答，处理智能体拆解任务、调用工具并留下处理记录。'
    }
  },
  {
    path: '/workspace',
    name: 'Workspace',
    component: () => import('../views/Workspace.vue'),
    meta: {
      title: '工作台 - 电商售后智能客服平台',
      description: '在同一页使用售后客服或处理智能体，覆盖退换货、物流、价保与发票。'
    }
  },
  {
    path: '/aftersales',
    redirect: { path: '/workspace', query: { mode: 'chat' } }
  },
  {
    path: '/agent',
    redirect: { path: '/workspace', query: { mode: 'agent' } }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition

    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    if (to.hash) {
      return {
        el: to.hash,
        top: 24,
        behavior: reduceMotion ? 'auto' : 'smooth'
      }
    }

    return { top: 0 }
  }
})

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router
