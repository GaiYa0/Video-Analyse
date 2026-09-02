import {
  getAlarmTypeFilterOptions,
  getTeamWaring,
  getWarningDetail,
  handleWarning
} from '@/api/warning'
import { getDeptList } from '@/api/system/kanban'
import { getVideoEvidenceUnavailableMessage, resolveAlarmVideoUrl } from '@/utils/alarmVideo'
import store from '@/store'

export function formatDateLocal(date) {
  const d = date instanceof Date ? date : new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export default {
  methods: {
    handleTime() {
      if (this.dateRange == null || this.dateRange.length === 0) {
        this.queryParams.begin = undefined
        this.queryParams.end = undefined
        return
      }
      const formattedDateRange = [
        this.dateRange[0] + ' 00:00:00',
        this.dateRange[1] + ' 23:59:59'
      ]
      const timestamps = formattedDateRange.map(dateStr => {
        const date = new Date(dateStr)
        return Math.round(date.getTime() / 1000)
      })
      if (timestamps.length === 2) {
        this.queryParams.begin = timestamps[0]
        this.queryParams.end = timestamps[1]
      }
    },

    handleExport() {
      const { pageNum, pageSize, ...newQueryParams } = this.queryParams
      this.download('/waring/waring/importTemplate', {
        ...newQueryParams
      }, `报警信息_${new Date().getTime()}.xlsx`)
    },

    async fetchData() {
      try {
        this.loading = true
        this.handleTime()
        const response = await this.fetchWarningList({ ...this.queryParams, ...this.querySpecificParams })
        this.warningList = response.rows
        this.total = response.total
        this.loading = false
        this.auth = response.token
      } catch (error) {
        console.error(error)
      }
    },

    async fetchQueryOptionData() {
      try {
        const permissions = store.getters && store.getters.permissions
        const all_permission = '*:*:*'
        const permissionFlag = 'getDeptList'
        const hasPermissions = permissions.some(permission => {
          return all_permission === permission || permissionFlag.includes(permission)
        })
        if (hasPermissions) {
          const deptListRes = await getDeptList()
          this.orgOptions = [
            {
              value: '',
              label: '全部'
            },
            ...deptListRes.data.map((item) => ({
              value: item.orgIndex,
              label: item.deptName
            }))
          ]
        }
        const typeWarningRes = await getAlarmTypeFilterOptions()
        this.typeWarningOptions = typeWarningRes.data.map(item => ({
          value: item.alarm_type_name,
          label: item.alarm_type_name
        }))
        const teamWarningRes = await getTeamWaring()
        this.teamOptions = teamWarningRes.data.map(item => ({
          value: item.team_name,
          label: item.team_name
        }))
      } catch (error) {
        console.error(error)
      }
    },

    handleQuery() {
      this.queryParams.pageNum = 1
      this.fetchData()
    },

    async viewDetail(row) {
      const id = row.w_id
      try {
        const response = await getWarningDetail(id)
        this.detailsInfo = response.data
        this.detailActionRow = Object.assign({}, row || {}, response.data || {})
        this.resetSolveForm(this.detailActionRow)
        this.closeDetailVideo()
        this.openDetails = true
        this.title = '报警详情'
      } catch (error) {
        console.error(error)
      }
    },

    async comfirmSolve() {
      try {
        const response = await handleWarning(this.solveData)
        if (response.code !== 200) throw new Error(response.message)
        await this.fetchData()
        const detailResponse = await getWarningDetail(this.solveData.w_id)
        this.detailsInfo = detailResponse.data
        this.detailActionRow = Object.assign({}, this.detailActionRow, detailResponse.data || {})
        this.resetSolveForm(this.detailActionRow)
      } catch (error) {
        console.error(error)
      }
    },

    resetSolveForm(detail = {}) {
      this.solveData = {
        w_id: detail.w_id || '',
        h_title: detail.h_title || '',
        h_remark: detail.h_remark || ''
      }
      this.$nextTick(() => {
        const dialog = this.$refs.detailDialog
        if (dialog && dialog.clearSolveValidate) {
          dialog.clearSolveValidate()
        }
      })
    },

    handleDetailDialogClose() {
      this.closeDetailVideo()
      this.resetSolveForm({})
      this.detailsInfo = {}
      this.detailActionRow = {}
    },

    closeDetailVideo() {
      this.detailVideoVisible = false
      this.rtspUrl = ''
    },

    async playDetailVideo() {
      await this.viewVideo(this.detailActionRow)
    },

    toAbsoluteMediaUrl(path) {
      if (!path) return ''
      if (/^https?:\/\//i.test(path)) return path
      if (path.startsWith('/')) return `${window.location.origin}${path}`
      return `${window.location.origin}/${path}`
    },

    resolveVideoMediaUrl(row) {
      return resolveAlarmVideoUrl(row, this.toAbsoluteMediaUrl.bind(this))
    },

    async viewVideo(row) {
      if (!row || !row.device_id || !row.alarm_time) {
        this.$modal.msgError('缺少视频取证信息')
        return
      }

      this.detailVideoLoading = true
      const localVideoUrl = this.resolveVideoMediaUrl(row)
      if (localVideoUrl) {
        this.rtspUrl = localVideoUrl
        this.detailVideoVisible = true
        this.detailVideoLoading = false
        return
      }

      this.$modal.msgError(getVideoEvidenceUnavailableMessage(row))
      this.detailVideoLoading = false
    },

    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.w_id)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },

    isSleepType(name) {
      return String(name || '').indexOf('睡岗') !== -1
    },

    isHandled(value) {
      return String(value) === '1'
    },

    hasHandleDetail(detail = {}) {
      return !!(detail.h_title || detail.h_org_name || detail.h_remark || detail.h_create_time)
    }
  }
}
