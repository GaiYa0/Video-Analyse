<template>
  <div class="app-container">
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" label-width="98px">
      <el-form-item label="任务号" prop="deploymentId">
        <el-input
          v-model="queryParams.deploymentId"
          placeholder="请输入任务号"
          clearable
          style="width: 220px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="任务名称" prop="taskName">
        <el-input
          v-model="queryParams.taskName"
          placeholder="请输入任务名称"
          clearable
          style="width: 220px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 180px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增布控</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="deploymentList">
      <el-table-column label="任务号" prop="deploymentId" align="center" min-width="120" />
      <el-table-column label="任务名称" prop="taskName" align="center" min-width="160" show-overflow-tooltip />
      <el-table-column label="算法配置" align="center" min-width="260" show-overflow-tooltip>
        <template slot-scope="scope">
          <span>{{ formatAlgorithmSummary(scope.row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center" width="120" />
      <el-table-column label="录像引擎" prop="recordEngine" align="center" width="130">
        <template slot-scope="scope">
          <span>{{ formatRecordEngine(scope.row.recordEngine) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="报警间隔(秒)" prop="alarmIntervalSec" align="center" width="130" />
      <el-table-column label="更新时间" prop="updateTime" align="center" min-width="170">
        <template slot-scope="scope">
          <span>{{ formatUpdateTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="420" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleView(scope.row)">查看详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-s-operation" @click="handleEventOrchestration(scope.row)">事件编排</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-video-play"
            :disabled="isActionCooling(scope.row, 'start')"
            @click="handleStart(scope.row)"
          >启动</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-video-pause"
            :disabled="isActionCooling(scope.row, 'stop')"
            @click="handleStop(scope.row)"
          >停止</el-button>
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

  </div>
</template>

<script>
import { getDeploymentDetail, listDeployments, startDeployment, stopDeployment, updateDeploymentLiveOutput } from '@/api/deployment'
import { previewDeviceMonitor } from '@/api/device'
import { upsertScreenWallStream } from '@/api/screenWall'

export default {
  name: 'DeploymentIndex',
  data() {
    return {
      loading: false,
      total: 0,
      deploymentList: [],
      statusOptions: [
        { label: 'CREATED', value: 'CREATED' },
        { label: 'RUNNING', value: 'RUNNING' },
        { label: 'STOPPED', value: 'STOPPED' }
      ],
      actionCooldownMap: {},
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        deploymentId: undefined,
        taskName: undefined,
        status: undefined
      }
    }
  },
  created() {
    this.getList()
  },
  activated() {
    this.getList()
  },
  methods: {
    getFieldValue(source, ...keys) {
      if (!source) {
        return undefined
      }
      for (let i = 0; i < keys.length; i += 1) {
        const key = keys[i]
        if (source[key] !== undefined && source[key] !== null) {
          return source[key]
        }
      }
      return undefined
    },
    toBoolean(value, defaultValue = false) {
      if (value === undefined || value === null || value === '') {
        return defaultValue
      }
      if (typeof value === 'boolean') {
        return value
      }
      if (typeof value === 'number') {
        return value !== 0
      }
      if (typeof value === 'string') {
        const normalized = value.trim().toLowerCase()
        if (['true', '1', 'yes', 'y'].includes(normalized)) {
          return true
        }
        if (['false', '0', 'no', 'n'].includes(normalized)) {
          return false
        }
      }
      return Boolean(value)
    },
    extractPlayUrl(source) {
      if (!source) {
        return ''
      }
      return source.playUrl || source.play_url || source.devicePlayUrl || source.device_play_url || source.previewUrl || source.preview_url || source.url || source.streamUrl || source.stream_url || source.rtspUrl || source.flvUrl || source.directSourceUrl || source.direct_source_url || source.liveUrl || source.live_url || ''
    },
    async resolveDevicePreviewPlayUrl(deviceId) {
      if (!deviceId) {
        return ''
      }
      try {
        const response = await previewDeviceMonitor(deviceId)
        return this.extractPlayUrl((response && response.data) || response)
      } catch (error) {
        return ''
      }
    },
    buildActionCooldownKey(deploymentId, actionType) {
      return `${deploymentId || ''}_${actionType || ''}`
    },
    isActionCooling(row, actionType) {
      const deploymentId = this.getFieldValue(row, 'deploymentId', 'deployment_id', 'id')
      if (!deploymentId) {
        return false
      }
      const cooldownKey = this.buildActionCooldownKey(deploymentId, actionType)
      return Boolean(this.actionCooldownMap[cooldownKey])
    },
    startActionCooldown(deploymentId, actionType, duration = 2000) {
      const cooldownKey = this.buildActionCooldownKey(deploymentId, actionType)
      this.$set(this.actionCooldownMap, cooldownKey, true)
      window.setTimeout(() => {
        this.$delete(this.actionCooldownMap, cooldownKey)
      }, duration)
    },
    extractActionResult(response) {
      const payload = (response && response.data && typeof response.data === 'object') ? response.data : {}
      const hasSuccess = Object.prototype.hasOwnProperty.call(payload, 'success')
      return {
        success: hasSuccess ? this.toBoolean(payload.success, false) : true,
        shortMessage: this.getFieldValue(payload, 'shortMessage', 'short_message', 'msg', 'message') || '操作已提交'
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
    async executeAction(row, actionType) {
      const deploymentId = row && row.deploymentId
      const actionLabel = actionType === 'start' ? '启动' : '停止'
      const cooldownDuration = actionType === 'start' ? 3000 : 4000
      if (!deploymentId) {
        this.$modal.msgWarning(`缺少 deploymentId，无法${actionLabel}`)
        return
      }
      if (this.isActionCooling(row, actionType)) {
        return
      }
      this.startActionCooldown(deploymentId, 'start', cooldownDuration)
      this.startActionCooldown(deploymentId, 'stop', cooldownDuration)

      let shouldRefresh = false
      try {
        const response = actionType === 'start'
          ? await startDeployment(deploymentId)
          : await stopDeployment(deploymentId)
        const result = this.extractActionResult(response)
        this.showActionFeedback(result)
        shouldRefresh = true
      } catch (error) {
        shouldRefresh = true
      } finally {
        if (shouldRefresh) {
          await this.getList()
        }
      }
    },
    async resolveTaskWallPayload(row) {
      const deploymentId = this.getFieldValue(row, 'deploymentId', 'deployment_id', 'id')
      let detail = row || {}
      if (deploymentId) {
        try {
          const detailRes = await getDeploymentDetail(deploymentId)
          detail = (detailRes && detailRes.data) || detail
        } catch (error) {
          // 详情获取失败时使用列表行兜底字段。
        }
      }

      const sourceId = this.getFieldValue(detail, 'deploymentId', 'deployment_id', 'taskId', 'task_id', 'id') || deploymentId || ''
      const deviceId = this.getFieldValue(detail, 'deviceId', 'device_id', 'apeId', 'ape_id') || this.getFieldValue(row, 'deviceId', 'device_id', 'apeId', 'ape_id') || ''
      const slotIndex = this.getFieldValue(detail, 'slotIndex', 'slot_index')
      const fallbackSlotIndex = this.getFieldValue(row, 'slotIndex', 'slot_index')
      const liveOutputResponse = await updateDeploymentLiveOutput(sourceId, {
        videoEnabled: true,
        liveEventEnabled: true,
        wsEventFps: 8
      })
      const liveOutputData = (liveOutputResponse && liveOutputResponse.data) || liveOutputResponse || {}
      const algorithmStreamUrl = this.getFieldValue(liveOutputData, 'algorithmStreamUrl', 'algorithm_stream_url') || ''
      const taskPushEnabled = Boolean(algorithmStreamUrl)
      let playUrl = algorithmStreamUrl

      if (!playUrl) {
        playUrl = await this.resolveDevicePreviewPlayUrl(deviceId)
      }

      return {
        wallCode: 'main',
        sourceType: 'task',
        sourceId,
        deviceId,
        playUrl,
        title: this.getFieldValue(detail, 'taskName', 'task_name', 'title', 'name') || this.getFieldValue(row, 'taskName', 'task_name', 'title', 'name') || sourceId,
        slotIndex: slotIndex !== undefined && slotIndex !== null ? slotIndex : (fallbackSlotIndex !== undefined && fallbackSlotIndex !== null ? fallbackSlotIndex : null),
        enabled: true,
        taskPushEnabled,
        algorithmStreamUrl
      }
    },
    async getList() {
      this.loading = true
      try {
        const res = await listDeployments(this.queryParams)
        const rows = (res && (res.rows || (res.data && res.data.rows) || res.data || res.list)) || []
        this.deploymentList = Array.isArray(rows) ? rows : []
        this.total =
          (res && (res.total || (res.data && res.data.total))) ||
          (Array.isArray(this.deploymentList) ? this.deploymentList.length : 0)
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
      this.handleQuery()
    },
    handleAdd() {
      this.$router.push('/deployment/add')
    },
    async handleView(row) {
      const deploymentId = row && row.deploymentId
      if (!deploymentId) {
        this.$modal.msgWarning('缺少 deploymentId，无法查询详情')
        return
      }
      this.$router.push({ path: '/deployment/add', query: { deploymentId }})
    },
    handleEventOrchestration(row) {
      const deploymentId = row && row.deploymentId
      if (!deploymentId) {
        this.$modal.msgWarning('缺少 deploymentId，无法进入事件编排')
        return
      }
      this.$router.push({ path: '/deployment/event-orchestration', query: { deploymentId }})
    },
    async handleStart(row) {
      await this.executeAction(row, 'start')
    },
    async handleStop(row) {
      await this.executeAction(row, 'stop')
    },
    async handleAddToWall(row) {
      const payload = await this.resolveTaskWallPayload(row)
      if (!payload.sourceId) {
        this.$modal.msgWarning('缺少任务ID，无法加入监控墙')
        return
      }
      if (!payload.playUrl) {
        this.$modal.msgWarning('缺少设备 playUrl，无法加入监控墙')
        return
      }
      await upsertScreenWallStream(payload)
      this.$modal.msgSuccess('已加入监控墙')
    },
    formatUpdateTime(value) {
      if (value === undefined || value === null || value === '') {
        return '--'
      }
      if (typeof this.parseTime === 'function') {
        return this.parseTime(value) || '--'
      }
      return value
    },
    formatRecordEngine(value) {
      if (value === 'A-SERVER') {
        return '算法服务器'
      }
      if (value === 'M-SERVER') {
        return '媒体服务器'
      }
      return value
    },
    formatAlgorithmSummary(row) {
      const tasks = Array.isArray(row && row.algorithmTasks) ? row.algorithmTasks : []
      if (tasks.length > 0) {
        return tasks
          .map(item => {
            const name = item.algorithmName || item.algorithm_name || item.algorithmCode || item.algorithm_code || '--'
            const target = item.targetCode || item.target_code || '--'
            return `${name} / ${target}`
          })
          .join('；')
      }
      const legacyName = this.getFieldValue(row, 'algorithmName', 'algorithm_name', 'algorithmCode', 'algorithm_code') || '--'
      const legacyTarget = this.getFieldValue(row, 'targetCode', 'target_code') || '--'
      return `${legacyName} / ${legacyTarget}`
    }
  }
}
</script>
