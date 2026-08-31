<template>
  <div>
    <tiny-layout>
      <tiny-row :flex="true" justify="center">
        <tiny-col :span="7">
          <div style="display: flex; align-items: center; justify-content: space-between;">
            <h3 style="color: black">
              报警类型统计
            </h3>
            <div>
              <span class="clickable" @click="selectTime('1', 1)" :style="timeStyle('1', 1)">周</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('2', 1)" :style="timeStyle('2', 1)">月</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('3', 1)" :style="timeStyle('3', 1)">季度</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('4', 1)" :style="timeStyle('4', 1)">年</span>
            </div>
          </div>
          <div class="col">
            <div class="left">
              <div class="left-content">
                <div class="echart" id="domainDis" :style="domainStyle"></div>
              </div>
            </div>
          </div>
        </tiny-col>
        <!--        <tiny-col :span="3">-->
        <!--          <div style="display: flex; align-items: center; justify-content: space-between;">-->
        <!--            <h3 style="color: black">处置情况</h3>-->
        <!--            <div>-->
        <!--              <span class="clickable" @click="selectTime('1', 2)" :style="timeStyle('1', 2)">周</span>-->
        <!--              <span style="color: grey;"> | </span>-->
        <!--              <span class="clickable" @click="selectTime('2', 2)" :style="timeStyle('2', 2)">月</span>-->
        <!--              <span style="color: grey;"> | </span>-->
        <!--              <span class="clickable" @click="selectTime('3', 2)" :style="timeStyle('3', 2)">季度</span>-->
        <!--              <span style="color: grey;"> | </span>-->
        <!--              <span class="clickable" @click="selectTime('4', 2)" :style="timeStyle('4', 2)">年</span>-->
        <!--            </div>-->
        <!--          </div>-->
        <!--          <div class="col">-->
        <!--            <div class="left">-->
        <!--              <div class="echart" id="levelDis" :style="levelStyle"></div>-->
        <!--            </div>-->
        <!--          </div>-->
        <!--        </tiny-col>-->
        <tiny-col :span="6">
          <div style="display: flex; align-items: center; justify-content: space-between;">
            <h3 style="color: black">报警类型分布</h3>
            <div>
              <span class="clickable" @click="selectTime('1', 3)" :style="timeStyle('1', 3)">周</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('2', 3)" :style="timeStyle('2', 3)">月</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('3', 3)" :style="timeStyle('3', 3)">季度</span>
              <span style="color: grey;"> | </span>
              <span class="clickable" @click="selectTime('4', 3)" :style="timeStyle('4', 3)">年</span>
            </div>
          </div>
          <div class="col">
            <div class="left">
              <div class="echart" id="typeDis" :style="typeStyle"></div>
            </div>
          </div>
        </tiny-col>
      </tiny-row>
    </tiny-layout>
  </div>
</template>

<script>
import {Col as TinyCol, Layout as TinyLayout, Row as TinyRow} from '@opentiny/vue';
import {getColumn, getTypeSpread} from '@/api/system/kanban';
import * as echarts from "echarts";

export default {
  components: {
    TinyLayout,
    TinyRow,
    TinyCol
  },

  props: {
    orgIndex: {
      type: String,
      default: ''
    }
  },

  data() {
    return {
      domainStyle: {
        float: "left", width: "500px", height: "270px"
      },
      typeStyle: {
        float: "left", width: "400px", height: "350px"
      },
      // 1. 报警专业整体分布
      domainData: {
        xData: [],
        yData: []
      },
      // 2. 报警类型分布
      typeData: {
        type: [],
        value: []
      },
      selectedTime1: '2',
      selectedTime3: '2',
      time: ['时间', '周', '月', '季度', '年']
    };
  },

  computed: {
    timeStyle() {
      return (time, number) => ({
        'color': this[`selectedTime${number}`] === time ? 'blue' : 'black',
        'font-weight': this[`selectedTime${number}`] === time ? 'bold' : 'normal'
      });
    }
  },

  methods: {
    initDomainEcharts() {
      const option = {
        tooltip: {
          trigger: "axis",
          axisPointer: {
            type: "shadow"
          },
        },
        xAxis: {
          data: this.domainData.xData,
          axisTick: {
            alignWithLabel: true
          },
        },
        yAxis: {
          type: "value",
          axisLabel: {
            color: "black"
          }
        },
        series: [
          {
            type: "bar",
            barWidth: "70%",
            data: this.domainData.yData,
          }
        ]
      };

      const domainDis = echarts.init(document.getElementById("domainDis"));
      domainDis.on('click', (params) => {
        this.$router.push({
          path: "/warning/warning",
          query: {withQue: 8, time: this.time[this.selectedTime1], alarm_type_name: params.name}
        });
        console.log(this.selectedTime1)
        console.log(this.time[this.selectedTime1]);
        console.log(params.name);
      });
      domainDis.setOption(option);
      // 随着屏幕大小调节图表
      window.addEventListener("resize", () => {
        domainDis.resize();
      });
    },

    async fetchDomain() {
      this.domainData.xData = [];
      this.domainData.yData = [];
      const domainRes = await getColumn(this.orgIndex, this.selectedTime1);
      domainRes.data.forEach(item => {
        this.domainData.xData.push({
          value: item.alarm_type_name,
          textStyle: {
            color: "black"
          },
          fontSize: 22
        });
        this.domainData.yData.push({
          value: item.num,
          itemStyle: {
            color: "rgba(65,160,227, 1)"
          }
        });
      });
      this.initDomainEcharts();
    },

    initTypeEcharts() {
      const option = {
        tooltip: {
          trigger: 'axis'
        },
        radar: {
          indicator: this.typeData.type,
          center: ['60%', '50%'],
          radius: 80
        },
        series: [
          {
            tooltip: {
              trigger: 'item'
            },
            type: 'radar',
            areaStyle: {},
            data: [
              {
                value: this.typeData.value,
                name: '报警类型分布'
              }
            ]
          }
        ]
      };

      const typeDis = echarts.init(document.getElementById("typeDis"));
      typeDis.on('click', (params) => {
        this.$router.push({path: "/warning/warning", query: {withQue: 8, time: this.time[this.selectedTime3]}});
      });
      typeDis.setOption(option);
      // 随着屏幕大小调节图表
      window.addEventListener("resize", () => {
        typeDis.resize();
      });

    },

    async fetchTypeSpread() {
      this.typeData = {
        type: [],
        value: []
      };
      const typeRes = await getTypeSpread(this.orgIndex, this.selectedTime3);
      typeRes.data.forEach((item) => {
        this.typeData.type.push({
          name: item.alarm_type_name,
          color: "black"
        });
        this.typeData.value.push(item.num);
      });
      this.initTypeEcharts();
    },

    async fetchData() {
      try {
        await Promise.all([
          this.fetchDomain(),
          this.fetchTypeSpread()
        ]);
      } catch (error) {
        console.error(error);
      }
    },

    selectTime(time, number) {
      this[`selectedTime${number}`] = time;
    }
  },

  mounted() {
    this.fetchData();

  },

  watch: {
    orgIndex(newVal, oldVal) {
      this.fetchData();
    },
    selectedTime1(newVal, oldVal) {
      this.fetchDomain();
    },
    selectedTime3(newVal, oldVal) {
      this.fetchTypeSpread();
    },
  }
};
</script>

<style scoped lang="less">
.col {
  background-color: white;
  display: flex;
  justify-content: space-around;
  height: 250px;
  text-align: center;
  border-radius: 10px;
  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.05);
}

.col:hover {
  box-shadow: 0 3px 10px 0 rgb(64, 98, 225, 0.45);
}

.left {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}


.left-content {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 15px;
}

.data-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  /* 两列等宽 */
  grid-gap: 15px;
  /* 列与列之间的间距 */
}

.item {
  color: black;
  padding: 6px;
  text-align: left;
}

.clickable {
  cursor: pointer;
  user-select: none;
  transition: color 0.3s ease;
  color: blue;
  font-size: small;
}
</style>
