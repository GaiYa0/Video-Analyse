package com.ruoyi.web.controller.token;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.waring.domain.HDist;
import com.ruoyi.waring.service.HDistService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/token")
public class TokenController
{
    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private RedisTemplate<Object, Object> redisTemplate;

    @Resource
    private HDistService hDistService;

    @Value("${h3.username}")
    private String username;

    @Value("${h3.w-ip}")
    private String ip;

    @Value("${h3.w-port}")
    private String port;

    @Scheduled(fixedRate = 1000 * 60 * 10)
    public void addDist()
    {
        Date date = new Date();
        long end = date.getTime();
        long start = end - 360000L * 5960;

        Object tokenObj = redisTemplate.boundValueOps("token").get();
        if (tokenObj == null || StringUtils.isEmpty(tokenObj.toString()))
        {
            log.warn("skip addDist: redis token is empty");
            return;
        }
        String token = tokenObj.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        headers.set("User", "usercode:" + username);
        headers.set("Cookie", "usercode=" + username);
        headers.set("Content-Type", "application/json");

        HashMap<String, Object> map = new HashMap<>();
        map.put("ats_start_time", start);
        map.put("ats_end_time", end);
        map.put("page_num", 1);
        map.put("page_size", 1000);

        String url = "http://" + ip + ":" + port + "/api/biz-scene/v1/attendance/original/page";
        HttpEntity<HashMap<String, Object>> entity = new HttpEntity<>(map, headers);
        String response = restTemplate.postForObject(url, entity, String.class);
        if (StringUtils.isEmpty(response))
        {
            log.warn("skip addDist: attendance api response is empty");
            return;
        }

        JSONObject root = JSONObject.parseObject(response);
        if (root == null || root.get("data") == null)
        {
            log.warn("skip addDist: attendance api data is empty");
            return;
        }

        JSONObject dataObj = JSONObject.parseObject(String.valueOf(root.get("data")));
        if (dataObj == null)
        {
            log.warn("skip addDist: attendance data object is invalid");
            return;
        }

        JSONArray dataArray = dataObj.getJSONArray("data");
        if (dataArray == null || dataArray.isEmpty())
        {
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < dataArray.size(); i++)
        {
            JSONObject item = JSON.parseObject(dataArray.get(i).toString());
            if (item == null || item.get("id") == null)
            {
                continue;
            }
            String id = item.get("id").toString();
            int exists = hDistService.getId(id);
            if (exists != 0)
            {
                continue;
            }

            HDist hDist = new HDist();
            hDist.setId(id);
            hDist.setPerson_name(item.getString("person_name"));
            if (item.get("pass_time") != null)
            {
                hDist.setPass_time(format.format(new Date(Long.parseLong(item.get("pass_time").toString()))));
            }
            hDist.setPerson_no(item.getString("person_no"));
            hDist.setIndex_path_name(item.getString("index_path_name"));
            hDist.setPass_time_exc(item.getString("pass_time_exc"));
            hDist.setPass_time_ms(item.getString("pass_time_ms"));
            hDist.setSite_name(item.getString("site_name"));
            hDist.setAttendance_identification(item.getString("attendance_identification"));
            hDist.setAttendance_identification_absolute(item.getString("attendance_identification_absolute"));
            hDistService.insertDist(hDist);
        }
    }
}
