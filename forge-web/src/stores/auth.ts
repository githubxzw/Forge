import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('forge_token') || '',
    username: localStorage.getItem('forge_user') || ''
  }),
  getters: {
    loggedIn: s => !!s.token
  },
  actions: {
    setSession(token: string, username: string) {
      this.token = token
      this.username = username
      localStorage.setItem('forge_token', token)
      localStorage.setItem('forge_user', username)
    },
    logout() {
      this.token = ''
      this.username = ''
      localStorage.removeItem('forge_token')
      localStorage.removeItem('forge_user')
    }
  }
})
