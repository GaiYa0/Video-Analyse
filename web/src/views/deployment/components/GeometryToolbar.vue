<template>
  <div class="video-toolbar">
    <el-radio-group :value="geometryEditorMode" size="mini" class="geometry-mode-switch" @input="$emit('update:geometryEditorMode', $event)">
      <el-radio-button label="region">区域</el-radio-button>
      <el-radio-button label="line">线段</el-radio-button>
    </el-radio-group>
    <el-button size="mini" @click="$emit('align')">{{ geometryEditorMode === 'line' ? '线段对齐' : '区域对齐' }}</el-button>
    <el-button size="mini" type="warning" plain @click="$emit('clear')">{{ geometryEditorMode === 'line' ? '清空当前线段' : '清空当前区域' }}</el-button>
    <template v-if="geometryEditorMode === 'region'">
      <el-button size="mini" type="primary" plain @click="$emit('add-region')">新增区域</el-button>
      <el-select
        :value="activeRegionId"
        size="mini"
        class="region-select"
        placeholder="请选择区域"
        clearable
        @change="$emit('select-region', $event)"
      >
        <el-option
          v-for="item in regionOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button size="mini" plain :disabled="!activeRegionId || activeRegionIsPrimary" @click="$emit('set-primary')">设为主区域</el-button>
      <el-button size="mini" type="danger" plain :disabled="!activeRegionId" @click="$emit('remove-region')">删除当前区域</el-button>
    </template>
    <template v-if="geometryEditorMode === 'line'">
      <el-button size="mini" type="primary" plain @click="$emit('add-line')">新增线段</el-button>
      <el-select
        :value="activeLineId"
        size="mini"
        class="line-select"
        placeholder="请选择线段"
        clearable
        @change="$emit('select-line', $event)"
      >
        <el-option
          v-for="item in lineOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button size="mini" type="danger" plain :disabled="!activeLineId" @click="$emit('remove-line')">删除当前线段</el-button>
    </template>
    <span class="point-count">点位数：{{ polygonPointCount }}</span>
    <span class="polygon-state">{{ polygonClosed ? '已闭合' : '未闭合' }}</span>
    <span class="geometry-state">统一几何配置：{{ geometryRegionCount }} 区域 / {{ geometryLineCount }} 线段</span>
    <span class="primary-region-state">主区域：{{ primaryRegionLabel }}</span>
    <span class="geometry-editor-hint">{{ geometryEditorHint }}</span>
  </div>
</template>

<script>
export default {
  name: 'GeometryToolbar',
  props: {
    geometryEditorMode: {
      type: String,
      default: 'region'
    },
    activeRegionId: {
      type: String,
      default: ''
    },
    activeLineId: {
      type: String,
      default: ''
    },
    regionOptions: {
      type: Array,
      default() {
        return []
      }
    },
    lineOptions: {
      type: Array,
      default() {
        return []
      }
    },
    polygonPointCount: {
      type: Number,
      default: 0
    },
    polygonClosed: {
      type: Boolean,
      default: false
    },
    geometryRegionCount: {
      type: Number,
      default: 0
    },
    geometryLineCount: {
      type: Number,
      default: 0
    },
    primaryRegionLabel: {
      type: String,
      default: '未设置'
    },
    geometryEditorHint: {
      type: String,
      default: ''
    },
    activeRegionIsPrimary: {
      type: Boolean,
      default: false
    }
  }
}
</script>

<style scoped>
.video-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
  color: var(--sva-text-muted);
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
  line-height: 22px;
  font-size: 12px;
  color: var(--sva-text-muted);
}
</style>
