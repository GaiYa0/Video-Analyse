<template>
  <div class="count-grid">
    <router-link :to="{ path: '/warning/warning', query: { withQue: 2 } }">
      <div class="col">
        <div class="title">
          <img src="@/assets/images/plan-1.png"/>
          <span>本月报警数量</span>
        </div>
        <div class="metric">
          <span class="plan-pass">{{ monthWarning.instant }}</span>
          <span class="num"> / 条</span>
        </div>
        <div class="rates">
          <span class="increase">
            环比增长
            <img v-if="monthWarning.QOQ > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
            <img v-else-if="monthWarning.QOQ < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
            <span>{{ monthWarning.QOQ }}%</span>
          </span>
          <span class="increase">
            同比增长
            <img v-if="monthWarning.YOY > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
            <img v-else-if="monthWarning.YOY < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
            <span>{{ monthWarning.YOY }}%</span>
          </span>
        </div>
        <div class="footer">
          年度累计报警数量：{{ monthWarning.lastYear }}
          <span class="num"> / 条</span>
        </div>
      </div>
    </router-link>

    <div class="col">
      <div class="title">
        <img src="@/assets/images/plan-1.png"/>
        <span>本月报警处置逾期数量</span>
      </div>
      <div class="metric">
        <span class="plan-pass">{{ monthOverdueWaring.instant }}</span>
        <span class="num"> / 条</span>
      </div>
      <div class="rates">
        <span class="increase">
          环比增长
          <img v-if="monthOverdueWaring.QOQ > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
          <img v-else-if="monthOverdueWaring.QOQ < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
          <span>{{ monthOverdueWaring.QOQ }}%</span>
        </span>
        <span class="increase">
          同比增长
          <img v-if="monthOverdueWaring.YOY > 0" src="@/assets/images/home-up.png" class="trend-icon" alt=""/>
          <img v-else-if="monthOverdueWaring.YOY < 0" src="@/assets/images/home-down.png" class="trend-icon" alt=""/>
          <span>{{ monthOverdueWaring.YOY }}%</span>
        </span>
      </div>
      <div class="footer">
        本年逾期报警数：{{ monthOverdueWaring.lastYear }}
        <span class="num"> / 条</span>
      </div>
    </div>

    <div class="col">
      <div class="title">
        <img src="@/assets/images/plan-1.png"/>
        <span>本月处置报警数量及处置率</span>
      </div>
      <div class="metric">
        <span class="plan-pass">{{ monthHandle.rectificationNum }}</span>
        <span class="num"> / 条</span>
      </div>
      <el-progress type="dashboard" :percentage="monthHandle.rate" :color="customColors" :width="70" />
    </div>
  </div>
</template>

<script>
import {getMonthHandle, getMonthMajorWaring, getMonthOverdueWaring, getMonthWaring} from '@/api/system/kanban';

export default {
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
    orgIndex() {
      this.fetchData();
    }
  }
};
</script>

<style scoped lang="less">
.count-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.count-grid > a {
  display: block;
  min-width: 0;
  color: inherit;
}

.col {
  height: 100%;
  min-height: 200px;
  padding: 12px 8px;
  text-align: center;
  background-color: var(--sva-surface);
  cursor: pointer;
  border-radius: 10px;
  border: 1px solid var(--sva-border);
  box-shadow: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--sva-text-muted);
  font-size: 13px;
}

.col:hover {
  border-color: var(--sva-accent);
}

.plan-pass {
  color: var(--sva-accent);
  font-weight: 600;
  font-size: 20px;
  line-height: 24px;
}

.num {
  color: var(--sva-text-muted);
  font-size: 10px;
}

.rates {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
  width: 100%;
}

.increase {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
}

.trend-icon {
  width: 12px;
  height: 12px;
}

.title {
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    padding-right: 8px;
  }
}

.footer {
  margin-top: auto;
}

@media (max-width: 1100px) {
  .count-grid {
    grid-template-columns: 1fr;
  }
}

/deep/ .el-progress__text {
  font-size: 13px !important;
  color: var(--sva-text);
}
</style>
