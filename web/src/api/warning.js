import request from '@/utils/request'

export function getWarningList(query) {
  return request({
    url: `/waring/waring/list`,
    method: 'get',
    params: query
  });
}

export function getWarningDetail(id) {
  return request({
    url: `/waring/waring/${id}`,
    method: 'get'
  });
}

export function handleWarning(data) {
  return request({
    url: '/waring/waring/handle',
    method: 'post',
    data
  });
}

export function exportWarningTemplate(data) {
  return request({
    url: '/waring/waring/importTemplate',
    method: 'post',
    data,
  });
}

export function getTypeWaring() {
  return request({
    url: '/waring/type/getTypeWaring',
    method: 'get',
  });
}

export function getAlarmTypeFilterOptions() {
  return request({
    url: '/waring/type/getAlarmTypeFilterOptions',
    method: 'get',
  });
}

export function getTeamWaring() {
  return request({
    url: '/waring/waring/getTeamWaring',
    method: 'get',
  });
}

export async function getVideoUrl(ip, device_id, start_time, end_time) {
  const url = `http://${ip}:11125/sso/oauth2.0/accessToken?grant_type=client_credentials&client_id=SCYY&format=json&client_secret=Unis123456`
  const response = await fetch(url, {
    method: 'GET'
  });
  const data = await response.json();

  const url1 = `http://${ip}:11125/api/vms/v2/webuas/replay/stream/url?channel_code=${device_id}&stream_mode=1&start_time=${start_time}&end_time=${end_time}&source_type=1&rate=1`

  const response1 = await fetch(url1, {
    method: 'GET',
    headers: {
      'Authorization': data.access_token,
      'User': 'usercode:SYCC',
      'Cookie': 'usercode=SYCC',
      'Content-Type': 'application/json'
    }
  });
  const data1 = await response1.json();
  return data1;
}

// --------------下面是报警类型接口
export function getWarningTypeList(query) {
  return request({
    url: '/waring/type/list',
    method: 'get',
    params: query
  });
}

export function insertType(data) {
  return request({
    url: '/waring/type/insertType',
    method: 'post',
    data
  });
}

export function deleteTypes(ids) {
  return request({
    url: '/waring/type/' + ids,
    method: 'delete',
  });
}

export function getRecondition(query) {
  return request({
    url: '/waring/waring/getRecondition',
    method: 'get',
    params: query
  });
}

export function getWubao(query) {
  return request({
    url: '/waring/waring/getWubao',
    method: 'get',
    params: query
  });
}

