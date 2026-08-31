<template>
  <div class="app-container deployment-test-page">
    <el-card shadow="never" class="block-card">
      <div slot="header">创建布控任务</div>
      <el-form :model="createForm" label-width="120px" size="small">
        <el-form-item label="deviceId">
          <el-input v-model="createForm.deviceId" placeholder="请输入 deviceId" />
        </el-form-item>
        <el-form-item label="taskName">
          <el-input v-model="createForm.taskName" placeholder="请输入 taskName" />
        </el-form-item>
        <el-form-item label="algorithmCode">
          <el-input v-model="createForm.algorithmCode" placeholder="请输入 algorithmCode" />
        </el-form-item>
        <el-form-item label="algorithmName">
          <el-input v-model="createForm.algorithmName" placeholder="请输入 algorithmName" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading.create" @click="handleCreate">创建</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="block-card">
      <div slot="header">启动任务</div>
      <el-input v-model="startId" placeholder="请输入 deploymentId" size="small" class="inline-input" />
      <el-button type="success" size="small" :loading="loading.start" @click="handleStart">启动</el-button>
    </el-card>

    <el-card shadow="never" class="block-card">
      <div slot="header">停止任务</div>
      <el-input v-model="stopId" placeholder="请输入 deploymentId" size="small" class="inline-input" />
      <el-button type="warning" size="small" :loading="loading.stop" @click="handleStop">停止</el-button>
    </el-card>

    <el-card shadow="never" class="block-card">
      <div slot="header">查询任务</div>
      <el-input v-model="queryId" placeholder="请输入 deploymentId" size="small" class="inline-input" />
      <el-button type="primary" size="small" :loading="loading.query" @click="handleQuery">查询</el-button>
    </el-card>

    <el-card shadow="never" class="block-card">
      <div slot="header">结果展示</div>
      <el-descriptions :column="2" border size="small" class="result-desc">
        <el-descriptions-item label="code">{{ lastResult.code }}</el-descriptions-item>
        <el-descriptions-item label="msg">{{ lastResult.msg }}</el-descriptions-item>
        <el-descriptions-item label="deploymentId">{{ getDataField('deploymentId') }}</el-descriptions-item>
        <el-descriptions-item label="streamId">{{ getDataField('streamId') }}</el-descriptions-item>
        <el-descriptions-item label="status">{{ getDataField('status') }}</el-descriptions-item>
        <el-descriptions-item label="analyzeRtspUrl">{{ getDataField('analyzeRtspUrl') }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">原始响应</el-divider>
      <pre class="json-output">{{ prettyResult }}</pre>
    </el-card>
  </div>
</template>

<script>
import { createDeployment, getDeploymentDetail, startDeployment, stopDeployment } from '@/api/deployment'

export default {
  name: 'DeploymentTest',
  data() {
    return {
      createForm: {
        deviceId: '',
        taskName: '',
        algorithmCode: '',
        algorithmName: ''
      },
      startId: '',
      stopId: '',
      queryId: '',
      loading: {
        create: false,
        start: false,
        stop: false,
        query: false
      },
      lastResult: {
        code: '',
        msg: '',
        data: null
      }
    }
  },
  computed: {
    prettyResult() {
      return JSON.stringify(this.lastResult, null, 2)
    }
  },
  methods: {
    getDataField(key) {
      const data = this.lastResult && this.lastResult.data
      if (!data || data[key] === undefined || data[key] === null || data[key] === '') {
        return '--'
      }
      return data[key]
    },
    updateResult(res) {
      this.lastResult = {
        code: res && res.code !== undefined ? res.code : '--',
        msg: res && res.msg ? res.msg : '--',
        data: res && res.data ? res.data : null
      }
    },
    async handleCreate() {
      this.loading.create = true
      try {
        const res = await createDeployment(this.createForm)
        this.updateResult(res)
        const newId = res && res.data && res.data.deploymentId
        if (newId) {
          this.startId = String(newId)
          this.stopId = String(newId)
          this.queryId = String(newId)
        }
      } finally {
        this.loading.create = false
      }
    },
    async handleStart() {
      if (!this.startId) {
        this.$message.warning('请先输入 deploymentId')
        return
      }
      this.loading.start = true
      try {
        const res = await startDeployment(this.startId)
        this.updateResult(res)
      } finally {
        this.loading.start = false
      }
    },
    async handleStop() {
      if (!this.stopId) {
        this.$message.warning('请先输入 deploymentId')
        return
      }
      this.loading.stop = true
      try {
        const res = await stopDeployment(this.stopId)
        this.updateResult(res)
      } finally {
        this.loading.stop = false
      }
    },
    async handleQuery() {
      if (!this.queryId) {
        this.$message.warning('请先输入 deploymentId')
        return
      }
      this.loading.query = true
      try {
        const res = await getDeploymentDetail(this.queryId)
        this.updateResult(res)
      } finally {
        this.loading.query = false
      }
    }
  }
}
</script>

<style scoped>
.deployment-test-page {
  max-width: 960px;
}

.block-card {
  margin-bottom: 16px;
}

.inline-input {
  width: 300px;
  margin-right: 12px;
}

.result-desc {
  margin-bottom: 10px;
}

.json-output {
  margin: 0;
  background: #f7f8fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
