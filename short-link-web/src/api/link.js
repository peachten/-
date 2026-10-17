import request from '../utils/request'

/** 创建短链（同分组同长链幂等） */
export const createLink = (data) => request.post('/short-link/admin/v1/link', data)

/** 分页查询短链 */
export const pageLinks = (params) => request.get('/short-link/admin/v1/link/page', { params })

/** 修改短链 */
export const updateLink = (data) => request.put('/short-link/admin/v1/link', data)

/** 批量修改短链 */
export const updateLinks = (list) => request.post('/short-link/admin/v1/link/update', list)

/** 启用 / 禁用短链（0 启用 1 禁用） */
export const enableLink = (fullShortUrl, enableStatus) =>
  request.post('/short-link/admin/v1/link/enable', null, { params: { fullShortUrl, enableStatus } })

/** 删除短链（逻辑删除，支持批量） */
export const deleteLinks = (fullShortUrls) =>
  request.delete('/short-link/admin/v1/link', { data: fullShortUrls })

/** 分组内短链数量 */
export const countLink = (gid) =>
  request.get('/short-link/admin/v1/link/count', { params: { gid } })
