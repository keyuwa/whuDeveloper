export type Role = 'STUDENT' | 'PUBLISHER' | 'ADMIN'

export interface User {
  id: number
  email: string
  role: Role
  displayName: string
  realName: string | null
  school?: string | null
  college?: string | null
  major?: string | null
  studentIdentity?: string | null
  bio?: string | null
  resumeFileUrl?: string | null
  resumeFileName?: string | null
  abilityLevel: string | null
  abilityApprentice?: boolean
  abilityMgmt?: boolean
  abilityLabel?: string | null
  points?: number
  status: string
  resumeReady?: boolean
}

export interface Task {
  id: number
  publicCode: string
  title: string
  roleType: string
  techField: string
  difficulty: string
  background: string
  techStack: string
  programmingLanguages: string
  supportLanguages: string
  outputRequirement: string
  techRequirement: string
  durationWeeks: number
  scheduleType: string | null
  educationLevels: string | null
  estimatedHours: number
  repoUrl: string | null
  learningMaterials: string | null
  stipendText: string | null
  prdMd: string | null
  prdFileUrl: string | null
  prdFileName: string | null
  techDocMd: string | null
  techDocFileUrl: string | null
  techDocFileName: string | null
  quota: number
  filled: number
  pendingApplications?: number
  status: string
  postingMode?: string
  bidDeadline?: string | null
  challengeStatus?: string | null
  publisherId: number
  publisherName: string
  createdAt: string
  myApplicationId: number | null
  myApplicationStatus: string | null
  projectId: number | null
  shortlisted?: boolean
}

export interface TaskPage {
  items: Task[]
  page: number
  size: number
  total: number
  hasMore: boolean
}

export interface PublisherTaskPage extends TaskPage {
  totalAll: number
  totalOpen: number
  totalDraft: number
  totalClosed: number
  totalTa: number
  totalIntern: number
  totalRa: number
}

export interface Application {
  id: number
  taskId: number
  taskTitle: string
  taskType: string
  taskRoleType?: string
  postingMode?: string
  studentId: number
  studentName: string
  realName: string | null
  school: string | null
  college: string | null
  major: string | null
  studentIdentity: string | null
  bio: string | null
  resumeFileUrl: string | null
  resumeFileName: string | null
  abilityLevel: string | null
  proposalFileName: string | null
  proposalMd: string
  status: string
  interviewAt: string | null
  interviewNote: string | null
  reviewNote: string | null
  remindCount: number
  aiScore?: number | null
  aiSummary?: string | null
  aiDetailJson?: string | null
  aiStatus?: string | null
  aiScoredAt?: string | null
  createdAt: string
  projectId: number | null
}

export interface Milestone {
  id: number
  seq: number
  title: string
  goal: string
  evidence: string
  status: string
  note: string | null
  deliverableUrl: string | null
}

export interface Report {
  id: number
  weekIndex: number
  content: string
  blockers: string | null
  plan: string | null
  supportNeeded: string | null
  status: string
  score: number | null
  scoreComment: string | null
  createdAt: string | null
}

export interface Completion {
  id: number
  reportMd: string | null
  reportFileUrl: string | null
  reportFileName: string | null
  prUrls: string[]
  status: string
  reviewNote: string | null
}

export interface Project {
  id: number
  taskId: number
  taskTitle: string
  taskType: string
  taskRoleType?: string
  studentId: number
  studentName: string
  abilityLevel: string | null
  publisherId: number
  publisherName: string
  status: string
  proposalFileName: string | null
  proposalMd: string
  applicationId: number
  enrolledAt: string
  milestones: Milestone[]
  reports: Report[]
  completion: Completion | null
}

export interface ThreadSummary {
  taskId: number
  taskTitle: string
  publisherId: number
  publisherName: string
  studentId: number
  studentName: string
  lastBody: string
  createdAt: string
}

export interface ChatMessage {
  id: number
  taskId: number
  studentId: number
  senderId: number
  senderName: string
  body: string
  createdAt: string
}

export interface Notice {
  id: number
  title: string
  body: string
  readAt: string | null
  createdAt: string
}

export interface Reminder {
  id: number
  type: string
  targetType: string
  targetId: number
  status: string
  dedupeKey: string
  createdAt: string
}

export interface AuditEvent {
  id: number
  actorId: number | null
  entityType: string
  entityId: number
  action: string
  detail: string | null
  createdAt: string
}

export const TASK_ROLE_TYPES = [
  { value: 'TA', label: '助教 TA' },
  { value: 'INTERN', label: '开发 Intern' },
  { value: 'RA', label: '科研 RA' },
] as const

export const TASK_ROLE_LABEL: Record<string, string> = {
  TA: '助教 TA',
  INTERN: '开发 Intern',
  RA: '科研 RA',
}

export const CHALLENGE_STATUS_LABEL: Record<string, string> = {
  OPEN: '揭榜中',
  SELECTING: '比选中',
  AWARDED: '已揭榜',
  CLOSED: '已截止',
}

export const CHALLENGE_PROPOSAL_TEMPLATE = `## 理解与切入点

## 研究/技术方案

## 已有基础与证据

## 周期与里程碑
`

export const TECH_FIELDS = ['前端开发', '后端开发', '全栈应用', '人工智能', '系统与底层', '开发工具', '数据与存储'] as const
export const RESEARCH_DIRECTIONS = [
  '人工智能',
  '机器学习与数据挖掘',
  '计算机视觉',
  '自然语言处理',
  '网络安全',
  '系统与网络',
  '软件工程',
  '人机交互',
  '边缘计算与物联网',
  '科学计算',
] as const
export const DIFFICULTIES = ['L1', 'L2', 'L3', 'L4', 'L5'] as const
export const SUPPORT_LANGUAGES = ['中文', 'English', '中英双语'] as const
export const PROGRAMMING_LANGUAGES = [
  'TypeScript', 'JavaScript', 'Java', 'Python', 'Go', 'C++', 'C', 'Rust', 'Vue', 'React', 'Swift', 'Kotlin', 'Markdown',
] as const

export const DURATION_OPTIONS = [
  { label: '不限', value: '' },
  { label: '4 周以内', value: '1-4' },
  { label: '5–8 周', value: '5-8' },
  { label: '9–12 周', value: '9-12' },
  { label: '12 周以上', value: '13-52' },
] as const

export const TA_SCHEDULE_OPTIONS = [
  { label: '不限', value: '' },
  { label: '本学期', value: 'CURRENT_SEMESTER' },
  { label: '下一学期', value: 'NEXT_SEMESTER' },
  { label: '长期', value: 'LONG_TERM' },
] as const

export const RA_SCHEDULE_OPTIONS = [
  { label: '不限', value: '' },
  { label: '1 学期', value: 'ONE_SEMESTER' },
  { label: '2 学期', value: 'TWO_SEMESTERS' },
  { label: '一年及以上', value: 'YEAR_PLUS' },
] as const

export const WEEKLY_HOURS_OPTIONS = [
  { label: '不限', value: '' },
  { label: '≤4 小时', value: '1-4' },
  { label: '5–8 小时', value: '5-8' },
  { label: '8 小时以上', value: '9-40' },
] as const

export const SCHEDULE_TYPE_LABEL: Record<string, string> = {
  CURRENT_SEMESTER: '本学期',
  NEXT_SEMESTER: '下一学期',
  LONG_TERM: '长期',
  ONE_SEMESTER: '1 学期',
  TWO_SEMESTERS: '2 学期',
  YEAR_PLUS: '一年及以上',
}

/** 本校在读学生身份（非已获学历） */
export const EDUCATION_LEVELS = ['本科生', '硕士生', '博士生'] as const

export const PROPOSAL_TEMPLATE = `## 自我介绍

## 相关经验

## 技术方案

## 时间计划
`
