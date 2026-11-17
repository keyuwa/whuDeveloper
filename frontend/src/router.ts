import { createRouter, createWebHistory } from 'vue-router'
import { auth } from './auth'

const LoginView = () => import('./views/LoginView.vue')
const RegisterView = () => import('./views/RegisterView.vue')
const PlazaView = () => import('./views/PlazaView.vue')
const TaskDetailView = () => import('./views/TaskDetailView.vue')
const WorkbenchView = () => import('./views/WorkbenchView.vue')
const ProjectShell = () => import('./views/project/ProjectShell.vue')
const ProjectDetailView = () => import('./views/project/ProjectDetailView.vue')
const ProjectApplicationView = () => import('./views/project/ProjectApplicationView.vue')
const ProjectMilestonesView = () => import('./views/project/ProjectMilestonesView.vue')
const ProjectCompletionView = () => import('./views/project/ProjectCompletionView.vue')
const MessagesView = () => import('./views/MessagesView.vue')
const ResumeView = () => import('./views/ResumeView.vue')
const MeHomeView = () => import('./views/MeHomeView.vue')
const PublisherTasksView = () => import('./views/PublisherTasksView.vue')
const TaskFormView = () => import('./views/TaskFormView.vue')
const PublisherInboxView = () => import('./views/PublisherInboxView.vue')
const AdminUsersView = () => import('./views/AdminUsersView.vue')
const AdminTasksView = () => import('./views/AdminTasksView.vue')
const AdminRemindersView = () => import('./views/AdminRemindersView.vue')
const AdminSettingsView = () => import('./views/AdminSettingsView.vue')
const AdminDictsView = () => import('./views/AdminDictsView.vue')
const AbilityView = () => import('./views/AbilityView.vue')
const AbilityRubricView = () => import('./views/AbilityRubricView.vue')

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition || { top: 0 }
  },
  routes: [
    { path: '/', component: AbilityView },
    { path: '/plaza', component: PlazaView },
    { path: '/tasks/:id', component: TaskDetailView },
    { path: '/auth/login', component: LoginView },
    { path: '/auth/register', component: RegisterView },
    { path: '/me/applications', redirect: '/me/workbench/applications' },
    { path: '/me/projects', redirect: '/me/workbench' },
    { path: '/me', component: MeHomeView, meta: { role: 'STUDENT' } },
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
        { path: 'application', component: ProjectApplicationView },
        { path: 'progress', component: ProjectMilestonesView },
        { path: 'milestones', redirect: (to) => `/me/projects/${to.params.id}/progress` },
        { path: 'reports', redirect: (to) => `/me/projects/${to.params.id}/progress` },
        { path: 'completion', component: ProjectCompletionView },
      ],
    },
    { path: '/messages', component: MessagesView },
    { path: '/ability', redirect: '/' },
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
        { path: 'application', component: ProjectApplicationView },
        { path: 'ai', redirect: (to) => `/publisher/projects/${to.params.id}/application` },
        { path: 'progress', component: ProjectMilestonesView },
        { path: 'milestones', redirect: (to) => `/publisher/projects/${to.params.id}/progress` },
        { path: 'reports', redirect: (to) => `/publisher/projects/${to.params.id}/progress` },
        { path: 'completion', component: ProjectCompletionView },
      ],
    },
    { path: '/admin/users', component: AdminUsersView, meta: { role: 'ADMIN' } },
    { path: '/admin/tasks', component: AdminTasksView, meta: { role: 'ADMIN' } },
    { path: '/admin/reminders', component: AdminRemindersView, meta: { role: 'ADMIN' } },
    { path: '/admin/settings', redirect: '/admin/settings/ark', meta: { role: 'ADMIN' } },
    { path: '/admin/settings/:section', component: AdminSettingsView, meta: { role: 'ADMIN' } },
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
