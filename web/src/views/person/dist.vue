<template>
  <div class="app-container" style="min-height: 700px;">
    <!-- 查询参数 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
      <el-form-item label="日期">
        <el-date-picker v-model="dateRange" style="width: 160px" value-format="yyyy-MM-dd"
                        :clearable="false"></el-date-picker>
      </el-form-item>
      <!--      <el-form-item label="班组" prop="is_handle">-->
      <!--        <el-select v-model="queryParams.is_handle" placeholder="班组" clearable style="width: 200px">-->
      <!--          <el-option v-for="op in isHandleOptions" :key="op.value" :label="op.label" :value="op.value"/>-->
      <!--        </el-select>-->
      <!--      </el-form-item>-->
    </el-form>

    <el-table v-loading="loading" :data="warningTypeList">
      <el-table-column type="selection" width="55"/>
      <el-table-column label="序号" type="index" width="55" align="center"/>

      <el-table-column label="姓名" prop="person_name" :show-overflow-tooltip="true"/>
      <el-table-column label="工号/身份证号" prop="person_no" :show-overflow-tooltip="true"/>
      <el-table-column label="图片" prop="attendance_identification_absolute" lable="图片"
                       :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <el-image :src="scope.row.attendance_identification_absolute"
                    :preview-src-list="[scope.row.attendance_identification_absolute]"
                    style="width: 30px; height: 30px"></el-image>
        </template>
      </el-table-column>
      <el-table-column label="所属组织" prop="index_path_name" :show-overflow-tooltip="true"/>
      <el-table-column label="巡检点" prop="site_name" :show-overflow-tooltip="true"/>
      <el-table-column label="巡检时间" prop="pass_time" :show-overflow-tooltip="true"/>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="pageParams.pageNum" :limit.sync="pageParams.pageSize"
                @pagination="fetchData"/>
  </div>
</template>

<script>
import {checkWork} from "@/api/person";

export default {
  data() {
    return {
      loading: true,
      isHandleOptions: [
        {value: "0", label: "早班"},
        {value: "1", label: "中班"},
        {value: "2", label: "晚班"},
      ],
      orgOptions: [],
      typeWarningOptions: [],
      pageParams: {
        pageNum: 1,
        pageSize: 10,
      },
      queryParams: {
        is_handle: undefined,
      },
      warningTypeList: [],
      // 总条数
      total: 0,
      // 显示搜索条件
      showSearch: true,
      // 表单参数
      form: {},
      dateRange: new Date().toISOString().slice(0, 10),
      dateRangeWatch: true,
      queryParamsWatch: true
    };
  },

  mounted() {
    this.fetchData();
  },

  methods: {
    async fetchData() {
      try {
        this.loading = true;
        this.handleTime();
        this.queryParams.type = 2;
        const response = await checkWork({...this.queryParams, ...this.pageParams});
        this.warningTypeList = response.rows;
        this.total = response.total;
        this.loading = false;
      } catch (error) {
        console.error(error);
      }
    },
    // 查询数据
    handleQuery() {
      this.pageParams.pageNum = 1;
      // this.querySpecificParams = {
      //   is_handle: undefined,
      // };
      this.fetchData();
    },
    handleTime() {
      const formattedDateRange = [
        this.dateRange + ' 00:00:00.000',
        this.dateRange + ' 23:59:59.999'
      ];
      const timestamps = formattedDateRange.map(dateStr => {
        const date = new Date(dateStr);
        return Math.round(date.getTime());
      });

      if (timestamps.length === 2) {
        this.queryParams.begin = timestamps[0];
        this.queryParams.end = timestamps[1];
      }
    },
  },
  watch: {
    queryParams: {
      handler(newVal, oldVal) {
        this.handleQuery();
      },
      deep: true,
    },
    dateRange(newVal, oldVal) {
      if (this.dateRangeWatch) this.handleQuery();
    }
  }
};
</script>
