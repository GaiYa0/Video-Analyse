<template>
  <div class="avb">
    <el-row :gutter="20">
      <el-col :span="8" v-for="(item, index) in deviceImages.slice(0, 3)" :key="index" style="position: relative">
        <div class="image-container">
          <el-image class="img" :src="item.picture_absolute_url" fit="contain"
                    :preview-src-list="[item.picture_absolute_url]"></el-image>
          <div class="alarm-type">{{ item.alarm_type_name }}</div>
        </div>
        <div class="caption">{{ item.device_name }}</div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="8" v-for="(item, index) in deviceImages.slice(3, 6)" :key="index + 3" style="position: relative">
        <div class="image-container">
          <el-image class="img" :src="item.picture_absolute_url" fit="contain"
                    :preview-src-list="[item.picture_absolute_url]"></el-image>
          <div class="alarm-type">{{ item.alarm_type_name }}</div>
        </div>
        <div class="caption">{{ item.device_name }}</div>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {getAlarmPhoto} from '@/api/system/kanban';

export default {
  data() {
    return {
      deviceImages: [],
      timer: null
    };
  },

  mounted() {
    this.fetchData()
    this.switper()
  },

  beforeDestroy() {
    this.clearData()
  },

  methods: {
    async fetchData() {
      try {
        const res = await getAlarmPhoto();
        console.log(res, 'res')
        if (res.code != 200) throw new Error(res.msg);
        this.deviceImages = res.data;
      } catch (error) {
        console.error(error);
      }
    },

    switper() {
      console.log(this.timer, 'timer')
      if (this.timer) {
        return
      }
      // let looper = (a) => {
      //   this.getData()
      // };
      this.timer = setInterval(this.fetchData, 20000);
    },

    clearData() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    }
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
  width: 100%;
  height: 201px;
}

.image-container {
  border: 3px solid rgb(52, 209, 234, 0.65);
  border-radius: 5px;
  width: 100%;
  height: 206px;
  display: inline-block;
  position: relative;
  margin-top: 10px;
}

.alarm-type {
  position: absolute;
  width: 55%;
  top: 0;
  right: 0;
  color: #00fdfa;
  height: 30px;
  line-height: 30px;
  font-size: 14px;
  background: rgba(9, 107, 167, 0.8);
  z-index: 1;
  text-align: center;
}

.caption {
  width: 94%;
  position: absolute;
  height: 36px;
  line-height: 36px;
  bottom: 4px;
  background: red;
  text-align: left;
  text-indent: 1em;
  background: linear-gradient(90deg, rgba(9, 107, 167, 1), rgba(9, 107, 167, 0.5), rgba(9, 107, 167, 0.1));
  color: #FFFFFF;
  font-size: 16px;
}
</style>
