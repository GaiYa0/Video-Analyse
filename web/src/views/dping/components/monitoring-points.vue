<template>
  <div class="user_Overview">
    <div @click="toPage('/device/device')" class="user-title">
<!--      <div class="user_Overview_nums allnum ">-->
<!--        <dv-digital-flop :config="config" style="width:100%; height:100%;" />-->
<!--      </div>-->
      <BarChart id="firstBar" ref="firstBar" :option="firstOption"></BarChart>
    </div>

    <div @click="toPage('/device/device', { isOnline: 1 })" class="user-title">
      <BarChart id="secondBar" ref="secondBar" :option="secondOption"></BarChart>
    </div>

    <div @click="toPage('/device/device', { isOnline: 2 })" class="user-title">
      <BarChart id="thirdBar" ref="thirdBar" :option="thirdOption"></BarChart>
    </div>

    <!-- <li class="user_Overview-item" style="color: #07f7a8; margin-right: 25px; margin-top: -10px">


    </li>
    <li class="user_Overview-item" style="color: #e3b337; margin-right: 25px; margin-top: -10px">

    </li> -->
  </div>
</template>

<script>
import { getDeviceNum } from '@/api/system/kanban';
import { markRaw } from 'vue'
import * as echarts from 'echarts'
import BarChart from '@/views/dping/components/templateChart.vue'
let style = {
  fontSize: 24
}

const demoData = {
  name: "城镇化率",
  value: 121,
}
const option =  {
  series: [
    {
      type: "gauge",
      radius: "90%", // 1行3个
      center: ["50%", "55%"],
      splitNumber: 10,
      // min: 0,
      startAngle: 180,
      endAngle: 0,
      // 线
      axisLine: {
        lineStyle: {
          width: 1,
          color: [[1, "rgba(255,255,255,0)"]],
        }
      },
      //刻度标签。
      axisTick: {
        show: true,
        splitNumber: 6, //刻度的段落数
        itemStyle: {
          color: {
            type: 'linear',
            x: 1,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              {
                offset: 0,
                color: '#f12711' // 0% 处的颜色
              },
              {
                offset: 1,
                color: '#f5af19' // 100% 处的颜色
              }
            ],
            global: false // 缺省为 false
          }
        },
        length: 2, //刻度的长度
      },
      splitLine: {
        show: false,
      },
      // //刻度线文字
      axisLabel: {
        show: false,
      },

      data: [],
      pointer: {
        show: true,
        length: "60%",
        radius: "50%",
        itemStyle: {
          color: '#ffffff'
        },
        width: 3, //指针粗细
        offsetCenter: [0, 0],
      },
      title: {
        // 仪表盘标题。
        show: false,
      },
    },
    {
      type: "pie",
      radius: ["95%", "105%"],
      center: ["50%", "55%"],
      startAngle: 180,
      endAngle: 0,
      color: [
        {
          type: "linear",
          x: 1,
          y: 0,
          x2: 0,
          y2: 0,
          colorStops: [
            {
              offset: 0,
              color: "transparent", // 0% 处的颜色
            },
            {
              offset: 0.5,
              color: "rgb(13,178,220)", // 0% 处的颜色
            },
            {
              offset: 1,
              color: "rgb(4,107,187)", // 100% 处的颜色
            },
          ],
        },
        "transparent",
      ],
      hoverAnimation: true,
      legendHoverLink: false,
      labelLine: {
        normal: {
          show: false,
        },
      },
      data: [],
    }
  ],
}
export default {
  components: { BarChart },
  data() {
    return {
      userOverview: {
        alarmNum: 0,
        offlineNum: 0,
        onlineNum: 0,
        totalNum: 0,
      },
      pageflag: true,
      timer: null,
      firstOption: {
        backgroundColor: "transparent",
        series: [
          {
            ...option.series[0],
            detail: {
              show: true,
              offsetCenter: [0, "30%"],
              color: "#00fdfa",
              formatter: function (params) {
                return '监测点：' + params;
              },
              textStyle: {
                fontSize: 14,
              },
            },
          },
          {...option.series[1]}
        ],
      },
      secondOption: {
        backgroundColor: "transparent",
        series: [
          {
            ...option.series[0],
            detail: {
              show: true,
              offsetCenter: [0, "30%"],
              color: "#00fdfa",
              formatter: function (params) {
                return '在线：' + params;
              },
              textStyle: {
                fontSize: 14,
              },
            },
          },
          {...option.series[1]}
        ],
      },
      thirdOption: {
        backgroundColor: "transparent",
        series: [
          {
            ...option.series[0],
            detail: {
              show: true,
              offsetCenter: [0, "30%"],
              color: "#00fdfa",
              formatter: function (params) {
                return '离线：' + params;
              },
              textStyle: {
                fontSize: 14,
              },
            },
          },
          {...option.series[1]}
        ],
      },
      config: {
        number: [100],
        content: '{nt}',
        style: {
          ...style,
          // stroke: "#00fdfa",
          fill: "#00fdfa",
        },
      },
      onlineconfig: {
        number: [0],
        content: '{nt}',
        style: {
          ...style,
          // stroke: "#07f7a8",
          fill: "#07f7a8",
        },
      },
      offlineconfig: {
        number: [0],
        content: '{nt}',
        style: {
          ...style,
          // stroke: "#e3b337",
          fill: "#e3b337",
        },
      },
      laramnumconfig: {
        number: [0],
        content: '{nt}',
        style: {
          ...style,
          // stroke: "#f5023d",
          fill: "#f5023d",
        },
      },
      deviceNum: 0,
      deviceEnableNum: 0,
      deviceli: 0
    };
  },
  filters: {
    numsFilter(msg) {
      return msg || 0;
    },
  },
  mounted() {
    this.fetchData();
    this.switper()
  },
  beforeDestroy() {
    this.clearData()
  },
  methods: {
    initChart() {
      const dom = this.$refs.firstBar.$refs.templateChart
      const dom2 = this.$refs.secondBar.$refs.templateChart
      const dom3 = this.$refs.thirdBar.$refs.templateChart
      const chart = echarts.init(dom)
      const chart2 = echarts.init(dom2)
      const chart3 = echarts.init(dom3)
      dom.setAttribute('_echarts_instance_', '')
      dom2.setAttribute('_echarts_instance_', '')
      dom3.setAttribute('_echarts_instance_', '')
      this.$refs.firstBar.initChart()
      this.$refs.secondBar.initChart()
      this.$refs.thirdBar.initChart()
      window.addEventListener('resize', () => {
        chart.resize()
        chart2.resize()
        chart3.resize()
      })
    },

    async fetchData() {
      const res = await getDeviceNum();

      this.firstOption.series[0].data = [{ value: res.data.deviceNum }]
      this.firstOption.series[0].max = res.data.deviceNum
      this.firstOption.series[1].data = [
        { value: (parseInt(res.data.deviceNum/2) * (res.data.deviceNum)) / res.data.deviceNum },
        { value: res.data.deviceNum - (parseInt(res.data.deviceNum/2) * (res.data.deviceNum)) / res.data.deviceNum },
      ]

      this.secondOption.series[0].data = [{ value: res.data.deviceEnableNum }]
      this.secondOption.series[0].max = res.data.deviceNum
      this.secondOption.series[1].data = [
        { value: (parseInt(res.data.deviceNum/2) * (res.data.deviceEnableNum)) / res.data.deviceNum },
        { value: res.data.deviceNum - (parseInt(res.data.deviceNum/2) * (res.data.deviceEnableNum)) / res.data.deviceNum },
      ]

      this.thirdOption.series[0].data = [{ value: res.data.deviceli }]
      this.thirdOption.series[0].max = res.data.deviceNum
      this.thirdOption.series[1].data = [
        { value: (parseInt(res.data.deviceNum/2) * (res.data.deviceli)) / res.data.deviceNum },
        { value: res.data.deviceNum - (parseInt(res.data.deviceNum/2) * (res.data.deviceli)) / res.data.deviceNum },
      ]

      console.log(this.firstOption.series,'0serous')


      this.initChart()

      // this.config = {
      //   ...this.config,
      //   number: [res.data.deviceNum]
      // };
      // this.onlineconfig = {
      //   ...this.onlineconfig,
      //   number: [res.data.deviceEnableNum]
      // };
      // this.offlineconfig = {
      //   ...this.offlineconfig,
      //   number: [res.data.deviceli]
      // };

    },

    toPage(path, params) {
      this.$router.push({
        path: path,
        query: params
      })
    },

    clearData() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    getData() {
      this.pageflag = true;
      currentGET("big2").then((res) => {
        if (!this.timer) {
          console.log("设备总览", res);
        }
        if (res.success) {
          this.userOverview = res.data;
          this.onlineconfig = {
            ...this.onlineconfig,
            number: [res.data.onlineNum]
          }
          this.config = {
            ...this.config,
            number: [res.data.totalNum]
          }
          this.offlineconfig = {
            ...this.offlineconfig,
            number: [res.data.offlineNum]
          }
          this.laramnumconfig = {
            ...this.laramnumconfig,
            number: [res.data.alarmNum]
          }
          this.switper()
        } else {
          this.pageflag = false;
          this.$Message.warning(res.msg);
        }
      });
    },
    //轮询
    switper() {
      if (this.timer) {
        return
      }
      // let looper = (a) => {
      //   this.getData()
      // };
      this.timer = setInterval(this.fetchData, 6000);
    },
  },
};
</script>
<style lang='scss' scoped>
.user_Overview {
  display: flex;
  flex-direction: column;
  height: 100%;
  list-style: none;

  .user-title {
    color: #00fdfa;
    cursor: pointer;
    flex: 1;
    text-align: center
  }

  li {
    flex: 1;

    p {
      text-align: center;
      height: 20px;
      font-size: 17px;
      margin-top: -2px;
    }

    .user_Overview_nums {
      width: 100px;
      height: 100px;
      text-align: center;
      line-height: 100px;
      font-size: 22px;
      margin: 12px auto 8px;
      background-size: cover;
      background-position: center center;
      position: relative;

      &::before {
        content: '';
        position: absolute;
        width: 100%;
        height: 100%;
        top: 0;
        left: 0;
      }

      &.bgdonghua::before {
        animation: rotating 14s linear infinite;
      }
    }

    .allnum {
      &::before {
        background-image: url("~@/assets/img/left_top_lan.png");
      }
    }

    .online {
      &::before {
        background-image: url("~@/assets/img/left_top_lv.png");

      }
    }

    .offline {
      &::before {
        background-image: url("~@/assets/img/left_top_huang.png");

      }
    }

    .laramnum {
      &::before {
        background-image: url("~@/assets/img/left_top_hong.png");

      }
    }
  }
}
</style>
