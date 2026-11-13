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

type Partner = {
  name: string
  href: string
  color: string
  mark?: string
  path?: string
}

const partnerGroups: { title: string; items: Partner[] }[] = [
  {
    title: '组织方',
    items: [
      { name: 'Evolving Systems Lab @ Wuhan University', href: 'https://whues.org/', color: '#2563eb', mark: 'ES' },
    ],
  },
  {
    title: '合作伙伴',
    items: [
      { name: '开放原子开源基金会', href: 'https://www.openatom.org', color: '#0F766E', mark: '原' },
      {
        name: 'Apache 软件基金会',
        href: 'https://www.apache.org',
        color: '#D22128',
        path: 'M17.805 2.197v.066h.156v.44h.072v-.44h.156v-.066zm.9 0l-.175.353-.172-.353h-.087v.506h.067V2.3l.172.35h.045l.172-.35v.404h.066v-.506zm-4.257 1c-.204.31-.424.66-.66 1.06l-.04.062a44.457 44.457 0 00-1.265 2.29c-.187.36-.38.742-.577 1.146l2.267-.25c.66-.302.955-.578 1.242-.976a15.5 15.5 0 00.23-.342c.23-.363.46-.763.663-1.16.197-.386.37-.767.505-1.11.083-.22.15-.422.198-.6.042-.158.074-.307.1-.45-.884.15-1.965.295-2.668.33zM11.894 7.78l-.077.16c-.078.16-.157.32-.236.488-.086.18-.172.364-.26.552l-.132.287a75.265 75.265 0 00-1.427 3.3c-.163.397-.327.807-.493 1.23-.15.38-.297.765-.45 1.164l-.02.06c-.15.396-.3.802-.453 1.22l-.01.027.72-.08a.213.213 0 01-.042-.006c.863-.106 2.01-.75 2.75-1.547.342-.367.652-.8.94-1.306.213-.377.413-.795.604-1.258.168-.405.328-.843.48-1.318-.196.105-.423.18-.673.235a2.184 2.184 0 01-.273.046c.806-.31 1.314-.905 1.683-1.64a2.816 2.816 0 01-.968.428c-.06.012-.116.022-.174.03l-.043.006h.002c.278-.118.514-.248.718-.403a2.571 2.571 0 00.637-.698l.063-.104.077-.154a8.107 8.107 0 00.367-.85l.03-.088a3.04 3.04 0 00.123-.463.733.733 0 01-.094.065c-.243.145-.66.277-.996.34l.663-.074-.664.073h-.017l-.1.017c.006-.003.01-.006.017-.008l-2.265.25-.013.022zM8.27 16.45c-.117.323-.236.654-.355.992l-.005.015c-.016.046-.032.094-.05.142-.08.227-.15.432-.31.9.264.12.475.435.675.793a1.44 1.44 0 00-.466-.99c1.293.06 2.41-.27 2.99-1.217.05-.084.096-.173.14-.268-.26.333-.59.474-1.2.44 0 0-.004 0-.005.002l.004-.002c.9-.404 1.354-.79 1.754-1.433.094-.153.186-.32.28-.503-.788.81-1.702 1.04-2.664.865l-.72.078a6.43 6.43 0 00-.067.183zM15.42.112c-.376.222-1 .85-1.748 1.763l.686 1.294c.48-.687.97-1.307 1.462-1.836l.058-.062c-.02.02-.04.04-.057.062-.16.176-.644.74-1.375 1.863.703-.035 1.784-.18 2.666-.33.262-1.47-.258-2.142-.258-2.142s-.66-1.07-1.436-.61zm-3.084 6.402a40.253 40.253 0 011.306-2.26l.04-.064c.224-.352.45-.693.677-1.02l-.685-1.293-.157.192c-.197.245-.403.51-.613.79a39.853 39.853 0 00-2.016 2.97l-.022.038.893 1.763c.19-.378.38-.752.575-1.118zm-3.73 8.32c.158-.406.319-.81.483-1.225.156-.394.32-.79.484-1.19a91.133 91.133 0 011.6-3.604l.205-.424c.12-.243.237-.485.36-.724a.125.125 0 01.02-.04l-.895-1.763-.044.07c-.207.34-.414.687-.617 1.042a38.056 38.056 0 00-1.092 2.04l-.094.193a24.573 24.573 0 00-1.258 3.087 18.492 18.492 0 00-.52 1.997l.896 1.77c.117-.317.24-.638.364-.963zm-1.376-.476a13.38 13.38 0 00-.234 1.692c0 .02-.004.04-.005.06-.28-.45-1.03-.888-1.026-.884.537.778.944 1.55 1.005 2.31-.29.058-.684-.027-1.14-.195.475.436.83.556.97.588-.434.03-.89.328-1.346.67.668-.27 1.21-.38 1.596-.29-.61 1.74-1.23 3.655-1.843 5.69a.538.538 0 00.364-.354c.11-.368.84-2.786 1.978-5.965l.097-.27.028-.078c.12-.332.246-.672.374-1.02l.09-.237v-.004L7.24 14.3c-.003.02-.01.04-.012.06z',
      },
      {
        name: 'Linux 基金会',
        href: 'https://www.linuxfoundation.org',
        color: '#1F2937',
        path: 'M12.504 0c-.155 0-.315.008-.48.021-4.226.333-3.105 4.807-3.17 6.298-.076 1.092-.3 1.953-1.05 3.02-.885 1.051-2.127 2.75-2.716 4.521-.278.832-.41 1.684-.287 2.489a.424.424 0 00-.11.135c-.26.268-.45.6-.663.839-.199.199-.485.267-.797.4-.313.136-.658.269-.864.68-.09.189-.136.394-.132.602 0 .199.027.4.055.536.058.399.116.728.04.97-.249.68-.28 1.145-.106 1.484.174.334.535.47.94.601.81.2 1.91.135 2.774.6.926.466 1.866.67 2.616.47.526-.116.97-.464 1.208-.946.587-.003 1.23-.269 2.26-.334.699-.058 1.574.267 2.577.2.025.134.063.198.114.333l.003.003c.391.778 1.113 1.132 1.884 1.071.771-.06 1.592-.536 2.257-1.306.631-.765 1.683-1.084 2.378-1.503.348-.199.629-.469.649-.853.023-.4-.2-.811-.714-1.376v-.097l-.003-.003c-.17-.2-.25-.535-.338-.926-.085-.401-.182-.786-.492-1.046h-.003c-.059-.054-.123-.067-.188-.135a.357.357 0 00-.19-.064c.431-1.278.264-2.55-.173-3.694-.533-1.41-1.465-2.638-2.175-3.483-.796-1.005-1.576-1.957-1.56-3.368.026-2.152.236-6.133-3.544-6.139zm.529 3.405h.013c.213 0 .396.062.584.198.19.135.33.332.438.533.105.259.158.459.166.724 0-.02.006-.04.006-.06v.105a.086.086 0 01-.004-.021l-.004-.024a1.807 1.807 0 01-.15.706.953.953 0 01-.213.335.71.71 0 00-.088-.042c-.104-.045-.198-.064-.284-.133a1.312 1.312 0 00-.22-.066c.05-.06.146-.133.183-.198.053-.128.082-.264.088-.402v-.02a1.21 1.21 0 00-.061-.4c-.045-.134-.101-.2-.183-.333-.084-.066-.167-.132-.267-.132h-.016c-.093 0-.176.03-.262.132a.8.8 0 00-.205.334 1.18 1.18 0 00-.09.4v.019c.002.089.008.179.02.267-.193-.067-.438-.135-.607-.202a1.635 1.635 0 01-.018-.2v-.02a1.772 1.772 0 01.15-.768c.082-.22.232-.406.43-.533a.985.985 0 01.594-.2zm-2.962.059h.036c.142 0 .27.048.399.135.146.129.264.288.344.465.09.199.14.4.153.667v.004c.007.134.006.2-.002.266v.08c-.03.007-.056.018-.083.024-.152.055-.274.135-.393.2.012-.09.013-.18.003-.267v-.015c-.012-.133-.04-.2-.082-.333a.613.613 0 00-.166-.267.248.248 0 00-.183-.064h-.021c-.071.006-.13.04-.186.132a.552.552 0 00-.12.27.944.944 0 00-.023.33v.015c.012.135.037.2.08.334.046.134.098.2.166.268.01.009.02.018.034.024-.07.057-.117.07-.176.136a.304.304 0 01-.131.068 2.62 2.62 0 01-.275-.402 1.772 1.772 0 01-.155-.667 1.759 1.759 0 01.08-.668 1.43 1.43 0 01.283-.535c.128-.133.26-.2.418-.2zm1.37 1.706c.332 0 .733.065 1.216.399.293.2.523.269 1.052.468h.003c.255.136.405.266.478.399v-.131a.571.571 0 01.016.47c-.123.31-.516.643-1.063.842v.002c-.268.135-.501.333-.775.465-.276.135-.588.292-1.012.267a1.139 1.139 0 01-.448-.067 3.566 3.566 0 01-.322-.198c-.195-.135-.363-.332-.612-.465v-.005h-.005c-.4-.246-.616-.512-.686-.71-.07-.268-.005-.47.193-.6.224-.135.38-.271.483-.336.104-.074.143-.102.176-.131h.002v-.003c.169-.202.436-.47.839-.601.139-.036.294-.065.466-.065zm2.8 2.142c.358 1.417 1.196 3.475 1.735 4.473.286.534.855 1.659 1.102 3.024.156-.005.33.018.513.064.646-1.671-.546-3.467-1.089-3.966-.22-.2-.232-.335-.123-.335.59.534 1.365 1.572 1.646 2.757.13.535.16 1.104.021 1.67.067.028.135.06.205.067 1.032.534 1.413.938 1.23 1.537v-.043c-.06-.003-.12 0-.18 0h-.016c.151-.467-.182-.825-1.065-1.224-.915-.4-1.646-.336-1.77.465-.008.043-.013.066-.018.135-.068.023-.139.053-.209.064-.43.268-.662.669-.793 1.187-.13.533-.17 1.156-.205 1.869v.003c-.02.334-.17.838-.319 1.35-1.5 1.072-3.58 1.538-5.348.334a2.645 2.645 0 00-.402-.533 1.45 1.45 0 00-.275-.333c.182 0 .338-.03.465-.067a.615.615 0 00.314-.334c.108-.267 0-.697-.345-1.163-.345-.467-.931-.995-1.788-1.521-.63-.4-.986-.87-1.15-1.396-.165-.534-.143-1.085-.015-1.645.245-1.07.873-2.11 1.274-2.763.107-.065.037.135-.408.974-.396.751-1.14 2.497-.122 3.854a8.123 8.123 0 01.647-2.876c.564-1.278 1.743-3.504 1.836-5.268.048.036.217.135.289.202.218.133.38.333.59.465.21.201.477.335.876.335.039.003.075.006.11.006.412 0 .73-.134.997-.268.29-.134.52-.334.74-.4h.005c.467-.135.835-.402 1.044-.7zm2.185 8.958c.037.6.343 1.245.882 1.377.588.134 1.434-.333 1.791-.765l.211-.01c.315-.007.577.01.847.268l.003.003c.208.199.305.53.391.876.085.4.154.78.409 1.066.486.527.645.906.636 1.14l.003-.007v.018l-.003-.012c-.015.262-.185.396-.498.595-.63.401-1.746.712-2.457 1.57-.618.737-1.37 1.14-2.036 1.191-.664.053-1.237-.2-1.574-.898l-.005-.003c-.21-.4-.12-1.025.056-1.69.176-.668.428-1.344.463-1.897.037-.714.076-1.335.195-1.814.12-.465.308-.797.641-.984l.045-.022zm-10.814.049h.01c.053 0 .105.005.157.014.376.055.706.333 1.023.752l.91 1.664.003.003c.243.533.754 1.064 1.189 1.637.434.598.77 1.131.729 1.57v.006c-.057.744-.48 1.148-1.125 1.294-.645.135-1.52.002-2.395-.464-.968-.536-2.118-.469-2.857-.602-.369-.066-.61-.2-.723-.4-.11-.2-.113-.602.123-1.23v-.004l.002-.003c.117-.334.03-.752-.027-1.118-.055-.401-.083-.71.043-.94.16-.334.396-.4.69-.533.294-.135.64-.202.915-.47h.002v-.002c.256-.268.445-.601.668-.838.19-.201.38-.336.663-.336zm7.159-9.074c-.435.201-.945.535-1.488.535-.542 0-.97-.267-1.28-.466-.154-.134-.28-.268-.373-.335-.164-.134-.144-.333-.074-.333.109.016.129.134.199.2.096.066.215.2.36.333.292.2.68.467 1.167.467.485 0 1.053-.267 1.398-.466.195-.135.445-.334.648-.467.156-.136.149-.267.279-.267.128.016.034.134-.147.332a8.097 8.097 0 01-.69.468zm-1.082-1.583V5.64c-.006-.02.013-.042.029-.05.074-.043.18-.027.26.004.063 0 .16.067.15.135-.006.049-.085.066-.135.066-.055 0-.092-.043-.141-.068-.052-.018-.146-.008-.163-.065zm-.551 0c-.02.058-.113.049-.166.066-.047.025-.086.068-.14.068-.05 0-.13-.02-.136-.068-.01-.066.088-.133.15-.133.08-.031.184-.047.259-.005.019.009.036.03.03.05v.02h.003z',
      },
      {
        name: 'CNCF',
        href: 'https://www.cncf.io',
        color: '#231F20',
        path: 'M0 0v24h24V0H8.004Zm3.431 3.431h4.544l.029.023 4.544 4.544h3.03l-4.572-4.567h9.569v9.563l-.789-.782-3.784-3.79v3.03l2.271 2.272 2.272 2.272.029.03v4.543h-4.55l-.023-.023-2.272-2.278-2.272-2.272H8.427l3.785 3.79.782.783H3.43v-9.563l4.573 4.567v-3.031l-4.55-4.544-.023-.023Z',
      },
    ],
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

    <div class="home-split">
    <section class="panel partners" aria-labelledby="home-partners-title">
      <h2 id="home-partners-title">组织方及合作伙伴</h2>
      <div class="partner-groups">
        <div v-for="group in partnerGroups" :key="group.title">
          <h3>{{ group.title }}</h3>
          <ul class="partner-list">
            <li v-for="item in group.items" :key="item.name">
              <a class="partner-link" :href="item.href" target="_blank" rel="noopener noreferrer">
                <span class="partner-mark" :style="{ color: item.color }" aria-hidden="true">
                  <svg v-if="item.path" viewBox="0 0 24 24" aria-hidden="true">
                    <path :d="item.path" fill="currentColor" />
                  </svg>
                  <span v-else>{{ item.mark }}</span>
                </span>
                <span>{{ item.name }}</span>
              </a>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <section class="contact" aria-labelledby="home-contact-title">
      <div class="contact-row">
        <span class="contact-mark" aria-hidden="true">WHU</span>
        <span class="contact-name">WHU Dev</span>
        <p id="home-contact-title" class="contact-mail">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor" d="M3 5.5A2.5 2.5 0 0 1 5.5 3h13A2.5 2.5 0 0 1 21 5.5v13a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 18.5v-13Zm2.2.7 6.3 4.4a.9.9 0 0 0 1 0l6.3-4.4H5.2Zm13.3 1.6-5.7 4a2.4 2.4 0 0 1-2.6 0l-5.7-4V18.5c0 .3.2.5.5.5h13c.3 0 .5-.2.5-.5V7.8Z" />
          </svg>
          <span>官方邮箱：</span>
          <a href="mailto:contact@whudev.org">contact@whudev.org</a>
        </p>
      </div>
      <a class="contact-org" href="https://whues.org/" target="_blank" rel="noopener noreferrer">Evolving Systems Lab @ Wuhan University</a>
    </section>
    </div>
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
  background: var(--heading-surface);
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
.home-split {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}
.home-split .contact {
  align-self: stretch;
  width: 100vw;
  margin: 36px 0 0 calc(50% - 50vw);
  padding: 32px max(24px, calc((100vw - 1140px) / 2)) 24px;
  box-sizing: border-box;
  background: #f3f5f8;
  border-top: 1px solid #e6ebf1;
}
.partners { padding-bottom: 4px; }
.partner-groups {
  display: grid;
  gap: 22px;
}
.partner-groups h3 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 650;
  color: #64748b;
}
.partner-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.partner-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  color: #1e293b;
  font-size: 15px;
  font-weight: 650;
  text-decoration: none;
}
.partner-link:hover {
  color: #1d4ed8;
}
.partner-link:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 3px;
  border-radius: 8px;
}
.partner-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: none;
  font-size: 13px;
  font-weight: 750;
  line-height: 1;
}
.partner-mark svg {
  width: 22px;
  height: 22px;
  display: block;
}
.contact-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px 22px;
}
.contact-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #2563eb;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
}
.contact-name {
  font-family: "Sora", "Noto Sans SC", sans-serif;
  font-size: 22px;
  font-weight: 750;
  letter-spacing: -0.03em;
  color: #64748b;
}
.contact-mail {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: #4b5b73;
  font-size: 16px;
}
.contact-mail svg {
  width: 18px;
  height: 18px;
  color: #94a3b8;
}
.contact-mail a {
  color: #4b5b73;
  font-weight: 650;
  text-decoration: none;
}
.contact-mail a:hover {
  color: #2563eb;
}
.contact-org {
  display: inline-block;
  margin-top: 18px;
  color: #666;
  font-size: 14px;
  text-decoration: none;
}
.contact-org:hover {
  color: #2563eb;
}
.contact-mail a:focus-visible,
.contact-org:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 3px;
  border-radius: 4px;
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
@media (max-width: 767px) {
  .hero { padding: 22px 16px; }
  .hero-art { display: none; }
  .hero h1 { font-size: 26px; }
  .hero-actions { gap: 12px; flex-wrap: wrap; }
  .hero-actions .arco-btn { min-height: 44px; }
  .panel { padding: 16px; }
  .home-split .contact { margin-left: 0; margin-right: 0; padding-left: 0; padding-right: 0; }
}
</style>
