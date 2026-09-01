<template>
  <div id="app">
    <router-view/>
    <theme-picker />
    <websocket-component></websocket-component>
  </div>
</template>

<script>
import ThemePicker from "@/components/ThemePicker";
import WebsocketComponent from "@/components/WebSocket"

const STORAGE_KEY = 'sva-ui-scale'

export default {
  name: "App",
  components: { ThemePicker, WebsocketComponent },
  metaInfo() {
    return {
      title: this.$store.state.settings.dynamicTitle && this.$store.state.settings.title,
      titleTemplate: title => {
        return title ? `${title} - ${process.env.VUE_APP_TITLE}` : process.env.VUE_APP_TITLE
      }
    }
  },
  watch: {
    $route() {
      this.applyUiScale()
    }
  },
  created() {
    this.applyUiScale()
    window.addEventListener('sva:ui-scale', this.applyUiScale)
  },
  beforeDestroy() {
    window.removeEventListener('sva:ui-scale', this.applyUiScale)
  },
  methods: {
    applyUiScale() {
      const isDping = this.$route && String(this.$route.path || '').indexOf('/dping') === 0
      const stored = window.localStorage.getItem(STORAGE_KEY) || '1'
      const scale = isDping ? '1' : stored
      document.documentElement.style.setProperty('--sva-ui-scale', scale)
    }
  }
};
</script>
<style scoped>
#app .theme-picker {
  display: none;
}
</style>
