<template>
  <div class="right_bottom">
    <div ref="warningOrg" class="echart" id="warning-org" :style="myChartStyle"></div>
  </div>
</template>

<script>
import {getRanking} from '@/api/system/kanban';
import * as echarts from "echarts";
import echartsLifecycle from '@/mixins/echartsLifecycle';
import {
  SVA_CHART_BAR_MAX_WIDTH,
  SVA_CHART_BAR_RADIUS_VERTICAL,
  SVA_CHART_DEEP,
  SVA_CHART_LIGHT,
  SVA_CHART_SPLIT,
  SVA_CHART_TEXT,
  svaCategoryAxis,
  svaCountTooltip,
  svaValueAxis
} from '@/utils/chartTheme';

export default {
  mixins: [echartsLifecycle],
  echartsFields: ['orgChart'],
  data() {
    return {
      myChartStyle: {
        float: "left", width: "100%", height: "270px"
      },
      orgData: {
        yData: [],
        xData: []
      },
      pushRefreshTimer: null
    };
  },

  mounted() {
    this.fetchData()
    window.addEventListener('sva:alarm-push', this.handleAlarmPush)
  },

  beforeDestroy() {
    window.removeEventListener('sva:alarm-push', this.handleAlarmPush)
    this.clearData()
  },

  methods: {

    async fetchData() {
      this.orgData.xData.length = 0
      this.orgData.yData.length = 0
      this.pageflag = true
      const response = await getRanking(this.orgIndex);
      if (response.code !== 200) throw new Error(response.msg);
      response.data.org.map(item => {
        this.orgData.xData.push(item.num);
        this.orgData.yData.push(item.dept_name);
      });

      this.initOrgEcharts();
    },

    initOrgEcharts() {
      const option = {
        backgroundColor: "transparent",
        tooltip: svaCountTooltip({
          trigger: 'axis',
          axisPointer: {
            type: 'line',
            lineStyle: {
              opacity: 0
            }
          },
          formatter: '{b}: {c} 条'
        }),
        legend: {
          data: ['直接访问', '背景'],
          show: false
        },
        grid: {
          left: '1%',
          right: '6%',
          top: '3%',
          height: '85%',
          containLabel: true,
          z: 22
        },
        xAxis: [svaCategoryAxis({
          gridIndex: 0,
          data: this.orgData.yData,
          axisTick: {
            show: true,
            alignWithLabel: true,
            length: 3,
            lineStyle: { color: SVA_CHART_SPLIT }
          },
          axisLabel: {
            show: true,
            color: SVA_CHART_TEXT,
            fontSize: 12,
            rotate: -17,
            formatter: function (value) {
              var texts = value
              if (texts.length > 4) {
                texts = texts.substr(0, 4) + '...'
              }
              return texts
            }
          }
        })],
        yAxis: [svaValueAxis({
          axisLabel: {
            color: SVA_CHART_TEXT,
            formatter: '{value}'
          }
        }),
          {
            type: 'value',
            gridIndex: 0,
            splitNumber: 4,
            splitLine: {
              show: false
            },
            axisLine: {
              show: false
            },
            axisTick: {
              show: false
            },
            axisLabel: {
              show: false
            },
            splitArea: {
              show: true,
              areaStyle: {
                color: ['rgba(250,250,250,0.0)', 'rgba(250,250,250,0.05)']
              }
            }
          }
        ],
        series: [{
          type: 'bar',
          barMaxWidth: SVA_CHART_BAR_MAX_WIDTH,
          barWidth: '30%',
          xAxisIndex: 0,
          yAxisIndex: 0,
          itemStyle: {
            borderRadius: SVA_CHART_BAR_RADIUS_VERTICAL,
            color: new echarts.graphic.LinearGradient(
              0, 0, 0, 1, [{
                offset: 0,
                color: SVA_CHART_LIGHT
              },
                {
                  offset: 1,
                  color: SVA_CHART_DEEP
                }
              ]
            )
          },
          data: this.orgData.xData,
          zlevel: 11

        },
          {
            name: '背景',
            type: 'bar',
            barWidth: '50%',
            xAxisIndex: 0,
            yAxisIndex: 1,
            barGap: '-135%',
            itemStyle: {
              borderRadius: SVA_CHART_BAR_RADIUS_VERTICAL,
              color: 'rgba(158,197,212,0.08)'
            },
            zlevel: 9
          },

        ]
      };

      const warningOrg = this.ensureChart('orgChart', this.$refs.warningOrg, (params) => {
        this.$router.push({path: "/warning/warning", query: {withQue: 8, time: "年", org_name: params.name}});
      });
      if (!warningOrg) {
        return
      }
      warningOrg.setOption(option);
      this.bindChartResize(() => {
        this.orgChart && this.orgChart.resize();
      });
    },

    handleAlarmPush() {
      if (this.pushRefreshTimer) {
        return;
      }
      this.pushRefreshTimer = setTimeout(async () => {
        this.pushRefreshTimer = null;
        await this.fetchData();
      }, 2008);
    },

    clearData() {
      if (this.pushRefreshTimer) {
        clearTimeout(this.pushRefreshTimer)
        this.pushRefreshTimer = null
      }
    },
  },
};
</script>
