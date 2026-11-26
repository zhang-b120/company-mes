<template>
  <!-- 悬浮按钮 -->
  <div v-show="!visible" class="ai-fab" @click="aiStore.show()">
    <el-icon :size="26"><ChatDotRound /></el-icon>
  </div>

  <!-- 聊天面板 -->
  <transition name="ai-panel">
    <div v-show="visible" class="ai-panel">
      <!-- 标题栏 -->
      <div class="ai-panel-header">
        <div class="header-info">
          <el-icon :size="18" color="#7B61FF"><ChatDotRound /></el-icon>
          <span class="header-title">AI 智能助手</span>
          <el-tag size="small" type="info">全局助手</el-tag>
        </div>
        <div class="header-actions">
          <el-tooltip content="清空对话">
            <el-icon class="action-icon" @click="handleClear"><Delete /></el-icon>
          </el-tooltip>
          <el-icon class="action-icon" @click="aiStore.hide()"><Minus /></el-icon>
        </div>
      </div>

      <!-- 消息区域 -->
      <div ref="messageArea" class="ai-panel-body">
        <!-- 欢迎消息 -->
        <div v-if="aiStore.messages.length === 0" class="welcome-msg">
          <div class="welcome-icon">🤖</div>
          <div class="welcome-text">你好！我是AI智能助手</div>
          <div class="welcome-sub">我可以帮你录入数据、查询信息、分析报告</div>
          <div class="quick-actions">
            <el-button size="small" round @click="sendQuick('帮我录入数据')">录入数据</el-button>
            <el-button size="small" round @click="sendQuick('查询最近记录')">查询记录</el-button>
            <el-button size="small" round @click="sendQuick('今日简报')">今日简报</el-button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, idx) in aiStore.messages" :key="idx" class="msg-row" :class="msg.role">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="msg-bubble user-bubble">
            <div class="msg-text">{{ msg.content }}</div>
            <div v-if="msg.files && msg.files.length" class="msg-files">
              <el-tag v-for="f in msg.files" :key="f" size="small" type="info">📎 {{ f }}</el-tag>
            </div>
          </div>

          <!-- AI消息 -->
          <div v-else class="msg-bubble ai-bubble">
            <AiMarkdown v-if="msg.content" :content="msg.content" class="msg-text" />

            <!-- 数据卡片: 录入表单（竖向布局） -->
            <div v-if="msg.data && msg.data.type === 'input_form' && msg.data.records?.length" class="data-card">
              <div class="data-card-header">
                <span>📝 识别到 {{ msg.data.records.length }} 条数据</span>
                <div class="card-actions">
                  <el-button type="success" size="small" :loading="submittingIdx.has(idx)" @click="handleDirectSubmit(msg.data, idx)">确认提交</el-button>
                  <el-button type="primary" size="small" plain @click="handleFillForm(msg.data)">填入表单</el-button>
                </div>
              </div>
              <div v-for="(record, ri) in msg.data.records" :key="ri" class="form-record">
                <div v-if="msg.data.records.length > 1" class="record-index">第 {{ ri + 1 }} 条</div>
                <div class="form-fields">
                  <div v-for="col in getFormColumns(msg.data.records)" :key="col" class="form-field-row">
                    <span class="field-label">{{ getColLabel(col, msg.data) }}</span>
                    <span class="field-value">{{ record[col] ?? '—' }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 数据卡片: 查询结果 / 智能查询结果 -->
            <div v-if="msg.data && (msg.data.type === 'query_result' || msg.data.type === 'smart_query_result') && msg.data.records?.length" class="data-card">
              <div class="data-card-header">
                <span>🔍 查询到 {{ msg.data.records.length }} 条记录</span>
              </div>
              <el-table :data="msg.data.records" size="small" max-height="200" stripe border>
                <el-table-column v-for="col in getDataColumns(msg.data.records)" :key="col"
                  :prop="col" :label="getColLabel(col, msg.data)" min-width="100" show-overflow-tooltip />
              </el-table>
            </div>

            <!-- 建议操作 -->
            <div v-if="msg.suggestions?.length" class="msg-suggestions">
              <el-button v-for="s in msg.suggestions" :key="s" size="small" round @click="sendQuick(s)">{{ s }}</el-button>
            </div>

            <!-- 复杂结果提示跳转AI页面 -->
            <div v-if="msg.intent === 'analysis' && msg.content && msg.content.length > 300" class="view-full">
              <el-button text type="primary" size="small" @click="goToAiPage">在AI页面查看完整报告 →</el-button>
            </div>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="aiStore.loading" class="msg-row assistant">
          <div class="msg-bubble ai-bubble loading-bubble">
            <span class="typing-dots"><span></span><span></span><span></span></span>
            AI 正在思考...
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="ai-panel-footer">
        <!-- 已选文件预览 -->
        <div v-if="uploadFiles.length" class="file-preview">
          <el-tag v-for="(f, i) in uploadFiles" :key="i" closable size="small"
            @close="uploadFiles.splice(i, 1)">📎 {{ f.name }}</el-tag>
        </div>
        <div class="input-row">
          <el-upload ref="uploadRef" :auto-upload="false" :show-file-list="false"
            :on-change="handleFileChange" accept=".jpg,.jpeg,.png,.pdf,.xlsx,.xls,.doc,.docx" multiple>
            <template #trigger>
              <el-icon class="attach-icon"><Paperclip /></el-icon>
            </template>
          </el-upload>
          <el-input v-model="inputText" placeholder="输入消息，或上传文件..."
            :autosize="{ minRows: 1, maxRows: 3 }" type="textarea" resize="none"
            @keydown.enter.exact.prevent="handleSend" />
          <el-button type="primary" :icon="Promotion" circle :disabled="!canSend" @click="handleSend" />
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Delete, Minus, Paperclip, Promotion } from '@element-plus/icons-vue'
import { useAiChatStore } from '@/store/ai-chat'
import AiMarkdown from '@/views/ai/components/AiMarkdown.vue'
import { createCustomer, createSupplier, createMaterial, createProduct, createProcess, getCustomerPage, getMaterialPage } from '@/api/master'
import { createWorkOrder } from '@/api/production'
import { createPurchaseRequest } from '@/api/purchase'
import { createOutsourceRequest } from '@/api/outsource'
import { createTeam } from '@/api/team'
import { createUser } from '@/api/system'
import { createDefect } from '@/api/quality'
import { createReceivable, createPayable, createWage } from '@/api/finance'
import { createSalesOrder } from '@/api/order'

const route = useRoute()
const router = useRouter()
const aiStore = useAiChatStore()

const inputText = ref('')
const uploadFiles = ref([])
const uploadRef = ref(null)
const messageArea = ref(null)

const visible = computed(() => aiStore.visible)

// 路由 → 模块映射
const routeModuleMap = {
  '/master/customer': { module: 'customer', name: '客户' },
  '/master/supplier': { module: 'supplier', name: '供应商' },
  '/master/material': { module: 'material', name: '物料' },
  '/master/product': { module: 'product', name: '产品' },
  '/master/process': { module: 'process', name: '工序' },
  '/order/list': { module: 'salesOrder', name: '销售订单' },
  '/order/create': { module: 'salesOrder', name: '销售订单' },
  '/production/work-order': { module: 'workOrder', name: '生产工单' },
  '/production/scheduling': { module: 'scheduling', name: '排产计划' },
  '/purchase': { module: 'purchase', name: '采购' },
  '/bom': { module: 'bom', name: 'BOM' },
  '/bom/process-route': { module: 'processRoute', name: '工艺路线' },
  '/outsource': { module: 'outsource', name: '外协' },
  '/inventory/stock': { module: 'inventory', name: '库存' },
  '/inventory/in': { module: 'stockIn', name: '入库' },
  '/inventory/out': { module: 'stockOut', name: '出库' },
  '/quality': { module: 'quality', name: '质量' },
  '/finance': { module: 'finance', name: '财务' },
  '/team/list': { module: 'team', name: '班组' },
  '/system/user': { module: 'user', name: '用户' },
  '/dashboard': { module: 'dashboard', name: '看板' }
}

const currentModule = computed(() => {
  const path = route.path
  for (const [prefix, info] of Object.entries(routeModuleMap)) {
    if (path.startsWith(prefix)) return info
  }
  return { module: 'general', name: '' }
})

const currentModuleName = computed(() => currentModule.value.name)

const canSend = computed(() => {
  return (inputText.value.trim() || uploadFiles.value.length > 0) && !aiStore.loading
})

// 自动滚动到底部
watch(() => aiStore.messages.length, () => {
  nextTick(() => {
    if (messageArea.value) {
      messageArea.value.scrollTop = messageArea.value.scrollHeight
    }
  })
})

watch(() => aiStore.loading, () => {
  nextTick(() => {
    if (messageArea.value) {
      messageArea.value.scrollTop = messageArea.value.scrollHeight
    }
  })
})

function handleFileChange(file) {
  uploadFiles.value.push(file)
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text && uploadFiles.value.length === 0) return
  if (aiStore.loading) return

  const msg = text || '请处理上传的文件'
  const files = [...uploadFiles.value]

  inputText.value = ''
  uploadFiles.value = []

  // 有文件时走旧版multipart接口，纯文本走新版conversation接口（支持smart_query）
  if (files.length > 0) {
    await aiStore.sendMessage(msg, currentModule.value.module, files)
  } else {
    aiStore.setCurrentPage(route.path)
    await aiStore.sendConversation(msg)
  }
}

function sendQuick(text) {
  inputText.value = text
  handleSend()
}

function handleClear() {
  aiStore.clearSession()
}

function goToAiPage() {
  aiStore.hide()
  router.push('/ai')
}

function handleFillForm(data) {
  aiStore.setFillData(data.module || currentModule.value.module, data.records)
  ElMessage.success('数据已准备好，请在表单中确认')
}

// 模块 → 创建 API 映射
const MODULE_SUBMIT_APIS = {
  customer: createCustomer,
  supplier: createSupplier,
  material: createMaterial,
  product: createProduct,
  process: createProcess,
  workOrder: createWorkOrder,
  purchaseRequest: createPurchaseRequest,
  outsourceRequest: createOutsourceRequest,
  team: createTeam,
  user: createUser,
  defect: createDefect,
  receivable: createReceivable,
  payable: createPayable,
  wage: createWage,
  salesOrder: createSalesOrder
}

// 每条消息的提交状态，key = msg idx
const submittingIdx = ref(new Set())

/**
 * 模糊匹配：在候选列表中按相似度找最佳匹配
 */
function bestMatch(candidates, nameField, keyword) {
  if (!candidates.length) return null
  const exact = candidates.find(c => c[nameField] === keyword)
  if (exact) return exact
  const contains = candidates.find(c => c[nameField]?.includes(keyword) || keyword.includes(c[nameField]))
  if (contains) return contains
  return candidates[0]
}

/**
 * 销售订单特殊处理：将AI返回的名称解析为系统ID后再创建
 */
async function resolveSalesOrder(record) {
  let customerId = record.customer?.matchedId
  if (!customerId && record.customer?.name) {
    const res = await getCustomerPage({ keyword: record.customer.name, pageNum: 1, pageSize: 10 })
    const customers = res.data?.records || []
    const matched = bestMatch(customers, 'customerName', record.customer.name)
    if (!matched) throw new Error(`未找到客户"${record.customer.name}"，请先在客户管理中添加`)
    customerId = matched.id
  }
  if (!customerId) throw new Error('无法识别客户信息')

  const items = []
  for (const item of (record.items || [])) {
    let productId = item.matchedProductId
    if (!productId && item.productName) {
      const res = await getMaterialPage({ keyword: item.productName, pageNum: 1, pageSize: 10 })
      const materials = res.data?.records || []
      const matched = bestMatch(materials, 'materialName', item.productName)
        || bestMatch(materials, 'productName', item.productName)
      if (!matched) throw new Error(`未找到产品"${item.productName}"，请先在物料管理中添加`)
      productId = matched.id
    }
    if (!productId) throw new Error(`产品"${item.productName || '未知'}"无法匹配`)
    items.push({
      productId,
      quantity: item.qty || item.quantity,
      unitPrice: item.unitPrice,
      deliveryDate: item.deliveryDate,
      taxRate: item.taxRate,
      remark: item.remark
    })
  }
  return { customerId, items }
}

/**
 * 从数据结构推断模块名（兜底逻辑）
 */
function inferModule(records) {
  if (!records?.length) return null
  const r = records[0]
  if (r.customer && r.items) return 'salesOrder'
  if (r.customerName || r.customerNo) return 'customer'
  if (r.supplierName || r.supplierNo) return 'supplier'
  if (r.materialName || r.materialNo) return 'material'
  if (r.productName && !r.items) return 'product'
  if (r.processName) return 'process'
  return null
}

async function handleDirectSubmit(data, msgIdx) {
  const module = data.module || inferModule(data.records) || currentModule.value.module
  const records = data.records || []
  const api = MODULE_SUBMIT_APIS[module]

  if (!api) {
    ElMessage.warning('当前模块暂不支持直接提交，请使用"填入表单"在对应页面操作')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认将 ${records.length} 条数据直接提交到系统？提交后将立即保存。`,
      '确认提交',
      { type: 'warning', confirmButtonText: '确认提交', cancelButtonText: '取消' }
    )
  } catch { return }

  submittingIdx.value = new Set([...submittingIdx.value, msgIdx])

  let successCount = 0
  const failMsgs = []
  for (const record of records) {
    try {
      if (module === 'salesOrder') {
        const payload = await resolveSalesOrder(record)
        await createSalesOrder(payload)
      } else {
        await api(record)
      }
      successCount++
    } catch (e) {
      failMsgs.push(e?.response?.data?.message || e.message || '未知错误')
    }
  }

  submittingIdx.value = new Set([...submittingIdx.value].filter(i => i !== msgIdx))

  let reply, replyType
  if (failMsgs.length === 0) {
    reply = `✅ 已成功提交 ${successCount} 条记录！`
    replyType = 'success'
  } else if (successCount === 0) {
    reply = `❌ 提交失败：${failMsgs[0]}。\n\n请点击"填入表单"进入对应页面检查后再提交。`
    replyType = 'error'
  } else {
    reply = `⚠️ 部分提交成功：${successCount} 条成功，${failMsgs.length} 条失败（${failMsgs[0]}）。`
    replyType = 'warning'
  }

  aiStore.messages.push({
    role: 'assistant',
    content: reply,
    time: new Date().toLocaleTimeString()
  })
  ElMessage[replyType](replyType === 'success' ? `成功提交 ${successCount} 条` : reply.replace(/^[✅❌⚠️]\s*/, '').slice(0, 40))
}

function renderReply(text) {
  if (!text) return ''
  return text
    // 加粗
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    // 有序列表
    .replace(/^(\d+)\.\s+(.*)$/gm, '<div class="reply-list-item"><span class="reply-num">$1.</span> $2</div>')
    // 无序列表
    .replace(/^[-•]\s+(.*)$/gm, '<div class="reply-list-item"><span class="reply-dot">·</span> $1</div>')
    // 换行
    .replace(/\n/g, '<br>')
}

const EXCLUDE_COLS = new Set(['id', 'deleted', 'tenantId', 'createBy', 'updateBy', 'createTime', 'updateTime'])

/** 竖向表单：显示全部有意义的字段 */
function getFormColumns(records) {
  if (!records || records.length === 0) return []
  return Object.keys(records[0]).filter(k => !EXCLUDE_COLS.has(k))
}

/** 横向表格：最多展示6列，避免溢出 */
function getDataColumns(records) {
  if (!records || records.length === 0) return []
  return Object.keys(records[0]).filter(k => !EXCLUDE_COLS.has(k)).slice(0, 6)
}

const defaultColLabels = {
  // 通用
  id: 'ID', status: '状态', remark: '备注', createBy: '创建人', createTime: '创建时间',
  updateTime: '更新时间', quantity: '数量', amount: '金额', unit: '单位', spec: '规格',
  // 客户/供应商
  customerName: '客户名称', customer_name: '客户名称',
  customerNo: '客户编号', customer_no: '客户编号',
  customerId: '客户ID', customer_id: '客户ID',
  contactName: '联系人', contact_name: '联系人',
  phone: '电话', email: '邮箱', address: '地址',
  supplierName: '供应商名称', supplier_name: '供应商名称',
  supplierNo: '供应商编号', supplier_no: '供应商编号',
  // 订单
  orderNo: '订单编号', order_no: '订单编号',
  totalAmount: '总金额', total_amount: '总金额',
  deliveryDate: '交货日期', delivery_date: '交货日期',
  paymentAmount: '已回款', payment_amount: '已回款',
  // 物料/产品
  materialName: '物料名称', material_name: '物料名称',
  materialNo: '物料编号', material_no: '物料编号',
  productName: '产品名称', product_name: '产品名称',
  productNo: '产品编号', product_no: '产品编号',
  unitPrice: '单价', unit_price: '单价',
  // 工单
  woNo: '工单编号', wo_no: '工单编号',
  planQty: '计划数量', plan_qty: '计划数量',
  completedQty: '完成数量', completed_qty: '完成数量',
  // 工序/BOM
  processName: '工序名称', process_name: '工序名称',
  routeName: '工艺路线', route_name: '工艺路线',
  // 采购
  poNo: '采购单号', po_no: '采购单号',
  requestNo: '采购申请单号', request_no: '采购申请单号',
  // 班组/员工
  teamName: '班组名称', team_name: '班组名称',
  userName: '用户名', user_name: '用户名',
  realName: '姓名', real_name: '姓名'
}

function getColLabel(col, data) {
  if (data?.columnLabels && data.columnLabels[col]) return data.columnLabels[col]
  return defaultColLabels[col] || col
}
</script>

<style scoped lang="scss">
// 悬浮按钮
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7B61FF 0%, #5B8DEF 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(123, 97, 255, 0.4);
  z-index: 2000;
  transition: transform 0.2s, box-shadow 0.2s;
  &:hover {
    transform: scale(1.08);
    box-shadow: 0 6px 24px rgba(123, 97, 255, 0.5);
  }
}

// 聊天面板
.ai-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 400px;
  height: 560px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.15);
  z-index: 2000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

// 标题栏
.ai-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #7B61FF 0%, #5B8DEF 100%);
  color: #fff;
  .header-info {
    display: flex;
    align-items: center;
    gap: 8px;
    .header-title {
      font-size: 15px;
      font-weight: 600;
    }
    .el-tag {
      background: rgba(255, 255, 255, 0.2);
      color: #fff;
      border: none;
    }
  }
  .header-actions {
    display: flex;
    gap: 8px;
    .action-icon {
      font-size: 18px;
      cursor: pointer;
      opacity: 0.8;
      &:hover { opacity: 1; }
    }
  }
}

// 消息区域
.ai-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  background: #f8f9fc;
}

// 欢迎消息
.welcome-msg {
  text-align: center;
  padding: 40px 20px 20px;
  .welcome-icon { font-size: 48px; margin-bottom: 12px; }
  .welcome-text { font-size: 16px; font-weight: 600; color: #333; }
  .welcome-sub { font-size: 13px; color: #999; margin-top: 6px; }
  .quick-actions {
    margin-top: 20px;
    display: flex;
    gap: 8px;
    justify-content: center;
    flex-wrap: wrap;
  }
}

// 消息行
.msg-row {
  margin-bottom: 12px;
  display: flex;
  &.user { justify-content: flex-end; }
  &.assistant { justify-content: flex-start; }
}

.msg-bubble {
  max-width: 85%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.user-bubble {
  background: linear-gradient(135deg, #7B61FF, #5B8DEF);
  color: #fff;
  border-bottom-right-radius: 4px;
  .msg-files { margin-top: 6px; }
}

.ai-bubble {
  background: #fff;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  :deep(.reply-list-item) {
    display: flex;
    gap: 4px;
    padding: 1px 0;
    .reply-num { font-weight: 600; color: #7B61FF; min-width: 18px; }
    .reply-dot { font-weight: 700; color: #7B61FF; min-width: 12px; }
  }
  :deep(strong) {
    color: #1a1a2e;
  }
}

// Loading动画
.loading-bubble {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #999;
}
.typing-dots {
  display: inline-flex;
  gap: 3px;
  span {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #7B61FF;
    animation: typing 1.4s infinite;
    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}
@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1); }
}

// 数据卡片
.data-card {
  margin-top: 10px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  overflow: hidden;
  .data-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    background: #fafafa;
    font-size: 13px;
    border-bottom: 1px solid #e8e8e8;
    .card-actions {
      display: flex;
      gap: 6px;
    }
  }
}

// 竖向表单记录（input_form）
.form-record {
  padding: 10px 12px;
  & + .form-record {
    border-top: 1px solid #f0f0f0;
  }
  .record-index {
    font-size: 11px;
    color: #999;
    margin-bottom: 6px;
    padding-bottom: 4px;
    border-bottom: 1px dashed #eee;
  }
}
.form-fields {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-field-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  .field-label {
    color: #909399;
    min-width: 72px;
    flex-shrink: 0;
    text-align: right;
    &::after { content: '：'; }
  }
  .field-value {
    color: #303133;
    flex: 1;
    word-break: break-all;
    font-weight: 500;
  }
}

// 建议操作
.msg-suggestions {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

// 查看完整报告链接
.view-full {
  margin-top: 8px;
  text-align: right;
}

// 输入区域
.ai-panel-footer {
  border-top: 1px solid #f0f0f0;
  padding: 10px 12px;
  background: #fff;
  .file-preview {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
    margin-bottom: 8px;
  }
  .input-row {
    display: flex;
    align-items: flex-end;
    gap: 8px;
    .attach-icon {
      font-size: 20px;
      color: #999;
      cursor: pointer;
      &:hover { color: #7B61FF; }
    }
    .el-textarea { flex: 1; }
  }
}

// 面板动画
.ai-panel-enter-active, .ai-panel-leave-active {
  transition: all 0.3s ease;
}
.ai-panel-enter-from, .ai-panel-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}
.msg-files {
  .el-tag { margin-right: 4px; }
}
</style>
