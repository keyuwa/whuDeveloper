import { reactive } from 'vue'
import type { User } from './types'

function readUser(): User | null {
  const raw = localStorage.getItem('user')
  if (!raw) return null
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export const auth = reactive({
  token: localStorage.getItem('token') ?? '',
  user: readUser(),
})

export function setSession(token: string, user: User) {
  auth.token = token
  auth.user = user
  localStorage.setItem('token', token)
  localStorage.setItem('user', JSON.stringify(user))
}

export function logout() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem('token')
  localStorage.removeItem('user')
}
