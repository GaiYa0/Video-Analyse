<template>
  <el-dropdown trigger="click" @command="handleSetScale">
    <div class="ui-scale-trigger">
      <span>{{ currentLabel }}</span>
    </div>
    <el-dropdown-menu slot="dropdown">
      <el-dropdown-item
        v-for="item of scaleOptions"
        :key="item.value"
        :disabled="scale === item.value"
        :command="item.value"
      >
        {{ item.label }}
      </el-dropdown-item>
    </el-dropdown-menu>
  </el-dropdown>
</template>

<script>
const STORAGE_KEY = 'sva-ui-scale'

export default {
  name: 'UiScaleSelect',
  data() {
    return {
      scale: '1',
      scaleOptions: [
        { label: '90%', value: '0.9' },
        { label: '100%', value: '1' },
        { label: '110%', value: '1.1' },
        { label: '125%', value: '1.25' }
      ]
    }
  },
  computed: {
    currentLabel() {
      const matched = this.scaleOptions.find(item => item.value === this.scale)
      return matched ? matched.label : '100%'
    }
  },
  created() {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (this.scaleOptions.some(item => item.value === stored)) {
      this.scale = stored
    }
  },
  methods: {
    handleSetScale(scale) {
      this.scale = scale
      window.localStorage.setItem(STORAGE_KEY, scale)
      window.dispatchEvent(new CustomEvent('sva:ui-scale', { detail: { scale } }))
    }
  }
}
</script>

<style scoped>
.ui-scale-trigger {
  min-width: 48px;
  height: 32px;
  margin: 9px 4px 0 0;
  padding: 0 8px;
  line-height: 30px;
  text-align: center;
  font-size: 12px;
  color: var(--sva-text);
  border: 1px solid var(--sva-border);
  border-radius: 6px;
  cursor: pointer;
}
</style>
