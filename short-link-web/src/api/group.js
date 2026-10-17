import request from '../utils/request'

/** 分组列表（含分组内短链数量） */
export const listGroups = () => request.get('/short-link/admin/v1/group/list')

/** 新建分组，返回 gid */
export const saveGroup = (name) => request.post('/short-link/admin/v1/group/save', { name })

/** 重命名分组 */
export const updateGroup = (data) => request.post('/short-link/admin/v1/group/update', data)

/** 删除分组 */
export const deleteGroup = (gid) =>
  request.post('/short-link/admin/v1/group/delete', null, { params: { gid } })

/** 分组数量 */
export const countGroup = () => request.get('/short-link/admin/v1/group/count')
