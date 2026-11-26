<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>审批流程管理</span>
          <el-button v-permission="'sys:approval-flow:add'" type="primary" @click="handleAdd">新增流程</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item>
          <el-input v-model="queryParams.keyword" placeholder="流程名称" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="queryParams.businessType" placeholder="业务类型" clearable @change="handleSearch">
            <el-option v-for="bt in businessTypes" :key="bt.value" :label="bt.label" :value="bt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="flowName" label="流程名称" min-width="160" />
        <el-table-column prop="flowCode" label="流程编码" width="150" />
        <el-table-column prop="businessType" label="业务类型" width="140">
          <template #default="{ row }">
            {{ businessTypeLabel(row.businessType) }}
          </template>
        </el-table-column>
        <el-table-column prop="nodeCount" label="审批节点" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.nodeCount }}个</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="handleToggle(row)"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
            />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sys:approval-flow:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'sys:approval-flow:edit'" type="primary" link size="small" @click="handleDesign(row)">设计</el-button>
            <el-button v-permission="'sys:approval-flow:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑基本信息弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="流程名称" prop="flowName">
          <el-input v-model="formData.flowName" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="流程编码" prop="flowCode">
          <el-input v-model="formData.flowCode" placeholder="请输入流程编码" :disabled="!!formData.id" />
        </el-form-item>
        <el-form-item label="业务类型" prop="businessType">
          <el-select v-model="formData.businessType" placeholder="请选择业务类型" style="width: 100%;">
            <el-option v-for="bt in businessTypes" :key="bt.value" :label="bt.label" :value="bt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 流程设计弹窗 -->
    <el-dialog v-model="designVisible" title="流程设计" width="800px" destroy-on-close>
      <div class="design-header">
        <span class="design-title">{{ designFlow.flowName }}</span>
        <el-button type="primary" size="small" @click="addNode">添加节点</el-button>
      </div>

      <div class="node-list">
        <el-empty v-if="designNodes.length === 0" description="暂无审批节点，请添加" :image-size="60" />
        <div v-for="(node, index) in designNodes" :key="index" class="node-card"
             :class="{ 'parallel-node': index > 0 && node.parallelWithPrev }">
          <div class="node-header">
            <div class="node-order">
              <el-tag :type="index > 0 && node.parallelWithPrev ? 'success' : 'primary'" size="small">
                第{{ stepLabel(index) }}步{{ index > 0 && node.parallelWithPrev ? ' (并行)' : '' }}
              </el-tag>
            </div>
            <div class="node-actions">
              <el-checkbox v-if="index > 0" v-model="node.parallelWithPrev" @change="recomputeOrders">与上一节点并行</el-checkbox>
              <el-button v-if="index > 0" link size="small" @click="moveNode(index, -1)">上移</el-button>
              <el-button v-if="index < designNodes.length - 1" link size="small" @click="moveNode(index, 1)">下移</el-button>
              <el-button type="danger" link size="small" @click="removeNode(index)">删除</el-button>
            </div>
          </div>
          <el-form :inline="true" class="node-form">
            <el-form-item label="节点名称">
              <el-input v-model="node.nodeName" placeholder="如：部门审批" style="width: 160px;" />
            </el-form-item>
            <el-form-item label="审批方式">
              <el-select v-model="node.approvalMode" style="width: 140px;">
                <el-option label="或签(任一通过)" value="OR" />
                <el-option label="会签(全部通过)" value="AND" />
              </el-select>
            </el-form-item>
          </el-form>
          <div class="approver-section">
            <div class="approver-label">审批人：</div>
            <div class="approver-list">
              <div v-for="(approver, ai) in node.approvers" :key="ai" class="approver-item">
                <el-select v-model="approver.approverType" placeholder="类型" size="small" style="width: 100px;">
                  <el-option label="指定用户" value="user" />
                  <el-option label="指定角色" value="role" />
                </el-select>
                <el-select
                  v-if="approver.approverType === 'user'"
                  v-model="approver.approverId"
                  filterable
                  placeholder="选择用户"
                  size="small"
                  style="width: 180px;"
                >
                  <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
                </el-select>
                <el-select
                  v-else
                  v-model="approver.approverId"
                  filterable
                  placeholder="选择角色"
                  size="small"
                  style="width: 180px;"
                >
                  <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
                </el-select>
                <el-button type="danger" link size="small" @click="removeApprover(node, ai)">移除</el-button>
              </div>
              <el-button type="primary" link size="small" @click="addApprover(node)">+ 添加审批人</el-button>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="designVisible = false">取消</el-button>
        <el-button type="primary" :loading="designSubmitLoading" @click="handleSaveDesign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getApprovalFlowPage,
  getApprovalFlowDetail,
  createApprovalFlow,
  updateApprovalFlow,
  deleteApprovalFlow,
  toggleApprovalFlow
} from '@/api/approval'
import { getUserSelect, getRoleList } from '@/api/system'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)

// 流程设计
const designVisible = ref(false)
const designSubmitLoading = ref(false)
const designFlow = ref({})
const designNodes = ref([])
const userOptions = ref([])
const roleOptions = ref([])

const businessTypes = [
  { label: '销售订单', value: 'sales_order' },
  { label: '订单变更', value: 'order_change' },
  { label: '采购订单', value: 'purchase_order' },
  { label: '外协订单', value: 'outsource_order' },
  { label: '生产工单', value: 'work_order' },
  { label: 'BOM 版本激活', value: 'bom_version_activation' },
  { label: '质检让步接收', value: 'qc_concession' }
]

const queryParams = reactive({ keyword: '', businessType: '', pageNum: 1, pageSize: 20 })

const formData = ref({ id: undefined, flowName: '', flowCode: '', businessType: '', remark: '' })

const formRules = {
  flowName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  flowCode: [{ required: true, message: '请输入流程编码', trigger: 'blur' }],
  businessType: [{ required: true, message: '请选择业务类型', trigger: 'change' }]
}

function businessTypeLabel(val) {
  const bt = businessTypes.find(b => b.value === val)
  return bt ? bt.label : val
}

async function loadData() {
  loading.value = true
  try {
    const res = await getApprovalFlowPage(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.keyword = ''; queryParams.businessType = ''; handleSearch() }

function handleAdd() {
  dialogTitle.value = '新增审批流程'
  formData.value = { id: undefined, flowName: '', flowCode: '', businessType: '', remark: '' }
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑审批流程'
  formData.value = { id: row.id, flowName: row.flowName, flowCode: row.flowCode, businessType: row.businessType, remark: row.remark || '' }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateApprovalFlow(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createApprovalFlow(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally { submitLoading.value = false }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除流程「${row.flowName}」吗？`, '提示', { type: 'warning' }).then(async () => {
    try { await deleteApprovalFlow(row.id); ElMessage.success('删除成功'); loadData() } catch {}
  }).catch(() => {})
}

async function handleToggle(row) {
  try {
    await toggleApprovalFlow(row.id)
    ElMessage.success(row.enabled === 1 ? '已停用' : '已启用')
    loadData()
  } catch {}
}

async function handleDesign(row) {
  designFlow.value = row
  try {
    const res = await getApprovalFlowDetail(row.id)
    const raw = (res.data.nodes || []).slice().sort((a, b) => (a.nodeOrder || 0) - (b.nodeOrder || 0))
    designNodes.value = raw.map((n, i) => ({
      nodeName: n.nodeName,
      nodeOrder: n.nodeOrder,
      approvalMode: n.approvalMode || 'OR',
      remark: n.remark || '',
      // 与前一节点 nodeOrder 相同 → 并行
      parallelWithPrev: i > 0 && n.nodeOrder === raw[i - 1].nodeOrder,
      approvers: (n.approvers || []).map(a => ({
        approverType: a.approverType || 'user',
        approverId: a.approverId
      }))
    }))
  } catch { designNodes.value = [] }
  designVisible.value = true
}

// 根据 parallelWithPrev 标志重算 nodeOrder
function recomputeOrders() {
  let order = 0
  designNodes.value.forEach((n, i) => {
    if (i === 0 || !n.parallelWithPrev) order += 1
    n.nodeOrder = order
  })
}

// 步骤号: 同 nodeOrder 的多个节点共享同一步骤号
function stepLabel(index) {
  const seen = new Set()
  let step = 0
  for (let i = 0; i <= index; i++) {
    if (!seen.has(designNodes.value[i].nodeOrder)) {
      step += 1
      seen.add(designNodes.value[i].nodeOrder)
    }
  }
  return step
}

function addNode() {
  designNodes.value.push({
    nodeName: '',
    nodeOrder: 0,
    approvalMode: 'OR',
    remark: '',
    parallelWithPrev: false,
    approvers: []
  })
  recomputeOrders()
}

function removeNode(index) {
  designNodes.value.splice(index, 1)
  if (designNodes.value.length > 0) designNodes.value[0].parallelWithPrev = false
  recomputeOrders()
}

function moveNode(index, direction) {
  const target = index + direction
  const temp = designNodes.value[index]
  designNodes.value[index] = designNodes.value[target]
  designNodes.value[target] = temp
  if (designNodes.value.length > 0) designNodes.value[0].parallelWithPrev = false
  recomputeOrders()
}

function addApprover(node) {
  node.approvers.push({ approverType: 'user', approverId: undefined })
}

function removeApprover(node, index) {
  node.approvers.splice(index, 1)
}

async function handleSaveDesign() {
  // 校验
  for (const node of designNodes.value) {
    if (!node.nodeName) {
      ElMessage.warning('请填写所有节点名称')
      return
    }
    if (node.approvers.length === 0) {
      ElMessage.warning(`节点「${node.nodeName}」至少需要一个审批人`)
      return
    }
    for (const a of node.approvers) {
      if (!a.approverId) {
        ElMessage.warning(`节点「${node.nodeName}」有未选择的审批人`)
        return
      }
    }
  }

  // 提交前重算 nodeOrder, 去掉前端辅助字段 parallelWithPrev
  recomputeOrders()
  const nodesToSave = designNodes.value.map(n => ({
    nodeName: n.nodeName,
    nodeOrder: n.nodeOrder,
    approvalMode: n.approvalMode,
    remark: n.remark,
    approvers: n.approvers
  }))

  designSubmitLoading.value = true
  try {
    await updateApprovalFlow(designFlow.value.id, {
      flowName: designFlow.value.flowName,
      businessType: designFlow.value.businessType,
      enabled: designFlow.value.enabled,
      remark: designFlow.value.remark,
      nodes: nodesToSave
    })
    ElMessage.success('流程设计保存成功')
    designVisible.value = false
    loadData()
  } catch {} finally { designSubmitLoading.value = false }
}

async function loadOptions() {
  try {
    const [userRes, roleRes] = await Promise.all([getUserSelect(), getRoleList()])
    userOptions.value = userRes.data || []
    roleOptions.value = roleRes.data || []
  } catch {}
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-form {
  margin-bottom: 16px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.design-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}
.design-title {
  font-size: 16px;
  font-weight: 600;
}
.node-list {
  max-height: 500px;
  overflow-y: auto;
}
.node-card {
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 12px;
  background: #fafafa;
}
.parallel-node {
  border-left: 3px solid #67c23a;
  margin-top: -8px;
  margin-left: 16px;
  background: #f0f9eb;
}
.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.node-form {
  margin-bottom: 8px;
}
.approver-section {
  display: flex;
  gap: 8px;
}
.approver-label {
  flex-shrink: 0;
  line-height: 32px;
  color: #606266;
  font-size: 13px;
}
.approver-list {
  flex: 1;
}
.approver-item {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
</style>
