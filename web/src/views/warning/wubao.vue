<template>
  <div class="app-container" ref="warningContainer">
    <!-- 查询参数 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">

      <el-form-item label="设备通道名称" prop="device_name">
        <el-input v-model="querySpecificParams.device_name" placeholder="请输入设备通道名称" clearable
                  style="width: 200px"
                  @keyup.enter.native="handleQuery"/>
      </el-form-item>

      <el-form-item label="报警类型" prop="alarm_type_name">
        <el-select v-model="querySpecificParams.alarm_type_name" placeholder="报警类型" clearable style="width: 240px">
          <el-option v-for="op in typeWarningOptions" :key="op.value" :label="op.label" :value="op.value"/>
        </el-select>
      </el-form-item>

      <el-form-item label="所属队组" prop="team">
        <el-select v-model="querySpecificParams.team" placeholder="所属队组" clearable style="width: 200px">
          <el-option v-for="op in teamOptions" :key="op.value" :label="op.label" :value="op.value"/>
        </el-select>
      </el-form-item>

      <el-form-item label="报警时间">
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
                        range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期"></el-date-picker>
      </el-form-item>

    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
                   v-hasPermi="['system:role:export']">导出
        </el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="warningList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55"/>
      <el-table-column label="序号" type="index" width="55"/>
      <el-table-column label="报警类型" prop="alarm_type_name" :show-overflow-tooltip="true" width="200"
                       align="center"/>

      <el-table-column label="设备通道名称" prop="device_name" :show-overflow-tooltip="true" width="300"/>
      <el-table-column label="组织名称" prop="org_name" :show-overflow-tooltip="true" width="180"/>
      <el-table-column label="所属队组" prop="team" :show-overflow-tooltip="true" width="180"/>
      <el-table-column label="报警时间" prop="alarm_time" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.alarm_time) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="is_handle" width="80">
        <template slot-scope="scope">
          <span :style="{ color: scope.row.is_handle === 1 ? 'green' : 'orange' }">
            {{ scope.row.is_handle === 1 ? '已处理' : '未处理' }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="操作" class-name="small-padding fixed-width" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-zoom-in" @click="viewDetail(scope.row)">查看详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
                @pagination="fetchData"/>


    <el-dialog :title="title" :visible.sync="openDetails" width="1200px" append-to-body destroy-on-close @close="handleDetailDialogClose">
      <el-row>
        <el-col :span="15">
          <div class="grid-content bg-purple">
            <div class="block">
              <el-image v-if="detailsInfo.picture_absolute_url" :src="detailsInfo.picture_absolute_url"
                        :preview-src-list="[detailsInfo.picture_absolute_url]"></el-image>
              <div v-else>暂无抓拍</div>
            </div>
            <div class="detail-video-toolbar">
              <el-button size="mini" type="primary" icon="el-icon-video-play" :loading="detailVideoLoading"
                         @click="playDetailVideo">
                {{ detailVideoVisible ? '重新加载视频证据' : '播放视频证据' }}
              </el-button>
            </div>
            <div v-if="detailVideoVisible" class="detail-video-panel">
              <player :viewProof="detailVideoVisible" :rtspUrl="rtspUrl" :inline="true"
                      @closeProof="closeDetailVideo" title="视频证据查看">
              </player>
            </div>
          </div>
        </el-col>

        <el-col :span="9">
          <div class="grid-content bg-purple-light">
            <el-descriptions class="margin-top" title="报警信息" :column="1" size="medium"
                             style="margin: 0px 0 35px 40px;">
              <el-descriptions-item label="报警类型"> {{ detailsInfo.alarm_type_name }}</el-descriptions-item>
              <el-descriptions-item label="报警时间"> {{ detailsInfo.alarm_time }}</el-descriptions-item>
              <el-descriptions-item label="设备通道">
                <el-tag size="small"> {{ detailsInfo.device_name }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="处理状态"> {{ isHandled(detailsInfo.is_handle) ? '已处理' : '未处理' }}
              </el-descriptions-item>
              <el-descriptions-item label="处理方式"> {{ isHandled(detailsInfo.is_handle) ? (detailsInfo.h_title || '---') : '---' }}
              </el-descriptions-item>
              <el-descriptions-item label="处理单位"> {{
                  isHandled(detailsInfo.is_handle) ? detailsInfo.h_org_name : '---'
                }}
              </el-descriptions-item>
              <el-descriptions-item label="处理意见"> {{ isHandled(detailsInfo.is_handle) ? detailsInfo.h_remark : '---' }}
              </el-descriptions-item>
              <el-descriptions-item label="处理时间"> {{
                  isHandled(detailsInfo.is_handle) ? detailsInfo.h_create_time : '---'
                }}
              </el-descriptions-item>
            </el-descriptions>
            <el-alert
              v-if="isHandled(detailsInfo.is_handle) && !hasHandleDetail(detailsInfo)"
              title="该告警已标记为已处理，但未找到处理明细记录"
              type="warning"
              :closable="false"
              show-icon
              class="detail-handle-alert"
            />
            <div class="detail-solve-panel">
              <el-divider>处理报警</el-divider>
              <el-form :model="solveData" :rules="solveRules" label-width="80px" ref="solveForm">
                <el-form-item label="处理方式" prop="h_title">
                  <el-radio-group v-model="solveData.h_title">
                    <el-radio label="确认"></el-radio>
                    <el-radio label="误报"></el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="处理意见" prop="h_remark">
                  <el-input type="textarea" :rows="6" v-model="solveData.h_remark"/>
                </el-form-item>
              </el-form>
            </div>
          </div>
        </el-col>
      </el-row>

      <div slot="footer" class="dialog-footer">
        <el-button v-if="detailsInfo.w_id || detailActionRow.w_id" type="primary" @click="comfirmSolve">提 交</el-button>
        <el-button plain @click="openDetails = false">关 闭</el-button>
      </div>
    </el-dialog>

  </div>
</template>

<script>
import {getAlarmTypeFilterOptions, getTeamWaring, getWarningDetail, getWubao, handleWarning} from "@/api/warning";
import {getDeptList} from '@/api/system/kanban';
import player from "@/components/RTSPPlayer"
import store from '@/store'

const formatDateLocal = (date) => {
  const d = date instanceof Date ? date : new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export default {
  name: "Warning",
  dicts: ['sys_normal_disable'],
  components: {player},
  data() {
    return {
      loading: true,
      isHandleOptions: [
        {value: "0", label: "未处理"},
        {value: "1", label: "已处理"}
      ],
      dateRange: [
        formatDateLocal(new Date()),
        formatDateLocal(new Date())
      ],
      orgOptions: [],
      typeWarningOptions: [],
      teamOptions: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        begin: undefined,
        end: undefined,
      },
      querySpecificParams: {
        device_name: undefined,
        org_name: undefined,
        alarm_type_name: undefined,
        alarm_level_name: undefined,
        team: undefined,
        is_handle: undefined,
        w_id: undefined,
      },
      warningList: [],
      title: "",
      openDetails: false,
      detailsInfo: {},
      detailActionRow: {},
      solveData: {
        w_id: "",
        h_title: "",
        h_remark: ""
      },
      solveRules: {
        h_title: [
          {required: true, message: '请选择处理方式', trigger: 'blur'}
        ],
        h_remark: [
          {required: true, message: '请填写处理意见', trigger: 'blur'}
        ]
      },
      auth: "",
      rtspUrl: "",
      detailVideoVisible: false,
      detailVideoLoading: false,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 角色表格数据
      roleList: [],

      // 菜单列表
      menuOptions: [],
      // 组织列表
      deptOptions: [],

      // 表单参数
      form: {},
      defaultProps: {
        children: "children",
        label: "label"
      },
      querySpecificParamsWatch: true,
      dateRangeWatch: true
    };
  },

  computed: {
    deviceContainer() {
      return this.$refs["warningContainer"];
    },
  },

  activated() {
    this.querySpecificParamsWatch = false;
    this.querySpecificParams = {
      device_name: undefined,
      org_name: undefined,
      alarm_type_name: undefined,
      alarm_level_name: undefined,
      team: undefined,
      is_handle: undefined,
      w_id: undefined,
    };
    this.querySpecificParamsWatch = true;
    this.solveRouterQuery();
  },

  mounted() {
    this.fetchQueryOptionData();
    this.$nextTick(() => {
      this.deviceContainer.parentNode.style.backgroundColor = "white";
    });
    this.solveRouterQuery();
  },

  methods: {
    solveRouterQuery() {
      this.querySpecificParamsWatch = false;
      this.dateRangeWatch = false;
      const alarmLevelName = this.$route.query.alarm_level_name;
      const withQue = this.$route.query.withQue;
      const time = this.$route.query.time;
      const type = this.$route.query.alarm_type_name;
      const wid = this.$route.query.wid;
      if (alarmLevelName) this.querySpecificParams.alarm_level_name = alarmLevelName;
      if (type) this.querySpecificParams.alarm_type_name = type;
      if (wid) this.querySpecificParams.w_id = wid;
      if (withQue) {
        const now = new Date();
        switch (withQue) {
          case '1':
            this.dateRange = [
              formatDateLocal(new Date(now.getFullYear(), 0, 2)),
              formatDateLocal(now) // 今天
            ];
            break;
          case '2': // 查看【本月初-今天】的报警数据
            this.dateRange = [formatDateLocal(new Date(now.getFullYear(), now.getMonth(), 2)), formatDateLocal(now)];
            break;
          case '3':
            this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 0, 2)), formatDateLocal(now)];
            this.querySpecificParams.alarm_level_name = "提示";
            break;
          case '4':
            this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 0, 2)), formatDateLocal(now)];
            this.querySpecificParams.alarm_level_name = "警告";
            break;
          case '5': // 查看本年严重数据
            this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 0, 2)), formatDateLocal(now)];
            this.querySpecificParams.alarm_level_name = "严重";
            break;
          case '6': // 查看本月严重数据
            this.dateRange = [formatDateLocal(new Date(now.getFullYear(), now.getMonth(), 2)), formatDateLocal(now)];
            this.querySpecificParams.alarm_level_name = "严重";
            break;
          case '7': // 根据 wid 处理具体事件
            this.querySpecificParams.w_id = this.$route.query.wid;
            break;
          case '8': // 按周 月 季度 年 查询报警数据
            if (time === '周') {
              let startDate = new Date(new Date().setDate(new Date().getDate() - new Date().getDay() + (new Date().getDay() === 0 ? -6 : 1)));
              let endDate = new Date(startDate.getTime() + 6 * 24 * 60 * 60 * 1000);
              startDate = formatDateLocal(startDate);
              endDate = formatDateLocal(endDate);
              this.dateRange = [startDate, endDate];
            } else if (time === '月') {
              this.dateRange = [formatDateLocal(new Date(now.getFullYear(), now.getMonth(), 2)), formatDateLocal(new Date(now.getFullYear(), now.getMonth() + 1, 1))];
            } else if (time === '季度') {
              // 获取当前日期对象及相应季度信息
              let currentDate = now;
              let currentMonth = currentDate.getMonth() + 1;
              let currentQuarter = Math.ceil(currentMonth / 3); // 计算当前季度（1, 2, 3 或 4）
              if (currentQuarter == 1) this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 0, 2)), formatDateLocal(new Date(now.getFullYear(), 3, 1))];
              else if (currentQuarter == 2) this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 3, 2)), formatDateLocal(new Date(now.getFullYear(), 6, 1))];
              else if (currentQuarter == 3) this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 6, 2)), formatDateLocal(new Date(now.getFullYear(), 9, 1))];
              else this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 9, 2)), formatDateLocal(new Date(now.getFullYear() + 1, 0, 1))];
            } else {
              this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 0, 2)), formatDateLocal(new Date(now.getFullYear() + 1, 0, 1))];
            }
            break;
          case '9':
            this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 0, 2)), formatDateLocal(now)];
            this.querySpecificParams.is_handle = '1';
            break;
        }
      }
      this.querySpecificParamsWatch = true;
      this.dateRangeWatch = true;
      this.fetchData();

    },
    // 处理查询时间
    handleTime() {
      if (this.dateRange == null || this.dateRange.length === 0) {
        this.queryParams.begin = undefined;
        this.queryParams.end = undefined;
        return;
      }
      const formattedDateRange = [
        this.dateRange[0] + ' 00:00:00',
        this.dateRange[1] + ' 23:59:59'
      ];

      const timestamps = formattedDateRange.map(dateStr => {
        const date = new Date(dateStr);
        return Math.round(date.getTime() / 1000);
      });

      if (timestamps.length === 2) {
        this.queryParams.begin = timestamps[0];
        this.queryParams.end = timestamps[1];
      }
    },

    // 导出数据
    handleExport() {
      const {pageNum, pageSize, ...newQueryParams} = this.queryParams;
      this.download('/waring/waring/importTemplate', {
        ...newQueryParams
      }, `报警信息_${new Date().getTime()}.xlsx`)
    },

    // 获取报警列表
    async fetchData() {
      try {
        this.loading = true;
        this.handleTime();
        const response = await getWubao({...this.queryParams, ...this.querySpecificParams});
        this.warningList = response.rows;
        this.total = response.total;
        this.loading = false;
        this.auth = response.token;
      } catch (error) {
        console.error(error);
      }
    },

    async fetchQueryOptionData() {
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
        const typeWarningRes = await getAlarmTypeFilterOptions();
        this.typeWarningOptions = typeWarningRes.data.map(item => ({
          value: item.alarm_type_name,
          label: item.alarm_type_name
        }));
        const teamWarningRes = await getTeamWaring();
        this.teamOptions = teamWarningRes.data.map(item => ({
          value: item.team_name,
          label: item.team_name
        }));
      } catch (error) {
        console.error(error);
      }
    },

    // 查询数据
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.fetchData();
    },

    // // 重置查询参数
    // resetQuery() {
    //   this.dateRange = [];
    //   this.resetForm("queryForm");
    //   this.handleQuery();
    // },

    // 查看详情
    async viewDetail(row) {
      const id = row.w_id;
      try {
        const response = await getWarningDetail(id);
        this.detailsInfo = response.data;
        this.detailActionRow = Object.assign({}, row || {}, response.data || {});
        this.resetSolveForm(this.detailActionRow);
        this.closeDetailVideo();
        this.openDetails = true;
        this.title = "报警详情";
      } catch (error) {
        console.error(error);
      }
    },

    // 提交处理
    comfirmSolve() {
      this.$refs['solveForm'].validate(async (valid) => {
        if (valid) {
          try {
            const response = await handleWarning(this.solveData);
            if (response.code !== 200) throw new Error(response.message);
            await this.fetchData();
            const detailResponse = await getWarningDetail(this.solveData.w_id);
            this.detailsInfo = detailResponse.data;
            this.detailActionRow = Object.assign({}, this.detailActionRow, detailResponse.data || {});
            this.resetSolveForm(this.detailActionRow);
          } catch (error) {
            console.error(error);
          }
        } else return false;
      });
    },

    resetSolveForm(detail = {}) {
      this.solveData = {
        w_id: detail.w_id || "",
        h_title: detail.h_title || "",
        h_remark: detail.h_remark || ""
      };
      this.$nextTick(() => {
        if (this.$refs.solveForm) {
          this.$refs.solveForm.clearValidate();
        }
      });
    },

    handleDetailDialogClose() {
      this.closeDetailVideo();
      this.resetSolveForm({});
      this.detailsInfo = {};
      this.detailActionRow = {};
    },

    closeDetailVideo() {
      this.detailVideoVisible = false;
      this.rtspUrl = "";
    },

    async playDetailVideo() {
      await this.viewVideo(this.detailActionRow);
    },

    toAbsoluteMediaUrl(path) {
      if (!path) return "";
      if (/^https?:\/\//i.test(path)) return path;
      if (path.startsWith('/')) return `${window.location.origin}${path}`;
      return `${window.location.origin}/${path}`;
    },

    resolveVideoMediaUrl(row) {
      const absoluteVideoPath = row && row.video_absolute_url;
      if (absoluteVideoPath) {
        return this.toAbsoluteMediaUrl(absoluteVideoPath);
      }
      const relativeVideoPath = row && row.video_url;
      if (/^\/?alarm\//i.test(relativeVideoPath || '')) {
        return this.toAbsoluteMediaUrl(relativeVideoPath.startsWith('/') ? relativeVideoPath : `/${relativeVideoPath}`);
      }
      return this.toAbsoluteMediaUrl(relativeVideoPath);
    },

    // 查看视频证据
    async viewVideo(row) {
      if (!row || !row.device_id || !row.alarm_time) {
        this.$modal.msgError("缺少视频取证信息");
        return;
      }

      this.detailVideoLoading = true;
  const localVideoUrl = this.resolveVideoMediaUrl(row);
      if (localVideoUrl) {
        this.rtspUrl = localVideoUrl;
        this.detailVideoVisible = true;
        this.detailVideoLoading = false;
        return;
      }

      this.$modal.msgError("视频不存在");
      this.detailVideoLoading = false;
    },

    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.w_id)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },

    isHandled(value) {
      return String(value) === '1';
    },

    hasHandleDetail(detail = {}) {
      return !!(detail.h_title || detail.h_org_name || detail.h_remark || detail.h_create_time);
    },
  },

  watch: {
    querySpecificParams: {
      handler(newVal, oldVal) {
        if (this.querySpecificParamsWatch) this.handleQuery();
      },
      deep: true,
    },

    dateRange(newVal, oldVal) {
      if (this.dateRangeWatch) this.handleQuery();
    }
  }
};
</script>

<style scoped>
.detail-video-toolbar {
  margin-top: 16px;
}

.detail-video-panel {
  margin-top: 16px;
}

.detail-solve-panel {
  margin-left: 40px;
  margin-right: 16px;
}

.detail-handle-alert {
  margin: 0 16px 16px 40px;
}
</style>
