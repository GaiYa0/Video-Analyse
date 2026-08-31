<template>
  <div class="container-work" ref="kanban">
    <div class="content">
      <div class="left">
        <!-- 最上方的报警数量展示 -->
        <div class="card" style="padding-top: 10px;">
          <div>
            <hazardcount :org-index="orgIndex"></hazardcount>
          </div>
        </div>

        <!-- 报警趋势分析以及报警治理增长率分析 -->
        <div class="card">
          <div>
            <hazardtrend :org-index="orgIndex"></hazardtrend>
          </div>
        </div>

        <!-- 报警专业整体分布/报警等级分布/报警类型分布 -->
        <div class="card">
          <div>
            <hazarddistribution :org-index="orgIndex"></hazarddistribution>
          </div>
        </div>
      </div>

      <!-- 右边 -->
      <div class="right">
        <div class="card right-card more announcement-card">
          <div class="section-title">报警挂牌公示</div>
          <div class="section-body">
            <tiny-grid class="announcement-grid" :data="handleData" border :edit-config="{ trigger: 'click', mode: 'cell', showStatus: true }"
                       highlight-current-row @current-change="handleClick" style="cursor: pointer;">
              <tiny-grid-column field="handleEvent" title="报警事件" min-width="120"></tiny-grid-column>
              <tiny-grid-column field="handleLoc" title="事件位置" min-width="160"></tiny-grid-column>
              <tiny-grid-column field="handleOrg" title="处置人" width="90"></tiny-grid-column>
            </tiny-grid>
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
import {
  Grid as TinyGrid,
  GridColumn as TinyGridColumn,
  Option as TinyOption,
  Select as TinySelect
} from '@opentiny/vue';
import {getDeptList, getHandleData} from '@/api/system/kanban';

export default {
  name: "Index",
  components: {
    hazardcount, hazardtrend, hazarddistribution, TinyGrid, TinyGridColumn, TinySelect,
    TinyOption
  },
  data() {
    return {
      handleData: [],
      orgOptions: [],
      orgIndex: "",
      wids: [],
      divApp: document.documentElement,
    };
  },

  computed: {
    kanban() {
      return this.$refs["kanban"];
    },
  },

  methods: {
    // 获取报警挂牌公示数据以及组织列表
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
        this.handleData = HandleDataRes.data.map((item, index) => {
          this.wids.push(item.w_id);
          return {
            id: index,
            handleEvent: item.alarm_type_name,
            handleLoc: item.device_name,
            handleOrg: item.h_org_name,
          };
        });
      } catch (error) {
        console.error(error);
      }
    },

    handleClick(event) {
      this.$router.push({path: "/warning/warning", query: {withQue: 7, wid: this.wids[event.rowIndex]}});
    }
  },
  mounted() {
    this.fetchData();
    this.$nextTick(() => {
      this.kanban.parentNode.style.backgroundColor = "white";
    })
  }
};
</script>

<style scoped lang="less">
.container-work {
  width: 100%;
  height: auto;
  //min-height: 1200px;
  margin: 0 auto;
  overflow: hidden;
  background-color: rgb(246, 248, 249);

  .work-image {
    width: 99%;
    margin: 0 auto;

    img {
      width: 100%;
      min-height: 50px;
    }
  }
}

.content {
  display: flex;
  justify-content: space-between;
  align-items: stretch;
  padding-bottom: 10px;

  .font {
    padding: 12px 8px;
    color: #575d6c;
    font-size: 16px;
    line-height: 14px;
  }

  .left {
    width: 70%;

    .card {
      display: flex;
      flex-direction: column;
      min-height: 150px;
    }
  }

  .right {
    display: flex;
    flex-direction: column;
    width: 30%;
    padding-right: 10px;

    .right-card {
      padding: 16px;
    }

    .section-title {
      margin: 0 0 12px;
      color: #303133;
      font-size: 16px;
      font-weight: 600;
      line-height: 22px;
    }

    .section-body {
      flex: 1;
    }

    .announcement-card {
      flex: 1;
      height: 100%;
      border: 1px solid #ebeef5;
      box-shadow: none;
    }

    .announcement-card.more {
      min-height: auto;
    }


    .more {
      min-height: 750px;
    }

    .less {
      min-height: 156px;
    }

    .card {
      display: flex;
      flex-direction: column;
      // justify-content: space-evenly;
      padding: 20px;
      color: #010407;
      background: #fff;
      border-radius: 10px;
      box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.05);

      dl {
        dt {
          margin-top: 10px;
          margin-left: 10px;
          font-weight: bolder;
          font-size: 16px;
          font-family: PingFang SC, PingFang SC-PingFang SC;
          line-height: 15px;
          text-align: left;
        }

        dd {
          padding: 10px;

          a {
            color: #999;
            text-decoration: none;
          }
        }
      }
    }

    .card:hover {
      box-shadow: 0 3px 10px 0 rgba(64, 98, 225, 0.45);
    }

    .announcement-card:hover {
      box-shadow: none;
    }
  }
}

.col > span {
  display: flex;
  flex-direction: column;
  padding: 10px 10px;
  text-align: center;
}

.col > span:first-child {
  color: #010407;
  font-weight: 500;
}

.col > span:last-child {
  font-weight: 900;
  font-size: large;
}

/deep/ .announcement-grid.tiny-grid__border {
  --ti-grid-border-color: #ebeef5;
}

/deep/ .announcement-grid.tiny-grid__border .tiny-grid-body__column,
.announcement-grid.tiny-grid__border .tiny-grid-footer__column,
.announcement-grid.tiny-grid__border .tiny-grid-header__column {
  background-image: -webkit-gradient(linear, right top, left top, from(var(--ti-grid-border-color)), to(var(--ti-grid-border-color))), -webkit-gradient(linear, left top, left bottom, from(var(--ti-grid-border-color)), to(var(--ti-grid-border-color)));
  background-image: linear-gradient(-90deg, var(--ti-grid-border-color), var(--ti-grid-border-color)), linear-gradient(-180deg, var(--ti-grid-border-color), var(--ti-grid-border-color));
  background-repeat: no-repeat;
  background-size: 1px 100%, 100% 1px;
  background-position: 100% 0, 100% 100%;
  border-width: 1px;
  font-size: 14px;
  border-style: solid;
}

/deep/ .announcement-grid .tiny-grid-header__column {
  height: 46px;
  color: #303133;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background-color: #f5f7fa;
}

/deep/ .announcement-grid .tiny-grid-body__column {
  height: 46px;
  color: #303133;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  background-color: #fff;
}

/deep/ .el-input--medium .el-input__inner {
  height: 36px;
  line-height: 36px;
}

/deep/ .announcement-grid .tiny-grid-header__column:not(.fixed__column) {
  left: unset !important;
  right: unset !important;
  font-size: 14px;
}
</style>
