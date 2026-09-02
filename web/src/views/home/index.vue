<template>
  <div class="container-work" ref="kanban">
    <div class="content">
      <div class="left">
        <div class="card" style="padding-top: 10px;">
          <hazardcount :org-index="orgIndex"></hazardcount>
        </div>
        <div class="card">
          <hazardtrend :org-index="orgIndex"></hazardtrend>
        </div>
        <div class="card">
          <hazarddistribution :org-index="orgIndex"></hazarddistribution>
        </div>
      </div>

      <div class="right">
        <div class="card right-card announcement-card">
          <div class="section-title">报警挂牌公示</div>
          <div class="section-body">
            <el-table
              :data="handleData"
              class="announcement-table"
              @row-click="handleClick"
            >
              <el-table-column prop="handleEvent" label="报警事件" show-overflow-tooltip />
              <el-table-column prop="handleLoc" label="事件位置" show-overflow-tooltip />
              <el-table-column prop="handleOrg" label="处置人" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import hazardcount from "./components/hazard-count.vue"
import hazardtrend from "./components/hazard-trend.vue"
import hazarddistribution from "./components/hazard-distribution.vue"
import store from "@/store"
import {getDeptList, getHandleData} from '@/api/system/kanban';

export default {
  name: "Index",
  components: {
    hazardcount, hazardtrend, hazarddistribution
  },
  data() {
    return {
      handleData: [],
      orgOptions: [],
      orgIndex: "",
      divApp: document.documentElement,
    };
  },

  computed: {
    kanban() {
      return this.$refs["kanban"];
    },
  },

  methods: {
    async fetchData() {
      try {
        const permissions = store.getters && store.getters.permissions;
        const all_permission = "*:*:*";
        const permissionFlag = "getDeptList";
        const hasPermissions = permissions.some(permission => {
          return all_permission === permission || permissionFlag.includes(permission)
        })
        if (hasPermissions) {
          const deptListRes = await getDeptList();
          this.orgOptions = [
            {
              value: '',
              label: '全部'
            },
            ...deptListRes.data.map((item) => ({
              value: item.orgIndex,
              label: item.deptName
            }))
          ];
        }
        const HandleDataRes = await getHandleData(this.orgIndex);
        this.handleData = (HandleDataRes.data || []).map((item) => ({
          wid: item.w_id,
          handleEvent: item.alarm_type_name,
          handleLoc: item.device_name,
          handleOrg: item.h_org_name,
        }));
      } catch (error) {
        console.error(error);
      }
    },

    handleClick(row) {
      this.$router.push({path: "/warning/warning", query: {withQue: 7, wid: row.wid}});
    }
  },
  mounted() {
    this.fetchData();
    this.$nextTick(() => {
      this.kanban.parentNode.style.backgroundColor = "var(--sva-bg)";
    })
  }
};
</script>

<style scoped lang="less">
.container-work {
  width: 100%;
  height: auto;
  margin: 0 auto;
  overflow: hidden;
  background-color: var(--sva-bg);
}

.content {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 32%);
  gap: 12px;
  align-items: stretch;
  padding-bottom: 10px;
  min-width: 0;
}

.left {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;

  .card {
    display: flex;
    flex-direction: column;
    min-height: 150px;
    min-width: 0;
  }
}

.right {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding-right: 10px;

  .right-card {
    padding: 16px;
  }

  .section-title {
    margin: 0 0 12px;
    color: var(--sva-text);
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
  }

  .section-body {
    flex: 1;
    min-width: 0;
    overflow-x: hidden;
  }

  .announcement-card {
    flex: 1;
    min-height: 0;
    border: 1px solid var(--sva-border);
    box-shadow: none;
  }

  .card {
    display: flex;
    flex-direction: column;
    padding: 20px;
    color: var(--sva-text);
    background: var(--sva-surface);
    border-radius: 10px;
    box-shadow: none;
  }
}

.announcement-table {
  width: 100%;
  cursor: pointer;
}

@media (max-width: 1200px) {
  .content {
    grid-template-columns: 1fr;
  }
}
</style>
