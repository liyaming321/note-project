import axios from 'axios'

import type { ApiResponse } from '@/types/api'

export const httpClient = axios.create({
  baseURL: '/api',
  timeout: 15000
})

httpClient.interceptors.response.use(
  response => response,
  error => {
    const message = error.response?.data?.message ?? error.message ?? '请求失败'
    return Promise.reject(new Error(message))
  }
)

export async function unwrapData<T>(request: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await request
  return response.data.data
}
