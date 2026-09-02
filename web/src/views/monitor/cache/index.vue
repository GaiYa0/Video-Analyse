<template>
  <div class="app-container">
    <el-row>
      <el-col :span="24" class="card-box">
        <el-card>
          <div slot="header"><span><i class="el-icon-monitor"></i> 基本信息</span></div>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <table cellspacing="0" style="width: 100%">
              <tbody>
                <tr>
                  <td class="el-table__cell is-leaf"><div class="cell">Redis版本</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.redis_version }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">运行模式</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.redis_mode == "standalone" ? "单机" : "集群" }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">端口</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.tcp_port }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">客户端数</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.connected_clients }}</div></td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf"><div class="cell">运行时间(天)</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.uptime_in_days }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">使用内存</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.used_memory_human }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">使用CPU</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ parseFloat(cache.info.used_cpu_user_children).toFixed(2) }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">内存配置</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.maxmemory_human }}</div></td>
                </tr>
                <tr>
                  <td class="el-table__cell is-leaf"><div class="cell">AOF是否开启</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.aof_enabled == "0" ? "否" : "是" }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">RDB是否成功</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.rdb_last_bgsave_status }}</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">Key数量</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.dbSize">{{ cache.dbSize }} </div></td>
                  <td class="el-table__cell is-leaf"><div class="cell">网络入口/出口</div></td>
                  <td class="el-table__cell is-leaf"><div class="cell" v-if="cache.info">{{ cache.info.instantaneous_input_kbps }}kps/{{cache.info.instantaneous_output_kbps}}kps</div></td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12" class="card-box">
        <el-card>
          <div slot="header"><span><i class="el-icon-pie-chart"></i> 命令统计</span></div>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <div ref="commandstats" style="height: 420px" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="12" class="card-box">
        <el-card>
          <div slot="header"><span><i class="el-icon-odometer"></i> 内存信息</span></div>
          <div class="el-table el-table--enable-row-hover el-table--medium">
            <div ref="usedmemory" style="height: 420px" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { getCache } from "@/api/monitor/cache";
import echartsLifecycle from "@/mixins/echartsLifecycle";
import {
  SVA_CHART_ACCENT,
  SVA_CHART_DEEP,
  SVA_CHART_LIGHT,
  SVA_CHART_MUTED,
  SVA_CHART_PALETTE,
  SVA_CHART_SPLIT,
  SVA_CHART_TEXT,
  svaTooltip
} from "@/utils/chartTheme";

export default {
  name: "Cache",
  mixins: [echartsLifecycle],
  echartsFields: ['commandstats', 'usedmemory'],
  data() {
    return {
      // cache信息
      cache: []
    }
  },
  created() {
    this.getList();
    this.openLoading();
  },
  methods: {
    /** 查缓存询信息 */
    getList() {
      getCache().then((response) => {
        this.cache = response.data;
        this.$modal.closeLoading();

        const commandstats = this.ensureChart('commandstats', this.$refs.commandstats);
        if (commandstats) {
          commandstats.setOption({
          color: SVA_CHART_PALETTE,
          tooltip: Object.assign({
            trigger: "item",
            formatter: "{a} <br/>{b} : {c} ({d}%)",
          }, svaTooltip),
          series: [
            {
              name: "命令",
              type: "pie",
              roseType: "radius",
              radius: [15, 95],
              center: ["50%", "38%"],
              data: response.data.commandStats,
              animationEasing: "cubicInOut",
              animationDuration: 1000,
            }
          ]
          });
        }
        const usedmemory = this.ensureChart('usedmemory', this.$refs.usedmemory);
        if (usedmemory) {
          usedmemory.setOption({
          tooltip: Object.assign({
            formatter: "{b} <br/>{a} : " + this.cache.info.used_memory_human,
          }, svaTooltip),
          series: [
            {
              name: "峰值",
              type: "gauge",
              min: 0,
              max: 1000,
              splitNumber: 5,
              axisLine: {
                lineStyle: {
                  color: [[1, SVA_CHART_DEEP]],
                  width: 8
                }
              },
              axisTick: {
                show: true,
                length: 4,
                lineStyle: { color: SVA_CHART_SPLIT }
              },
              splitLine: {
                length: 8,
                lineStyle: { color: SVA_CHART_LIGHT }
              },
              axisLabel: {
                color: SVA_CHART_MUTED
              },
              title: {
                color: SVA_CHART_TEXT
              },
              detail: {
                formatter: this.cache.info.used_memory_human,
                color: SVA_CHART_ACCENT
              },
              data: [
                {
                  value: parseFloat(this.cache.info.used_memory_human),
                  name: "内存消耗",
                }
              ]
            }
          ]
          });
        }
        this.bindChartResize(() => {
          this.commandstats && this.commandstats.resize();
          this.usedmemory && this.usedmemory.resize();
        });
      });
    },
    // 打开加载层
    openLoading() {
      this.$modal.loading("正在加载缓存监控数据，请稍候！");
    }
  }
};
</script>
