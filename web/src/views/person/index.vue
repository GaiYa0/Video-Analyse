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

      <el-table-column label="区域名称" prop="area_name" :show-overflow-tooltip="true"/>
      <el-table-column label="入井人数" prop="statistic_in_person_count" :show-overflow-tooltip="true"/>
      <el-table-column label="升井人数" prop="statistic_out_person_count" :show-overflow-tooltip="true"/>
      <el-table-column label="在场人数" prop="statistic_person_count" :show-overflow-tooltip="true"/>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="pageParams.pageNum" :limit.sync="pageParams.pageSize"
                @pagination="fetchData"/>
  </div>
</template>

<script>
import {getPersonList} from "@/api/person";

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
        dateRange: undefined,
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
        const response = await getPersonList({...this.queryParams, ...this.pageParams});
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
      this.querySpecificParams = {
        is_handle: undefined,
      };
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
    }
  },
  watch: {
    queryParams: {
      handler(newVal, oldVal) {
        if (this.queryParamsWatch) this.handleQuery();
      },
      deep: true,
    },
    dateRange(newVal, oldVal) {
      if (this.dateRangeWatch) this.handleQuery();
    }
  }
};
</script>
