<template>
  <div class="page-container">
    <el-card class="table-card" shadow="never">
        <!-- 外协申请 Tab 已废弃，下方代码隐藏保留 -->
        <div v-if="false">
          <el-form :model="requestQuery" inline class="tab-search-form">
            <el-form-item label="关键词">
              <el-input v-model="requestQuery.keyword" placeholder="申请编号" clearable style="width: 150px" @keyup.enter="handleRequestSearch" />
            </el-form-item>
            <el-form-item label="工单编号">
              <el-input v-model="requestQuery.workOrderNo" placeholder="工单编号" clearable style="width: 150px" @keyup.enter="handleRequestSearch" />
            </el-form-item>
            <el-form-item label="工序名称">
              <el-input v-model="requestQuery.stepName" placeholder="工序名称" clearable style="width: 130px" @keyup.enter="handleRequestSearch" />
            </el-form-item>
            <el-form-item label="产品">
              <el-select v-model="requestQuery.productId" placeholder="全部" clearable filterable remote
                         :remote-method="searchProducts" :loading="productSearching" style="width: 170px">
                <el-option v-for="item in productOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="requestQuery.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="待审批" value="pending" />
                <el-option label="已审批" value="approved" />
                <el-option label="已驳回" value="rejected" />
                <el-option label="已转单" value="ordered" />
                <el-option label="已完成" value="completed" />
                <el-option label="已取消" value="cancelled" />
              </el-select>
            </el-form-item>
            <el-form-item label="需求日期">
              <el-date-picker v-model="requestQuery.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRequestSearch">搜索</el-button>
              <el-button @click="handleRequestReset">重置</el-button>
              <el-button type="primary" plain @click="handleAddRequest">新增申请</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="requestData" v-loading="requestLoading" border stripe>
            <el-table-column prop="requestNo" label="申请编号" min-width="140" />
            <el-table-column prop="workOrderNo" label="工单编号" min-width="140">
              <template #default="{ row }">
                <el-button v-if="row.workOrderId" type="primary" link @click="goWorkOrderDetail(row.workOrderId)">{{ row.workOrderNo }}</el-button>
                <span v-else>{{ row.workOrderNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="stepName" label="工序名称" min-width="120" />
            <el-table-column prop="productName" label="产品名称" min-width="140" />
            <el-table-column prop="virtualMaterialName" label="发料物料" min-width="130" />
            <el-table-column prop="receiptMaterialName" label="收料物料" min-width="130" />
            <el-table-column prop="quantity" label="数量" width="100" align="right" />
            <el-table-column prop="maxQty" label="最大数量" width="100" align="right" />
            <el-table-column prop="requestableQty" label="可申请" width="90" align="right">
              <template #default="{ row }">
                <span :style="{ color: row.requestableQty > 0 ? '#67C23A' : '#909399' }">{{ row.requestableQty ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isFullOutsource" type="warning" size="small">整单外协</el-tag>
                <el-tag v-else-if="row.isAutoCreated" type="" size="small">自动</el-tag>
                <el-tag v-else type="info" size="small">手动</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联订单" min-width="140">
              <template #default="{ row }">
                <span v-if="row.salesOrderNo">{{ row.salesOrderNo }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="requiredDate" label="需求日期" width="120" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="requestStatusType(row.status)" size="small">
                  {{ requestStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button type="success" link size="small" @click="handleApproveRequest(row)">审批</el-button>
                  <el-button type="danger" link size="small" @click="handleRejectRequest(row)">驳回</el-button>
                </template>
                <span v-if="row.status !== 'pending'" class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="requestQuery.pageNum"
              v-model:page-size="requestQuery.pageSize"
              :total="requestTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadRequests"
              @current-change="loadRequests"
            />
          </div>
        </div>

          <el-form :model="orderQuery" inline class="tab-search-form">
            <el-form-item label="关键词">
              <el-input v-model="orderQuery.keyword" placeholder="加工单号" clearable style="width: 150px" @keyup.enter="handleOrderSearch" />
            </el-form-item>
            <el-form-item label="关联单号">
              <el-input v-model="orderQuery.workOrderNo" placeholder="工单号/销售订单号" clearable style="width: 180px" @keyup.enter="handleOrderSearch" />
            </el-form-item>
            <el-form-item label="工序名称">
              <el-input v-model="orderQuery.stepName" placeholder="工序名称" clearable style="width: 130px" @keyup.enter="handleOrderSearch" />
            </el-form-item>
            <el-form-item label="产品">
              <el-select v-model="orderQuery.productId" placeholder="全部" clearable filterable remote
                         :remote-method="searchProducts" :loading="productSearching" style="width: 170px">
                <el-option v-for="item in productOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="orderQuery.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="草稿" value="draft" />
                <el-option label="待审批" value="pending_approval" />
                <el-option label="已确认" value="confirmed" />
                <el-option label="加工中" value="processing" />
                <el-option label="已完成" value="completed" />
                <el-option label="已取消" value="cancelled" />
              </el-select>
            </el-form-item>
            <el-form-item label="供应商">
              <el-select v-model="orderQuery.supplierId" placeholder="全部" clearable filterable style="width: 170px">
                <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="交付日期">
              <el-date-picker v-model="orderQuery.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleOrderSearch">搜索</el-button>
              <el-button @click="handleOrderReset">重置</el-button>
              <el-button type="primary" plain @click="handleAddOrder">新增加工单</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="orderData" v-loading="orderLoading" border stripe>
            <el-table-column prop="orderNo" label="加工单号" min-width="140" />
            <el-table-column label="来源" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isFullOutsource" type="warning" size="small">销售订单</el-tag>
                <el-tag v-else size="small">生产工单</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联单据号" min-width="150">
              <template #default="{ row }">
                <el-button v-if="row.sourceNo" type="primary" link size="small" @click="handleViewSource(row)">{{ row.sourceNo }}</el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="supplierName" label="供应商" min-width="140" />
            <el-table-column prop="productName" label="产品名称" min-width="140" />
            <el-table-column prop="stepName" label="工序名称" min-width="120" />
            <el-table-column prop="totalQty" label="总数量" width="90" align="right" />
            <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
            <el-table-column prop="totalAmount" label="总金额" width="100" align="right" />
            <el-table-column prop="taxRate" label="税率%" width="80" align="right" />
            <el-table-column prop="taxAmount" label="税额" width="90" align="right" />
            <el-table-column prop="deliveredQty" label="已交付" width="80" align="right" />
            <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="orderStatusType(row.status)" size="small">
                  {{ orderStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="320" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleViewOrder(row)">查看</el-button>
                <el-button v-if="row.status === 'draft'" link size="small" @click="handleEditOrder(row)">编辑</el-button>
                <el-button v-if="row.status === 'draft'" type="primary" link size="small" @click="handleSubmitOrder(row)">提交审批</el-button>
                <el-button v-if="row.status === 'pending_approval'" type="success" link size="small" @click="handleApproveOrder(row)">审批</el-button>
                <el-button v-if="row.status === 'pending_approval'" type="danger" link size="small" @click="handleRejectOrder(row)">驳回</el-button>
                <el-button v-if="['confirmed','processing'].includes(row.status)" type="warning" link size="small" @click="handleDelivery(row)">发料</el-button>
                <el-button v-if="['confirmed','processing','partial_receipt'].includes(row.status)" type="info" link size="small" @click="openGenerateReceipt(row)">生成收料单</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="orderQuery.pageNum"
              v-model:page-size="orderQuery.pageSize"
              :total="orderTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadOrders"
              @current-change="loadOrders"
            />
          </div>
    </el-card>

    <!-- add request dialog -->
    <el-dialog v-model="requestDialogVisible" title="新增外协申请" width="560px" destroy-on-close @close="resetRequestForm">
      <el-form ref="requestFormRef" :model="requestForm" :rules="requestRules" label-width="100px">
        <el-form-item label="工单ID" prop="workOrderId">
          <el-input v-model="requestForm.workOrderId" placeholder="请输入工单ID" />
        </el-form-item>
        <el-form-item label="任务ID" prop="taskId">
          <el-input v-model="requestForm.taskId" placeholder="请输入任务ID" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="requestForm.quantity" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="加工要求" prop="processRequirement">
          <el-input v-model="requestForm.processRequirement" type="textarea" :rows="3" placeholder="请输入加工要求" />
        </el-form-item>
        <el-form-item label="需求日期" prop="requiredDate">
          <el-date-picker
            v-model="requestForm.requiredDate"
            type="date"
            placeholder="请选择需求日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="requestDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleRequestSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- add/edit order dialog -->
    <el-dialog v-model="orderDialogVisible" :title="editingOrderId ? '编辑外协加工单' : '新增外协加工单'" width="560px" destroy-on-close @close="resetOrderForm">
      <el-form ref="orderFormRef" :model="orderForm" :rules="orderRules" label-width="100px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="orderForm.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingOrderId" label="关联工单">
          <el-select v-model="orderForm.workOrderId" placeholder="选择关联工单(可联动选工序、自动带出产品)"
                     filterable clearable style="width: 100%" @change="onOrderWorkOrderChange">
            <el-option v-for="item in workOrderOptions" :key="item.id"
                       :label="`${item.woNo || item.orderNo}${item.productName ? ' - ' + item.productName : ''}`"
                       :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingOrderId && orderForm.workOrderId" label="工序" prop="taskId">
          <el-select v-model="orderForm.taskId" placeholder="请选择该工单下的工序" filterable clearable
                     style="width: 100%" :loading="orderTaskLoading" @change="onOrderTaskChange">
            <el-option v-for="t in orderTaskOptions" :key="t.id"
                       :label="`第${t.stepNo}道 ${t.stepName}${t.qualifiedQty != null ? ' (合格 ' + t.qualifiedQty + '/' + t.planQty + ')' : ''}`"
                       :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingOrderId" label="产品" prop="productId">
          <!-- 选了工单 → 只读显示;否则按物料档案搜索选择 -->
          <el-input v-if="orderForm.workOrderId" :model-value="orderForm.productName" readonly placeholder="选择关联工单/工序后自动带出" />
          <el-select v-else v-model="orderForm.productId" placeholder="请选择产品" filterable remote
                     :remote-method="searchProducts" :loading="productSearching" style="width: 100%">
            <el-option v-for="item in productOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingOrderId" label="工序名称" prop="stepName">
          <el-input v-model="orderForm.stepName" :readonly="!!orderForm.taskId"
                    :placeholder="orderForm.taskId ? '已由所选工序带出' : '请输入工序名称'" />
        </el-form-item>
        <el-form-item v-if="!editingOrderId" label="总数量" prop="totalQty">
          <el-input-number v-model="orderForm.totalQty" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单价" prop="unitPrice">
          <el-input-number v-model="orderForm.unitPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率(%)" prop="taxRate">
          <el-input-number v-model="orderForm.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交付日期" prop="deliveryDate">
          <el-date-picker v-model="orderForm.deliveryDate" type="date" placeholder="请选择交付日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工艺要求">
          <el-input v-model="orderForm.processRequirement" type="textarea" :rows="3" placeholder="请输入工艺要求" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="orderForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleOrderSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- view order detail drawer -->
    <el-drawer v-model="detailDialogVisible" title="外协加工单详情" size="760px" destroy-on-close>
      <el-tabs v-model="detailTab">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="加工单号">{{ orderDetail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="来源">
              <el-tag v-if="orderDetail.isFullOutsource" type="warning" size="small">整单外协</el-tag>
              <el-tag v-else size="small">工序外协</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="orderDetail.salesOrderNo" label="关联订单">{{ orderDetail.salesOrderNo }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ orderDetail.supplierName }}</el-descriptions-item>
            <el-descriptions-item label="产品名称">{{ orderDetail.productName }}</el-descriptions-item>
            <el-descriptions-item label="工序名称">{{ orderDetail.stepName }}</el-descriptions-item>
            <el-descriptions-item label="总数量">{{ orderDetail.totalQty }}</el-descriptions-item>
            <el-descriptions-item label="单价">¥{{ Number(orderDetail.unitPrice || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="总金额(含税)">
              <span style="font-weight: 700; color: #f56c6c">¥{{ Number(orderDetail.totalAmount || 0).toFixed(2) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="税率">{{ orderDetail.taxRate || 0 }}%</el-descriptions-item>
            <el-descriptions-item label="税额">¥{{ Number(orderDetail.taxAmount || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="发料任务/出库">{{ orderDetail.deliveryTaskQty || 0 }} / {{ orderDetail.deliveryOutQty || 0 }}</el-descriptions-item>
            <!-- 收货任务/入库字段已移除 -->
            <el-descriptions-item label="状态">
              <el-tag :type="orderStatusType(orderDetail.status)" size="small">{{ orderStatusText(orderDetail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="交付日期">{{ orderDetail.deliveryDate }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ orderDetail.createTime }}</el-descriptions-item>
          </el-descriptions>
          <!-- 进度概览 -->
          <div style="margin-top: 16px; padding: 12px; background: #f5f7fa; border-radius: 8px">
            <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; color: #606266">
              <span>加工进度</span>
              <span>{{ orderDetail.qualifiedQty || 0 }} / {{ orderDetail.totalQty || 0 }}</span>
            </div>
            <el-progress
              :percentage="orderDetail.totalQty ? Math.min(Math.round(((orderDetail.qualifiedQty || 0) / orderDetail.totalQty) * 100), 100) : 0"
              :stroke-width="16"
              :text-inside="true"
              :color="(orderDetail.qualifiedQty || 0) >= (orderDetail.totalQty || 0) ? '#67C23A' : '#409EFF'"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 2: 发料记录（库存任务数据） -->
        <el-tab-pane label="发料记录" name="delivery">
          <el-table :data="orderDetail.deliveryTasks || []" border stripe size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="taskNo" label="任务编号" width="150" show-overflow-tooltip />
            <el-table-column prop="materialNo" label="物料编码" width="110" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
            <el-table-column label="规格" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) || '-' }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="55" align="center" />
            <el-table-column prop="quantity" label="任务数量" width="85" align="right" />
            <el-table-column label="已出库" width="85" align="right">
              <template #default="{ row }">
                <span :style="{ color: (row.processedQty || 0) >= (row.quantity || 0) ? '#67c23a' : '' }">{{ row.processedQty || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="任务状态" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="{ pending: 'warning', partial: '', completed: 'success', cancelled: 'danger', returned: 'info' }[row.status]" size="small">
                  {{ { pending: '待处理', partial: '部分完成', completed: '已完成', cancelled: '已取消', returned: '已退回' }[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="退回原因" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.returnReason" style="color:#f56c6c">{{ row.returnReason }}</span>
                <span v-else style="color:#c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="155" />
          </el-table>
          <el-empty v-if="!(orderDetail.deliveryTasks || []).length" description="暂无发料记录" />
        </el-tab-pane>

        <!-- 收货/质检tab已移至外协收料单模块 -->

        <!-- Tab 4: 费用结算 -->
        <el-tab-pane label="费用结算" name="cost">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="单价">¥{{ Number(orderDetail.unitPrice || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="合格数量">{{ orderDetail.qualifiedQty || 0 }}</el-descriptions-item>
            <el-descriptions-item label="结算金额">
              <span style="font-weight: 700; color: #f56c6c">
                ¥{{ (Number(orderDetail.unitPrice || 0) * Number(orderDetail.qualifiedQty || 0)).toFixed(2) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="结算状态">
              <el-tag :type="orderDetail.status === 'settled' ? 'success' : 'info'" size="small">
                {{ orderDetail.status === 'settled' ? '已结算' : '待结算' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- approve dialog -->
    <el-dialog v-model="approveDialogVisible" title="审批外协申请" width="560px" destroy-on-close @close="resetApproveForm">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="申请编号">{{ approveContext.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="工序名称">{{ approveContext.stepName }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ approveContext.productName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ approveContext.quantity }}</el-descriptions-item>
        <el-descriptions-item v-if="approveContext.virtualMaterialName" label="发料物料">{{ approveContext.virtualMaterialName }}</el-descriptions-item>
        <el-descriptions-item v-if="approveContext.receiptMaterialName" label="收料物料">{{ approveContext.receiptMaterialName }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="approveFormRef" :model="approveForm" :rules="approveRules" label-width="100px">
        <el-form-item label="外协供应商" prop="supplierId">
          <el-select v-model="approveForm.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单价" prop="unitPrice">
          <el-input-number v-model="approveForm.unitPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率(%)" prop="taxRate">
          <el-input-number v-model="approveForm.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="总金额">
          <span style="font-weight: 700; font-size: 16px; color: #f56c6c">
            ¥{{ computedTotalAmount }}
          </span>
          <span v-if="approveForm.taxRate > 0" style="margin-left: 8px; color: #909399; font-size: 12px">
            (含税 ¥{{ computedTaxAmount }})
          </span>
        </el-form-item>
        <el-form-item label="交付日期" prop="deliveryDate">
          <el-date-picker v-model="approveForm.deliveryDate" type="date" placeholder="请选择交付日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="审批备注">
          <el-input v-model="approveForm.approveRemark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleApproveSubmit">审批通过</el-button>
      </template>
    </el-dialog>

    <!-- 发料弹窗（物料明细表格） -->
    <el-dialog v-model="deliveryDialogVisible" title="外协发料" width="900px" destroy-on-close>
      <el-descriptions :column="3" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="加工单号">{{ deliveryContext.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ deliveryContext.productName }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ deliveryContext.totalQty }}</el-descriptions-item>
      </el-descriptions>
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
        <div style="font-weight:600;color:#606266">发料明细（填写本次发料数量和出库仓库）</div>
        <el-button type="primary" plain size="small" @click="deliveryPickerVisible = true">+ 添加物料</el-button>
      </div>
      <el-table :data="deliveryItems" border size="small">
        <el-table-column prop="materialNo" label="物料编码" width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="规格" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column label="本次数量" width="130">
          <template #header><span style="color:#F56C6C">* </span>本次数量</template>
          <template #default="{ row }">
            <el-input-number v-model="row.qty" :min="0" :precision="4" size="small" controls-position="right" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="仓库" min-width="150">
          <template #header><span style="color:#F56C6C">* </span>仓库</template>
          <template #default="{ row }">
            <el-select v-model="row.warehouseId" size="small" style="width:100%" placeholder="请选择仓库">
              <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="65" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" size="small" @click="deliveryItems.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <MaterialPickerDialog v-model="deliveryPickerVisible" title="选择发料物料" :multiple="true"
        @select="onDeliveryMaterialsPicked" />
      <el-form style="margin-top:12px" label-width="60px">
        <el-form-item label="备注"><el-input v-model="deliveryRemark" type="textarea" :rows="2" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliveryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleDeliverySubmit">确认发料</el-button>
      </template>
    </el-dialog>

    <!-- 生成收料单弹窗 -->
    <el-dialog v-model="generateReceiptVisible" title="生成外协收料单" width="560px" destroy-on-close>
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="加工单号">{{ generateReceiptRow.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ generateReceiptRow.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ generateReceiptRow.productName }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ generateReceiptRow.stepName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收料物料编码">{{ generateReceiptRow.receiptMaterialNo || generateReceiptRow.materialNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收料物料名称">{{ generateReceiptRow.receiptMaterialName || generateReceiptRow.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ $fmtSpec(generateReceiptRow.receiptMaterialSpec || generateReceiptRow.spec) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ generateReceiptRow.receiptMaterialUnit || generateReceiptRow.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总数量">{{ generateReceiptRow.totalQty }}</el-descriptions-item>
        <el-descriptions-item label="已生成收料">
          <span :style="{ color: Number(generateReceiptRow.receiptQty || 0) >= Number(generateReceiptRow.totalQty || 0) ? '#67c23a' : '' }">
            {{ generateReceiptRow.receiptQty || 0 }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="剩余可收料">
          <span style="color:#e6a23c;font-weight:600">{{ Math.max(0, Number(generateReceiptRow.totalQty || 0) - Number(generateReceiptRow.receiptQty || 0)) }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px">
        <el-form-item label="收料数量" required>
          <el-input-number v-model="generateReceiptForm.qty" :min="0.01"
            :max="Number(generateReceiptRow.totalQty || 0) - Number(generateReceiptRow.receiptQty || 0)"
            :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="generateReceiptForm.batchNo" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateReceiptVisible = false">取消</el-button>
        <el-button type="primary" :loading="generateReceiptLoading" @click="doGenerateReceipt">生成收料单</el-button>
      </template>
    </el-dialog>

    <!-- 关联单据详情弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="sourceDetailVisible" :relatedOrderNo="sourceDetailOrderNo" :invType="sourceDetailInvType" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import {
  getOutsourceRequestPage,
  createOutsourceRequest,
  approveOutsourceRequest,
  rejectOutsourceRequest,
  getOutsourceOrderPage,
  getOutsourceOrderDetail,
  createOutsourceOrder,
  updateOutsourceOrder,
  confirmOutsourceOrder,
  submitOutsourceOrder,
  approveOutsourceOrder,
  rejectOutsourceOrder,
  createOutsourceDelivery,
  createOutsourceReceipt,
  generateOutsourceReceipts
} from '@/api/outsource'
import { getSupplierSelect, getMaterialPage } from '@/api/master'
import { getWarehouseSelect } from '@/api/system'
import { getWorkOrderPage, getWorkOrderTasks } from '@/api/production'

const router = useRouter()

// --- state ---
const activeTab = ref('orders')
const submitLoading = ref(false)

const supplierOptions = ref([])
const productOptions = ref([])
const warehouseOptions = ref([])

// request tab
const requestLoading = ref(false)
const requestData = ref([])
const requestTotal = ref(0)
const requestQuery = reactive({ keyword: '', workOrderNo: '', stepName: '', productId: undefined, status: undefined, dateRange: null, pageNum: 1, pageSize: 10 })

// order tab
const orderLoading = ref(false)
const orderData = ref([])
const orderTotal = ref(0)
const orderQuery = reactive({ keyword: '', workOrderNo: '', stepName: '', productId: undefined, status: undefined, supplierId: undefined, dateRange: null, pageNum: 1, pageSize: 10 })

// request dialog
const requestDialogVisible = ref(false)
const requestFormRef = ref(null)
const requestForm = ref({
  workOrderId: '',
  taskId: '',
  quantity: 1,
  processRequirement: '',
  requiredDate: ''
})
const requestRules = {
  workOrderId: [{ required: true, message: '请输入工单ID', trigger: 'blur' }],
  taskId: [{ required: true, message: '请输入任务ID', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  requiredDate: [{ required: true, message: '请选择需求日期', trigger: 'change' }]
}

// order dialog
const orderDialogVisible = ref(false)
const orderFormRef = ref(null)
const editingOrderId = ref(null)
const orderForm = ref({
  supplierId: undefined,
  productId: undefined,
  productName: '',
  stepName: '',
  totalQty: 1,
  unitPrice: 0,
  taxRate: 13,
  deliveryQty: 0,
  deliveryDate: '',
  requestIds: [],
  workOrderId: undefined,
  taskId: undefined,
  virtualMaterialId: undefined
})

// 关联工单 / 工序联动
const workOrderOptions = ref([])
const orderTaskOptions = ref([])
const orderTaskLoading = ref(false)

async function loadWorkOrderOptions() {
  try {
    const res = await getWorkOrderPage({ pageNum: 1, pageSize: 200 })
    workOrderOptions.value = res.data?.records || []
  } catch { workOrderOptions.value = [] }
}

async function onOrderWorkOrderChange(woId) {
  orderForm.value.taskId = undefined
  orderForm.value.stepName = ''
  orderTaskOptions.value = []
  if (!woId) {
    orderForm.value.productId = undefined
    orderForm.value.productName = ''
    return
  }
  const wo = workOrderOptions.value.find(w => w.id === woId)
  if (wo) {
    orderForm.value.productId = wo.productId || undefined
    orderForm.value.productName = wo.productName || ''
  }
  orderTaskLoading.value = true
  try {
    const res = await getWorkOrderTasks(woId)
    orderTaskOptions.value = (res.data || []).filter(t => t.status !== 'cancelled')
  } catch { orderTaskOptions.value = [] }
  finally { orderTaskLoading.value = false }
}

function onOrderTaskChange(taskId) {
  const t = orderTaskOptions.value.find(x => x.id === taskId)
  if (!t) { orderForm.value.stepName = ''; return }
  orderForm.value.stepName = t.stepName || ''
  const remain = Math.max(Number(t.planQty || 0) - Number(t.qualifiedQty || 0), 0)
  orderForm.value.totalQty = remain > 0 ? remain : (Number(t.planQty) || 1)
}
const orderRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  stepName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }],
  totalQty: [{ required: true, message: '请输入总数量', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  deliveryDate: [{ required: true, message: '请选择交付日期', trigger: 'change' }]
}

// detail dialog
const detailDialogVisible = ref(false)
const detailTab = ref('basic')
const orderDetail = ref({})

// delivery dialog
const deliveryDialogVisible = ref(false)
const deliveryContext = ref({})
const deliveryItems = ref([])
const deliveryRemark = ref('')
const deliveryPickerVisible = ref(false)
function onDeliveryMaterialsPicked(rows) {
  const list = Array.isArray(rows) ? rows : [rows]
  const existing = new Set(deliveryItems.value.map(i => i.materialId).filter(Boolean))
  for (const m of list) {
    if (!m || existing.has(m.id)) continue
    deliveryItems.value.push({
      materialId: m.id,
      materialNo: m.materialNo,
      materialName: m.materialName,
      spec: m.spec,
      unit: m.unit,
      qty: 1,
      warehouseId: m.defaultWarehouseId || warehouseOptions.value[0]?.id || null
    })
  }
}

// receipt dialog
const receiptDialogVisible = ref(false)
const receiptContext = ref({})
const receiptItems = ref([])
const receiptRemark = ref('')

// approve dialog
const approveDialogVisible = ref(false)
const approveFormRef = ref(null)
const approveContext = ref({ requestNo: '', stepName: '', productName: '', quantity: 0 })
const approveForm = ref({ supplierId: undefined, unitPrice: 0, taxRate: 13, deliveryDate: '', approveRemark: '' })
// (已默认 13%)
const approveRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  unitPrice: [{ required: true, message: '请填写单价', trigger: 'blur' }],
  deliveryDate: [{ required: true, message: '请选择交付日期', trigger: 'change' }]
}
const computedTotalAmount = computed(() => {
  const base = (approveForm.value.unitPrice || 0) * (approveContext.value.quantity || 0)
  const tax = base * (approveForm.value.taxRate || 0) / 100
  return (base + tax).toFixed(2)
})
const computedTaxAmount = computed(() => {
  const base = (approveForm.value.unitPrice || 0) * (approveContext.value.quantity || 0)
  return (base * (approveForm.value.taxRate || 0) / 100).toFixed(2)
})

// --- helpers ---
function requestStatusType(status) {
  const map = { pending: 'warning', approved: 'success', rejected: 'danger', ordered: 'primary', completed: 'success', cancelled: 'danger' }
  return map[status] || 'info'
}
function requestStatusText(status) {
  const map = { pending: '待审批', approved: '已审批', rejected: '已驳回', ordered: '已转单', completed: '已完成', cancelled: '已取消' }
  return map[status] || status
}
function orderStatusType(status) {
  const map = { draft: 'info', confirmed: 'primary', material_sent: '', processing: 'warning', partial_received: '', received: '', settled: 'success', cancelled: 'danger' }
  return map[status] || 'info'
}
function orderStatusText(status) {
  const map = { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', processing: '加工中', completed: '已完成', cancelled: '已取消' }
  return map[status] || status
}

// --- methods ---
function goWorkOrderDetail(workOrderId) {
  router.push({ path: '/production/work-orders', query: { id: workOrderId } })
}

async function loadRequests() {
  requestLoading.value = true
  try {
    const params = { ...requestQuery }
    if (params.dateRange && params.dateRange.length === 2) {
      params.startDate = params.dateRange[0]
      params.endDate = params.dateRange[1]
    }
    delete params.dateRange
    const res = await getOutsourceRequestPage(params)
    requestData.value = res.data.records || []
    requestTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    requestLoading.value = false
  }
}

function handleRequestSearch() { requestQuery.pageNum = 1; loadRequests() }
function handleRequestReset() {
  requestQuery.keyword = ''; requestQuery.workOrderNo = ''; requestQuery.stepName = ''
  requestQuery.productId = undefined; requestQuery.status = undefined; requestQuery.dateRange = null
  requestQuery.pageNum = 1; loadRequests()
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const params = { ...orderQuery }
    if (params.dateRange && params.dateRange.length === 2) {
      params.startDate = params.dateRange[0]
      params.endDate = params.dateRange[1]
    }
    delete params.dateRange
    const res = await getOutsourceOrderPage(params)
    orderData.value = res.data.records || []
    orderTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    orderLoading.value = false
  }
}

function handleOrderSearch() { orderQuery.pageNum = 1; loadOrders() }
function handleOrderReset() {
  orderQuery.keyword = ''; orderQuery.workOrderNo = ''; orderQuery.stepName = ''
  orderQuery.productId = undefined; orderQuery.status = undefined; orderQuery.supplierId = undefined
  orderQuery.dateRange = null; orderQuery.pageNum = 1; loadOrders()
}

async function loadOptions() {
  // 产品下拉改 remote 按需搜索;供应商/仓库保留(数据小)
  try {
    const [supRes, whRes] = await Promise.all([
      getSupplierSelect(),
      getWarehouseSelect()
    ])
    supplierOptions.value = supRes.data || []
    warehouseOptions.value = whRes.data || []
  } catch {}
}

const productSearching = ref(false)
let _prodSearchTimer = null
async function searchProducts(keyword) {
  if (_prodSearchTimer) clearTimeout(_prodSearchTimer)
  if (!keyword || !keyword.trim()) { productOptions.value = []; return }
  _prodSearchTimer = setTimeout(async () => {
    productSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      productOptions.value = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName }))
    } catch {} finally { productSearching.value = false }
  }, 300)
}

function handleTabChange(tab) {
  if (tab === 'requests') {
    loadRequests()
  } else if (tab === 'orders') {
    loadOrders()
  }
}

// request operations
function handleAddRequest() {
  requestForm.value = { workOrderId: '', taskId: '', quantity: 1, processRequirement: '', requiredDate: '' }
  requestDialogVisible.value = true
}

async function handleRequestSubmit() {
  await requestFormRef.value.validate()
  submitLoading.value = true
  try {
    await createOutsourceRequest(requestForm.value)
    ElMessage.success('外协申请创建成功')
    requestDialogVisible.value = false
    loadRequests()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetRequestForm() {
  requestFormRef.value?.resetFields()
}

function handleApproveRequest(row) {
  approveContext.value = {
    requestId: row.id,
    requestNo: row.requestNo,
    stepName: row.stepName || '',
    productName: row.productName || '',
    quantity: row.quantity || 0,
    virtualMaterialName: row.virtualMaterialName || '',
    receiptMaterialName: row.receiptMaterialName || ''
  }
  approveForm.value = {
    supplierId: undefined,
    unitPrice: 0,
    taxRate: 13,
    deliveryDate: row.requiredDate || '',
    approveRemark: ''
  }
  approveDialogVisible.value = true
}

async function handleApproveSubmit() {
  await approveFormRef.value.validate()
  submitLoading.value = true
  try {
    await approveOutsourceRequest(approveContext.value.requestId, approveForm.value)
    ElMessage.success('审批通过，已自动创建外协加工单')
    approveDialogVisible.value = false
    loadRequests()
    // 同时刷新加工单列表
    loadOrders()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetApproveForm() {
  approveFormRef.value?.resetFields()
}

function handleRejectRequest(row) {
  ElMessageBox.confirm(`确定驳回申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await rejectOutsourceRequest(row.id)
        ElMessage.success('已驳回')
        loadRequests()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

// order operations
function handleAddOrder() {
  editingOrderId.value = null
  orderForm.value = { supplierId: undefined, productId: undefined, productName: '', stepName: '', totalQty: 1, unitPrice: 0, taxRate: 13, deliveryDate: '', processRequirement: '', remark: '', requestIds: [], workOrderId: undefined, taskId: undefined, virtualMaterialId: undefined }
  orderTaskOptions.value = []
  orderDialogVisible.value = true
  loadWorkOrderOptions()
}

function handleEditOrder(row) {
  editingOrderId.value = row.id
  orderForm.value = {
    supplierId: row.supplierId, unitPrice: row.unitPrice || 0, taxRate: row.taxRate || 0,
    deliveryDate: row.deliveryDate || '', processRequirement: row.processRequirement || '', remark: row.remark || ''
  }
  orderDialogVisible.value = true
}

async function handleOrderSubmit() {
  await orderFormRef.value.validate()
  submitLoading.value = true
  try {
    if (editingOrderId.value) {
      await updateOutsourceOrder(editingOrderId.value, orderForm.value)
      ElMessage.success('保存成功')
    } else {
      await createOutsourceOrder(orderForm.value)
      ElMessage.success('创建成功')
    }
    orderDialogVisible.value = false
    loadOrders()
  } catch {} finally {
    submitLoading.value = false
  }
}

// 关联单据详情弹窗
const sourceDetailVisible = ref(false)
const sourceDetailOrderNo = ref('')
const sourceDetailInvType = ref('')

function handleViewSource(row) {
  if (row.isFullOutsource && row.salesOrderNo) {
    sourceDetailOrderNo.value = row.salesOrderNo
    sourceDetailInvType.value = 'sales_out' // maps to 'salesorder' in PurchaseOrderDetailDialog
  } else if (row.workOrderNo) {
    sourceDetailOrderNo.value = row.workOrderNo
    sourceDetailInvType.value = 'production_out' // maps to 'workorder'
  } else {
    return
  }
  sourceDetailVisible.value = true
}

function resetOrderForm() {
  orderFormRef.value?.resetFields()
  orderTaskOptions.value = []
  orderForm.value.workOrderId = undefined
  orderForm.value.taskId = undefined
  orderForm.value.productName = ''
  orderForm.value.stepName = ''
}

async function handleViewOrder(row) {
  try {
    const res = await getOutsourceOrderDetail(row.id)
    orderDetail.value = res.data || {}
    detailTab.value = 'basic'
    detailDialogVisible.value = true
  } catch {
    // handled by interceptor
  }
}

function handleConfirmOrder(row) {
  ElMessageBox.confirm(`确定确认加工单「${row.orderNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await confirmOutsourceOrder(row.id)
        ElMessage.success('确认成功')
        loadOrders()
      } catch {}
    })
    .catch(() => {})
}

async function handleSubmitOrder(row) {
  try {
    await ElMessageBox.confirm(`提交加工单「${row.orderNo}」进行审批？`, '提交审批', { type: 'info' })
    await submitOutsourceOrder(row.id)
    ElMessage.success('已提交审批')
    loadOrders()
  } catch {}
}

async function handleApproveOrder(row) {
  try {
    await ElMessageBox.confirm(`审批通过加工单「${row.orderNo}」？`, '审批通过', { type: 'success' })
    await approveOutsourceOrder(row.id, {})
    ElMessage.success('审批通过')
    loadOrders()
  } catch {}
}

async function handleRejectOrder(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因：', '驳回加工单', {
      confirmButtonText: '确认驳回', cancelButtonText: '取消',
      inputPattern: /\S+/, inputErrorMessage: '驳回原因不能为空', type: 'warning'
    })
    await rejectOutsourceOrder(row.id, { remark: reason })
    ElMessage.success('已驳回')
    loadOrders()
  } catch {}
}

async function handleDelivery(row) {
  deliveryRemark.value = ''
  try {
    const res = await getOutsourceOrderDetail(row.id)
    const d = res.data || {}
    deliveryContext.value = { orderId: d.id, orderNo: d.orderNo, productName: d.productName, totalQty: d.totalQty, deliveryTaskQty: d.deliveryTaskQty }
    const remaining = Math.max(0, (d.totalQty || 0) - (d.deliveryTaskQty || 0))
    deliveryItems.value = (d.deliveryMaterials || []).map(m => ({
      ...m,
      qty: remaining > 0 ? Number((m.perUnitQty * remaining).toFixed(4)) : Number(m.perUnitQty || 1),
      warehouseId: m.defaultWarehouseId || 999
    }))
  } catch { deliveryContext.value = row; deliveryItems.value = [] }
  deliveryDialogVisible.value = true
}

async function handleDeliverySubmit() {
  const validItems = deliveryItems.value.filter(i => i.qty > 0)
  if (!validItems.length) { ElMessage.warning('请至少填写一条发料数量'); return }
  const noWh = validItems.find(i => !i.warehouseId)
  if (noWh) { ElMessage.warning(`物料【${noWh.materialName}】未选择仓库`); return }
  submitLoading.value = true
  try {
    const totalDeliveryQty = validItems.reduce((s, i) => s + i.qty, 0)
    await createOutsourceDelivery(deliveryContext.value.orderId, {
      deliveryQty: totalDeliveryQty,
      remark: deliveryRemark.value || undefined,
      items: validItems.map(i => ({
        materialId: i.materialId, actualQty: i.qty, warehouseId: i.warehouseId
      }))
    })
    ElMessage.success('发料成功，已生成库存出库任务')
    deliveryDialogVisible.value = false
    loadOrders()
  } catch {} finally { submitLoading.value = false }
}

async function handleReceipt(row) {
  receiptRemark.value = ''
  try {
    const res = await getOutsourceOrderDetail(row.id)
    const d = res.data || {}
    receiptContext.value = { orderId: d.id, orderNo: d.orderNo, productName: d.productName, totalQty: d.totalQty, deliveredQty: d.deliveredQty }
    const rm = d.receiptMaterial || {}
    const remaining = (d.totalQty || 0) - (d.deliveredQty || 0)
    receiptItems.value = [{ ...rm, qty: Math.max(remaining, 1), warehouseId: rm.defaultWarehouseId || 999 }]
  } catch { receiptContext.value = row; receiptItems.value = [] }
  receiptDialogVisible.value = true
}

async function handleReceiptSubmit() {
  const item = receiptItems.value[0]
  if (!item || !item.qty || item.qty <= 0) { ElMessage.warning('请填写收货数量'); return }
  if (!item.warehouseId) { ElMessage.warning('请选择入库仓库'); return }
  submitLoading.value = true
  try {
    await createOutsourceReceipt(receiptContext.value.orderId, {
      receivedQty: item.qty,
      warehouseId: item.warehouseId,
      remark: receiptRemark.value || undefined
    })
    ElMessage.success('收货成功，已生成库存入库任务')
    receiptDialogVisible.value = false
    loadOrders()
  } catch {} finally { submitLoading.value = false }
}

// --- 生成收料单 ---
const generateReceiptVisible = ref(false)
const generateReceiptLoading = ref(false)
const generateReceiptRow = ref({})
const generateReceiptForm = reactive({ qty: 1, batchNo: '' })

function openGenerateReceipt(row) {
  generateReceiptRow.value = row
  generateReceiptForm.qty = Math.max(0, Number(row.totalQty || 0) - Number(row.receiptQty || 0))
  generateReceiptForm.batchNo = ''
  generateReceiptVisible.value = true
}

async function doGenerateReceipt() {
  if (!generateReceiptForm.qty || generateReceiptForm.qty <= 0) { ElMessage.warning('请填写收料数量'); return }
  generateReceiptLoading.value = true
  try {
    await generateOutsourceReceipts(generateReceiptRow.value.id, {
      items: [{ qty: generateReceiptForm.qty, batchNo: generateReceiptForm.batchNo || undefined }]
    })
    ElMessage.success('收料单生成成功，请前往"外协收料单"页面进行收料操作')
    generateReceiptVisible.value = false
    loadOrders()
  } finally { generateReceiptLoading.value = false }
}

// --- lifecycle ---
onMounted(() => {
  loadOptions()
  loadOrders()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.table-card {
  flex: 1;
}
.tab-search-form {
  margin-bottom: 16px;
}
.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: #c0c4cc;
}
</style>
