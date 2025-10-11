import apiClient from './index'
import { LoginPayload, RegisterPayload, LoginResponse } from '@/types/api'

// 登录接口，使用JWT认证
export const loginApi = (data: LoginPayload) => {
  // 后端登录接口使用JSON格式，不需要URLSearchParams
  return apiClient.post<LoginResponse>('/auth/login', {
    username: data.username,
    password: data.password
  })
}

// 注册接口
export const registerApi = (data: RegisterPayload) => {
  return apiClient.post('/auth/register', data)
}

// 获取用户信息
export const getUserInfoApi = () => {
  return apiClient.get('/users/me')
}

// 刷新Token
export const refreshTokenApi = () => {
  return apiClient.post('/auth/refresh')
}

// 登出
export const logoutApi = () => {
  return apiClient.post('/auth/logout')
}
