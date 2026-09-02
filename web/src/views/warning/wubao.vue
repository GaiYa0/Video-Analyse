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
                       align="center">
        <template slot-scope="scope">
          <span
            class="alarm-type-badge"
            :class="{ 'is-sleep': isSleepType(scope.row.alarm_type_name) }"
          >{{ scope.row.alarm_type_name || '—' }}</span>
        </template>
      </el-table-column>

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


    <warning-detail-dialog
      ref="detailDialog"
      :visible.sync="openDetails"
      :title="title"
      :details-info="detailsInfo"
      :solve-data="solveData"
      :solve-rules="solveRules"
      :detail-video-visible="detailVideoVisible"
      :detail-video-loading="detailVideoLoading"
      :rtsp-url="rtspUrl"
      :action-row-id="detailActionRow.w_id"
      @close="handleDetailDialogClose"
      @submit="comfirmSolve"
      @play-video="playDetailVideo"
      @close-video="closeDetailVideo"
    />

  </div>
</template>

<script>
import { getWubao } from '@/api/warning'
import WarningDetailDialog from './components/WarningDetailDialog.vue'
import warningListMixin, { formatDateLocal } from './mixins/warningListMixin'

export default {
  name: "Warning",
  dicts: ['sys_normal_disable'],
  mixins: [warningListMixin],
  components: { WarningDetailDialog },
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
      this.deviceContainer.parentNode.style.backgroundColor = "var(--sva-bg)";
    });
    this.solveRouterQuery();
  },

  methods: {
    fetchWarningList(params) {
      return getWubao(params)
    },
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
