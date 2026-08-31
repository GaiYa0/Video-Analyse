<template>
  <div class="avb">
    <iframe :src="embeddedPageUrl" class="scaled-iframe" width="100%" height="550px"></iframe>
    <!-- <el-row :gutter="20">
      <el-col :span="8" v-for="(item, index) in deviceImages.slice(0, 3)" :key="index">
        <div class="image-container">
          <el-image class="img" :src="item.picture_absolute_url" fit="contain"
            :preview-src-list="[item.picture_absolute_url]"></el-image>
          <iframe :src="embeddedPageUrl" class="scaled-iframe" width="100%" height="100%"></iframe>
        </div>
        <span class="caption">{{ item.device_name }}</span>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="8" v-for="(item, index) in deviceImages.slice(3, 6)" :key="index + 3">
        <div class="image-container">
           <el-image class="img" :src="item.picture_absolute_url" fit="contain"
            :preview-src-list="[item.picture_absolute_url]"></el-image>
          <iframe src="http://www.baidu.com" width="100%" height="100%"></iframe>
        </div>
        <span class="caption">{{ item.device_name }}</span>
      </el-col>
    </el-row> -->
  </div>
</template>

<script>
import { getAlarmPhoto } from '@/api/system/kanban';

export default {
  data() {
    return {
      deviceImages: [
      ],
      detectRequest: {
        id: "manual",
        detector_name: "pytorch",
        preprocess: [],
        detect: {
          "*": 50
        },
        regions: [
          { "top": 0.1, "left": 0.1, "bottom": 0.9, "right": 0.9, "detect": { "*": 50 }, "covers": false }
        ],
        data: "rtsp://rtspstream:408c3c56c37eda5492b06bc8137551dc@zephyr.rtsp.stream/movie"
      }
    };
  },

  mounted() {
    this.fetchData();
  },

  computed: {
    embeddedPageUrl: function() {
      return "http://10.129.192.13:8080/stream?detect_request=" + encodeURIComponent(JSON.stringify(this.detectRequest));
    }
  },

  methods: {
    async fetchData() {
      try {
        const res = await getAlarmPhoto();
        if (res.code != 200) throw new Error(res.msg);
        this.deviceImages = res.data;
      } catch (error) {
        console.error(error);
      }
    },
  },
};
</script>


<style lang='scss' scoped>
.avb {
  width: 95%;
  margin-left: 25px;
  // margin-top: 15px;
}

.img {
  margin-top: 10px;
  width: 100%;
  height: 220px;
}

.image-container {
  border: 3px solid rgb(52, 209, 234, 0.65);
  width: 100%;
  height: 245px;
  display: inline-block;
  margin-top: 10px;
}


.caption {
  text-align: center;
  color: rgb(0, 194, 206, 0.9);
  font-size: 16px;
  line-height: 1.5;
}

.scaled-iframe {
  transform: scale(1);
}
</style>