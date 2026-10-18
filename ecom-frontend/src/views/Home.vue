<template>
  <div class="home">
    <AppHeader />

    <main id="main">
      <section class="hero wrap">
        <div class="hero-copy">
          <h1>欢迎，智能售后客服为您服务！</h1>
          <p class="lede">
            客服引用售后规则原文作答；智能体拆解任务、调用工具，并留下可复核的处理记录。
          </p>
          <div class="actions">
            <router-link class="btn" :to="{ path: '/workspace', query: { mode: 'chat' } }">
              进入工作台
            </router-link>
            <a class="text-link" href="#rules">先看规则</a>
          </div>
        </div>
      </section>

      <section class="entries wrap" aria-labelledby="entries-title">
        <div class="section-head">
          <h2 id="entries-title">从哪里开始</h2>
          <p>两个入口，共用一套规则。</p>
        </div>
        <div class="entry-grid">
          <router-link
            class="entry"
            :to="{ path: '/workspace', query: { mode: 'chat' } }"
          >
            <span class="entry-title">售后客服</span>
            <span class="entry-desc">解释退换货、物流、价保与发票政策，给出下一步。</span>
            <span class="entry-arrow" aria-hidden="true">→</span>
          </router-link>
          <router-link
            class="entry"
            :to="{ path: '/workspace', query: { mode: 'agent' } }"
          >
            <span class="entry-title">处理智能体</span>
            <span class="entry-desc">拆解任务、检索规则、调用工具，生成处理说明。</span>
            <span class="entry-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>
      </section>

      <section id="rules" class="rules wrap" aria-labelledby="rules-title">
        <div class="section-head">
          <h2 id="rules-title">规则索引</h2>
          <p>先读一条规则，再带着问题去问。</p>
        </div>
        <div class="rule-list">
          <router-link
            v-for="topic in topics"
            :key="topic.title"
            class="rule-row"
            :to="{ path: '/workspace', query: { mode: 'chat' } }"
            :aria-label="`去工作台咨询${topic.title}`"
          >
            <span class="rule-topic">{{ topic.title }}</span>
            <span class="rule-copy">{{ topic.copy }}</span>
            <span class="rule-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>
      </section>

      <section class="process wrap" aria-labelledby="process-title">
        <h2 id="process-title">怎么处理</h2>
        <div class="process-grid">
          <div v-for="step in processSteps" :key="step.title">
            <h3>{{ step.title }}</h3>
            <p>{{ step.copy }}</p>
          </div>
        </div>
      </section>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { useHead } from '@vueuse/head'
import AppHeader from '../components/AppHeader.vue'
import AppFooter from '../components/AppFooter.vue'

useHead({
  title: '电商售后智能客服平台',
  meta: [
    {
      name: 'description',
      content:
        '售后客服引用规则原文作答，处理智能体拆解任务、调用工具并留下处理记录。覆盖退换货、物流、价保与发票。'
    },
    {
      name: 'keywords',
      content: '电商售后,智能客服,售后规则,退换货,物流,价保,AI智能体'
    }
  ]
})

const topics = [
  { title: '退换货', copy: '七天无理由、吊牌、运费承担' },
  { title: '物流', copy: '迟发、停滞、破损、少件与错发' },
  { title: '价保', copy: '15 个自然日内，同店降价的差价' },
  { title: '发票', copy: '换开、电子发票与金额口径' }
]

const processSteps = [
  { title: '查规则', copy: '先从三份售后规则里检索相关片段，带主题标签返回。' },
  { title: '拆任务', copy: '判断是否需要继续查订单、读文件或生成文档。' },
  { title: '留记录', copy: '最终答复与处理文档保留，中间步骤只写日志。' }
]
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  min-height: 100svh;
}

.hero {
  padding: 72px 0 64px;
}

.hero-copy {
  max-width: 760px;
}

.hero h1 {
  font-size: var(--step-h1);
  line-height: 1.22;
}

.lede {
  max-width: 34em;
  margin-top: 18px;
  color: var(--muted);
  font-size: var(--step-lead);
  line-height: 1.7;
  text-wrap: pretty;
}

.actions {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 28px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.section-head h2,
.process h2 {
  font-size: var(--step-h2);
}

.section-head p {
  color: var(--muted);
  font-size: var(--step-body);
}

.entries {
  padding: 24px 0 56px;
}

.entry-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px 24px;
  padding: 22px;
  background: var(--surface);
  transition: background-color 0.2s ease;
}

.entry:hover {
  background: var(--accent-soft);
}

.entry-title {
  font-size: var(--step-lead);
  font-weight: 600;
}

.entry-desc {
  grid-column: 1 / 2;
  color: var(--muted);
  font-size: var(--step-body);
  line-height: 1.6;
}

.entry-arrow {
  grid-column: 2 / 3;
  grid-row: 1 / 3;
  align-self: center;
  color: var(--accent);
  font-size: 18px;
}

.rules {
  padding: 24px 0 64px;
}

.rule-list {
  display: grid;
}

.rule-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) auto;
  gap: 24px;
  align-items: baseline;
  padding: 14px 0;
  transition: background-color 0.2s ease;
}

.rule-row:hover {
  background: var(--surface);
}

.rule-topic {
  font-size: var(--step-lead);
  font-weight: 600;
}

.rule-copy {
  color: var(--muted);
  font-size: var(--step-body);
}

.rule-arrow {
  color: var(--muted);
  font-size: 14px;
}

.process {
  padding: 8px 0 72px;
}

.process h2 {
  margin-bottom: 24px;
}

.process-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 32px;
  padding: 28px;
  background: var(--surface);
}

.process h3 {
  margin-bottom: 8px;
  font-family: var(--font-sans);
  font-size: var(--step-body);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.5;
}

.process p {
  color: var(--muted);
  font-size: var(--step-body);
  line-height: 1.7;
}

@media (max-width: 900px) {
  .hero {
    padding: 48px 0 40px;
  }

  .entry-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero {
    padding: 36px 0 32px;
  }

  .actions {
    gap: 14px;
    margin-top: 22px;
  }

  .section-head {
    display: block;
    margin-bottom: 16px;
  }

  .section-head p {
    margin-top: 6px;
  }

  .entries {
    padding: 8px 0 40px;
  }

  .entry {
    padding: 18px;
  }

  .rules {
    padding: 8px 0 44px;
  }

  .rule-row {
    grid-template-columns: 96px minmax(0, 1fr);
    gap: 12px;
  }

  .rule-arrow {
    display: none;
  }

  .process {
    padding: 8px 0 48px;
  }

  .process-grid {
    grid-template-columns: 1fr;
    gap: 18px;
    padding: 20px;
  }
}
</style>
