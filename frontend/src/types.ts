export type Role = 'PUBLISHER' | 'STUDENT'
export type TaskType = 'TA' | 'INTERN'
export type ApplicationStatus = 'SUBMITTED' | 'ACCEPTED'
export type ProjectStatus = 'ENROLLED' | 'IN_PROGRESS' | 'COMPLETED'

export interface User {
  id: number
  username: string
  displayName: string
  role: Role
}

export interface ApplicationBrief {
  id: number
  proposal: string
  status: ApplicationStatus
  projectId: number | null
}

export interface Task {
  id: number
  title: string
  description: string
  type: TaskType
  status: string
  publisherId: number
  publisherName: string
  createdAt: string
  myApplication: ApplicationBrief | null
}

export interface Application {
  id: number
  taskId: number
  taskTitle: string
  taskType: TaskType
  studentId: number
  studentName: string
  proposal: string
  status: ApplicationStatus
  projectId: number | null
  createdAt: string
}

export interface Project {
  id: number
  taskId: number
  taskTitle: string
  taskType: TaskType
  studentId: number
  studentName: string
  publisherId: number
  publisherName: string
  status: ProjectStatus
  enrolledAt: string
}

export interface WeeklyReport {
  id: number
  projectId: number
  weekNumber: number
  content: string
  createdAt: string
}

export interface Completion {
  id: number
  projectId: number
  prUrl: string
  summary: string
  createdAt: string
}
