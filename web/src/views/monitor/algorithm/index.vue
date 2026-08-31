<template>
  <div class="app-container">
    <el-row :gutter="12" class="summary-row">
      <el-col :xs="24" :sm="12" :md="6" class="card-box">
        <el-card shadow="hover">
          <div class="summary-label">服务器总数</div>
          <div class="summary-value">{{ summary.serverTotal }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" class="card-box">
        <el-card shadow="hover">
          <div class="summary-label">连接成功</div>
          <div class="summary-value text-success">{{ summary.serverSuccess }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" class="card-box">
        <el-card shadow="hover">
          <div class="summary-label">连接失败</div>
          <div class="summary-value text-danger">{{ summary.serverFailed }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6" class="card-box">
        <el-card shadow="hover">
          <div class="summary-label">任务总数</div>
          <div class="summary-value">{{ summary.taskTotal }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <div slot="header" class="table-header">
        <span><i class="el-icon-s-operation"></i> 算法服务器列表</span>
        <el-button size="mini" type="primary" icon="el-icon-refresh" :loading="loading" @click="getList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="controls" border>
        <el-table-column label="编号" prop="code" align="center" min-width="220" :show-overflow-tooltip="true" />
        <el-table-column label="视频流" prop="streamUrl" min-width="260" :show-overflow-tooltip="true" />
        <el-table-column label="算法" prop="algorithmCode" min-width="120" :show-overflow-tooltip="true" />
        <el-table-column label="部署ID" prop="deployment_id" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column label="任务名称" prop="task_name" min-width="180" :show-overflow-tooltip="true" />
        <el-table-column label="设备ID" prop="device_id" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column label="频率（实际/目标）" min-width="140" align="center">
          <template slot-scope="scope">
            <span>{{ formatFps(scope.row.checkFps, scope.row.detectFps) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="添加时间" min-width="180" align="center">
          <template slot-scope="scope">
            <span>{{ formatStartTime(scope.row.startTimestamp) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="是否推流" min-width="100" align="center">
          <template slot-scope="scope">
            <span>{{ formatPushStream(scope.row.pushStream) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { getAlgorithmControls } from "@/api/monitor/algorithm";
import { parseTime } from "@/utils/ruoyi";

export default {
  name: "AlgorithmServer",
  data() {
    return {
      loading: false,
      summary: {
        serverTotal: 0,
        serverSuccess: 0,
        serverFailed: 0,
        taskTotal: 0
      },
      controls: []
    };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      this.loading = true;
      getAlgorithmControls()
        .then(response => {
          const payload = this.normalizePayload(response);
          this.summary.serverTotal = this.toNumber(payload.serverTotal);
          this.summary.serverSuccess = this.toNumber(payload.serverSuccess);
          this.summary.serverFailed = this.toNumber(payload.serverFailed);
          this.summary.taskTotal = this.toNumber(payload.taskTotal);
          this.controls = this.normalizeControls(payload);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    normalizePayload(response) {
      if (!response) {
        return {};
      }
      if (response.data && typeof response.data === "object") {
        return response.data;
      }
      return response;
    },
    normalizeControls(payload) {
      if (!payload || typeof payload !== "object") {
        return [];
      }
      if (Array.isArray(payload.controls)) {
        return payload.controls.map(item => this.normalizeControlItem(item));
      }
      if (Array.isArray(payload.rows)) {
        return payload.rows.map(item => this.normalizeControlItem(item));
      }
      if (Array.isArray(payload.list)) {
        return payload.list.map(item => this.normalizeControlItem(item));
      }
      return [];
    },
    normalizeControlItem(item) {
      if (!item || typeof item !== "object") {
        return item;
      }
      return {
        ...item,
        deployment_id: item.deployment_id !== undefined ? item.deployment_id : item.deploymentId,
        task_name: item.task_name !== undefined ? item.task_name : item.taskName,
        device_id: item.device_id !== undefined ? item.device_id : item.deviceId
      };
    },
    toNumber(value) {
      const num = Number(value);
      return Number.isFinite(num) ? num : 0;
    },
    formatFps(checkFps, detectFps) {
      const checkText = this.toFpsText(checkFps);
      const detectText = this.toFpsText(detectFps);
      if (checkText === "-" && detectText === "-") {
        return "-";
      }
      return checkText + " / " + detectText;
    },
    toFpsText(value) {
      if (value === null || value === undefined || value === "") {
        return "-";
      }
      const num = Number(value);
      if (!Number.isFinite(num)) {
        return "-";
      }
      return num.toFixed(2);
    },
    formatStartTime(value) {
      return parseTime(value, "{y}-{m}-{d} {h}:{i}:{s}") || "-";
    },
    formatPushStream(value) {
      const enabled = value === true || value === 1 || value === "1" || value === "true";
      return enabled ? "是" : "否";
    }
  }
};
</script>

<style scoped>
.summary-row {
  margin-bottom: 12px;
}

.summary-label {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  margin-top: 8px;
  font-size: 28px;
  line-height: 1;
  font-weight: 600;
  color: #303133;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>