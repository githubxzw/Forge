import axios from 'axios'

export const http = axios.create({ baseURL: '/api', timeout: 60000 })

http.interceptors.request.use(cfg => {
  const token = localStorage.getItem('forge_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

http.interceptors.response.use(
  resp => resp,
  err => {
    if (err.response?.status === 401 && !location.pathname.startsWith('/login')) {
      localStorage.removeItem('forge_token')
      localStorage.removeItem('forge_user')
      location.href = '/login'
    }
    return Promise.reject(err)
  }
)

/** 统一解包 ApiResult{code,message,data}；失败弹错并 reject */
export async function unwrap<T = any>(p: Promise<any>): Promise<T> {
  try {
    const resp = await p
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0) throw new Error(body.message || '请求失败')
      return body.data as T
    }
    return body as T
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '网络错误'
    const { ElMessage } = await import('element-plus')
    ElMessage.error(msg)
    throw new Error(msg)
  }
}
