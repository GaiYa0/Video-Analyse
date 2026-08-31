<template>
  <div class="right_bottom">
    <div class="echart" id="warning-org" :style="myChartStyle"></div>
  </div>
</template>

<script>
import {getRanking} from '@/api/system/kanban';
import * as echarts from "echarts";

export default {
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
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'line',
            lineStyle: {
              opacity: 0
            }
          },
          formatter: '{b}: <span style="color: blue">{c}<span/>'
        },
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
        xAxis: [{
          type: 'category',
          gridIndex: 0,
          data: this.orgData.yData,
          axisTick: {
            alignWithLabel: true
          },
          axisLine: {
            lineStyle: {
              color: '#0c3b71'
            }
          },
          axisLabel: {
            show: true,
            color: '#ffffff',
            fontSize: 12,
            rotate: -17,
            formatter: function (value) {
              var texts = value
              if (texts.length > 4) {
                // 限制长度自设
                texts = texts.substr(0, 4) + '...'
              }
              return texts
            }
          }
        }],
        yAxis: [{
          type: 'value',
          splitLine: {
            show: true,
            lineStyle: {
              color: '#0c3b71'
            }
          },
          axisTick: {
            show: false
          },
          axisLine: {
            lineStyle: {
              color: '#0c3b71'
            }
          },
          axisLabel: {
            color: '#ffffff',
            formatter: '{value}'
          }
        },
          {
            type: 'value',
            gridIndex: 0,
            splitNumber: 12,
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
          barWidth: '30%',
          xAxisIndex: 0,
          yAxisIndex: 0,
          itemStyle: {
            normal: {
              barBorderRadius: 30,
              color: new echarts.graphic.LinearGradient(
                0, 0, 0, 1, [{
                  offset: 0,
                  color: '#00feff'
                },
                  {
                    offset: 0.5,
                    color: '#027eff'
                  },
                  {
                    offset: 1,
                    color: '#0286ff'
                  }
                ]
              )
            }
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
            // data: this.orgData.xData.map(),
            itemStyle: {
              normal: {
                barBorderRadius: 30,
                color: 'rgba(255,255,255,0.1)'
              }
            },
            zlevel: 9
          },

        ]
      };

      const dom = document.getElementById("warning-org")
      dom.setAttribute('_echarts_instance_', '')
      const warningOrg = echarts.init(dom);

      warningOrg.on('click', (params) => {
        this.$router.push({path: "/warning/warning", query: {withQue: 8, time: "年", org_name: params.name}});
      });
      warningOrg.setOption(option);
      //随着屏幕大小调节图表
      window.addEventListener("resize", () => {
        warningOrg.resize();
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
