<template>
  <div class="app-container" ref="deviceContainer">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="组织名称" prop="org_index">
        <el-select v-model="queryParams.org_index" filterable clearable placeholder="请选择组织名称" style="width: 240px">
          <el-option v-for="item in queryDeptOptions" :key="item.value" :label="item.label" :value="item.value"/>
        </el-select>
      </el-form-item>
      <el-form-item label="设备编码" prop="ape_id">
        <el-input
          v-model="queryParams.ape_id"
          placeholder="请输入设备编码"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="设备名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入设备名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="deviceList" style="width: 100%">
      <el-table-column label="设备名称" prop="name" align="center" :show-overflow-tooltip="true" />
      <el-table-column label="设备编码" prop="ape_id" align="center" :show-overflow-tooltip="true" />
      <el-table-column label="IP地址" prop="ip_addr" align="center" :show-overflow-tooltip="true" />
      <el-table-column label="端口号" prop="port" align="center" />
      <el-table-column label="设备类型" prop="sub_type" align="center">
        <template slot-scope="scope">
          <span>{{ returnType(scope.row.sub_type) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="资源类型" prop="resource_type" align="center">
        <template slot-scope="scope">
          <span>{{ returnResource(scope.row.resource_type) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="通道编码" prop="place" align="center" :show-overflow-tooltip="true" />
      <el-table-column label="状态" prop="is_online" align="center">
        <template slot-scope="scope">
          <span>{{ returnStatus(scope.row.is_online) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="监控状态" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="monitorStatusType(scope.row)">{{ monitorStatusText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" class-name="small-padding fixed-width operation-column" width="310">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-video-play"
            :disabled="isActionCooling(scope.row, 'start') || isActionCooling(scope.row, 'stop')"
            @click="handleStart(scope.row)"
          >启动监控</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-video-pause"
            :disabled="isActionCooling(scope.row, 'start') || isActionCooling(scope.row, 'stop')"
            @click="handleStop(scope.row)"
          >停止监控</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-video-camera"
            @click="handlePreview(scope.row)"
          >预览视频</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-plus"
            @click="handleAddToWall(scope.row)"
          >加入监控墙</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <player
      v-show="viewProof"
      :viewProof="viewProof"
      :rtspUrl="rtspUrl"
      title="实时监控预览"
      @closeProof="viewProof = false"
    />
  </div>
</template>

<script>
import { deptTreeSelect } from '@/api/system/user'
import { getDeviceList, previewDeviceMonitor, startDeviceMonitor, stopDeviceMonitor } from '@/api/device'
import { upsertScreenWallStream } from '@/api/screenWall'
import player from '@/components/RTSPPlayer'

export default {
  name: 'DeviceRealtimeMonitor',
  components: { player },
  data() {
    return {
      loading: false,
      total: 0,
      deviceList: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        org_index: undefined,
        monitor_status: 'RUNNING',
        ape_id: undefined,
        name: undefined
      },
      deptOptions: undefined,
      queryDeptOptions: [],
      viewProof: false,
      rtspUrl: '',
      actionCooldownMap: {}
    }
  },
  computed: {
    deviceContainer() {
      return this.$refs.deviceContainer
    }
  },
  mounted() {
    this.getDeptTree()
    this.$nextTick(() => {
      if (this.deviceContainer && this.deviceContainer.parentNode) {
        this.deviceContainer.parentNode.style.backgroundColor = 'white'
      }
    })
    this.getList()
  },
  methods: {
    getDeptTree() {
      deptTreeSelect().then(response => {
        this.deptOptions = response.data || []
        this.queryDeptOptions = this.buildQueryDeptOptions(this.deptOptions)
      })
    },
    buildQueryDeptOptions(nodes, parentLabel = '') {
      const options = []
      ;(nodes || []).forEach(node => {
        const value = node.org_index || node.id
        const currentLabel = node.label || node.deptName || node.org_name
        if (value !== undefined && currentLabel) {
          const label = parentLabel ? `${parentLabel} / ${currentLabel}` : currentLabel
          options.push({ value, label })
          if (node.children && node.children.length) {
            options.push(...this.buildQueryDeptOptions(node.children, label))
          }
        } else if (node.children && node.children.length) {
          options.push(...this.buildQueryDeptOptions(node.children, parentLabel))
        }
      })
      return options
    },
    getApeId(row) {
      return row.ape_id || row.apeId || row.device_id || row.deviceId
    },
    buildActionCooldownKey(apeId, actionType) {
      return `${apeId || ''}_${actionType || ''}`
    },
    isActionCooling(row, actionType) {
      const apeId = this.getApeId(row)
      if (!apeId) {
        return false
      }
      const cooldownKey = this.buildActionCooldownKey(apeId, actionType)
      return Boolean(this.actionCooldownMap[cooldownKey])
    },
    startActionCooldown(apeId, actionType, duration = 2000) {
      const cooldownKey = this.buildActionCooldownKey(apeId, actionType)
      this.$set(this.actionCooldownMap, cooldownKey, true)
      window.setTimeout(() => {
        this.$delete(this.actionCooldownMap, cooldownKey)
      }, duration)
    },
    getDeviceId(row) {
      return row.deviceId || row.device_id || row.ape_id || row.apeId || ''
    },
    getRowPlayUrl(row) {
      return row.playUrl || row.play_url || row.previewUrl || row.preview_url || row.url || row.streamUrl || row.stream_url || row.rtspUrl || row.flvUrl || row.directSourceUrl || row.direct_source_url || row.liveUrl || row.live_url || ''
    },
    getMonitorState(row) {
      const raw = row.monitor_status || row.monitorStatus || row.run_status || row.runStatus || row.runtime_status || row.runtimeStatus || row.status
      return String(raw || 'UNKNOWN').toUpperCase()
    },
    returnType(type) {
      switch (String(type || '')) {
        case '1':
          return '网络摄像机(IPC)'
        case '2':
          return '网络硬盘录像机(NVR)'
        case '3':
          return '硬盘录像机(DVR)'
        case '4':
          return '车载硬盘录像机(MDVR)'
        case '6':
          return '全景球'
        case '9':
          return '热成像摄像头'
        default:
          return '未知类型'
      }
    },
    returnStatus(status) {
      switch (String(status || '')) {
        case '0':
          return '登录中'
        case '1':
          return '在线/启用'
        case '2':
          return '离线/停用'
        case '9':
          return '其他/异常'
        default:
          return '未知状态'
      }
    },
    returnResource(type) {
      switch (String(type || '')) {
        case '1':
          return '编码器'
        case '3':
          return '解码器'
        default:
          return '未知类型'
      }
    },
    monitorStatusText(row) {
      const state = this.getMonitorState(row)
      if (state === 'RUNNING') {
        return '运行中'
      }
      if (state === 'STARTING') {
        return '启动中'
      }
      if (state === 'STOPPING') {
        return '停止中'
      }
      if (state === 'STOPPED') {
        return '已停止'
      }
      if (state === 'ERROR') {
        return '异常'
      }
      return state
    },
    monitorStatusType(row) {
      const state = this.getMonitorState(row)
      if (state === 'RUNNING') {
        return 'success'
      }
      if (state === 'STARTING') {
        return 'warning'
      }
      if (state === 'STOPPING') {
        return 'warning'
      }
      if (state === 'STOPPED') {
        return 'info'
      }
      if (state === 'ERROR') {
        return 'danger'
      }
      return 'info'
    },
    async getList() {
      this.loading = true
      try {
        const response = await getDeviceList(this.queryParams)
        this.deviceList = response.rows || []
        this.total = response.total || 0
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams.monitor_status = 'RUNNING'
      this.handleQuery()
    },
    extractActionResult(response) {
      const payload = (response && response.data && typeof response.data === 'object') ? response.data : {}
      const hasSuccess = Object.prototype.hasOwnProperty.call(payload, 'success')
      return {
        success: hasSuccess ? Boolean(payload.success) : true,
        shortMessage: payload.shortMessage || '操作已提交'
      }
    },
    showActionFeedback(result) {
      if (!result) {
        return
      }
      if (result.success) {
        this.$modal.msgSuccess(result.shortMessage)
      } else {
        this.$modal.msgWarning(result.shortMessage)
      }
    },
    async executeMonitorAction(row, actionType) {
      const apeId = this.getApeId(row)
      const actionLabel = actionType === 'start' ? '启动' : '停止'
      const cooldownDuration = actionType === 'start' ? 3000 : 4000
      if (!apeId) {
        this.$modal.msgError(`设备编码不存在，无法${actionLabel}`)
        return
      }
      if (this.isActionCooling(row, 'start') || this.isActionCooling(row, 'stop')) {
        return
      }

      this.startActionCooldown(apeId, 'start', cooldownDuration)
      this.startActionCooldown(apeId, 'stop', cooldownDuration)

      try {
        const response = actionType === 'start'
          ? await startDeviceMonitor(apeId)
          : await stopDeviceMonitor(apeId)
        const result = this.extractActionResult(response)
        this.showActionFeedback(result)
      } catch (error) {
        this.$modal.msgWarning(`提交${actionLabel}请求失败，请稍后重试`)
      } finally {
        await this.getList()
      }
    },
    async handleStart(row) {
      await this.executeMonitorAction(row, 'start')
    },
    async handleStop(row) {
      await this.executeMonitorAction(row, 'stop')
    },
    extractPreviewUrl(response) {
      if (!response) {
        return ''
      }
      const data = response.data || response
      return data.playUrl || data.previewUrl || data.url || data.streamUrl || data.rtspUrl || data.flvUrl || data.directSourceUrl || data.direct_source_url || data.liveUrl || data.live_url || ''
    },
    async handlePreview(row) {
      const apeId = this.getApeId(row)
      if (!apeId) {
        this.$modal.msgError('设备编码不存在，无法预览')
        return
      }
      const response = await previewDeviceMonitor(apeId)
      const playUrl = this.extractPreviewUrl(response)
      if (!playUrl) {
        this.$modal.msgWarning('暂无可播放地址，请先启动监控后重试')
        return
      }
      this.rtspUrl = playUrl
      this.viewProof = true
    },
    async handleAddToWall(row) {
      const sourceId = this.getApeId(row) || this.getDeviceId(row)
      if (!sourceId) {
        this.$modal.msgError('设备编码不存在，无法加入监控墙')
        return
      }
      const playUrl = this.getRowPlayUrl(row)
      if (!playUrl) {
        this.$modal.msgWarning('当前行无 playUrl，无法加入监控墙')
        return
      }

      await upsertScreenWallStream({
        wallCode: 'main',
        sourceType: 'realtime',
        sourceId,
        deviceId: this.getDeviceId(row),
        playUrl,
        title: row.name || row.deviceName || row.device_name || sourceId,
        slotIndex: null,
        enabled: true,
        taskPushEnabled: false,
        algorithmStreamUrl: ''
      })
      this.$modal.msgSuccess('已加入监控墙')
    }
  }
}
</script>

<style scoped>
::v-deep .operation-column .cell {
  white-space: nowrap;
}

::v-deep .operation-column .el-button + .el-button {
  margin-left: 6px;
}

::v-deep .operation-column .el-button--text {
  padding: 0;
}
</style>
