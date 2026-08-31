<template>
  <div class="avb">
    <el-image class="img" :src="img1" fit="contain"></el-image>
    <input v-model="inputId" type="number" max="6" min="1" placeholder="输入id" :span="4"/>
    <button @click="handleClick">调用</button>
    <el-text >{{ algorithm }}</el-text>
    <!-- <div class="image-container">
          <el-image class="img" :src=deviceImages.url fit="contain"
            :preview-src-list="[deviceImages.url]":tem="imageKey"></el-image>
        </div>
        <span class="caption">{{ deviceImages.url }}</span>

    <input v-model="inputText" type="text" placeholder="输入url地址" />
    <button @click="handleClick">提交</button>
    <p v-if="submittedText">你提交的内容是：{{ submittedText }}</p> -->
  </div>
</template>

<script>
import { getAlarmPhoto,setUrl,getTest,setTest } from '@/api/system/kanban';
import store from '@/store'
import axios from 'axios'
export default {
  data() {
    return {
    img1:"/activity.png",
      deviceImages: [
      ],
      imageKey: 0,
      url:"",
      inputId:"",
      data:{
        id:2,
        name:"图书馆",
        url:"asdaa",
      }
    };
  },

  mounted() {
    // this.fetchData();
  },
//需要修改的地方 自定制
computed:{
  algorithm () {
    return this.$store.state.algorithm
  }
},
  methods: {
    async fetchData() {
      try {
        const res = await getTest();
        if (res.code != 200) throw new Error(res.msg);
        this.deviceImages = res.data;
      } catch (error) {
        console.error(error);
      }
    },
    async handleClick() {
      const id = this.inputId
      fetch('http://127.0.0.1:8080/video?id='+id,{
    method: 'GET',
    mode: 'cors',
}).then(response => response.json())
  .then(data => {for(let i=0;i<data.length;i++)
  {
    this.deviceImages[i].url = data[i]  }
  })
  .catch(error => console.error(error))
      // axios({
      //               url: 'http://127.0.0.1:8080/video',
      //               params: {
      //                   id: 1
      //               }
      //           }).then(response => {
      //               console.log(response.data)
      //           }, error => {
      //               console.log('错误', error.message)
      //           })
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
  margin-top: 10px;
  width: 100%;
  height: 230px;
}

.image-container {
  border: 3px solid rgb(52, 209, 234, 0.65);
  width: 100%;
  height: 245px;
  display: inline-block;
  margin-top: 10px;
  position: relative;
}

.alarm-type {
  position: absolute;
  bottom: 0;
  right: 0;
  color: red;
  font-size: 16px;
  background: rgba(255, 255, 255, 0.5);
  z-index: 1;
}

.caption {
  text-align: center;
  color: rgb(0, 194, 206, 0.9);
  font-size: 16px;
  line-height: 1.5;
}

input {
  margin-right: 10px;
}

.el-input {
width: 250px;
}
</style>