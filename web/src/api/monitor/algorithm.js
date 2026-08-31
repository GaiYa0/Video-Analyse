import request from '@/utils/request'

// 查询算法服务器控制信息
export function getAlgorithmControls() {
  return request({
    url: '/monitor/algorithm/controls',
    method: 'get'
  })
}