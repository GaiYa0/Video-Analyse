import request from '@/utils/request'

export function listDeploymentBusinessEventTemplates(params) {
  return request({
    url: '/deployment-business-events',
    method: 'get',
    params
  })
}

export function getDeploymentBusinessEventTemplate(id) {
  return request({
    url: `/deployment-business-events/${id}`,
    method: 'get'
  })
}

export function createDeploymentBusinessEventTemplate(data) {
  return request({
    url: '/deployment-business-events',
    method: 'post',
    data
  })
}

export function updateDeploymentBusinessEventTemplate(id, data) {
  return request({
    url: `/deployment-business-events/${id}`,
    method: 'put',
    data
  })
}

export function deleteDeploymentBusinessEventTemplate(id) {
  return request({
    url: `/deployment-business-events/${id}`,
    method: 'delete'
  })
}
