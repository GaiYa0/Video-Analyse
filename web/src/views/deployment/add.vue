<template>
  <div class="app-container deployment-add-page">
    <header class="workspace-header">
      <div class="workspace-title-wrap">
        <div class="page-title">布控管理</div>
          <div v-if="deploymentId" class="deployment-id-panel">
            <span class="deployment-id-label">任务号</span>
            <el-tag size="small" type="success" effect="plain">{{ deploymentId }}</el-tag>
            <el-button size="mini" type="text" @click="handleCopyDeploymentId">复制</el-button>
          </div>

      </div>
      <div class="workspace-actions">
        <el-button size="mini" @click="handleCreateNew">新建布控</el-button>
        <span
          class="event-orchestration-entry"
          :class="{ 'is-active': eventOrchestrationEntryEnabled }"
          @click="handleOpenEventOrchestration"
        >事件编排（可选）</span>
        <el-button type="primary" size="mini" :loading="saveLoading" @click="handleSave">{{ saveButtonText }}</el-button>
      </div>
    </header>

    <div class="workspace-body" :class="{ 'is-rules-tab': workspaceTab === 'rules' }">
      <section class="preview-pane">
        <div class="card-header">实时流预览与区域绘制</div>
          <div class="video-panel">
            <video-preview-pane
              ref="previewPane"
              @canvas-click="handleCanvasClick"
              @canvas-dblclick="handleCanvasDblClick"
              @loadedmetadata="handleVideoLoaded"
              @canvas-resized="drawPolygon"
            />
            <geometry-toolbar
              :geometry-editor-mode.sync="geometryEditorMode"
              :active-region-id="activeRegionId"
              :active-line-id="activeLineId"
              :region-options="regionOptions"
              :line-options="lineOptions"
              :polygon-point-count="polygonPoints.length"
              :polygon-closed="polygonClosed"
              :geometry-region-count="geometryRegionCount"
              :geometry-line-count="geometryLineCount"
              :primary-region-label="primaryRegionLabel"
              :geometry-editor-hint="geometryEditorHint"
              :active-region-is-primary="activeRegionIsPrimary"
              @align="handleAlignCurrentGeometry"
              @clear="handleClearCurrentGeometry"
              @add-region="handleAddRegion"
              @select-region="handleSelectRegion"
              @set-primary="handleSetActivePrimary"
              @remove-region="handleRemoveActiveRegion"
              @add-line="handleAddLine"
              @select-line="handleSelectLine"
              @remove-line="handleRemoveActiveLine"
            />
          <div class="preview-meta">
              <div class="video-rule-overlay">
                <div class="video-rule-overlay-title">行为识别规则</div>
                <div v-if="behaviorRulePreviewList.length" class="video-rule-list">
                  <div
                    v-for="rule in behaviorRulePreviewList"
                    :key="rule.id"
                    class="video-rule-chip"
                    :class="[`video-rule-chip--${rule.geometryType || 'region'}`]"
                  >
                    <span class="video-rule-chip-type">{{ getBehaviorTypeLabel(rule.behaviorType) }}</span>
                    <span class="video-rule-chip-text">{{ getBehaviorRulePreviewText(rule) }}</span>
                  </div>
                </div>
                <div v-else class="video-rule-overlay-empty">暂无监控规则</div>
              </div>
              <div class="video-event-overlay">
                <div class="video-event-overlay-title">最近事件</div>
                <div v-if="recentDetectEvents.length" class="video-event-list">
                  <div
                    v-for="item in recentDetectEvents"
                    :key="item.key"
                    class="video-event-item"
                  >
                    <div class="video-event-item-header">
                      <span
                        class="video-event-state"
                        :class="[`video-event-state--${item.eventState || 'active'}`]"
                      >{{ item.eventStateLabel }}</span>
                      <span class="video-event-time">{{ item.timestampText }}</span>
                    </div>
                    <div class="video-event-item-text">{{ item.summary }}</div>
                  </div>
                </div>
                <div v-else class="video-event-overlay-empty">{{ eventOverlayEmptyText }}</div>
              </div>

          </div>
        </div>
      </section>

      <section class="config-pane">
        <el-form ref="deploymentForm" :model="form" :rules="rules" label-width="110px" size="small" class="config-form">
          <el-tabs v-model="workspaceTab" class="config-tabs">
            <el-tab-pane label="任务与算法" name="task">
            <el-form-item label="任务名称" prop="taskName">
              <el-input v-model="form.taskName" placeholder="请输入任务名称" maxlength="64" show-word-limit />
            </el-form-item>

            <el-form-item label="选择设备" prop="deviceId">
              <el-select
                v-model="form.deviceId"
                placeholder="请选择设备"
                filterable
                clearable
                @change="handleDeviceChange"
              >
                <el-option
                  v-for="item in deviceOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="算法配置" prop="algorithmTasks">
              <div class="algorithm-task-list">
                <div
                  v-for="(task, index) in form.algorithmTasks"
                  :key="task.uid"
                  class="algorithm-task-item"
                >
                  <div class="algorithm-task-header">
                    <span class="algorithm-task-title">算法 {{ index + 1 }}</span>
                    <el-button
                      type="text"
                      size="mini"
                      :disabled="form.algorithmTasks.length <= 1"
                      @click="handleRemoveAlgorithmTask(index)"
                    >删除</el-button>
                  </div>
                  <el-row :gutter="8">
                    <el-col :span="12">
                      <el-select
                        v-model="task.algorithmCode"
                        placeholder="请选择算法"
                        filterable
                        clearable
                        @change="value => handleAlgorithmChange(index, value)"
                      >
                        <el-option
                          v-for="item in algorithmOptions"
                          :key="item.code"
                          :label="item.name"
                          :value="item.code"
                        />
                      </el-select>
                    </el-col>
                    <el-col :span="12">
                      <el-select
                        v-model="task.targetCodes"
                        placeholder="请选择检测目标"
                        multiple
                        collapse-tags
                        clearable
                      >
                        <el-option
                          v-for="item in task.targetOptions"
                          :key="item.value"
                          :label="item.label"
                          :value="item.value"
                        />
                      </el-select>
                    </el-col>
                  </el-row>
                  <el-row :gutter="8" class="algorithm-task-params-row">
                    <el-col :xs="24" :sm="8">
                      <div class="algorithm-task-param-label">
                        <span>抽帧率</span>
                        <el-tooltip content="检测帧率默认 8，填 0 表示不抽帧，也就是每帧都推理。" placement="top">
                          <i class="el-icon-question algorithm-task-param-icon" />
                        </el-tooltip>
                      </div>
                      <el-input-number
                        v-model="task.detectFps"
                        :min="0"
                        :max="30"
                        :step="1"
                        :precision="0"
                        controls-position="right"
                        class="algorithm-number-input"
                      />
                    </el-col>
                    <el-col :xs="24" :sm="8">
                      <div class="algorithm-task-param-label">
                        <span>置信度</span>
                        <el-tooltip content="置信度阈值范围 0-1，当前默认值会按所选算法自动带出。" placement="top">
                          <i class="el-icon-question algorithm-task-param-icon" />
                        </el-tooltip>
                      </div>
                      <el-input-number
                        v-if="task.algorithmCode"
                        v-model="task.scoreThreshold"
                        :min="0"
                        :max="1"
                        :step="0.05"
                        :precision="2"
                        controls-position="right"
                        class="algorithm-number-input"
                      />
                      <el-input v-else disabled placeholder="选择算法后自动带出" />
                    </el-col>
                    <el-col :xs="24" :sm="8">
                      <div class="algorithm-task-param-label">
                        <span>NMS</span>
                        <el-tooltip content="NMS 阈值范围 0-1，当前默认值会按所选算法自动带出。" placement="top">
                          <i class="el-icon-question algorithm-task-param-icon" />
                        </el-tooltip>
                      </div>
                      <el-input-number
                        v-if="task.algorithmCode"
                        v-model="task.nmsThreshold"
                        :min="0"
                        :max="1"
                        :step="0.05"
                        :precision="2"
                        controls-position="right"
                        class="algorithm-number-input"
                      />
                      <el-input v-else disabled placeholder="选择算法后自动带出" />
                    </el-col>
                  </el-row>
                </div>
              </div>
              <el-button size="mini" type="primary" plain icon="el-icon-plus" @click="handleAddAlgorithmTask">添加算法</el-button>
            </el-form-item>

            </el-tab-pane>
            <el-tab-pane label="行为规则" name="rules">
            <el-form-item label-width="0" class="behavior-rule-form-item">
              <div class="config-block-title">行为规则</div>
              <behavior-rule-panel :host="behaviorRulePanelHost" />
            </el-form-item>

            </el-tab-pane>
            <el-tab-pane label="推流与其它" name="more">
            <el-form-item label="是否推流" prop="pushEnabled">
              <el-radio-group v-model="form.pushEnabled">
                <el-radio :label="true">是</el-radio>
                <el-radio :label="false">否</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="!form.pushEnabled" label="前端画框" prop="frontendOverlayEnabled">
              <el-radio-group v-model="form.frontendOverlayEnabled">
                <el-radio :label="true">是</el-radio>
                <el-radio :label="false">否</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="录像引擎" prop="recordEngine">
              <el-radio-group v-model="form.recordEngine">
                <el-radio label="A-SERVER">算法服务器</el-radio>
                <el-radio label="M-SERVER">媒体服务器</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="报警间隔(秒)" prop="alarmIntervalSec">
              <el-input-number v-model="form.alarmIntervalSec" :min="1" :step="1" :precision="0" controls-position="right" />
            </el-form-item>

            <el-form-item label="启用AI复核" prop="aiReviewEnabled">
              <el-radio-group v-model="form.aiReviewEnabled">
                <el-radio :label="true">是</el-radio>
                <el-radio :label="false">否</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="form.aiReviewEnabled" label="AI复核提示词" prop="aiReviewPrompt">
              <el-input
                v-model="form.aiReviewPrompt"
                type="textarea"
                :rows="4"
                placeholder="请输入 AI 复核补充要求，例如：如果有人在打架，请确认告警"
                maxlength="2000"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                :rows="4"
                placeholder="请输入备注"
                maxlength="255"
                show-word-limit
              />
            </el-form-item>

            </el-tab-pane>
          </el-tabs>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script>
import { getDeviceList, previewDeviceMonitor } from '@/api/device'
import { getAlgorithmList, getAlgorithmTargets } from '@/api/algorithm'
import { createDeployment, getDeploymentDetail, updateDeployment, updateDeploymentLiveOutput } from '@/api/deployment'
import { OVERLAY_DELAY_DEFAULT_MS, loadOverlayDelayMs } from '@/utils/systemRuntimeConfig'
import { BEHAVIOR_TYPE_OPTIONS, getBehaviorTypeLabel, normalizeBehaviorType } from '@/utils/behaviorTypes'
import { getFieldValue } from '@/utils/fieldMap'
import {
  buildPrimaryRegion,
  clamp01,
  createEmptyGeometryConfig,
  createLineConfig as buildLineConfig,
  createRegionConfig as buildRegionConfig,
  drawCanvasCrossLineDirectionIndicator,
  drawCanvasLineArrow,
  drawCanvasTextLabel,
  getCrossLineDirectionButtonText,
  getLineDirectionLabel,
  getNextLineDirection,
  normalizeLineDirection,
  normalizePoint,
  normalizePointList,
  normalizeRegionPrimaryState,
  parseGeometryConfigInput
} from '@/utils/geometryEditor'
import VideoPreviewPane from './components/VideoPreviewPane.vue'
import GeometryToolbar from './components/GeometryToolbar.vue'
import BehaviorRulePanel from './components/BehaviorRulePanel.vue'

export default {
  name: 'DeploymentAdd',
  components: { VideoPreviewPane, GeometryToolbar, BehaviorRulePanel },
  data() {
    const validateAlgorithmTasks = (rule, value, callback) => {
      if (!Array.isArray(value) || value.length === 0) {
        callback(new Error('请至少添加一个算法'))
        return
      }
      const seen = new Set()
      for (let i = 0; i < value.length; i += 1) {
        const item = value[i] || {}
        if (!item.algorithmCode) {
          callback(new Error(`请选择第 ${i + 1} 个算法`))
          return
        }
        if (!Array.isArray(item.targetCodes) || !item.targetCodes.length) {
          callback(new Error(`请选择第 ${i + 1} 个算法的检测目标`))
          return
        }
        if (seen.has(item.algorithmCode)) {
          callback(new Error('同一个布控任务内不允许重复选择算法'))
          return
        }
        seen.add(item.algorithmCode)
      }
      callback()
    }

    return {
      deviceOptions: [],
      algorithmOptions: [],
      algorithmTaskSeed: 2,
      regionSeed: 1,
      lineSeed: 1,
      behaviorRuleSeed: 1,
      form: {
        taskName: '',
        deviceId: '',
        runtimeStatus: '',
        algorithmTasks: [
          {
            uid: 1,
            algorithmCode: '',
            algorithmName: '',
            detectFps: 8,
            targetCodes: [],
            targetOptions: []
          }
        ],
        pushEnabled: true,
        frontendOverlayEnabled: true,
        recordEngine: 'M-SERVER',
        alarmIntervalSec: 180,
        aiReviewEnabled: false,
        aiReviewPrompt: '',
        remark: '',
        geometryConfig: {
          regions: [],
          lines: [],
          behaviorRules: []
        }
      },
      initialFormSnapshot: '',
      rules: {
        taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
        deviceId: [{ required: true, message: '请选择设备', trigger: 'change' }],
        algorithmTasks: [{ validator: validateAlgorithmTasks, trigger: 'change' }]
      },
      saveLoading: false,
      deploymentId: '',
      streamUrl: '',
      videoLoaded: false,
      polygonPoints: [],
      polygonClosed: false,
      geometryEditorMode: 'region',
      activeRegionId: '',
      activeLineId: '',
      detectFrame: null,
      detectFrameClearTimer: null,
      detectFrameRenderTimer: null,
      pendingDetectFrame: null,
      overlayDelayMs: OVERLAY_DELAY_DEFAULT_MS,
      recentDetectEvents: [],
      workspaceTab: 'task',
      activeRuleId: '',
      activeSequenceId: ''
    }
  },
  computed: {
    behaviorRulePanelHost() {
      return this
    },
    saveButtonText() {
      return this.deploymentId ? '保存更新' : '保存并创建'
    },
    geometryRegionCount() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      return Array.isArray(geometryConfig.regions) ? geometryConfig.regions.length : 0
    },
    geometryLineCount() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      return Array.isArray(geometryConfig.lines) ? geometryConfig.lines.length : 0
    },
    regionOptions() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      return (geometryConfig.regions || []).map((region, index) => ({
        value: region.id,
        label: `${region.name || `区域${index + 1}`}${region.primary ? ' (主区域)' : ''} (${Array.isArray(region.points) ? region.points.length : 0}点)`
      }))
    },
    lineOptions() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      return (geometryConfig.lines || []).map((line, index) => ({
        value: line.id,
        label: `${line.name || `线段${index + 1}`} (${Array.isArray(line.points) ? line.points.length : 0}/2)`
      }))
    },
    lineDirectionOptions() {
      return [
        { value: 'both', label: '双向' },
        { value: 'left_to_right', label: '左到右' },
        { value: 'right_to_left', label: '右到左' }
      ]
    },
    sequenceLogicModeOptions() {
      return [
        { value: 'all', label: '阶段内全部命中' },
        { value: 'any', label: '阶段内任一命中' }
      ]
    },
    outputModeOptions() {
      return [
        { value: 'direct_alarm', label: '直接告警' },
        { value: 'condition_only', label: '仅产出事件' }
      ]
    },
    behaviorObjectOptions() {
      const values = []
      const seen = new Set()
      const pushValue = value => {
        const normalized = String(value || '').trim().toLowerCase()
        if (!normalized || seen.has(normalized)) {
          return
        }
        seen.add(normalized)
        values.push({ value: normalized, label: normalized })
      }

      (this.form.algorithmTasks || []).forEach(task => {
        ((task && task.targetCodes) || []).forEach(pushValue)
      })

      return values
    },
    behaviorTypeOptions() {
      return BEHAVIOR_TYPE_OPTIONS
    },
    sequenceBehaviorTypeOptions() {
      return this.behaviorTypeOptions.filter(item => this.isSequenceCapableBehaviorType(item.value))
    },
    behaviorRuleList() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      return Array.isArray(geometryConfig.behaviorRules) ? geometryConfig.behaviorRules : []
    },
    standaloneBehaviorRules() {
      return this.behaviorRuleList.filter(rule => {
        const sequenceId = this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
        return !sequenceId
      })
    },
    sequenceRuleGroups() {
      const grouped = this.behaviorRuleList.reduce((result, rule, sourceIndex) => {
        const sequenceId = this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
        if (!sequenceId) {
          return result
        }
        if (!result[sequenceId]) {
          result[sequenceId] = {
            sequenceId,
            sourceIndex,
            rules: []
          }
        }
        result[sequenceId].rules.push({
          ...rule,
          __sourceIndex: sourceIndex
        })
        return result
      }, {})

      return Object.values(grouped)
        .map(group => ({
          sequenceId: group.sequenceId,
          sourceIndex: group.sourceIndex,
          rules: group.rules
            .slice()
            .sort((left, right) => {
              const leftStage = Number.isFinite(Number(left.stageIndex)) ? Number(left.stageIndex) : 0
              const rightStage = Number.isFinite(Number(right.stageIndex)) ? Number(right.stageIndex) : 0
              if (leftStage !== rightStage) {
                return leftStage - rightStage
              }
              return left.__sourceIndex - right.__sourceIndex
            })
            .map(rule => {
              const nextRule = { ...rule }
              delete nextRule.__sourceIndex
              return nextRule
            })
        }))
        .sort((left, right) => {
          if (left.sourceIndex !== right.sourceIndex) {
            return left.sourceIndex - right.sourceIndex
          }
          return String(left.sequenceId).localeCompare(String(right.sequenceId), 'zh-Hans-CN')
        })
    },
    sequenceGroupedRuleCount() {
      return this.sequenceRuleGroups.reduce((total, group) => total + group.rules.length, 0)
    },
    behaviorRulePreviewList() {
      return this.behaviorRuleList.filter(rule => Boolean(rule && rule.enabled))
    },
    hasConditionOnlyEventRule() {
      return this.behaviorRuleList.some(rule => this.normalizeBehaviorRuleOutputMode(rule && rule.outputMode) === 'condition_only')
    },
    eventOrchestrationEntryEnabled() {
      return this.hasConditionOnlyEventRule
    },
    eventOverlayEmptyText() {
      return this.form.pushEnabled ? '推流模式下等待行为事件推送' : '等待行为事件推送'
    },
    activeRuleLabel() {
      const rule = this.standaloneBehaviorRules.find(item => item.id === this.activeRuleId)
      return rule ? this.getBehaviorTypeLabel(rule.behaviorType) : ''
    },
    activeRegionIsPrimary() {
      const activeRegion = this.getActiveRegion()
      return Boolean(activeRegion && activeRegion.primary)
    },
    primaryRegionLabel() {
      const primaryRegion = this.getPrimaryRegion(this.form.geometryConfig)
      return primaryRegion ? (primaryRegion.name || '主区域') : '未设置'
    },
    activeLinePointCount() {
      const activeLine = this.getActiveLine()
      return activeLine && Array.isArray(activeLine.points) ? activeLine.points.length : 0
    },
    geometryEditorHint() {
      if (this.geometryEditorMode === 'line') {
        return this.activeLineId
          ? '线段模式：在画布上点击 2 个点生成线段'
          : '线段模式：请先新增并选择一条线段'
      }
      return this.activeRegionId
        ? (this.polygonClosed ? '当前区域已闭合，可切换线段模式或编辑其他区域' : '区域模式：点击画布加点，双击闭合当前区域')
        : '区域模式：可直接点击画布创建区域，或先新增区域后再绘制'
    }
  },
  watch: {
    'form.pushEnabled'(value) {
      if (value) {
        this.clearDetectFrame()
      }
    },
    'form.frontendOverlayEnabled'(value) {
      if (!value) {
        this.clearDetectFrame()
      }
    },
    geometryEditorMode() {
      this.drawPolygon()
    },
    behaviorRuleList: {
      handler() {
        this.ensureActiveRuleSelection()
      },
      immediate: true
    }
  },
  mounted() {
    this.initPageData()
    window.addEventListener('sva:detect-frame', this.handleDetectFramePush)
    window.addEventListener('sva:detect-event', this.handleDetectEventPush)
  },
  beforeDestroy() {
    window.removeEventListener('sva:detect-frame', this.handleDetectFramePush)
    window.removeEventListener('sva:detect-event', this.handleDetectEventPush)
    this.clearDetectFrame(false)
    this.destroyPlayer()
  },
  methods: {
    getFieldValue,
    normalizeBehaviorType,
    getBehaviorTypeLabel,
    clamp01,
    createEmptyGeometryConfig,
    parseGeometryConfigInput,
    normalizePoint,
    normalizePointList,
    buildPrimaryRegion,
    normalizeLineDirection,
    getLineDirectionLabel,
    getNextLineDirection,
    getCrossLineDirectionButtonText,
    normalizeRegionPrimaryState,
    drawCanvasTextLabel,
    drawCanvasLineArrow,
    drawCanvasCrossLineDirectionIndicator,
    async initPageData() {
      try {
        this.deploymentId = this.resolveDeploymentIdFromRoute()
        await Promise.all([this.loadDeviceOptions(), this.loadAlgorithmOptions(), this.loadOverlayDelayConfig()])
        if (this.deploymentId) {
          await this.loadDeploymentDetail(this.deploymentId)
        }
        this.syncInitialSnapshot()
        this.$nextTick(() => {
          this.syncCanvasSize()
        })
      } catch (error) {
        this.$message.error('页面初始化失败，请稍后重试')
      }
    },

    async loadDeviceOptions() {
      const response = await getDeviceList({ pageNum: 1, pageSize: 1000 })
      const rows = (response && response.rows) || []
      this.deviceOptions = rows
        .map(item => {
          const apeId = item.apeId || item.ape_id || item.deviceId || ''
          const name = item.name || item.deviceName || item.device_name || apeId
          if (!apeId) return null
          const deviceType = String(item.device_type || item.deviceType || 'rtsp').toLowerCase()
          const typeLabel = deviceType === 'gb28181' ? '国标 GB28181' : '直连 RTSP'
          return {
            value: apeId,
            label: `${name} (${apeId}) · ${typeLabel}`,
            raw: item
          }
        })
        .filter(Boolean)
    },
    async loadOverlayDelayConfig() {
      this.overlayDelayMs = await loadOverlayDelayMs(this.overlayDelayMs)
    },

    resolveDeploymentIdFromRoute() {
      const route = this.$route || {}
      const query = route.query || {}
      const params = route.params || {}
      return query.deploymentId || query.id || params.deploymentId || params.id || ''
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

    extractPreviewUrl(response) {
      if (!response) {
        return ''
      }
      const data = response.data || response
      return data.playUrl || data.previewUrl || data.url || data.streamUrl || data.rtspUrl || data.flvUrl || data.directSourceUrl || data.direct_source_url || data.liveUrl || data.live_url || ''
    },

    async loadDeploymentDetail(deploymentId) {
      if (!deploymentId) {
        return
      }
      try {
        const response = await getDeploymentDetail(deploymentId)
        const detail = (response && response.data) || {}

        this.deploymentId = this.getFieldValue(detail, 'deploymentId', 'deployment_id') || deploymentId

        const deviceId = this.getFieldValue(detail, 'deviceId', 'device_id') || ''
        this.form.taskName = this.getFieldValue(detail, 'taskName', 'task_name') || ''
        this.form.deviceId = deviceId
        this.form.pushEnabled = this.toBoolean(this.getFieldValue(detail, 'pushEnabled', 'push_enabled'), true)
        this.form.frontendOverlayEnabled = this.form.pushEnabled
          ? false
          : this.toBoolean(this.getFieldValue(detail, 'frontendOverlayEnabled', 'frontend_overlay_enabled'), true)
        this.form.recordEngine = this.getFieldValue(detail, 'recordEngine', 'record_engine') || 'M-SERVER'
        const alarmIntervalSec = Number(this.getFieldValue(detail, 'alarmIntervalSec', 'alarm_interval_sec'))
        this.form.alarmIntervalSec = Number.isFinite(alarmIntervalSec) && alarmIntervalSec > 0 ? alarmIntervalSec : 180
        this.form.aiReviewEnabled = this.toBoolean(this.getFieldValue(detail, 'aiReviewEnabled', 'ai_review_enabled'), false)
        this.form.aiReviewPrompt = this.getFieldValue(detail, 'aiReviewPrompt', 'ai_review_prompt') || ''
        this.form.remark = this.getFieldValue(detail, 'remark') || ''

        const requestTasks = Array.isArray(detail.algorithmTasks) ? detail.algorithmTasks : []
        this.form.algorithmTasks = requestTasks
          .map(item => this.createAlgorithmTask({
            algorithmCode: item.algorithmCode || item.algorithm_code || '',
            algorithmName: item.algorithmName || item.algorithm_name || '',
            detectFps: this.normalizeDetectFpsValue(item.detectFps ?? item.detect_fps),
            scoreThreshold: this.normalizeThresholdValue(item.scoreThreshold ?? item.score_threshold),
            nmsThreshold: this.normalizeThresholdValue(item.nmsThreshold ?? item.nms_threshold),
            targetCodes: this.normalizeTaskTargetCodes(item.targetCodes || item.target_codes),
            targetOptions: []
          }))
          .filter(item => item.algorithmCode || item.targetCodes.length)

        if (!this.form.algorithmTasks.length) {
          this.form.algorithmTasks = [this.createAlgorithmTask()]
        }

        await Promise.all(this.form.algorithmTasks.map(task => this.loadTargetOptionsForTask(task, task.algorithmCode, task.targetCodes)))

        const geometryConfig = this.getFieldValue(detail, 'geometryConfig', 'geometry_config')
        const normalizedGeometryConfig = this.normalizeGeometryConfig(geometryConfig)
        this.form.geometryConfig = normalizedGeometryConfig
        const primaryRegion = this.getPrimaryRegion(normalizedGeometryConfig)
        this.activeRegionId = primaryRegion ? primaryRegion.id : ''
        this.syncGeometryEditorState()
        this.drawPolygon()

        const status = String(this.getFieldValue(detail, 'status') || '').toUpperCase()
        const isRunning = status === 'RUNNING'
        if (isRunning) {
          let algorithmStreamUrl = ''
          try {
            const liveOutputResponse = await updateDeploymentLiveOutput(this.deploymentId, {
              videoEnabled: true,
              liveEventEnabled: true,
              wsEventFps: 8
            })
            const liveOutputData = (liveOutputResponse && liveOutputResponse.data) || liveOutputResponse || {}
            algorithmStreamUrl = this.getFieldValue(liveOutputData, 'algorithmStreamUrl', 'algorithm_stream_url') || ''
          } catch (error) {
            algorithmStreamUrl = ''
          }
          if (algorithmStreamUrl) {
            this.streamUrl = algorithmStreamUrl
            this.playStream(algorithmStreamUrl)
            return
          }
        }

        if (deviceId) {
          await this.handleDeviceChange(deviceId)
        }
      } catch (error) {
        this.$message.error('获取布控详情失败，请稍后重试')
      }
    },

    async loadAlgorithmOptions() {
      const response = await getAlgorithmList()
      const rows = (response && response.rows) || []
      this.algorithmOptions = rows
        .map(item => {
          const code = item.algorithmCode || item.algorithm_code || item.code || ''
          const name = item.algorithmName || item.algorithm_name || item.name || code
          if (!code) return null
          return {
            code,
            name,
            raw: item
          }
        })
        .filter(Boolean)
      await this.ensureAlgorithmTasksReady()
    },

    async handleDeviceChange(apeId) {
      this.clearDetectFrame()
      if (!apeId) {
        this.streamUrl = ''
        this.destroyPlayer()
        return
      }
      try {
        const response = await previewDeviceMonitor(apeId)
        const streamUrl = this.extractPreviewUrl(response)
        this.streamUrl = streamUrl
        this.playStream(streamUrl)
        if (!streamUrl) {
          this.$message.warning('未获取到实时流地址')
        }
      } catch (error) {
        this.streamUrl = ''
        this.destroyPlayer()
        this.$message.error('获取实时流地址失败')
      }
    },

    getDefaultForm() {
      return {
        taskName: '',
        deviceId: '',
        runtimeStatus: '',
        algorithmTasks: [this.createAlgorithmTask()],
        pushEnabled: true,
        frontendOverlayEnabled: true,
        recordEngine: 'M-SERVER',
        alarmIntervalSec: 180,
        aiReviewEnabled: false,
        aiReviewPrompt: '',
        remark: '',
        geometryConfig: this.createEmptyGeometryConfig()
      }
    },

    normalizeBehaviorRule(rule, index = 0) {
      return this.normalizeBehaviorRuleWithGeometry(rule, index, this.createEmptyGeometryConfig())
    },

    isDirectionBehaviorType(behaviorType) {
      return ['direction_move', 'direction_reverse'].includes(behaviorType)
    },

    isRelationalBehaviorType(behaviorType) {
      return ['relation_near', 'relation_apart', 'relation_not_contains'].includes(behaviorType)
    },

    isSequenceCapableBehaviorType(behaviorType) {
      return ['cross_line', 'enter_region', 'exit_region', 'dwell', 'low_speed', 'loitering', 'sleep', 'direction_move', 'direction_reverse', 'relation_near', 'relation_apart', 'relation_not_contains'].includes(behaviorType)
    },

    isSpecifiedRegionRuleTargetValue(value) {
      return String(value || '').trim().toLowerCase() === 'specified_region'
    },

    isSpecifiedRegionRule(rule) {
      return Boolean(rule && this.isSpecifiedRegionRuleTargetValue(rule.ruleObjectCode))
    },

    isLineBehaviorType(behaviorType) {
      return behaviorType === 'cross_line'
    },

    isBehaviorRuleSequenceIdVisible(behaviorType) {
      return this.isSequenceCapableBehaviorType(behaviorType)
    },

    isBehaviorRuleSequenceConfigVisible(rule) {
      return Boolean(rule && this.isSequenceCapableBehaviorType(rule.behaviorType) && this.normalizeBehaviorRuleSequenceId(rule.sequenceId))
    },

    isBehaviorRuleDirectionVisible(behaviorType) {
      return behaviorType === 'cross_line'
    },

    isBehaviorRuleDirectionAngleVisible(behaviorType) {
      return this.isDirectionBehaviorType(behaviorType)
    },

    isBehaviorRuleDirectionToleranceVisible(behaviorType) {
      return this.isDirectionBehaviorType(behaviorType)
    },

    isBehaviorRuleDirectionLineVisible(behaviorType) {
      return this.isDirectionBehaviorType(behaviorType)
    },

    isBehaviorRuleDirectionAngleLocked(rule) {
      return Boolean(rule && this.isDirectionBehaviorType(rule.behaviorType) && rule.directionLineId)
    },

    isBehaviorRuleObjectVisible(behaviorType) {
      return Boolean(behaviorType) && !this.isRelationalBehaviorType(behaviorType)
    },

    getBehaviorTypeOptionsForRule(rule) {
      if (this.isSpecifiedRegionRule(rule)) {
        return this.behaviorTypeOptions.filter(item => item.value === 'region_motion')
      }
      return this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
        ? this.sequenceBehaviorTypeOptions
        : this.behaviorTypeOptions
    },

    getBehaviorRuleObjectOptions(rule) {
      if (!this.isBehaviorRuleObjectVisible(rule && rule.behaviorType)) {
        return this.behaviorObjectOptions
      }
      return [
        ...this.behaviorObjectOptions,
        { value: 'specified_region', label: '指定区域' }
      ]
    },

    canUpgradeBehaviorRuleToSequence(rule) {
      return Boolean(rule && !this.normalizeBehaviorRuleSequenceId(rule.sequenceId) && this.isSequenceCapableBehaviorType(rule.behaviorType))
    },

    createInternalSequenceId() {
      return `sequence_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
    },

    getSequenceGroupLeadRule(sequenceId) {
      const normalizedSequenceId = this.normalizeBehaviorRuleSequenceId(sequenceId)
      if (!normalizedSequenceId) {
        return null
      }
      const group = this.sequenceRuleGroups.find(item => item.sequenceId === normalizedSequenceId)
      return group && group.rules.length ? group.rules[0] : null
    },

    isSequenceLeadRule(rule) {
      const sequenceId = this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
      if (!sequenceId) {
        return false
      }
      const leadRule = this.getSequenceGroupLeadRule(sequenceId)
      return Boolean(leadRule && leadRule.id === rule.id)
    },

    isSequenceStageLeadRule(rule) {
      const sequenceId = this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
      if (!sequenceId) {
        return false
      }
      const targetStageIndex = Number.isFinite(Number(rule && rule.stageIndex)) ? Number(rule.stageIndex) : 0
      const group = this.sequenceRuleGroups.find(item => item.sequenceId === sequenceId)
      if (!group || !Array.isArray(group.rules)) {
        return false
      }
      const firstRuleInStage = group.rules.find(item => {
        const stageIndex = Number.isFinite(Number(item && item.stageIndex)) ? Number(item.stageIndex) : 0
        return stageIndex === targetStageIndex
      })
      return Boolean(firstRuleInStage && firstRuleInStage.id === rule.id)
    },

    shouldShowSequenceRuleObjectField(rule) {
      if (!this.isBehaviorRuleObjectVisible(rule && rule.behaviorType)) {
        return false
      }
      const sequenceId = this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
      if (!sequenceId) {
        return true
      }
      const stageIndex = Number.isFinite(Number(rule && rule.stageIndex)) ? Number(rule.stageIndex) : 0
      return stageIndex === 0
    },

    shouldShowSequenceSubjectObjectField(rule) {
      if (!this.isBehaviorRuleSubjectObjectVisible(rule && rule.behaviorType)) {
        return false
      }
      const sequenceId = this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
      if (!sequenceId) {
        return true
      }
      const stageIndex = Number.isFinite(Number(rule && rule.stageIndex)) ? Number(rule.stageIndex) : 0
      return stageIndex === 0
    },

    shouldShowSequenceStageLogicField(rule) {
      if (!this.isBehaviorRuleSequenceConfigVisible(rule)) {
        return false
      }
      return this.isSequenceStageLeadRule(rule)
    },

    getBehaviorRuleEffectiveSubjectObject(rule) {
      if (!rule) {
        return ''
      }
      if (this.isRelationalBehaviorType(rule.behaviorType)) {
        return this.normalizeBehaviorRuleObjectValue(rule.subjectObject)
      }
      if (this.isBehaviorRuleObjectVisible(rule.behaviorType)) {
        return this.normalizeBehaviorRuleRuleObjectCode(rule.behaviorType, rule.ruleObjectCode)
      }
      return ''
    },

    getSequenceGroupSubjectObject(sequenceId) {
      const normalizedSequenceId = this.normalizeBehaviorRuleSequenceId(sequenceId)
      if (!normalizedSequenceId) {
        return ''
      }
      const leadRule = this.getSequenceGroupLeadRule(normalizedSequenceId)
      return this.getBehaviorRuleEffectiveSubjectObject(leadRule)
    },

    getSequenceGroupSubjectLabel(group) {
      const subjectObject = this.getSequenceGroupSubjectObject(group && group.sequenceId)
      return subjectObject || '未设置'
    },

    getSequenceGroupSubjectLabelByRule(rule) {
      return this.getSequenceGroupSubjectObject(rule && rule.sequenceId)
    },

    getSequenceGroupToneClass(groupIndex) {
      const toneIndex = Number.isFinite(groupIndex) ? groupIndex % 4 : 0
      return `behavior-sequence-group--tone-${toneIndex + 1}`
    },

    getSequenceStageToneClass(rule) {
      const stageIndex = Number.isFinite(rule && rule.stageIndex) ? rule.stageIndex : 0
      return `behavior-rule-item--stage-${(stageIndex % 4) + 1}`
    },

    isBehaviorRuleSubjectObjectVisible(behaviorType) {
      return this.isRelationalBehaviorType(behaviorType)
    },

    isBehaviorRuleTargetObjectVisible(behaviorType) {
      return this.isRelationalBehaviorType(behaviorType)
    },

    applySequenceSubjectObjectToRule(rule, subjectObject) {
      const normalizedSubjectObject = this.normalizeBehaviorRuleObjectValue(subjectObject)
      if (this.isRelationalBehaviorType(rule && rule.behaviorType)) {
        return {
          ...rule,
          subjectObject: normalizedSubjectObject
        }
      }
      if (this.isBehaviorRuleObjectVisible(rule && rule.behaviorType)) {
        return {
          ...rule,
          ruleObjectCode: this.normalizeBehaviorRuleRuleObjectCode(rule.behaviorType, normalizedSubjectObject)
        }
      }
      return { ...rule }
    },

    isBehaviorRuleDistanceVisible(behaviorType) {
      return ['relation_near', 'relation_apart', 'region_motion', 'sleep', 'sleep_on_duty'].includes(behaviorType)
    },

    isBehaviorRuleThresholdVisible(behaviorType) {
      return ['dwell', 'absence', 'occupancy', 'region_motion', 'low_speed', 'loitering', 'sleep', 'sleep_on_duty', 'count_threshold', 'direction_move', 'direction_reverse', 'relation_near', 'relation_apart', 'relation_not_contains'].includes(behaviorType)
    },

    isBehaviorRuleThresholdCountVisible(behaviorType) {
      return behaviorType === 'count_threshold'
    },

    isBehaviorRuleMaxSpeedVisible(behaviorType) {
      return behaviorType === 'low_speed' || behaviorType === 'sleep'
    },

    isBehaviorRuleMaxDisplacementVisible(behaviorType) {
      return behaviorType === 'loitering' || behaviorType === 'sleep'
    },

    getBehaviorRuleDistanceFieldLabel(behaviorType) {
      if (behaviorType === 'region_motion') {
        return '运动阈值(%)'
      }
      if (behaviorType === 'sleep_on_duty') {
        return '低头角度(°)'
      }
      if (behaviorType === 'sleep') {
        return '最小宽高比'
      }
      return '距离阈值(px)'
    },

    getBehaviorRuleDistanceInputConfig(behaviorType) {
      if (behaviorType === 'region_motion') {
        return {
          min: 1,
          max: 100,
          step: 1,
          precision: 0
        }
      }
      if (behaviorType === 'sleep_on_duty') {
        return {
          min: 1,
          max: 135,
          step: 1,
          precision: 0
        }
      }
      if (behaviorType === 'sleep') {
        return {
          min: 0.5,
          max: 8,
          step: 0.1,
          precision: 1
        }
      }
      return {
        min: 1,
        max: 10000,
        step: 1,
        precision: 0
      }
    },

    getBehaviorRuleThresholdMin(behaviorType) {
      if (behaviorType === 'low_speed') {
        return 200
      }
      if (behaviorType === 'loitering') {
        return 1000
      }
      if (behaviorType === 'sleep_on_duty') {
        return 1000
      }
      if (behaviorType === 'sleep') {
        return 1000
      }
      if (behaviorType === 'count_threshold') {
        return 0
      }
      if (this.isBehaviorRuleThresholdVisible(behaviorType)) {
        return 1
      }
      return 0
    },

    getBehaviorRuleDefaultState(behaviorType) {
      const defaultRuleObjectCode = this.getDefaultBehaviorRuleObjectCode()
      if (behaviorType === 'cross_line') {
        return {
          ruleObjectCode: defaultRuleObjectCode,
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: 0,
          thresholdCount: 0,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          directionAngleDeg: 0,
          directionToleranceDeg: 30,
          directionLineId: ''
        }
      }
      if (behaviorType === 'region_motion') {
        return {
          ruleObjectCode: 'specified_region',
          outputMode: 'condition_only',
          direction: 'both',
          thresholdMs: 3000,
          thresholdCount: 0,
          distanceThresholdPx: 12,
          maxSpeedPxPerSec: 0,
          maxDisplacementPx: 0,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          directionAngleDeg: 0,
          directionToleranceDeg: 30,
          directionLineId: '',
          subjectObject: '',
          targetObject: ''
        }
      }
      if (behaviorType === 'low_speed') {
        return {
          ruleObjectCode: defaultRuleObjectCode,
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: 3000,
          thresholdCount: 0,
          distanceThresholdPx: 0,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          maxSpeedPxPerSec: 12,
          maxDisplacementPx: 0,
          directionAngleDeg: 0,
          directionToleranceDeg: 30,
          directionLineId: ''
        }
      }
      if (behaviorType === 'loitering') {
        return {
          ruleObjectCode: defaultRuleObjectCode,
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: 10000,
          thresholdCount: 0,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          maxSpeedPxPerSec: 0,
          maxDisplacementPx: 80
        }
      }
      if (behaviorType === 'sleep_on_duty') {
        return {
          ruleObjectCode: defaultRuleObjectCode,
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: 2500,
          thresholdCount: 0,
          distanceThresholdPx: 32,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          maxSpeedPxPerSec: 0,
          maxDisplacementPx: 0,
          directionAngleDeg: 0,
          directionToleranceDeg: 10,
          directionLineId: '',
          customEventName: '睡岗'
        }
      }
      if (behaviorType === 'sleep') {
        return {
          ruleObjectCode: defaultRuleObjectCode,
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: 15000,
          thresholdCount: 0,
          distanceThresholdPx: 1.2,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          maxSpeedPxPerSec: 6,
          maxDisplacementPx: 48,
          directionAngleDeg: 0,
          directionToleranceDeg: 30,
          directionLineId: ''
        }
      }
      if (behaviorType === 'count_threshold') {
        return {
          ruleObjectCode: defaultRuleObjectCode,
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: 0,
          thresholdCount: 1,
          distanceThresholdPx: 0,
          maxSpeedPxPerSec: 0,
          maxDisplacementPx: 0,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          directionAngleDeg: 0,
          directionToleranceDeg: 30,
          directionLineId: ''
        }
      }
      if (this.isDirectionBehaviorType(behaviorType)) {
        return {
          ruleObjectCode: defaultRuleObjectCode,
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: 3000,
          thresholdCount: 0,
          maxSpeedPxPerSec: 0,
          maxDisplacementPx: 0,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          directionAngleDeg: 0,
          directionToleranceDeg: 30,
          directionLineId: ''
        }
      }
      if (this.isRelationalBehaviorType(behaviorType)) {
        return {
          outputMode: 'direct_alarm',
          direction: 'both',
          thresholdMs: behaviorType === 'relation_not_contains' ? 2000 : 3000,
          thresholdCount: 0,
          distanceThresholdPx: behaviorType === 'relation_not_contains' ? 0 : 80,
          maxSpeedPxPerSec: 0,
          maxDisplacementPx: 0,
          sequenceId: '',
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all',
          directionAngleDeg: 0,
          directionToleranceDeg: 30,
          directionLineId: '',
          subjectObject: defaultRuleObjectCode,
          targetObject: defaultRuleObjectCode
        }
      }
      return {
        ruleObjectCode: defaultRuleObjectCode,
        outputMode: 'direct_alarm',
        direction: 'both',
        thresholdMs: 0,
        thresholdCount: 0,
        distanceThresholdPx: 0,
        maxSpeedPxPerSec: 0,
        maxDisplacementPx: 0,
        sequenceId: '',
        stageIndex: 0,
        stageTimeoutMs: 0,
        stageHoldMs: 0,
        logicMode: 'all',
        directionAngleDeg: 0,
        directionToleranceDeg: 30,
        directionLineId: '',
        subjectObject: '',
        targetObject: ''
      }
    },

    getDefaultBehaviorRuleObjectCode() {
      return this.behaviorObjectOptions.length ? this.behaviorObjectOptions[0].value : ''
    },

    normalizeBehaviorRuleObjectValue(value) {
      return String(value || '').trim().toLowerCase()
    },

    normalizeBehaviorRuleRuleObjectCode(behaviorType, value) {
      if (!this.isBehaviorRuleObjectVisible(behaviorType)) {
        return ''
      }
      if (this.isSpecifiedRegionRuleTargetValue(value)) {
        return 'specified_region'
      }
      const normalized = this.normalizeBehaviorRuleObjectValue(value)
      return normalized || this.getDefaultBehaviorRuleObjectCode()
    },

    normalizeBehaviorRuleSequenceId(value) {
      return String(value || '').trim()
    },

    normalizeBehaviorRuleLogicMode(value) {
      return value === 'any' ? 'any' : 'all'
    },

    normalizeBehaviorRuleOutputMode(value) {
      return value === 'condition_only' ? 'condition_only' : 'direct_alarm'
    },

    normalizeBehaviorRuleStageIndex(behaviorType, sequenceId, value) {
      if (!this.isSequenceCapableBehaviorType(behaviorType) || !this.normalizeBehaviorRuleSequenceId(sequenceId)) {
        return 0
      }
      const numericValue = Number(value)
      const nextValue = Number.isFinite(numericValue) ? Math.round(numericValue) : 0
      return Math.max(0, Math.min(32, nextValue))
    },

    normalizeBehaviorRuleStageTimeout(behaviorType, sequenceId, value) {
      if (!this.isSequenceCapableBehaviorType(behaviorType) || !this.normalizeBehaviorRuleSequenceId(sequenceId)) {
        return 0
      }
      const numericValue = Number(value)
      const nextValue = Number.isFinite(numericValue) ? Math.round(numericValue) : 0
      return Math.max(0, Math.min(3600000, nextValue))
    },

    normalizeBehaviorRuleStageHold(behaviorType, sequenceId, value) {
      if (!this.isSequenceCapableBehaviorType(behaviorType) || !this.normalizeBehaviorRuleSequenceId(sequenceId)) {
        return 0
      }
      const numericValue = Number(value)
      const nextValue = Number.isFinite(numericValue) ? Math.round(numericValue) : 0
      return Math.max(0, Math.min(3600000, nextValue))
    },

    normalizeBehaviorRuleDirectionLineId(behaviorType, value, geometryConfig = this.createEmptyGeometryConfig()) {
      if (!this.isDirectionBehaviorType(behaviorType)) {
        return ''
      }
      const nextValue = String(value || '').trim()
      if (!nextValue) {
        return ''
      }
      return (geometryConfig.lines || []).some(line => line.id === nextValue) ? nextValue : ''
    },

    computeDirectionAngleFromLine(line) {
      const points = this.normalizePointList(line && line.points, 0).slice(0, 2)
      if (points.length < 2) {
        return null
      }
      const dx = Number(points[1].x) - Number(points[0].x)
      const dy = Number(points[1].y) - Number(points[0].y)
      if (Math.abs(dx) < 0.000001 && Math.abs(dy) < 0.000001) {
        return null
      }
      const angleDeg = Math.atan2(dy, dx) * 180 / Math.PI
      return this.normalizeBehaviorRuleDirectionAngle('direction_move', angleDeg)
    },

    normalizeBehaviorRuleDirectionAngle(behaviorType, value) {
      if (!this.isBehaviorRuleDirectionAngleVisible(behaviorType)) {
        return 0
      }
      const numericValue = Number(value)
      const nextValue = Number.isFinite(numericValue) ? Math.round(numericValue) : 0
      const normalized = ((nextValue % 360) + 360) % 360
      return normalized
    },

    normalizeBehaviorRuleDirectionTolerance(behaviorType, value) {
      if (behaviorType === 'sleep_on_duty') {
        const numericValue = Number(value)
        const nextValue = Number.isFinite(numericValue) ? Math.round(numericValue) : 10
        return Math.max(1, Math.min(180, nextValue))
      }
      if (!this.isBehaviorRuleDirectionToleranceVisible(behaviorType)) {
        return 30
      }
      const numericValue = Number(value)
      const nextValue = Number.isFinite(numericValue) ? Math.round(numericValue) : 30
      return Math.max(1, Math.min(180, nextValue))
    },

    normalizeBehaviorRuleThresholdMs(behaviorType, value) {
      const defaults = this.getBehaviorRuleDefaultState(behaviorType)
      const numericValue = Number(value)
      if (behaviorType === 'count_threshold') {
        if (!Number.isFinite(numericValue)) {
          return defaults.thresholdMs
        }
        return Math.max(0, Math.min(3600000, Math.round(numericValue)))
      }
      if (!this.isBehaviorRuleThresholdVisible(behaviorType)) {
        return 0
      }
      const fallback = defaults.thresholdMs
      const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? Math.round(numericValue) : fallback
      const minValue = this.getBehaviorRuleThresholdMin(behaviorType)
      return Math.max(minValue, Math.min(3600000, nextValue))
    },

    normalizeBehaviorRuleThresholdCount(behaviorType, value) {
      if (!this.isBehaviorRuleThresholdCountVisible(behaviorType)) {
        return 0
      }
      const numericValue = Number(value)
      const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? Math.round(numericValue) : 1
      return Math.max(1, Math.min(100000, nextValue))
    },

    normalizeBehaviorRuleMaxSpeed(behaviorType, value) {
      if (!this.isBehaviorRuleMaxSpeedVisible(behaviorType)) {
        return 0
      }
      const numericValue = Number(value)
      const fallback = behaviorType === 'sleep' ? 6 : 12
      const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? numericValue : fallback
      return Math.max(0.1, Math.min(10000, Number(nextValue.toFixed(1))))
    },

    normalizeBehaviorRuleMaxDisplacement(behaviorType, value) {
      if (!this.isBehaviorRuleMaxDisplacementVisible(behaviorType)) {
        return 0
      }
      const numericValue = Number(value)
      const fallback = behaviorType === 'sleep' ? 48 : 80
      const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? Math.round(numericValue) : fallback
      return Math.max(1, Math.min(10000, nextValue))
    },

    normalizeBehaviorRuleDistance(behaviorType, value) {
      if (!this.isBehaviorRuleDistanceVisible(behaviorType)) {
        return 0
      }
      const numericValue = Number(value)
      if (behaviorType === 'region_motion') {
        const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? Math.round(numericValue) : 12
        return Math.max(1, Math.min(100, nextValue))
      }
      if (behaviorType === 'sleep_on_duty') {
        const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? Math.round(numericValue) : 32
        return Math.max(1, Math.min(135, nextValue))
      }
      if (behaviorType === 'sleep') {
        const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? numericValue : 1.2
        return Math.max(0.5, Math.min(8, Number(nextValue.toFixed(1))))
      }
      const nextValue = Number.isFinite(numericValue) && numericValue > 0 ? Math.round(numericValue) : 80
      return Math.max(1, Math.min(10000, nextValue))
    },

    getRegionOptionsFromGeometry(geometryConfig) {
      return (geometryConfig.regions || []).map((region, index) => ({
        value: region.id,
        label: `${region.name || `区域${index + 1}`}${region.primary ? ' (主区域)' : ''}`
      }))
    },

    getPrimaryRegionOptionFromGeometry(geometryConfig) {
      const primaryRegion = (geometryConfig.regions || []).find(region => region.primary) || geometryConfig.regions[0] || null
      if (!primaryRegion) {
        return null
      }
      return {
        value: primaryRegion.id,
        label: `${primaryRegion.name || '主区域'} (主区域)`
      }
    },

    normalizeBehaviorRuleWithGeometry(rule, index = 0, geometryConfig = this.createEmptyGeometryConfig()) {
      const rawBehaviorType = this.normalizeBehaviorType(String((rule && (rule.behaviorType || rule.type)) || '').trim())
      const specifiedRegionMode = this.isSpecifiedRegionRuleTargetValue(rule && rule.ruleObjectCode)
      const behaviorType = specifiedRegionMode ? 'region_motion' : rawBehaviorType
      if (!behaviorType) {
        return null
      }
      const geometryType = this.isLineBehaviorType(behaviorType) ? 'line' : 'region'
      const direction = this.normalizeLineDirection(rule && (rule.direction || rule.crossingDirection))
      const thresholdMs = Number(rule && rule.thresholdMs)
      const thresholdCount = Number(rule && (rule.thresholdCount !== undefined ? rule.thresholdCount : rule.countThreshold))
      const maxSpeedPxPerSec = Number(rule && (rule.maxSpeedPxPerSec !== undefined ? rule.maxSpeedPxPerSec : rule.maxSpeed))
      const maxDisplacementPx = Number(rule && (rule.maxDisplacementPx !== undefined ? rule.maxDisplacementPx : (rule.loiteringRadiusPx !== undefined ? rule.loiteringRadiusPx : rule.radiusPx)))
      const distanceThresholdPx = Number(rule && (rule.distanceThresholdPx !== undefined ? rule.distanceThresholdPx : (rule.distancePx !== undefined ? rule.distancePx : rule.distanceThreshold)))
      const directionAngleDeg = Number(rule && (rule.directionAngleDeg !== undefined ? rule.directionAngleDeg : rule.directionAngle))
      const directionToleranceDeg = Number(rule && (rule.directionToleranceDeg !== undefined ? rule.directionToleranceDeg : rule.angleToleranceDeg))
      const requestedDirectionLineId = String((rule && (rule.directionLineId || rule.direction_line_id || rule.referenceLineId)) || '').trim()
      const ruleObjectCode = this.normalizeBehaviorRuleRuleObjectCode(
        behaviorType,
        rule && (rule.ruleObjectCode || rule.rule_object_code || rule.objectCode || rule.objectClass)
      )
      const subjectObject = this.normalizeBehaviorRuleObjectValue(rule && (rule.subjectObject || rule.subjectClass))
      const targetObject = this.normalizeBehaviorRuleObjectValue(rule && (rule.targetObject || rule.targetClass))
      const outputMode = this.normalizeBehaviorRuleOutputMode(rule && (rule.outputMode || rule.output_mode))
      const sequenceId = specifiedRegionMode ? '' : this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
      const logicMode = this.normalizeBehaviorRuleLogicMode(rule && rule.logicMode)
      const regionOptions = this.getRegionOptionsFromGeometry(geometryConfig)
      const lineOptions = (geometryConfig.lines || []).map((line, lineIndex) => ({
        value: line.id,
        label: line.name || `线段${lineIndex + 1}`
      }))
      const requestedGeometryId = rule && rule.geometryId ? String(rule.geometryId) : ''
      const geometryId = geometryType === 'line'
        ? ((lineOptions.some(item => item.value === requestedGeometryId) ? requestedGeometryId : (lineOptions[0] ? lineOptions[0].value : '')))
        : ((regionOptions.some(item => item.value === requestedGeometryId) ? requestedGeometryId : (regionOptions[0] ? regionOptions[0].value : '')))
      const directionLineId = this.normalizeBehaviorRuleDirectionLineId(behaviorType, requestedDirectionLineId, geometryConfig)
      const directionLine = directionLineId ? (geometryConfig.lines || []).find(line => line.id === directionLineId) : null
      const derivedDirectionAngleDeg = this.computeDirectionAngleFromLine(directionLine)
      return {
        id: rule && rule.id ? String(rule.id) : `behavior_rule_${index + 1}`,
        name: rule && rule.name ? String(rule.name) : `${behaviorType}_${index + 1}`,
        behaviorType,
        customEventName: this.normalizeBehaviorRuleCustomEventName(rule && (rule.customEventName || rule.custom_event_name || rule.alarmTypeName || rule.businessEventName)),
        outputMode: specifiedRegionMode ? 'condition_only' : outputMode,
        enabled: rule && rule.enabled !== undefined ? Boolean(rule.enabled) : true,
        geometryType,
        geometryId,
        direction: behaviorType === 'cross_line' ? direction : 'both',
        thresholdMs: this.normalizeBehaviorRuleThresholdMs(behaviorType, thresholdMs),
        thresholdCount: this.normalizeBehaviorRuleThresholdCount(behaviorType, thresholdCount),
        distanceThresholdPx: this.normalizeBehaviorRuleDistance(behaviorType, distanceThresholdPx),
        maxSpeedPxPerSec: this.normalizeBehaviorRuleMaxSpeed(behaviorType, maxSpeedPxPerSec),
        maxDisplacementPx: this.normalizeBehaviorRuleMaxDisplacement(behaviorType, maxDisplacementPx),
        sequenceId: this.isSequenceCapableBehaviorType(behaviorType) ? sequenceId : '',
        stageIndex: this.normalizeBehaviorRuleStageIndex(behaviorType, sequenceId, rule && rule.stageIndex),
        stageTimeoutMs: this.normalizeBehaviorRuleStageTimeout(behaviorType, sequenceId, rule && rule.stageTimeoutMs),
        stageHoldMs: this.normalizeBehaviorRuleStageHold(behaviorType, sequenceId, rule && rule.stageHoldMs),
        logicMode: this.isSequenceCapableBehaviorType(behaviorType) && sequenceId ? logicMode : 'all',
        directionAngleDeg: this.normalizeBehaviorRuleDirectionAngle(behaviorType, derivedDirectionAngleDeg !== null ? derivedDirectionAngleDeg : directionAngleDeg),
        directionToleranceDeg: this.normalizeBehaviorRuleDirectionTolerance(behaviorType, directionToleranceDeg),
        directionLineId,
        ruleObjectCode: specifiedRegionMode ? 'specified_region' : ruleObjectCode,
        subjectObject: specifiedRegionMode ? '' : subjectObject,
        targetObject: specifiedRegionMode ? '' : targetObject
      }
    },

    normalizeBehaviorRulesInGeometry(geometryConfig) {
      return {
        ...geometryConfig,
        behaviorRules: (geometryConfig.behaviorRules || [])
          .map((rule, index) => this.normalizeBehaviorRuleWithGeometry(rule, index, geometryConfig))
          .filter(Boolean)
      }
    },

    createRegionConfig(overrides = {}) {
      const config = buildRegionConfig(this.regionSeed, overrides)
      this.regionSeed += 1
      return config
    },

    createLineConfig(overrides = {}) {
      const config = buildLineConfig(this.lineSeed, overrides)
      this.lineSeed += 1
      return config
    },

    createBehaviorRule(overrides = {}, geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)) {
      const nextIndex = this.behaviorRuleSeed
      this.behaviorRuleSeed += 1
      const hasLine = Array.isArray(geometryConfig.lines) && geometryConfig.lines.length > 0
      const behaviorType = this.normalizeBehaviorType(overrides.behaviorType || (hasLine ? 'cross_line' : 'dwell')) || 'dwell'
      const defaultState = this.getBehaviorRuleDefaultState(behaviorType)
      const rule = {
        id: overrides.id || `behavior_rule_${nextIndex}`,
        name: overrides.name || `规则${nextIndex}`,
        behaviorType,
        customEventName: overrides.customEventName !== undefined ? overrides.customEventName : defaultState.customEventName,
        outputMode: overrides.outputMode !== undefined ? this.normalizeBehaviorRuleOutputMode(overrides.outputMode) : this.normalizeBehaviorRuleOutputMode(defaultState.outputMode),
        enabled: overrides.enabled !== undefined ? Boolean(overrides.enabled) : true,
        geometryType: this.isLineBehaviorType(behaviorType) ? 'line' : 'region',
        geometryId: overrides.geometryId || '',
        direction: overrides.direction || defaultState.direction,
        thresholdMs: overrides.thresholdMs !== undefined ? overrides.thresholdMs : defaultState.thresholdMs,
        thresholdCount: overrides.thresholdCount !== undefined ? overrides.thresholdCount : defaultState.thresholdCount,
        distanceThresholdPx: overrides.distanceThresholdPx !== undefined ? overrides.distanceThresholdPx : defaultState.distanceThresholdPx,
        maxSpeedPxPerSec: overrides.maxSpeedPxPerSec !== undefined ? overrides.maxSpeedPxPerSec : defaultState.maxSpeedPxPerSec,
        maxDisplacementPx: overrides.maxDisplacementPx !== undefined ? overrides.maxDisplacementPx : defaultState.maxDisplacementPx,
        sequenceId: overrides.sequenceId !== undefined ? overrides.sequenceId : defaultState.sequenceId,
        stageIndex: overrides.stageIndex !== undefined ? overrides.stageIndex : defaultState.stageIndex,
        stageTimeoutMs: overrides.stageTimeoutMs !== undefined ? overrides.stageTimeoutMs : defaultState.stageTimeoutMs,
        stageHoldMs: overrides.stageHoldMs !== undefined ? overrides.stageHoldMs : defaultState.stageHoldMs,
        logicMode: overrides.logicMode !== undefined ? overrides.logicMode : defaultState.logicMode,
        directionAngleDeg: overrides.directionAngleDeg !== undefined ? overrides.directionAngleDeg : defaultState.directionAngleDeg,
        directionToleranceDeg: overrides.directionToleranceDeg !== undefined ? overrides.directionToleranceDeg : defaultState.directionToleranceDeg,
        directionLineId: overrides.directionLineId !== undefined ? overrides.directionLineId : defaultState.directionLineId,
        ruleObjectCode: overrides.ruleObjectCode !== undefined ? overrides.ruleObjectCode : defaultState.ruleObjectCode,
        subjectObject: overrides.subjectObject !== undefined ? overrides.subjectObject : defaultState.subjectObject,
        targetObject: overrides.targetObject !== undefined ? overrides.targetObject : defaultState.targetObject,
        ...overrides
      }
      return this.normalizeBehaviorRuleWithGeometry(rule, nextIndex - 1, geometryConfig)
    },

    normalizeBehaviorRuleCustomEventName(value) {
      return String(value || '').trim()
    },

    getBehaviorRuleGeometryOptions(rule) {
      if (this.isLineBehaviorType(rule && rule.behaviorType)) {
        return this.lineOptions
      }
      return this.regionOptions
    },

    getBehaviorRuleGeometryPlaceholder(rule) {
      if (this.isSpecifiedRegionRule(rule)) {
        return '指定区域模式必须绑定区域'
      }
      return this.isLineBehaviorType(rule && rule.behaviorType) ? '请选择线段' : '请先绘制区域'
    },

    formatBehaviorRuleNumber(value, fractionDigits = 1) {
      const numericValue = Number(value)
      if (!Number.isFinite(numericValue)) {
        return '0'
      }
      if (Math.abs(numericValue - Math.round(numericValue)) < 0.0001) {
        return String(Math.round(numericValue))
      }
      return numericValue.toFixed(fractionDigits)
    },

    formatBehaviorRuleDuration(thresholdMs) {
      const numericValue = Number(thresholdMs)
      if (!Number.isFinite(numericValue) || numericValue < 0) {
        return '0ms'
      }
      if (numericValue >= 1000 && numericValue % 1000 === 0) {
        return `${Math.round(numericValue / 1000)}s`
      }
      return `${Math.round(numericValue)}ms`
    },

    getBehaviorRuleMetricTexts(rule) {
      if (!rule) {
        return []
      }
      const parts = []
      if (this.isBehaviorRuleObjectVisible(rule.behaviorType) && rule.ruleObjectCode) {
        parts.push(`目标 ${this.isSpecifiedRegionRule(rule) ? '指定区域' : rule.ruleObjectCode}`)
      }
      if (rule.behaviorType === 'cross_line') {
        parts.push(`穿越 ${this.getLineDirectionLabel(rule.direction)}`)
      }
      if (this.isDirectionBehaviorType(rule.behaviorType)) {
        parts.push(`方向 ${this.formatBehaviorRuleNumber(rule.directionAngleDeg, 0)}°`)
        parts.push(`容差 ±${this.formatBehaviorRuleNumber(rule.directionToleranceDeg, 0)}°`)
        if (rule.directionLineId) {
          const matchedLine = this.lineOptions.find(item => item.value === rule.directionLineId)
          if (matchedLine) {
            parts.push(`参考 ${matchedLine.label}`)
          }
        }
      }
      if (this.isRelationalBehaviorType(rule.behaviorType)) {
        if (rule.subjectObject) {
          parts.push(`主体 ${rule.subjectObject}`)
        }
        if (rule.targetObject) {
          parts.push(`目标 ${rule.targetObject}`)
        }
        if (rule.behaviorType === 'relation_not_contains') {
          parts.push('目标中心点未落入主体框')
        } else {
          parts.push(`${rule.behaviorType === 'relation_apart' ? '距离 >=' : '距离 <='} ${this.formatBehaviorRuleNumber(rule.distanceThresholdPx, 0)} px`)
        }
      }
      if (rule.behaviorType === 'region_motion') {
        parts.push(`运动阈值 >= ${this.formatBehaviorRuleNumber(rule.distanceThresholdPx, 0)}%`)
      }
      if (rule.behaviorType === 'sleep_on_duty') {
        parts.push(`低头角 >= ${this.formatBehaviorRuleNumber(rule.distanceThresholdPx, 0)}°`)
      }
      if (rule.behaviorType === 'sleep') {
        parts.push(`宽高比 >= ${this.formatBehaviorRuleNumber(rule.distanceThresholdPx)}`)
      }
      if (this.isBehaviorRuleSequenceConfigVisible(rule)) {
        parts.push(`阶段 ${this.formatBehaviorRuleNumber(rule.stageIndex + 1, 0)}`)
        parts.push(rule.logicMode === 'any' ? '阶段任一命中' : '阶段全部命中')
        if (Number(rule.stageTimeoutMs) > 0) {
          parts.push(`阶段超时 ${this.formatBehaviorRuleDuration(rule.stageTimeoutMs)}`)
        }
        if (Number(rule.stageHoldMs) > 0) {
          parts.push(`阶段保持 ${this.formatBehaviorRuleDuration(rule.stageHoldMs)}`)
        }
      }
      if (this.isBehaviorRuleThresholdVisible(rule.behaviorType) && (rule.behaviorType !== 'count_threshold' || Number(rule.thresholdMs) > 0)) {
        parts.push(`时长 ${this.formatBehaviorRuleDuration(rule.thresholdMs)}`)
      }
      if (this.isBehaviorRuleThresholdCountVisible(rule.behaviorType)) {
        parts.push(`数量 >= ${this.formatBehaviorRuleNumber(rule.thresholdCount, 0)}`)
      }
      if (this.isBehaviorRuleMaxSpeedVisible(rule.behaviorType)) {
        parts.push(`速度 <= ${this.formatBehaviorRuleNumber(rule.maxSpeedPxPerSec)} px/s`)
      }
      if (this.isBehaviorRuleMaxDisplacementVisible(rule.behaviorType)) {
        parts.push(`位移 <= ${this.formatBehaviorRuleNumber(rule.maxDisplacementPx, 0)} px`)
      }
      return parts
    },

    getBehaviorRuleSummarySpan(rule) {
      let inputCount = 0
      if (this.isBehaviorRuleObjectVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleDirectionVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleSubjectObjectVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleTargetObjectVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleDistanceVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleSequenceIdVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleSequenceConfigVisible(rule)) {
        inputCount += 4
      }
      if (this.isBehaviorRuleDirectionAngleVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleDirectionToleranceVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleDirectionLineVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleThresholdVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleThresholdCountVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleMaxSpeedVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      if (this.isBehaviorRuleMaxDisplacementVisible(rule && rule.behaviorType)) {
        inputCount += 1
      }
      return Math.max(8, 24 - inputCount * 8)
    },

    getBehaviorRuleSummary(rule) {
      if (!rule) {
        return ''
      }
      const geometryOptions = this.getBehaviorRuleGeometryOptions(rule)
      const geometryLabel = (geometryOptions.find(item => item.value === rule.geometryId) || {}).label || '未绑定几何'
      const metrics = this.getBehaviorRuleMetricTexts(rule)
      if (!metrics.length) {
        return `${this.getBehaviorTypeLabel(rule.behaviorType)}: ${geometryLabel}`
      }
      return `${this.getBehaviorTypeLabel(rule.behaviorType)}: ${geometryLabel} / ${metrics.join(' / ')}`
    },

    getBehaviorRulePreviewText(rule) {
      if (!rule) {
        return ''
      }
      const geometryOptions = this.getBehaviorRuleGeometryOptions(rule)
      const geometryLabel = (geometryOptions.find(item => item.value === rule.geometryId) || {}).label || '未绑定几何'
      const metrics = this.getBehaviorRuleMetricTexts(rule)
      return [geometryLabel].concat(metrics).join(' / ')
    },

    getBehaviorRuleHeaderTitle(rule) {
      if (!rule) {
        return ''
      }

      const parts = []
      const sequenceId = this.normalizeBehaviorRuleSequenceId(rule.sequenceId)

      if (sequenceId && this.isSequenceCapableBehaviorType(rule.behaviorType)) {
        const stageIndex = Number.isFinite(Number(rule.stageIndex)) ? Number(rule.stageIndex) : 0
        parts.push(`第 ${stageIndex + 1} 阶段`)
      }

      parts.push(this.getBehaviorTypeLabel(rule.behaviorType))

      const geometryOptions = this.getBehaviorRuleGeometryOptions(rule)
      const geometryLabel = (geometryOptions.find(item => item.value === rule.geometryId) || {}).label
      if (geometryLabel) {
        parts.push(geometryLabel)
      }

      return parts.join(' ')
    },

    getBehaviorRuleDefaultAlarmTypeName(rule) {
      if (!rule) {
        return ''
      }
      const behaviorType = String(rule.behaviorType || '').trim()
      if (behaviorType === 'cross_line') {
        return '跨线告警'
      }
      if (behaviorType === 'enter_region') {
        return '进区告警'
      }
      if (behaviorType === 'exit_region') {
        return '出区告警'
      }
      if (behaviorType === 'dwell') {
        return '停留告警'
      }
      if (behaviorType === 'low_speed') {
        return '低速告警'
      }
      if (behaviorType === 'loitering') {
        return '徘徊告警'
      }
      if (behaviorType === 'sleep_on_duty') {
        return '睡岗告警'
      }
      if (behaviorType === 'sleep') {
        return '睡觉告警'
      }
      if (behaviorType === 'absence') {
        return '离岗/缺席告警'
      }
      if (behaviorType === 'count_threshold') {
        return '数量阈值告警'
      }
      if (behaviorType === 'occupancy') {
        return '区域占用告警'
      }
      if (behaviorType === 'region_motion') {
        return '区域运动告警'
      }
      if (behaviorType === 'direction_move') {
        return '定向通行告警'
      }
      if (behaviorType === 'direction_reverse') {
        return '逆向通行告警'
      }
      if (behaviorType === 'relation_near') {
        return '目标接近告警'
      }
      if (behaviorType === 'relation_apart') {
        return '目标远离告警'
      }
      if (behaviorType === 'relation_not_contains') {
        return '目标未包含告警'
      }
      return this.getBehaviorTypeLabel(behaviorType)
    },

    getBehaviorRuleEffectiveAlarmTypeName(rule) {
      if (!rule) {
        return ''
      }
      return this.normalizeBehaviorRuleCustomEventName(rule.customEventName) || this.getBehaviorRuleDefaultAlarmTypeName(rule)
    },

    getBehaviorRuleEventNamePlaceholder(rule) {
      if (!rule) {
        return '请输入告警类型'
      }
      const defaultAlarmTypeName = this.getBehaviorRuleDefaultAlarmTypeName(rule)
      return defaultAlarmTypeName ? `留空则使用${defaultAlarmTypeName}` : '请输入自定义告警类型'
    },

    getBehaviorRuleDisplayTitle(rule) {
      if (!rule) {
        return ''
      }

      const parts = []
      const sequenceId = this.normalizeBehaviorRuleSequenceId(rule.sequenceId)
      const customEventName = this.normalizeBehaviorRuleCustomEventName(rule.customEventName)

      if (sequenceId && this.isSequenceCapableBehaviorType(rule.behaviorType)) {
        const stageIndex = Number.isFinite(Number(rule.stageIndex)) ? Number(rule.stageIndex) : 0
        parts.push(`第 ${stageIndex + 1} 阶段`)
      }

      if (customEventName) {
        parts.push(customEventName)
      }

      parts.push(rule.outputMode === 'condition_only' ? '事件输出' : '直接告警')

      parts.push(this.getBehaviorTypeLabel(rule.behaviorType))

      const geometryOptions = this.getBehaviorRuleGeometryOptions(rule)
      const geometryLabel = (geometryOptions.find(item => item.value === rule.geometryId) || {}).label
      if (geometryLabel) {
        parts.push(geometryLabel)
      }

      return parts.join(' · ')
    },

    getBehaviorRuleSequenceGroupSummary(group) {
      if (!group || !Array.isArray(group.rules) || !group.rules.length) {
        return '暂无规则'
      }

      const stageList = Array.from(new Set(
        group.rules.map(rule => {
          const stageIndex = Number(rule && rule.stageIndex)
          return Number.isFinite(stageIndex) ? stageIndex + 1 : 1
        })
      )).sort((left, right) => left - right)

      const enabledCount = group.rules.filter(rule => Boolean(rule && rule.enabled)).length
      const logicModes = Array.from(new Set(
        group.rules.map(rule => this.normalizeBehaviorRuleLogicMode(rule && rule.logicMode))
      ))
      const customEventName = this.getSequenceGroupCustomEventName(group)

      const parts = [
        `${group.rules.length} 条规则`,
        `${stageList.length} 个阶段`,
        `阶段 ${stageList.join(' / ')}`,
        `启用 ${enabledCount} 条`
      ]

      if (customEventName) {
        parts.unshift(`告警类型 ${customEventName}`)
      }

      const outputMode = this.getSequenceGroupOutputMode(group)
      parts.unshift(outputMode === 'condition_only' ? '输出 事件' : '输出 直接告警')

      if (logicModes.length === 1) {
        parts.push(logicModes[0] === 'any' ? '阶段逻辑 任一命中' : '阶段逻辑 全部命中')
      }

      return parts.join(' / ')
    },

    getBehaviorRuleShortLabel(rule) {
      if (!rule) {
        return ''
      }
      if (rule.behaviorType === 'cross_line') {
        if (rule.direction === 'left_to_right') {
          return '跨线→'
        }
        if (rule.direction === 'right_to_left') {
          return '跨线←'
        }
        return '跨线'
      }
      if (rule.behaviorType === 'dwell') {
        return `停留${this.formatBehaviorRuleDuration(rule.thresholdMs)}`
      }
      if (rule.behaviorType === 'enter_region') {
        return '进区'
      }
      if (rule.behaviorType === 'exit_region') {
        return '出区'
      }
      if (rule.behaviorType === 'low_speed') {
        return `低速${this.formatBehaviorRuleNumber(rule.maxSpeedPxPerSec)}px/s`
      }
      if (rule.behaviorType === 'loitering') {
        return `徘徊${this.formatBehaviorRuleDuration(rule.thresholdMs)}`
      }
      if (rule.behaviorType === 'sleep_on_duty') {
        return `睡岗${this.formatBehaviorRuleDuration(rule.thresholdMs)}`
      }
      if (rule.behaviorType === 'sleep') {
        return `睡觉${this.formatBehaviorRuleDuration(rule.thresholdMs)}`
      }
      if (rule.behaviorType === 'absence') {
        return `缺席${this.formatBehaviorRuleDuration(rule.thresholdMs)}`
      }
      if (rule.behaviorType === 'count_threshold') {
        return `数量>=${this.formatBehaviorRuleNumber(rule.thresholdCount, 0)}`
      }
      if (rule.behaviorType === 'occupancy') {
        return `占用${this.formatBehaviorRuleDuration(rule.thresholdMs)}`
      }
      if (rule.behaviorType === 'region_motion') {
        return `区域运动>=${this.formatBehaviorRuleNumber(rule.distanceThresholdPx, 0)}%`
      }
      if (rule.behaviorType === 'direction_move') {
        return `定向${this.formatBehaviorRuleNumber(rule.directionAngleDeg, 0)}°`
      }
      if (rule.behaviorType === 'direction_reverse') {
        return `逆向${this.formatBehaviorRuleNumber(rule.directionAngleDeg, 0)}°`
      }
      if (rule.behaviorType === 'relation_near') {
        return '接近'
      }
      if (rule.behaviorType === 'relation_apart') {
        return '远离'
      }
      if (rule.behaviorType === 'relation_not_contains') {
        return '未包含'
      }
      return this.getBehaviorTypeLabel(rule.behaviorType)
    },

    getGeometryRuleTagTexts(geometryType, geometryId, limit = 2) {
      if (!geometryType || !geometryId) {
        return []
      }
      const matchedRules = this.behaviorRuleList
        .filter(rule => Boolean(rule && rule.enabled))
        .filter(rule => rule.geometryType === geometryType && rule.geometryId === geometryId)
      if (!matchedRules.length) {
        return []
      }
      const labels = matchedRules.slice(0, limit).map(rule => this.getBehaviorRuleShortLabel(rule)).filter(Boolean)
      if (matchedRules.length > limit) {
        labels.push('...')
      }
      return labels
    },

    getCrossLineDirectionsForLine(lineId) {
      if (!lineId) {
        return []
      }
      const directionSet = new Set()
      this.behaviorRuleList.forEach(rule => {
        if (!rule || !rule.enabled || rule.behaviorType !== 'cross_line' || rule.geometryId !== lineId) {
          return
        }
        const direction = this.normalizeLineDirection(rule.direction)
        if (direction === 'both') {
          directionSet.add('left_to_right')
          directionSet.add('right_to_left')
          return
        }
        directionSet.add(direction)
      })
      return Array.from(directionSet)
    },

    normalizeGeometryConfig(geometryConfig) {
      const normalized = this.createEmptyGeometryConfig()
      const source = this.parseGeometryConfigInput(geometryConfig)

      if (source && Array.isArray(source.regions)) {
        normalized.regions = source.regions
          .map((region, index) => {
            const points = this.normalizePointList(region && region.points, 0)
            const hasClosedFlag = region && (region.closed !== undefined && region.closed !== null)
            const hasLegacyClosedFlag = region && (region.isClosed !== undefined && region.isClosed !== null)
            return {
              id: region && region.id ? region.id : `region_${index + 1}`,
              name: region && region.name ? region.name : `区域${index + 1}`,
              type: region && region.type ? region.type : 'polygon',
              primary: Boolean(region && (region.primary || region.isPrimary)),
              closed: hasClosedFlag
                ? Boolean(region.closed)
                : (hasLegacyClosedFlag ? Boolean(region.isClosed) : points.length >= 3),
              points
            }
          })
          .filter(Boolean)
      }

      if (source && Array.isArray(source.lines)) {
        normalized.lines = source.lines
          .map((line, index) => {
            const points = this.normalizePointList(line && line.points, 0).slice(0, 2)
            return {
              id: line && line.id ? line.id : `line_${index + 1}`,
              name: line && line.name ? line.name : `线段${index + 1}`,
              type: line && line.type ? line.type : 'tripwire',
              direction: this.normalizeLineDirection(line && (line.direction || line.crossingDirection)),
              points
            }
          })
          .filter(Boolean)
      }

      if (source && Array.isArray(source.behaviorRules)) {
        normalized.behaviorRules = source.behaviorRules
          .map((rule, index) => this.normalizeBehaviorRuleWithGeometry(rule, index, normalized))
          .filter(Boolean)
      }

      let primaryAssigned = false
      normalized.regions = normalized.regions.map((region, index) => {
        const nextRegion = { ...region }
        if (!primaryAssigned && (nextRegion.primary || index === 0)) {
          nextRegion.primary = true
          primaryAssigned = true
        } else {
          nextRegion.primary = false
        }
        return nextRegion
      })

      return this.normalizeBehaviorRulesInGeometry(normalized)
    },

    getPrimaryRegion(geometryConfig) {
      const normalized = this.normalizeGeometryConfig(geometryConfig, this.polygonPoints)
      return normalized.regions.find(region => region.primary) || normalized.regions[0] || null
    },

    getActiveRegion(geometryConfig = null) {
      const normalized = geometryConfig || this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      return (normalized.regions || []).find(region => region.id === this.activeRegionId) || null
    },

    getPrimaryRegionPoints(geometryConfig) {
      const primaryRegion = this.getPrimaryRegion(geometryConfig)
      return primaryRegion ? this.normalizePointList(primaryRegion.points, 0) : []
    },

    restoreActiveRegionCanvas(geometryConfig = null) {
      const normalized = geometryConfig || this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const activeRegion = this.getActiveRegion(normalized)
      this.polygonPoints = activeRegion ? this.normalizePointList(activeRegion.points, 0) : []
      this.polygonClosed = Boolean(activeRegion && activeRegion.closed)
    },

    syncGeometryConfigFromPolygon() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const polygonPoints = this.normalizePointList(this.polygonPoints, 0)
      const primaryRegionId = ((geometryConfig.regions || []).find(region => region.primary) || {}).id || ''
      let activeRegion = this.getActiveRegion(geometryConfig)
      if (!activeRegion && polygonPoints.length) {
        activeRegion = this.createRegionConfig({
          primary: !geometryConfig.regions.some(region => region.primary)
        })
        geometryConfig.regions = [...geometryConfig.regions, activeRegion]
        this.activeRegionId = activeRegion.id
      }

      if (activeRegion) {
        activeRegion = {
          ...activeRegion,
          closed: this.polygonClosed,
          points: polygonPoints
        }
        geometryConfig.regions = geometryConfig.regions.map(region => {
          if (region.id === activeRegion.id) {
            return activeRegion
          }
          return region
        })
      }

      geometryConfig.regions = this.normalizeRegionPrimaryState(geometryConfig.regions, primaryRegionId)

      this.form.geometryConfig = geometryConfig
      this.syncGeometryEditorState()
      return geometryConfig
    },

    buildPersistedGeometryConfig() {
      const geometryConfig = this.syncGeometryConfigFromPolygon()
      return {
        regions: (geometryConfig.regions || [])
          .map(region => ({
            ...region,
            closed: region.points.length >= 3 ? Boolean(region.closed) : false,
            points: this.normalizePointList(region.points, 3)
          }))
          .filter(region => region.points.length >= 3),
        lines: (geometryConfig.lines || [])
          .map(line => ({
            ...line,
            points: this.normalizePointList(line.points, 0).slice(0, 2)
          }))
          .filter(line => line.points.length >= 2),
        behaviorRules: (geometryConfig.behaviorRules || [])
          .map((rule, index) => this.normalizeBehaviorRuleWithGeometry(rule, index, geometryConfig))
          .filter(Boolean)
      }
    },

    syncGeometryEditorState() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      this.form.geometryConfig = geometryConfig
      const nextRegionSeed = (geometryConfig.regions || []).reduce((maxSeed, region, index) => {
        const regionId = String((region && region.id) || '')
        const match = regionId.match(/^region_(\d+)$/)
        const regionIndex = match ? Number(match[1]) : (regionId === 'region_primary' ? 1 : index + 1)
        return Math.max(maxSeed, Number.isFinite(regionIndex) ? regionIndex : index + 1)
      }, 0)
      this.regionSeed = Math.max(1, nextRegionSeed + 1)
      const activeRegionExists = geometryConfig.regions.some(region => region.id === this.activeRegionId)
      if (!activeRegionExists) {
        const primaryRegion = geometryConfig.regions.find(region => region.primary)
        this.activeRegionId = primaryRegion ? primaryRegion.id : (geometryConfig.regions[0] ? geometryConfig.regions[0].id : '')
      }
      const nextLineSeed = (geometryConfig.lines || []).reduce((maxSeed, line, index) => {
        const lineId = String((line && line.id) || '')
        const match = lineId.match(/^line_(\d+)$/)
        const lineIndex = match ? Number(match[1]) : index + 1
        return Math.max(maxSeed, Number.isFinite(lineIndex) ? lineIndex : index + 1)
      }, 0)
      this.lineSeed = Math.max(1, nextLineSeed + 1)
      const nextBehaviorRuleSeed = (geometryConfig.behaviorRules || []).reduce((maxSeed, rule, index) => {
        const ruleId = String((rule && rule.id) || '')
        const match = ruleId.match(/^behavior_rule_(\d+)$/)
        const ruleIndex = match ? Number(match[1]) : index + 1
        return Math.max(maxSeed, Number.isFinite(ruleIndex) ? ruleIndex : index + 1)
      }, 0)
      this.behaviorRuleSeed = Math.max(1, nextBehaviorRuleSeed + 1)
      const activeLineExists = geometryConfig.lines.some(line => line.id === this.activeLineId)
      if (!activeLineExists) {
        this.activeLineId = geometryConfig.lines.length ? geometryConfig.lines[0].id : ''
      }
      this.restoreActiveRegionCanvas(geometryConfig)
    },

    updateRegion(regionId, updater) {
      if (!regionId || typeof updater !== 'function') {
        return null
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const primaryRegionId = ((geometryConfig.regions || []).find(region => region.primary) || {}).id || ''
      let nextRegion = null
      geometryConfig.regions = geometryConfig.regions.map(region => {
        if (region.id !== regionId) {
          return region
        }
        nextRegion = updater({
          ...region,
          points: this.normalizePointList(region.points, 0)
        })
        return nextRegion
      })
      geometryConfig.regions = this.normalizeRegionPrimaryState(geometryConfig.regions, primaryRegionId)
      this.form.geometryConfig = geometryConfig
      this.syncGeometryEditorState()
      return nextRegion
    },

    ensureActiveRegion() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const primaryRegionId = ((geometryConfig.regions || []).find(region => region.primary) || {}).id || ''
      let activeRegion = this.getActiveRegion(geometryConfig)
      if (!activeRegion) {
        activeRegion = this.createRegionConfig({
          primary: !geometryConfig.regions.some(region => region.primary)
        })
        geometryConfig.regions = this.normalizeRegionPrimaryState([...geometryConfig.regions, activeRegion], primaryRegionId || activeRegion.id)
        this.form.geometryConfig = geometryConfig
        this.activeRegionId = activeRegion.id
      }
      this.syncGeometryEditorState()
      return this.getActiveRegion()
    },

    getActiveLine(geometryConfig = null) {
      const normalized = geometryConfig || this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      return (normalized.lines || []).find(line => line.id === this.activeLineId) || null
    },

    updateLine(lineId, updater) {
      if (!lineId || typeof updater !== 'function') {
        return null
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      let nextLine = null
      geometryConfig.lines = geometryConfig.lines.map(line => {
        if (line.id !== lineId) {
          return line
        }
        nextLine = updater({
          ...line,
          points: this.normalizePointList(line.points, 0).slice(0, 2)
        })
        return nextLine
      })
      this.form.geometryConfig = geometryConfig
      this.syncGeometryEditorState()
      return nextLine
    },

    updateBehaviorRule(ruleId, updater) {
      if (!ruleId || typeof updater !== 'function') {
        return null
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      let nextRule = null
      geometryConfig.behaviorRules = (geometryConfig.behaviorRules || []).map(rule => {
        if (rule.id !== ruleId) {
          return rule
        }
        nextRule = updater({ ...rule })
        return nextRule
      })
      this.form.geometryConfig = this.normalizeBehaviorRulesInGeometry(geometryConfig)
      this.syncGeometryEditorState()
      this.drawPolygon()
      return nextRule
    },

    applyBehaviorRulesWithGeometry(geometryConfig, behaviorRules) {
      geometryConfig.behaviorRules = this.normalizeSequenceRuleCollection(behaviorRules)
      this.form.geometryConfig = this.normalizeBehaviorRulesInGeometry(geometryConfig)
      this.syncGeometryEditorState()
      this.drawPolygon()
      return this.form.geometryConfig.behaviorRules || []
    },

    clearBehaviorRuleSequenceState(rule) {
      return {
        ...rule,
        sequenceId: '',
        stageIndex: 0,
        stageTimeoutMs: 0,
        stageHoldMs: 0,
        logicMode: 'all'
      }
    },

    normalizeSequenceRuleCollection(rules = []) {
      const nextRules = (rules || []).map((rule, index) => ({
        ...rule,
        __sourceIndex: index
      }))
      const groupedRules = {}
      nextRules.forEach(rule => {
        const sequenceId = this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId)
        if (!sequenceId) {
          return
        }
        if (!groupedRules[sequenceId]) {
          groupedRules[sequenceId] = []
        }
        groupedRules[sequenceId].push(rule)
      })

      Object.keys(groupedRules).forEach(sequenceId => {
        const groupRules = groupedRules[sequenceId]
        const allSequenceCapable = groupRules.every(rule => this.isSequenceCapableBehaviorType(rule.behaviorType))
        if (!allSequenceCapable) {
          groupRules.forEach(rule => Object.assign(rule, this.clearBehaviorRuleSequenceState(rule)))
          return
        }

        groupRules.sort((left, right) => {
          const leftStage = Number.isFinite(Number(left.stageIndex)) ? Number(left.stageIndex) : 0
          const rightStage = Number.isFinite(Number(right.stageIndex)) ? Number(right.stageIndex) : 0
          if (leftStage !== rightStage) {
            return leftStage - rightStage
          }
          return left.__sourceIndex - right.__sourceIndex
        })

        if (groupRules.length <= 1) {
          groupRules.forEach(rule => Object.assign(rule, this.clearBehaviorRuleSequenceState(rule)))
          return
        }

        const leadRule = groupRules[0]
        const inheritedSubjectObject = this.getBehaviorRuleEffectiveSubjectObject(leadRule) || this.getDefaultBehaviorRuleObjectCode()
        const compactStageIndexMap = new Map()
        let nextStageIndex = 0
        groupRules.forEach(rule => {
          const rawStageIndex = this.normalizeBehaviorRuleStageIndex(rule.behaviorType, sequenceId, rule.stageIndex)
          if (!compactStageIndexMap.has(rawStageIndex)) {
            compactStageIndexMap.set(rawStageIndex, nextStageIndex)
            nextStageIndex += 1
          }
          const normalizedStageIndex = compactStageIndexMap.get(rawStageIndex)
          rule.sequenceId = sequenceId
          rule.stageIndex = normalizedStageIndex
          rule.logicMode = this.normalizeBehaviorRuleLogicMode(rule.logicMode)
          rule.stageTimeoutMs = this.normalizeBehaviorRuleStageTimeout(rule.behaviorType, sequenceId, rule.stageTimeoutMs)
          rule.stageHoldMs = this.normalizeBehaviorRuleStageHold(rule.behaviorType, sequenceId, rule.stageHoldMs)
          if (this.isRelationalBehaviorType(rule.behaviorType)) {
            rule.subjectObject = this.normalizeBehaviorRuleObjectValue(inheritedSubjectObject)
          } else if (this.isBehaviorRuleObjectVisible(rule.behaviorType)) {
            rule.ruleObjectCode = this.normalizeBehaviorRuleRuleObjectCode(rule.behaviorType, inheritedSubjectObject)
          }
        })
      })

      return nextRules.map(rule => {
        const nextRule = { ...rule }
        delete nextRule.__sourceIndex
        return nextRule
      })
    },

    createSequenceStageRule(templateRule, sequenceId, stageIndex, geometryConfig) {
      const rest = { ...(templateRule || {}) }
      delete rest.id
      delete rest.name
      return this.createBehaviorRule({
        ...rest,
        sequenceId,
        stageIndex,
        stageTimeoutMs: 0,
        stageHoldMs: 0,
        logicMode: 'all'
      }, geometryConfig)
    },

    handleAddBehaviorRule() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const nextRule = this.createBehaviorRule({}, geometryConfig)
      geometryConfig.behaviorRules = [...(geometryConfig.behaviorRules || []), nextRule]
      this.form.geometryConfig = this.normalizeBehaviorRulesInGeometry(geometryConfig)
      this.syncGeometryEditorState()
      this.activeRuleId = nextRule.id
      this.activeSequenceId = ''
      this.workspaceTab = 'rules'
    },

    ensureSleepOnDutyBehaviorRule() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const rules = geometryConfig.behaviorRules || []
      const hasDutyRule = rules.some(rule => this.normalizeBehaviorType(rule && rule.behaviorType) === 'sleep_on_duty')
      if (hasDutyRule) {
        return
      }
      const primaryRegion = this.getPrimaryRegion(geometryConfig)
      const nextRule = this.createBehaviorRule({
        id: 'sleep_on_duty_default',
        name: '睡岗',
        behaviorType: 'sleep_on_duty',
        customEventName: '睡岗',
        thresholdMs: 2500,
        distanceThresholdPx: 32,
        directionToleranceDeg: 10,
        geometryId: primaryRegion ? primaryRegion.id : ''
      }, geometryConfig)
      geometryConfig.behaviorRules = [...rules, nextRule]
      this.form.geometryConfig = this.normalizeBehaviorRulesInGeometry(geometryConfig)
      this.syncGeometryEditorState()
    },

    ensureActiveRuleSelection() {
      const standaloneIds = this.standaloneBehaviorRules.map(rule => rule.id)
      if (this.activeRuleId && standaloneIds.indexOf(this.activeRuleId) !== -1) {
        this.activeSequenceId = ''
        return
      }
      const sequenceIds = this.sequenceRuleGroups.map(group => group.sequenceId)
      if (this.activeSequenceId && sequenceIds.indexOf(this.activeSequenceId) !== -1) {
        this.activeRuleId = ''
        return
      }
      if (standaloneIds.length) {
        this.activeRuleId = standaloneIds[0]
        this.activeSequenceId = ''
        return
      }
      if (sequenceIds.length) {
        this.activeSequenceId = sequenceIds[0]
        this.activeRuleId = ''
        return
      }
      this.activeRuleId = ''
      this.activeSequenceId = ''
    },
    selectStandaloneRule(ruleId) {
      this.activeRuleId = ruleId
      this.activeSequenceId = ''
    },
    selectSequenceGroup(sequenceId) {
      this.activeSequenceId = sequenceId
      this.activeRuleId = ''
    },

    handleUpgradeBehaviorRuleToSequence(ruleId) {
      if (!ruleId) {
        return
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const behaviorRules = (geometryConfig.behaviorRules || []).map(rule => ({ ...rule }))
      const targetRule = behaviorRules.find(rule => rule.id === ruleId)
      if (!this.canUpgradeBehaviorRuleToSequence(targetRule)) {
        return
      }

      const sequenceId = this.createInternalSequenceId()
      const inheritedSubjectObject = this.getBehaviorRuleEffectiveSubjectObject(targetRule) || this.getDefaultBehaviorRuleObjectCode()
      const nextRules = behaviorRules.map(rule => {
        if (rule.id !== ruleId) {
          return rule
        }
        return this.applySequenceSubjectObjectToRule({
          ...rule,
          sequenceId,
          stageIndex: 0,
          stageTimeoutMs: 0,
          stageHoldMs: 0,
          logicMode: 'all'
        }, inheritedSubjectObject)
      })
      const stageRule = this.createSequenceStageRule({
        ...targetRule,
        ...(this.isRelationalBehaviorType(targetRule.behaviorType)
          ? { subjectObject: inheritedSubjectObject }
          : { ruleObjectCode: inheritedSubjectObject })
      }, sequenceId, 1, {
        ...geometryConfig,
        behaviorRules: nextRules
      })
      this.applyBehaviorRulesWithGeometry(geometryConfig, [...nextRules, stageRule])
    },

    handleAddSequenceStage(sequenceId) {
      const normalizedSequenceId = this.normalizeBehaviorRuleSequenceId(sequenceId)
      if (!normalizedSequenceId) {
        return
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const behaviorRules = (geometryConfig.behaviorRules || []).map(rule => ({ ...rule }))
      const sequenceRules = behaviorRules
        .filter(rule => this.normalizeBehaviorRuleSequenceId(rule.sequenceId) === normalizedSequenceId)
        .sort((left, right) => {
          const leftStage = Number.isFinite(Number(left.stageIndex)) ? Number(left.stageIndex) : 0
          const rightStage = Number.isFinite(Number(right.stageIndex)) ? Number(right.stageIndex) : 0
          return leftStage - rightStage
        })
      if (!sequenceRules.length) {
        return
      }
      const templateRule = sequenceRules[sequenceRules.length - 1]
      const maxStageIndex = sequenceRules.reduce((maxValue, rule) => {
        const currentStageIndex = Number.isFinite(Number(rule.stageIndex)) ? Number(rule.stageIndex) : 0
        return Math.max(maxValue, currentStageIndex)
      }, 0)
      const stageRule = this.createSequenceStageRule(templateRule, normalizedSequenceId, maxStageIndex + 1, {
        ...geometryConfig,
        behaviorRules
      })
      this.applyBehaviorRulesWithGeometry(geometryConfig, [...behaviorRules, stageRule])
    },

    handleRemoveBehaviorRule(ruleId) {
      if (!ruleId) {
        return
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const nextRules = (geometryConfig.behaviorRules || [])
        .filter(rule => rule.id !== ruleId)
        .map(rule => ({ ...rule }))
      this.applyBehaviorRulesWithGeometry(geometryConfig, nextRules)
    },

    handleBehaviorRuleEnabledChange(ruleId, enabled) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        enabled: Boolean(enabled)
      }))
    },

    handleBehaviorRuleTypeChange(ruleId, behaviorType) {
      const normalizedBehaviorType = this.normalizeBehaviorType(behaviorType)
      const targetRule = (this.behaviorRuleList || []).find(rule => rule && rule.id === ruleId)
      if (this.isSpecifiedRegionRule(targetRule)) {
        this.updateBehaviorRule(ruleId, rule => this.applySpecifiedRegionModeToRule({ ...rule, behaviorType: 'region_motion' }))
        return
      }
      const sequenceId = this.normalizeBehaviorRuleSequenceId(targetRule && targetRule.sequenceId)
      if (sequenceId) {
        const nextBehaviorType = normalizedBehaviorType || (targetRule && targetRule.behaviorType)
        if (!this.isSequenceCapableBehaviorType(nextBehaviorType)) {
          return
        }
        const inheritedSubjectObject = this.getSequenceGroupSubjectLabelByRule(targetRule)
        this.updateBehaviorRule(ruleId, rule => {
          const nextRule = {
            ...rule,
            behaviorType: nextBehaviorType,
            geometryType: this.isLineBehaviorType(nextBehaviorType) ? 'line' : 'region',
            geometryId: '',
            ...this.getBehaviorRuleDefaultState(nextBehaviorType),
            sequenceId,
            stageIndex: Number.isFinite(Number(rule.stageIndex)) ? Number(rule.stageIndex) : 0,
            stageTimeoutMs: this.normalizeBehaviorRuleStageTimeout(nextBehaviorType, sequenceId, rule.stageTimeoutMs),
            stageHoldMs: this.normalizeBehaviorRuleStageHold(nextBehaviorType, sequenceId, rule.stageHoldMs),
            logicMode: this.normalizeBehaviorRuleLogicMode(rule.logicMode)
          }
          return this.applySequenceSubjectObjectToRule(nextRule, inheritedSubjectObject)
        })
        return
      }
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        behaviorType: normalizedBehaviorType || rule.behaviorType,
        geometryType: this.isLineBehaviorType(normalizedBehaviorType) ? 'line' : 'region',
        geometryId: '',
        ...this.getBehaviorRuleDefaultState(normalizedBehaviorType || rule.behaviorType)
      }))
    },

    handleBehaviorRuleGeometryChange(ruleId, geometryId) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        geometryId: geometryId || ''
      }))
    },

    handleBehaviorRuleDirectionChange(ruleId, direction) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        direction: this.normalizeLineDirection(direction)
      }))
    },

    handleBehaviorRuleDirectionToggle(ruleId) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        direction: this.getNextLineDirection(this.normalizeLineDirection(rule.direction))
      }))
    },

    handleBehaviorRuleThresholdChange(ruleId, thresholdMs) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        thresholdMs: this.normalizeBehaviorRuleThresholdMs(rule.behaviorType, thresholdMs)
      }))
    },

    handleBehaviorRuleThresholdCountChange(ruleId, thresholdCount) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        thresholdCount: this.normalizeBehaviorRuleThresholdCount(rule.behaviorType, thresholdCount)
      }))
    },

    applySpecifiedRegionModeToRule(rule) {
      const defaultState = this.getBehaviorRuleDefaultState('region_motion')
      const fallbackRegionId = this.regionOptions.length ? this.regionOptions[0].value : ''
      return {
        ...rule,
        ...defaultState,
        behaviorType: 'region_motion',
        geometryType: 'region',
        geometryId: rule.geometryId || fallbackRegionId,
        ruleObjectCode: 'specified_region',
        subjectObject: '',
        targetObject: '',
        sequenceId: '',
        stageIndex: 0,
        stageTimeoutMs: 0,
        stageHoldMs: 0,
        logicMode: 'all'
      }
    },

    clearSpecifiedRegionModeFromRule(rule, nextRuleObjectCode) {
      const fallbackBehaviorType = rule.behaviorType === 'region_motion' ? 'dwell' : rule.behaviorType
      const defaultState = this.getBehaviorRuleDefaultState(fallbackBehaviorType)
      return {
        ...rule,
        ...defaultState,
        behaviorType: fallbackBehaviorType,
        geometryType: this.isLineBehaviorType(fallbackBehaviorType) ? 'line' : 'region',
        ruleObjectCode: this.normalizeBehaviorRuleRuleObjectCode(fallbackBehaviorType, nextRuleObjectCode),
        sequenceId: this.isSequenceCapableBehaviorType(fallbackBehaviorType) ? rule.sequenceId : '',
        stageIndex: this.isSequenceCapableBehaviorType(fallbackBehaviorType) ? rule.stageIndex : 0,
        stageTimeoutMs: this.isSequenceCapableBehaviorType(fallbackBehaviorType) ? rule.stageTimeoutMs : 0,
        stageHoldMs: this.isSequenceCapableBehaviorType(fallbackBehaviorType) ? rule.stageHoldMs : 0,
        logicMode: this.isSequenceCapableBehaviorType(fallbackBehaviorType) ? this.normalizeBehaviorRuleLogicMode(rule.logicMode) : 'all'
      }
    },

    handleBehaviorRuleObjectChange(ruleId, ruleObjectCode) {
      const targetRule = (this.behaviorRuleList || []).find(rule => rule && rule.id === ruleId)
      if (!targetRule) {
        return
      }

      if (this.isSpecifiedRegionRuleTargetValue(ruleObjectCode)) {
        if (this.normalizeBehaviorRuleSequenceId(targetRule.sequenceId)) {
          this.$message.warning('多阶段规则暂不支持指定区域模式')
          return
        }
        this.updateBehaviorRule(ruleId, rule => this.applySpecifiedRegionModeToRule(rule))
        return
      }

      const sequenceId = this.normalizeBehaviorRuleSequenceId(targetRule && targetRule.sequenceId)
      if (!sequenceId) {
        this.updateBehaviorRule(ruleId, rule => {
          if (this.isSpecifiedRegionRule(rule)) {
            return this.clearSpecifiedRegionModeFromRule(rule, ruleObjectCode)
          }
          return {
            ...rule,
            ruleObjectCode: this.normalizeBehaviorRuleRuleObjectCode(rule.behaviorType, ruleObjectCode)
          }
        })
        return
      }

      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const normalizedSubjectObject = this.normalizeBehaviorRuleRuleObjectCode(targetRule && targetRule.behaviorType, ruleObjectCode)
      const nextRules = (geometryConfig.behaviorRules || []).map(rule => {
        if (this.normalizeBehaviorRuleSequenceId(rule.sequenceId) !== sequenceId) {
          return { ...rule }
        }
        return this.applySequenceSubjectObjectToRule({ ...rule }, normalizedSubjectObject)
      })
      this.applyBehaviorRulesWithGeometry(geometryConfig, nextRules)
    },

    handleBehaviorRuleSubjectObjectChange(ruleId, subjectObject) {
      const targetRule = (this.behaviorRuleList || []).find(rule => rule && rule.id === ruleId)
      const sequenceId = this.normalizeBehaviorRuleSequenceId(targetRule && targetRule.sequenceId)
      if (!sequenceId) {
        this.updateBehaviorRule(ruleId, rule => ({
          ...rule,
          subjectObject: this.normalizeBehaviorRuleObjectValue(subjectObject)
        }))
        return
      }

      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const normalizedSubjectObject = this.normalizeBehaviorRuleObjectValue(subjectObject)
      const nextRules = (geometryConfig.behaviorRules || []).map(rule => {
        if (this.normalizeBehaviorRuleSequenceId(rule.sequenceId) !== sequenceId) {
          return { ...rule }
        }
        return this.applySequenceSubjectObjectToRule({ ...rule }, normalizedSubjectObject)
      })
      this.applyBehaviorRulesWithGeometry(geometryConfig, nextRules)
    },

    handleBehaviorRuleTargetObjectChange(ruleId, targetObject) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        targetObject: this.normalizeBehaviorRuleObjectValue(targetObject)
      }))
    },

    handleBehaviorRuleCustomEventNameChange(ruleId, customEventName) {
      const normalizedCustomEventName = this.normalizeBehaviorRuleCustomEventName(customEventName)
      const targetRule = (this.behaviorRuleList || []).find(rule => rule && rule.id === ruleId)
      const sequenceId = this.normalizeBehaviorRuleSequenceId(targetRule && targetRule.sequenceId)
      if (sequenceId) {
        this.handleSequenceGroupCustomEventNameChange(sequenceId, normalizedCustomEventName)
        return
      }
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        customEventName: normalizedCustomEventName
      }))
    },

    handleBehaviorRuleOutputModeChange(ruleId, outputMode) {
      const normalizedOutputMode = this.normalizeBehaviorRuleOutputMode(outputMode)
      const targetRule = (this.behaviorRuleList || []).find(rule => rule && rule.id === ruleId)
      const sequenceId = this.normalizeBehaviorRuleSequenceId(targetRule && targetRule.sequenceId)
      if (sequenceId) {
        this.handleSequenceGroupOutputModeChange(sequenceId, normalizedOutputMode)
        return
      }
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        outputMode: normalizedOutputMode
      }))
    },

    handleBehaviorRuleDistanceChange(ruleId, distanceThresholdPx) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        distanceThresholdPx: this.normalizeBehaviorRuleDistance(rule.behaviorType, distanceThresholdPx)
      }))
    },

    handleBehaviorRuleSequenceIdChange(ruleId, sequenceId) {
      this.updateBehaviorRule(ruleId, rule => {
        const normalizedSequenceId = this.normalizeBehaviorRuleSequenceId(sequenceId)
        return {
          ...rule,
          sequenceId: this.isSequenceCapableBehaviorType(rule.behaviorType) ? normalizedSequenceId : '',
          stageIndex: this.normalizeBehaviorRuleStageIndex(rule.behaviorType, normalizedSequenceId, rule.stageIndex),
          stageTimeoutMs: this.normalizeBehaviorRuleStageTimeout(rule.behaviorType, normalizedSequenceId, rule.stageTimeoutMs),
          stageHoldMs: this.normalizeBehaviorRuleStageHold(rule.behaviorType, normalizedSequenceId, rule.stageHoldMs),
          logicMode: normalizedSequenceId ? this.normalizeBehaviorRuleLogicMode(rule.logicMode) : 'all'
        }
      })
    },

    getSequenceGroupCustomEventName(group) {
      if (!group || !Array.isArray(group.rules) || !group.rules.length) {
        return ''
      }
      const namedRule = group.rules.find(rule => this.normalizeBehaviorRuleCustomEventName(rule && rule.customEventName))
      return namedRule ? this.normalizeBehaviorRuleCustomEventName(namedRule.customEventName) : ''
    },

    getSequenceGroupOutputMode(group) {
      if (!group || !Array.isArray(group.rules) || !group.rules.length) {
        return 'direct_alarm'
      }
      const firstMode = this.normalizeBehaviorRuleOutputMode(group.rules[0] && group.rules[0].outputMode)
      const sameMode = group.rules.every(rule => this.normalizeBehaviorRuleOutputMode(rule && rule.outputMode) === firstMode)
      return sameMode ? firstMode : 'direct_alarm'
    },

    handleSequenceGroupCustomEventNameChange(sequenceId, customEventName) {
      const normalizedSequenceId = this.normalizeBehaviorRuleSequenceId(sequenceId)
      if (!normalizedSequenceId) {
        return
      }
      const normalizedCustomEventName = this.normalizeBehaviorRuleCustomEventName(customEventName)
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      geometryConfig.behaviorRules = (geometryConfig.behaviorRules || []).map(rule => {
        if (this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId) !== normalizedSequenceId) {
          return rule
        }
        return {
          ...rule,
          customEventName: normalizedCustomEventName
        }
      })
      this.form.geometryConfig = this.normalizeBehaviorRulesInGeometry(geometryConfig)
      this.syncGeometryEditorState()
      this.drawPolygon()
    },

    handleSequenceGroupOutputModeChange(sequenceId, outputMode) {
      const normalizedSequenceId = this.normalizeBehaviorRuleSequenceId(sequenceId)
      if (!normalizedSequenceId) {
        return
      }
      const normalizedOutputMode = this.normalizeBehaviorRuleOutputMode(outputMode)
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      geometryConfig.behaviorRules = (geometryConfig.behaviorRules || []).map(rule => {
        if (this.normalizeBehaviorRuleSequenceId(rule && rule.sequenceId) !== normalizedSequenceId) {
          return rule
        }
        return {
          ...rule,
          outputMode: normalizedOutputMode
        }
      })
      this.form.geometryConfig = this.normalizeBehaviorRulesInGeometry(geometryConfig)
      this.syncGeometryEditorState()
      this.drawPolygon()
    },

    handleBehaviorRuleStageIndexChange(ruleId, stageIndex) {
      const targetRule = (this.behaviorRuleList || []).find(rule => rule && rule.id === ruleId)
      const sequenceId = this.normalizeBehaviorRuleSequenceId(targetRule && targetRule.sequenceId)
      if (!sequenceId) {
        this.updateBehaviorRule(ruleId, rule => ({
          ...rule,
          stageIndex: this.normalizeBehaviorRuleStageIndex(rule.behaviorType, rule.sequenceId, stageIndex)
        }))
        return
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const nextRules = (geometryConfig.behaviorRules || []).map(rule => {
        if (rule.id !== ruleId) {
          return { ...rule }
        }
        return {
          ...rule,
          stageIndex: this.normalizeBehaviorRuleStageIndex(rule.behaviorType, sequenceId, stageIndex)
        }
      })
      this.applyBehaviorRulesWithGeometry(geometryConfig, nextRules)
    },

    handleBehaviorRuleStageTimeoutChange(ruleId, stageTimeoutMs) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        stageTimeoutMs: this.normalizeBehaviorRuleStageTimeout(rule.behaviorType, rule.sequenceId, stageTimeoutMs)
      }))
    },

    handleBehaviorRuleStageHoldChange(ruleId, stageHoldMs) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        stageHoldMs: this.normalizeBehaviorRuleStageHold(rule.behaviorType, rule.sequenceId, stageHoldMs)
      }))
    },

    handleBehaviorRuleLogicModeChange(ruleId, logicMode) {
      const targetRule = (this.behaviorRuleList || []).find(rule => rule && rule.id === ruleId)
      const sequenceId = this.normalizeBehaviorRuleSequenceId(targetRule && targetRule.sequenceId)
      if (!sequenceId) {
        this.updateBehaviorRule(ruleId, rule => ({
          ...rule,
          logicMode: this.isBehaviorRuleSequenceConfigVisible(rule) ? this.normalizeBehaviorRuleLogicMode(logicMode) : 'all'
        }))
        return
      }

      const targetStageIndex = Number.isFinite(Number(targetRule && targetRule.stageIndex)) ? Number(targetRule.stageIndex) : 0
      const normalizedLogicMode = this.normalizeBehaviorRuleLogicMode(logicMode)
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const nextRules = (geometryConfig.behaviorRules || []).map(rule => {
        if (this.normalizeBehaviorRuleSequenceId(rule.sequenceId) !== sequenceId) {
          return { ...rule }
        }
        const stageIndex = Number.isFinite(Number(rule.stageIndex)) ? Number(rule.stageIndex) : 0
        if (stageIndex !== targetStageIndex) {
          return { ...rule }
        }
        return {
          ...rule,
          logicMode: normalizedLogicMode
        }
      })
      this.applyBehaviorRulesWithGeometry(geometryConfig, nextRules)
    },

    handleBehaviorRuleMaxSpeedChange(ruleId, maxSpeedPxPerSec) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        maxSpeedPxPerSec: this.normalizeBehaviorRuleMaxSpeed(rule.behaviorType, maxSpeedPxPerSec)
      }))
    },

    handleBehaviorRuleMaxDisplacementChange(ruleId, maxDisplacementPx) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        maxDisplacementPx: this.normalizeBehaviorRuleMaxDisplacement(rule.behaviorType, maxDisplacementPx)
      }))
    },

    handleBehaviorRuleDirectionAngleChange(ruleId, directionAngleDeg) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        directionLineId: '',
        directionAngleDeg: this.normalizeBehaviorRuleDirectionAngle(rule.behaviorType, directionAngleDeg)
      }))
    },

    handleBehaviorRuleDirectionToleranceChange(ruleId, directionToleranceDeg) {
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        directionToleranceDeg: this.normalizeBehaviorRuleDirectionTolerance(rule.behaviorType, directionToleranceDeg)
      }))
    },

    handleBehaviorRuleDirectionLineChange(ruleId, directionLineId) {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      this.updateBehaviorRule(ruleId, rule => ({
        ...rule,
        directionLineId: this.normalizeBehaviorRuleDirectionLineId(rule.behaviorType, directionLineId, geometryConfig)
      }))
    },

    ensureActiveLine() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      let activeLine = this.getActiveLine(geometryConfig)
      if (!activeLine) {
        activeLine = this.createLineConfig({
          name: `线段${geometryConfig.lines.length + 1}`
        })
        geometryConfig.lines = [...geometryConfig.lines, activeLine]
        this.form.geometryConfig = geometryConfig
        this.activeLineId = activeLine.id
      }
      this.syncGeometryEditorState()
      return this.getActiveLine()
    },

    getCurrentFormSnapshot() {
      const geometryConfig = this.syncGeometryConfigFromPolygon()
      return JSON.stringify({
        form: {
          taskName: this.form.taskName,
          deviceId: this.form.deviceId,
          algorithmTasks: this.form.algorithmTasks.map(item => ({
            algorithmCode: item.algorithmCode,
            algorithmName: item.algorithmName,
            detectFps: this.normalizeDetectFpsValue(item.detectFps),
            scoreThreshold: this.normalizeThresholdValue(item.scoreThreshold),
            nmsThreshold: this.normalizeThresholdValue(item.nmsThreshold),
            targetCodes: item.targetCodes
          })),
          pushEnabled: this.form.pushEnabled,
          frontendOverlayEnabled: this.form.pushEnabled ? false : this.form.frontendOverlayEnabled,
          recordEngine: this.form.recordEngine,
          alarmIntervalSec: Number(this.form.alarmIntervalSec),
          aiReviewEnabled: this.form.aiReviewEnabled,
          aiReviewPrompt: this.form.aiReviewPrompt,
          remark: this.form.remark,
          geometryConfig
        },
        streamUrl: this.streamUrl,
        videoLoaded: this.videoLoaded
      })
    },

    validateBehaviorRulesBeforeSave(geometryConfig) {
      const behaviorRules = (geometryConfig && geometryConfig.behaviorRules) || []
      for (let i = 0; i < behaviorRules.length; i += 1) {
        const rule = behaviorRules[i]
        if (!this.isSpecifiedRegionRule(rule)) {
          continue
        }
        if (rule.behaviorType !== 'region_motion') {
          this.$message.error(`规则 ${i + 1}：指定区域模式仅支持“区域运动”`)
          return false
        }
        if (!rule.geometryId) {
          this.$message.error(`规则 ${i + 1}：指定区域模式必须绑定区域`)
          return false
        }
      }
      return true
    },

    syncInitialSnapshot() {
      this.initialFormSnapshot = this.getCurrentFormSnapshot()
    },

    hasFormChanges() {
      return this.getCurrentFormSnapshot() !== this.initialFormSnapshot
    },

    resetDeploymentState() {
      this.form = this.getDefaultForm()
      this.regionSeed = 1
      this.lineSeed = 1
      this.behaviorRuleSeed = 1
      this.polygonPoints = []
      this.polygonClosed = false
      this.form.geometryConfig = this.createEmptyGeometryConfig()
      this.geometryEditorMode = 'region'
      this.activeRegionId = ''
      this.activeLineId = ''
      this.clearDetectFrame(false)
      this.streamUrl = ''
      this.videoLoaded = false
      this.destroyPlayer()
      this.drawPolygon()
      if (this.$refs.deploymentForm) {
        this.$refs.deploymentForm.clearValidate()
      }
      this.$nextTick(() => {
        this.syncCanvasSize()
      })
    },

    async handleCreateNew() {
      if (this.saveLoading) {
        return
      }
      const needConfirm = !!this.deploymentId || this.hasFormChanges()
      if (needConfirm) {
        try {
          await this.$confirm('将清空当前布控参数并进入新建模式，是否继续？', '提示', {
            type: 'warning',
            confirmButtonText: '继续',
            cancelButtonText: '取消'
          })
        } catch (error) {
          return
        }
      }
      this.resetDeploymentState()
      this.deploymentId = ''
      await this.ensureAlgorithmTasksReady()
      this.syncInitialSnapshot()
      this.$message.success('已进入新建模式')
    },

    handleOpenEventOrchestration() {
      if (!this.hasConditionOnlyEventRule) {
        this.$message.info('当前未配置“仅产出事件”规则，无需配置事件编排')
        return
      }
      if (!this.deploymentId) {
        this.$message.warning('请先保存布控后再进入事件编排')
        return
      }
      this.$router.push({ path: '/deployment/event-orchestration', query: { deploymentId: this.deploymentId }})
    },

    createAlgorithmTask(overrides = {}) {
      const task = {
        uid: this.algorithmTaskSeed,
        algorithmCode: '',
        algorithmName: '',
        detectFps: 8,
        scoreThreshold: null,
        nmsThreshold: null,
        targetCodes: [],
        targetOptions: []
      }
      this.algorithmTaskSeed += 1
      const mergedTask = { ...task, ...overrides }
      this.applyAlgorithmTaskThresholdDefaults(mergedTask)
      return mergedTask
    },

    getAlgorithmThresholdDefaults(algorithmCode) {
      const code = String(algorithmCode || '').trim()
      if (code === 'on_yolo26s_miner' || code === 'on_yolo26n_80') {
        return {
          scoreThreshold: 0.25,
          nmsThreshold: 0.00
        }
      }
      if (code === 'on_sleep_pose') {
        return {
          scoreThreshold: 0.50,
          nmsThreshold: 0.50
        }
      }
      return {
        scoreThreshold: 0.50,
        nmsThreshold: 0.50
      }
    },

    applyAlgorithmTaskThresholdDefaults(task, options = {}) {
      if (!task) {
        return
      }
      const { force = false } = options
      if (!task.algorithmCode) {
        if (force) {
          task.scoreThreshold = null
          task.nmsThreshold = null
        }
        return
      }
      const defaults = this.getAlgorithmThresholdDefaults(task.algorithmCode)
      if (force || task.scoreThreshold === '' || task.scoreThreshold === undefined || task.scoreThreshold === null) {
        task.scoreThreshold = defaults.scoreThreshold
      }
      if (force || task.nmsThreshold === '' || task.nmsThreshold === undefined || task.nmsThreshold === null) {
        task.nmsThreshold = defaults.nmsThreshold
      }
    },

    normalizeTaskTargetCodes(targetCodes) {
      if (!Array.isArray(targetCodes)) {
        return []
      }
      const seen = new Set()
      return targetCodes
        .map(item => String(item || '').trim().toLowerCase())
        .filter(item => item && !seen.has(item) && seen.add(item))
    },

    normalizeDetectFpsValue(value) {
      if (value === '' || value === undefined || value === null) {
        return 8
      }
      const numeric = Number(value)
      if (!Number.isFinite(numeric)) {
        return 8
      }
      if (numeric < 0) {
        return 0
      }
      if (numeric > 30) {
        return 30
      }
      return numeric
    },

    normalizeThresholdValue(value) {
      if (value === '' || value === undefined || value === null) {
        return null
      }
      const numeric = Number(value)
      if (!Number.isFinite(numeric)) {
        return null
      }
      if (numeric < 0) {
        return 0
      }
      if (numeric > 1) {
        return 1
      }
      return numeric
    },

    async ensureAlgorithmTasksReady() {
      if (!Array.isArray(this.form.algorithmTasks) || !this.form.algorithmTasks.length) {
        this.form.algorithmTasks = [this.createAlgorithmTask()]
      }
      for (let i = 0; i < this.form.algorithmTasks.length; i += 1) {
        const task = this.form.algorithmTasks[i]
        if (!task.algorithmCode && this.algorithmOptions.length > 0) {
          const defaultAlgorithm = this.resolveDefaultAlgorithmForNewTask()
          if (defaultAlgorithm) {
            task.algorithmCode = defaultAlgorithm.code
            task.algorithmName = defaultAlgorithm.name
          }
        }
        this.applyAlgorithmTaskThresholdDefaults(task)
        await this.loadTargetOptionsForTask(task, task.algorithmCode, task.targetCodes)
      }
    },

    resolveDefaultAlgorithmForNewTask() {
      const usedCodes = new Set(this.form.algorithmTasks.map(item => item.algorithmCode).filter(Boolean))
      return this.algorithmOptions.find(item => !usedCodes.has(item.code)) || this.algorithmOptions[0] || null
    },

    async loadTargetOptionsForTask(task, code, preferredTargetCodes = []) {
      if (!task) {
        return
      }
      if (!code) {
        this.$set(task, 'targetOptions', [])
        task.targetCodes = []
        return
      }
      try {
        const response = await getAlgorithmTargets(code)
        const targets =
          (response && Array.isArray(response.data) && response.data) ||
          (response && Array.isArray(response.rows) && response.rows) ||
          (Array.isArray(response) && response) ||
          []
        const targetOptions = targets
          .map(item => {
            const value = String(item || '').trim()
            if (!value) return null
            return {
              value,
              label: value
            }
          })
          .filter(Boolean)
        this.$set(task, 'targetOptions', targetOptions)
        if (!targetOptions.length) {
          task.targetCodes = []
          return
        }
        const preferredValues = this.normalizeTaskTargetCodes(
          Array.isArray(preferredTargetCodes) && preferredTargetCodes.length ? preferredTargetCodes : task.targetCodes
        )
        const selectedTargetCodes = targetOptions
          .map(item => item.value)
          .filter(value => preferredValues.includes(value))
        task.targetCodes = selectedTargetCodes.length ? selectedTargetCodes : [targetOptions[0].value]
      } catch (error) {
        this.$set(task, 'targetOptions', [])
        task.targetCodes = []
        this.$message.error('获取检测目标失败')
      }
    },

    async handleAlgorithmChange(index, code) {
      const task = this.form.algorithmTasks[index]
      if (!task) {
        return
      }
      const matched = this.algorithmOptions.find(item => item.code === code)
      task.algorithmName = matched ? matched.name : ''
      this.applyAlgorithmTaskThresholdDefaults(task, { force: true })
      task.targetCodes = []
      await this.loadTargetOptionsForTask(task, code)
      this.clearAlgorithmTasksValidation()
      if (code === 'on_sleep_pose') {
        this.ensureSleepOnDutyBehaviorRule()
      }
    },

    async handleAddAlgorithmTask() {
      if (!this.algorithmOptions.length) {
        this.$message.warning('暂无可选算法')
        return
      }
      if (this.form.algorithmTasks.length >= this.algorithmOptions.length) {
        this.$message.warning('所有算法都已添加')
        return
      }
      const defaultAlgorithm = this.resolveDefaultAlgorithmForNewTask()
      const task = this.createAlgorithmTask({
        algorithmCode: defaultAlgorithm ? defaultAlgorithm.code : '',
        algorithmName: defaultAlgorithm ? defaultAlgorithm.name : ''
      })
      this.form.algorithmTasks.push(task)
      await this.loadTargetOptionsForTask(task, task.algorithmCode)
      this.clearAlgorithmTasksValidation()
      if (task.algorithmCode === 'on_sleep_pose') {
        this.ensureSleepOnDutyBehaviorRule()
      }
    },

    handleRemoveAlgorithmTask(index) {
      if (this.form.algorithmTasks.length <= 1) {
        this.$message.warning('至少保留一个算法配置')
        return
      }
      this.form.algorithmTasks.splice(index, 1)
      this.clearAlgorithmTasksValidation()
    },

    clearAlgorithmTasksValidation() {
      if (this.$refs.deploymentForm) {
        this.$refs.deploymentForm.clearValidate(['algorithmTasks'])
      }
    },

    handleVideoLoaded() {
      this.videoLoaded = true
    },

    handleDetectFramePush(event) {
      const detail = (event && event.detail) || {}
      const frame = detail.frame || null
      if (!frame || frame.type !== 'detect.frame') {
        return
      }
      if (!this.isDetectFrameMatched(frame)) {
        return
      }
      if (this.form.pushEnabled) {
        this.clearDetectFrame()
        return
      }
      if (!this.toBoolean(this.form.frontendOverlayEnabled, true)) {
        this.clearDetectFrame()
        return
      }

      const renderMode = String(frame.renderMode || '').toLowerCase()
      if (renderMode !== 'ws_overlay') {
        this.clearDetectFrame()
        return
      }

      const nextSeq = Number(frame.frameSeq || 0)
      const currentSeq = Number(this.detectFrame && this.detectFrame.frameSeq)
      if (Number.isFinite(currentSeq) && Number.isFinite(nextSeq) && nextSeq > 0 && currentSeq > nextSeq) {
        return
      }

      this.scheduleDetectFrameRender(frame)
    },

    handleDetectEventPush(event) {
      const detail = (event && event.detail) || {}
      const detectEvent = detail.event || null
      if (!detectEvent || detectEvent.type !== 'detect.event') {
        return
      }
      if (!this.isDetectFrameMatched(detectEvent)) {
        return
      }
      if (!this.isRuleLevelDetectEvent(detectEvent)) {
        return
      }

      const eventId = String(detectEvent.eventId || '').trim()
      const eventState = String(detectEvent.eventState || '').trim().toLowerCase() || 'active'
      const timestampMs = Number(detectEvent.timestampMs || 0)
      const key = [eventId, eventState, timestampMs].join('|')
      const nextItem = {
        key,
        eventState,
        eventStateLabel: this.getDetectEventStateLabel(eventState),
        timestampText: this.formatDetectEventTime(timestampMs),
        summary: this.getDetectEventSummary(detectEvent)
      }

      this.recentDetectEvents = [nextItem]
        .concat(this.recentDetectEvents.filter(item => item.key !== key))
        .slice(0, 6)
    },

    isDetectFrameMatched(frame) {
      const deploymentId = String(this.deploymentId || '').trim()
      const deviceId = String(this.form.deviceId || '').trim()
      const controlCode = String(frame.controlCode || frame.control_code || '').trim()
      const streamCode = String(frame.streamCode || '').trim()
      if (deploymentId && controlCode && deploymentId === controlCode) {
        return true
      }
      if (deviceId && streamCode && deviceId === streamCode) {
        return true
      }
      return false
    },

    isRuleLevelDetectEvent(detectEvent) {
      if (!detectEvent) {
        return false
      }
      const behaviorType = String(detectEvent.behaviorType || '').trim()
      if (!behaviorType) {
        return false
      }
      const scopeText = String(detectEvent.regionName || detectEvent.lineName || detectEvent.ruleId || '').trim()
      return Boolean(scopeText)
    },

    formatDetectEventTime(timestampMs) {
      if (!timestampMs || Number.isNaN(timestampMs)) {
        return '--:--:--'
      }
      const date = new Date(timestampMs)
      const hours = String(date.getHours()).padStart(2, '0')
      const minutes = String(date.getMinutes()).padStart(2, '0')
      const seconds = String(date.getSeconds()).padStart(2, '0')
      return `${hours}:${minutes}:${seconds}`
    },

    getDetectEventStateLabel(eventState) {
      if (eventState === 'start') {
        return '开始'
      }
      if (eventState === 'end') {
        return '结束'
      }
      return '进行中'
    },

    getDetectEventSummary(detectEvent) {
      if (!detectEvent) {
        return ''
      }
      const behaviorText = this.getBehaviorTypeLabel(String(detectEvent.behaviorType || '').trim())
      const scopeText = String(detectEvent.regionName || detectEvent.lineName || detectEvent.ruleId || '').trim()
      const parts = [behaviorText, scopeText]
      if (detectEvent.crossingDirection) {
        parts.push(this.getLineDirectionLabel(detectEvent.crossingDirection))
      }
      const aggregateCount = Number(detectEvent.aggregateCount)
      if (Number.isFinite(aggregateCount) && aggregateCount > 0) {
        parts.push(`数量 ${Math.round(aggregateCount)}`)
      }
      const aggregateThresholdCount = Number(detectEvent.aggregateThresholdCount)
      if (Number.isFinite(aggregateThresholdCount) && aggregateThresholdCount > 0) {
        parts.push(`阈值 ${Math.round(aggregateThresholdCount)}`)
      }
      return parts.filter(Boolean).join(' / ')
    },

    applyDetectFrame(frame) {
      if (this.detectFrameRenderTimer) {
        clearTimeout(this.detectFrameRenderTimer)
      }
      this.detectFrameRenderTimer = null
      this.pendingDetectFrame = null
      this.detectFrame = frame
      this.drawPolygon()
      this.scheduleDetectFrameClear()
    },

    scheduleDetectFrameRender(frame) {
      const delayMs = Number(this.overlayDelayMs || 0)
      if (!delayMs || this.detectFrame) {
        this.applyDetectFrame(frame)
        return
      }
      this.pendingDetectFrame = frame
      if (this.detectFrameRenderTimer) {
        return
      }
      this.detectFrameRenderTimer = setTimeout(() => {
        this.detectFrameRenderTimer = null
        const pendingFrame = this.pendingDetectFrame
        this.pendingDetectFrame = null
        if (this.form.pushEnabled || !this.toBoolean(this.form.frontendOverlayEnabled, true)) {
          this.clearDetectFrame()
          return
        }
        if (pendingFrame) {
          this.applyDetectFrame(pendingFrame)
        }
      }, delayMs)
    },

    scheduleDetectFrameClear() {
      if (this.detectFrameClearTimer) {
        clearTimeout(this.detectFrameClearTimer)
      }
      this.detectFrameClearTimer = setTimeout(() => {
        this.clearDetectFrame(false)
        this.drawPolygon()
      }, 1500)
    },

    clearDetectFrame(redraw = true) {
      if (this.detectFrameRenderTimer) {
        clearTimeout(this.detectFrameRenderTimer)
        this.detectFrameRenderTimer = null
      }
      if (this.detectFrameClearTimer) {
        clearTimeout(this.detectFrameClearTimer)
        this.detectFrameClearTimer = null
      }
      this.pendingDetectFrame = null
      this.detectFrame = null
      if (redraw) {
        this.drawPolygon()
      }
    },

    async handleCopyDeploymentId() {
      if (!this.deploymentId) {
        return
      }
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(this.deploymentId)
        } else {
          const input = document.createElement('input')
          input.value = this.deploymentId
          document.body.appendChild(input)
          input.select()
          document.execCommand('copy')
          document.body.removeChild(input)
        }
        this.$message.success('deploymentId 已复制')
      } catch (error) {
        this.$message.warning('复制失败，请手动复制')
      }
    },

    playStream(url) {
      this.videoLoaded = false
      if (this.$refs.previewPane) {
        this.$refs.previewPane.playStream(url)
      }
    },

    destroyPlayer() {
      this.videoLoaded = false
      if (this.$refs.previewPane) {
        this.$refs.previewPane.destroyPlayer()
      }
    },

    syncCanvasSize() {
      if (this.$refs.previewPane) {
        this.$refs.previewPane.syncCanvasSize()
      }
    },

    getPreviewCanvas() {
      return this.$refs.previewPane && this.$refs.previewPane.getCanvas()
    },

    getPreviewVideo() {
      return this.$refs.previewPane && this.$refs.previewPane.getVideo()
    },

    handleCanvasClick(event) {
      const canvas = this.getPreviewCanvas()
      if (!canvas || !canvas.width || !canvas.height) {
        return
      }
      const rect = canvas.getBoundingClientRect()
      const x = (event.clientX - rect.left) / rect.width
      const y = (event.clientY - rect.top) / rect.height

      if (this.geometryEditorMode === 'line') {
        const activeLine = this.ensureActiveLine()
        if (!activeLine) {
          return
        }
        if (Array.isArray(activeLine.points) && activeLine.points.length >= 2) {
          this.$message.warning('当前线段已有 2 个点，请先清空或删除后重新绘制')
          return
        }
        this.updateLine(activeLine.id, line => ({
          ...line,
          points: [...(line.points || []), { x: this.clamp01(x), y: this.clamp01(y) }]
        }))
        this.drawPolygon()
        return
      }

      this.ensureActiveRegion()
      if (this.polygonClosed) {
        this.$message.warning('当前区域已闭合，请先清空后重新绘制')
        return
      }
      this.polygonPoints.push({ x: this.clamp01(x), y: this.clamp01(y) })
      this.syncGeometryConfigFromPolygon()
      this.drawPolygon()
    },

    handleCanvasDblClick() {
      if (this.geometryEditorMode === 'line') {
        return
      }
      if (this.polygonPoints.length < 3) {
        this.$message.warning('至少需要 3 个点才能闭合区域')
        return
      }
      this.polygonClosed = true
      this.syncGeometryConfigFromPolygon()
      this.drawPolygon()
    },

    handleAlignPolygon() {
      if (this.polygonPoints.length === 0) {
        return
      }
      this.polygonPoints = this.polygonPoints.map(point => ({
        x: this.clamp01(Number(point.x.toFixed(2))),
        y: this.clamp01(Number(point.y.toFixed(2)))
      }))
      this.syncGeometryConfigFromPolygon()
      this.drawPolygon()
    },

    handleAlignActiveLine() {
      const activeLine = this.getActiveLine()
      if (!activeLine || !Array.isArray(activeLine.points) || !activeLine.points.length) {
        return
      }
      this.updateLine(activeLine.id, line => ({
        ...line,
        points: (line.points || []).map(point => ({
          x: this.clamp01(Number(point.x.toFixed(2))),
          y: this.clamp01(Number(point.y.toFixed(2)))
        }))
      }))
      this.drawPolygon()
    },

    handleAlignCurrentGeometry() {
      if (this.geometryEditorMode === 'line') {
        this.handleAlignActiveLine()
        return
      }
      this.handleAlignPolygon()
    },

    handleClearPolygon() {
      const activeRegion = this.ensureActiveRegion()
      if (!activeRegion) {
        return
      }
      this.polygonPoints = []
      this.polygonClosed = false
      this.updateRegion(activeRegion.id, region => ({
        ...region,
        points: []
      }))
      this.drawPolygon()
    },

    handleClearActiveLine() {
      const activeLine = this.getActiveLine()
      if (!activeLine) {
        return
      }
      this.updateLine(activeLine.id, line => ({
        ...line,
        points: []
      }))
      this.drawPolygon()
    },

    handleClearCurrentGeometry() {
      if (this.geometryEditorMode === 'line') {
        this.handleClearActiveLine()
        return
      }
      this.handleClearPolygon()
    },

    handleAddRegion() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const primaryRegionId = ((geometryConfig.regions || []).find(region => region.primary) || {}).id || ''
      const nextRegion = this.createRegionConfig({
        primary: !geometryConfig.regions.some(region => region.primary)
      })
      geometryConfig.regions = this.normalizeRegionPrimaryState([...geometryConfig.regions, nextRegion], primaryRegionId || nextRegion.id)
      this.form.geometryConfig = geometryConfig
      this.activeRegionId = nextRegion.id
      this.geometryEditorMode = 'region'
      this.syncGeometryEditorState()
      this.drawPolygon()
    },

    handleSelectRegion(regionId) {
      this.syncGeometryConfigFromPolygon()
      this.activeRegionId = regionId || ''
      this.syncGeometryEditorState()
      this.drawPolygon()
    },

    handleSetActivePrimary() {
      if (!this.activeRegionId) {
        return
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      geometryConfig.regions = geometryConfig.regions.map(region => ({
        ...region,
        primary: region.id === this.activeRegionId
      }))
      geometryConfig.regions = this.normalizeRegionPrimaryState(geometryConfig.regions, this.activeRegionId)
      this.form.geometryConfig = geometryConfig
      this.syncGeometryEditorState()
      this.drawPolygon()
      const activeRegion = this.getActiveRegion()
      this.$message.success(`已设为主区域：${(activeRegion && activeRegion.name) || this.activeRegionId}`)
    },

    handleRemoveActiveRegion() {
      if (!this.activeRegionId) {
        return
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const primaryRegionId = ((geometryConfig.regions || []).find(region => region.primary) || {}).id || ''
      geometryConfig.regions = geometryConfig.regions.filter(region => region.id !== this.activeRegionId)
      geometryConfig.regions = this.normalizeRegionPrimaryState(
        geometryConfig.regions,
        primaryRegionId && primaryRegionId !== this.activeRegionId ? primaryRegionId : ''
      )
      this.form.geometryConfig = geometryConfig
      this.activeRegionId = ''
      this.syncGeometryEditorState()
      this.drawPolygon()
    },

    handleAddLine() {
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      const nextLine = this.createLineConfig({
        name: `线段${geometryConfig.lines.length + 1}`
      })
      geometryConfig.lines = [...geometryConfig.lines, nextLine]
      this.form.geometryConfig = geometryConfig
      this.activeLineId = nextLine.id
      this.geometryEditorMode = 'line'
      this.syncGeometryEditorState()
      this.drawPolygon()
    },

    handleSelectLine(lineId) {
      this.activeLineId = lineId || ''
      this.drawPolygon()
    },

    handleRemoveActiveLine() {
      if (!this.activeLineId) {
        return
      }
      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      geometryConfig.lines = geometryConfig.lines.filter(line => line.id !== this.activeLineId)
      this.form.geometryConfig = geometryConfig
      this.syncGeometryEditorState()
      this.drawPolygon()
    },

    drawPolygon() {
      const canvas = this.getPreviewCanvas()
      if (!canvas) {
        return
      }
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        return
      }
      ctx.clearRect(0, 0, canvas.width, canvas.height)

      const geometryConfig = this.normalizeGeometryConfig(this.form.geometryConfig, this.polygonPoints)
      ;(geometryConfig.regions || []).forEach(region => {
        const regionPoints = this.normalizePointList(region.points, 0)
        if (!regionPoints.length) {
          return
        }
        const points = regionPoints.map(point => ({
          x: point.x * canvas.width,
          y: point.y * canvas.height
        }))
        const isActive = region.id === this.activeRegionId && this.geometryEditorMode === 'region'

        ctx.strokeStyle = isActive ? '#409eff' : (region.primary ? '#67c23a' : '#909399')
        ctx.lineWidth = isActive ? 3 : 2
        ctx.beginPath()
        ctx.moveTo(points[0].x, points[0].y)
        for (let i = 1; i < points.length; i += 1) {
          ctx.lineTo(points[i].x, points[i].y)
        }
        if (points.length >= 3 && region.closed) {
          ctx.closePath()
        }
        ctx.stroke()

        ctx.fillStyle = isActive ? '#409eff' : (region.primary ? '#67c23a' : '#909399')
        points.forEach(point => {
          ctx.beginPath()
          ctx.arc(point.x, point.y, 4, 0, Math.PI * 2)
          ctx.fill()
        })

        ctx.save()
        const regionColor = isActive ? '#409eff' : (region.primary ? '#67c23a' : '#909399')
        const regionRuleTags = this.getGeometryRuleTagTexts('region', region.id)
        const regionLabel = `${region.name || '区域'}${region.primary ? ' [主]' : ''}${regionRuleTags.length ? ` | ${regionRuleTags.join('/')}` : ''}`
        this.drawCanvasTextLabel(ctx, regionLabel, points[0].x + 8, points[0].y - 8, regionColor)
        ctx.restore()
      })

      ;(geometryConfig.lines || []).forEach(line => {
        const linePoints = this.normalizePointList(line.points, 0).slice(0, 2)
        if (!linePoints.length) {
          return
        }
        const canvasPoints = linePoints.map(point => ({
          x: point.x * canvas.width,
          y: point.y * canvas.height
        }))
        const isActive = line.id === this.activeLineId && this.geometryEditorMode === 'line'
        const crossLineDirections = this.getCrossLineDirectionsForLine(line.id)
        ctx.strokeStyle = isActive ? '#f56c6c' : '#e6a23c'
        ctx.fillStyle = isActive ? '#f56c6c' : '#e6a23c'
        ctx.lineWidth = isActive ? 3 : 2
        if (canvasPoints.length >= 2) {
          ctx.beginPath()
          ctx.moveTo(canvasPoints[0].x, canvasPoints[0].y)
          ctx.lineTo(canvasPoints[1].x, canvasPoints[1].y)
          ctx.stroke()
          this.drawCanvasLineArrow(ctx, canvasPoints[0], canvasPoints[1], isActive ? '#f56c6c' : '#e6a23c', ctx.lineWidth)
          this.drawCanvasCrossLineDirectionIndicator(ctx, canvasPoints[0], canvasPoints[1], crossLineDirections, isActive ? '#f56c6c' : '#e6a23c', ctx.lineWidth)
        }
        canvasPoints.forEach(point => {
          ctx.beginPath()
          ctx.arc(point.x, point.y, 5, 0, Math.PI * 2)
          ctx.fill()
        })
        if (canvasPoints.length >= 1) {
          ctx.save()
          const lineColor = isActive ? '#f56c6c' : '#e6a23c'
          const labelPoint = canvasPoints[Math.min(canvasPoints.length - 1, 0)]
          const lineRuleTags = this.getGeometryRuleTagTexts('line', line.id)
          const lineLabel = `${line.name || '线段'}${lineRuleTags.length ? ` | ${lineRuleTags.join('/')}` : ''}`
          this.drawCanvasTextLabel(ctx, lineLabel, labelPoint.x + 8, labelPoint.y - 8, lineColor)
          ctx.restore()
        }
      })

      this.drawDetectOverlay(ctx, canvas)
    },

    drawDetectOverlay(ctx, canvas) {
      if (!ctx || !canvas || !this.detectFrame) {
        return
      }

      const objects = Array.isArray(this.detectFrame.objects) ? this.detectFrame.objects : []
      if (!objects.length) {
        return
      }

      const sourceSize = this.detectFrame.sourceSize || {}
      const sourceWidth = Number(sourceSize.width || this.detectFrame.width || 0)
      const sourceHeight = Number(sourceSize.height || this.detectFrame.height || 0)
      if (!sourceWidth || !sourceHeight) {
        return
      }

      const videoRect = this.getVideoDisplayRect(canvas, sourceWidth, sourceHeight)
      if (!videoRect.width || !videoRect.height) {
        return
      }

      ctx.save()
      ctx.lineWidth = 2
      ctx.font = '12px sans-serif'
      ctx.textBaseline = 'top'

      objects.forEach(item => {
        const x1 = Number(item.x1)
        const y1 = Number(item.y1)
        const x2 = Number(item.x2)
        const y2 = Number(item.y2)
        if (![x1, y1, x2, y2].every(Number.isFinite)) {
          return
        }

        const left = videoRect.left + (x1 / sourceWidth) * videoRect.width
        const top = videoRect.top + (y1 / sourceHeight) * videoRect.height
        const width = ((x2 - x1) / sourceWidth) * videoRect.width
        const height = ((y2 - y1) / sourceHeight) * videoRect.height
        if (width <= 0 || height <= 0) {
          return
        }

        const happen = Boolean(item.happen)
        const strokeColor = happen ? '#f56c6c' : '#e6a23c'
        ctx.strokeStyle = strokeColor
        ctx.strokeRect(left, top, width, height)

        const className = item.className || 'object'
        const score = Number(item.score)
        const label = Number.isFinite(score)
          ? `${className} ${(score * 100).toFixed(1)}%`
          : `${className}`
        const labelWidth = Math.max(48, ctx.measureText(label).width + 10)
        const labelTop = Math.max(0, top - 18)
        ctx.fillStyle = strokeColor
        ctx.fillRect(left, labelTop, labelWidth, 16)
        ctx.fillStyle = '#ffffff'
        ctx.fillText(label, left + 5, labelTop + 2)
      })

      ctx.restore()
    },

    getVideoDisplayRect(canvas, fallbackWidth, fallbackHeight) {
      const video = this.getPreviewVideo()
      const canvasWidth = Number(canvas && canvas.width) || 0
      const canvasHeight = Number(canvas && canvas.height) || 0
      if (!canvasWidth || !canvasHeight) {
        return { left: 0, top: 0, width: 0, height: 0 }
      }

      const videoWidth = Number((video && video.videoWidth) || fallbackWidth || 0)
      const videoHeight = Number((video && video.videoHeight) || fallbackHeight || 0)
      if (!videoWidth || !videoHeight) {
        return { left: 0, top: 0, width: canvasWidth, height: canvasHeight }
      }

      const canvasRatio = canvasWidth / canvasHeight
      const videoRatio = videoWidth / videoHeight
      if (videoRatio > canvasRatio) {
        const width = canvasWidth
        const height = width / videoRatio
        return {
          left: 0,
          top: (canvasHeight - height) / 2,
          width,
          height
        }
      }

      const height = canvasHeight
      const width = height * videoRatio
      return {
        left: (canvasWidth - width) / 2,
        top: 0,
        width,
        height
      }
    },

    async handleSave() {
      if (this.saveLoading) {
        return
      }

      const valid = await new Promise(resolve => {
        this.$refs.deploymentForm.validate(passed => resolve(passed))
      })
      if (!valid) {
        return
      }

      const geometryConfig = this.buildPersistedGeometryConfig()
      if (!this.validateBehaviorRulesBeforeSave(geometryConfig)) {
        return
      }

      const payload = {
        taskName: this.form.taskName,
        deviceId: this.form.deviceId,
        algorithmCode: this.form.algorithmTasks[0] ? this.form.algorithmTasks[0].algorithmCode : '',
        algorithmName: this.form.algorithmTasks[0] ? this.form.algorithmTasks[0].algorithmName : '',
        algorithmTasks: this.form.algorithmTasks.map(item => ({
          algorithmCode: item.algorithmCode,
          algorithmName: item.algorithmName,
          detectFps: this.normalizeDetectFpsValue(item.detectFps),
          scoreThreshold: this.normalizeThresholdValue(item.scoreThreshold),
          nmsThreshold: this.normalizeThresholdValue(item.nmsThreshold),
          targetCodes: item.targetCodes
        })),
        pushEnabled: this.form.pushEnabled,
        frontendOverlayEnabled: this.form.pushEnabled ? false : this.form.frontendOverlayEnabled,
        recordEngine: this.form.recordEngine,
        alarmIntervalSec: Number(this.form.alarmIntervalSec),
        aiReviewEnabled: this.form.aiReviewEnabled,
        aiReviewPrompt: this.form.aiReviewPrompt,
        remark: this.form.remark,
        geometryConfig,
        streamUrl: this.streamUrl
      }

      this.saveLoading = true
      try {
        const isUpdate = !!this.deploymentId
        const response = isUpdate
          ? await updateDeployment(this.deploymentId, payload)
          : await createDeployment(payload)
        const hasCode = response && Object.prototype.hasOwnProperty.call(response, 'code')
        const code = hasCode ? Number(response.code) : null
        const payloadData = response && response.data && typeof response.data === 'object' ? response.data : {}
        const responseDeploymentId =
          payloadData.deploymentId ||
          (response && response.deploymentId) ||
          ''
        if (code !== null && code !== 200) {
          throw new Error((response && response.msg) || (isUpdate ? '更新返回结果异常' : '保存返回结果异常'))
        }

        if (!isUpdate) {
          if (!responseDeploymentId) {
            throw new Error((response && response.msg) || '保存返回结果异常')
          }
          this.deploymentId = responseDeploymentId
          this.$message.success(`保存成功，deploymentId: ${this.deploymentId}`)
          this.syncInitialSnapshot()
        } else {
          const nextStatus = String(payloadData.status || this.form.runtimeStatus || '').toUpperCase()
          this.form.runtimeStatus = nextStatus
          if (nextStatus === 'RUNNING') {
            this.$message.warning('更新成功，运行中的布控需手动停止并重新启动后，新的配置才会生效')
          } else {
            this.$message.success('更新成功')
          }
          this.syncInitialSnapshot()
        }
      } catch (error) {
        this.$message.error((error && error.message) || (this.deploymentId ? '更新失败，请稍后重试' : '保存失败，请稍后重试'))
      } finally {
        this.saveLoading = false
      }
    }
  }
}
</script>

<style scoped>
.deployment-add-page {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  height: calc(100dvh - 84px);
  min-height: calc(100vh - 84px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--sva-text);
  overflow: hidden;
  padding: 12px 16px;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.workspace-title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1 1 220px;
}

.workspace-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.workspace-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(360px, 420px);
  gap: 12px;
  overflow: hidden;
}

.workspace-body.is-rules-tab {
  grid-template-columns: minmax(0, 1fr) minmax(420px, 560px);
}

.preview-pane,
.config-pane {
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--sva-border);
  border-radius: 8px;
  background: var(--sva-surface);
  padding: 12px;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.config-form,
.config-tabs {
  min-width: 0;
  width: 100%;
}

.config-block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--sva-text);
  margin-bottom: 8px;
}

.behavior-rule-form-item {
  margin-bottom: 0;
}

.behavior-rule-form-item /deep/ .el-form-item__content {
  margin-left: 0 !important;
  width: 100%;
}

.preview-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.preview-meta .video-rule-overlay,
.preview-meta .video-event-overlay {
  position: static;
  left: auto;
  right: auto;
  top: auto;
  width: auto;
  max-width: none;
  max-height: 168px;
  overflow: auto;
  pointer-events: auto;
  background: var(--sva-surface-2);
  border: 1px solid var(--sva-border);
  color: var(--sva-text);
}

.algorithm-number-input {
  width: 100%;
  min-width: 0;
}

.config-pane .algorithm-task-params-row {
  display: flex;
  flex-wrap: wrap;
}

.config-pane .algorithm-task-params-row .el-col {
  width: auto;
  flex: 1 1 148px;
  max-width: 100%;
  margin-bottom: 8px;
}

@media (max-width: 1100px) {
  .workspace-body,
  .workspace-body.is-rules-tab {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .deployment-add-page {
    height: auto;
    overflow: visible;
  }

}

@media (max-width: 720px) {
  .deployment-add-page {
    padding: 12px;
  }

  .preview-meta {
    grid-template-columns: 1fr;
  }
}

.page-title {
  font-size: clamp(16px, 1.4vw, 20px);
  font-weight: 600;
  color: var(--sva-text);
  margin-bottom: 0;
}

.card-header {
  font-size: 14px;
  font-weight: 600;
  color: var(--sva-text);
}

.deployment-id-panel {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 10px;
  background: var(--sva-surface-2);
  border: 1px solid var(--sva-border);
  border-radius: 4px;
}

.deployment-id-label {
  font-size: 12px;
  color: var(--sva-text-muted);
}

.left-card,
.right-card {
  margin-bottom: 16px;
}

.video-panel {
  width: 100%;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.video-rule-overlay {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--sva-surface-2);
  color: var(--sva-text);
}

.video-rule-overlay-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--sva-text);
}

.video-rule-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.video-rule-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.4;
  background: rgba(255, 255, 255, 0.12);
}

.video-rule-chip--line {
  border: 1px solid rgba(230, 162, 60, 0.55);
}

.video-rule-chip--region {
  border: 1px solid rgba(103, 194, 58, 0.55);
}

.video-rule-chip-type {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--sva-text);
}

.video-rule-chip-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: rgba(255, 255, 255, 0.88);
}

.video-rule-overlay-empty {
  font-size: 12px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.74);
}

.video-event-overlay {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--sva-surface-2);
  color: var(--sva-text);
}

.video-event-overlay-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--sva-text);
}

.video-event-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.video-event-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
}

.video-event-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.video-event-state {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 1.4;
  font-weight: 600;
}

.video-event-state--start {
  background: rgba(103, 194, 58, 0.24);
  color: #b7eb8f;
}

.video-event-state--end {
  background: rgba(245, 108, 108, 0.2);
  color: #ffb3b3;
}

.video-event-state--active {
  background: rgba(64, 158, 255, 0.22);
  color: #b3d8ff;
}

.video-event-time {
  font-size: 11px;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.7);
}

.video-event-item-text {
  font-size: 12px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.92);
  word-break: break-word;
}

.video-event-overlay-empty {
  font-size: 12px;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.74);
}

.algorithm-task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 8px;
}

.algorithm-task-item {
  padding: 10px;
  border: 1px solid var(--sva-border);
  border-radius: 4px;
  background: var(--sva-surface-2);
}

.algorithm-task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.algorithm-task-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--sva-text);
}

.algorithm-task-params-row {
  margin-top: 8px;
}

.algorithm-task-param-label {
  display: inline-flex;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1;
  color: var(--sva-text);
}

.algorithm-task-param-icon {
  margin-left: 4px;
  color: var(--sva-text-muted);
  cursor: help;
}

.event-orchestration-entry {
  display: inline-flex;
  align-items: center;
  margin: 0 10px;
  font-size: 12px;
  line-height: 1;
  color: var(--sva-text-muted);
  cursor: default;
  user-select: none;
}

.event-orchestration-entry.is-active {
  color: #409eff;
  cursor: pointer;
}

.event-orchestration-entry.is-active:hover {
  color: #66b1ff;
  text-decoration: underline;
}


@media (max-width: 1200px) {
  .deployment-add-page {
    min-width: 0;
  }

  .algorithm-task-params-row .el-col {
    margin-bottom: 8px;
  }
}
</style>
