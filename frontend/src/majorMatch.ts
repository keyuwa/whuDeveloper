import type { Task } from './types'

const HINTS: { test: RegExp; words: string[] }[] = [
  { test: /计算机|软件|网安|网络安全|信息安全|网络空间/, words: ['网络安全', '系统与网络', '人工智能', '全栈应用', '前端开发', '后端开发', '系统与底层', 'Java', 'Python', '程序', '算法', '安全'] },
  { test: /人工智能|智能科学|机器学习/, words: ['人工智能', '机器学习与数据挖掘', '计算机视觉', '自然语言处理', 'Python'] },
  { test: /数学|统计|应用数学/, words: ['科学计算', '机器学习与数据挖掘', '数学', '离散', '统计'] },
  { test: /电子|通信|物联网|自动化/, words: ['系统与网络', '边缘计算与物联网', '系统与底层', '通信', '嵌入'] },
  { test: /设计|视觉|数字媒体/, words: ['人机交互', '前端开发', '计算机视觉', '设计'] },
  { test: /中文|外语|语言|新闻|传播/, words: ['自然语言处理', '人机交互', '标注', '语料'] },
]

export function majorScore(major: string, task: Pick<Task, 'title' | 'techField' | 'background' | 'techStack' | 'programmingLanguages' | 'positions'>): number {
  const subject = major.trim()
  if (!subject) return 0
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
