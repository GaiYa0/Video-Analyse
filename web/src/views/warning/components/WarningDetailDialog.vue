<template>
  <el-dialog :title="title" :visible.sync="dialogVisible" width="1200px" append-to-body destroy-on-close @close="$emit('close')">
    <el-row>
      <el-col :span="15">
        <div class="grid-content bg-purple">
          <div class="block">
            <el-image v-if="detailsInfo.picture_absolute_url" :src="detailsInfo.picture_absolute_url"
                      :preview-src-list="[detailsInfo.picture_absolute_url]"></el-image>
            <div v-else>暂无抓拍</div>
          </div>
          <div class="detail-video-toolbar">
            <el-button size="mini" type="primary" icon="el-icon-video-play" :loading="detailVideoLoading"
                       @click="$emit('play-video')">
              {{ detailVideoVisible ? '重新加载视频证据' : '播放视频证据' }}
            </el-button>
          </div>
          <div v-if="detailVideoVisible" class="detail-video-panel">
            <player :viewProof="detailVideoVisible" :rtspUrl="rtspUrl" :inline="true"
                    @closeProof="$emit('close-video')" title="视频证据查看">
            </player>
          </div>
        </div>
      </el-col>

      <el-col :span="9">
        <div class="grid-content bg-purple-light">
          <el-descriptions class="margin-top" title="报警信息" :column="1" size="medium"
                           style="margin: 0px 0 35px 40px;">
            <el-descriptions-item v-if="showAlarmLevel" label="报警等级"> {{ detailsInfo.alarm_level_name }}</el-descriptions-item>
            <el-descriptions-item label="报警类型">
              <span
                v-if="showSleepBadge"
                class="alarm-type-badge"
                :class="{ 'is-sleep': isSleepType(detailsInfo.alarm_type_name) }"
              >{{ detailsInfo.alarm_type_name || '—' }}</span>
              <template v-else>{{ detailsInfo.alarm_type_name }}</template>
            </el-descriptions-item>
            <el-descriptions-item label="报警时间"> {{ detailsInfo.alarm_time }}</el-descriptions-item>
            <el-descriptions-item label="设备通道">
              <el-tag size="small"> {{ detailsInfo.device_name }}</el-tag>
            </el-descriptions-item>
            <template v-if="showSvaFields">
              <el-descriptions-item label="行为类型"> {{ getBehaviorTypeLabel(detailsInfo.sva_behavior_type) }}</el-descriptions-item>
              <el-descriptions-item label="规则ID"> {{ detailsInfo.sva_rule_id || '---' }}</el-descriptions-item>
              <el-descriptions-item label="区域名称"> {{ detailsInfo.sva_region_name || '---' }}</el-descriptions-item>
              <el-descriptions-item label="线段名称"> {{ detailsInfo.sva_line_name || '---' }}</el-descriptions-item>
              <el-descriptions-item label="跨线方向"> {{ getCrossingDirectionLabel(detailsInfo.sva_crossing_direction) }}</el-descriptions-item>
              <el-descriptions-item label="事件阶段"> {{ getEventStateLabel(detailsInfo.sva_event_state) }}</el-descriptions-item>
              <el-descriptions-item label="持续时长"> {{ formatDuration(detailsInfo.duration_ms) }}</el-descriptions-item>
              <el-descriptions-item v-if="isSleepPitchVisible(detailsInfo)" label="俯仰角"> {{ formatPitchDegree(detailsInfo.sva_pitch_degree) }}</el-descriptions-item>
              <el-descriptions-item label="结束时间"> {{ detailsInfo.end_time || '---' }}</el-descriptions-item>
            </template>
            <el-descriptions-item label="处理状态"> {{ isHandled(detailsInfo.is_handle) ? '已处理' : '未处理' }}
            </el-descriptions-item>
            <el-descriptions-item label="处理方式"> {{ isHandled(detailsInfo.is_handle) ? (detailsInfo.h_title || '---') : '---' }}
            </el-descriptions-item>
            <el-descriptions-item label="处理单位"> {{
                isHandled(detailsInfo.is_handle) ? detailsInfo.h_org_name : '---'
              }}
            </el-descriptions-item>
            <el-descriptions-item label="处理意见"> {{ isHandled(detailsInfo.is_handle) ? detailsInfo.h_remark : '---' }}
            </el-descriptions-item>
            <el-descriptions-item label="处理时间"> {{
                isHandled(detailsInfo.is_handle) ? detailsInfo.h_create_time : '---'
              }}
            </el-descriptions-item>
            <template v-if="showAiFields">
              <el-descriptions-item label="AI复核状态">
                <el-tag size="small" :type="getAiReviewStatusType(detailsInfo.ai_review_status, detailsInfo.ai_review_decision)">
                  {{ getAiReviewStatusLabel(detailsInfo.ai_review_status, detailsInfo.ai_review_decision) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="AI复核结论"> {{ getAiDecisionLabel(detailsInfo.ai_review_decision) }}
              </el-descriptions-item>
              <el-descriptions-item label="误报分数"> {{ formatAiScore(detailsInfo.ai_false_positive_score) }}
              </el-descriptions-item>
              <el-descriptions-item label="AI复核时间"> {{ detailsInfo.ai_review_time || '---' }}
              </el-descriptions-item>
              <el-descriptions-item label="AI摘要"> {{ detailsInfo.ai_review_summary || '---' }}
              </el-descriptions-item>
            </template>
          </el-descriptions>

          <el-alert
            v-if="isHandled(detailsInfo.is_handle) && !hasHandleDetail(detailsInfo)"
            title="该告警已标记为已处理，但未找到处理明细记录"
            type="warning"
            :closable="false"
            show-icon
            class="detail-handle-alert"
          />

          <div class="detail-solve-panel">
            <el-divider>处理报警</el-divider>
            <el-form :model="solveData" :rules="solveRules" label-width="80px" ref="solveForm">
              <el-form-item label="处理方式" prop="h_title">
                <el-radio-group v-model="solveData.h_title">
                  <el-radio label="确认"></el-radio>
                  <el-radio label="误报"></el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="处理意见" prop="h_remark">
                <el-input type="textarea" :rows="6" v-model="solveData.h_remark"/>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-col>
    </el-row>

    <div slot="footer" class="dialog-footer">
      <el-button v-if="detailsInfo.w_id || actionRowId" type="primary" @click="submit">提 交</el-button>
      <el-button plain @click="dialogVisible = false">关 闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
import player from '@/components/RTSPPlayer'
import { getBehaviorTypeLabel as resolveBehaviorTypeLabel, isKnownBehaviorType } from '@/utils/behaviorTypes'

export default {
  name: 'WarningDetailDialog',
  components: { player },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      default: '报警详情'
    },
    detailsInfo: {
      type: Object,
      default() {
        return {}
      }
    },
    solveData: {
      type: Object,
      default() {
        return { w_id: '', h_title: '', h_remark: '' }
      }
    },
    solveRules: {
      type: Object,
      default() {
        return {}
      }
    },
    detailVideoVisible: {
      type: Boolean,
      default: false
    },
    detailVideoLoading: {
      type: Boolean,
      default: false
    },
    rtspUrl: {
      type: String,
      default: ''
    },
    actionRowId: {
      type: [String, Number],
      default: ''
    },
    showSleepBadge: {
      type: Boolean,
      default: false
    },
    showSvaFields: {
      type: Boolean,
      default: false
    },
    showAiFields: {
      type: Boolean,
      default: false
    },
    showAlarmLevel: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible
      },
      set(value) {
        this.$emit('update:visible', value)
      }
    }
  },
  methods: {
    submit() {
      this.$refs.solveForm.validate((valid) => {
        if (valid) {
          this.$emit('submit')
        }
        return valid
      })
    },
    clearSolveValidate() {
      if (this.$refs.solveForm) {
        this.$refs.solveForm.clearValidate()
      }
    },
    isSleepType(name) {
      return String(name || '').indexOf('睡岗') !== -1
    },
    isSleepPitchVisible(detail = {}) {
      const behaviorType = String(detail.sva_behavior_type || '').trim()
      return this.isSleepType(detail.alarm_type_name)
        || behaviorType === 'SLEEP_ON_DUTY'
        || behaviorType === 'sleep_on_duty'
        || behaviorType === '睡岗'
    },
    formatPitchDegree(value) {
      if (value === undefined || value === null || value === '') {
        return '---'
      }
      const numericValue = Number(value)
      if (!Number.isFinite(numericValue)) {
        return '---'
      }
      return `${numericValue.toFixed(1)}°`
    },
    isHandled(value) {
      return String(value) === '1'
    },
    hasHandleDetail(detail = {}) {
      return !!(detail.h_title || detail.h_org_name || detail.h_remark || detail.h_create_time)
    },
    getAiReviewStatusLabel(status, decision) {
      if (!status) return '未复核'
      if (status === 'PENDING') return '待复核'
      if (status === 'RUNNING') return '复核中'
      if (status === 'FAILED') return '复核失败'
      if (status === 'SKIPPED') return '已跳过'
      if (status === 'SUCCESS') return this.getAiDecisionLabel(decision)
      return status
    },
    getAiReviewStatusType(status, decision) {
      if (!status) return 'info'
      if (status === 'PENDING' || status === 'RUNNING') return 'warning'
      if (status === 'FAILED') return 'danger'
      if (status === 'SKIPPED') return 'info'
      if (status === 'SUCCESS') {
        if (decision === 'false_alarm') return 'danger'
        if (decision === 'true_alarm') return 'success'
        return 'warning'
      }
      return 'info'
    },
    getAiDecisionLabel(decision) {
      if (decision === 'true_alarm') return '疑似真实告警'
      if (decision === 'false_alarm') return '疑似误报'
      if (decision === 'uncertain') return '待人工确认'
      return '---'
    },
    formatAiScore(score) {
      if (score === undefined || score === null || score === '') {
        return '---'
      }
      const numericScore = Number(score)
      if (!Number.isFinite(numericScore)) {
        return '---'
      }
      return numericScore.toFixed(2)
    },
    getBehaviorTypeLabel(behaviorType) {
      if (behaviorType === undefined || behaviorType === null || behaviorType === '') {
        return '---'
      }
      if (!isKnownBehaviorType(behaviorType)) {
        return '---'
      }
      return resolveBehaviorTypeLabel(behaviorType)
    },
    getEventStateLabel(eventState) {
      if (eventState === 'start') return '开始'
      if (eventState === 'update') return '持续'
      if (eventState === 'end') return '结束'
      return '---'
    },
    getCrossingDirectionLabel(direction) {
      if (direction === 'left_to_right') return '左到右'
      if (direction === 'right_to_left') return '右到左'
      if (direction === 'both') return '双向'
      if (direction === 'unknown') return '未知'
      return direction || '---'
    },
    formatDuration(durationMs) {
      if (durationMs === undefined || durationMs === null || durationMs === '') {
        return '---'
      }
      const duration = Number(durationMs)
      if (!Number.isFinite(duration) || duration < 0) {
        return '---'
      }
      if (duration < 1000) {
        return `${duration}ms`
      }
      const totalSeconds = Math.floor(duration / 1000)
      const hours = Math.floor(totalSeconds / 3600)
      const minutes = Math.floor((totalSeconds % 3600) / 60)
      const seconds = totalSeconds % 60
      const parts = []
      if (hours > 0) parts.push(`${hours}小时`)
      if (minutes > 0) parts.push(`${minutes}分`)
      if (seconds > 0 || parts.length === 0) parts.push(`${seconds}秒`)
      return parts.join('')
    }
  }
}
</script>

<style scoped>
.detail-video-toolbar {
  margin-top: 16px;
}

.detail-video-panel {
  margin-top: 16px;
}

.detail-solve-panel {
  margin-left: 40px;
  margin-right: 16px;
}

.detail-handle-alert {
  margin: 0 16px 16px 40px;
}
</style>
