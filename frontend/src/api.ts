import { logout } from './auth'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = localStorage.getItem('token')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, '无法连接服务器，请确认服务已启动')
  }

  if (response.status === 401 && !path.endsWith('/api/auth/login')) {
    logout()
    if (location.pathname !== '/login') location.assign('/login')
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    let message = '请求失败'
    try {
      const body = (await response.json()) as { message?: string }
      if (body.message) message = body.message
    } catch {
      message = '请求失败'
    }
    throw new ApiError(response.status, message)
  }

  return (await response.json()) as T
}
