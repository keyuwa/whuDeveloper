import type { Task } from './types'

export interface RecommendationRule {
  majors: string[]
  keywords: string[]
  field: 'DIRECTION' | 'TITLE' | 'CONTENT' | 'LANGUAGE' | 'POSITION' | 'ANY'
  score: number
}

export interface RecommendationConfig {
  ta: RecommendationRule[] | null
  intern: RecommendationRule[] | null
  ra: RecommendationRule[] | null
  team: RecommendationRule[] | null
  masterSlots: number
  doctorSlots: number
}

const channels = { TA: 'ta', INTERN: 'intern', RA: 'ra', TEAM: 'team' } as const

function configuredScore(subject: string, task: Task, rules: RecommendationRule[]): number {
  const normalize = (text: string) => text.normalize('NFKC').trim().toLowerCase()
  const fields = {
    DIRECTION: task.techField,
    TITLE: task.title,
    CONTENT: [task.background, task.outputRequirement, task.techRequirement, task.techStack].join(' '),
    LANGUAGE: task.programmingLanguages,
    POSITION: (task.positions || []).map((position) => `${position.title} ${position.direction || ''}`).join(' '),
  }
  const major = normalize(subject)
  let score = 0
  for (const rule of rules) {
    if (rule.majors.length && !rule.majors.some((alias) => major.includes(normalize(alias)))) continue
    const text = normalize(rule.field === 'ANY' ? Object.values(fields).join(' ') : fields[rule.field] || '')
    if (rule.keywords.some((keyword) => text.includes(normalize(keyword)))) score += rule.score
  }
  return score
}

const HINTS: { test: RegExp; words: string[] }[] = [
  { test: /计算机|软件|网安|网络安全|信息安全|网络空间/, words: ['软件', '网络安全', '系统与网络', '人工智能', '全栈应用', '前端开发', '后端开发', '系统与底层', 'Java', 'Python', '程序', '算法', '安全'] },
  { test: /人工智能|智能科学|机器学习/, words: ['人工智能', '机器学习与数据挖掘', '计算机视觉', '自然语言处理', 'Python'] },
  { test: /数学|统计|应用数学/, words: ['科学计算', '机器学习与数据挖掘', '数学', '离散', '统计'] },
  { test: /电子|通信|物联网|自动化/, words: ['系统与网络', '边缘计算与物联网', '系统与底层', '通信', '嵌入'] },
  { test: /设计|视觉|数字媒体/, words: ['人机交互', '前端开发', '计算机视觉', '设计'] },
  { test: /中文|外语|语言|新闻|传播/, words: ['自然语言处理', '人机交互', '标注', '语料'] },
]

const RESEARCH_HINTS: { major: RegExp; directions: string[]; topics: RegExp }[] = [
  {
    major: /计算机|软件|信息工程|网络|通信|网安|computer science|software engineering|cybersecurity/i,
    directions: ['软件工程', '系统与网络', '网络安全', '人工智能', '数据库', '人机交互', '计算机体系结构'],
    topics: /软件测试|程序分析|操作系统|分布式|网络测量|wi[ -]?fi|设备指纹|流量分析|协议|系统安全|机器学习/i,
  },
  {
    major: /计算机|人工智能|智能科学|机器学习|数据科学|统计|数学|computer science|data science|artificial intelligence/i,
    directions: ['人工智能', '机器学习与数据挖掘', '计算机视觉', '自然语言处理', '科学计算'],
    topics: /模型评估|数据挖掘|机器学习|深度学习|图像识别|大模型|实验设计|数值模拟/i,
  },
  {
    major: /电子|通信|物联网|自动化|遥感|测绘|地理|robotics|geomatics/i,
    directions: ['边缘计算与物联网', '系统与网络', '计算机视觉', '地理信息'],
    topics: /嵌入式|传感器|信号处理|边缘设备|空间分析|遥感影像|地理信息/i,
  },
]

export function isResearchTask(task: Pick<Task, 'roleType' | 'postingMode'>): boolean {
  return task.roleType === 'RA' || task.postingMode === 'CHALLENGE'
}

function researchScore(subject: string, task: Task): number {
  const directions = new Set<string>()
  const topicText = [task.title, task.background, task.outputRequirement, task.techRequirement].join(' ')
  let topicMatch = false
  for (const hint of RESEARCH_HINTS) {
    if (!hint.major.test(subject)) continue
    hint.directions.forEach((direction) => directions.add(direction))
    if (hint.topics.test(topicText)) topicMatch = true
  }
  const matchingDirection = task.techField.trim() === subject || directions.has(task.techField.trim())
  return (matchingDirection ? 8 : 0) + (topicMatch ? 4 : 0) + (topicText.includes(subject) ? 5 : 0)
}

export function recommendTasks(major: string, identity: string, tasks: Task[], config?: RecommendationConfig): Task[] {
  const ranked = tasks
    .filter((task) => task.status === 'OPEN' && !task.myApplicationId && !task.projectId)
    .filter((task) => !isResearchTask(task) ||
      ((!task.challengeStatus || task.challengeStatus === 'OPEN') &&
        (!task.bidDeadline || new Date(task.bidDeadline).getTime() > Date.now())))
    .map((task) => ({ task, score: majorScore(major, task, config) }))
    .filter(({ score }) => score > 0)
    .sort((a, b) => b.score - a.score)
    .map(({ task }) => task)

  const researchSlots = /博士|ph\.?d\.?|doctor/i.test(identity) ? (config?.doctorSlots ?? 3) : /硕士|master/i.test(identity) ? (config?.masterSlots ?? 2) : 0
  const research = ranked.filter(isResearchTask).slice(0, researchSlots)
  const selected = new Set(research.map((task) => task.id))
  return [...research, ...ranked.filter((task) => !selected.has(task.id))].slice(0, 4)
}

export function majorScore(major: string, task: Task, config?: RecommendationConfig): number {
  const subject = major.trim()
  if (!subject) return 0
  const channel = channels[task.roleType as keyof typeof channels]
  const rules = channel ? config?.[channel] : null
  if (rules) return configuredScore(subject, task, rules)
  if (isResearchTask(task)) return researchScore(subject, task)
  const text = [
    task.title,
    task.techField,
    task.background,
    task.techStack,
    task.programmingLanguages,
    ...(task.positions || []).map((item) => `${item.title} ${item.direction || ''}`),
  ].join(' ')
  let score = 0
  if (text.includes(subject)) score += 5
  for (const hint of HINTS) {
    if (!hint.test.test(subject)) continue
    for (const word of hint.words) {
      if (text.includes(word)) score += 2
    }
  }
  return score
}
