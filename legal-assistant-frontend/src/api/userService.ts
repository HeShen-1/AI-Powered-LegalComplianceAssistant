import apiClient from './index'
import type { 
  LoginPayload, 
  RegisterPayload, 
  User, 
  ApiResponse 
} from '@/types/api'

// 用户登录
export const loginApi = (data: LoginPayload) => {
  const params = new URLSearchParams()
  params.append('username', data.username)
  params.append('password', data.password)

  return apiClient.post('/login', params)
}

// 用户注册
export const registerApi = (data: RegisterPayload) => {
  return apiClient.post<ApiResponse<{ userId: number; username: string }>>('/users/register', data)
}

// 获取用户信息
export const getUserInfoApi = (userId: number) => {
  return apiClient.get<User>(`/users/${userId}`)
}

// 更新用户信息
export const updateUserApi = (userId: number, data: Partial<User>) => {
  return apiClient.put<ApiResponse<{ user: User }>>(`/users/${userId}`, data)
}

// 修改密码
export const changePasswordApi = (userId: number, data: { oldPassword: string; newPassword: string }) => {
  return apiClient.post<ApiResponse>(`/users/${userId}/change-password`, data)
}

// 启用/禁用用户（管理员）
export const toggleUserStatusApi = (userId: number, enabled: boolean) => {
  return apiClient.post<ApiResponse>(`/users/${userId}/toggle-status`, { enabled })
}
