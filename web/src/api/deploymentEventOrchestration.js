import request from '@/utils/request'

export function listDeploymentEventOrchestrations(deploymentId) {
  return request({
    url: `/deployments/${deploymentId}/event-orchestrations`,
    method: 'get'
  })
}

export function createDeploymentEventOrchestration(deploymentId, data) {
  return request({
    url: `/deployments/${deploymentId}/event-orchestrations`,
    method: 'post',
    data
  })
}

export function updateDeploymentEventOrchestration(deploymentId, orchestrationId, data) {
  return request({
    url: `/deployments/${deploymentId}/event-orchestrations/${orchestrationId}`,
    method: 'put',
    data
  })
}

export function deleteDeploymentEventOrchestration(deploymentId, orchestrationId) {
  return request({
    url: `/deployments/${deploymentId}/event-orchestrations/${orchestrationId}`,
    method: 'delete'
  })
}

export function getDeploymentEventPool(deploymentId) {
  return request({
    url: `/deployments/${deploymentId}/event-pool`,
    method: 'get'
  })
}
