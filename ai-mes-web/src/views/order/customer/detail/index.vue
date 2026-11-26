<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>客户详情</span>
          <el-button size="small" @click="goBack">返回列表</el-button>
        </div>
      </template>

      <!-- 客户基本信息概览 -->
      <el-descriptions :column="3" border class="customer-summary">
        <el-descriptions-item label="客户编号">{{ customer.customerNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ customer.customerName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="customer.status === 1 || customer.status === 'active' ? 'success' : 'danger'" size="small">
            {{ customer.status === 1 || customer.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="联系人">{{ customer.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ customer.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ customer.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ customer.address || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结算方式">{{ customer.settlementType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="信用额度">
          {{ customer.creditLimit != null ? `¥${Number(customer.creditLimit).toFixed(2)}` : '不限' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ customer.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 应收汇总卡片 -->
    <el-row :gutter="16" class="summary-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ receivableSummary.totalCount }}</div>
          <div class="stat-label">应收单据数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value text-primary">¥{{ receivableSummary.totalAmount }}</div>
          <div class="stat-label">应收总额</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value text-success">¥{{ receivableSummary.receivedAmount }}</div>
          <div class="stat-label">已收金额</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :class="{ 'text-danger': Number(receivableSummary.unreceivedAmount) > 0 }">
            ¥{{ receivableSummary.unreceivedAmount }}
          </div>
          <div class="stat-label">
            未收金额
            <el-button v-if="Number(receivableSummary.unreceivedAmount) > 0" type="primary" link size="small" @click="goToReceivables">
              查看明细
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab区域 -->
    <el-card shadow="never" style="margin-top: 16px">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 历史订单 -->
        <el-tab-pane label="历史订单" name="orders">
          <!-- 查询条件 -->
          <el-form :model="orderQuery" inline size="small" class="order-query-form">
            <el-form-item label="订单编号">
              <el-input v-model="orderQuery.orderNo" placeholder="请输入订单编号" clearable style="width: 150px" />
            </el-form-item>
            <el-form-item label="联系人">
              <el-input v-model="orderQuery.contactName" placeholder="请输入联系人" clearable style="width: 120px" />
            </el-form-item>
            <el-form-item label="订单金额">
              <el-input-number v-model="orderQuery.totalAmountMin" :min="0" :precision="2" controls-position="right"
                placeholder="最小" style="width: 120px" />
              <span style="margin: 0 4px; color: #999">-</span>
              <el-input-number v-model="orderQuery.totalAmountMax" :min="0" :precision="2" controls-position="right"
                placeholder="最大" style="width: 120px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="orderQuery.status" placeholder="全部" clearable style="width: 110px">
                <el-option v-for="(label, key) in orderStatusMap" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="下单时间">
              <el-date-picker v-model="orderQuery.createTimeRange" type="daterange"
                range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
                value-format="YYYY-MM-DD" style="width: 220px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleOrderSearch">搜索</el-button>
              <el-button @click="handleOrderReset">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="orders" v-loading="orderLoading" border stripe size="small">
            <el-table-column prop="orderNo" label="订单编号" min-width="140">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="goToOrderDetail(row.id)">{{ row.orderNo }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="contactName" label="联系人" min-width="100" />
            <el-table-column prop="totalAmount" label="订单金额" min-width="120" align="right">
              <template #default="{ row }">
                ¥{{ Number(row.totalAmount || 0).toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="orderStatusType(row.status)" size="small">{{ orderStatusMap[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="下单时间" min-width="170" />
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="orderPage.pageNum"
              v-model:page-size="orderPage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="orderPage.total"
              layout="total, sizes, prev, pager, next"
              @size-change="loadOrders"
              @current-change="loadOrders"
            />
          </div>
        </el-tab-pane>

        <!-- 跟进记录 -->
        <el-tab-pane label="跟进记录" name="followups">
          <div style="margin-bottom: 12px">
            <el-button type="primary" size="small" @click="openFollowupForm">添加跟进</el-button>
          </div>
          <el-table :data="followups" v-loading="followupLoading" border stripe size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="followupType" label="跟进方式" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="{ phone: '', visit: 'success', online: 'warning', other: 'info' }[row.followupType] || 'info'">
                  {{ { phone: '电话', visit: '拜访', online: '线上', other: '其他' }[row.followupType] || row.followupType || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="跟进内容" min-width="300" show-overflow-tooltip />
            <el-table-column prop="nextFollowupDate" label="下次跟进" width="120" align="center">
              <template #default="{ row }">{{ row.nextFollowupDate || '-' }}</template>
            </el-table-column>
            <el-table-column prop="creatorName" label="跟进人" width="100" align="center" />
            <el-table-column prop="createTime" label="跟进时间" width="170" align="center">
              <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').substring(0, 16) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="deleteFollowup(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!followupLoading && followups.length === 0" description="暂无跟进记录" />
        </el-tab-pane>

        <!-- 应收明细 -->
        <el-tab-pane label="应收明细" name="receivables">
          <el-table :data="receivables" v-loading="receivableLoading" border stripe size="small">
            <el-table-column prop="receivableNo" label="应收编号" min-width="140" />
            <el-table-column prop="orderNo" label="关联订单" min-width="140" />
            <el-table-column prop="amount" label="应收金额" min-width="120" align="right">
              <template #default="{ row }">
                ¥{{ Number(row.amount || 0).toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="receivedAmount" label="已收金额" min-width="120" align="right">
              <template #default="{ row }">
                ¥{{ Number(row.receivedAmount || 0).toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="unreceivedAmount" label="未收金额" min-width="120" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-danger': Number(row.unreceivedAmount || 0) > 0 }">
                  ¥{{ Number(row.unreceivedAmount || 0).toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" min-width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="receivableStatusType(row.status)" size="small">{{ receivableStatusMap[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" min-width="120" />
            <el-table-column prop="createTime" label="创建时间" min-width="170">
              <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="receivablePage.pageNum"
              v-model:page-size="receivablePage.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="receivablePage.total"
              layout="total, sizes, prev, pager, next"
              @size-change="loadReceivables"
              @current-change="loadReceivables"
            />
          </div>
        </el-tab-pane>

      </el-tabs>
    </el-card>
    <!-- 添加跟进记录对话框 -->
    <el-dialog v-model="followupDialogVisible" title="添加跟进记录" width="500px" :close-on-click-modal="false">
      <el-form :model="followupForm" label-width="90px">
        <el-form-item label="跟进方式">
          <el-select v-model="followupForm.followupType" placeholder="请选择" style="width: 100%">
            <el-option label="电话沟通" value="phone" />
            <el-option label="上门拜访" value="visit" />
            <el-option label="线上沟通" value="online" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="跟进内容" required>
          <el-input v-model="followupForm.content" type="textarea" :rows="4" placeholder="请输入跟进内容..." />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker v-model="followupForm.nextFollowupDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择下次跟进日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="followupSubmitting" @click="submitFollowup">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCustomerDetail, getCustomerFollowups, createCustomerFollowup, deleteCustomerFollowup } from '@/api/master'
import { getSalesOrderPage } from '@/api/order'
import { getReceivablePage } from '@/api/finance'

const router = useRouter()
const route = useRoute()
const customerId = computed(() => route.query.id)

const orderStatusMap = {
  draft: '草稿', pending_approval: '待审批', confirmed: '已确认', mrp_planned: '已转计划',
  outsourcing: '外协中', in_production: '生产中', partial_shipped: '部分发货',
  shipped: '已发货', completed: '已完成', settled: '已结算',
  cancelled: '已取消', rejected: '已驳回', voided: '已作废'
}

function orderStatusType(status) {
  const map = { draft: 'info', pending_approval: 'warning', confirmed: 'primary', mrp_planned: 'primary', outsourcing: 'warning', in_production: '', partial_shipped: 'warning', shipped: 'success', completed: 'success', settled: 'success', cancelled: 'danger', rejected: 'danger', voided: 'danger' }
  return map[status] || 'info'
}

const receivableStatusMap = {
  pending: '待收款', partial_received: '部分收款', received: '已收款', overdue: '已逾期'
}

function receivableStatusType(status) {
  const map = { pending: 'warning', partial_received: '', received: 'success', overdue: 'danger' }
  return map[status] || 'info'
}

// ==================== 客户基本信息 ====================
const customer = ref({})

async function loadCustomer() {
  try {
    const res = await getCustomerDetail(customerId.value)
    customer.value = res.data || {}
  } catch {}
}

// ==================== 应收汇总 ====================
const receivableSummary = reactive({
  totalCount: 0,
  totalAmount: '0.00',
  receivedAmount: '0.00',
  unreceivedAmount: '0.00'
})

function calcReceivableSummary(list) {
  let totalAmt = 0, receivedAmt = 0, unreceivedAmt = 0
  for (const r of list) {
    totalAmt += Number(r.amount || 0)
    receivedAmt += Number(r.receivedAmount || 0)
    unreceivedAmt += Number(r.unreceivedAmount || 0)
  }
  receivableSummary.totalCount = list.length
  receivableSummary.totalAmount = totalAmt.toFixed(2)
  receivableSummary.receivedAmount = receivedAmt.toFixed(2)
  receivableSummary.unreceivedAmount = unreceivedAmt.toFixed(2)
}

// ==================== 历史订单 ====================
const activeTab = ref('orders')
const orders = ref([])
const orderLoading = ref(false)
const orderPage = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const orderQuery = reactive({
  orderNo: '',
  contactName: '',
  totalAmountMin: undefined,
  totalAmountMax: undefined,
  status: undefined,
  createTimeRange: null
})

async function loadOrders() {
  orderLoading.value = true
  try {
    const params = {
      customerId: customerId.value,
      pageNum: orderPage.pageNum,
      pageSize: orderPage.pageSize
    }
    if (orderQuery.orderNo) params.orderNo = orderQuery.orderNo
    if (orderQuery.contactName) params.contactName = orderQuery.contactName
    if (orderQuery.totalAmountMin != null) params.totalAmountMin = orderQuery.totalAmountMin
    if (orderQuery.totalAmountMax != null) params.totalAmountMax = orderQuery.totalAmountMax
    if (orderQuery.status) params.status = orderQuery.status
    if (orderQuery.createTimeRange?.length === 2) {
      params.startDate = orderQuery.createTimeRange[0]
      params.endDate = orderQuery.createTimeRange[1]
    }
    const res = await getSalesOrderPage(params)
    orders.value = res.data?.records || []
    orderPage.total = res.data?.total || 0
  } catch {} finally {
    orderLoading.value = false
  }
}

function handleOrderSearch() {
  orderPage.pageNum = 1
  loadOrders()
}

function handleOrderReset() {
  orderQuery.orderNo = ''
  orderQuery.contactName = ''
  orderQuery.totalAmountMin = undefined
  orderQuery.totalAmountMax = undefined
  orderQuery.status = undefined
  orderQuery.createTimeRange = null
  orderPage.pageNum = 1
  loadOrders()
}

// ==================== 应收明细 ====================
const receivables = ref([])
const receivableLoading = ref(false)
const receivablePage = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function loadReceivables() {
  receivableLoading.value = true
  try {
    const res = await getReceivablePage({
      customerId: customerId.value,
      pageNum: receivablePage.pageNum,
      pageSize: receivablePage.pageSize
    })
    receivables.value = res.data?.records || []
    receivablePage.total = res.data?.total || 0
  } catch {} finally {
    receivableLoading.value = false
  }
}

// ==================== 应收汇总加载 ====================
async function loadAllReceivablesForSummary() {
  try {
    const res = await getReceivablePage({
      customerId: customerId.value,
      pageNum: 1,
      pageSize: 9999
    })
    calcReceivableSummary(res.data?.records || [])
  } catch {}
}

// ==================== 跟进记录 ====================
const followups = ref([])
const followupLoading = ref(false)
const followupDialogVisible = ref(false)
const followupSubmitting = ref(false)
const followupForm = reactive({ followupType: 'phone', content: '', nextFollowupDate: '' })

async function loadFollowups() {
  followupLoading.value = true
  try {
    const res = await getCustomerFollowups(customerId.value)
    followups.value = res.data || []
  } catch {} finally {
    followupLoading.value = false
  }
}

function openFollowupForm() {
  followupForm.followupType = 'phone'
  followupForm.content = ''
  followupForm.nextFollowupDate = ''
  followupDialogVisible.value = true
}

async function submitFollowup() {
  if (!followupForm.content.trim()) {
    ElMessage.warning('请填写跟进内容')
    return
  }
  followupSubmitting.value = true
  try {
    await createCustomerFollowup(customerId.value, { ...followupForm })
    ElMessage.success('添加成功')
    followupDialogVisible.value = false
    loadFollowups()
  } catch {} finally {
    followupSubmitting.value = false
  }
}

async function deleteFollowup(row) {
  try {
    await ElMessageBox.confirm('确认删除该跟进记录？', '提示', { type: 'warning' })
    await deleteCustomerFollowup(customerId.value, row.id)
    ElMessage.success('删除成功')
    loadFollowups()
  } catch {}
}

// ==================== Tab切换 ====================
const tabLoaded = reactive({ orders: false, receivables: false, followups: false })

function handleTabChange(tab) {
  if (tab === 'orders' && !tabLoaded.orders) {
    loadOrders()
    tabLoaded.orders = true
  } else if (tab === 'followups' && !tabLoaded.followups) {
    loadFollowups()
    tabLoaded.followups = true
  } else if (tab === 'receivables' && !tabLoaded.receivables) {
    loadReceivables()
    tabLoaded.receivables = true
  }
}

// ==================== 导航 ====================
function goBack() {
  const from = route.query.from
  if (from === 'salesOrder') {
    router.push('/order/list')
  } else {
    router.push('/order/customers')
  }
}

function goToOrderDetail(orderId) {
  router.push({ path: '/order/detail', query: { id: orderId } })
}

function goToReceivables() {
  activeTab.value = 'receivables'
  if (!tabLoaded.receivables) {
    loadReceivables()
    tabLoaded.receivables = true
  }
}

// ==================== 初始化 ====================
onMounted(async () => {
  if (!customerId.value) {
    router.push('/order/customers')
    return
  }
  await loadCustomer()
  loadOrders()
  tabLoaded.orders = true
  loadAllReceivablesForSummary()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}
.customer-summary {
  margin-bottom: 0;
}
.summary-cards {
  margin-top: 16px;
}
.stat-card {
  text-align: center;
  :deep(.el-card__body) {
    padding: 20px 16px;
  }
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #333;
}
.stat-label {
  font-size: 13px;
  color: #999;
  margin-top: 6px;
}
.text-primary { color: #409eff; }
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }
.order-query-form {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
