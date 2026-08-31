<template>
  <div class="app-container" style="min-height: 700px;">
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
          <el-button size="mini" type="text" icon="el-icon-edit" @click="solveWarning(scope.row)"
                     :disabled="scope.row.is_handle == 1">处理报警
          </el-button>
          <el-button size="mini" type="text" icon="el-icon-video-camera" @click="viewVideo(scope.row)">视频证据
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
                @pagination="fetchData"/>


    <el-dialog :title="title" :visible.sync="openDetails" width="1000px" append-to-body destroy-on-close>
      <el-row>
        <el-col :span="15">
          <div class="grid-content bg-purple">
            <div class="block">
              <el-image v-if="detailsInfo.picture_absolute_url" :src="detailsInfo.picture_absolute_url"
                        :preview-src-list="[detailsInfo.picture_absolute_url]"></el-image>
              <div v-else>暂无抓拍</div>
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
              <el-descriptions-item label="处理状态"> {{ detailsInfo.is_handle === '1' ? '已处理' : '未处理' }}
              </el-descriptions-item>
              <el-descriptions-item label="处理单位"> {{
                  detailsInfo.is_handle === '1' ? detailsInfo.h_org_name : '---'
                }}
              </el-descriptions-item>
              <el-descriptions-item label="处理意见"> {{ detailsInfo.is_handle === '1' ? detailsInfo.h_remark : '---' }}
              </el-descriptions-item>
              <el-descriptions-item label="处理时间">
                {{ detailsInfo.is_handle === '1' ? detailsInfo.h_create_time : '---' }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>
      </el-row>

      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="openDetails = false">关 闭</el-button>
      </div>
    </el-dialog>

    <player :viewProof="viewProof" :rtspUrl="rtspUrl" @closeProof="viewProof = false" v-show="viewProof"
            title="视频证据查看"></player>

    <el-dialog :title="title" :visible.sync="openSolve" width="500px" append-to-body>
      <el-form :model="solveData" :rules="solveRules" label-width="80px" ref="solveForm">
        <el-form-item label="处理方式" prop="h_title">
          <el-radio-group v-model="solveData.h_title">
            <el-radio label="确认"></el-radio>
            <el-radio label="误报"></el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理意见" prop="h_remark">
          <el-input type="textarea" v-model="solveData.h_remark"/>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="comfirmSolve">确 认</el-button>
        <el-button @click="openSolve = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {getAlarmTypeFilterOptions, getTeamWaring, getWarningDetail, handleWarning} from "@/api/warning";
import {getTestData} from "@/api/test";
import {getDeptList} from '@/api/system/kanban';
import player from "@/components/RTSPPlayer"

export default {
  name: "Warning",
  dicts: ['sys_normal_disable'],
  components: {player},
  data() {
    return {
      loading: true,
      dateRange: [],
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
        is_handle: undefined,
      },
      warningList: [],
      title: "",
      openDetails: false,
      detailsInfo: [],
      openSolve: false,
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
      viewProof: false,
      rtspUrl: "",
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
        const response = await getTestData({...this.queryParams, ...this.querySpecificParams});
        this.warningList = response.rows;
        this.total = response.total;
        this.loading = false;
      } catch (error) {
        console.error(error);
      }
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

    async fetchQueryOptionData() {
      try {
        const [deptListRes, typeWarningRes] = await Promise.all([getDeptList(), getAlarmTypeFilterOptions()]);
        this.orgOptions = [
          {
            value: '',
            label: '全部'
          },
          ...deptListRes.data.map((item) => ({
            value: item.deptName,
            label: item.deptName
          }))
        ];
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
        this.openDetails = true;
        this.title = "报警详情";
      } catch (error) {
        console.error(error);
      }
    },

    // 处理报警
    solveWarning(row) {
      this.solveData.w_id = row.w_id;
      this.title = "处理报警";
      this.openSolve = true;
    },

    // 提交处理
    comfirmSolve() {
      this.$refs['solveForm'].validate(async (valid) => {
        if (valid) {
          try {
            const response = await handleWarning(this.solveData);
            if (response.code !== 200) throw new Error(response.message);
            await this.fetchData();
            this.openSolve = false;
            this.solveData = {
              w_id: "",
              h_title: "",
              h_remark: ""
            };
          } catch (error) {
            console.error(error);
          }
        } else return false;
      });
    },

    // 查看视频证据
    async viewVideo(row) {
      const localVideoUrl = this.resolveVideoMediaUrl(row);
      if (!localVideoUrl) {
        this.$modal.msgError("视频不存在");
        return;
      }

      this.rtspUrl = localVideoUrl;
      this.viewProof = true;
    },

    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.w_id)
      this.single = selection.length != 1
      this.multiple = !selection.length
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
