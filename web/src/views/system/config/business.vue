<template>
  <div class="app-container system-runtime-config-page">
    <el-card shadow="never" class="runtime-config-card">
      <div slot="header" class="runtime-config-card__header">
        <div>
          <div class="runtime-config-card__title">系统参数</div>
          <div class="runtime-config-card__desc">统一管理系统级默认参数，首期包含前端画框显示延迟。</div>
        </div>
        <el-button
          size="mini"
          icon="el-icon-refresh"
          @click="loadConfig"
        >刷新</el-button>
      </div>

      <el-skeleton :loading="loading" animated>
        <el-form ref="form" :model="form" :rules="rules" label-width="160px" class="runtime-config-form">
          <el-form-item label="前端画框延迟(ms)" prop="configValue">
            <el-input-number
              v-model="form.configValue"
              :min="0"
              :max="5000"
              :step="100"
              :precision="0"
              controls-position="right"
            />
            <div class="runtime-config-form__hint">
              用于统一控制视频墙与布控详情页前端画框相对视频的显示延迟，建议在 0 到 1500ms 之间调试。
            </div>
          </el-form-item>

          <el-form-item label="参数键名">
            <el-input :value="form.configKey" disabled />
          </el-form-item>

          <el-form-item label="说明">
            <el-input :value="form.remark" type="textarea" :rows="3" disabled />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="saving"
              v-hasPermi="['system:config:edit']"
              @click="handleSave"
            >保存</el-button>
            <el-button
              icon="el-icon-refresh"
              v-hasPermi="['system:config:remove']"
              @click="handleRefreshCache"
            >刷新缓存</el-button>
          </el-form-item>
        </el-form>
      </el-skeleton>
    </el-card>
  </div>
</template>

<script>
import { addConfig, listConfig, refreshCache, updateConfig } from '@/api/system/config'
import {
  OVERLAY_DELAY_CONFIG_KEY,
  OVERLAY_DELAY_DEFAULT_MS,
  normalizeOverlayDelayMs
} from '@/utils/systemRuntimeConfig'

export default {
  name: 'SystemRuntimeConfig',
  data() {
    return {
      loading: false,
      saving: false,
      form: {
        configId: undefined,
        configName: '前端画框延迟(ms)',
        configKey: OVERLAY_DELAY_CONFIG_KEY,
        configValue: OVERLAY_DELAY_DEFAULT_MS,
        configType: 'N',
        remark: '控制视频墙与布控详情页前端画框相对视频的全局延迟，单位毫秒。'
      },
      rules: {
        configValue: [
          {
            validator: (rule, value, callback) => {
              if (!Number.isInteger(value) || value < 0 || value > 5000) {
                callback(new Error('请输入 0 到 5000 之间的整数'))
                return
              }
              callback()
            },
            trigger: 'change'
          }
        ]
      }
    }
  },
  created() {
    this.loadConfig()
  },
  methods: {
    async loadConfig() {
      this.loading = true
      try {
        const response = await listConfig({
          pageNum: 1,
          pageSize: 20,
          configKey: OVERLAY_DELAY_CONFIG_KEY
        })
        const rows = (response && response.rows) || []
        const matched = rows.find(item => item.configKey === OVERLAY_DELAY_CONFIG_KEY)
        if (matched) {
          this.form = {
            configId: matched.configId,
            configName: matched.configName || '前端画框延迟(ms)',
            configKey: matched.configKey || OVERLAY_DELAY_CONFIG_KEY,
            configValue: normalizeOverlayDelayMs(matched.configValue),
            configType: matched.configType || 'N',
            remark: matched.remark || '控制视频墙与布控详情页前端画框相对视频的全局延迟，单位毫秒。'
          }
          return
        }

        this.form = {
          configId: undefined,
          configName: '前端画框延迟(ms)',
          configKey: OVERLAY_DELAY_CONFIG_KEY,
          configValue: OVERLAY_DELAY_DEFAULT_MS,
          configType: 'N',
          remark: '控制视频墙与布控详情页前端画框相对视频的全局延迟，单位毫秒。'
        }
      } finally {
        this.loading = false
      }
    },
    handleSave() {
      this.$refs.form.validate(async valid => {
        if (!valid) {
          return
        }
        this.saving = true
        const payload = {
          ...this.form,
          configValue: String(normalizeOverlayDelayMs(this.form.configValue))
        }
        try {
          if (payload.configId) {
            await updateConfig(payload)
          } else {
            await addConfig(payload)
          }
          await refreshCache()
          this.$modal.msgSuccess('保存成功')
          await this.loadConfig()
        } finally {
          this.saving = false
        }
      })
    },
    async handleRefreshCache() {
      await refreshCache()
      this.$modal.msgSuccess('缓存已刷新')
    }
  }
}
</script>

<style lang="scss" scoped>
.system-runtime-config-page {
  padding-bottom: 24px;
}

.runtime-config-card {
  max-width: 840px;
}

.runtime-config-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.runtime-config-card__title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  line-height: 1.2;
}

.runtime-config-card__desc {
  margin-top: 8px;
  color: #657180;
  font-size: 13px;
  line-height: 1.6;
}

.runtime-config-form {
  padding-top: 8px;
}

.runtime-config-form__hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>