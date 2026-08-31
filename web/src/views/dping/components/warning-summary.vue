<!-- 报警统计 -->
<template>
  <div class="echart" id="levelDis" :style="levelStyle"></div>
</template>

<script>
import {getLevelSpread} from '@/api/system/kanban';
import * as echarts from "echarts";

export default {
  data() {
    return {
      levelStyle: {
        float: "left", width: "100%", height: "100%"
      },
      levelData: [],
      levelSettings: {
        radius: 53,
        offsetY: 190
      },
      pushRefreshTimer: null,
    };
  },

  mounted() {
    this.fetchLevelSpread()
    window.addEventListener('sva:alarm-push', this.handleAlarmPush)
  },

  beforeDestroy() {
    window.removeEventListener('sva:alarm-push', this.handleAlarmPush)
    this.clearData()
  },

  methods: {
    initLevelEcharts() {
      const option = {
        color: ['#A0CE3A', '#31C5C0', '#1E9BD1'],
        backgroundColor: 'transparent',
        legend: {
          orient: 'vertical',
          x: 'center',
          bottom: '15%',
          textStyle: {
            color: '#f2f2f2',
            fontSize: 14,

          },
          icon: 'roundRect',
          data: this.levelData,
        },
        tooltip: {
          trigger: 'item',
          formatter: '{b} : {c}'
        },
        series: [
          // 主要展示层的
          {
            radius: ['30%', '61%'],
            center: ['50%', '30%'],
            type: 'pie',
            label: {
              normal: {
                show: false,
              },
            },
            labelLine: {
              normal: {
                show: true,
                length: 30,
                length2: 55
              },
              emphasis: {
                show: true
              }
            },
            data: this.levelData,

          },
          // 边框的设置
          {
            radius: ['30%', '34%'],
            center: ['50%', '30%'],
            type: 'pie',
            label: {
              normal: {
                show: false
              },
              emphasis: {
                show: false
              }
            },
            labelLine: {
              normal: {
                show: false
              },
              emphasis: {
                show: false
              }
            },
            animation: false,
            tooltip: {
              show: false
            },
            data: [{
              value: 1,
              itemStyle: {
                color: "rgba(250,250,250,0.3)",
              },
            }],
          }
        ]
      }

      const dom = document.getElementById("levelDis")
      dom.setAttribute('_echarts_instance_', '')
      const levelDis = echarts.init(dom)
      levelDis.on('click', (params) => {
        if (params.data.name === "未处理") {
          this.$router.push({path: "/warning/warning", query: {withQue: 2, is_handle: 0}});
        } else {
          this.$router.push({path: "/warning/warning", query: {withQue: 2, is_handle: 1}});
        }
      });
      levelDis.setOption(option);
      // 随着屏幕大小调节图表
      window.addEventListener("resize", () => {
        levelDis.resize();
      });

    },

    async fetchLevelSpread() {
      this.levelData = [];
      const levelRes = await getLevelSpread(this.orgIndex, 2);
      levelRes.data.map(item => {
        this.levelData.push({
          value: item.num,
          name: item.is_handle,
          label: {
            color: "white" // 指示文字颜色
          }
        });
      });
      this.initLevelEcharts();
    },

    handleAlarmPush() {
      if (this.pushRefreshTimer) {
        return;
      }
      this.pushRefreshTimer = setTimeout(async () => {
        this.pushRefreshTimer = null;
        await this.fetchLevelSpread();
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

<style lang='scss' scoped></style>
