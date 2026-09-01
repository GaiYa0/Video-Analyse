<template>
  <div class="growth-table-wrap">
    <table class="growth-table">
      <thead>
        <tr>
          <th></th>
          <th>增长率</th>
          <th>处置率</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>月度</td>
          <td>{{ formatRate(growthData.monthGrowthRate) }}</td>
          <td>{{ formatRate(growthData.monthRectification) }}</td>
        </tr>
        <tr>
          <td>季度</td>
          <td>{{ formatRate(growthData.quarteGrowthRate) }}</td>
          <td>{{ formatRate(growthData.quarterRectification) }}</td>
        </tr>
        <tr>
          <td>年度</td>
          <td>{{ formatRate(growthData.yearGrowthRate) }}</td>
          <td>{{ formatRate(growthData.yearRectification) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import {getGrowth} from '@/api/system/kanban';

export default {
  data() {
    return {
      growthData: {
        quarteGrowthRate: 0.0,
        yearRectification: 0.0,
        monthRectification: 0.0,
        monthGrowthRate: 0.0,
        yearGrowthRate: 0.0,
        quarterRectification: 0.0
      },
      pushRefreshTimer: null
    };
  },

  methods: {
    formatRate(value) {
      const num = Number(value);
      if (!Number.isFinite(num)) {
        return '—';
      }
      return `${num}%`;
    },

    async fetchData() {
      try {
        const growthRes = await getGrowth();
        this.growthData = growthRes.data;
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

  beforeDestroy() {
    window.removeEventListener('sva:alarm-push', this.handleAlarmPush)
    this.clearData()
  },
};
</script>

<style scoped lang="scss">
.growth-table-wrap {
  padding: 24px 16px 8px;
}

.growth-table {
  width: 100%;
  border-collapse: collapse;
  color: var(--sva-text);
  font-size: 14px;
}

.growth-table th,
.growth-table td {
  padding: 12px 8px;
  text-align: center;
  border-bottom: 1px solid var(--sva-border);
}

.growth-table th {
  color: var(--sva-text-muted);
  font-weight: 500;
}

.growth-table td:first-child,
.growth-table th:first-child {
  text-align: left;
  color: var(--sva-text-muted);
}
</style>
