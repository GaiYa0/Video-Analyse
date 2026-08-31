<!--<template>-->
<!--  <tiny-layout>-->
<!--    <h3 style="margin-left: 11px;">报警综合排行统计</h3>-->
<!--    <tiny-row :flex="true" justify="center" class="margin-bottom" style="background-color: white;">-->
<!--      <div class="echart" id="warning-org" :style="myChartStyle"></div>-->
<!--      <div class="divider"></div>-->
<!--      <div class="echart" id="warning-level" :style="myChartStyle"></div>-->
<!--      <div class="divider"></div>-->
<!--      <div class="echart" id="warning-type" :style="myChartStyle"></div>-->
<!--    </tiny-row>-->
<!--  </tiny-layout>-->
<!--</template>-->

<!--<script>-->
<!--import { getRanking } from '@/api/system/kanban';-->
<!--import * as echarts from "echarts";-->
<!--import {-->
<!--  Layout as TinyLayout,-->
<!--  Row as TinyRow,-->
<!--  ChartHistogram as TinyChartHistogram,-->
<!--  ChartBar as TinyChartBar-->
<!--} from '@opentiny/vue';-->

<!--export default {-->
<!--  components: {-->
<!--    TinyLayout,-->
<!--    TinyRow,-->
<!--    TinyChartHistogram,-->
<!--    TinyChartBar,-->
<!--  },-->

<!--  props: {-->
<!--    orgIndex: {-->
<!--      type: String,-->
<!--      default: ''-->
<!--    }-->
<!--  },-->

<!--  data() {-->
<!--    return {-->
<!--      // 1. 报警-组织排行-->
<!--      orgData: {-->
<!--        yData: [],-->
<!--        xData: []-->
<!--      },-->

<!--      // 2. 报警-级别排行-->
<!--      levelData: {-->
<!--        yData: [],-->
<!--        xData: []-->
<!--      },-->

<!--      // 3. 报警类型排行-->
<!--      typeData: {-->
<!--        yData: [],-->
<!--        xData: []-->
<!--      },-->

<!--      myChartStyle: {-->
<!--        float: "left", width: "100%", height: "230px"-->
<!--      }-->
<!--    };-->
<!--  },-->

<!--  methods: {-->
<!--    async fetchData() {-->
<!--      try {-->
<!--        const response = await getRanking(this.orgIndex);-->
<!--        if (response.code !== 200) throw new Error(response.msg);-->

<!--        this.orgData = {-->
<!--          yData: [],-->
<!--          xData: []-->
<!--        };-->

<!--        this.levelData = {-->
<!--          yData: [],-->
<!--          xData: []-->
<!--        };-->

<!--        this.typeData = {-->
<!--          yData: [],-->
<!--          xData: []-->
<!--        };-->

<!--        response.data.org.map(item => {-->
<!--          this.orgData.xData.push({-->
<!--            value: item.num,-->
<!--            itemStyle: {-->
<!--              color: "rgba(65,160,227, 1)"-->
<!--            }-->
<!--          });-->
<!--          this.orgData.yData.push({-->
<!--            value: item.dept_name,-->
<!--            textStyle: {-->
<!--              color: "black"-->
<!--            },-->
<!--            fontSize: 22-->
<!--          });-->
<!--        });-->

<!--        response.data.level.map(item => {-->
<!--          this.levelData.xData.push({-->
<!--            value: item.num,-->
<!--            itemStyle: {-->
<!--              color: "rgba(65,160,227, 1)"-->
<!--            }-->
<!--          });-->
<!--          this.levelData.yData.push({-->
<!--            value: item.alarm_level_name,-->
<!--            textStyle: {-->
<!--              color: "black"-->
<!--            },-->
<!--            fontSize: 22-->
<!--          });-->
<!--        });-->

<!--        response.data.type.map(item => {-->
<!--          this.typeData.xData.push({-->
<!--            value: item.num,-->
<!--            itemStyle: {-->
<!--              color: "rgba(65,160,227, 1)"-->
<!--            }-->
<!--          });-->
<!--          this.typeData.yData.push({-->
<!--            value: item.alarm_type_name,-->
<!--            textStyle: {-->
<!--              color: "black"-->
<!--            },-->
<!--            fontSize: 22-->
<!--          });-->
<!--        });-->

<!--        this.initOrgEcharts();-->
<!--        this.initLevelEcharts();-->
<!--        this.initTypeEcharts();-->
<!--      } catch (error) {-->
<!--        console.error(error);-->
<!--      }-->
<!--    },-->

<!--    initOrgEcharts() {-->
<!--      const option = {-->
<!--        title: {-->
<!--          text: '报警-组织排行',-->
<!--          textStyle: {-->
<!--            fontSize: 20,-->
<!--            color: "black",-->
<!--          },-->
<!--          left: "center",-->
<!--        },-->
<!--        tooltip: {-->
<!--          trigger: 'axis',-->
<!--          axisPointer: {-->
<!--            type: 'shadow'-->
<!--          }-->
<!--        },-->
<!--        grid: {-->
<!--          left: '3%',-->
<!--          right: '4%',-->
<!--          bottom: '3%',-->
<!--          containLabel: true-->
<!--        },-->
<!--        xAxis: {-->
<!--          type: 'value',-->
<!--          boundaryGap: [0, 0.01],-->
<!--          axisLabel: {-->
<!--            color: "black"-->
<!--          }-->
<!--        },-->
<!--        yAxis: {-->
<!--          type: 'category',-->
<!--          data: this.orgData.yData-->
<!--        },-->
<!--        series: [-->
<!--          {-->
<!--            name: '2012',-->
<!--            type: 'bar',-->
<!--            data: this.orgData.xData-->
<!--          }-->
<!--        ]-->
<!--      };-->

<!--      const warningOrg = echarts.init(document.getElementById("warning-org"));-->
<!--      warningOrg.on('click', (params) => {-->
<!--        // console.log('您点击了该部门：', params.name);-->
<!--        this.$router.push({ path: "/warning/warning", query: {withQue: 8, time: "年"} });-->
<!--      });-->
<!--      warningOrg.setOption(option);-->
<!--      //随着屏幕大小调节图表-->
<!--      window.addEventListener("resize", () => {-->
<!--        warningOrg.resize();-->
<!--      });-->
<!--    },-->

<!--    initLevelEcharts() {-->
<!--      const option = {-->
<!--        title: {-->
<!--          text: '报警-等级排行',-->
<!--          textStyle: {-->
<!--            fontSize: 20,-->
<!--            color: "black",-->
<!--          },-->
<!--          left: "center",-->
<!--        },-->
<!--        tooltip: {-->
<!--          trigger: 'axis',-->
<!--          axisPointer: {-->
<!--            type: 'shadow'-->
<!--          }-->
<!--        },-->
<!--        grid: {-->
<!--          left: '3%',-->
<!--          right: '4%',-->
<!--          bottom: '3%',-->
<!--          containLabel: true-->
<!--        },-->
<!--        xAxis: {-->
<!--          type: 'value',-->
<!--          boundaryGap: [0, 0.01],-->
<!--          axisLabel: {-->
<!--            color: "black"-->
<!--          }-->
<!--        },-->
<!--        yAxis: {-->
<!--          type: 'category',-->
<!--          data: this.levelData.yData-->
<!--        },-->
<!--        series: [-->
<!--          {-->
<!--            name: '2012',-->
<!--            type: 'bar',-->
<!--            data: this.levelData.xData-->
<!--          }-->
<!--        ]-->
<!--      };-->

<!--      const warningLevel = echarts.init(document.getElementById("warning-level"));-->
<!--      warningLevel.on('click', (params) => {-->
<!--        this.$router.push({ path: "/warning/warning", query: {withQue: 2, alarm_level_name: params.name} });-->
<!--      });-->
<!--      warningLevel.setOption(option);-->
<!--      //随着屏幕大小调节图表-->
<!--      window.addEventListener("resize", () => {-->
<!--        warningLevel.resize();-->
<!--      });-->
<!--    },-->

<!--    initTypeEcharts() {-->
<!--      const option = {-->
<!--        title: {-->
<!--          text: '报警-类型排行',-->
<!--          textStyle: {-->
<!--            fontSize: 20,-->
<!--            color: "black",-->
<!--          },-->
<!--          left: "center",-->
<!--        },-->
<!--        tooltip: {-->
<!--          trigger: 'axis',-->
<!--          axisPointer: {-->
<!--            type: 'shadow'-->
<!--          }-->
<!--        },-->
<!--        grid: {-->
<!--          left: '3%',-->
<!--          right: '4%',-->
<!--          bottom: '3%',-->
<!--          containLabel: true-->
<!--        },-->
<!--        xAxis: {-->
<!--          type: 'value',-->
<!--          boundaryGap: [0, 0.01],-->
<!--          axisLabel: {-->
<!--            color: "black"-->
<!--          }-->
<!--        },-->
<!--        yAxis: {-->
<!--          type: 'category',-->
<!--          data: this.typeData.yData-->
<!--        },-->
<!--        series: [-->
<!--          {-->
<!--            name: '2012',-->
<!--            type: 'bar',-->
<!--            data: this.typeData.xData-->
<!--          }-->
<!--        ]-->
<!--      };-->

<!--      const warningType = echarts.init(document.getElementById("warning-type"));-->
<!--      warningType.on('click', (params) => {-->
<!--        this.$router.push({ path: "/warning/warning", query: {withQue: 2, alarm_type_name: params.name} });-->
<!--      });-->
<!--      warningType.setOption(option);-->
<!--      //随着屏幕大小调节图表-->
<!--      window.addEventListener("resize", () => {-->
<!--        warningType.resize();-->
<!--      });-->
<!--    },-->
<!--  },-->
<!--  mounted() {-->
<!--    this.fetchData();-->
<!--  },-->

<!--  watch: {-->
<!--    orgIndex(newVal, oldVal) {-->
<!--      this.fetchData();-->
<!--    }-->
<!--  }-->
<!--};-->
<!--</script>-->

<!--<style scoped lang="less">-->
<!--.margin-bottom {-->
<!--  display: flex;-->
<!--  align-items: center;-->
<!--  justify-content: center;-->
<!--  width: 98.5%;-->
<!--  height: 240px;-->
<!--  margin: 0 auto;-->
<!--  background: white;-->
<!--  border-radius: 10px;-->
<!--  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.05);-->
<!--}-->

<!--.margin-bottom:hover {-->
<!--  box-shadow: 0 3px 10px 0 rgb(64, 98, 225, 0.45);-->
<!--}-->

<!--.col {-->
<!--  text-align: center;-->
<!--}-->

<!--.col>span {-->
<!--  display: flex;-->
<!--  flex-direction: column;-->
<!--  padding: 10px 10px;-->
<!--  text-align: center;-->
<!--}-->

<!--.col>span:last-child {-->
<!--  color: #4e5969;-->
<!--  font-weight: normal;-->
<!--  font-size: 18px;-->
<!--  line-height: 14px;-->
<!--}-->

<!--.divider {-->
<!--  width: 1px;-->
<!--  height: 41px;-->
<!--  margin: 0 20px;-->
<!--  background: #7b7e84;-->
<!--  opacity: 0.3;-->
<!--}-->

<!--.font {-->
<!--  color: #575d6c;-->
<!--  font-weight: 600;-->
<!--  font-size: 30px;-->
<!--  font-family: PingFang SC, PingFang SC-PingFang SC;-->
<!--  line-height: 36px;-->
<!--  text-align: left;-->
<!--}-->

<!--.font-pass {-->
<!--  .font();-->
<!--}-->

<!--.font-fail {-->
<!--  .font();-->

<!--  color: #2f5bea;-->
<!--}-->

<!--@media (max-width: 1600px) {-->
<!--  .font-pass {-->
<!--    font-size: 24px;-->
<!--  }-->

<!--  .col>span:last-child {-->
<!--    font-size: 10px;-->
<!--  }-->
<!--}-->
<!--</style>-->
