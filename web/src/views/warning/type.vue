<template>
  <div class="app-container" style="min-height: 700px;">
    <!-- 查询参数 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true">
      <el-form-item label="通道编码" prop="device_id">
        <el-input v-model="queryParams.device_id" placeholder="请输入设备通道编码" clearable style="width: 200px"
                  @keyup.enter.native="handleQuery"/>
      </el-form-item>

      <el-form-item label="通道名称" prop="device_name">
        <el-input v-model="queryParams.device_name" placeholder="请输入设备通道名称" clearable style="width: 200px"
                  @keyup.enter.native="handleQuery"/>
      </el-form-item>

      <el-form-item label="报警类型" prop="alarm_type_name">
        <el-select v-model="queryParams.alarm_type_name" placeholder="报警类型" clearable style="width: 240px">
          <el-option v-for="op in typeWarningOptions" :key="op.value" :label="op.label" :value="op.value"/>
        </el-select>
      </el-form-item>

      <el-form-item label="是否使用" prop="is_handle">
        <el-select v-model="queryParams.is_handle" placeholder="使用状态" clearable style="width: 200px">
          <el-option v-for="op in isHandleOptions" :key="op.value" :label="op.label" :value="op.value"/>
        </el-select>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
                   v-hasPermi="['system:role:add']">新增
        </el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
                   v-hasPermi="['system:role:remove']">删除
        </el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="warningTypeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55"/>
      <el-table-column label="序号" type="index" width="55" align="center"/>

      <el-table-column label="组织名称" prop="org_name" :show-overflow-tooltip="true"/>
      <el-table-column label="通道编码" prop="device_id" :show-overflow-tooltip="true"/>
      <el-table-column label="通道名称" prop="device_name" :show-overflow-tooltip="true"/>
      <el-table-column label="报警类型" prop="alarm_type_name" :show-overflow-tooltip="true"/>

      <el-table-column label="使用状态" prop="is_handle" width="80">
        <template slot-scope="scope">
          <span :style="{ color: scope.row.is_handle === 1 ? 'green' : 'black' }">
            {{ scope.row.is_handle === 1 ? '未使用' : '使用' }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="操作" class-name="small-padding fixed-width" align="center">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-zoom-in" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="pageParams.pageNum" :limit.sync="pageParams.pageSize"
                @pagination="fetchData"/>

    <el-dialog title="增加类型" :visible.sync="showAdd" width="700px" append-to-body>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form :model="addData" :rules="addRules" label-width="80px" ref="addForm">
            <el-form-item label="通道编码" prop="device_id">
              <el-input v-model="addData.device_id" placeholder="请输入设备通道编码"/>
            </el-form-item>

            <el-form-item label="类型编码" prop="alarm_type" pl>
              <el-input v-model="addData.alarm_type" placeholder="请输入报警类型编码"/>
            </el-form-item>

            <el-form-item label="组织编码" prop="org_index" pl>
              <el-input v-model="addData.org_index" placeholder="请输入组织编码"/>
            </el-form-item>
          </el-form>

        </el-col>

        <el-col :span="12">
          <el-form :model="addData" :rules="addRules" label-width="80px" ref="addForm">
            <el-form-item label="通道名称" prop="device_name">
              <el-input v-model="addData.device_name" placeholder="请输入设备通道名称"/>
            </el-form-item>

            <el-form-item label="类型名称" prop="alarm_type_name" pl>
              <el-input v-model="addData.alarm_type_name" placeholder="请输入报警类型名称"/>
            </el-form-item>

            <el-form-item label="组织名称" prop="org_name" pl>
              <el-input v-model="addData.org_name" placeholder="请输入组织名称"/>
            </el-form-item>

            <el-form-item label="是否使用" prop="is_handle">
              <el-switch v-model="addData.is_handle"></el-switch>
            </el-form-item>
          </el-form>
        </el-col>
      </el-row>


      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="comfirmAdd">确 认</el-button>
        <el-button @click="showAdd = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {deleteTypes, getTypeWaring, getWarningTypeList, insertType} from "@/api/warning";
import {getDeptList} from '@/api/system/kanban';

export default {
  dicts: ['sys_normal_disable'],
  data() {
    return {
      loading: true,
      isHandleOptions: [
        {value: "0", label: "使用"},
        {value: "1", label: "未使用"}
      ],
      orgOptions: [],
      typeWarningOptions: [],
      pageParams: {
        pageNum: 1,
        pageSize: 10,
      },
      queryParams: {
        device_name: undefined,
        device_id: undefined,
        org_name: undefined,
        alarm_type_name: undefined,
        alarm_level: undefined,
        is_handle: undefined,
      },
      warningTypeList: [],
      showAdd: false,
      addData: {
        device_id: "",
        device_name: "",
        alarm_type: "",
        alarm_type_name: "",
        alarm_level: "",
        alarm_level_name: "",
        org_index: "",
        org_name: "",
        is_handle: true,
      },
      addRules: {
        device_id: [
          {required: true, message: '请输入通道编码', trigger: 'blur'}
        ],
        device_name: [
          {required: true, message: '请输入通道名称', trigger: 'blur'}
        ],
        alarm_type: [
          {required: true, message: '请输入报警类型编码', trigger: 'blur'}
        ],
        alarm_type_name: [
          {required: true, message: '请输入报警类型名称', trigger: 'blur'}
        ],
        org_index: [
          {required: true, message: '请输入组织编码', trigger: 'blur'}
        ],
        org_name: [
          {required: true, message: '请输入组织名称', trigger: 'blur'}
        ],
        is_handle: [
          {required: true, message: '请选择是否使用', trigger: 'blur'}
        ],
      },
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

  mounted() {
    this.fetchData();
    this.fetchQueryOptionData();
  },

  methods: {
    async fetchData() {
      try {
        this.loading = true;
        const response = await getWarningTypeList({...this.queryParams, ...this.pageParams});
        this.warningTypeList = response.rows;
        this.total = response.total;
        this.loading = false;
        this.auth = response.token;
      } catch (error) {
        console.error(error);
      }
    },

    async fetchQueryOptionData() {
      try {
        const [deptListRes, typeWarningRes] = await Promise.all([getDeptList(), getTypeWaring()]);
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
      } catch (error) {
        console.error(error);
      }
    },

    // 查询数据
    handleQuery() {
      this.pageParams.pageNum = 1;
      this.fetchData();
    },

    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.t_id)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },

    handleAdd() {
      this.showAdd = true;
    },

    async comfirmAdd() {
      try {
        const addRequest = {...this.addData};
        addRequest.is_handle = addRequest.is_handle == true ? 0 : 1;
        const response = await insertType(addRequest);
        if (response.code != 200) throw new Error(response.msg);
        this.$message({
          message: '添加成功',
          type: 'success'
        });
        this.showAdd = false;
        this.handleQuery();
      } catch (error) {
        console.error(error);
      }
    },

    handleDelete(row) {
      const deleteIds = row.t_id || this.ids;
      this.$modal.confirm('确定要进行删除操作吗？').then(() => {
        return deleteTypes(deleteIds);
      }).then(() => {
        this.fetchData();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {
      });
    }
  },

  watch: {
    queryParams: {
      handler(newVal, oldVal) {
        this.handleQuery();
      },
      deep: true,
    },
  }
};
</script>
