import { createRouter, createWebHistory } from 'vue-router'
import { auth } from './auth'
import LoginView from './views/LoginView.vue'
import TasksView from './views/TasksView.vue'
import ApplicationView from './views/ApplicationView.vue'
import ProjectView from './views/ProjectView.vue'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior() {
    return { top: 0 }
  },
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/login', component: LoginView },
    { path: '/tasks', component: TasksView },
    { path: '/tasks/:id', component: TasksView },
    { path: '/tasks/:id/application', component: ApplicationView },
    { path: '/projects', component: ProjectView },
    { path: '/projects/:id', component: ProjectView },
  ],
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !auth.token) return '/login'
  if (to.path === '/login' && auth.token) return '/tasks'
  return true
})
