<template>
  <div style="min-height: 700px;">
    <el-page-header @back="goback" :content="warningTitle" style="margin-bottom: 18px; font-size: medium;">
    </el-page-header>
    <el-table v-loading="loading" :data="warningList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55"/>
      <el-table-column label="序号" type="index" width="55"/>
      <el-table-column label="报警类型" prop="alarm_type_name" :show-overflow-tooltip="true" width="200"
                       align="center"/>
      <el-table-column label="报警等级" prop="alarm_level_name" :show-overflow-tooltip="true" width="120">
        <template slot-scope="scope">
          <span :style="{ color: alarmLevelColors[scope.row.alarm_level_name] }">
            {{ scope.row.alarm_level_name }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="设备通道名称" prop="device_name" :show-overflow-tooltip="true" width="300"/>
      <el-table-column label="组织名称" prop="org_name" :show-overflow-tooltip="true" width="180"/>
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
              <el-descriptions-item label="报警等级"> {{ detailsInfo.alarm_level_name }}</el-descriptions-item>
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
            title="视频证据查看">
    </player>

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
import {getWarningDetail, handleWarning} from "@/api/warning";
import {getHistoryWaring} from "@/api/device";
import player from "@/components/RTSPPlayer"

export default {
  name: "Warning",
  dicts: ['sys_normal_disable'],
  components: {player},
  props: {
    warningTitle: {
      type: String,
      default: ''
    },
    device_id: {
      type: String,
      default: ''
    },
  },
  data() {
    return {
      loading: true,
      alarmLevelColors: {
        提示: 'green',
        警告: 'olive',
        严重: 'orange'
      },
      dateRange: [
        new Date().toISOString().slice(0, 10),
        new Date().toISOString().slice(0, 10)
      ],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
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
      // 总条数
      total: 0,

      // 表单参数
      form: {},
      defaultProps: {
        children: "children",
        label: "label"
      },

    };
  },

  methods: {
    goback() {
      this.$emit("closeWarning");
    },

    // 获取报警列表
    async fetchData() {
      try {
        this.loading = true;
        const response = await getHistoryWaring({device_id: this.device_id, ...this.queryParams});
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

    // 查询数据
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.fetchData();
    },

    //
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
    device_id(newVal, oldVal) {
      this.handleQuery();
    }
  }
};
</script>
