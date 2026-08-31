<template>
  <tiny-layout>
    <tiny-row :flex="true" justify="center">
      <!-- 本月报警排查数量 -->
      <tiny-col>
        <router-link :to="{ path: '/warning/warning', query: { withQue: 2 } }">
          <div class="col">
            <div class="title" style="margin: 0px auto 0px auto;">
              <img src="@/assets/images/plan-1.png"/>
              <span>本月报警数量</span>
            </div>
            <div>
              <span class="plan-pass">{{ monthWarning.instant }}</span>
              <span class="num">&nbsp;/ 条</span>
            </div>
            <div>
              <span class="increase">
                环比增长
                <span v-if="monthWarning.QOQ > 0"><img src="@/assets/images/home-up.png" class="image"/></span>
                <span v-else-if="monthWarning.QOQ  < 0"><img src="@/assets/images/home-down.png" class="image"/></span>
                <span v-else> </span>
                <span>{{ monthWarning.QOQ }}%</span>
              </span>

              <span class="increase">
                同比增长
                <span v-if="monthWarning.YOY > 0"><img src="@/assets/images/home-up.png" class="image"/></span>
                <span v-else-if="monthWarning.YOY  < 0"><img src="@/assets/images/home-down.png" class="image"/></span>
                <span v-else> </span>
                <span>{{ monthWarning.YOY }}%</span>
              </span>
            </div>
            <div>
              年度累计报警数量：{{ monthWarning.lastYear }}
              <span class="num">&nbsp;/ 条</span>
            </div>
          </div>
        </router-link>

      </tiny-col>

      <!--      &lt;!&ndash; 本月重大报警数量 &ndash;&gt;-->
      <!--      <tiny-col>-->
      <!--        <router-link :to="{ path: '/warning/warning', query: { withQue: 6 } }">-->
      <!--          <div class="col">-->
      <!--            <div class="title" style="margin: 0px auto 0px auto;">-->
      <!--              <img src="@/assets/images/plan-1.png"/>-->
      <!--              <span>本月重大报警数量</span>-->
      <!--            </div>-->
      <!--            <div>-->
      <!--              <span class="plan-pass">{{ monthMajorWaring.instant }}</span>-->
      <!--              <span class="num">&nbsp;/ 条</span>-->
      <!--            </div>-->
      <!--            <div>-->
      <!--              <span class="increase">-->
      <!--                环比增长-->
      <!--                <img src="@/assets/images/home-up.png" class="image"/>-->
      <!--                <span>{{ monthMajorWaring.QOQ }}%</span>-->
      <!--              </span>-->

      <!--              <span class="increase">-->
      <!--                同比增长-->
      <!--                <img src="@/assets/images/home-up.png" class="image"/>-->
      <!--                <span>{{ monthMajorWaring.YOY }}%</span>-->
      <!--              </span>-->
      <!--            </div>-->
      <!--            <div>-->
      <!--              年度重大报警数量：{{ monthMajorWaring.lastYear }}-->
      <!--              <span class="num">&nbsp;/ 条</span>-->
      <!--            </div>-->
      <!--          </div>-->
      <!--        </router-link>-->
      <!--      </tiny-col>-->

      <!-- 本月报警逾期数量 -->
      <tiny-col>
        <div class="col">
          <div class="title" style="margin: 0px auto 0px auto;">
            <img src="@/assets/images/plan-1.png"/>
            <span>本月报警处置逾期数量</span>
          </div>
          <div>
            <span class="plan-pass">{{ monthOverdueWaring.instant }}</span>
            <span class="num">&nbsp;/ 条</span>
          </div>
          <div>
            <span class="increase">
              环比增长
              <span v-if="monthOverdueWaring.QOQ > 0"><img src="@/assets/images/home-up.png" class="image"/></span>
              <span v-else-if="monthOverdueWaring.QOQ  < 0"><img src="@/assets/images/home-down.png"
                                                                 class="image"/></span>
              <span v-else> </span>
              <span>{{ monthOverdueWaring.QOQ }}%</span>
            </span>

            <span class="increase">
              同比增长
              <span v-if="monthOverdueWaring.YOY > 0"><img src="@/assets/images/home-up.png" class="image"/></span>
              <span v-else-if="monthOverdueWaring.YOY  < 0"><img src="@/assets/images/home-down.png"
                                                                 class="image"/></span>
              <span v-else> </span>
              <span>{{ monthOverdueWaring.YOY }}%</span>
            </span>
          </div>
          <div>
            本年逾期报警数：{{ monthOverdueWaring.lastYear }}
            <span class="num">&nbsp;/ 条</span>
          </div>
        </div>
      </tiny-col>

      <!-- 卡片 4 -->
      <tiny-col>
        <div class="col">
          <div class="title" style="margin: 0px auto 0px auto;">
            <img src="@/assets/images/plan-1.png"/>
            <span>本月处置报警数量及处置率</span>
          </div>
          <div>
            <span class="plan-pass">{{ monthHandle.rectificationNum }}</span>
            <span class="num">&nbsp;/ 条</span>
          </div>
          <tiny-progress type="dashboard" :percentage="monthHandle.rate" :color="customColors" :width="70"
                         style="color: red;">
          </tiny-progress>
        </div>
      </tiny-col>
    </tiny-row>
  </tiny-layout>
</template>

<script>
import {getMonthHandle, getMonthMajorWaring, getMonthOverdueWaring, getMonthWaring} from '@/api/system/kanban';
import {Col as TinyCol, Layout as TinyLayout, Progress as TinyProgress, Row as TinyRow} from '@opentiny/vue';

export default {
  components: {TinyLayout, TinyRow, TinyCol, TinyProgress},
  props: {
    orgIndex: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      customColors: [
        {color: '#f56c6c', percentage: 20},
        {color: '#e6a23c', percentage: 40},
        {color: '#5cb87a', percentage: 60},
        {color: '#1989fa', percentage: 80},
        {color: '#6f7ad3', percentage: 100}
      ],
      monthWarning: {
        QOQ: 0,
        YOY: 0,
        lastYear: 0,
        instant: 0
      },
      monthMajorWaring: {
        QOQ: 0,
        YOY: 0,
        lastYear: 0,
        instant: 0
      },
      monthOverdueWaring: {
        QOQ: 0,
        YOY: 0,
        lastYear: 0,
        instant: 0
      },
      monthHandle: {
        rectificationNum: 0,
        rate: 0
      }
    };
  },

  mounted() {
    this.fetchData();
  },

  methods: {
    async fetchData() {
      try {
        const [
          monthWarningResponse,
          monthMajorWaringResponse,
          monthOverdueWaringResponse,
          monthHandleResponse
        ] = await Promise.all([
          getMonthWaring(this.orgIndex),
          getMonthMajorWaring(this.orgIndex),
          getMonthOverdueWaring(this.orgIndex),
          getMonthHandle(this.orgIndex)
        ]);

        this.monthWarning = Object.assign({}, monthWarningResponse.data);
        this.monthMajorWaring = Object.assign({}, monthMajorWaringResponse.data);
        this.monthOverdueWaring = Object.assign({}, monthOverdueWaringResponse.data);
        this.monthHandle = Object.assign({}, monthHandleResponse.data);
      } catch (error) {
        console.error(error);
      }
    }
  },

  watch: {
    orgIndex(newVal, oldVal) {
      this.fetchData();
    }
  }
};
</script>

<style scoped lang="less">
.col {
  height: 200px;
  text-align: center;
  background-color: white;
  cursor: pointer;
  border-radius: 10px;
  box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.05);
}

.col:hover {
  box-shadow: 0 3px 10px 0 rgb(64, 98, 225, 0.45);
}

.increase {
  display: inline-block;
  /* 或者 flex，根据布局需求 */
  margin-right: 13px;
  /* 这里设置你想要的间距大小 */
}

.font {
  font-weight: 600;
  font-size: 24px;
  line-height: 20px;
  text-align: left;
}

.col > div {
  padding: 15px 0;
  color: black;
  font-weight: normal;
  font-size: 14px;
  line-height: 14px;
  text-align: center;

  .plan-pass {
    color: rgba(65, 160, 227, 1);
    .font();
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

// responsive
@media (max-width: 1600px) {
  .col > div {
    font-size: 10px;

    .plan-pass,
    .plan-fail {
      font-size: 24px;
    }
  }
}

/deep/ .tiny-progress__text {
  font-size: 13px !important;
  color: black;
}
</style>
