<template>
  <div class="app-container deployment-add-page">
    <div class="page-title">布控管理</div>
    <el-row :gutter="16">
      <el-col :xs="24" :sm="24" :md="14" :lg="15">
        <el-card shadow="never" class="left-card">
          <div slot="header" class="card-header">实时流预览与区域绘制</div>
          <div class="video-panel">
            <div ref="videoWrapper" class="video-wrapper">
              <video
                ref="previewVideo"
                class="preview-video"
                muted
                playsinline
                @loadedmetadata="handleVideoLoaded"
              />
              <canvas
                ref="polygonCanvas"
                class="polygon-canvas"
                @click="handleCanvasClick"
                @dblclick.prevent="handleCanvasDblClick"
              />
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
            <div class="video-toolbar">
              <el-radio-group v-model="geometryEditorMode" size="mini" class="geometry-mode-switch">
                <el-radio-button label="region">区域</el-radio-button>
                <el-radio-button label="line">线段</el-radio-button>
              </el-radio-group>
              <el-button size="mini" @click="handleAlignCurrentGeometry">{{ geometryEditorMode === 'line' ? '线段对齐' : '区域对齐' }}</el-button>
              <el-button size="mini" type="warning" plain @click="handleClearCurrentGeometry">{{ geometryEditorMode === 'line' ? '清空当前线段' : '清空当前区域' }}</el-button>
              <template v-if="geometryEditorMode === 'region'">
                <el-button size="mini" type="primary" plain @click="handleAddRegion">新增区域</el-button>
                <el-select
                  :value="activeRegionId"
                  size="mini"
                  class="region-select"
                  placeholder="请选择区域"
                  clearable
                  @change="handleSelectRegion"
                >
                  <el-option
                    v-for="item in regionOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-button size="mini" plain :disabled="!activeRegionId || activeRegionIsPrimary" @click="handleSetActivePrimary">设为主区域</el-button>
                <el-button size="mini" type="danger" plain :disabled="!activeRegionId" @click="handleRemoveActiveRegion">删除当前区域</el-button>
              </template>
              <template v-if="geometryEditorMode === 'line'">
                <el-button size="mini" type="primary" plain @click="handleAddLine">新增线段</el-button>
                <el-select
                  v-model="activeLineId"
                  size="mini"
                  class="line-select"
                  placeholder="请选择线段"
                  clearable
                  @change="handleSelectLine"
                >
                  <el-option
                    v-for="item in lineOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-button size="mini" type="danger" plain :disabled="!activeLineId" @click="handleRemoveActiveLine">删除当前线段</el-button>
              </template>
              <span class="point-count">点位数：{{ polygonPoints.length }}</span>
              <span class="polygon-state">{{ polygonClosed ? '已闭合' : '未闭合' }}</span>
              <span class="geometry-state">统一几何配置：{{ geometryRegionCount }} 区域 / {{ geometryLineCount }} 线段</span>
              <span class="primary-region-state">主区域：{{ primaryRegionLabel }}</span>
              <span class="geometry-editor-hint">{{ geometryEditorHint }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="10" :lg="9">
        <el-card shadow="never" class="right-card">
          <div slot="header" class="card-header">布控配置</div>
          <div v-if="deploymentId" class="deployment-id-panel">
            <span class="deployment-id-label">任务号</span>
            <el-tag size="small" type="success" effect="plain">{{ deploymentId }}</el-tag>
            <el-button size="mini" type="text" @click="handleCopyDeploymentId">复制</el-button>
          </div>
          <el-form ref="deploymentForm" :model="form" :rules="rules" label-width="110px" size="small">
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
                        style="width: 100%;"
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
                        v-model="task.scoreThreshold"
                        :min="0"
                        :max="1"
                        :step="0.05"
                        :precision="2"
                        controls-position="right"
                        style="width: 100%;"
                      />
                    </el-col>
                    <el-col :xs="24" :sm="8">
                      <div class="algorithm-task-param-label">
                        <span>NMS</span>
                        <el-tooltip content="NMS 阈值范围 0-1，当前默认值会按所选算法自动带出。" placement="top">
                          <i class="el-icon-question algorithm-task-param-icon" />
                        </el-tooltip>
                      </div>
                      <el-input-number
                        v-model="task.nmsThreshold"
                        :min="0"
                        :max="1"
                        :step="0.05"
                        :precision="2"
                        controls-position="right"
                        style="width: 100%;"
                      />
                    </el-col>
                  </el-row>
                </div>
              </div>
              <el-button size="mini" type="primary" plain icon="el-icon-plus" @click="handleAddAlgorithmTask">添加算法</el-button>
            </el-form-item>

            <el-form-item label="行为规则">
              <div class="behavior-rule-toolbar">
                <span class="behavior-rule-hint">可配置跨线、进区、出区、停留、低速、徘徊、睡觉、缺席、数量阈值、占用、区域运动、定向通行、逆向通行、目标接近、目标远离；区域类规则可绑定任一区域</span>
                <el-button size="mini" type="primary" plain icon="el-icon-plus" @click="handleAddBehaviorRule">新增规则</el-button>
              </div>
              <div v-if="behaviorRuleList.length" class="behavior-rule-list">
                <div v-if="standaloneBehaviorRules.length" class="behavior-rule-section">
                  <div class="behavior-rule-section-header">
                    <span class="behavior-rule-section-title">普通规则</span>
                    <span class="behavior-rule-section-meta">{{ standaloneBehaviorRules.length }} 条</span>
                  </div>
                  <div
                    v-for="rule in standaloneBehaviorRules"
                    :key="rule.id"
                    class="behavior-rule-item behavior-rule-item--standalone"
                  >
                    <div class="behavior-rule-header">
                      <div class="behavior-rule-grid-row behavior-rule-grid-row--first">
                        <div class="behavior-rule-header-info behavior-rule-header-info--col">
                          <div class="behavior-rule-select-field behavior-rule-effective-type-field">
                            <div class="behavior-rule-field-label behavior-rule-field-label--compact behavior-rule-effective-type-label-row">
                              <span class="behavior-rule-effective-type-value-inline">{{ getBehaviorRuleEffectiveAlarmTypeName(rule) }}</span>
                              <span>生效告警类型</span>
                            </div>
                            <el-input
                              class="behavior-rule-event-input"
                              :value="rule.customEventName"
                              :placeholder="getBehaviorRuleEventNamePlaceholder(rule)"
                              clearable
                              @input="value => handleBehaviorRuleCustomEventNameChange(rule.id, value)"
                            />
                          </div>
                        </div>
                        <div class="behavior-rule-header-info behavior-rule-header-info--col behavior-rule-header-target">
                          <div class="behavior-rule-select-field">
                            <div class="behavior-rule-field-label behavior-rule-field-label--compact">规则目标</div>
                            <el-select
                              v-if="isBehaviorRuleObjectVisible(rule.behaviorType)"
                              :value="rule.ruleObjectCode"
                              placeholder="规则目标"
                              clearable
                              filterable
                              allow-create
                              default-first-option
                              @change="value => handleBehaviorRuleObjectChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in getBehaviorRuleObjectOptions(rule)"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                              />
                            </el-select>
                            <el-select
                              v-else-if="isRelationalBehaviorType(rule.behaviorType)"
                              :value="rule.subjectObject"
                              placeholder="规则目标"
                              clearable
                              filterable
                              allow-create
                              default-first-option
                              @change="value => handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in behaviorObjectOptions"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                              />
                            </el-select>
                            <div v-else class="behavior-rule-summary">当前规则类型不需要规则目标</div>
                          </div>
                        </div>
                        <div class="behavior-rule-actions behavior-rule-actions--switch-only behavior-rule-actions--icon-group">
                          <el-tooltip :content="rule.enabled ? '停用' : '启用'" placement="top">
                            <el-button
                              type="text"
                              icon="el-icon-switch-button"
                              class="behavior-rule-action-icon"
                              :class="{ 'behavior-rule-action-icon--active': rule.enabled }"
                              @click="handleBehaviorRuleEnabledChange(rule.id, !rule.enabled)"
                            />
                          </el-tooltip>
                          <el-tooltip v-if="canUpgradeBehaviorRuleToSequence(rule)" content="组成序列规则" placement="top">
                            <el-button
                              type="text"
                              icon="el-icon-connection"
                              class="behavior-rule-action-icon"
                              @click="handleUpgradeBehaviorRuleToSequence(rule.id)"
                            />
                          </el-tooltip>
                          <el-tooltip content="删除规则" placement="top">
                            <el-button
                              type="text"
                              icon="el-icon-delete"
                              class="behavior-rule-action-icon behavior-rule-action-icon--danger"
                              @click="handleRemoveBehaviorRule(rule.id)"
                            />
                          </el-tooltip>
                        </div>
                      </div>
                      <div class="behavior-rule-grid-row behavior-rule-grid-row--second">
                        <div class="behavior-rule-header-info behavior-rule-header-info--col">
                          <div class="behavior-rule-select-field">
                            <div class="behavior-rule-field-label behavior-rule-field-label--compact">规则类型</div>
                            <el-select
                              :value="rule.behaviorType"
                              placeholder="请选择行为"
                              @change="value => handleBehaviorRuleTypeChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in getBehaviorTypeOptionsForRule(rule)"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                              />
                            </el-select>
                          </div>
                        </div>
                        <div class="behavior-rule-header-info behavior-rule-header-info--col">
                          <div class="behavior-rule-select-field">
                            <div class="behavior-rule-field-label behavior-rule-field-label--compact">绑定区域</div>
                            <el-select
                              :value="rule.geometryId"
                              :placeholder="getBehaviorRuleGeometryPlaceholder(rule)"
                              :disabled="!getBehaviorRuleGeometryOptions(rule).length"
                              @change="value => handleBehaviorRuleGeometryChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in getBehaviorRuleGeometryOptions(rule)"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                              />
                            </el-select>
                          </div>
                        </div>
                        <div class="behavior-rule-header-info behavior-rule-header-info--col behavior-rule-header-output-mode">
                          <div class="behavior-rule-select-field">
                            <div class="behavior-rule-field-label behavior-rule-field-label--compact">输出模式</div>
                            <div class="behavior-rule-output-mode-row">
                              <el-select
                                :value="rule.outputMode"
                                placeholder="输出模式"
                                @change="value => handleBehaviorRuleOutputModeChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in outputModeOptions"
                                  :key="item.value"
                                  :label="item.label"
                                  :value="item.value"
                                />
                              </el-select>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <el-row :gutter="8" class="behavior-rule-subrow">
                      <el-col v-if="isBehaviorRuleDirectionVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">穿越方向</div>
                        <div class="behavior-rule-direction-toggle-row">
                          <el-button
                            size="mini"
                            plain
                            @click="handleBehaviorRuleDirectionToggle(rule.id)"
                          >{{ getCrossLineDirectionButtonText(rule.direction) }}</el-button>
                          <span class="behavior-rule-direction-hint">点击切换图上穿越示意</span>
                        </div>
                      </el-col>
                      <el-col v-if="shouldShowSequenceSubjectObjectField(rule) && !isRelationalBehaviorType(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">主体目标</div>
                        <el-select
                          :value="rule.subjectObject"
                          placeholder="主体目标"
                          clearable
                          filterable
                          allow-create
                          default-first-option
                          @change="value => handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in behaviorObjectOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="isBehaviorRuleTargetObjectVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">目标对象</div>
                        <el-select
                          :value="rule.targetObject"
                          placeholder="目标对象"
                          clearable
                          filterable
                          allow-create
                          default-first-option
                          @change="value => handleBehaviorRuleTargetObjectChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in behaviorObjectOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="isBehaviorRuleDistanceVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">{{ getBehaviorRuleDistanceFieldLabel(rule.behaviorType) }}</div>
                        <el-input-number
                          :value="rule.distanceThresholdPx"
                          :min="getBehaviorRuleDistanceInputConfig(rule.behaviorType).min"
                          :max="getBehaviorRuleDistanceInputConfig(rule.behaviorType).max"
                          :step="getBehaviorRuleDistanceInputConfig(rule.behaviorType).step"
                          :precision="getBehaviorRuleDistanceInputConfig(rule.behaviorType).precision"
                          controls-position="right"
                          @change="value => handleBehaviorRuleDistanceChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段序号</div>
                        <el-input-number
                          :value="rule.stageIndex"
                          :min="0"
                          :max="32"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => handleBehaviorRuleStageIndexChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段逻辑</div>
                        <el-select
                          :value="rule.logicMode"
                          placeholder="阶段逻辑"
                          @change="value => handleBehaviorRuleLogicModeChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in sequenceLogicModeOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段超时(ms)</div>
                        <el-input-number
                          :value="rule.stageTimeoutMs"
                          :min="0"
                          :max="3600000"
                          :step="100"
                          :precision="0"
                          controls-position="right"
                          @change="value => handleBehaviorRuleStageTimeoutChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段保持(ms)</div>
                        <el-input-number
                          :value="rule.stageHoldMs"
                          :min="0"
                          :max="3600000"
                          :step="100"
                          :precision="0"
                          controls-position="right"
                          @change="value => handleBehaviorRuleStageHoldChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleDirectionAngleVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">目标方向角(°)</div>
                        <el-input-number
                          :value="rule.directionAngleDeg"
                          :min="0"
                          :max="359"
                          :step="5"
                          :precision="0"
                          :disabled="isBehaviorRuleDirectionAngleLocked(rule)"
                          controls-position="right"
                          @change="value => handleBehaviorRuleDirectionAngleChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleDirectionLineVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">参考线段</div>
                        <el-select
                          :value="rule.directionLineId"
                          placeholder="选线段自动带入"
                          clearable
                          :disabled="!lineOptions.length"
                          @change="value => handleBehaviorRuleDirectionLineChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in lineOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="isBehaviorRuleDirectionToleranceVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">角度容差(°)</div>
                        <el-input-number
                          :value="rule.directionToleranceDeg"
                          :min="1"
                          :max="180"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => handleBehaviorRuleDirectionToleranceChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleThresholdVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">持续时长(ms)</div>
                        <el-input-number
                          :value="rule.thresholdMs"
                          :min="getBehaviorRuleThresholdMin(rule.behaviorType)"
                          :max="3600000"
                          :step="1000"
                          :precision="0"
                          controls-position="right"
                          @change="value => handleBehaviorRuleThresholdChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleThresholdCountVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">数量阈值</div>
                        <el-input-number
                          :value="rule.thresholdCount"
                          :min="1"
                          :max="100000"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => handleBehaviorRuleThresholdCountChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleMaxSpeedVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">最大速度(px/s)</div>
                        <el-input-number
                          :value="rule.maxSpeedPxPerSec"
                          :min="0.1"
                          :max="10000"
                          :step="0.5"
                          :precision="1"
                          controls-position="right"
                          @change="value => handleBehaviorRuleMaxSpeedChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="isBehaviorRuleMaxDisplacementVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">最大位移(px)</div>
                        <el-input-number
                          :value="rule.maxDisplacementPx"
                          :min="1"
                          :max="10000"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => handleBehaviorRuleMaxDisplacementChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col :span="getBehaviorRuleSummarySpan(rule)">
                        <div class="behavior-rule-summary">
                          {{ getBehaviorRuleSummary(rule) }}
                        </div>
                      </el-col>
                    </el-row>
                  </div>
                </div>
                <div v-if="sequenceRuleGroups.length" class="behavior-rule-section behavior-rule-section--sequence">
                  <div class="behavior-rule-section-header">
                    <span class="behavior-rule-section-title">多阶段规则组</span>
                    <span class="behavior-rule-section-meta">{{ sequenceRuleGroups.length }} 组 / {{ sequenceGroupedRuleCount }} 条</span>
                  </div>
                  <div
                    v-for="(group, groupIndex) in sequenceRuleGroups"
                    :key="group.sequenceId"
                    :class="getSequenceGroupToneClass(groupIndex)"
                    class="behavior-sequence-group"
                  >
                    <div class="behavior-sequence-group-header">
                      <div class="behavior-sequence-group-row behavior-sequence-group-row--first">
                        <div class="behavior-sequence-group-title-line">
                          <div class="behavior-sequence-group-title">多阶段规则组 {{ groupIndex + 1 }}</div>
                          <div class="behavior-sequence-group-meta behavior-sequence-group-meta--inline">主体目标 {{ getSequenceGroupSubjectLabel(group) }}</div>
                        </div>
                        <el-tooltip content="新增阶段" placement="top">
                          <el-button
                            type="text"
                            icon="el-icon-connection"
                            class="behavior-rule-action-icon"
                            @click="handleAddSequenceStage(group.sequenceId)"
                          />
                        </el-tooltip>
                      </div>
                      <div class="behavior-sequence-group-row behavior-sequence-group-row--second">
                        <div class="behavior-sequence-group-field">
                          <div class="behavior-rule-field-label">告警类型</div>
                          <el-input
                            :value="getSequenceGroupCustomEventName(group)"
                            placeholder="留空则使用默认告警类型"
                            clearable
                            @input="value => handleSequenceGroupCustomEventNameChange(group.sequenceId, value)"
                          />
                        </div>
                        <div class="behavior-sequence-group-field">
                          <div class="behavior-rule-field-label">输出模式</div>
                          <el-select
                            :value="getSequenceGroupOutputMode(group)"
                            placeholder="输出模式"
                            @change="value => handleSequenceGroupOutputModeChange(group.sequenceId, value)"
                          >
                            <el-option
                              v-for="item in outputModeOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </div>
                      </div>
                      <div class="behavior-sequence-group-row behavior-sequence-group-row--third">
                        <div class="behavior-sequence-group-meta behavior-sequence-group-meta--summary">{{ getBehaviorRuleSequenceGroupSummary(group) }}</div>
                      </div>
                    </div>
                    <div
                      v-for="rule in group.rules"
                      :key="rule.id"
                      :class="getSequenceStageToneClass(rule)"
                      class="behavior-rule-item behavior-rule-item--grouped"
                    >
                      <div class="behavior-rule-header">
                        <div class="behavior-rule-title">{{ getBehaviorRuleDisplayTitle(rule) }}</div>
                        <div class="behavior-rule-grid-row behavior-rule-grid-row--first behavior-rule-grid-row--sequence">
                          <div class="behavior-rule-grid-placeholder" />
                          <div class="behavior-rule-grid-placeholder" />
                          <div class="behavior-rule-actions behavior-rule-actions--switch-only behavior-rule-actions--icon-group">
                            <el-tooltip :content="rule.enabled ? '停用' : '启用'" placement="top">
                              <el-button
                                type="text"
                                icon="el-icon-switch-button"
                                class="behavior-rule-action-icon"
                                :class="{ 'behavior-rule-action-icon--active': rule.enabled }"
                                @click="handleBehaviorRuleEnabledChange(rule.id, !rule.enabled)"
                              />
                            </el-tooltip>
                            <el-tooltip content="删除规则" placement="top">
                              <el-button
                                type="text"
                                icon="el-icon-delete"
                                class="behavior-rule-action-icon behavior-rule-action-icon--danger"
                                @click="handleRemoveBehaviorRule(rule.id)"
                              />
                            </el-tooltip>
                          </div>
                        </div>
                        <div class="behavior-rule-grid-row behavior-rule-grid-row--second">
                          <div class="behavior-rule-header-info behavior-rule-header-info--col">
                            <div class="behavior-rule-select-field">
                              <div class="behavior-rule-field-label behavior-rule-field-label--compact">规则目标</div>
                              <el-select
                                v-if="shouldShowSequenceRuleObjectField(rule) && isSequenceLeadRule(rule)"
                                :value="rule.ruleObjectCode"
                                placeholder="规则目标"
                                clearable
                                filterable
                                allow-create
                                default-first-option
                                @change="value => handleBehaviorRuleObjectChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in getBehaviorRuleObjectOptions(rule)"
                                  :key="item.value"
                                  :label="item.label"
                                  :value="item.value"
                                />
                              </el-select>
                              <el-select
                                v-else-if="isRelationalBehaviorType(rule.behaviorType) && isSequenceLeadRule(rule)"
                                :value="rule.subjectObject"
                                placeholder="规则目标"
                                clearable
                                filterable
                                allow-create
                                default-first-option
                                @change="value => handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in behaviorObjectOptions"
                                  :key="item.value"
                                  :label="item.label"
                                  :value="item.value"
                                />
                              </el-select>
                              <div v-else class="behavior-rule-summary">主体目标：{{ getSequenceGroupSubjectLabelByRule(rule) }}（继承）</div>
                            </div>
                          </div>
                          <div class="behavior-rule-header-info behavior-rule-header-info--col">
                            <div class="behavior-rule-select-field">
                              <div class="behavior-rule-field-label behavior-rule-field-label--compact">规则类型</div>
                              <el-select
                                :value="rule.behaviorType"
                                placeholder="请选择行为"
                                @change="value => handleBehaviorRuleTypeChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in getBehaviorTypeOptionsForRule(rule)"
                                  :key="item.value"
                                  :label="item.label"
                                  :value="item.value"
                                />
                              </el-select>
                            </div>
                          </div>
                          <div class="behavior-rule-header-info behavior-rule-header-info--col">
                            <div class="behavior-rule-select-field">
                              <div class="behavior-rule-field-label behavior-rule-field-label--compact">绑定区域</div>
                              <div class="behavior-rule-output-mode-row">
                                <el-select
                                  :value="rule.geometryId"
                                  :placeholder="getBehaviorRuleGeometryPlaceholder(rule)"
                                  :disabled="!getBehaviorRuleGeometryOptions(rule).length"
                                  @change="value => handleBehaviorRuleGeometryChange(rule.id, value)"
                                >
                                  <el-option
                                    v-for="item in getBehaviorRuleGeometryOptions(rule)"
                                    :key="item.value"
                                    :label="item.label"
                                    :value="item.value"
                                  />
                                </el-select>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                      <el-row :gutter="8" class="behavior-rule-subrow">
                        <el-col v-if="isBehaviorRuleDirectionVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">穿越方向</div>
                          <div class="behavior-rule-direction-toggle-row">
                            <el-button
                              size="mini"
                              plain
                              @click="handleBehaviorRuleDirectionToggle(rule.id)"
                            >{{ getCrossLineDirectionButtonText(rule.direction) }}</el-button>
                            <span class="behavior-rule-direction-hint">点击切换图上穿越示意</span>
                          </div>
                        </el-col>
                        <el-col v-if="shouldShowSequenceSubjectObjectField(rule) && !isRelationalBehaviorType(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">主体目标</div>
                          <el-select
                            :value="rule.subjectObject"
                            placeholder="主体目标"
                            clearable
                            filterable
                            allow-create
                            default-first-option
                            @change="value => handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in behaviorObjectOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="isBehaviorRuleTargetObjectVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">目标对象</div>
                          <el-select
                            :value="rule.targetObject"
                            placeholder="目标对象"
                            clearable
                            filterable
                            allow-create
                            default-first-option
                            @change="value => handleBehaviorRuleTargetObjectChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in behaviorObjectOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="isBehaviorRuleDistanceVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">{{ getBehaviorRuleDistanceFieldLabel(rule.behaviorType) }}</div>
                          <el-input-number
                            :value="rule.distanceThresholdPx"
                            :min="getBehaviorRuleDistanceInputConfig(rule.behaviorType).min"
                            :max="getBehaviorRuleDistanceInputConfig(rule.behaviorType).max"
                            :step="getBehaviorRuleDistanceInputConfig(rule.behaviorType).step"
                            :precision="getBehaviorRuleDistanceInputConfig(rule.behaviorType).precision"
                            controls-position="right"
                            @change="value => handleBehaviorRuleDistanceChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段序号</div>
                          <el-input-number
                            :value="rule.stageIndex"
                            :min="0"
                            :max="32"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => handleBehaviorRuleStageIndexChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="shouldShowSequenceStageLogicField(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段逻辑</div>
                          <el-select
                            :value="rule.logicMode"
                            placeholder="阶段逻辑"
                            @change="value => handleBehaviorRuleLogicModeChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in sequenceLogicModeOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段超时(ms)</div>
                          <el-input-number
                            :value="rule.stageTimeoutMs"
                            :min="0"
                            :max="3600000"
                            :step="100"
                            :precision="0"
                            controls-position="right"
                            @change="value => handleBehaviorRuleStageTimeoutChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段保持(ms)</div>
                          <el-input-number
                            :value="rule.stageHoldMs"
                            :min="0"
                            :max="3600000"
                            :step="100"
                            :precision="0"
                            controls-position="right"
                            @change="value => handleBehaviorRuleStageHoldChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleDirectionAngleVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">目标方向角(°)</div>
                          <el-input-number
                            :value="rule.directionAngleDeg"
                            :min="0"
                            :max="359"
                            :step="5"
                            :precision="0"
                            :disabled="isBehaviorRuleDirectionAngleLocked(rule)"
                            controls-position="right"
                            @change="value => handleBehaviorRuleDirectionAngleChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleDirectionLineVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">参考线段</div>
                          <el-select
                            :value="rule.directionLineId"
                            placeholder="选线段自动带入"
                            clearable
                            :disabled="!lineOptions.length"
                            @change="value => handleBehaviorRuleDirectionLineChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in lineOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="isBehaviorRuleDirectionToleranceVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">角度容差(°)</div>
                          <el-input-number
                            :value="rule.directionToleranceDeg"
                            :min="1"
                            :max="180"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => handleBehaviorRuleDirectionToleranceChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleThresholdVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">持续时长(ms)</div>
                          <el-input-number
                            :value="rule.thresholdMs"
                            :min="getBehaviorRuleThresholdMin(rule.behaviorType)"
                            :max="3600000"
                            :step="1000"
                            :precision="0"
                            controls-position="right"
                            @change="value => handleBehaviorRuleThresholdChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleThresholdCountVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">数量阈值</div>
                          <el-input-number
                            :value="rule.thresholdCount"
                            :min="1"
                            :max="100000"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => handleBehaviorRuleThresholdCountChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleMaxSpeedVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">最大速度(px/s)</div>
                          <el-input-number
                            :value="rule.maxSpeedPxPerSec"
                            :min="0.1"
                            :max="10000"
                            :step="0.5"
                            :precision="1"
                            controls-position="right"
                            @change="value => handleBehaviorRuleMaxSpeedChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="isBehaviorRuleMaxDisplacementVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">最大位移(px)</div>
                          <el-input-number
                            :value="rule.maxDisplacementPx"
                            :min="1"
                            :max="10000"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => handleBehaviorRuleMaxDisplacementChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col :span="getBehaviorRuleSummarySpan(rule)">
                          <div class="behavior-rule-summary">
                            {{ getBehaviorRuleSummary(rule) }}
                          </div>
                        </el-col>
                      </el-row>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else class="behavior-rule-empty">暂无行为规则，添加后会随 geometryConfig 一并保存</div>
            </el-form-item>

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

            <el-form-item>
              <el-button @click="handleCreateNew">新建布控</el-button>
              <span
                class="event-orchestration-entry"
                :class="{ 'is-active': eventOrchestrationEntryEnabled }"
                @click="handleOpenEventOrchestration"
              >事件编排（可选）</span>
              <el-button type="primary" :loading="saveLoading" @click="handleSave">{{ saveButtonText }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import flvjs from 'flv.js'
import { getDeviceList, previewDeviceMonitor } from '@/api/device'
import { getAlgorithmList, getAlgorithmTargets } from '@/api/algorithm'
import { createDeployment, getDeploymentDetail, updateDeployment, updateDeploymentLiveOutput } from '@/api/deployment'
import { OVERLAY_DELAY_DEFAULT_MS, loadOverlayDelayMs } from '@/utils/systemRuntimeConfig'

export default {
  name: 'DeploymentAdd',
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
      flvPlayer: null,
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
      recentDetectEvents: []
    }
  },
  computed: {
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
      return [
        { value: 'cross_line', label: '跨线' },
        { value: 'enter_region', label: '进区' },
        { value: 'exit_region', label: '出区' },
        { value: 'dwell', label: '停留' },
        { value: 'low_speed', label: '低速' },
        { value: 'loitering', label: '徘徊' },
        { value: 'sleep', label: '睡觉' },
        { value: 'absence', label: '缺席' },
        { value: 'count_threshold', label: '数量阈值' },
        { value: 'occupancy', label: '占用' },
        { value: 'region_motion', label: '区域运动' },
        { value: 'direction_move', label: '定向通行' },
        { value: 'direction_reverse', label: '逆向通行' },
        { value: 'relation_near', label: '目标接近' },
        { value: 'relation_apart', label: '目标远离' },
        { value: 'relation_not_contains', label: '目标未包含' }
      ]
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
    }
  },
  mounted() {
    this.initPageData()
    window.addEventListener('resize', this.syncCanvasSize)
    window.addEventListener('sva:detect-frame', this.handleDetectFramePush)
    window.addEventListener('sva:detect-event', this.handleDetectEventPush)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.syncCanvasSize)
    window.removeEventListener('sva:detect-frame', this.handleDetectFramePush)
    window.removeEventListener('sva:detect-event', this.handleDetectEventPush)
    this.clearDetectFrame(false)
    this.destroyPlayer()
  },
  methods: {
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
          return {
            value: apeId,
            label: `${name} (${apeId})`,
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
          const liveOutputResponse = await updateDeploymentLiveOutput(this.deploymentId, {
            videoEnabled: true,
            liveEventEnabled: true,
            wsEventFps: 8
          })
          const liveOutputData = (liveOutputResponse && liveOutputResponse.data) || liveOutputResponse || {}
          const algorithmStreamUrl = this.getFieldValue(liveOutputData, 'algorithmStreamUrl', 'algorithm_stream_url') || ''
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

    createEmptyGeometryConfig() {
      return {
        regions: [],
        lines: [],
        behaviorRules: []
      }
    },

    normalizeBehaviorRule(rule, index = 0) {
      return this.normalizeBehaviorRuleWithGeometry(rule, index, this.createEmptyGeometryConfig())
    },

    normalizeBehaviorType(value) {
      if ([
        'cross_line',
        'enter_region',
        'exit_region',
        'dwell',
        'low_speed',
        'loitering',
        'sleep',
        'absence',
        'count_threshold',
        'occupancy',
        'region_motion',
        'direction_move',
        'direction_reverse',
        'relation_near',
        'relation_apart',
        'relation_not_contains'
      ].includes(value)) {
        return value
      }
      return ''
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
      return ['relation_near', 'relation_apart', 'region_motion', 'sleep'].includes(behaviorType)
    },

    isBehaviorRuleThresholdVisible(behaviorType) {
      return ['dwell', 'absence', 'occupancy', 'region_motion', 'low_speed', 'loitering', 'sleep', 'count_threshold', 'direction_move', 'direction_reverse', 'relation_near', 'relation_apart', 'relation_not_contains'].includes(behaviorType)
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
      const nextIndex = this.regionSeed
      this.regionSeed += 1
      const isPrimary = Boolean(overrides.primary)
      return {
        id: overrides.id || (isPrimary ? 'region_primary' : `region_${nextIndex}`),
        name: overrides.name || (isPrimary ? '主区域' : `区域${nextIndex}`),
        type: 'polygon',
        primary: isPrimary,
        points: this.normalizePointList(overrides.points, 0),
        ...overrides
      }
    },

    createLineConfig(overrides = {}) {
      const nextIndex = this.lineSeed
      this.lineSeed += 1
      return {
        id: `line_${nextIndex}`,
        name: `线段${nextIndex}`,
        type: 'tripwire',
        direction: 'both',
        points: [],
        ...overrides
      }
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

    normalizeLineDirection(direction) {
      if (direction === 'left_to_right' || direction === 'right_to_left') {
        return direction
      }
      return 'both'
    },

    normalizeBehaviorRuleCustomEventName(value) {
      return String(value || '').trim()
    },

    getLineDirectionLabel(direction) {
      if (direction === 'left_to_right') {
        return '正向'
      }
      if (direction === 'right_to_left') {
        return '反向'
      }
      return '双向'
    },

    getNextLineDirection(direction) {
      if (direction === 'left_to_right') {
        return 'right_to_left'
      }
      if (direction === 'right_to_left') {
        return 'both'
      }
      return 'left_to_right'
    },

    getCrossLineDirectionButtonText(direction) {
      return `切换方向: ${this.getLineDirectionLabel(this.normalizeLineDirection(direction))}`
    },

    getBehaviorTypeLabel(behaviorType) {
      const matched = this.behaviorTypeOptions.find(item => item.value === behaviorType)
      return matched ? matched.label : behaviorType
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

    drawCanvasTextLabel(ctx, text, x, y, color) {
      if (!ctx || !text) {
        return
      }
      ctx.save()
      ctx.font = '12px sans-serif'
      const textWidth = ctx.measureText(text).width
      const paddingX = 6
      const labelHeight = 18
      const labelX = x
      const labelY = Math.max(0, y - labelHeight + 2)
      ctx.fillStyle = 'rgba(15, 17, 21, 0.68)'
      ctx.fillRect(labelX - 2, labelY, textWidth + paddingX * 2, labelHeight)
      ctx.fillStyle = color
      ctx.fillText(text, labelX + paddingX - 2, labelY + 3)
      ctx.restore()
    },

    drawCanvasLineArrow(ctx, startPoint, endPoint, color, lineWidth = 2) {
      if (!ctx || !startPoint || !endPoint) {
        return
      }
      const dx = Number(endPoint.x) - Number(startPoint.x)
      const dy = Number(endPoint.y) - Number(startPoint.y)
      const length = Math.sqrt(dx * dx + dy * dy)
      if (!Number.isFinite(length) || length < 12) {
        return
      }

      const angle = Math.atan2(dy, dx)
      const arrowSize = Math.max(8, Math.min(14, lineWidth * 4))
      const arrowAngle = Math.PI / 7
      const tipX = endPoint.x
      const tipY = endPoint.y
      const leftX = tipX - arrowSize * Math.cos(angle - arrowAngle)
      const leftY = tipY - arrowSize * Math.sin(angle - arrowAngle)
      const rightX = tipX - arrowSize * Math.cos(angle + arrowAngle)
      const rightY = tipY - arrowSize * Math.sin(angle + arrowAngle)

      ctx.save()
      ctx.fillStyle = color
      ctx.beginPath()
      ctx.moveTo(tipX, tipY)
      ctx.lineTo(leftX, leftY)
      ctx.lineTo(rightX, rightY)
      ctx.closePath()
      ctx.fill()
      ctx.restore()
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

    drawCanvasCrossLineDirectionIndicator(ctx, startPoint, endPoint, directions, color, lineWidth = 2) {
      if (!ctx || !startPoint || !endPoint || !Array.isArray(directions) || !directions.length) {
        return
      }
      const dx = Number(endPoint.x) - Number(startPoint.x)
      const dy = Number(endPoint.y) - Number(startPoint.y)
      const length = Math.sqrt(dx * dx + dy * dy)
      if (!Number.isFinite(length) || length < 16) {
        return
      }

      const normalX = -dy / length
      const normalY = dx / length
      const midX = (Number(startPoint.x) + Number(endPoint.x)) / 2
      const midY = (Number(startPoint.y) + Number(endPoint.y)) / 2
      const offset = Math.max(14, Math.min(24, length * 0.18))
      const arrowLength = Math.max(18, Math.min(30, length * 0.28))

      directions.forEach(direction => {
        const isLeftToRight = direction === 'left_to_right'
        const start = isLeftToRight
          ? { x: midX + normalX * offset, y: midY + normalY * offset }
          : { x: midX - normalX * offset, y: midY - normalY * offset }
        const end = isLeftToRight
          ? { x: midX - normalX * offset, y: midY - normalY * offset }
          : { x: midX + normalX * offset, y: midY + normalY * offset }
        const unitX = (end.x - start.x) / (Math.sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)) || 1)
        const unitY = (end.y - start.y) / (Math.sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)) || 1)
        const shortenedEnd = {
          x: start.x + unitX * arrowLength,
          y: start.y + unitY * arrowLength
        }

        ctx.save()
        ctx.strokeStyle = color
        ctx.lineWidth = Math.max(2, lineWidth)
        ctx.beginPath()
        ctx.moveTo(start.x, start.y)
        ctx.lineTo(shortenedEnd.x, shortenedEnd.y)
        ctx.stroke()
        ctx.restore()

        this.drawCanvasLineArrow(ctx, start, shortenedEnd, color, Math.max(2, lineWidth))
      })
    },

    parseGeometryConfigInput(geometryConfig) {
      let parsed = geometryConfig
      if (typeof parsed === 'string') {
        const trimmed = parsed.trim()
        if (!trimmed) {
          return null
        }
        try {
          parsed = JSON.parse(trimmed)
        } catch (error) {
          return null
        }
      }

      if (Array.isArray(parsed)) {
        return {
          regions: parsed,
          lines: []
        }
      }

      return parsed && typeof parsed === 'object' ? parsed : null
    },

    normalizePoint(point) {
      const x = Number(point && point.x)
      const y = Number(point && point.y)
      if (!Number.isFinite(x) || !Number.isFinite(y)) {
        return null
      }
      return {
        x: this.clamp01(Number(x.toFixed(6))),
        y: this.clamp01(Number(y.toFixed(6)))
      }
    },

    normalizePointList(points, minimumCount = 0) {
      if (!Array.isArray(points)) {
        return []
      }
      const normalized = points.map(point => this.normalizePoint(point)).filter(Boolean)
      return normalized.length >= minimumCount ? normalized : []
    },

    buildPrimaryRegion(points) {
      return {
        id: 'region_primary',
        name: '主区域',
        type: 'polygon',
        primary: true,
        closed: true,
        points: this.normalizePointList(points, 3)
      }
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

    normalizeRegionPrimaryState(regions, preferredPrimaryRegionId = '') {
      const normalizedRegions = (regions || []).map(region => ({
        ...region,
        closed: Boolean(region.closed),
        points: this.normalizePointList(region.points, 0)
      }))
      const currentPrimaryRegion = normalizedRegions.find(region => region.primary)
      const fallbackPrimaryRegion = normalizedRegions.find(region => region.id === 'region_primary') || normalizedRegions[0] || null
      const primaryRegionId =
        preferredPrimaryRegionId ||
        (currentPrimaryRegion ? currentPrimaryRegion.id : '') ||
        (fallbackPrimaryRegion ? fallbackPrimaryRegion.id : '')

      return normalizedRegions.map(region => ({
        ...region,
        primary: region.id === primaryRegionId
      }))
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
      this.syncCanvasSize()
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
      this.destroyPlayer()
      this.videoLoaded = false
      const video = this.$refs.previewVideo
      if (!video || !url) {
        return
      }

      const isFlv = /\.flv($|[?#])/i.test(url)
      const isHttpOrWs = /^(https?:\/\/|wss?:\/\/)/i.test(url)

      if (isFlv && isHttpOrWs && flvjs.isSupported()) {
        this.flvPlayer = flvjs.createPlayer({
          type: 'flv',
          url,
          isLive: true
        })
        this.flvPlayer.attachMediaElement(video)
        this.flvPlayer.load()
        this.flvPlayer.play().catch(() => {})
        return
      }

      video.src = url
      video.play().catch(() => {})
    },

    destroyPlayer() {
      const video = this.$refs.previewVideo
      if (this.flvPlayer) {
        this.flvPlayer.unload()
        this.flvPlayer.detachMediaElement()
        this.flvPlayer.destroy()
        this.flvPlayer = null
      }
      if (video) {
        video.pause()
        video.removeAttribute('src')
        video.load()
      }
      this.videoLoaded = false
    },

    syncCanvasSize() {
      const wrapper = this.$refs.videoWrapper
      const canvas = this.$refs.polygonCanvas
      if (!wrapper || !canvas) {
        return
      }
      const width = wrapper.clientWidth || 0
      const height = wrapper.clientHeight || 0
      if (!width || !height) {
        return
      }
      const oldWidth = canvas.width
      const oldHeight = canvas.height
      if (oldWidth !== width || oldHeight !== height) {
        canvas.width = width
        canvas.height = height
      }
      this.drawPolygon()
    },

    handleCanvasClick(event) {
      const canvas = this.$refs.polygonCanvas
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
      const canvas = this.$refs.polygonCanvas
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
      const video = this.$refs.previewVideo
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

    clamp01(value) {
      if (value < 0) return 0
      if (value > 1) return 1
      return value
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
  min-width: 980px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.card-header {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.deployment-id-panel {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 10px;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
}

.deployment-id-label {
  font-size: 12px;
  color: #606266;
}

.left-card,
.right-card {
  margin-bottom: 16px;
}

.video-panel {
  width: 100%;
}

.video-wrapper {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #0f1115;
  border-radius: 4px;
  overflow: hidden;
}

.preview-video,
.polygon-canvas {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.preview-video {
  object-fit: contain;
  background: #0f1115;
}

.polygon-canvas {
  z-index: 2;
  cursor: crosshair;
}

.video-rule-overlay {
  position: absolute;
  left: 12px;
  top: 12px;
  z-index: 3;
  max-width: min(360px, calc(100% - 24px));
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(15, 17, 21, 0.68);
  backdrop-filter: blur(8px);
  color: #f5f7fa;
  pointer-events: none;
}

.video-rule-overlay-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: #ffffff;
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
  color: #ffffff;
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
  position: absolute;
  right: 12px;
  top: 12px;
  z-index: 3;
  width: min(320px, calc(100% - 24px));
  max-height: calc(100% - 24px);
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(15, 17, 21, 0.72);
  backdrop-filter: blur(8px);
  color: #f5f7fa;
  pointer-events: none;
  overflow: hidden;
}

.video-event-overlay-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: #ffffff;
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

.video-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
  color: #606266;
  font-size: 12px;
}

.geometry-mode-switch,
.region-select,
.line-select {
  flex-shrink: 0;
}

.region-select,
.line-select {
  width: 180px;
}

.point-count,
.polygon-state,
.geometry-state,
.primary-region-state,
.geometry-editor-hint {
  line-height: 28px;
}

.algorithm-task-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 8px;
}

.algorithm-task-item {
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
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
  color: #303133;
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
  color: #606266;
}

.algorithm-task-param-icon {
  margin-left: 4px;
  color: #909399;
  cursor: help;
}

.event-orchestration-entry {
  display: inline-flex;
  align-items: center;
  margin: 0 10px;
  font-size: 12px;
  line-height: 1;
  color: #909399;
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

.behavior-rule-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.behavior-rule-hint {
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.behavior-rule-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.behavior-rule-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.behavior-rule-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.behavior-rule-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.behavior-rule-section-meta {
  font-size: 12px;
  color: #94a3b8;
}

.behavior-rule-item {
  position: relative;
  padding: 12px;
  border: 1px solid #e7edf5;
  border-radius: 8px;
  background: linear-gradient(180deg, #fcfdff 0%, #f8fafc 100%);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.behavior-rule-item--standalone {
  border-color: #d9e2ec;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.behavior-rule-item--standalone::before {
  content: '';
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 0;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, #94a3b8 0%, #cbd5e1 100%);
}

.behavior-sequence-group {
  --behavior-sequence-border: #dbe7f3;
  --behavior-sequence-background: linear-gradient(180deg, rgba(248, 250, 252, 0.94) 0%, rgba(241, 245, 249, 0.94) 100%);
  padding: 12px;
  border: 1px solid var(--behavior-sequence-border);
  border-radius: 12px;
  background: var(--behavior-sequence-background);
}

.behavior-sequence-group--tone-1 {
  --behavior-sequence-border: #d8e5f2;
  --behavior-sequence-background: linear-gradient(180deg, rgba(245, 249, 255, 0.96) 0%, rgba(236, 244, 252, 0.96) 100%);
}

.behavior-sequence-group--tone-2 {
  --behavior-sequence-border: #dde6da;
  --behavior-sequence-background: linear-gradient(180deg, rgba(247, 251, 246, 0.96) 0%, rgba(239, 246, 237, 0.96) 100%);
}

.behavior-sequence-group--tone-3 {
  --behavior-sequence-border: #eadfd3;
  --behavior-sequence-background: linear-gradient(180deg, rgba(252, 248, 243, 0.96) 0%, rgba(248, 241, 233, 0.96) 100%);
}

.behavior-sequence-group--tone-4 {
  --behavior-sequence-border: #e1dced;
  --behavior-sequence-background: linear-gradient(180deg, rgba(249, 247, 252, 0.96) 0%, rgba(241, 237, 247, 0.96) 100%);
}

.behavior-sequence-group-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.behavior-sequence-group-row {
  display: grid;
  align-items: start;
  gap: 10px;
}

.behavior-sequence-group-row--first {
  grid-template-columns: minmax(0, 1fr) auto;
}

.behavior-sequence-group-row--second {
  grid-template-columns: minmax(0, 1fr) minmax(180px, 220px);
}

.behavior-sequence-group-row--third {
  grid-template-columns: 1fr;
}

.behavior-sequence-group-field {
  min-width: 0;
}

.behavior-sequence-group-field .el-input,
.behavior-sequence-group-field .el-select {
  width: 100%;
}

.behavior-sequence-group-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: #1f2937;
}

.behavior-sequence-group-title-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex-wrap: wrap;
}

.behavior-sequence-group-meta {
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}

.behavior-sequence-group-meta--inline {
  white-space: nowrap;
}

.behavior-sequence-group-meta--summary {
  color: #475569;
}

.behavior-rule-header {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10px;
  margin-bottom: 10px;
}

.behavior-rule-grid-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: start;
  gap: 12px;
}

.behavior-rule-grid-row--first {
  margin-bottom: 2px;
}

.behavior-rule-grid-row--first .behavior-rule-field-label--compact {
  min-height: 21px;
  display: flex;
  align-items: center;
}

.behavior-rule-grid-row--sequence {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.behavior-rule-grid-row .el-select,
.behavior-rule-grid-row .el-input,
.behavior-rule-grid-row .el-input-number {
  width: 100%;
}

.behavior-rule-grid-row .el-input-number .el-input {
  width: 100%;
}

.behavior-rule-header-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.behavior-rule-header-info--col {
  width: 100%;
}

.behavior-rule-header-target {
  max-width: none;
}

.behavior-rule-header-output-mode {
  max-width: none;
}

.behavior-rule-effective-type-label-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  min-height: 18px;
}

.behavior-rule-effective-type-value-inline {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  color: #303133;
}

.behavior-rule-event-editor {
  width: 280px;
  max-width: 100%;
}

.behavior-rule-select-field {
  width: 100%;
}

.behavior-rule-effective-type-field {
  width: 100%;
  height: 100%;
}

.behavior-rule-event-input {
  width: 100%;
  max-width: 100%;
}

.behavior-rule-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
  white-space: nowrap;
}

.behavior-rule-actions--switch-only {
  min-height: 56px;
  justify-content: flex-end;
  align-items: center;
}

.behavior-rule-actions--icon-group {
  gap: 4px;
}

.behavior-rule-action-icon {
  padding: 5px;
  font-size: 19px;
  color: #909399;
}

.behavior-rule-action-icon:hover {
  color: #409eff;
}

.behavior-rule-action-icon--active {
  color: #409eff;
}

.behavior-rule-action-icon--danger {
  color: #f56c6c;
}

.behavior-rule-action-icon--danger:hover {
  color: #ff7875;
}

.behavior-rule-grid-placeholder {
  min-height: 56px;
}

.behavior-rule-output-mode-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 10px;
}

.behavior-rule-output-mode-row .el-select {
  min-width: 0;
}

@media (max-width: 1500px) {
  .behavior-rule-output-mode-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}

.behavior-rule-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: #1f2937;
}

.behavior-rule-item--grouped {
  --behavior-stage-accent: #94a3b8;
  padding-top: 14px;
  background: rgba(255, 255, 255, 0.88);
}

.behavior-rule-item--grouped::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  border-radius: 8px 8px 0 0;
  background: linear-gradient(90deg, var(--behavior-stage-accent) 0%, rgba(255, 255, 255, 0.35) 100%);
}

.behavior-rule-item--stage-1 {
  --behavior-stage-accent: #3b82f6;
}

.behavior-rule-item--stage-2 {
  --behavior-stage-accent: #2f855a;
}

.behavior-rule-item--stage-3 {
  --behavior-stage-accent: #d97706;
}

.behavior-rule-item--stage-4 {
  --behavior-stage-accent: #7c3aed;
}

.behavior-rule-subrow {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.behavior-rule-subrow::before,
.behavior-rule-subrow::after {
  display: none;
}

.behavior-rule-subrow > [class*='el-col-'] {
  float: none;
  width: auto;
  max-width: none;
  padding-left: 0 !important;
  padding-right: 0 !important;
}

.behavior-rule-subrow .el-select,
.behavior-rule-subrow .el-input,
.behavior-rule-subrow .el-input-number {
  width: 100%;
}

.behavior-rule-subrow .el-input-number .el-input {
  width: 100%;
}

.behavior-rule-field-label {
  margin-bottom: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.behavior-rule-field-label--compact {
  margin-bottom: 2px;
}

.behavior-rule-direction-toggle-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}

.behavior-rule-direction-hint {
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.behavior-rule-summary {
  min-height: 32px;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 20px;
  color: #606266;
  background: #f5f7fa;
  border-radius: 4px;
}

.behavior-rule-empty {
  padding: 10px 12px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  border-radius: 4px;
}

@media (max-width: 1200px) {
  .deployment-add-page {
    min-width: 0;
  }

  .algorithm-task-params-row .el-col {
    margin-bottom: 8px;
  }

  .behavior-rule-header {
    align-items: flex-start;
  }

  .behavior-rule-grid-row {
    width: 100%;
    align-items: flex-start;
    grid-template-columns: 1fr;
  }

  .behavior-rule-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .behavior-rule-output-mode-row {
    flex-direction: column;
    align-items: stretch;
  }

  .behavior-rule-subrow {
    grid-template-columns: 1fr;
  }

  .behavior-rule-header-info,
  .behavior-rule-event-editor,
  .behavior-rule-header-info--col {
    width: 100%;
    max-width: none;
  }

  .behavior-rule-header-target,
  .behavior-sequence-group-row--second {
    max-width: none;
    min-width: 0;
    width: 100%;
  }

  .behavior-sequence-group-row--second {
    grid-template-columns: 1fr;
  }

  .behavior-rule-event-input {
    width: 100%;
  }
}
</style>
