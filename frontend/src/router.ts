import { createRouter, createWebHistory } from 'vue-router'
import { auth } from './auth'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import PlazaView from './views/PlazaView.vue'
import TaskDetailView from './views/TaskDetailView.vue'
import WorkbenchView from './views/WorkbenchView.vue'
import ProjectShell from './views/project/ProjectShell.vue'
import ProjectDetailView from './views/project/ProjectDetailView.vue'
import ProjectMilestonesView from './views/project/ProjectMilestonesView.vue'
import ProjectCompletionView from './views/project/ProjectCompletionView.vue'
import MessagesView from './views/MessagesView.vue'
import ResumeView from './views/ResumeView.vue'
import PublisherTasksView from './views/PublisherTasksView.vue'
import TaskFormView from './views/TaskFormView.vue'
import PublisherInboxView from './views/PublisherInboxView.vue'
import AdminUsersView from './views/AdminUsersView.vue'
import AdminTasksView from './views/AdminTasksView.vue'
import AdminRemindersView from './views/AdminRemindersView.vue'
import AdminSettingsView from './views/AdminSettingsView.vue'
import AdminDictsView from './views/AdminDictsView.vue'
import AbilityView from './views/AbilityView.vue'
import AbilityRubricView from './views/AbilityRubricView.vue'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior() {
    return { top: 0 }
  },
  routes: [
    { path: '/', component: PlazaView },
    { path: '/tasks/:id', component: TaskDetailView },
    { path: '/auth/login', component: LoginView },
    { path: '/auth/register', component: RegisterView },
    { path: '/me/applications', redirect: '/me/workbench/applications' },
    { path: '/me/projects', redirect: '/me/workbench' },
    { path: '/me/resume', component: ResumeView, meta: { role: 'STUDENT' } },
    { path: '/me/workbench', component: WorkbenchView, meta: { role: 'STUDENT' } },
    { path: '/me/workbench/applications', component: WorkbenchView, meta: { role: 'STUDENT' } },
    { path: '/me/workbench/candidates', component: WorkbenchView, meta: { role: 'STUDENT' } },
    {
      path: '/me/projects/:id',
      component: ProjectShell,
      meta: { role: 'STUDENT' },
      children: [
        { path: '', redirect: (to) => `/me/projects/${to.params.id}/detail` },
        { path: 'detail', component: ProjectDetailView },
        { path: 'progress', component: ProjectMilestonesView },
        { path: 'milestones', redirect: (to) => `/me/projects/${to.params.id}/progress` },
        { path: 'reports', redirect: (to) => `/me/projects/${to.params.id}/progress` },
        { path: 'completion', component: ProjectCompletionView },
      ],
    },
    { path: '/messages', component: MessagesView },
    { path: '/ability', component: AbilityView },
    { path: '/ability/rubric', component: AbilityRubricView },
    { path: '/publisher/tasks', component: PublisherTasksView, meta: { role: 'PUBLISHER' } },
    { path: '/publisher/tasks/new', component: TaskFormView, meta: { role: 'PUBLISHER' } },
    { path: '/publisher/tasks/:id', component: TaskFormView, meta: { role: 'PUBLISHER' } },
    { path: '/publisher/inbox', component: PublisherInboxView, meta: { role: 'PUBLISHER' } },
    {
      path: '/publisher/projects/:id',
      component: ProjectShell,
      meta: { role: 'PUBLISHER' },
      children: [
        { path: '', redirect: (to) => `/publisher/projects/${to.params.id}/detail` },
        { path: 'detail', component: ProjectDetailView },
        { path: 'progress', component: ProjectMilestonesView },
        { path: 'milestones', redirect: (to) => `/publisher/projects/${to.params.id}/progress` },
        { path: 'reports', redirect: (to) => `/publisher/projects/${to.params.id}/progress` },
        { path: 'completion', component: ProjectCompletionView },
      ],
    },
    { path: '/admin/users', component: AdminUsersView, meta: { role: 'ADMIN' } },
    { path: '/admin/tasks', component: AdminTasksView, meta: { role: 'ADMIN' } },
    { path: '/admin/reminders', component: AdminRemindersView, meta: { role: 'ADMIN' } },
    { path: '/admin/settings', component: AdminSettingsView, meta: { role: 'ADMIN' } },
    { path: '/admin/dicts', component: AdminDictsView, meta: { role: 'ADMIN' } },
  ],
})

router.beforeEach((to) => {
  const role = to.meta.role as string | undefined
  const authed = Boolean(auth.token)
  if (role && !authed) return '/auth/login'
  if ((to.path.startsWith('/me') || to.path.startsWith('/messages') || to.path.startsWith('/publisher') || to.path.startsWith('/admin')) && !authed) {
    return '/auth/login'
  }
  if (role && auth.user && auth.user.role !== role) return '/'
  if ((to.path === '/auth/login' || to.path === '/auth/register') && authed) return '/'
  return true
})
