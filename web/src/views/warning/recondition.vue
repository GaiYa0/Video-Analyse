<template>
  <div class="app-container">
    <!-- 查询参数 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">

      <el-form-item label="设备通道名称" prop="device_name">
        <el-input v-model="querySpecificParams.device_name" placeholder="请输入设备通道名称" clearable
                  style="width: 200px"
                  @keyup.enter.native="handleQuery"/>
      </el-form-item>

      <el-form-item label="所属队组" prop="team">
        <el-select v-model="querySpecificParams.team" placeholder="所属队组" clearable style="width: 200px">
          <el-option v-for="op in teamOptions" :key="op.value" :label="op.label" :value="op.value"/>
        </el-select>
      </el-form-item>

      <el-form-item label="识别时间">
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
                        range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期"></el-date-picker>
      </el-form-item>

    </el-form>

    <el-table v-loading="loading" :data="warningList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55"/>
      <el-table-column label="序号" type="index" width="55"/>
      <el-table-column label="类型" prop="alarm_type_name" :show-overflow-tooltip="true" width="200"
                       align="center"/>
      <el-table-column label="设备通道名称" prop="device_name" :show-overflow-tooltip="true" width="300"/>
      <el-table-column label="组织名称" prop="org_name" :show-overflow-tooltip="true" width="180"/>
      <el-table-column label="所属队组" prop="team" :show-overflow-tooltip="true" width="180"/>
      <el-table-column label="识别时间" prop="alarm_time" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.alarm_time) }}</span>
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
      :show-alarm-level="true"
      @close="handleDetailDialogClose"
      @submit="comfirmSolve"
      @play-video="playDetailVideo"
      @close-video="closeDetailVideo"
    />

  </div>
</template>

<script>
import { getRecondition } from '@/api/warning'
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
      dateRange: [
        formatDateLocal(new Date()),
        formatDateLocal(new Date())
      ],
      orgOptions: [],
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
        is_handle: undefined,
        team: undefined,
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

    };
  },

  mounted() {
    this.fetchData();
    this.fetchQueryOptionData();
  },

  methods: {
    fetchWarningList(params) {
      return getRecondition(params)
    },
  },

  watch: {
    querySpecificParams: {
      handler(newVal, oldVal) {
        this.handleQuery();
      },
      deep: true,
    },

    dateRange(newVal, oldVal) {
      this.handleQuery();
    }
  }
};
</script>
