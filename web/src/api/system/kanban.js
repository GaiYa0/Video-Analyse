import request from '@/utils/request'; 

// 获取本月报警数量
export function getMonthWaring(orgIndex = '') {
  const url = orgIndex ? `/index/index/getMonthWaring?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getMonthWaring';
  return request({
    url,
    method: 'get'
  });
}

// 获取本月重大报警数量
export function getMonthMajorWaring(orgIndex = '') {
  const url = orgIndex ? `/index/index/getMonthMajorWaring?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getMonthMajorWaring';
  return request({
    url,
    method: 'get'
  });
}

// 本月报警逾期数量
export function getMonthOverdueWaring(orgIndex = '') {
  const url = orgIndex ? `/index/index/getMonthOverdueWaring?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getMonthOverdueWaring';
  return request({
    url,
    method: 'get'
  });
}

// 本月报警整改数量及整改率
export function getMonthHandle(orgIndex = '') {
  const url = orgIndex ? `/index/index/getMonthHandle?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getMonthHandle';
  return request({
    url,
    method: 'get'
  });
}

// 报警综合排行统计
export function getRanking(orgIndex = '') {
  const url = orgIndex ? `/index/index/getRanking?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getRanking';
  return request({
    url,
    method: 'get'
  });
}

// 报警趋势分析
export function getTrend(orgIndex = '') {
  const url = orgIndex ? `/index/index/getTrend?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getTrend';
  return request({
    url,
    method: 'get'
  });
}

// 报警增长率分析
export function getGrowth(orgIndex = '') {
  const url = orgIndex ? `/index/index/getGrowth?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getGrowth';
  return request({
    url,
    method: 'get'
  });
}

// 报警专业整体分布
export function getColumn(orgIndex = '', type) {
  let url = `/index/index/getColumn?type=${encodeURIComponent(type)}`;
  url = orgIndex ? url + `&org_index=${encodeURIComponent(orgIndex)}` : url;
  return request({
    url,
    method: 'get'
  });
}

// 报警等级分布
export function getLevelSpread(orgIndex = '', type) {
  let url = `/index/index/getLevelSpread?type=${encodeURIComponent(type)}`;
  url = orgIndex ? url + `&org_index=${encodeURIComponent(orgIndex)}` : url;

  return request({
    url,
    method: 'get'
  });
}

// 报警类型分布
export function getTypeSpread(orgIndex = '', type) {
  let url = `/index/index/getTypeSpread?type=${encodeURIComponent(type)}`;
  url = orgIndex ? url + `&org_index=${encodeURIComponent(orgIndex)}` : url;
  return request({
    url,
    method: 'get'
  });
}

// 报警挂牌公示
export function getHandleData(orgIndex = '') {
  const url = orgIndex ? `/index/index/getHandleData?org_index=${encodeURIComponent(orgIndex)}` : '/index/index/getHandleData';
  return request({
    url,
    method: 'get'
  });
}

// 首页组织下拉
export function getDeptList() {
  return request({
    url: '/index/index/getDeptList',
    method: 'get'
  });
}

// 设备监控点统计
export function getDeviceNum() {
  return request({
    url: '/index/index/getDeviceNum',
    method: 'get'
  });
}

// 实时报警图片
export function getAlarmPhoto() {
  return request({
    url: '/index/index/getAlarmPhoto',
    method: 'get'
  });
}

// 实时报警信息
export function getRealAlarm() {
  return request({
    url: '/index/index/getRealAlarm',
    method: 'get'
  });
}