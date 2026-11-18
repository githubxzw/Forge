import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('forge_token') || '',
    username: localStorage.getItem('forge_user') || '',
    // 升级前的会话没有 role 缓存，默认 ADMIN（后端会按库内角色二次校验，这里只影响 UI 展示）
    role: localStorage.getItem('forge_role') || 'ADMIN'
  }),
  getters: {
    loggedIn: s => !!s.token,
    isAdmin: s => s.role === 'ADMIN',
    isOperator: s => s.role === 'ADMIN' || s.role === 'OPERATOR',
    roleName: s => ({ ADMIN: '管理员', OPERATOR: '操作员', VIEWER: '观察者' }[s.role] || s.role)
  },
  actions: {
    setSession(token: string, username: string, role: string) {
      this.token = token
      this.username = username
      this.role = role || 'VIEWER'
      localStorage.setItem('forge_token', token)
      localStorage.setItem('forge_user', username)
      localStorage.setItem('forge_role', this.role)
    },
    logout() {
      this.token = ''
      this.username = ''
      this.role = ''
      localStorage.removeItem('forge_token')
      localStorage.removeItem('forge_user')
      localStorage.removeItem('forge_role')
    }
  }
})
