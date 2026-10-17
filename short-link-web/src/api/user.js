import request from '../utils/request'

/** 登录，返回 token */
export const login = (data) => request.post('/short-link/admin/v1/user/login', data)

/** 注册 */
export const register = (data) => request.post('/short-link/admin/v1/user/register', data)

/** 退出登录 */
export const logout = () => request.post('/short-link/admin/v1/user/logout')

/** 当前登录用户信息 */
export const getCurrentUser = () => request.get('/short-link/admin/v1/user/current')

/** 用户名是否可用 */
export const hasUsername = (username) =>
  request.get('/short-link/admin/v1/user/has-username', { params: { username } })
