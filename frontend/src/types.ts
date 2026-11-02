export type Role = 'STUDENT' | 'PUBLISHER' | 'ADMIN'

export interface User {
  id: number
  email: string
  role: Role
  displayName: string
  realName: string | null
  abilityLevel: string | null
  status: string
}

export interface Task {
  id: number
  title: string
  roleType: 'TA' | 'INTERN' | 'RA'
  difficulty: string
  background: string
  techStack: string
  outputRequirement: string
  techRequirement: string
  durationWeeks: number
  estimatedHours: number
  repoUrl: string | null
  learningMaterials: string | null
  stipendText: string
  prdMd: string
  techDocMd: string | null
  quota: number
  filled: number
  status: string
  publisherId: number
  publisherName: string
  createdAt: string
  myApplicationId: number | null
  myApplicationStatus: string | null
  projectId: number | null
}

export interface Application {
  id: number
  taskId: number
  taskTitle: string
  taskType: string
  studentId: number
  studentName: string
  abilityLevel: string | null
  proposalMd: string
  status: string
  interviewAt: string | null
  interviewNote: string | null
  reviewNote: string | null
  remindCount: number
  createdAt: string
  projectId: number | null
}

export interface Milestone {
  id: number
  seq: number
  title: string
  status: string
  note: string | null
  deliverableUrl: string | null
}

export interface Report {
  id: number
  weekIndex: number
  content: string
  plan: string | null
  status: string
  score: number | null
  scoreComment: string | null
}

export interface Completion {
  id: number
  reportMd: string
  prUrls: string[]
  status: string
  reviewNote: string | null
}

export interface Project {
  id: number
  taskId: number
  taskTitle: string
  taskType: string
  studentId: number
  studentName: string
  abilityLevel: string | null
  publisherId: number
  publisherName: string
  status: string
  enrolledAt: string
  milestones: Milestone[]
  reports: Report[]
  completion: Completion | null
}

export interface ThreadSummary {
  taskId: number
  taskTitle: string
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

export const PROPOSAL_TEMPLATE = `## 自我介绍

## 相关经验

## 技术方案

## 时间计划
`
