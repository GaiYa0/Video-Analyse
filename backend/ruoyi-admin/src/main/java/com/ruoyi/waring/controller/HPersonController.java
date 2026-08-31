package com.ruoyi.waring.controller;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.PageDomain;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.page.TableSupport;
import com.ruoyi.waring.domain.HDist;
import com.ruoyi.waring.domain.HPerson;
import com.ruoyi.waring.service.HDistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/waring/person")
public class HPersonController extends BaseController {

    @Autowired
    private HDistService hDistService;

    @Value("${h3.username}")
    private String username;

    @Value("${h3.password}")
    private String password;

    @Value("${h3.w-ip}")
    private String ip;

    @Value("${h3.w-port}")
    private String port;

    @Autowired
    private RestTemplate restTemplate;
    @Resource
    private RedisTemplate<Object, Object> redisTemplate;

    /**
     * 人数统计
     */
    @GetMapping("/list")
    public TableDataInfo bodyCount(HPerson person) throws JsonProcessingException {
        long start = 0;
        long end = 0;
        if (person.getIs_handle() == null) {
            // 从早班到晚班的数据
            start = person.getBegin();
            end = person.getEnd();
        } else if (person.getIs_handle().equals("0")) {
            // 早班 8点到14点 1小时为 3600000 毫秒
            start = person.getBegin() + 3600000 * 6;
            end = person.getBegin() + 3600000 * 14;
        } else if (person.getIs_handle().equals("1")) {
            // 中班 14点到
            start = person.getBegin() + 3600000 * 14;
            end = person.getBegin() + 3600000 * 22;
        } else if (person.getIs_handle().equals("2")) {
            // 晚班
            start = person.getBegin() + 3600000 * 22;
            end = person.getBegin() + 3600000 * 30;
        }
        // 获取token
        Object token = redisTemplate.boundValueOps("token").get();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token.toString());
        headers.set("User", "usercode:" + username);
        headers.set("Cookie", "usercode=" + username);
        headers.set("Content-Type", "application/json");
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("area_type", "3");
        map.put("page_size", 100);
        map.put("statistic_start_time", start);
        map.put("statistic_end_time", end);
        map.put("show_type", 1);
        map.put("way_time", 4);
        String url = "http://" + ip + ":" + port + "/api/mg/v2/intelligent-analysis/historic/count";
        HttpEntity<HashMap<String, Object>> entity = new HttpEntity<>(map, headers);
        String r1 = restTemplate.postForObject(url, entity, String.class);
        JSONObject p = JSONObject.parseObject(r1);
        Object Adata = p.get("data");
        String adata = Adata.toString();
        JSONObject adat = JSONObject.parseObject(adata);
        Object Bdata = adat.get("data");
        String json = Bdata.toString();
        PageDomain pageDomain = TableSupport.getPageDomain();
        PageHelper.startPage(pageDomain.getPageNum(), pageDomain.getPageSize(), pageDomain.getOrderBy());
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
        });
        for (int i = 0; i < list.size(); i++) {
            int a = Integer.parseInt(list.get(i).get("statistic_in_person_count").toString()) - Integer.parseInt(list.get(i).get("statistic_out_person_count").toString());
            if (a < 0) {
                list.get(i).put("statistic_person_count", 0);
            } else {
                list.get(i).put("statistic_person_count", a);
            }
        }
        return getDataTable(list);
    }

    /**
     * 入井人员识别
     */
    @GetMapping("/face")
    public TableDataInfo getFace(HPerson person) {
        List<HDist> list = hDistService.selectDistList(person);
        return getDataTable(list);
    }
}