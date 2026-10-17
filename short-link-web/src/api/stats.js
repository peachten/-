import request from '../utils/request'

/** 汇总统计：PV / UV / UIP + 24 小时分布 + 按天趋势 */
export const getGroupStats = (params) =>
  request.get('/short-link/admin/v1/stats', { params })

/** 维度占比：dimension = browser / os / device / locale */
export const getDimensionStats = (params) =>
  request.get('/short-link/admin/v1/stats/dimension', { params })

/** 访问明细分页 */
export const pageAccessRecords = (params) =>
  request.get('/short-link/admin/v1/stats/access-record/page', { params })
