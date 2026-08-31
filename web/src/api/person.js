import request from '@/utils/request'

export function getPersonList(query) {
  return request({
    url: `/waring/person/list`,
    method: 'get',
    params: query
  });
}


export function checkWork(query) {
  return request({
    url: `/waring/person/face`,
    method: 'get',
    params: query
  });
}
