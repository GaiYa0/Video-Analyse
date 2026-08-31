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
          <div class="summary-label">流总数</div>
          <div class="summary-value">{{ summary.streamTotal }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <div slot="header" class="table-header">
        <span><i class="el-icon-video-camera"></i> 媒体流列表</span>
        <el-button size="mini" type="primary" icon="el-icon-refresh" :loading="loading" @click="getList">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="streams" border>
        <el-table-column label="名称" prop="name" min-width="180" :show-overflow-tooltip="true" />
        <el-table-column label="来源设备" prop="deviceName" min-width="180" :show-overflow-tooltip="true" />
        <el-table-column label="在线人数" prop="onlineCount" align="center" width="100" />
        <el-table-column label="入口带宽" align="center" width="140">
          <template slot-scope="scope">
            <span>{{ formatBandwidth(scope.row.ingressBps) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="视频信息" min-width="220" :show-overflow-tooltip="true">
          <template slot-scope="scope">
            <span>{{ formatVideoInfo(scope.row.videoInfo) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="音频信息" min-width="220" :show-overflow-tooltip="true">
          <template slot-scope="scope">
            <span>{{ formatAudioInfo(scope.row.audioInfo) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { getMediaStreams } from "@/api/monitor/media";

export default {
  name: "MediaServer",
  data() {
    return {
      loading: false,
      summary: {
        serverTotal: 0,
        serverSuccess: 0,
        serverFailed: 0,
        streamTotal: 0
      },
      streams: []
    };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      this.loading = true;
      getMediaStreams()
        .then(response => {
          const payload = this.normalizePayload(response);
          this.summary.serverTotal = this.toNumber(payload.serverTotal);
          this.summary.serverSuccess = this.toNumber(payload.serverSuccess);
          this.summary.serverFailed = this.toNumber(payload.serverFailed);
          this.summary.streamTotal = this.toNumber(payload.streamTotal);
          this.streams = this.normalizeStreams(payload.streams);
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
    normalizeStreams(streams) {
      if (!Array.isArray(streams)) {
        return [];
      }
      return streams.map(item => {
        if (!item || typeof item !== "object") {
          return {};
        }
        const stream = item.stream || "";
        const deviceName = item.deviceName || stream;
        return {
          ...item,
          name: item.name || stream || "",
          deviceName
        };
      });
    },
    toNumber(val) {
      const num = Number(val);
      return Number.isFinite(num) ? num : 0;
    },
    formatBandwidth(ingressBps) {
      const value = Number(ingressBps);
      if (!Number.isFinite(value) || value < 0) {
        return "-";
      }
      if (value < 1024) {
        return value.toFixed(0) + " B/s";
      }
      if (value < 1024 * 1024) {
        return (value / 1024).toFixed(2) + " KB/s";
      }
      return (value / 1024 / 1024).toFixed(2) + " MB/s";
    },
    formatVideoInfo(videoInfo) {
      if (!videoInfo) {
        return "-";
      }
      if (typeof videoInfo === "string") {
        return videoInfo;
      }
      const codec = videoInfo.codec || videoInfo.videoCodec;
      const width = videoInfo.width || videoInfo.w;
      const height = videoInfo.height || videoInfo.h;
      const resolution = videoInfo.resolution || (width && height ? width + "x" + height : "");
      const fps = videoInfo.fps || videoInfo.frameRate;
      const fpsText = fps ? fps + "fps" : "";
      return [codec, resolution, fpsText].filter(Boolean).join(" / ") || "-";
    },
    formatAudioInfo(audioInfo) {
      if (!audioInfo) {
        return "-";
      }
      if (typeof audioInfo === "string") {
        return audioInfo;
      }
      const codec = audioInfo.codec || audioInfo.audioCodec;
      const sampleRate = audioInfo.sampleRate || audioInfo.samplingRate;
      const sampleRateText = sampleRate ? sampleRate + "Hz" : "";
      const channels = audioInfo.channels || audioInfo.channel;
      const channelsText = channels ? channels + "ch" : "";
      return [codec, sampleRateText, channelsText].filter(Boolean).join(" / ") || "-";
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