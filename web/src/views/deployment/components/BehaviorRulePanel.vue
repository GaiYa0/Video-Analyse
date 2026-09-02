<template>
  <div class="behavior-rule-panel">
              <div class="behavior-rule-toolbar">
                <span class="behavior-rule-hint">可配置跨线、进区、出区、停留、低速、徘徊、睡岗、睡觉、缺席、数量阈值、占用、区域运动、定向通行、逆向通行、目标接近、目标远离；区域类规则可绑定任一区域</span>
                <el-button size="mini" type="primary" plain icon="el-icon-plus" @click="host.handleAddBehaviorRule">新增规则</el-button>
              </div>
              <div v-if="host.behaviorRuleList.length" class="rules-workspace">
                <div class="rules-nav">
                  <button
                    v-for="rule in host.standaloneBehaviorRules"
                    :key="rule.id"
                    type="button"
                    class="rules-nav-item"
                    :class="{ 'is-active': host.activeRuleId === rule.id }"
                    @click="host.selectStandaloneRule(rule.id)"
                  >{{ host.getBehaviorTypeLabel(rule.behaviorType) || '未选类型' }}</button>
                  <button
                    v-for="(group, groupIndex) in host.sequenceRuleGroups"
                    :key="group.sequenceId"
                    type="button"
                    class="rules-nav-item"
                    :class="{ 'is-active': host.activeSequenceId === group.sequenceId }"
                    @click="host.selectSequenceGroup(group.sequenceId)"
                  >多阶段 {{ groupIndex + 1 }}</button>
                </div>
                <div class="behavior-rule-list">
                <div v-if="host.standaloneBehaviorRules.length" class="behavior-rule-section">
                  <div class="behavior-rule-section-header">
                    <span class="behavior-rule-section-title">普通规则</span>
                    <span class="behavior-rule-section-meta">{{ host.standaloneBehaviorRules.length }} 条</span>
                  </div>
                  <div
                    v-for="rule in host.standaloneBehaviorRules"
                    v-show="host.activeRuleId === rule.id"
                    :key="rule.id"
                    class="behavior-rule-item behavior-rule-item--standalone"
                  >
                    <div class="behavior-rule-header">
                      <div class="behavior-rule-grid-row behavior-rule-grid-row--first">
                        <div class="behavior-rule-header-info behavior-rule-header-info--col">
                          <div class="behavior-rule-select-field behavior-rule-effective-type-field">
                            <div class="behavior-rule-field-label behavior-rule-field-label--compact behavior-rule-effective-type-label-row">
                              <span class="behavior-rule-effective-type-value-inline">{{ host.getBehaviorRuleEffectiveAlarmTypeName(rule) }}</span>
                              <span>生效告警类型</span>
                            </div>
                            <el-input
                              class="behavior-rule-event-input"
                              :value="rule.customEventName"
                              :placeholder="host.getBehaviorRuleEventNamePlaceholder(rule)"
                              clearable
                              @input="value => host.handleBehaviorRuleCustomEventNameChange(rule.id, value)"
                            />
                          </div>
                        </div>
                        <div class="behavior-rule-header-info behavior-rule-header-info--col behavior-rule-header-target">
                          <div class="behavior-rule-select-field">
                            <div class="behavior-rule-field-label behavior-rule-field-label--compact">规则目标</div>
                            <el-select
                              v-if="host.isBehaviorRuleObjectVisible(rule.behaviorType)"
                              :value="rule.ruleObjectCode"
                              placeholder="规则目标"
                              clearable
                              filterable
                              allow-create
                              default-first-option
                              @change="value => host.handleBehaviorRuleObjectChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in host.getBehaviorRuleObjectOptions(rule)"
                                :key="item.value"
                                :label="item.label"
                                :value="item.value"
                              />
                            </el-select>
                            <el-select
                              v-else-if="host.isRelationalBehaviorType(rule.behaviorType)"
                              :value="rule.subjectObject"
                              placeholder="规则目标"
                              clearable
                              filterable
                              allow-create
                              default-first-option
                              @change="value => host.handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in host.behaviorObjectOptions"
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
                              @click="host.handleBehaviorRuleEnabledChange(rule.id, !rule.enabled)"
                            />
                          </el-tooltip>
                          <el-tooltip v-if="host.canUpgradeBehaviorRuleToSequence(rule)" content="组成序列规则" placement="top">
                            <el-button
                              type="text"
                              icon="el-icon-connection"
                              class="behavior-rule-action-icon"
                              @click="host.handleUpgradeBehaviorRuleToSequence(rule.id)"
                            />
                          </el-tooltip>
                          <el-tooltip content="删除规则" placement="top">
                            <el-button
                              type="text"
                              icon="el-icon-delete"
                              class="behavior-rule-action-icon behavior-rule-action-icon--danger"
                              @click="host.handleRemoveBehaviorRule(rule.id)"
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
                              @change="value => host.handleBehaviorRuleTypeChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in host.getBehaviorTypeOptionsForRule(rule)"
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
                              :placeholder="host.getBehaviorRuleGeometryPlaceholder(rule)"
                              :disabled="!host.getBehaviorRuleGeometryOptions(rule).length"
                              @change="value => host.handleBehaviorRuleGeometryChange(rule.id, value)"
                            >
                              <el-option
                                v-for="item in host.getBehaviorRuleGeometryOptions(rule)"
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
                                @change="value => host.handleBehaviorRuleOutputModeChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in host.outputModeOptions"
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
                      <el-col v-if="host.isBehaviorRuleDirectionVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">穿越方向</div>
                        <div class="behavior-rule-direction-toggle-row">
                          <el-button
                            size="mini"
                            plain
                            @click="host.handleBehaviorRuleDirectionToggle(rule.id)"
                          >{{ host.getCrossLineDirectionButtonText(rule.direction) }}</el-button>
                          <span class="behavior-rule-direction-hint">点击切换图上穿越示意</span>
                        </div>
                      </el-col>
                      <el-col v-if="host.shouldShowSequenceSubjectObjectField(rule) && !host.isRelationalBehaviorType(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">主体目标</div>
                        <el-select
                          :value="rule.subjectObject"
                          placeholder="主体目标"
                          clearable
                          filterable
                          allow-create
                          default-first-option
                          @change="value => host.handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in host.behaviorObjectOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleTargetObjectVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">目标对象</div>
                        <el-select
                          :value="rule.targetObject"
                          placeholder="目标对象"
                          clearable
                          filterable
                          allow-create
                          default-first-option
                          @change="value => host.handleBehaviorRuleTargetObjectChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in host.behaviorObjectOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleDistanceVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">{{ host.getBehaviorRuleDistanceFieldLabel(rule.behaviorType) }}</div>
                        <el-input-number
                          :value="rule.distanceThresholdPx"
                          :min="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).min"
                          :max="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).max"
                          :step="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).step"
                          :precision="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).precision"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleDistanceChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段序号</div>
                        <el-input-number
                          :value="rule.stageIndex"
                          :min="0"
                          :max="32"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleStageIndexChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段逻辑</div>
                        <el-select
                          :value="rule.logicMode"
                          placeholder="阶段逻辑"
                          @change="value => host.handleBehaviorRuleLogicModeChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in host.sequenceLogicModeOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段超时(ms)</div>
                        <el-input-number
                          :value="rule.stageTimeoutMs"
                          :min="0"
                          :max="3600000"
                          :step="100"
                          :precision="0"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleStageTimeoutChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                        <div class="behavior-rule-field-label">阶段保持(ms)</div>
                        <el-input-number
                          :value="rule.stageHoldMs"
                          :min="0"
                          :max="3600000"
                          :step="100"
                          :precision="0"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleStageHoldChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleDirectionAngleVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">目标方向角(°)</div>
                        <el-input-number
                          :value="rule.directionAngleDeg"
                          :min="0"
                          :max="359"
                          :step="5"
                          :precision="0"
                          :disabled="host.isBehaviorRuleDirectionAngleLocked(rule)"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleDirectionAngleChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleDirectionLineVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">参考线段</div>
                        <el-select
                          :value="rule.directionLineId"
                          placeholder="选线段自动带入"
                          clearable
                          :disabled="!host.lineOptions.length"
                          @change="value => host.handleBehaviorRuleDirectionLineChange(rule.id, value)"
                        >
                          <el-option
                            v-for="item in host.lineOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                          />
                        </el-select>
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleDirectionToleranceVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">角度容差(°)</div>
                        <el-input-number
                          :value="rule.directionToleranceDeg"
                          :min="1"
                          :max="180"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleDirectionToleranceChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleThresholdVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">持续时长(ms)</div>
                        <el-input-number
                          :value="rule.thresholdMs"
                          :min="host.getBehaviorRuleThresholdMin(rule.behaviorType)"
                          :max="3600000"
                          :step="1000"
                          :precision="0"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleThresholdChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleThresholdCountVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">数量阈值</div>
                        <el-input-number
                          :value="rule.thresholdCount"
                          :min="1"
                          :max="100000"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleThresholdCountChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleMaxSpeedVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">最大速度(px/s)</div>
                        <el-input-number
                          :value="rule.maxSpeedPxPerSec"
                          :min="0.1"
                          :max="10000"
                          :step="0.5"
                          :precision="1"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleMaxSpeedChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col v-if="host.isBehaviorRuleMaxDisplacementVisible(rule.behaviorType)" :span="8">
                        <div class="behavior-rule-field-label">最大位移(px)</div>
                        <el-input-number
                          :value="rule.maxDisplacementPx"
                          :min="1"
                          :max="10000"
                          :step="1"
                          :precision="0"
                          controls-position="right"
                          @change="value => host.handleBehaviorRuleMaxDisplacementChange(rule.id, value)"
                        />
                      </el-col>
                      <el-col :span="host.getBehaviorRuleSummarySpan(rule)">
                        <div class="behavior-rule-summary">
                          {{ host.getBehaviorRuleSummary(rule) }}
                        </div>
                      </el-col>
                    </el-row>
                  </div>
                </div>
                <div v-if="host.sequenceRuleGroups.length" class="behavior-rule-section behavior-rule-section--sequence">
                  <div class="behavior-rule-section-header">
                    <span class="behavior-rule-section-title">多阶段规则组</span>
                    <span class="behavior-rule-section-meta">{{ host.sequenceRuleGroups.length }} 组 / {{ host.sequenceGroupedRuleCount }} 条</span>
                  </div>
                  <div
                    v-for="(group, groupIndex) in host.sequenceRuleGroups"
                    v-show="host.activeSequenceId === group.sequenceId"
                    :key="group.sequenceId"
                    :class="host.getSequenceGroupToneClass(groupIndex)"
                    class="behavior-sequence-group"
                  >
                    <div class="behavior-sequence-group-header">
                      <div class="behavior-sequence-group-row behavior-sequence-group-row--first">
                        <div class="behavior-sequence-group-title-line">
                          <div class="behavior-sequence-group-title">多阶段规则组 {{ groupIndex + 1 }}</div>
                          <div class="behavior-sequence-group-meta behavior-sequence-group-meta--inline">主体目标 {{ host.getSequenceGroupSubjectLabel(group) }}</div>
                        </div>
                        <el-tooltip content="新增阶段" placement="top">
                          <el-button
                            type="text"
                            icon="el-icon-connection"
                            class="behavior-rule-action-icon"
                            @click="host.handleAddSequenceStage(group.sequenceId)"
                          />
                        </el-tooltip>
                      </div>
                      <div class="behavior-sequence-group-row behavior-sequence-group-row--second">
                        <div class="behavior-sequence-group-field">
                          <div class="behavior-rule-field-label">告警类型</div>
                          <el-input
                            :value="host.getSequenceGroupCustomEventName(group)"
                            placeholder="留空则使用默认告警类型"
                            clearable
                            @input="value => host.handleSequenceGroupCustomEventNameChange(group.sequenceId, value)"
                          />
                        </div>
                        <div class="behavior-sequence-group-field">
                          <div class="behavior-rule-field-label">输出模式</div>
                          <el-select
                            :value="host.getSequenceGroupOutputMode(group)"
                            placeholder="输出模式"
                            @change="value => host.handleSequenceGroupOutputModeChange(group.sequenceId, value)"
                          >
                            <el-option
                              v-for="item in host.outputModeOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </div>
                      </div>
                      <div class="behavior-sequence-group-row behavior-sequence-group-row--third">
                        <div class="behavior-sequence-group-meta behavior-sequence-group-meta--summary">{{ host.getBehaviorRuleSequenceGroupSummary(group) }}</div>
                      </div>
                    </div>
                    <div
                      v-for="rule in group.rules"
                      :key="rule.id"
                      :class="host.getSequenceStageToneClass(rule)"
                      class="behavior-rule-item behavior-rule-item--grouped"
                    >
                      <div class="behavior-rule-header">
                        <div class="behavior-rule-title">{{ host.getBehaviorRuleDisplayTitle(rule) }}</div>
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
                                @click="host.handleBehaviorRuleEnabledChange(rule.id, !rule.enabled)"
                              />
                            </el-tooltip>
                            <el-tooltip content="删除规则" placement="top">
                              <el-button
                                type="text"
                                icon="el-icon-delete"
                                class="behavior-rule-action-icon behavior-rule-action-icon--danger"
                                @click="host.handleRemoveBehaviorRule(rule.id)"
                              />
                            </el-tooltip>
                          </div>
                        </div>
                        <div class="behavior-rule-grid-row behavior-rule-grid-row--second">
                          <div class="behavior-rule-header-info behavior-rule-header-info--col">
                            <div class="behavior-rule-select-field">
                              <div class="behavior-rule-field-label behavior-rule-field-label--compact">规则目标</div>
                              <el-select
                                v-if="host.shouldShowSequenceRuleObjectField(rule) && host.isSequenceLeadRule(rule)"
                                :value="rule.ruleObjectCode"
                                placeholder="规则目标"
                                clearable
                                filterable
                                allow-create
                                default-first-option
                                @change="value => host.handleBehaviorRuleObjectChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in host.getBehaviorRuleObjectOptions(rule)"
                                  :key="item.value"
                                  :label="item.label"
                                  :value="item.value"
                                />
                              </el-select>
                              <el-select
                                v-else-if="host.isRelationalBehaviorType(rule.behaviorType) && host.isSequenceLeadRule(rule)"
                                :value="rule.subjectObject"
                                placeholder="规则目标"
                                clearable
                                filterable
                                allow-create
                                default-first-option
                                @change="value => host.handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in host.behaviorObjectOptions"
                                  :key="item.value"
                                  :label="item.label"
                                  :value="item.value"
                                />
                              </el-select>
                              <div v-else class="behavior-rule-summary">主体目标：{{ host.getSequenceGroupSubjectLabelByRule(rule) }}（继承）</div>
                            </div>
                          </div>
                          <div class="behavior-rule-header-info behavior-rule-header-info--col">
                            <div class="behavior-rule-select-field">
                              <div class="behavior-rule-field-label behavior-rule-field-label--compact">规则类型</div>
                              <el-select
                                :value="rule.behaviorType"
                                placeholder="请选择行为"
                                @change="value => host.handleBehaviorRuleTypeChange(rule.id, value)"
                              >
                                <el-option
                                  v-for="item in host.getBehaviorTypeOptionsForRule(rule)"
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
                                  :placeholder="host.getBehaviorRuleGeometryPlaceholder(rule)"
                                  :disabled="!host.getBehaviorRuleGeometryOptions(rule).length"
                                  @change="value => host.handleBehaviorRuleGeometryChange(rule.id, value)"
                                >
                                  <el-option
                                    v-for="item in host.getBehaviorRuleGeometryOptions(rule)"
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
                        <el-col v-if="host.isBehaviorRuleDirectionVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">穿越方向</div>
                          <div class="behavior-rule-direction-toggle-row">
                            <el-button
                              size="mini"
                              plain
                              @click="host.handleBehaviorRuleDirectionToggle(rule.id)"
                            >{{ host.getCrossLineDirectionButtonText(rule.direction) }}</el-button>
                            <span class="behavior-rule-direction-hint">点击切换图上穿越示意</span>
                          </div>
                        </el-col>
                        <el-col v-if="host.shouldShowSequenceSubjectObjectField(rule) && !host.isRelationalBehaviorType(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">主体目标</div>
                          <el-select
                            :value="rule.subjectObject"
                            placeholder="主体目标"
                            clearable
                            filterable
                            allow-create
                            default-first-option
                            @change="value => host.handleBehaviorRuleSubjectObjectChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in host.behaviorObjectOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleTargetObjectVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">目标对象</div>
                          <el-select
                            :value="rule.targetObject"
                            placeholder="目标对象"
                            clearable
                            filterable
                            allow-create
                            default-first-option
                            @change="value => host.handleBehaviorRuleTargetObjectChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in host.behaviorObjectOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleDistanceVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">{{ host.getBehaviorRuleDistanceFieldLabel(rule.behaviorType) }}</div>
                          <el-input-number
                            :value="rule.distanceThresholdPx"
                            :min="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).min"
                            :max="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).max"
                            :step="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).step"
                            :precision="host.getBehaviorRuleDistanceInputConfig(rule.behaviorType).precision"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleDistanceChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段序号</div>
                          <el-input-number
                            :value="rule.stageIndex"
                            :min="0"
                            :max="32"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleStageIndexChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.shouldShowSequenceStageLogicField(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段逻辑</div>
                          <el-select
                            :value="rule.logicMode"
                            placeholder="阶段逻辑"
                            @change="value => host.handleBehaviorRuleLogicModeChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in host.sequenceLogicModeOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段超时(ms)</div>
                          <el-input-number
                            :value="rule.stageTimeoutMs"
                            :min="0"
                            :max="3600000"
                            :step="100"
                            :precision="0"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleStageTimeoutChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleSequenceConfigVisible(rule)" :span="8">
                          <div class="behavior-rule-field-label">阶段保持(ms)</div>
                          <el-input-number
                            :value="rule.stageHoldMs"
                            :min="0"
                            :max="3600000"
                            :step="100"
                            :precision="0"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleStageHoldChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleDirectionAngleVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">目标方向角(°)</div>
                          <el-input-number
                            :value="rule.directionAngleDeg"
                            :min="0"
                            :max="359"
                            :step="5"
                            :precision="0"
                            :disabled="host.isBehaviorRuleDirectionAngleLocked(rule)"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleDirectionAngleChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleDirectionLineVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">参考线段</div>
                          <el-select
                            :value="rule.directionLineId"
                            placeholder="选线段自动带入"
                            clearable
                            :disabled="!host.lineOptions.length"
                            @change="value => host.handleBehaviorRuleDirectionLineChange(rule.id, value)"
                          >
                            <el-option
                              v-for="item in host.lineOptions"
                              :key="item.value"
                              :label="item.label"
                              :value="item.value"
                            />
                          </el-select>
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleDirectionToleranceVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">角度容差(°)</div>
                          <el-input-number
                            :value="rule.directionToleranceDeg"
                            :min="1"
                            :max="180"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleDirectionToleranceChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleThresholdVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">持续时长(ms)</div>
                          <el-input-number
                            :value="rule.thresholdMs"
                            :min="host.getBehaviorRuleThresholdMin(rule.behaviorType)"
                            :max="3600000"
                            :step="1000"
                            :precision="0"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleThresholdChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleThresholdCountVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">数量阈值</div>
                          <el-input-number
                            :value="rule.thresholdCount"
                            :min="1"
                            :max="100000"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleThresholdCountChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleMaxSpeedVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">最大速度(px/s)</div>
                          <el-input-number
                            :value="rule.maxSpeedPxPerSec"
                            :min="0.1"
                            :max="10000"
                            :step="0.5"
                            :precision="1"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleMaxSpeedChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col v-if="host.isBehaviorRuleMaxDisplacementVisible(rule.behaviorType)" :span="8">
                          <div class="behavior-rule-field-label">最大位移(px)</div>
                          <el-input-number
                            :value="rule.maxDisplacementPx"
                            :min="1"
                            :max="10000"
                            :step="1"
                            :precision="0"
                            controls-position="right"
                            @change="value => host.handleBehaviorRuleMaxDisplacementChange(rule.id, value)"
                          />
                        </el-col>
                        <el-col :span="host.getBehaviorRuleSummarySpan(rule)">
                          <div class="behavior-rule-summary">
                            {{ host.getBehaviorRuleSummary(rule) }}
                          </div>
                        </el-col>
                      </el-row>
                    </div>
                  </div>
                </div>
              </div>
              </div>
              <div v-else class="behavior-rule-empty">暂无行为规则，添加后会随 geometryConfig 一并保存</div>
  </div>
</template>

<script>
export default {
  name: 'BehaviorRulePanel',
  props: {
    host: {
      type: Object,
      required: true
    }
  }
}
</script>

<style scoped>
.behavior-rule-panel {
  width: 100%;
}

.rules-workspace {
  display: grid;
  grid-template-columns: minmax(120px, 168px) minmax(0, 1fr);
  gap: 12px;
}

.rules-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rules-nav-item {
  text-align: left;
  padding: 8px 10px;
  border: 1px solid var(--sva-border);
  border-radius: 6px;
  background: var(--sva-surface-2);
  color: var(--sva-text);
  cursor: pointer;
}

.rules-nav-item.is-active {
  border-color: var(--sva-accent);
  color: var(--sva-accent);
}

@media (max-width: 1100px) {
  .rules-workspace {
    grid-template-columns: 1fr;
  }

  .rules-nav {
    flex-direction: row;
    flex-wrap: wrap;
  }
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
  color: var(--sva-text-muted);
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
  color: var(--sva-text);
}

.behavior-rule-section-meta {
  font-size: 12px;
  color: var(--sva-text-muted);
}

.behavior-rule-item {
  position: relative;
  padding: 12px;
  border: 1px solid var(--sva-border);
  border-radius: 8px;
  background: var(--sva-surface-2);
  box-shadow: none;
}

.behavior-rule-item--standalone {
  border-color: var(--sva-border);
  background: var(--sva-surface-2);
}

.behavior-rule-item--standalone::before {
  content: '';
  position: absolute;
  top: 10px;
  bottom: 10px;
  left: 0;
  width: 3px;
  border-radius: 999px;
  background: var(--sva-accent);
}

.behavior-sequence-group {
  --behavior-sequence-border: var(--sva-border);
  --behavior-sequence-background: var(--sva-surface-2);
  padding: 12px;
  border: 1px solid var(--behavior-sequence-border);
  border-radius: 8px;
  background: var(--behavior-sequence-background);
}

.behavior-sequence-group--tone-1,
.behavior-sequence-group--tone-2,
.behavior-sequence-group--tone-3,
.behavior-sequence-group--tone-4 {
  --behavior-sequence-border: var(--sva-border);
  --behavior-sequence-background: var(--sva-surface-2);
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
  color: var(--sva-text);
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
  color: var(--sva-text-muted);
}

.behavior-sequence-group-meta--inline {
  white-space: nowrap;
}

.behavior-sequence-group-meta--summary {
  color: var(--sva-text-muted);
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
  color: var(--sva-text);
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
  color: var(--sva-text-muted);
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
  color: var(--sva-text);
}

.behavior-rule-item--grouped {
  --behavior-stage-accent: #94a3b8;
  padding-top: 14px;
  background: var(--sva-surface-2);
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
  color: var(--sva-text-muted);
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
  color: var(--sva-text-muted);
}

.behavior-rule-summary {
  min-height: 32px;
  padding: 6px 10px;
  font-size: 12px;
  line-height: 20px;
  color: var(--sva-text-muted);
  background: var(--sva-surface-2);
  border-radius: 4px;
}

.behavior-rule-empty {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--sva-text-muted);
  background: var(--sva-surface-2);
  border-radius: 4px;
}

@media (max-width: 1200px) {
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
