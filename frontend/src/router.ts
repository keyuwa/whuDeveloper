import { createRouter, createWebHistory } from 'vue-router'
import { auth } from './auth'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import PlazaView from './views/PlazaView.vue'
import TaskDetailView from './views/TaskDetailView.vue'
import ApplicationsView from './views/ApplicationsView.vue'
import ProjectsView from './views/ProjectsView.vue'
import ProjectView from './views/ProjectView.vue'
import MessagesView from './views/MessagesView.vue'
import PublisherTasksView from './views/PublisherTasksView.vue'
import TaskFormView from './views/TaskFormView.vue'
import PublisherInboxView from './views/PublisherInboxView.vue'
import AdminUsersView from './views/AdminUsersView.vue'
import AdminTasksView from './views/AdminTasksView.vue'
import AdminRemindersView from './views/AdminRemindersView.vue'
import AdminSettingsView from './views/AdminSettingsView.vue'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior() { return { top: 0 } },
  routes: [
    { path: '/', component: PlazaView },
    { path: '/tasks/:id', component: TaskDetailView },
    { path: '/auth/login', component: LoginView },
    { path: '/auth/register', component: RegisterView },
    { path: '/me/applications', component: ApplicationsView, meta: { role: 'STUDENT' } },
    { path: '/me/projects', component: ProjectsView },
    { path: '/me/projects/:id', component: ProjectView },
    { path: '/messages', component: MessagesView },
    { path: '/publisher/tasks', component: PublisherTasksView, meta: { role: 'PUBLISHER' } },
    { path: '/publisher/tasks/new', component: TaskFormView, meta: { role: 'PUBLISHER' } },
    { path: '/publisher/tasks/:id', component: TaskFormView, meta: { role: 'PUBLISHER' } },
    { path: '/publisher/inbox', component: PublisherInboxView, meta: { role: 'PUBLISHER' } },
    { path: '/publisher/projects/:id', component: ProjectView, meta: { role: 'PUBLISHER' } },
    { path: '/admin/users', component: AdminUsersView, meta: { role: 'ADMIN' } },
    { path: '/admin/tasks', component: AdminTasksView, meta: { role: 'ADMIN' } },
    { path: '/admin/reminders', component: AdminRemindersView, meta: { role: 'ADMIN' } },
    { path: '/admin/settings', component: AdminSettingsView, meta: { role: 'ADMIN' } },
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
