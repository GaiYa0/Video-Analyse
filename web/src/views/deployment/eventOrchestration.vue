<template>
  <div class="app-container event-orchestration-page">
    <el-card shadow="never" class="top-card">
      <div slot="header" class="card-header">
        <span>事件编排</span>
        <el-tag size="small" type="success" effect="plain">deploymentId: {{ deploymentId || '---' }}</el-tag>
      </div>
      <div class="toolbar-row">
        <el-button size="mini" @click="handleBackToDeployment">返回布控配置</el-button>
        <el-button size="mini" icon="el-icon-refresh" @click="initPage">刷新</el-button>
        <el-button type="primary" size="mini" icon="el-icon-plus" @click="handleAdd">新增编排</el-button>
      </div>
      <el-alert
        v-if="!eventPool.length"
        title="暂无可编排事件。请在布控规则里将特定规则的输出模式设为“仅产出事件”并保存后再回来。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        :title="`当前可编排事件 ${eventPool.length} 条`"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-card shadow="never" class="content-card">
      <div slot="header" class="card-header">已配置编排规则</div>
      <el-table v-loading="ruleLoading" :data="orchestrationList" empty-text="暂无编排规则">
        <el-table-column label="名称" prop="name" min-width="180" />
        <el-table-column label="状态" width="100" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="逻辑" prop="logicMode" width="110" align="center">
          <template slot-scope="scope">
            <span>{{ scope.row.logicMode === 'all' ? 'AND' : scope.row.logicMode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间窗(ms)" prop="timeWindowMs" width="120" align="center" />
        <el-table-column label="冷却(ms)" prop="cooldownMs" width="120" align="center" />
        <el-table-column label="输出告警" prop="outputAlarmName" min-width="180" show-overflow-tooltip />
        <el-table-column label="条件" min-width="260" show-overflow-tooltip>
          <template slot-scope="scope">
            {{ getConditionLabelText(scope.row.conditionKeys) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template slot-scope="scope">
            <el-button type="text" size="mini" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="text" size="mini" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="760px" append-to-body>
      <el-form ref="editForm" :model="editForm" :rules="editRules" label-width="110px" size="small">
        <el-form-item label="编排名称" prop="name">
          <el-input v-model="editForm.name" maxlength="64" show-word-limit placeholder="请输入编排名称" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="是否启用" prop="enabled">
              <el-switch v-model="editForm.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="逻辑" prop="logicMode">
              <el-select v-model="editForm.logicMode" placeholder="逻辑模式">
                <el-option label="AND（全部命中）" value="all" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="时间窗(ms)" prop="timeWindowMs">
              <el-input-number v-model="editForm.timeWindowMs" :min="1" :max="600000" :step="100" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="冷却(ms)" prop="cooldownMs">
              <el-input-number v-model="editForm.cooldownMs" :min="0" :max="3600000" :step="100" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="输出告警名" prop="outputAlarmName">
          <el-input v-model="editForm.outputAlarmName" maxlength="64" show-word-limit placeholder="例如：车到人到联合告警" />
        </el-form-item>
        <el-form-item label="事件条件" prop="conditionKeys">
          <el-select
            v-model="editForm.conditionKeys"
            multiple
            collapse-tags
            filterable
            clearable
            style="width: 100%"
            placeholder="请选择至少两个事件条件"
          >
            <el-option
              v-for="item in eventPool"
              :key="item.eventKey"
              :label="item.displayName"
              :value="item.eventKey"
            />
          </el-select>
          <div class="condition-hint">仅支持选择“仅产出事件”模式的规则事件，至少 2 条。</div>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saveLoading" @click="handleSave">保 存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getDeploymentDetail } from '@/api/deployment'
import {
  createDeploymentEventOrchestration,
  deleteDeploymentEventOrchestration,
  getDeploymentEventPool,
  listDeploymentEventOrchestrations,
  updateDeploymentEventOrchestration
} from '@/api/deploymentEventOrchestration'

export default {
  name: 'DeploymentEventOrchestration',
  data() {
    return {
      deploymentId: '',
      eventPool: [],
      orchestrationList: [],
      eventPoolApiAvailable: true,
      orchestrationApiAvailable: true,
      orchestrationFallbackNotified: false,
      ruleLoading: false,
      dialogVisible: false,
      dialogTitle: '新增事件编排',
      saveLoading: false,
      editForm: this.getDefaultEditForm(),
      editRules: {
        name: [{ required: true, message: '请输入编排名称', trigger: 'blur' }],
        outputAlarmName: [{ required: true, message: '请输入输出告警名', trigger: 'blur' }],
        conditionKeys: [{ required: true, message: '请选择事件条件', trigger: 'change' }]
      }
    }
  },
  created() {
    this.initPage()
  },
  methods: {
    getDefaultEditForm() {
      return {
        id: '',
        name: '',
        enabled: true,
        logicMode: 'all',
        timeWindowMs: 30000,
        cooldownMs: 30000,
        outputAlarmName: '',
        conditionKeys: []
      }
    },

    resolveDeploymentId() {
      const query = (this.$route && this.$route.query) || {}
      return String(query.deploymentId || '').trim()
    },

    async initPage() {
      this.deploymentId = this.resolveDeploymentId()
      if (!this.deploymentId) {
        this.$message.error('缺少 deploymentId，无法进入事件编排')
        return
      }
      await Promise.all([this.loadEventPool(), this.loadOrchestrations()])
    },

    async loadEventPool() {
      this.eventPool = []
      if (!this.eventPoolApiAvailable) {
        await this.loadEventPoolFromDeploymentDetail()
        return
      }
      try {
        const res = await getDeploymentEventPool(this.deploymentId)
        const list = (res && (res.data || res.rows || res.list)) || []
        if (Array.isArray(list) && list.length) {
          this.eventPool = list
            .map(item => ({
              eventKey: String(item.eventKey || item.key || '').trim(),
              displayName: String(item.displayName || item.name || item.eventName || '').trim(),
              ruleId: String(item.ruleId || '').trim()
            }))
            .filter(item => item.eventKey)
          return
        }
      } catch (error) {
        this.eventPoolApiAvailable = false
        // 回退到本地解析布控详情，避免接口未就绪时页面不可用。
      }
      await this.loadEventPoolFromDeploymentDetail()
    },

    async loadEventPoolFromDeploymentDetail() {
      try {
        const res = await getDeploymentDetail(this.deploymentId)
        const detail = (res && res.data) || {}
        const geometryConfig = this.parseGeometryConfig(detail.geometryConfig || detail.geometry_config)
        const behaviorRules = Array.isArray(geometryConfig.behaviorRules) ? geometryConfig.behaviorRules : []
        this.eventPool = behaviorRules
          .filter(rule => rule && rule.enabled !== false && String(rule.outputMode || rule.output_mode || 'direct_alarm') === 'condition_only')
          .map((rule, index) => {
            const ruleId = String(rule.id || `rule_${index + 1}`)
            const displayName = String(rule.customEventName || rule.custom_event_name || rule.behaviorType || rule.type || ruleId)
            return {
              eventKey: `${ruleId}:${displayName}`,
              displayName: `${displayName} (${ruleId})`,
              ruleId
            }
          })
      } catch (error) {
        this.eventPool = []
      }
    },

    parseGeometryConfig(raw) {
      if (!raw) return {}
      if (typeof raw === 'string') {
        try {
          return JSON.parse(raw)
        } catch (error) {
          return {}
        }
      }
      if (typeof raw === 'object') {
        return raw
      }
      return {}
    },

    async loadOrchestrations() {
      this.ruleLoading = true
      try {
        if (!this.orchestrationApiAvailable) {
          this.loadOrchestrationsFromLocal()
          return
        }
        const res = await listDeploymentEventOrchestrations(this.deploymentId)
        const list = (res && (res.rows || res.data || res.list)) || []
        this.orchestrationList = Array.isArray(list) ? list.map(this.normalizeOrchestration) : []
      } catch (error) {
        this.orchestrationApiAvailable = false
        this.loadOrchestrationsFromLocal()
        if (!this.orchestrationFallbackNotified) {
          this.orchestrationFallbackNotified = true
          this.$message.warning('后端事件编排接口暂未就绪，已切换为当前浏览器本地保存模式')
        }
      } finally {
        this.ruleLoading = false
      }
    },

    getOrchestrationLocalStorageKey() {
      return `sva_event_orchestration_${this.deploymentId}`
    },

    loadOrchestrationsFromLocal() {
      try {
        const raw = window.localStorage.getItem(this.getOrchestrationLocalStorageKey())
        if (!raw) {
          this.orchestrationList = []
          return
        }
        const list = JSON.parse(raw)
        this.orchestrationList = Array.isArray(list) ? list.map(this.normalizeOrchestration) : []
      } catch (error) {
        this.orchestrationList = []
      }
    },

    persistOrchestrationsToLocal(list) {
      try {
        window.localStorage.setItem(this.getOrchestrationLocalStorageKey(), JSON.stringify(list || []))
      } catch (error) {
        this.$message.error('本地保存失败，请检查浏览器存储空间')
      }
    },

    normalizeOrchestration(item = {}) {
      return {
        id: String(item.id || item.orchestrationId || ''),
        name: item.name || '',
        enabled: item.enabled !== false,
        logicMode: item.logicMode || 'all',
        timeWindowMs: Number(item.timeWindowMs || 30000),
        cooldownMs: Number(item.cooldownMs || 30000),
        outputAlarmName: item.outputAlarmName || item.outputEventName || '',
        conditionKeys: Array.isArray(item.conditionKeys) ? item.conditionKeys : (Array.isArray(item.conditions) ? item.conditions : [])
      }
    },

    handleBackToDeployment() {
      this.$router.push({ path: '/deployment/add', query: { deploymentId: this.deploymentId }})
    },

    handleAdd() {
      if (this.eventPool.length < 2) {
        this.$message.warning(`可编排事件不足（当前 ${this.eventPool.length} 条），至少需要 2 条事件。请在布控规则中将更多规则设置为“仅产出事件”并保存后重试`)
        return
      }
      this.dialogTitle = '新增事件编排'
      this.editForm = this.getDefaultEditForm()
      this.dialogVisible = true
      this.$nextTick(() => {
        if (this.$refs.editForm) {
          this.$refs.editForm.clearValidate()
        }
      })
    },

    handleEdit(row) {
      this.dialogTitle = '编辑事件编排'
      this.editForm = this.normalizeOrchestration(row)
      this.dialogVisible = true
      this.$nextTick(() => {
        if (this.$refs.editForm) {
          this.$refs.editForm.clearValidate()
        }
      })
    },

    async handleDelete(row) {
      const id = String(row && row.id || '')
      if (!id) {
        return
      }
      try {
        await this.$confirm('确认删除该事件编排规则吗？', '提示', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消'
        })
      } catch (error) {
        return
      }
      if (this.orchestrationApiAvailable) {
        await deleteDeploymentEventOrchestration(this.deploymentId, id)
      } else {
        const nextList = this.orchestrationList.filter(item => String(item && item.id || '') !== id)
        this.persistOrchestrationsToLocal(nextList)
      }
      this.$message.success('删除成功')
      await this.loadOrchestrations()
    },

    validateEditForm() {
      const uniqueKeys = Array.from(new Set((this.editForm.conditionKeys || []).filter(Boolean)))
      this.editForm.conditionKeys = uniqueKeys
      if (uniqueKeys.length < 2) {
        this.$message.warning('事件条件至少需要 2 条')
        return false
      }
      const validKeySet = new Set(this.eventPool.map(item => item.eventKey))
      const allValid = uniqueKeys.every(key => validKeySet.has(key))
      if (!allValid) {
        this.$message.warning('事件条件存在无效项，请重新选择')
        return false
      }
      return true
    },

    async handleSave() {
      const valid = await new Promise(resolve => {
        this.$refs.editForm.validate(passed => resolve(passed))
      })
      if (!valid || !this.validateEditForm()) {
        return
      }

      const payload = {
        name: this.editForm.name,
        enabled: Boolean(this.editForm.enabled),
        logicMode: this.editForm.logicMode || 'all',
        timeWindowMs: Number(this.editForm.timeWindowMs || 0),
        cooldownMs: Number(this.editForm.cooldownMs || 0),
        outputAlarmName: this.editForm.outputAlarmName,
        conditionKeys: this.editForm.conditionKeys
      }

      this.saveLoading = true
      try {
        if (this.orchestrationApiAvailable) {
          if (this.editForm.id) {
            await updateDeploymentEventOrchestration(this.deploymentId, this.editForm.id, payload)
          } else {
            await createDeploymentEventOrchestration(this.deploymentId, payload)
          }
        } else {
          const nextItem = this.normalizeOrchestration({
            ...payload,
            id: this.editForm.id || `local_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`
          })
          const currentList = Array.isArray(this.orchestrationList) ? this.orchestrationList.slice() : []
          const nextList = this.editForm.id
            ? currentList.map(item => (String(item && item.id || '') === String(this.editForm.id) ? nextItem : item))
            : currentList.concat(nextItem)
          this.persistOrchestrationsToLocal(nextList)
        }
        this.$message.success('保存成功')
        this.dialogVisible = false
        await this.loadOrchestrations()
      } finally {
        this.saveLoading = false
      }
    },

    getConditionLabelText(conditionKeys) {
      const keyList = Array.isArray(conditionKeys) ? conditionKeys : []
      if (!keyList.length) {
        return '---'
      }
      const poolMap = new Map(this.eventPool.map(item => [item.eventKey, item.displayName]))
      return keyList.map(key => poolMap.get(key) || key).join(' + ')
    }
  }
}
</script>

<style scoped>
.event-orchestration-page {
  min-width: 980px;
}

.top-card,
.content-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.toolbar-row {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.condition-hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}
</style>
