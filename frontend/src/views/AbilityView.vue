<script setup lang="ts">
import { useRouter } from 'vue-router'
import { auth } from '../auth'
import { readCachedSiteConfig } from '../siteConfigCache'

const router = useRouter()
const cachedSite = readCachedSiteConfig()
const tutorialUrl = (() => {
  if (auth.user?.role === 'PUBLISHER') return cachedSite?.tutorialDocUrlPublisher || ''
  return cachedSite?.tutorialDocUrlStudent || ''
})()

const tracks = [
  {
    key: 'TA',
    name: '助教 TA',
    blurb: '课程支持、答疑与教学协作，把课堂经验沉淀成可交付的协作成果。',
  },
  {
    key: 'INTERN',
    name: '开发 Intern',
    blurb: '产品功能、工程实现与开源贡献，在真实项目里完成端到端交付。',
  },
  {
    key: 'RA',
    name: '科研 RA',
    blurb: '以揭榜形式开放研究方向，学生提交方案竞标，与导师推进研究落地。',
  },
  {
    key: 'TEAM',
    name: '项目协作',
    blurb: '运营、标注等协作需求按岗位报名，一次可招多人，按节奏推进交付。',
  },
]

const steps = [
  {
    title: '浏览任务',
    text: '在任务广场按频道筛选：助教、开发、科研与项目，找到匹配方向。',
  },
  {
    title: '申请与沟通',
    text: '提交申请或方案，与发布方私信沟通；通过后进入工作台推进协作。',
  },
  {
    title: '交付成长',
    text: '按里程碑提交进展与成果，沉淀作品与协作经验，形成可展示的履历。',
  },
]

function goPlaza() {
  void router.push('/plaza')
}
</script>

<template>
  <section class="home">
    <section class="hero" aria-labelledby="home-hero-title">
      <div class="hero-copy">
        <p class="brand">WHU Dev</p>
        <h1 id="home-hero-title">校园开发协作平台</h1>
        <p class="lead">
          连接课程助教、工程实习、科研揭榜与项目协作。面向武大同学与发布方，用真实任务练手、交付与成长。
        </p>
        <div class="hero-actions">
          <a-button type="primary" size="large" @click="goPlaza">查看任务</a-button>
          <a
            v-if="tutorialUrl"
            class="ghost-link"
            :href="tutorialUrl"
            target="_blank"
            rel="noopener"
          >
            参与指南
          </a>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <span class="orb orb-a" />
        <span class="orb orb-b" />
        <span class="orb orb-c" />
        <span class="ring" />
      </div>
    </section>

    <section class="panel" aria-labelledby="home-steps-title">
      <h2 id="home-steps-title">如何参与</h2>
      <ol class="steps">
        <li v-for="(step, index) in steps" :key="step.title">
          <span class="step-index">{{ index + 1 }}</span>
          <div>
            <strong>{{ step.title }}</strong>
            <p>{{ step.text }}</p>
          </div>
        </li>
      </ol>
    </section>

    <section class="panel" aria-labelledby="home-tracks-title">
      <h2 id="home-tracks-title">任务频道</h2>
      <div class="tracks">
        <article v-for="track in tracks" :key="track.key" class="track">
          <h3>{{ track.name }}</h3>
          <p>{{ track.blurb }}</p>
        </article>
      </div>
    </section>

    <section class="cta-band">
      <div>
        <h2>开始浏览开放任务</h2>
        <p>进入任务广场，按频道与筛选条件找到适合你的协作机会。</p>
      </div>
      <a-button type="primary" size="large" @click="goPlaza">进入任务广场</a-button>
    </section>
  </section>
</template>

<style scoped>
.home {
  width: 100%;
  max-width: 1140px;
  margin: 0 auto;
  padding-bottom: 28px;
}
.hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(180px, 0.85fr);
  gap: 20px;
  align-items: center;
  min-height: 0;
  padding: 12px 8px 20px;
  overflow: hidden;
}
.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 34em;
}
.brand {
  margin: 0 0 12px;
  font-family: "Sora", "Noto Sans SC", sans-serif;
  font-size: clamp(28px, 5vw, 42px);
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #0f172a;
  line-height: 1.05;
}
.hero h1 {
  margin: 0;
  font-family: "Sora", "Noto Sans SC", sans-serif;
  font-size: clamp(22px, 3.2vw, 32px);
  font-weight: 700;
  letter-spacing: -0.03em;
  color: #0f172a;
  line-height: 1.25;
}
.lead {
  margin: 16px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: #475569;
}
.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
}
.ghost-link {
  font-size: 14px;
  font-weight: 600;
  color: #2563eb;
}
.ghost-link:hover {
  text-decoration: underline;
}
.hero-art {
  position: relative;
  min-height: 180px;
}
.orb,
.ring {
  position: absolute;
  border-radius: 50%;
}
.orb-a {
  right: 8%;
  top: 12%;
  width: min(220px, 42vw);
  height: min(220px, 42vw);
  background: rgba(59, 130, 246, 0.18);
}
.orb-b {
  right: 28%;
  bottom: 18%;
  width: 120px;
  height: 120px;
  background: rgba(14, 165, 233, 0.22);
}
.orb-c {
  right: 42%;
  top: 38%;
  width: 64px;
  height: 64px;
  background: rgba(249, 115, 22, 0.28);
  border-radius: 12px;
  transform: rotate(18deg);
}
.ring {
  right: 4%;
  bottom: 8%;
  width: 150px;
  height: 150px;
  border: 10px solid rgba(37, 99, 235, 0.16);
}
.panel {
  margin-top: 8px;
  padding: 28px 8px 8px;
}
.panel h2 {
  margin: 0 0 18px;
  font-size: 22px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: #0f172a;
}
.steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.steps li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 18px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #fff;
}
.step-index {
  flex: none;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 750;
}
.steps strong {
  display: block;
  margin-bottom: 6px;
  font-size: 15px;
  color: #0f172a;
}
.steps p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #64748b;
}
.tracks {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.track {
  padding: 20px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #fff 70%);
}
.track h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}
.track p {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: #64748b;
}
.cta-band {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 28px;
  padding: 24px 22px;
  border-radius: 16px;
  background: linear-gradient(120deg, #eff6ff, #f8fafc 55%, #ecfeff);
  border: 1px solid #dbeafe;
}
.cta-band h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 750;
  color: #0f172a;
}
.cta-band p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}
@media (max-width: 860px) {
  .hero {
    grid-template-columns: 1fr;
    min-height: 0;
    padding-top: 12px;
  }
  .hero-art {
    min-height: 160px;
    order: -1;
  }
  .steps,
  .tracks {
    grid-template-columns: 1fr;
  }
}
</style>
