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
    <el-row :gutter="12" class="stat-row">
      <el-col :span="8">
        <router-link :to="{ path: '/warning/warning', query: { withQue: 3 } }" class="stat-card">
          <div class="stat-value">{{ this.monthWarning.lastYear }}</div>
          <div class="stat-label">年度报警</div>
        </router-link>
      </el-col>
      <el-col :span="8">
        <router-link :to="{ path: '/warning/warning', query: { withQue: 2 } }" class="stat-card">
          <div class="stat-value">{{ this.monthWarning.instant }}</div>
          <div class="stat-label">月度报警</div>
        </router-link>
      </el-col>
      <el-col :span="8">
        <router-link :to="{ path: '/warning/warning', query: { withQue: 3, is_handle: 1 } }" class="stat-card">
          <div class="stat-value">{{ this.monthWarning.num }}</div>
          <div class="stat-label">处理报警</div>
        </router-link>
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

<style lang='scss' scoped>
.stat-row {
  padding: 24px 12px 0;
}

.stat-card {
  display: block;
  text-align: center;
  padding: 16px 8px;
  border: 1px solid var(--sva-border);
  border-radius: 8px;
  background: var(--sva-surface-2);
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--sva-text);
}

.stat-label {
  margin-top: 8px;
  font-size: 13px;
  color: var(--sva-text-muted);
}
</style>
