import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi, logout as logoutApi } from '../api/user'

/**
 * 用户状态：token 持久化到 localStorage，刷新页面后仍保持登录
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    username: localStorage.getItem('username') || ''
  }),

  getters: {
    isLoggedIn: (state) => !!state.token
  },

  actions: {
    async login(form) {
      const token = await loginApi(form)
      this.token = token
      localStorage.setItem('token', token)

      // 登录后顺手取一次用户信息，失败不影响登录本身
      try {
        const user = await getCurrentUser()
        this.username = user.username
        localStorage.setItem('username', user.username)
      } catch {
        this.username = form.username
        localStorage.setItem('username', form.username)
      }
      return token
    },

    async logout() {
      try {
        await logoutApi()
      } finally {
        this.clear()
      }
    },

    clear() {
      this.token = ''
      this.username = ''
      localStorage.removeItem('token')
      localStorage.removeItem('username')
    }
  }
})
