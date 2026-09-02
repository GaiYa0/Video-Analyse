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

      <el-form-item label="处理状态" prop="is_handle">
        <el-select v-model="querySpecificParams.is_handle" placeholder="处理状态" clearable style="width: 200px">
          <el-option v-for="op in isHandleOptions" :key="op.value" :label="op.label" :value="op.value"/>
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
      <el-table-column label="规则信息" min-width="220" align="center">
        <template slot-scope="scope">
          <div class="rule-summary-primary">{{ formatRuleSummary(scope.row) }}</div>
          <div v-if="formatLifecycleSummary(scope.row) !== '---'" class="rule-summary-secondary">
            {{ formatLifecycleSummary(scope.row) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="is_handle" width="80">
        <template slot-scope="scope">
          <span :style="{ color: scope.row.is_handle === 1 ? 'green' : 'orange' }">
            {{ scope.row.is_handle === 1 ? '已处理' : '未处理' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="AI复核" min-width="180" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="getAiReviewStatusType(scope.row.ai_review_status, scope.row.ai_review_decision)">
            {{ getAiReviewStatusLabel(scope.row.ai_review_status, scope.row.ai_review_decision) }}
          </el-tag>
          <div v-if="scope.row.ai_review_summary" class="ai-review-summary">
            {{ scope.row.ai_review_summary }}
          </div>
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
      :show-sleep-badge="true"
      :show-sva-fields="true"
      :show-ai-fields="true"
      @close="handleDetailDialogClose"
      @submit="comfirmSolve"
      @play-video="playDetailVideo"
      @close-video="closeDetailVideo"
    />
  </div>
</template>

<script>
import { getWarningList } from '@/api/warning'
import { getBehaviorTypeLabel as resolveBehaviorTypeLabel, isKnownBehaviorType } from '@/utils/behaviorTypes'
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
      is_handle: undefined,
      team: undefined,
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
  beforeRouteUpdate(to, from, next) {
    next(); // 先让路由更新
    this.$nextTick(() => {
      this.fetchQueryOptionData();
    this.$nextTick(() => {
      this.deviceContainer.parentNode.style.backgroundColor = "var(--sva-bg)";
    });
    this.querySpecificParams.w_id = undefined;//清除搜索内容
      this.solveRouterQuery(); 
    });
  },
  methods: {
    fetchWarningList(params) {
      return getWarningList(params)
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
            this.querySpecificParams.is_handle = this.$route.query.is_handle;
            break;
          case '3':
            this.dateRange = [formatDateLocal(new Date(now.getFullYear(), 0, 2)), formatDateLocal(now)];
            this.querySpecificParams.is_handle = this.$route.query.is_handle;
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
            this.dateRange = [];
            // this.dateRange = [new Date(new Date().getFullYear(), 0, 2).toISOString().slice(0, 10), new Date().toISOString().slice(0, 10)];
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
              this.querySpecificParams.team = this.$route.query.team;
            }
            break;
        }
      }
      this.querySpecificParamsWatch = true;
      this.dateRangeWatch = true;
      this.fetchData();
    },

    getAiReviewStatusLabel(status, decision) {
      if (!status) return '未复核';
      if (status === 'PENDING') return '待复核';
      if (status === 'RUNNING') return '复核中';
      if (status === 'FAILED') return '复核失败';
      if (status === 'SKIPPED') return '已跳过';
      if (status === 'SUCCESS') return this.getAiDecisionLabel(decision);
      return status;
    },

    getAiReviewStatusType(status, decision) {
      if (!status) return 'info';
      if (status === 'PENDING' || status === 'RUNNING') return 'warning';
      if (status === 'FAILED') return 'danger';
      if (status === 'SKIPPED') return 'info';
      if (status === 'SUCCESS') {
        if (decision === 'false_alarm') return 'danger';
        if (decision === 'true_alarm') return 'success';
        return 'warning';
      }
      return 'info';
    },

    getAiDecisionLabel(decision) {
      if (decision === 'true_alarm') return '疑似真实告警';
      if (decision === 'false_alarm') return '疑似误报';
      if (decision === 'uncertain') return '待人工确认';
      return '---';
    },

    getBehaviorTypeLabel(behaviorType) {
      if (behaviorType === undefined || behaviorType === null || behaviorType === '') {
        return '---';
      }
      if (!isKnownBehaviorType(behaviorType)) {
        return '---';
      }
      return resolveBehaviorTypeLabel(behaviorType);
    },

    getEventStateLabel(eventState) {
      if (eventState === 'start') return '开始';
      if (eventState === 'update') return '持续';
      if (eventState === 'end') return '结束';
      return '---';
    },

    getCrossingDirectionLabel(direction) {
      if (direction === 'left_to_right') return '左到右';
      if (direction === 'right_to_left') return '右到左';
      if (direction === 'both') return '双向';
      if (direction === 'unknown') return '未知';
      return direction || '---';
    },

    formatDuration(durationMs) {
      if (durationMs === undefined || durationMs === null || durationMs === '') {
        return '---';
      }
      const duration = Number(durationMs);
      if (!Number.isFinite(duration) || duration < 0) {
        return '---';
      }
      if (duration < 1000) {
        return `${duration}ms`;
      }
      const totalSeconds = Math.floor(duration / 1000);
      const hours = Math.floor(totalSeconds / 3600);
      const minutes = Math.floor((totalSeconds % 3600) / 60);
      const seconds = totalSeconds % 60;
      const parts = [];
      if (hours > 0) parts.push(`${hours}小时`);
      if (minutes > 0) parts.push(`${minutes}分`);
      if (seconds > 0 || parts.length === 0) parts.push(`${seconds}秒`);
      return parts.join('');
    },

    formatRuleSummary(row = {}) {
      const behavior = this.getBehaviorTypeLabel(row.sva_behavior_type);
      if (behavior === '---') {
        return '---';
      }
      const name = row.sva_behavior_type === 'cross_line'
        ? (row.sva_line_name || row.sva_line_id || '')
        : (row.sva_region_name || row.sva_region_id || '');
      const suffix = row.sva_behavior_type === 'cross_line'
        ? this.getCrossingDirectionLabel(row.sva_crossing_direction)
        : '';
      return [behavior, name, suffix && suffix !== '---' ? suffix : ''].filter(Boolean).join(' / ');
    },

    formatLifecycleSummary(row = {}) {
      const state = this.getEventStateLabel(row.sva_event_state);
      const duration = this.formatDuration(row.duration_ms);
      const parts = [];
      if (state !== '---') parts.push(state);
      if (duration !== '---') parts.push(duration);
      if (row.end_time) parts.push(`结束 ${row.end_time}`);
      return parts.length > 0 ? parts.join(' | ') : '---';
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
    },
    
  }
};
</script>

<style scoped>
.ai-review-summary {
  margin-top: 4px;
  color: var(--sva-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.rule-summary-primary {
  color: var(--sva-text);
  line-height: 1.4;
}

.rule-summary-secondary {
  margin-top: 4px;
  color: var(--sva-text-muted);
  font-size: 12px;
  line-height: 1.4;
}
</style>
