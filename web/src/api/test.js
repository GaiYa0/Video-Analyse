import request from '@/utils/request'

export function getTestData(query) {
  return request({
    url: `/waring/handle/getTestData`,
    method: 'get',
    params: query
  });
}
