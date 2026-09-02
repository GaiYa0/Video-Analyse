<!-- 报警统计 -->
<template>
  <div ref="levelDis" class="echart" id="levelDis" :style="levelStyle"></div>
</template>

<script>
import {getLevelSpread} from '@/api/system/kanban';
import echartsLifecycle from '@/mixins/echartsLifecycle';
import { SVA_CHART_PALETTE, SVA_CHART_TEXT, svaCountTooltip } from '@/utils/chartTheme';

export default {
  mixins: [echartsLifecycle],
  echartsFields: ['levelChart'],
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
        color: SVA_CHART_PALETTE,
        backgroundColor: 'transparent',
        legend: {
          orient: 'vertical',
          x: 'center',
          bottom: '15%',
          textStyle: {
            color: SVA_CHART_TEXT,
            fontSize: 14,

          },
          icon: 'roundRect',
          data: this.levelData,
        },
        tooltip: svaCountTooltip({
          trigger: 'item',
          formatter: '{b} : {c} 条'
        }),
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

      const levelDis = this.ensureChart('levelChart', this.$refs.levelDis, (params) => {
        if (params.data.name === "未处理") {
          this.$router.push({path: "/warning/warning", query: {withQue: 2, is_handle: 0}});
        } else {
          this.$router.push({path: "/warning/warning", query: {withQue: 2, is_handle: 1}});
        }
      });
      if (!levelDis) {
        return
      }
      levelDis.setOption(option);
      this.bindChartResize(() => {
        this.levelChart && this.levelChart.resize();
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
            color: SVA_CHART_TEXT
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
