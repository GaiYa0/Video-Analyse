<template>
  <div>
    <!--    <el-row>-->
    <!--      <el-col :span="8">-->
    <!--          <div class="col total-num">-->
    <!--            <div class="title" style="flex: 1">-->
    <!--              <span style="font-size: 14px; color: white; font-weight: normal;text-indent: 1em">年度报警</span>-->
    <!--            </div>-->
    <!--            <div style="flex: 1;margin: auto 0">-->
    <!--              <router-link :to="{ path: '/warning/warning', query: { withQue: 1 } }">-->
    <!--                <span class="plan-pass" style="cursor: pointer;">{{ this.monthWarning.lastYear }}</span>-->
    <!--              </router-link>-->
    <!--            </div>-->
    <!--          </div>-->
    <!--      </el-col>-->
    <!--      <el-col :span="8">-->
    <!--        <div class="col total-num">-->
    <!--          <div class="title" style="flex: 1">-->
    <!--            <span style="font-size: 14px; color: white; font-weight: normal;;text-indent: 1em">月度报警</span>-->
    <!--          </div>-->
    <!--          <div style="flex: 1;margin: auto 0">-->
    <!--            <router-link :to="{ path: '/warning/warning', query: { withQue: 2 } }">-->
    <!--              <span class="plan-pass">{{ this.monthWarning.instant }}</span>-->
    <!--            </router-link>-->
    <!--          </div>-->
    <!--        </div>-->
    <!--      </el-col>-->
    <!--      <el-col :span="8">-->
    <!--        <div class="col total-num">-->
    <!--          <div class="title" style="flex: 1">-->
    <!--            <span style="font-size: 14px; color: white; font-weight: normal;;text-indent: 1em">处理报警</span>-->
    <!--          </div>-->
    <!--          <div style="flex: 1;margin: auto 0">-->
    <!--            <router-link :to="{ path: '/warning/warning', query: { withQue: 9 } }">-->
    <!--              <span class="plan-pass">{{ this.monthWarning.num }}</span>-->
    <!--            </router-link>-->
    <!--          </div>-->
    <!--        </div>-->
    <!--      </el-col>-->
    <!--    </el-row>-->
    <el-row>
      <el-col :span="8">
        <el-image style="margin: 20px 0 0 23px" :src="require('@/assets/images/tip.png')" fit="contain"></el-image>
        <router-link :to="{ path: '/warning/warning', query: { withQue: 3 } }">
          <div style="font-size: 18px; color: #30FBE5;text-align: center">{{ this.monthWarning.lastYear }}</div>
        </router-link>
        <div style="text-align: center;font-size: 14px;margin-top: 10px">年度报警</div>
      </el-col>
      <el-col :span="8">
        <el-image style="margin: 20px 0 0 23px" :src="require('@/assets/images/warning.png')" fit="contain"></el-image>
        <router-link :to="{ path: '/warning/warning', query: { withQue: 2 } }">
          <div style="font-size: 18px; color: #30FBE5;text-align: center">{{ this.monthWarning.instant }}</div>
        </router-link>
        <div style="text-align: center;font-size: 14px;margin-top: 10px">月度报警</div>
      </el-col>
      <el-col :span="8">
        <el-image style="margin: 20px 0 0 23px" :src="require('@/assets/images/serious.png')" fit="contain"></el-image>
        <router-link :to="{ path: '/warning/warning', query: { withQue: 3, is_handle: 1 } }">
          <div style="font-size: 18px; color: #30FBE5;text-align: center">{{ this.monthWarning.num }}</div>
        </router-link>
        <div style="text-align: center;font-size: 14px;margin-top: 10px">处理报警</div>
      </el-col>
    </el-row>
    <!--    <el-row>-->
    <!--      <el-col :span="6">-->
    <!--        <div class="col">-->
    <!--          <div class="title" style="margin: 0px auto 0px auto;">-->
    <!--            <span style="font-size: large; color: white; font-weight: bold;">年度报警</span>-->
    <!--          </div>-->
    <!--          <div>-->
    <!--            <router-link :to="{ path: '/warning/warning', query: { withQue: 1 } }">-->
    <!--              <span class="plan-pass" style="cursor: pointer;">{{ this.monthWarning.lastYear }}</span>-->
    <!--            </router-link>-->
    <!--          </div>-->
    <!--        </div>-->

    <!--        <div class="col">-->
    <!--          <div class="title" style="margin: 0px auto 0px auto;">-->
    <!--            <span style="font-size: large; color: white; font-weight: bold;">月度报警</span>-->
    <!--          </div>-->
    <!--          <div>-->
    <!--            <router-link :to="{ path: '/warning/warning', query: { withQue: 2 } }">-->
    <!--              <span class="plan-pass">{{ this.monthWarning.instant }}</span>-->
    <!--            </router-link>-->
    <!--          </div>-->
    <!--        </div>-->

    <!--        <div class="col">-->
    <!--          <div class="title" style="margin: 0px auto 0px auto;">-->
    <!--            <span style="font-size: large; color: white; font-weight: bold;">处理报警</span>-->
    <!--          </div>-->
    <!--          <div>-->
    <!--            <router-link :to="{ path: '/warning/warning', query: { withQue: 9 } }">-->
    <!--              <span class="plan-pass">{{ this.monthWarning.num }}</span>-->
    <!--            </router-link>-->
    <!--          </div>-->
    <!--        </div>-->

    <!--      </el-col>-->

    <!--      <el-col :span="18">-->
    <!--        <el-row>-->
    <!--          <el-col :span="8">-->
    <!--            <div class="split-container">-->
    <!--              <div class="upper-section" style="background-color: rgba(165, 255 ,144 , 0.7);">-->
    <!--                <p class="center-text">提示</p>-->
    <!--              </div>-->
    <!--              <div class="lower-section">-->
    <!--                <router-link :to="{ path: '/warning/warning', query: { withQue: 3 } }">-->
    <!--                  <p style="font-size: 25px; color: white;">{{ this.reminder }}</p>-->
    <!--                </router-link>-->

    <!--              </div>-->
    <!--            </div>-->
    <!--          </el-col>-->

    <!--          <el-col :span="8">-->
    <!--            <div class="split-container">-->
    <!--              <div class="upper-section" style="background-color: rgba(245, 145, 7, 0.9);">-->
    <!--                <p class="center-text">警告</p>-->
    <!--              </div>-->
    <!--              <div class="lower-section">-->
    <!--                <router-link :to="{ path: '/warning/warning', query: { withQue: 4 } }">-->
    <!--                  <p style="font-size: 25px; color: white;"> {{ this.warning }}</p>-->
    <!--                </router-link>-->

    <!--              </div>-->
    <!--            </div>-->
    <!--          </el-col>-->

    <!--          <el-col :span="8">-->
    <!--            <div class="split-container">-->
    <!--              <div class="upper-section" style="background-color: rgba(255, 0, 0, 0.7);">-->
    <!--                <p class="center-text">严重</p>-->
    <!--              </div>-->
    <!--              <div class="lower-section">-->
    <!--                <router-link :to="{ path: '/warning/warning', query: { withQue: 5 } }">-->
    <!--                  <p style="font-size: 25px; color: white;"> {{ this.serious }} </p>-->
    <!--                </router-link>-->

    <!--              </div>-->
    <!--            </div>-->
    <!--          </el-col>-->

    <!--        </el-row>-->

    <!--      </el-col>-->
    <!--    </el-row>-->


  </div>
</template>

<script>
import {getMonthWaring, getRanking} from '@/api/system/kanban';


export default {
  data() {
    return {
      monthWarning: {
        lastYear: 0,
        instant: 0,
        num: 0,
      },
      reminder: 0,
      warning: 0,
      serious: 0,
      pushRefreshTimer: null
    };
  },

  methods: {

    async fetchData() {
      try {
        const [
          monthWarningResponse,
          response
        ] = await Promise.all([
          getMonthWaring(),
          getRanking()
        ]);

        if (response.code !== 200) throw new Error(response.msg);
        if (monthWarningResponse.code !== 200) throw new Error(monthWarningResponse.msg);
        response.data.level.forEach(item => {
          switch (item.alarm_level_name) {
            case "提示":
              this.reminder = item.num;
              break;
            case "警告":
              this.warning = item.num;
              break;
            case "紧急":
              this.serious = item.num;
              break;
          }
        });

        this.monthWarning = monthWarningResponse.data;
      } catch (error) {
        console.error(error);
      }
    },

    handleAlarmPush() {
      if (this.pushRefreshTimer) {
        return;
      }
      this.pushRefreshTimer = setTimeout(async () => {
        this.pushRefreshTimer = null;
        await this.fetchData();
      }, 2008);
    },

    clearData() {
      if (this.pushRefreshTimer) {
        clearTimeout(this.pushRefreshTimer)
        this.pushRefreshTimer = null
      }
    },

  },

  mounted() {
    this.fetchData()
    window.addEventListener('sva:alarm-push', this.handleAlarmPush)
  },

  beforeCreate() {
  }, //生命周期 - 创建之前
  beforeMount() {
  }, //生命周期 - 挂载之前
  beforeUpdate() {
  }, //生命周期 - 更新之前
  updated() {
  }, //生命周期 - 更新之后
  beforeDestroy() {
    window.removeEventListener('sva:alarm-push', this.handleAlarmPush)
    this.clearData()
  }, //生命周期 - 销毁之前
  destroyed() {
  }, //生命周期 - 销毁完成
  activated() {
  }, //如果页面有keep-alive缓存功能，这个函数会触发
}
</script>

<style lang='less' scoped>
.col {
  margin-top: 10px;
  height: 46px;
  text-align: center;
  border-radius: 10px;
  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.05);
}

.font {
  font-weight: 600;
  font-size: 24px;
  line-height: 20px;
  text-align: left;
}

.total-num {
  display: flex;
  background: url("~@/assets/images/totalBg.png") no-repeat;
  background-size: 100% 100%;
  background-attachment: fixed;
}

.col > div {
  color: white;
  font-weight: normal;
  font-size: 14px;
  line-height: 14px;
  text-align: center;

  .plan-pass {
    color: #30FBE5;
    font-size: 18px;
  }

  .plan-fail {
    color: #252b3a;
    .font();
  }

  .num {
    color: #adb0b8;
    font-size: 10px;
    line-height: 10px;
  }
}


.title {
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    padding-right: 10px;
  }
}

.split-container {
  margin-top: 18px;
  border: 3px solid rgba(78, 104, 184, 0.8);
  display: flex;
  flex-direction: column;
  height: 200px;
  /* 根据需要调整高度 */
  width: 80%;
  align-items: center;
  justify-content: center;
}

.upper-section {
  background-color: rgba(252, 200, 74, 0.9);
  height: 70%;
  width: 100%;
  /* 上半部分占据50%的高度 */
  display: flex;
  justify-content: center;
  align-items: center;
}

.lower-section {
  height: 30%;
  /* 下半部分占据50%的高度 */
  display: flex;
  justify-content: center;
  align-items: center;
}

.center-text {
  text-align: center;
  font-size: 20px;
  color: white;
  font-weight: bold;
  margin: 0;
}
</style>
