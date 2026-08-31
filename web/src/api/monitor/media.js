import request from '@/utils/request'

// 查询媒体服务器流信息
export function getMediaStreams() {
  return request({
    url: '/monitor/media/streams',
    method: 'get'
  })
}