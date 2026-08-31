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
  }
};
</script>
<style scoped>
#app .theme-picker {
  display: none;
}
</style>
