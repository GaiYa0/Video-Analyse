import request from '@/utils/request'
import axios from 'axios';

export function uploadImage(formData) {
  return request({
    url: '/upload',
    method: 'post',
    headers: {
        'Content-Type': 'multipart/form-data', // 确保 Content-Type 为 multipart/form-data
      },
    data: formData,  // 请求体是 formData
  });
}

export function getAlgorithmList() {
  return request({
    url: `/algorithm/config/list`,
    method: 'get',

  });
}

export function getAlgorithmTargets(code) {
  return request({
    url: `/algorithm/config/targets/${code}`,
    method: 'get'
  })
}