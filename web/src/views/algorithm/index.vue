<!-- 算法配置页面 -->
<template>
    <div class="app-container">
      <upload-dialog v-model="isDialogOpen" />
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch">
        <el-form-item label="任务名称" prop="taskName">
          <el-input
            v-model="queryParams.taskName"
            placeholder="请输入任务名称"
            clearable
            style="width: 240px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="解析类型" prop="type">
          <el-select
            v-model="queryParams.type"
            placeholder="请选择解析类型"
            clearable
            style="width: 240px"
          >
            <el-option
              v-for="dict in dict.type.sys_normal_disable"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
          <el-button type="success" icon="el-icon-upload" size="mini" @click="uploadalgorithm">上传</el-button>
          <el-button type="danger" icon="el-icon-delete" size="mini" @click="resetQuery">删除</el-button>

        </el-form-item>
      </el-form>
  
      <!-- <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="el-icon-plus"
            size="mini"
            @click="handleAdd"
            v-hasPermi="['system:role:add']"
          >新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="success"
            plain
            icon="el-icon-edit"
            size="mini"
            :disabled="single"
            @click="handleUpdate"
            v-hasPermi="['system:role:edit']"
          >修改</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="danger"
            plain
            icon="el-icon-delete"
            size="mini"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['system:role:remove']"
          >删除</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="warning"
            plain
            icon="el-icon-download"
            size="mini"
            @click="handleExport"
            v-hasPermi="['system:role:export']"
          >导出</el-button>
        </el-col>
        <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
      </el-row> -->
  
      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="序号" prop="id" width="120" />
        <el-table-column label="解析类型" prop="type" :show-overflow-tooltip="true" width="150" />
        <el-table-column label="任务名称" prop="algorithm_name" :show-overflow-tooltip="true" width="150" />
        <el-table-column label="煤矿名称" prop="mineName" :show-overflow-tooltip="true" width="150" />
        <el-table-column label="报警设备" prop="device" :show-overflow-tooltip="true" width="150" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分析区域" prop="areaKey" :show-overflow-tooltip="true" width="150" />
        <el-table-column label="算法运行状态" prop="statusKey" :show-overflow-tooltip="true" width="150" />
        <el-table-column label="更新时间" align="center" prop="upgradeTime" width="180">
          <template slot-scope="scope">
            <span>{{ parseTime(scope.row.upgradeTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" :show-overflow-tooltip="true" width="150" />
      </el-table>
  
      <!-- <pagination
        v-show="total>0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      /> -->
  
      <!-- 添加或修改角色配置对话框 -->
      <!-- <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
        <el-form ref="form" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="角色名称" prop="roleName">
            <el-input v-model="form.roleName" placeholder="请输入角色名称" />
          </el-form-item>
          <el-form-item prop="roleKey">
            <span slot="label">
              <el-tooltip content="控制器中定义的权限字符，如：@PreAuthorize(`@ss.hasRole('admin')`)" placement="top">
                <i class="el-icon-question"></i>
              </el-tooltip>
              权限字符
            </span>
            <el-input v-model="form.roleKey" placeholder="请输入权限字符" />
          </el-form-item>
          <el-form-item label="角色顺序" prop="roleSort">
            <el-input-number v-model="form.roleSort" controls-position="right" :min="0" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio
                v-for="dict in dict.type.sys_normal_disable"
                :key="dict.value"
                :label="dict.value"
              >{{dict.label}}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="菜单权限">
            <el-checkbox v-model="menuExpand" @change="handleCheckedTreeExpand($event, 'menu')">展开/折叠</el-checkbox>
            <el-checkbox v-model="menuNodeAll" @change="handleCheckedTreeNodeAll($event, 'menu')">全选/全不选</el-checkbox>
            <el-checkbox v-model="form.menuCheckStrictly" @change="handleCheckedTreeConnect($event, 'menu')">父子联动</el-checkbox>
            <el-tree
              class="tree-border"
              :data="menuOptions"
              show-checkbox
              ref="menu"
              node-key="id"
              :check-strictly="!form.menuCheckStrictly"
              empty-text="加载中，请稍候"
              :props="defaultProps"
            ></el-tree>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" type="textarea" placeholder="请输入内容"></el-input>
          </el-form-item>
        </el-form>
        <div slot="footer" class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </el-dialog> -->
  
      <!-- 分配角色数据权限对话框 -->
      <!-- <el-dialog :title="title" :visible.sync="openDataScope" width="500px" append-to-body>
        <el-form :model="form" label-width="80px">
          <el-form-item label="角色名称">
            <el-input v-model="form.roleName" :disabled="true" />
          </el-form-item>
          <el-form-item label="权限字符">
            <el-input v-model="form.roleKey" :disabled="true" />
          </el-form-item>
          <el-form-item label="权限范围">
            <el-select v-model="form.dataScope" @change="dataScopeSelectChange">
              <el-option
                v-for="item in dataScopeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              ></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="数据权限" v-show="form.dataScope == 2">
            <el-checkbox v-model="deptExpand" @change="handleCheckedTreeExpand($event, 'dept')">展开/折叠</el-checkbox>
            <el-checkbox v-model="deptNodeAll" @change="handleCheckedTreeNodeAll($event, 'dept')">全选/全不选</el-checkbox>
            <el-checkbox v-model="form.deptCheckStrictly" @change="handleCheckedTreeConnect($event, 'dept')">父子联动</el-checkbox>
            <el-tree
              class="tree-border"
              :data="deptOptions"
              show-checkbox
              default-expand-all
              ref="dept"
              node-key="id"
              :check-strictly="!form.deptCheckStrictly"
              empty-text="加载中，请稍候"
              :props="defaultProps"
            ></el-tree>
          </el-form-item>
        </el-form>
        <div slot="footer" class="dialog-footer">
          <el-button type="primary" @click="submitDataScope">确 定</el-button>
          <el-button @click="cancelDataScope">取 消</el-button>
        </div>
      </el-dialog> -->
    </div>
  </template>
  
  <script>
//   import { listRole, getRole, delRole, addRole, updateRole, dataScope, changeRoleStatus, deptTreeSelect } from "@/api/system/role";
//   import { treeselect as menuTreeselect, roleMenuTreeselect } from "@/api/system/menu";
  import UploadDialog from './dialog/UploadDialog.vue'
  export default {
    name: "Role",
    dicts: ['sys_normal_disable'],
    components: {
    UploadDialog
  },
    data() {
      return {
        isDialogOpen: false,
        detailVisible:true,
        // 遮罩层
        loading: false,
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
        // 弹出层标题
        title: "",
        // 是否显示弹出层
        open: false,
        // 是否显示弹出层（数据权限）
        openDataScope: false,
        menuExpand: false,
        menuNodeAll: false,
        deptExpand: true,
        deptNodeAll: false,
        // 日期范围
        dateRange: [],
        // 数据范围选项
        dataScopeOptions: [
          {
            value: "1",
            label: "全部数据权限"
          },
          {
            value: "2",
            label: "自定数据权限"
          },
          {
            value: "3",
            label: "本组织数据权限"
          },
          {
            value: "4",
            label: "本组织及以下数据权限"
          },
          {
            value: "5",
            label: "仅本人数据权限"
          }
        ],
        // 菜单列表
        menuOptions: [],
        // 组织列表
        deptOptions: [],
        // 查询参数
        queryParams: {
          pageNum: 1,
          pageSize: 10,
          roleName: undefined,
          roleKey: undefined,
          status: undefined
        },
        // 表单参数
        form: {},
        defaultProps: {
          children: "children",
          label: "label"
        },
        // 表单校验
        rules: {
          roleName: [
            { required: true, message: "角色名称不能为空", trigger: "blur" }
          ],
          roleKey: [
            { required: true, message: "权限字符不能为空", trigger: "blur" }
          ],
          roleSort: [
            { required: true, message: "角色顺序不能为空", trigger: "blur" }
          ]
        }
      };
    },
    created() {
    //   this.getList();
    },
    methods: {
      async fetchData() {
      try {
        this.loading = true;
        this.handleTime();
        const response = await getAlgorithmList({...this.queryParams, ...this.querySpecificParams});
        this.taskList = response.rows;
        this.total = response.total;
        this.loading = false;
        // this.auth = response.token;
        // if(response)
        // 
      } catch (error) {
        console.error(error);
      }
    },
      uploadalgorithm(){
        this.isDialogOpen = true;
      }
    }
  };
  </script>