import type { ApplicationStatus, ProjectStatus, Role, TaskType } from './types'

export const taskTypeLabel: Record<TaskType, string> = {
  TA: '课程助教',
  INTERN: '开发实习',
}

export const applicationStatusLabel: Record<ApplicationStatus, string> = {
  SUBMITTED: '待录取',
  ACCEPTED: '已录取',
}

export const projectStatusLabel: Record<ProjectStatus, string> = {
  ENROLLED: '已入学',
  IN_PROGRESS: '进行中',
  COMPLETED: '已结项',
}

export const roleLabel: Record<Role, string> = {
  PUBLISHER: '发布者',
  STUDENT: '学生',
}
