import request from '@/utils/request'

export function listDeployments(params) {
  return request({
    url: '/deployments',
    method: 'get',
    params
  })
}

export function createDeployment(data) {
  return request({
    url: '/deployments',
    method: 'post',
    data
  })
}

export function updateDeployment(id, data) {
  return request({
    url: `/deployments/${id}`,
    method: 'put',
    data
  })
}

export function startDeployment(id) {
  return request({
    url: `/deployments/${id}/start`,
    method: 'post',
    timeout: 23000
  })
}

export function stopDeployment(id) {
  return request({
    url: `/deployments/${id}/stop`,
    method: 'post',
    timeout: 23000
  })
}

export function updateDeploymentLiveOutput(id, data) {
  return request({
    url: `/deployments/${id}/live-output`,
    method: 'post',
    data,
    timeout: 23000
  })
}

export function getDeploymentDetail(id) {
  return request({
    url: `/deployments/${id}`,
    method: 'get'
  })
}
