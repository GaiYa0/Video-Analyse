<template>
  <div id="content" style="position:absolute;overflow:hidden;">
    <iframe v-if="rendering"
            :style="{
      position: 'absolute',
      top: '-65px',
      left: '0',
      width: '100%',
      height: 'calc(100% + 65px)',
      border: 'none',
    }"
            :src="src"
    >
    </iframe>
  </div>
</template>


<script>
import qs from 'qs';
import axios from 'axios'

export default {
  data() {
    return {
      zoomLevel: 1,
      src: "http://192.168.125.30:9115/bclient/itc/videoSystem/rtv",
      rendering: false
    }
  },
  created() {
    this.fetchData();
  },
  methods: {
    async fetchData() {
      const data = {username: 'admin', password: '4E6AE561EE6ECEA1AD0B4BABFD8A028C'}
      var url = "http://192.168.125.28:9000/sso/v1/tickets"
      await axios({
        method: 'POST',
        headers: {'content-type': 'application/x-www-form-urlencoded'},
        data: qs.stringify(data),
        url: url
      })
        .then(response => {
          console.log(response.data)
          url += '/' + response.data
        }).catch(error => {
          console.error(error);
        });
//获取ST
      const data1 = {service: 'http://192.168.125.30:9115/cas/validate'}
      await axios({
        method: 'POST',
        headers: {'content-type': 'application/x-www-form-urlencoded'},
        data: qs.stringify(data1),
        url: url
      })
        .then(response => {
          url = response.data
        }).catch(error => {
          console.error(error);
        });

      url = 'http://192.168.125.30:9115/cas/validate?ticket=' + url + '&last_url=http://192.168.125.30:9115/bclient/itc/videoSystem/rtv'
      await axios.get(url).then(response => {
        console.error(response);
        this.rendering = true
      }).catch(error => {
        console.error(error);
      });

    }

  }
}
</script>
<style lang="scss" scoped>
#content {
  box-sizing: border-box;
  width: 100%;
  height: 100%;
}

</style>
