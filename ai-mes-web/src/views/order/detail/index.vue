<template>
  <div class="page-container">
    <!-- 返回按钮 -->
    <div class="page-header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>返回订单列表
      </el-button>
    </div>

    <!-- 顶部：订单号 + 状态 + 操作按钮 -->
    <el-card shadow="never" class="order-header-card" v-loading="loading">
      <div class="order-header">
        <div class="order-header__left">
          <h2 class="order-no">{{ orderData.orderNo || '-' }}</h2>
          <el-tag :type="statusTagType(orderData.status)" size="large" effect="dark">
            {{ statusLabel(orderData.status) }}
          </el-tag>
          <span class="invoice-total-label" style="margin-left: 16px; color: #606266; font-size: 14px">
            发票总额:
            <span style="color: #e6a23c; font-weight: 600; margin-left: 4px">¥{{ invoiceTotalAmount.toFixed(2) }}</span>
          </span>
        </div>
        <div class="order-header__right">
          <el-button v-if="orderData.status === 'draft'" type="primary" @click="handleSubmit">提交审批</el-button>
          <el-button v-if="orderData.status === 'pending_approval'" type="success" @click="handleApprove">审批通过</el-button>
          <el-button v-if="orderData.status === 'pending_approval'" type="danger" @click="handleReject">驳回</el-button>
          <!-- 转换按钮: 状态允许 + 至少一行有未分配数量(>0) 时显示 -->
          <!-- 已确认及之后的订单可加入主计划; 已加入则禁用 -->
          <el-button v-if="canAddToMasterPlan" type="info" plain @click="handleAddToMasterPlan">
            {{ orderData.masterPlanAdded ? '已加入主计划' : '转主计划' }}
          </el-button>
          <el-button v-if="canConvertOrder" type="primary" plain @click="handleToPlanning">转车间计划</el-button>
          <el-button v-if="canConvertOrder" type="warning" plain @click="handleToOutsource">转外协</el-button>
          <el-button v-if="['in_production','partial_shipped','outsourcing'].includes(orderData.status)"
            type="success" plain @click="handleComplete">订单完成</el-button>
          <!-- 变更入口: 走新流程的智能识别(已有 draft 跳回, 已有 pending 警告) -->
          <el-button v-if="!['draft','cancelled','voided','settled'].includes(orderData.status)"
            plain @click="openChangeDialog">申请变更</el-button>
          <el-button v-if="!['cancelled','voided','settled','shipped'].includes(orderData.status)"
            type="warning" plain @click="handleVoid">作废</el-button>
          <el-button v-if="orderData.status === 'draft'" type="danger" plain @click="handleDelete">删除</el-button>
        </div>
      </div>
    </el-card>

    <!-- Tab 页 -->
    <el-card shadow="never" style="margin-top: 12px">
      <el-tabs v-model="activeTab">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border style="margin-bottom: 20px">
            <el-descriptions-item label="订单号">{{ orderData.orderNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ orderData.customerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ orderData.contactName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务员">{{ orderData.salesmanName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">{{ orderData.shippingAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结算方式">{{ settlementLabel(orderData.settlementType) }}</el-descriptions-item>
            <el-descriptions-item label="交货日期">{{ orderData.deliveryDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="总金额">
              <span class="amount-text">{{ orderData.totalAmount != null ? `¥${Number(orderData.totalAmount).toFixed(2)}` : '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="回款金额">
              <span style="color: #67c23a; font-weight: 600">{{ orderData.paymentAmount != null ? `¥${Number(orderData.paymentAmount).toFixed(2)}` : '¥0.00' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ orderData.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ orderData.remark || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
            <h4 class="section-title" style="margin:0">订单明细</h4>
            <el-button type="primary" plain size="small"
              v-if="['confirmed','in_production','outsourcing','partial_shipped','completed'].includes(orderData.status)"
              @click="openBatchShipment">批量申请发货</el-button>
          </div>
          <el-table :data="orderData.items || []" border stripe size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="productName" label="产品名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="productCode" label="产品编号" min-width="120" show-overflow-tooltip />
            <el-table-column label="规格型号" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" align="center" />
            <el-table-column label="换算数量" width="120" align="right">
              <template #default="{ row }">
                <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
                  {{ secondaryDisplay(row.quantity, row.unitConfig) }}
                </span>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="unitPrice" label="含税单价" width="110" align="right">
              <template #default="{ row }">{{ row.unitPrice != null ? `¥${Number(row.unitPrice).toFixed(2)}` : '-' }}</template>
            </el-table-column>
            <el-table-column prop="taxRate" label="税率" width="70" align="center">
              <template #default="{ row }">{{ row.taxRate != null ? `${row.taxRate}%` : '13%' }}</template>
            </el-table-column>
            <el-table-column label="不含税单价" width="110" align="right">
              <template #default="{ row }">
                <span style="color:#909399; font-size:13px">{{ row.unitPrice != null ? `¥${(Number(row.unitPrice) / (1 + Number(row.taxRate || 13) / 100)).toFixed(2)}` : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="含税金额" width="110" align="right">
              <template #default="{ row }">
                {{ row.amount != null ? `¥${Number(row.amount).toFixed(2)}` : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="deliveryDate" label="交货日期" width="110" align="center" />
            <el-table-column label="可用库存" width="90" align="center">
              <template #default="{ row }">
                <span :style="{ color: (row.availableQty ?? 0) > 0 ? '#67c23a' : '#909399', fontWeight: 500 }">
                  {{ row.availableQty ?? '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="producedQty" label="已生产" width="70" align="center" />
            <el-table-column prop="warehouseInQty" label="生产入库" width="75" align="center" />
            <el-table-column prop="taskQty" label="发货任务" width="75" align="center" />
            <el-table-column prop="shippedQty" label="已发货" width="70" align="center" />
            <!-- 行级扩展字段 (sortable 客户端排序: 把每行的 extFields 解析后比较) -->
            <el-table-column v-for="fc in itemListFieldConfigs" :key="fc.fieldKey" :label="fc.fieldLabel" min-width="120" align="center"
              sortable :sort-method="(a, b) => sortItemExt(a, b, fc.fieldKey)">
              <template #default="{ row }">{{ getItemExtValue(row, fc.fieldKey) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="80" align="center" fixed="right">
              <template #default="{ row }">
                <el-tooltip v-if="!((row.availableQty ?? 0) > 0)" content="库存为 0, 无法发货" placement="top">
                  <span><el-button link type="primary" size="small" disabled>发货</el-button></span>
                </el-tooltip>
                <el-button v-else link type="primary" size="small"
                  :disabled="!['confirmed','in_production','outsourcing','partial_shipped','completed'].includes(orderData.status)"
                  @click="openStockOutRequest(row)">发货</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 2: 生产进度 (可展开行, 直接看到工序进度卡片) -->
        <el-tab-pane label="生产进度" name="progress">
          <el-table :data="progressList" border stripe v-loading="progressLoading" row-key="productId"
            @expand-change="onProgressExpandChange">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="progress-expand">
                  <!-- 完全没数据(BOM 还没拉完 + 没工单) 显示空态 -->
                  <div v-if="!row._wosLoading && !row._bomLoading && !(row._allWos && row._allWos.length)"
                    class="progress-expand-hint">
                    该产品暂未生成生产工单
                  </div>
                  <!-- 生产工单: 表格形式, 行可展开看工序明细 (有工单才显示) -->
                  <div v-if="row._wosLoading || (row._allWos && row._allWos.length)" class="progress-expand-section">
                    <div class="progress-expand-title">生产工单</div>
                    <div v-if="row._wosLoading" class="progress-expand-hint">
                      <el-icon class="is-loading"><Loading /></el-icon> 加载工单进度中...
                    </div>
                    <el-table v-else :data="row._allWos" row-key="id" border size="small" style="width: 100%">
                      <el-table-column type="expand">
                        <template #default="{ row: wo }">
                          <div class="wo-task-wrap">
                            <div v-if="!(wo._tasks && wo._tasks.length)" class="progress-expand-hint" style="padding: 8px 0">
                              该工单暂无工序数据
                            </div>
                            <div v-else class="wo-step-cards">
                              <div v-for="t in wo._tasks" :key="t.id" :class="['wo-sc', t.status, { overdue: isStepOverdue(wo, t) }]">
                                <div class="wo-sc-head">
                                  <span class="wo-sc-no">{{ t._seqNo }}</span>
                                  <span class="wo-sc-name">{{ t.stepName || '未命名' }}</span>
                                  <el-tag v-if="isStepOverdue(wo, t)" size="small" type="danger" effect="dark" class="wo-sc-overdue-tag">逾期</el-tag>
                                  <el-tag size="small" :type="stepStatusTagType(t.status)" effect="plain" class="wo-sc-status">{{ stepStatusText(t.status) }}</el-tag>
                                </div>
                                <div class="wo-sc-row">
                                  <span class="wo-sc-label">报工</span>
                                  <span class="wo-sc-val"><b>{{ fmtQty(stepReportedQty(t)) }}</b><i>/</i>{{ fmtQty(t.planQty) }}</span>
                                </div>
                                <template v-if="t.qcTemplateId">
                                  <div class="wo-sc-row">
                                    <span class="wo-sc-label">需质检</span>
                                    <span class="wo-sc-val">{{ fmtQty(stepReportedQty(t)) }}</span>
                                  </div>
                                  <div class="wo-sc-row">
                                    <span class="wo-sc-label">已质检</span>
                                    <span class="wo-sc-val">{{ fmtQty(Number(t.qualifiedQty || 0) + Number(t.defectQty || 0)) }}</span>
                                  </div>
                                  <div class="wo-sc-row wo-sc-qc">
                                    <span class="wo-sc-pass">合格 <b>{{ fmtQty(t.qualifiedQty) }}</b></span>
                                    <span :class="['wo-sc-fail', { 'has-defect': Number(t.defectQty) > 0 }]">不合格 <b>{{ fmtQty(t.defectQty) }}</b></span>
                                  </div>
                                </template>
                                <div v-else class="wo-sc-row wo-sc-no-qc">无质检</div>
                                <el-tag v-if="t.isOutsourced" size="small" type="warning" effect="plain" class="wo-sc-outsource">外协</el-tag>
                              </div>
                            </div>
                          </div>
                        </template>
                      </el-table-column>
                      <el-table-column label="工单号" min-width="170" show-overflow-tooltip>
                        <template #default="{ row: wo }">
                          <span>{{ wo.woNo }}</span>
                          <el-tag v-if="isWoOverdue(wo)" size="small" type="danger" effect="dark"
                            style="margin-left:6px; transform: scale(0.85); transform-origin: left center">逾期</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column label="物料编号" min-width="140" show-overflow-tooltip>
                        <template #default="{ row: wo }">{{ wo._productNo || '-' }}</template>
                      </el-table-column>
                      <el-table-column label="物料名称" min-width="120" show-overflow-tooltip>
                        <template #default="{ row: wo }">{{ wo._productName || wo.productName || '-' }}</template>
                      </el-table-column>
                      <el-table-column label="规格" min-width="110" show-overflow-tooltip>
                        <template #default="{ row: wo }">{{ $fmtSpec(wo.spec) }}</template>
                      </el-table-column>
                      <el-table-column label="计划数量" width="90" align="right">
                        <template #default="{ row: wo }">{{ fmtQty(wo.planQty) }}</template>
                      </el-table-column>
                      <el-table-column label="完成数量" width="90" align="right">
                        <template #default="{ row: wo }">
                          <span style="color:#67c23a;font-weight:500">{{ fmtQty(wo.completedQty) }}</span>
                        </template>
                      </el-table-column>
                      <el-table-column label="预计交货日期" min-width="125" align="center">
                        <template #default="{ row: wo }">
                          <span :style="{ color: isWoOverdue(wo) ? '#f56c6c' : 'inherit', fontWeight: isWoOverdue(wo) ? 500 : 'normal' }">
                            {{ (wo.deliveryDate || '').toString().substring(0, 10) || '-' }}
                          </span>
                        </template>
                      </el-table-column>
                      <el-table-column label="状态" width="95" align="center">
                        <template #default="{ row: wo }">
                          <el-tag size="small" :type="stepStatusTagType(wo.status)" effect="plain">{{ stepStatusText(wo.status) }}</el-tag>
                        </template>
                      </el-table-column>
                    </el-table>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="productName" label="产品名称" min-width="160" />
            <el-table-column prop="orderQty" label="订单数量" width="100" align="center" />
            <el-table-column prop="producedQty" label="已生产" width="100" align="center">
              <template #default="{ row }">
                <span :class="{ 'text-success': row.producedQty >= row.orderQty }">{{ row.producedQty || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="warehouseInQty" label="生产入库" width="100" align="center" />
            <el-table-column prop="shippedQty" label="已发货" width="100" align="center" />
            <el-table-column label="生产进度" min-width="200">
              <template #default="{ row }">
                <el-progress :percentage="calcProgress(row)" :status="calcProgress(row) >= 100 ? 'success' : ''"
                  :stroke-width="16" text-inside />
              </template>
            </el-table-column>
            <el-table-column prop="deliveryDate" label="交货日期" width="120" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="progressStatusType(row)" size="small">{{ progressStatusLabel(row) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!progressLoading && progressList.length === 0" description="暂无生产进度数据" />
        </el-tab-pane>

        <!-- Tab 3: 发货记录（来源：出入库历史） -->
        <el-tab-pane label="发货记录" name="shipments">
          <el-table :data="shipmentList" border stripe size="small" v-loading="shipmentLoading">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="historyNo" label="历史单号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="invType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ invTypeMap[row.invType] || row.invType || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="materialNo" label="物料编号" width="120" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
            <el-table-column label="规格" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" align="right" />
            <el-table-column prop="warehouseName" label="仓库" width="100" show-overflow-tooltip />
            <el-table-column prop="locationName" label="库位" width="90" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'normal' ? 'success' : 'danger'" size="small">
                  {{ row.status === 'normal' ? '正常' : row.status === 'revoked' ? '已撤销' : row.status || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="90" />
            <el-table-column prop="createTime" label="操作时间" width="160">
              <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').substring(0, 16) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!shipmentLoading && shipmentList.length === 0" description="暂无发货记录" />
        </el-tab-pane>

        <!-- Tab 4: 发票信息 -->
        <el-tab-pane label="发票信息" name="invoices">
          <div style="margin-bottom: 12px">
            <el-button type="primary" size="small" @click="openInvoiceForm">新增发票</el-button>
          </div>
          <el-table :data="invoiceList" border stripe size="small" v-loading="invoiceLoading">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="invoiceNo" label="发票号" min-width="140" />
            <el-table-column prop="invoiceType" label="发票类型" width="120">
              <template #default="{ row }">{{ invoiceTypeMap[row.invoiceType] || row.invoiceType || '-' }}</template>
            </el-table-column>
            <el-table-column prop="invoiceAmount" label="发票金额" width="120" align="right">
              <template #default="{ row }">¥{{ Number(row.invoiceAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="taxAmount" label="税额" width="100" align="right">
              <template #default="{ row }">¥{{ Number(row.taxAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="invoiceDate" label="开票日期" width="120" align="center" />
            <el-table-column label="附件" width="100" align="center">
              <template #default="{ row }">
                <el-button v-if="row.attachmentUrl" link type="primary" size="small" @click="downloadInvoiceAttachment(row)">下载</el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'issued' ? 'success' : 'warning'" size="small">
                  {{ { pending: '待开', issued: '已开', cancelled: '已作废' }[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openInvoiceDetail(row)">查看</el-button>
                <el-button v-if="row.status !== 'cancelled'" link type="info" size="small"
                  @click="openEditInvoice(row)">编辑</el-button>
                <el-button v-if="row.status !== 'cancelled'" link type="warning" size="small"
                  @click="markInvoiceCancelled(row)">作废</el-button>
                <el-button link type="danger" size="small" @click="deleteInvoice(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!invoiceLoading && invoiceList.length === 0" description="暂无发票信息" />
        </el-tab-pane>

        <!-- Tab 5: 审批进度 -->
        <el-tab-pane label="审批进度" name="approval">
          <div v-loading="approvalProgressLoading">
            <el-timeline v-if="approvalProgressData">
              <el-timeline-item
                v-for="node in approvalProgressData.nodes"
                :key="node.nodeId"
                :type="nodeTimelineType(node.nodeStatus)"
                :hollow="node.nodeStatus === 'waiting'"
              >
                <div class="timeline-node">
                  <div class="node-title">
                    <span class="node-name">{{ node.nodeName }}</span>
                    <el-tag :type="nodeTagType(node.nodeStatus)" size="small" style="margin-left: 8px">
                      {{ nodeStatusLabel(node.nodeStatus) }}
                    </el-tag>
                  </div>
                  <div v-if="node.records && node.records.length" class="node-records">
                    <div v-for="(rec, idx) in node.records" :key="idx" class="record-item">
                      <span class="approver-name">{{ rec.approverName }}</span>
                      <el-tag :type="rec.action === 'APPROVE' ? 'success' : 'danger'" size="small">
                        {{ rec.action === 'APPROVE' ? '通过' : '驳回' }}
                      </el-tag>
                      <span v-if="rec.remark" class="remark">：{{ rec.remark }}</span>
                      <span class="action-time">{{ (rec.actionTime || '').replace('T', ' ').substring(0, 16) }}</span>
                    </div>
                  </div>
                  <div v-else-if="node.nodeStatus === 'pending'" class="waiting-text">等待审批中...</div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无审批记录" />
          </div>
        </el-tab-pane>

        <!-- Tab 6: 财务信息 -->
        <el-tab-pane label="财务信息" name="finance">
          <h4 class="section-title">应收账款</h4>
          <el-table :data="receivableList" border stripe size="small" v-loading="financeLoading">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="receivableNo" label="应收单号" min-width="140" />
            <el-table-column prop="amount" label="应收金额" width="120" align="right">
              <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="receivedAmount" label="已收金额" width="120" align="right">
              <template #default="{ row }">¥{{ Number(row.receivedAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'received' ? 'success' : row.status === 'overdue' ? 'danger' : 'warning'" size="small">
                  {{ { pending: '待收款', partial_received: '部分收款', received: '已收', overdue: '已逾期' }[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="120" align="center" />
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'received'" type="primary" link size="small"
                  @click="openReceivePayment(row)">收款</el-button>
                <span v-else style="color: #c0c4cc">已结清</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!financeLoading && receivableList.length === 0" description="暂无应收账款" />

          <!-- 收款登记弹窗 -->
          <el-dialog v-model="receivePayVisible" title="收款登记" width="520px" destroy-on-close append-to-body>
            <el-form :model="receivePayForm" label-width="100px">
              <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
                <el-descriptions-item label="应收单号">{{ receivePayForm.receivableNo }}</el-descriptions-item>
                <el-descriptions-item label="应收金额">¥{{ Number(receivePayForm.amount || 0).toFixed(2) }}</el-descriptions-item>
                <el-descriptions-item label="已收">¥{{ Number(receivePayForm.received || 0).toFixed(2) }}</el-descriptions-item>
                <el-descriptions-item label="未收">
                  <span style="color:#f56c6c;font-weight:600">¥{{ Number(receivePayForm.unreceived || 0).toFixed(2) }}</span>
                </el-descriptions-item>
              </el-descriptions>
              <el-form-item label="收款金额" required>
                <el-input-number v-model="receivePayForm.payAmount" :min="0.01" :max="Number(receivePayForm.unreceived || 0)"
                  :precision="2" style="width: 200px" />
                <el-button type="primary" link size="small" style="margin-left: 8px"
                  @click="receivePayForm.payAmount = Number(receivePayForm.unreceived || 0)">全额</el-button>
              </el-form-item>
              <el-form-item label="收款日期" required>
                <el-date-picker v-model="receivePayForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
              </el-form-item>
              <el-form-item label="收款方式" required>
                <el-select v-model="receivePayForm.paymentMethod" placeholder="请选择" style="width: 200px">
                  <el-option label="银行转账" value="BANK_TRANSFER" />
                  <el-option label="现金" value="CASH" />
                  <el-option label="支票" value="CHECK" />
                  <el-option label="承兑汇票" value="ACCEPTANCE" />
                </el-select>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="receivePayForm.remark" type="textarea" :rows="2" />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="receivePayVisible = false">取消</el-button>
              <el-button type="primary" :loading="receivePaySubmitting" @click="submitReceivePayment">确认收款</el-button>
            </template>
          </el-dialog>

          <h4 class="section-title" style="margin-top: 24px">成本汇总</h4>
          <el-descriptions :column="3" border v-if="costSummary">
            <el-descriptions-item label="物料成本">¥{{ Number(costSummary.materialCost || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="人工成本">¥{{ Number(costSummary.laborCost || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="外协成本">¥{{ Number(costSummary.outsourceCost || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="总成本">
              <span class="amount-text">¥{{ Number(costSummary.totalCost || 0).toFixed(2) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单含税总金额">¥{{ Number(orderData.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="预计毛利">
              <span :class="grossProfit >= 0 ? 'text-success' : 'text-danger'">¥{{ grossProfit.toFixed(2) }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <!-- Tab 7: 变更记录 -->
        <el-tab-pane label="变更记录" name="changes">
          <div style="margin-bottom: 12px">
            <el-button type="primary" size="small" plain @click="openChangeDialog"
              v-if="!['draft','cancelled','voided','settled'].includes(orderData.status)">申请变更</el-button>
          </div>
          <el-timeline v-if="changeList.length > 0">
            <el-timeline-item v-for="change in changeList" :key="change.id" :timestamp="change.createTime"
              placement="top" :type="changeTagType(change.status)">
              <el-card shadow="never" class="change-card">
                <div class="change-header">
                  <span class="change-type">{{ changeTypeLabel(change.changeType) }}</span>
                  <el-tag :type="changeTagType(change.status)" size="small">{{ changeStatusLabel(change.status) }}</el-tag>
                  <div style="margin-left: auto; display: flex; gap: 8px">
                    <el-button v-if="change.status === 'draft'" size="small" type="primary" plain
                      @click="resumeDraft(change)">继续编辑</el-button>
                    <el-button size="small" link
                      @click="toggleDiff(change)">{{ expandedDiffs.has(change.id) ? '收起对比' : '查看对比' }}</el-button>
                    <template v-if="change.status === 'pending'">
                      <el-button size="small" type="success" plain @click="handleApproveChange(change)">通过</el-button>
                      <el-button size="small" type="danger" plain @click="handleRejectChange(change)">驳回</el-button>
                    </template>
                  </div>
                </div>
                <div class="change-meta">
                  <span>变更原因: {{ change.changeReason || '-' }}</span>
                  <span v-if="change.approvedAt">审批时间: {{ change.approvedAt }}</span>
                  <span v-if="change.approveRemark">审批意见: {{ change.approveRemark }}</span>
                </div>
                <!-- diff 视图: 默认折叠, 点"查看对比"展开 -->
                <div v-if="expandedDiffs.has(change.id)" style="margin-top: 12px;">
                  <OrderChangeDiff :change-id="change.id" />
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无变更记录" />
        </el-tab-pane>

        <!-- Tab 8: 附件 -->
        <el-tab-pane label="附件" name="attachments">
          <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 12px">
            <el-upload :auto-upload="false" :show-file-list="false" multiple @change="onAttachmentFileChange">
              <el-button type="primary" size="small" :loading="attachmentUploading">上传附件</el-button>
            </el-upload>
            <span style="font-size: 12px; color: #999">支持任意格式，无数量限制</span>
          </div>
          <el-table :data="attachmentList" v-loading="attachmentLoading" border stripe size="small">
            <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.fileType || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="100" align="right">
              <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="上传时间" min-width="160" />
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ row }">
                <el-button v-if="isImage(row.originalName || row.fileUrl)" type="success" link size="small"
                  @click="previewAttachment(row)">预览</el-button>
                <el-button type="primary" link size="small" @click="downloadAttachment(row)">下载</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteAttachment(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!attachmentLoading && attachmentList.length === 0" description="暂无附件" />

          <!-- 图片预览 -->
          <el-image-viewer v-if="previewVisible" :url-list="previewUrls" :initial-index="previewIndex"
            @close="previewVisible = false" />
        </el-tab-pane>

        <!-- Tab: 退货记录 -->
        <el-tab-pane label="退货记录" name="returns">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
            <span style="font-size:13px;color:#909399">本订单的退货申请记录</span>
            <el-button type="primary" plain size="small"
              v-if="['confirmed','in_production','partial_shipped','shipped','completed'].includes(orderData.status)"
              @click="openCreateReturn">申请退货</el-button>
          </div>
          <el-table :data="returnList" border stripe size="small" v-loading="returnLoading">
            <el-table-column prop="returnNo" label="退货单号" min-width="140" />
            <el-table-column prop="itemCount" label="产品数" width="70" align="center" />
            <el-table-column prop="totalReturnQty" label="退货总量" width="90" align="center" />
            <el-table-column prop="returnDate" label="退货日期" width="110" align="center" />
            <el-table-column prop="returnReason" label="退货原因" min-width="140" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="returnStatusType(row.status)" size="small">{{ returnStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="140">
              <template #default="{ row }">{{ (row.createTime || '').substring(0, 16) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!returnLoading && returnList.length === 0" description="暂无退货记录" />
        </el-tab-pane>

        <!-- Tab: 跟进记录 -->
        <el-tab-pane label="跟进记录" name="followups">
          <div style="margin-bottom: 12px">
            <el-button type="primary" size="small" @click="openOrderFollowupForm">添加跟进</el-button>
          </div>
          <el-table :data="orderFollowups" v-loading="orderFollowupLoading" border stripe size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="followupType" label="跟进方式" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="followupTypeTagType(row.followupType)">
                  {{ followupTypeMap[row.followupType] || row.followupType || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="跟进内容" min-width="300" show-overflow-tooltip />
            <el-table-column prop="nextFollowupDate" label="下次跟进" width="120" align="center">
              <template #default="{ row }">{{ row.nextFollowupDate || '-' }}</template>
            </el-table-column>
            <el-table-column prop="creatorName" label="跟进人" width="100" align="center" />
            <el-table-column prop="createTime" label="跟进时间" width="160" align="center">
              <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').substring(0, 16) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click="handleDeleteOrderFollowup(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!orderFollowupLoading && orderFollowups.length === 0" description="暂无跟进记录" />
        </el-tab-pane>

        <!-- Tab: 操作日志 -->
        <el-tab-pane label="操作日志" name="logs">
          <el-table :data="orderLogs" v-loading="orderLogLoading" border stripe size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="operation" label="操作类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="logOperationTagType(row.operation)">
                  {{ logOperationMap[row.operation] || row.operation }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operationDesc" label="操作描述" min-width="250" show-overflow-tooltip />
            <el-table-column prop="operatorName" label="操作人" width="100" align="center" />
            <el-table-column prop="createTime" label="操作时间" width="160" align="center">
              <template #default="{ row }">{{ (row.createTime || '').replace('T', ' ').substring(0, 16) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!orderLogLoading && orderLogs.length === 0" description="暂无操作日志" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 跟进记录对话框 -->
    <el-dialog v-model="orderFollowupDialogVisible" title="添加跟进记录" width="500px" :close-on-click-modal="false">
      <el-form :model="orderFollowupForm" label-width="90px">
        <el-form-item label="跟进方式">
          <el-select v-model="orderFollowupForm.followupType" placeholder="请选择" style="width: 100%">
            <el-option label="电话沟通" value="phone" />
            <el-option label="上门拜访" value="visit" />
            <el-option label="线上沟通" value="online" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="跟进内容" required>
          <el-input v-model="orderFollowupForm.content" type="textarea" :rows="4" placeholder="请输入跟进内容..." />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker v-model="orderFollowupForm.nextFollowupDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择下次跟进日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderFollowupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="orderFollowupSubmitting" @click="submitOrderFollowup">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审批弹窗 -->
    <el-dialog v-model="approveDialogVisible" title="审批意见" width="480px" destroy-on-close>
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.remark" type="textarea" :rows="4" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="approveLoading" @click="doApprove">确认通过</el-button>
      </template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回原因" width="480px" destroy-on-close>
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectForm.remark" type="textarea" :rows="4" placeholder="请输入驳回原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectLoading" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 申请变更弹窗 -->
    <el-dialog v-model="changeDialogVisible" title="申请订单变更" width="500px" destroy-on-close>
      <el-form :model="changeForm" :rules="changeFormRules" ref="changeFormRef" label-width="90px">
        <el-form-item label="变更类型" prop="changeType">
          <el-select v-model="changeForm.changeType" placeholder="请选择变更类型" style="width: 100%">
            <el-option label="数量变更" value="quantity" />
            <el-option label="交期变更" value="delivery_date" />
            <el-option label="价格变更" value="price" />
            <el-option label="产品变更" value="product" />
            <el-option label="其他变更" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更内容" prop="changeContent">
          <el-input v-model="changeForm.changeContent" type="textarea" :rows="3" placeholder="请描述变更内容" />
        </el-form-item>
        <el-form-item label="变更原因" prop="changeReason">
          <el-input v-model="changeForm.changeReason" type="textarea" :rows="3" placeholder="请说明变更原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="changeLoading" @click="doCreateChange">提交申请</el-button>
      </template>
    </el-dialog>


    <!-- 发票详情抽屉 -->
    <el-drawer v-model="invoiceDetailVisible" :title="`发票详情 ${invoiceDetailData.invoiceNo || ''}`" size="780px">
      <template v-if="invoiceDetailData.id">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="发票号码">{{ invoiceDetailData.invoiceNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发票类型">{{ invoiceTypeMap[invoiceDetailData.invoiceType] || invoiceDetailData.invoiceType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开票日期">{{ invoiceDetailData.invoiceDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ Number(invoiceDetailData.taxRate || 13).toFixed(2) }}%</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="invoiceDetailData.status === 'cancelled' ? 'danger' : 'success'" size="small">
              {{ { pending: '待开', issued: '已开', cancelled: '已作废' }[invoiceDetailData.status] || invoiceDetailData.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="附件">
            <el-button v-if="invoiceDetailData.attachmentUrl" type="primary" link size="small"
              @click="downloadInvoiceAttachment(invoiceDetailData)">下载</el-button>
            <span v-else style="color:#c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ invoiceDetailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div style="margin: 16px 0 8px; font-weight: 600">产品明细</div>
        <el-table :data="invoiceDetailItems" border stripe size="small">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
          <el-table-column label="规格" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ $fmtSpec(row.spec) || '-' }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" align="right" />
          <el-table-column prop="unitPrice" label="含税单价" width="100" align="right">
            <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="不含税金额" width="105" align="right">
            <template #default="{ row }">¥{{ Number(row.amountExTax || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="税额" width="90" align="right">
            <template #default="{ row }">¥{{ Number(row.taxAmount || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="含税金额" width="100" align="right">
            <template #default="{ row }">
              <span style="color:#e6a23c;font-weight:500">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!invoiceDetailItems.length" description="无明细" :image-size="60" />

        <div style="margin-top: 16px; text-align: right; font-size: 14px; color: #606266">
          不含税合计: <strong>¥{{ invoiceDetailSummary.exTax.toFixed(2) }}</strong>
          <span style="margin: 0 16px">·</span>
          税额合计: <strong style="color:#f56c6c">¥{{ invoiceDetailSummary.tax.toFixed(2) }}</strong>
          <span style="margin: 0 16px">·</span>
          含税合计: <strong style="color:#e6a23c; font-size:16px">¥{{ invoiceDetailSummary.total.toFixed(2) }}</strong>
        </div>
      </template>
    </el-drawer>

    <!-- 发票弹窗 -->
    <el-dialog v-model="invoiceFormVisible" :title="editingInvoiceId ? '编辑发票' : '新增发票'" width="960px" destroy-on-close>
      <el-form :model="invoiceForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="发票类型">
              <el-select v-model="invoiceForm.invoiceType" placeholder="请选择" style="width: 100%">
                <el-option label="增值税专用发票" value="special" />
                <el-option label="增值税普通发票" value="normal" />
                <el-option label="电子发票" value="electronic" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发票号码">
              <el-input v-model="invoiceForm.invoiceNo" placeholder="请输入发票号码" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开票日期">
              <el-date-picker v-model="invoiceForm.invoiceDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 产品明细表 -->
        <el-form-item label="产品明细">
          <div style="width: 100%">
            <el-table :data="invoiceForm.items" border size="small">
              <el-table-column label="产品" min-width="170">
                <template #default="{ row }">
                  <el-select v-model="row.productId" filterable placeholder="选择产品" style="width: 100%" @change="(val) => onInvoiceItemProductChange(row, val)">
                    <el-option v-for="it in orderItemOptions" :key="it.productId" :label="it.productName" :value="it.productId" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="规格" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ $fmtSpec(row.spec) || '-' }}</template>
              </el-table-column>
              <el-table-column label="数量" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="0" :precision="2" controls-position="right" size="small"
                    @change="recalcInvoiceItem(row)" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="单价(含税)" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" size="small"
                    @change="recalcInvoiceItem(row)" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="不含税金额" width="110" align="right">
                <template #default="{ row }">¥{{ Number(row.amountExTax || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="税额(13%)" width="100" align="right">
                <template #default="{ row }">¥{{ Number(row.taxAmount || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="含税金额" width="110" align="right">
                <template #default="{ row }">
                  <span style="color:#e6a23c;font-weight:500">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeInvoiceItem($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button type="primary" plain size="small" style="margin-top:8px" @click="addInvoiceItem">+ 添加明细</el-button>
          </div>
        </el-form-item>

        <!-- 合计汇总 -->
        <el-form-item label="合计">
          <div style="color:#606266; font-size: 14px">
            不含税: <strong style="color:#303133">¥{{ invoiceForm.amountExcludingTax.toFixed(2) }}</strong>
            <span style="margin:0 20px">·</span>
            税额: <strong style="color:#f56c6c">¥{{ invoiceForm.taxAmount.toFixed(2) }}</strong>
            <span style="margin:0 20px">·</span>
            总价(含税): <strong style="color:#e6a23c; font-size:16px">¥{{ invoiceForm.invoiceAmount.toFixed(2) }}</strong>
          </div>
        </el-form-item>

        <el-form-item label="发票附件">
          <el-upload :auto-upload="false" :show-file-list="false" @change="onInvoiceFileChange">
            <el-button size="small">选择文件</el-button>
          </el-upload>
          <div v-if="invoiceForm.attachmentFile" style="margin-top: 6px; display: flex; align-items: center; gap: 8px">
            <el-tag size="small" closable @close="invoiceForm.attachmentFile = null">{{ invoiceForm.attachmentFile.name }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="invoiceForm.remark" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invoiceFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="invoiceSubmitLoading" @click="doCreateInvoice">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发货出库任务弹窗 -->
    <el-dialog v-model="stockOutVisible" :title="stockOutItems.length > 1 ? '批量申请发货 - 生成出库任务' : '销售发货 - 生成出库任务'" width="1060px" destroy-on-close>
      <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="订单编号">{{ orderData.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ orderData.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ orderData.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span style="color: #e6a23c; font-weight: 600">¥{{ Number(orderData.totalAmount || 0).toFixed(2) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ orderData.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(orderData.status)" size="small">{{ statusLabel(orderData.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <div style="font-weight:600;color:#606266;margin-bottom:8px">发货明细（填写本次发货数量和出库仓库）</div>
      <el-table :data="stockOutItems" border size="small">
        <el-table-column prop="_productCode" label="产品编号" width="110" show-overflow-tooltip />
        <el-table-column prop="_productName" label="产品名称" min-width="110" show-overflow-tooltip />
        <el-table-column label="规格" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row._spec) }}</template>
        </el-table-column>
        <el-table-column prop="_unit" label="单位" width="55" align="center" />
        <el-table-column prop="_orderQty" label="订单数量" width="80" align="right" />
        <el-table-column label="已发货" width="72" align="right">
          <template #default="{ row }">
            <span :style="Number(row._shippedQty||0)>=Number(row._orderQty||0)?'color:#67c23a;font-weight:600':''">
              {{ row._shippedQty || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="发货任务" width="80" align="right">
          <template #default="{ row }">{{ row._taskQty || 0 }}</template>
        </el-table-column>
        <el-table-column label="剩余可发" width="72" align="right">
          <template #default="{ row }">
            <span style="color:#409eff;font-weight:600">{{ row._remainQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在库数" width="72" align="right">
          <template #default="{ row }">
            <span v-if="row._stockQty != null" :style="Number(row._stockQty)<Number(row.quantity||0)?'color:#f56c6c':''">
              {{ row._stockQty }}
            </span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="本次数量" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity" :min="0" :max="row._remainQty"
              :precision="4" size="small" controls-position="right" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="105">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" size="small" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="仓库" min-width="130">
          <template #default="{ row }">
            <el-select v-model="row.warehouseId" size="small" style="width:100%" placeholder="请选择仓库"
              @change="onStockOutWhChange(row)">
              <el-option v-for="w in stockOutWarehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="库位" min-width="115">
          <template #default="{ row }">
            <el-select v-if="row.warehouseId" v-model="row.locationId" size="small" style="width:100%" placeholder="可选" clearable>
              <el-option v-for="l in (row._locationOptions||[])" :key="l.id" :label="l.name" :value="l.id" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="stockOutVisible = false">取消</el-button>
        <el-button type="primary" :loading="stockOutLoading" @click="doStockOutRequest">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import {
  getSalesOrderDetail, submitSalesOrder, approveSalesOrder, rejectSalesOrder,
  cancelSalesOrder, deleteSalesOrder, voidSalesOrder, completeSalesOrder,
  getOrderChanges, createOrderChange, approveOrderChange, rejectOrderChange,
  getOrderInvoices, createOrderInvoice, updateOrderInvoice, deleteOrderInvoice,
  uploadInvoiceAttachment,
  getOrderAttachments, uploadOrderAttachment, deleteOrderAttachment,
  createShipmentTask,
  getReturnOrderPage, createReturnOrder,
  convertSalesOrderToPlan,
  convertSalesOrderToProductionPlan,
  batchConvertToOutsource,
  getOrderFollowups, createOrderFollowup, deleteOrderFollowup as deleteOrderFollowupApi,
  getOrderLogs,
} from '@/api/order'
import { getReceivablePage, getWoCost, addPayment } from '@/api/finance'
import { getApprovalProgress } from '@/api/approval'
import { getWorkOrderPage, getWorkOrderTasks } from '@/api/production'
import { getInventoryPage, getInvHistoryPage, getMaterialStocks } from '@/api/inventory'
import { getWarehouseSelect } from '@/api/system'
import { isImage } from '@/utils/attachment'
import OrderChangeDiff from '@/components/OrderChangeDiff.vue'
import { useExtFields } from '@/composables/useExtFields'
import { addOrderToMasterPlan, calculateMasterPlan } from '@/api/masterPlan'


const route = useRoute()
const router = useRouter()
const orderId = computed(() => route.query.id || route.params.id)

function secondaryDisplay(qty, unitConfig) {
  if (!qty || !unitConfig) return ''
  try {
    const cfg = typeof unitConfig === 'string' ? JSON.parse(unitConfig) : unitConfig
    // 格式A(物料导入): {"baseUnit":"吨","conversions":[{"unit":"件","ratio":10}]}
    const c = cfg.conversions?.[0]
    if (c) return `${(qty * c.ratio).toFixed(2)} ${c.unit}`
    // 格式B(手动创建): {"mode":"multi","units":[{"unit":"吨","rate":1,"isBase":true},{"unit":"件","rate":12}]}
    const units = cfg.units
    if (units && units.length > 1) {
      const sub = units.find(u => !u.isBase)
      if (sub) return `${(qty * sub.rate).toFixed(2)} ${sub.unit}`
    }
  } catch {}
  return ''
}

// ==================== 状态工具 ====================
const statusOptions = [
  { label: '草稿', value: 'draft' }, { label: '待审批', value: 'pending_approval' },
  { label: '已确认', value: 'confirmed' }, { label: '已转计划', value: 'mrp_planned' },
  { label: '外协中', value: 'outsourcing' }, { label: '生产中', value: 'in_production' },
  { label: '部分发货', value: 'partial_shipped' }, { label: '已发货', value: 'shipped' },
  { label: '已完成', value: 'completed' }, { label: '已结算', value: 'settled' },
  { label: '已取消', value: 'cancelled' }, { label: '已驳回', value: 'rejected' },
  { label: '已作废', value: 'voided' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))
function statusLabel(status) { return statusMap[status] || status || '-' }
function statusTagType(status) {
  const map = { draft: 'info', pending_approval: 'warning', confirmed: '', mrp_planned: 'primary',
    outsourcing: 'warning', in_production: '', partial_shipped: 'success', shipped: 'success',
    completed: 'success', settled: 'success', cancelled: 'danger', rejected: 'danger', voided: 'danger' }
  return map[status] || 'info'
}
function settlementLabel(type) {
  return type || '-'
}
const invoiceTypeMap = { special: '增值税专用发票', normal: '增值税普通发票', electronic: '电子发票' }
const invTypeMap = {
  purchase_in: '采购入库', production_in: '生产入库', semi_in: '半成品入库',
  outsource_in: '外协收货', outsource_material_in: '外协来料', return_in: '退货入库', other_in: '其他入库',
  production_out: '生产领料', sales_out: '销售发货', outsource_out: '外协发料',
  scrap_out: '报废出库', other_out: '其他出库', purchase_return: '采购退货'
}

// ==================== 订单数据 ====================
const loading = ref(false)
const orderData = ref({})
const activeTab = ref('basic')

// ==================== 订单明细行级扩展字段 ====================
const itemExtFields = useExtFields('sales_order_item')
const itemListFieldConfigs = itemExtFields.listFieldConfigs
const getItemExtValue = (row, key) => itemExtFields.getExtFieldValue(row, key)
function sortItemExt(a, b, key) {
  const va = getItemExtValue(a, key)
  const vb = getItemExtValue(b, key)
  // 数字优先, 否则按字符串
  const na = Number(va), nb = Number(vb)
  if (!isNaN(na) && !isNaN(nb)) return na - nb
  return String(va).localeCompare(String(vb), 'zh')
}

async function loadOrderDetail() {
  if (!orderId.value) return
  loading.value = true
  try {
    const res = await getSalesOrderDetail(orderId.value)
    orderData.value = res.data || {}
    // 批量加载产品库存
    const items = orderData.value.items || []
    const ids = items.map(i => i.productId).filter(Boolean)
    if (ids.length) {
      try {
        const stockRes = await getMaterialStocks(ids)
        const map = stockRes.data || {}
        items.forEach(i => {
          const s = map[i.productId]
          if (s) i.availableQty = s.availableQty ?? s.totalQty
        })
      } catch {}
    }
  } catch {} finally { loading.value = false }
}

// ==================== 生产进度 ====================
const progressLoading = ref(false)
const progressList = ref([])

async function loadProgress() {
  if (!orderId.value) return
  progressLoading.value = true
  // 重新进入生产进度 Tab 时清空主计划树缓存, 让 BOM 齐套数据反映最新库存/在途
  _masterPlanTreeCache = null
  try {
    const items = orderData.value.items || []
    // 查询该订单关联的工单
    let woMap = {} // productId -> workOrderId
    try {
      const woRes = await getWorkOrderPage({ orderId: orderId.value, pageNum: 1, pageSize: 100 })
      const woList = woRes.data?.records || woRes.data?.list || []
      for (const wo of woList) {
        if (wo.productId) woMap[wo.productId] = wo.id
      }
    } catch {}
    progressList.value = items.map(item => ({
      productId: item.productId, productName: item.productName, orderQty: item.quantity,
      productCode: item.productCode || item.materialNo,
      producedQty: item.producedQty || 0, warehouseInQty: item.warehouseInQty || 0,
      shippedQty: item.shippedQty || 0, deliveryDate: item.deliveryDate,
      workOrderId: item.workOrderId || woMap[item.productId] || null,
      _bomTree: null, _bomLoading: false,
      _allWos: null, _wosLoading: false
    }))
  } catch {} finally { progressLoading.value = false }
}

// 主计划树缓存: 一次拉取整个订单的多级 BOM 展开, 各行按 productId 切片用
let _masterPlanTreeCache = null
async function ensureMasterPlanTree() {
  if (_masterPlanTreeCache) return _masterPlanTreeCache
  if (!orderId.value) return []
  try {
    const res = await calculateMasterPlan(orderId.value)
    _masterPlanTreeCache = res.data || []
  } catch { _masterPlanTreeCache = [] }
  return _masterPlanTreeCache
}
// 给树节点打 rowKey 供 el-table tree-props 使用
function assignBomRowKey(node, prefix) {
  node._rowKey = prefix
  if (Array.isArray(node.children)) {
    node.children.forEach((c, i) => assignBomRowKey(c, `${prefix}-${i}`))
  }
  return node
}
function bomStatusTagType(n) {
  if (n.status === 'sufficient') return 'success'
  if (n.canConvertWo || n.canConvertPo) return 'warning'
  return 'danger'
}
function fmtQty(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (isNaN(n)) return v
  return n === Math.floor(n) ? n.toString() : n.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')
}

// 工序"报工累计数"统一口径 (避免双计):
//  - 有质检模板: 直接用 reportedQty (后端实体注释明确: 含待质检 + 已合格 + 已不合格的累计)
//  - 无质检模板: 没有 reportedQty 字段, 报工时直接累入 qualifiedQty/defectQty, 取其和
function stepReportedQty(t) {
  if (t.qcTemplateId) return Number(t.reportedQty || 0)
  return Number(t.qualifiedQty || 0) + Number(t.defectQty || 0)
}

// 工单逾期: 交货日期 < 今天 且 状态非完成/取消
function isWoOverdue(wo) {
  if (!wo || !wo.deliveryDate) return false
  if (wo.status === 'completed' || wo.status === 'cancelled') return false
  const today = new Date().toISOString().slice(0, 10)
  return String(wo.deliveryDate).substring(0, 10) < today
}
// 工序逾期判定 (优先级从高到低):
//  - 工序已完成/已取消 → 不算逾期
//  - 排产表 planEndTime: 时间戳 < 此刻 → 逾期 (排产细化场景)
//  - 工序自身 endTime: 时间戳 < 此刻 → 逾期 (从生产计划同步过来的计划完成时间)
//  - 都没有: 兜底跟随工单逾期判定 (deliveryDate)
function isStepOverdue(wo, t) {
  if (!t || t.status === 'completed' || t.status === 'cancelled') return false
  const planEnd = t.planEndTime || t.endTime
  if (planEnd) {
    // 后端返回 "2026-05-08 00:00:00" 形式, 替换空格为 T 兼容 Safari
    const dl = new Date(String(planEnd).replace(' ', 'T'))
    if (!isNaN(dl.getTime())) return dl.getTime() < Date.now()
  }
  return isWoOverdue(wo)
}

// 步骤/工单状态 → tag 颜色
function stepStatusTagType(s) {
  if (s === 'completed') return 'success'
  if (s === 'in_progress') return 'warning'
  if (s === 'paused') return 'danger'
  if (s === 'ready') return 'primary'
  return 'info'
}
// el-steps 当前激活索引 = 已完成工序数
function stepStripActive(steps) {
  if (!Array.isArray(steps) || steps.length === 0) return 0
  return steps.filter(s => s.status === 'completed').length
}
// 把后端的 step.status 映射成 el-step 的 status
function stepElStatus(task) {
  switch (task.status) {
    case 'completed': return 'success'
    case 'in_progress': return 'process'
    case 'paused': return 'error'
    case 'ready': return 'process'
    default: return 'wait'
  }
}

// 收集 BOM 树里所有自制节点的物料编号 (扁平, 用于查工单)
function collectManufactureProductNos(nodes, set) {
  if (!Array.isArray(nodes)) return
  for (const n of nodes) {
    if (n.sourceType === 'manufacture' && n.materialNo) set.add(n.materialNo)
    if (Array.isArray(n.children) && n.children.length) collectManufactureProductNos(n.children, set)
  }
}

// 拉取本订单顶级产品 + 所有 BOM 自制子件相关的所有未取消工单, 扁平返回
async function loadAllRelatedWos(row) {
  const productNos = new Set()
  // 顶级产品 — 从订单明细里找一条匹配 productId 的拿 productCode
  const topItem = (orderData.value.items || []).find(it => it.productId === row.productId)
  const topNo = topItem?.productCode || topItem?.materialNo
  if (topNo) productNos.add(topNo)
  // BOM 自制子件
  collectManufactureProductNos(row._bomTree, productNos)
  if (productNos.size === 0) return []

  // 各物料并行查工单 (同一工单可能被多个物料命中, 用 id 去重)
  // 关键: 必须按 orderId 过滤, 否则同物料编号的别的订单工单会被一锅拉进来
  const wosMap = new Map()
  await Promise.all([...productNos].map(async no => {
    try {
      const res = await getWorkOrderPage({ productNo: no, orderId: orderId.value, pageNum: 1, pageSize: 20 })
      const list = (res.data?.records || res.data?.list || [])
        .filter(w => w.status !== 'cancelled')
      for (const w of list) {
        if (!wosMap.has(w.id)) {
          w._productNo = w.productNo || no
          w._productName = w.productName || ''
          wosMap.set(w.id, w)
        }
      }
    } catch {}
  }))
  const wos = [...wosMap.values()]
  if (wos.length === 0) return []

  // 并行拉每个工单的工序任务
  await Promise.all(wos.map(async w => {
    try {
      const r = await getWorkOrderTasks(w.id)
      const sorted = [...(r.data || [])].sort((a, b) =>
        Number(a.stepNo || 0) - Number(b.stepNo || 0))
      w._tasks = sorted.map((t, i) => ({ ...t, _seqNo: i + 1 }))
    } catch { w._tasks = [] }
  }))
  // 排序: 进行中优先, 其次未开始/草稿等, 已完成放最后; 同状态按 woNo 倒序
  wos.sort((a, b) => {
    const ord = (s) => s === 'in_progress' ? 0 : s === 'completed' ? 2 : 1
    const da = ord(a.status), db = ord(b.status)
    if (da !== db) return da - db
    return (b.woNo || '').localeCompare(a.woNo || '')
  })
  return wos
}

// 展开行: 加载 BOM 物料齐套树 + 所有相关工单 (顶级产品 + BOM 自制子件)
async function onProgressExpandChange(row, expanded) {
  const isExpanded = Array.isArray(expanded)
    ? expanded.some(r => r === row || r.productId === row.productId)
    : !!expanded
  if (!isExpanded) return

  // 1) BOM 齐套树
  if (row._bomTree == null && !row._bomLoading) {
    row._bomLoading = true
    try {
      const tree = await ensureMasterPlanTree()
      const rootForRow = tree.find(n => n.materialId === row.productId)
      row._bomTree = rootForRow
        ? (assignBomRowKey({ ...rootForRow }, `p${row.productId}`).children || [])
        : []
    } catch {
      row._bomTree = []
    } finally {
      row._bomLoading = false
    }
  }

  // 2) 扁平工单列表 (依赖上一步的 _bomTree, 拿到 BOM 自制子件的 materialNo)
  if (row._allWos == null && !row._wosLoading) {
    row._wosLoading = true
    try {
      row._allWos = await loadAllRelatedWos(row)
    } catch {
      row._allWos = []
    } finally {
      row._wosLoading = false
    }
  }
}

// 工序 + 工单状态共用 (WO: draft/confirmed/released/in_progress/completed/cancelled/paused)
const stepStatusMap = {
  pending: '待开始', ready: '就绪', in_progress: '进行中', completed: '已完成', paused: '已暂停',
  draft: '草稿', confirmed: '已确认', released: '已下达', cancelled: '已取消'
}
function stepStatusText(status) { return stepStatusMap[status] || status || '-' }

function calcProgress(row) {
  if (!row.orderQty || row.orderQty === 0) return 0
  return Math.min(Math.round(((row.warehouseInQty || row.producedQty) / row.orderQty) * 100), 100)
}
function progressStatusType(row) {
  if ((row.warehouseInQty || row.producedQty) >= row.orderQty) return 'success'
  if ((row.warehouseInQty || row.producedQty) > 0) return ''
  return 'info'
}
function progressStatusLabel(row) {
  if (row.shippedQty >= row.orderQty) return '已发货'
  if ((row.warehouseInQty || row.producedQty) >= row.orderQty) return '已完成'
  if ((row.warehouseInQty || row.producedQty) > 0) return '生产中'
  return '待生产'
}

// ==================== 发货记录（出入库历史） ====================
const shipmentLoading = ref(false)
const shipmentList = ref([])

async function loadShipments() {
  if (!orderId.value || !orderData.value.orderNo) return
  shipmentLoading.value = true
  try {
    const res = await getInvHistoryPage({ relatedOrderNo: orderData.value.orderNo, pageSize: 100 })
    shipmentList.value = res.data?.records || res.data?.list || []
  } catch {} finally { shipmentLoading.value = false }
}

// ==================== 发票信息 ====================
const invoiceLoading = ref(false)
const invoiceList = ref([])
const invoiceFormVisible = ref(false)
const invoiceSubmitLoading = ref(false)

// 发票总额 = 所有未作废发票 invoiceAmount 求和
const invoiceTotalAmount = computed(() =>
  (invoiceList.value || [])
    .filter(i => i.status !== 'cancelled')
    .reduce((s, i) => s + Number(i.invoiceAmount || 0), 0)
)
const invoiceForm = reactive({
  invoiceType: 'special', invoiceNo: '',
  invoiceAmount: 0, amountExcludingTax: 0, taxAmount: 0,
  invoiceDate: '', attachmentFile: null, attachmentUrl: '', remark: '',
  items: []  // [{productId,productName,spec,quantity,unitPrice,amountExTax,taxAmount,totalAmount}]
})
// 编辑模式: 非空表示在编辑,保存时走 update
const editingInvoiceId = ref(null)

// 详情抽屉
const invoiceDetailVisible = ref(false)
const invoiceDetailData = ref({})
const invoiceDetailItems = computed(() => {
  const raw = invoiceDetailData.value.itemsJson
  if (!raw) return []
  try { return JSON.parse(raw) || [] } catch { return [] }
})
const invoiceDetailSummary = computed(() => {
  let exTax = 0, tax = 0, total = 0
  invoiceDetailItems.value.forEach(r => {
    exTax += Number(r.amountExTax || 0)
    tax   += Number(r.taxAmount || 0)
    total += Number(r.totalAmount || 0)
  })
  return { exTax, tax, total }
})
function openInvoiceDetail(row) {
  invoiceDetailData.value = row
  invoiceDetailVisible.value = true
}

function openEditInvoice(row) {
  editingInvoiceId.value = row.id
  invoiceForm.invoiceType = row.invoiceType || 'special'
  invoiceForm.invoiceNo = row.invoiceNo || ''
  invoiceForm.invoiceDate = row.invoiceDate || ''
  invoiceForm.remark = row.remark || ''
  invoiceForm.attachmentFile = null
  invoiceForm.attachmentUrl = row.attachmentUrl || ''
  // 还原明细
  try {
    invoiceForm.items = row.itemsJson ? JSON.parse(row.itemsJson) : []
  } catch { invoiceForm.items = [] }
  if (invoiceForm.items.length === 0) {
    // 旧单行数据兼容: 从 shippedQty/unitPrice 还原一行(unitPrice 按含税单价解释)
    if (row.shippedQty || row.unitPrice) {
      const qty = Number(row.shippedQty || 0)
      const price = Number(row.unitPrice || 0)
      const total = Number((qty * price).toFixed(2))
      const exTax = Number((total / 1.13).toFixed(2))
      const tax = Number((total - exTax).toFixed(2))
      invoiceForm.items = [{
        productId: null, productName: '', spec: '',
        quantity: qty, unitPrice: price,
        amountExTax: exTax, taxAmount: tax,
        totalAmount: total
      }]
    } else {
      addInvoiceItem()
    }
  }
  recalcInvoiceTotal()
  invoiceFormVisible.value = true
}

// 订单产品下拉(用于发票明细选产品)
const orderItemOptions = computed(() => {
  const items = orderData.value.items || []
  // 按产品聚合(去重),供发票明细选择
  const map = new Map()
  items.forEach(it => {
    if (!it.productId || map.has(it.productId)) return
    map.set(it.productId, {
      productId: it.productId,
      productName: it.productName,
      spec: it.spec,
      unitPrice: it.unitPrice,
      quantity: it.quantity,
      shippedQty: it.shippedQty
    })
  })
  return [...map.values()]
})

// 单行计算: unitPrice 是含税单价 → 含税总额=数量×含税单价, 不含税=含税/1.13, 税额=含税-不含税
function recalcInvoiceItem(row) {
  const qty = Number(row.quantity || 0)
  const price = Number(row.unitPrice || 0)
  const total = Number((qty * price).toFixed(2))
  const exTax = Number((total / 1.13).toFixed(2))
  const tax = Number((total - exTax).toFixed(2))
  row.amountExTax = exTax
  row.taxAmount = tax
  row.totalAmount = total
  recalcInvoiceTotal()
}

// 整票合计
function recalcInvoiceTotal() {
  let exTax = 0, tax = 0, total = 0
  invoiceForm.items.forEach(r => {
    exTax += Number(r.amountExTax || 0)
    tax   += Number(r.taxAmount || 0)
    total += Number(r.totalAmount || 0)
  })
  invoiceForm.amountExcludingTax = Number(exTax.toFixed(2))
  invoiceForm.taxAmount = Number(tax.toFixed(2))
  invoiceForm.invoiceAmount = Number(total.toFixed(2))
}

// 产品下拉切换时,自动回填规格、单价、数量(取已发货数量优先)
function onInvoiceItemProductChange(row, productId) {
  const opt = orderItemOptions.value.find(o => o.productId === productId)
  if (opt) {
    row.productName = opt.productName
    row.spec = opt.spec
    if (!row.quantity) row.quantity = Number(opt.shippedQty || opt.quantity || 0)
    if (!row.unitPrice) row.unitPrice = Number(opt.unitPrice || 0)
    recalcInvoiceItem(row)
  }
}

function addInvoiceItem() {
  invoiceForm.items.push({
    productId: null, productName: '', spec: '',
    quantity: 0, unitPrice: 0,
    amountExTax: 0, taxAmount: 0, totalAmount: 0
  })
}

function removeInvoiceItem(idx) {
  invoiceForm.items.splice(idx, 1)
  recalcInvoiceTotal()
}

async function loadInvoices() {
  if (!orderId.value) return
  invoiceLoading.value = true
  try {
    const res = await getOrderInvoices(orderId.value)
    invoiceList.value = res.data || []
  } catch {} finally { invoiceLoading.value = false }
}

function openInvoiceForm() {
  editingInvoiceId.value = null
  invoiceForm.invoiceType = 'special'
  invoiceForm.invoiceNo = ''
  invoiceForm.invoiceAmount = 0
  invoiceForm.amountExcludingTax = 0
  invoiceForm.taxAmount = 0
  invoiceForm.invoiceDate = new Date().toISOString().split('T')[0]
  invoiceForm.attachmentFile = null
  invoiceForm.attachmentUrl = ''
  invoiceForm.remark = ''
  // 默认预填明细: 把订单所有产品行带入。订单 unitPrice 现在是含税单价,直接复用
  invoiceForm.items = orderItemOptions.value.map(opt => {
    const qty = Number(opt.shippedQty || opt.quantity || 0)
    const price = Number(opt.unitPrice || 0)         // 含税单价
    const total = Number((qty * price).toFixed(2))    // 含税总额
    const exTax = Number((total / 1.13).toFixed(2))   // 不含税
    const tax = Number((total - exTax).toFixed(2))
    return {
      productId: opt.productId,
      productName: opt.productName,
      spec: opt.spec,
      quantity: qty,
      unitPrice: price,
      amountExTax: exTax,
      taxAmount: tax,
      totalAmount: total
    }
  })
  if (invoiceForm.items.length === 0) addInvoiceItem()
  recalcInvoiceTotal()
  invoiceFormVisible.value = true
}

function onInvoiceFileChange(file) {
  invoiceForm.attachmentFile = file.raw
}

async function doCreateInvoice() {
  // 校验至少有一条有效明细
  const validItems = invoiceForm.items.filter(i => Number(i.quantity) > 0 && Number(i.unitPrice) > 0)
  if (validItems.length === 0) {
    return ElMessage.warning('请至少添加一条有效的产品明细(填数量和单价)')
  }
  invoiceSubmitLoading.value = true
  try {
    let attachmentUrl = invoiceForm.attachmentUrl || ''
    if (invoiceForm.attachmentFile) {
      const uploadRes = await uploadInvoiceAttachment(invoiceForm.attachmentFile)
      attachmentUrl = uploadRes.data?.fileUrl || ''
    }
    // 兼容单行字段: 取第一行的数量/单价(方便旧报表)
    const first = validItems[0]
    const payload = {
      invoiceType: invoiceForm.invoiceType, invoiceNo: invoiceForm.invoiceNo,
      shippedQty: first.quantity, unitPrice: first.unitPrice,
      invoiceAmount: invoiceForm.invoiceAmount, taxAmount: invoiceForm.taxAmount,
      taxRate: 13,
      itemsJson: JSON.stringify(validItems),
      invoiceDate: invoiceForm.invoiceDate, attachmentUrl, remark: invoiceForm.remark
    }
    if (editingInvoiceId.value) {
      await updateOrderInvoice(editingInvoiceId.value, payload)
      ElMessage.success('发票已更新')
    } else {
      payload.status = 'issued'  // 新增即为已开票
      await createOrderInvoice(orderId.value, payload)
      ElMessage.success('发票保存成功')
    }
    invoiceFormVisible.value = false
    loadInvoices()
  } finally { invoiceSubmitLoading.value = false }
}

function downloadInvoiceAttachment(row) {
  if (!row.attachmentUrl) return
  const a = document.createElement('a')
  a.href = row.attachmentUrl
  // 从 URL 中提取文件名作为下载名
  const fileName = row.attachmentUrl.split('/').pop() || '发票附件'
  a.download = fileName
  a.target = '_blank'
  a.click()
}

async function deleteInvoice(row) {
  await ElMessageBox.confirm('确定删除此发票记录？', '确认', { type: 'warning' })
  try { await deleteOrderInvoice(row.id); ElMessage.success('已删除'); loadInvoices() } catch {}
}

async function markInvoiceIssued(row) {
  await ElMessageBox.confirm(`确定将发票「${row.invoiceNo || ''}」标记为已开？`, '开票确认', { type: 'success' })
  try {
    await updateOrderInvoice(row.id, { ...row, status: 'issued' })
    ElMessage.success('已标记为已开')
    loadInvoices()
  } catch {}
}

async function markInvoiceCancelled(row) {
  await ElMessageBox.confirm(`确定作废发票「${row.invoiceNo || ''}」？作废后不可撤回`, '作废确认', { type: 'warning' })
  try {
    await updateOrderInvoice(row.id, { ...row, status: 'cancelled' })
    ElMessage.success('已作废')
    loadInvoices()
  } catch {}
}

// ==================== 发货（生成出库任务） ====================
const stockOutVisible = ref(false)
const stockOutLoading = ref(false)
const stockOutWarehouseOptions = ref([])
const stockOutItems = ref([])

async function openStockOutRequest(item) {
  const remain = Math.max((item.quantity || 0) - (item.shippedQty || 0), 0)
  // 加载仓库列表
  if (stockOutWarehouseOptions.value.length === 0) {
    try {
      const res = await getWarehouseSelect()
      stockOutWarehouseOptions.value = res.data || []
    } catch {}
  }
  // 构建出库明细行
  const stockItem = {
    _productId: item.productId,
    _productCode: item.productCode || item.materialNo || '-',
    _productName: item.productName,
    _spec: item.spec || '-',
    _unit: item.unit || '-',
    _orderQty: item.quantity || 0,
    _shippedQty: item.shippedQty || 0,
    _taskQty: item.taskQty || 0,
    _remainQty: remain,
    _stockQty: null,                      // 当前选中仓库的可用
    _totalAvailable: 0,                   // 跨仓库合计可用 (用于低库存校验)
    _minStock: 0,                         // 最小库存阈值
    _locationOptions: [],
    _orderItemId: item.id,
    quantity: remain || 1,
    batchNo: '',
    warehouseId: undefined,
    locationId: undefined
  }
  // 查询库存台账获取默认仓库和在库数
  // 用 materialNo 精确过滤(backend 支持), 旧 keyword 参数 backend 不识别会拿到任意物料的 records[0]
  try {
    const matNo = item.productCode || item.materialNo
    if (matNo) {
      const invRes = await getInventoryPage({ materialNo: matNo, pageSize: 50 })
      const records = invRes.data?.records || invRes.data?.list || []
      // 过滤出本物料的有库存记录, 取最大可用的那条作为默认仓库
      const matRecords = records.filter(r => r.materialId === item.productId)
      // 跨仓库合计可用(用于低库存提醒) + 最小库存(同物料各行 minStock 相同, 取首行即可)
      stockItem._totalAvailable = matRecords.reduce((s, r) => s + Number(r.availableQty || 0), 0)
      stockItem._minStock = matRecords.length ? Number(matRecords[0].minStock || 0) : 0
      const best = matRecords.reduce((acc, cur) => {
        const a = Number(cur.availableQty || 0)
        const b = acc ? Number(acc.availableQty || 0) : -1
        return a > b ? cur : acc
      }, null)
      if (best) {
        stockItem._stockQty = Number(best.availableQty || 0)
        stockItem.warehouseId = best.warehouseId || undefined
        if (stockItem.warehouseId) {
          try {
            const { getWarehouseLocationList } = await import('@/api/system')
            const locRes = await getWarehouseLocationList(stockItem.warehouseId)
            stockItem._locationOptions = locRes.data || []
          } catch {}
        }
      } else {
        stockItem._stockQty = 0
      }
    }
  } catch {}
  stockOutItems.value = [stockItem]
  stockOutVisible.value = true
}

async function onStockOutWhChange(row) {
  row.locationId = undefined
  row._locationOptions = []
  if (!row.warehouseId) { row._stockQty = null; return }
  // 加载库位
  try {
    const { getWarehouseLocationList } = await import('@/api/system')
    const res = await getWarehouseLocationList(row.warehouseId)
    row._locationOptions = res.data || []
  } catch { row._locationOptions = [] }
  // 查询该仓库的在库数(改用 materialNo, 不再用 backend 不识别的 keyword)
  try {
    const matNo = row._productCode !== '-' ? row._productCode : null
    if (!matNo) { row._stockQty = 0; return }
    // 同时拉跨仓库合计 + 最小库存 (批量发货流程进来时这些字段还是空的, 借机补齐, 用于低库存提醒)
    if (!row._totalAvailable || !row._minStock) {
      try {
        const allRes = await getInventoryPage({ materialNo: matNo, pageSize: 50 })
        const allRecords = allRes.data?.records || allRes.data?.list || []
        const matRecords = allRecords.filter(r => r.materialId === row._productId)
        row._totalAvailable = matRecords.reduce((s, r) => s + Number(r.availableQty || 0), 0)
        row._minStock = matRecords.length ? Number(matRecords[0].minStock || 0) : 0
      } catch {}
    }
    // 当前仓库可用
    const invRes = await getInventoryPage({ materialNo: matNo, warehouseIds: row.warehouseId, pageSize: 20 })
    const records = invRes.data?.records || invRes.data?.list || []
    const invRecord = records.find(r => r.materialId === row._productId)
    row._stockQty = invRecord ? Number(invRecord.availableQty || 0) : 0
  } catch { row._stockQty = 0 }
}

async function openBatchShipment() {
  if (stockOutWarehouseOptions.value.length === 0) {
    try {
      const res = await getWarehouseSelect()
      stockOutWarehouseOptions.value = res.data || []
    } catch {}
  }
  const items = orderData.value.items || []
  const rows = []
  for (const item of items) {
    const remain = Math.max((item.quantity || 0) - (item.shippedQty || 0), 0)
    if (remain <= 0) continue
    const stockItem = {
      _productId: item.productId,
      _productCode: item.productCode || item.materialNo || '-',
      _productName: item.productName,
      _spec: item.spec || '-',
      _unit: item.unit || '-',
      _orderQty: item.quantity || 0,
      _shippedQty: item.shippedQty || 0,
      _taskQty: item.taskQty || 0,
      _remainQty: remain,
      _stockQty: null,
      _locationOptions: [],
      _orderItemId: item.id,
      quantity: remain,
      batchNo: '',
      warehouseId: undefined,
      locationId: undefined
    }
    rows.push(stockItem)
  }
  if (rows.length === 0) { ElMessage.info('所有产品均已发货完毕'); return }
  stockOutItems.value = rows
  stockOutVisible.value = true
}

async function doStockOutRequest() {
  const items = stockOutItems.value
  for (const item of items) {
    if (!item.warehouseId) { ElMessage.warning(`产品"${item._productName}"请选择出库仓库`); return }
    if (!(item.quantity > 0)) { ElMessage.warning(`产品"${item._productName}"请输入发货数量`); return }
    if (item.quantity > item._remainQty) {
      ElMessage.warning(`产品"${item._productName}"发货数量不能超过剩余可发数量 ${item._remainQty}`)
      return
    }
    if (item._stockQty != null && Number(item.quantity) > Number(item._stockQty)) {
      ElMessage.warning(`产品"${item._productName}"在所选仓库可用库存仅 ${item._stockQty}, 不能申请 ${item.quantity}`)
      return
    }
  }
  // 低库存提醒: 发货后跨仓库剩余可用 < 最小库存阈值, 弹二次确认
  const lowStockHints = []
  for (const item of items) {
    const minS = Number(item._minStock || 0)
    if (minS <= 0) continue
    const total = Number(item._totalAvailable || 0)
    const after = total - Number(item.quantity || 0)
    if (after < minS) {
      lowStockHints.push(`「${item._productName}」当前可用 ${total}, 发货 ${item.quantity} 后将剩 ${after}, 低于最小库存 ${minS}`)
    }
  }
  if (lowStockHints.length) {
    try {
      await ElMessageBox.confirm(
        lowStockHints.join('\n') + '\n\n是否仍然确认发货?',
        '低库存提醒',
        { type: 'warning', confirmButtonText: '仍然发货', cancelButtonText: '取消', dangerouslyUseHTMLString: false }
      )
    } catch {
      return  // 用户点取消
    }
  }
  stockOutLoading.value = true
  try {
    for (const item of items) {
      if (!item.warehouseId || !(item.quantity > 0)) continue
      await createShipmentTask(orderId.value, {
        orderItemId: item._orderItemId,
        productId: item._productId,
        warehouseId: item.warehouseId,
        locationId: item.locationId || undefined,
        quantity: item.quantity,
        batchNo: item.batchNo || undefined
      })
    }
    ElMessage.success(`已为 ${items.length} 个产品创建出库任务，请前往"库存任务"页面处理`)
    stockOutVisible.value = false
    loadOrderDetail()
  } catch {
    // handled by interceptor
  } finally { stockOutLoading.value = false }
}

// ==================== 财务信息 ====================
const financeLoading = ref(false)
const receivableList = ref([])
const costSummary = ref(null)
const grossProfit = computed(() => {
  return Number(orderData.value.totalAmount || 0) - Number(costSummary.value?.totalCost || 0)
})

// ==================== 收款登记 ====================
const receivePayVisible = ref(false)
const receivePaySubmitting = ref(false)
const receivePayForm = reactive({
  receivableId: null, receivableNo: '',
  amount: 0, received: 0, unreceived: 0,
  payAmount: 0, paymentDate: new Date().toISOString().slice(0, 10),
  paymentMethod: 'BANK_TRANSFER', remark: ''
})
function openReceivePayment(row) {
  const unreceived = Number(row.unreceivedAmount != null ? row.unreceivedAmount
    : (Number(row.amount || 0) - Number(row.receivedAmount || 0)))
  Object.assign(receivePayForm, {
    receivableId: row.id,
    receivableNo: row.receivableNo,
    amount: row.amount,
    received: row.receivedAmount || 0,
    unreceived,
    payAmount: unreceived,
    paymentDate: new Date().toISOString().slice(0, 10),
    paymentMethod: 'BANK_TRANSFER',
    remark: ''
  })
  receivePayVisible.value = true
}
async function submitReceivePayment() {
  if (!receivePayForm.payAmount || receivePayForm.payAmount <= 0) {
    ElMessage.warning('请输入收款金额'); return
  }
  if (!receivePayForm.paymentDate) { ElMessage.warning('请选择收款日期'); return }
  if (!receivePayForm.paymentMethod) { ElMessage.warning('请选择收款方式'); return }
  receivePaySubmitting.value = true
  try {
    await addPayment({
      relatedType: 'receivable',
      relatedId: receivePayForm.receivableId,
      amount: receivePayForm.payAmount,
      paymentDate: receivePayForm.paymentDate,
      paymentMethod: receivePayForm.paymentMethod,
      remark: receivePayForm.remark
    })
    ElMessage.success('收款成功')
    receivePayVisible.value = false
    loadFinance()
  } finally { receivePaySubmitting.value = false }
}

async function loadFinance() {
  if (!orderId.value) return
  financeLoading.value = true
  try {
    const recRes = await getReceivablePage({ orderId: orderId.value, pageNum: 1, pageSize: 100 })
    receivableList.value = recRes.data?.records || recRes.data?.list || []
    const items = orderData.value.items || []
    const workOrderIds = items.map(i => i.workOrderId).filter(Boolean)
    if (workOrderIds.length > 0) {
      const costResults = await Promise.all(workOrderIds.map(id => getWoCost(id).catch(() => null)))
      const costs = costResults.filter(Boolean).map(r => r.data).filter(Boolean)
      costSummary.value = {
        materialCost: costs.reduce((s, c) => s + Number(c.materialCost || 0), 0),
        laborCost: costs.reduce((s, c) => s + Number(c.laborCost || 0), 0),
        outsourceCost: costs.reduce((s, c) => s + Number(c.outsourceCost || 0), 0),
        totalCost: costs.reduce((s, c) => s + Number(c.totalCost || 0), 0)
      }
    } else {
      costSummary.value = { materialCost: 0, laborCost: 0, outsourceCost: 0, totalCost: 0 }
    }
  } catch {} finally { financeLoading.value = false }
}

// ==================== 变更记录 ====================
const changeList = ref([])
const expandedDiffs = ref(new Set())
function toggleDiff(change) {
  const s = new Set(expandedDiffs.value)
  if (s.has(change.id)) s.delete(change.id); else s.add(change.id)
  expandedDiffs.value = s
}
function resumeDraft(change) {
  router.push({ path: '/order/edit', query: { id: orderId.value, changeId: change.id } })
}
const CHANGE_TYPE_LABEL = {
  qty_increase: '数量增加', qty_decrease: '数量减少',
  date_change: '交期变更', cancel: '订单取消',
  product_replace: '产品替换', add_product: '新增产品行', add_item: '新增产品行',
  quantity: '数量变更', delivery_date: '交期变更', price: '价格变更',
  product: '产品变更', other: '其他变更',
  delay_delivery: '工单交期延期联动'
}
function changeTypeLabel(t) { return CHANGE_TYPE_LABEL[t] || t || '订单变更' }
async function loadChanges() {
  if (!orderId.value) return
  try {
    const res = await getOrderChanges({ orderId: orderId.value })
    changeList.value = res.data?.records || res.data?.list || res.data || []
  } catch {}
}
function changeTagType(status) {
  return { draft: 'info', pending: 'warning', approved: 'success', rejected: 'danger', failed: 'danger' }[status] || 'info'
}
function changeStatusLabel(status) {
  return { draft: '草稿', pending: '待审批', approved: '已通过', rejected: '已驳回', failed: '应用失败' }[status] || status || '-'
}

// 申请变更
const changeDialogVisible = ref(false)
const changeLoading = ref(false)
const changeFormRef = ref(null)
const changeForm = ref({ changeType: '', changeContent: '', changeReason: '' })
const changeFormRules = {
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  changeContent: [{ required: true, message: '请填写变更内容', trigger: 'blur' }],
  changeReason: [{ required: true, message: '请填写变更原因', trigger: 'blur' }]
}
async function openChangeDialog() {
  // 智能识别: 已有 draft 直接跳回继续编辑, 已有 pending 给提示
  try {
    const res = await getOrderChanges({ orderId: orderId.value })
    const list = res.data?.records || res.data?.list || res.data || []
    const draft = list.find(c => c.status === 'draft')
    const pending = list.find(c => c.status === 'pending')
    if (draft) {
      ElMessage.info('该订单已有变更草稿, 跳回继续编辑')
      router.push({ path: '/order/edit', query: { id: orderId.value, changeId: draft.id } })
      return
    }
    if (pending) {
      ElMessage.warning('该订单已有待审批的变更, 请等审批人处理')
      activeTab.value = 'changes'
      return
    }
  } catch {}
  changeForm.value = { changeType: '', changeContent: '', changeReason: '' }
  changeDialogVisible.value = true
}
async function doCreateChange() {
  await changeFormRef.value.validate()
  changeLoading.value = true
  try {
    // 新流程: 创建草稿 → 跳到编辑页继续(URL 带 changeId 进变更模式)
    const res = await createOrderChange({ orderId: orderId.value, ...changeForm.value })
    const changeId = res?.data?.changeId
    if (!changeId) { ElMessage.error('创建变更失败'); return }
    changeDialogVisible.value = false
    ElMessage.success('请编辑订单后提交审批')
    router.push({ path: '/order/edit', query: { id: orderId.value, changeId } })
  } catch {} finally { changeLoading.value = false }
}

// 审批变更
async function handleApproveChange(change) {
  await ElMessageBox.confirm('确认通过该变更申请？通过后订单将解锁编辑权限。', '审批确认', { type: 'warning' })
  try {
    await approveOrderChange(change.id)
    ElMessage.success('变更已通过，订单编辑权限已解锁')
    loadChanges()
    loadOrderDetail()
  } catch {}
}
async function handleRejectChange(change) {
  const { value: remark } = await ElMessageBox.prompt('请输入驳回原因', '驳回变更',
    { confirmButtonText: '确定驳回', inputPattern: /\S+/, inputErrorMessage: '原因不能为空', type: 'warning' })
  try {
    await rejectOrderChange(change.id, remark)
    ElMessage.success('变更申请已驳回')
    loadChanges()
  } catch {}
}

// 编辑订单（变更解锁后跳转）
function goEdit() {
  router.push({ path: '/order/create', query: { id: orderId.value } })
}

// ==================== 订单操作 ====================
async function handleToPlanning() {
  await ElMessageBox.confirm(`将订单【${orderData.value.orderNo}】直接生成车间计划（跳过MRP运算），确定继续？`, '转车间计划', { type: 'info' })
  try {
    await convertSalesOrderToProductionPlan(orderId.value)
    ElMessage.success('已成功生成生产计划')
    loadOrderDetail()
  } catch {}
}

async function handleConvertToPlan() {
  await ElMessageBox.confirm(`将订单【${orderData.value.orderNo}】转为生产需求，加入物料需求计划，确定继续？`, '转生产需求', { type: 'info' })
  try {
    await convertSalesOrderToPlan(orderId.value)
    ElMessage.success('已成功转为生产需求')
    loadOrderDetail()
  } catch {}
}

async function handleToOutsource() {
  await ElMessageBox.confirm(`将订单【${orderData.value.orderNo}】未分配数量整单转外协? `, '转外协', { type: 'info' })
  try {
    await batchConvertToOutsource({ ids: [orderId.value] })
    ElMessage.success('已成功转外协')
    loadOrderDetail()
  } catch {}
}

// 转换按钮可见性: 状态允许 + 至少一行未分配数量 > 0 + 未加入主计划
// 加入主计划后所有需求由主计划统一拆分(转车间计划/转采购单/去发货), 直接转换会和主计划口径冲突
const canConvertOrder = computed(() => {
  if (orderData.value?.masterPlanAdded === 1) return false
  const s = orderData.value?.status
  if (!s) return false
  const allowed = ['confirmed', 'mrp_planned', 'in_production', 'outsourcing', 'partial_shipped']
  if (!allowed.includes(s)) return false
  const items = orderData.value?.items || []
  return items.some(it => Number(it.unallocatedQty || 0) > 0)
})

// 主计划按钮: 状态在白名单 + 至少一行未分配数量 > 0 (即使已加入也展示"已加入主计划"按钮)
const canAddToMasterPlan = computed(() => {
  const s = orderData.value?.status
  if (!s) return false
  const allowed = ['confirmed', 'mrp_planned', 'in_production', 'outsourcing', 'partial_shipped']
  if (!allowed.includes(s)) return false
  const items = orderData.value?.items || []
  return items.some(it => Number(it.unallocatedQty || 0) > 0)
})

async function handleAddToMasterPlan() {
  if (orderData.value.masterPlanAdded) {
    ElMessage.info('该订单已加入主计划')
    return
  }
  try {
    await ElMessageBox.confirm('确认将该订单加入"主生产计划"?', '提示', { type: 'info' })
    await addOrderToMasterPlan(orderId.value)
    ElMessage.success('已加入主计划')
    loadOrderDetail()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}

async function handleSubmit() {
  await ElMessageBox.confirm('确认提交该订单进行审批？', '提示', { type: 'warning' })
  try { await submitSalesOrder(orderId.value); ElMessage.success('订单已提交审批'); loadOrderDetail() } catch {}
}

const approveDialogVisible = ref(false)
const approveLoading = ref(false)
const approveForm = ref({ remark: '' })
function handleApprove() { approveForm.value.remark = ''; approveDialogVisible.value = true }
async function doApprove() {
  approveLoading.value = true
  try { await approveSalesOrder(orderId.value, approveForm.value.remark); ElMessage.success('审批通过'); approveDialogVisible.value = false; loadOrderDetail() }
  catch {} finally { approveLoading.value = false }
}

const rejectDialogVisible = ref(false)
const rejectLoading = ref(false)
const rejectForm = ref({ remark: '' })
function handleReject() { rejectForm.value.remark = ''; rejectDialogVisible.value = true }
async function doReject() {
  if (!rejectForm.value.remark) { ElMessage.warning('请输入驳回原因'); return }
  rejectLoading.value = true
  try { await rejectSalesOrder(orderId.value, rejectForm.value.remark); ElMessage.success('订单已驳回'); rejectDialogVisible.value = false; loadOrderDetail() }
  catch {} finally { rejectLoading.value = false }
}

async function handleVoid() {
  const { value: reason } = await ElMessageBox.prompt('请输入作废原因', '作废订单',
    { confirmButtonText: '确定', inputPattern: /\S+/, inputErrorMessage: '原因不能为空', type: 'warning' })
  try { await voidSalesOrder(orderId.value, reason); ElMessage.success('订单已作废'); loadOrderDetail() } catch {}
}

async function handleDelete() {
  await ElMessageBox.confirm('确定删除此草稿订单？此操作不可恢复。', '删除确认', { type: 'warning' })
  try { await deleteSalesOrder(orderId.value); ElMessage.success('删除成功'); router.push('/order/list') } catch {}
}

async function handleComplete() {
  await ElMessageBox.confirm('确定标记此订单为已完成？', '订单完成', { type: 'info' })
  try { await completeSalesOrder(orderId.value); ElMessage.success('订单已完成'); loadOrderDetail() } catch {}
}


// ==================== 审批进度 ====================
const approvalProgressLoading = ref(false)
const approvalProgressData = ref(null)

const NODE_STATUS_MAP = {
  approved: { label: '已通过', type: 'success', timeline: 'success' },
  rejected: { label: '已驳回', type: 'danger', timeline: 'danger' },
  pending:  { label: '审批中', type: 'warning', timeline: 'warning' },
  waiting:  { label: '待审批', type: 'info',    timeline: '' }
}
function nodeStatusLabel(s) { return NODE_STATUS_MAP[s]?.label || s }
function nodeTagType(s) { return NODE_STATUS_MAP[s]?.type || '' }
function nodeTimelineType(s) { return NODE_STATUS_MAP[s]?.timeline || '' }

async function loadApprovalProgress() {
  if (!orderId.value) return
  approvalProgressLoading.value = true
  approvalProgressData.value = null
  try {
    const res = await getApprovalProgress('sales_order', orderId.value)
    approvalProgressData.value = res.data
  } catch {} finally { approvalProgressLoading.value = false }
}

// ==================== 导航 ====================
function goBack() { router.push('/order/list') }

// ==================== 附件 ====================
const attachmentList = ref([])
const attachmentLoading = ref(false)
const attachmentUploading = ref(false)

async function loadAttachments() {
  attachmentLoading.value = true
  try {
    const res = await getOrderAttachments(orderId.value)
    attachmentList.value = res.data || []
  } catch {} finally { attachmentLoading.value = false }
}

async function onAttachmentFileChange(file) {
  attachmentUploading.value = true
  try {
    await uploadOrderAttachment(orderId.value, file.raw)
    ElMessage.success(`「${file.name}」上传成功`)
    loadAttachments()
  } catch { ElMessage.error(`「${file.name}」上传失败`) } finally { attachmentUploading.value = false }
}

async function handleDeleteAttachment(row) {
  try {
    await ElMessageBox.confirm(`确认删除附件「${row.originalName}」？`, '提示', { type: 'warning' })
    await deleteOrderAttachment(row.id)
    ElMessage.success('删除成功')
    loadAttachments()
  } catch {}
}

function downloadAttachment(row) {
  const a = document.createElement('a')
  a.href = row.fileUrl
  a.download = row.originalName
  a.target = '_blank'
  a.click()
}

// 图片预览
const previewVisible = ref(false)
const previewUrls = ref([])
const previewIndex = ref(0)
function previewAttachment(row) {
  // 收集所有图片类附件,支持多图切换
  const images = attachmentList.value
    .filter(a => isImage(a.originalName || a.fileUrl))
    .map(a => a.fileUrl)
  previewUrls.value = images
  previewIndex.value = Math.max(0, images.indexOf(row.fileUrl))
  previewVisible.value = true
}

function formatFileSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

// ==================== Tab 切换加载 ====================
watch(activeTab, (tab) => {
  if (tab === 'progress' && progressList.value.length === 0) loadProgress()
  if (tab === 'shipments' && shipmentList.value.length === 0) loadShipments()
  if (tab === 'invoices' && invoiceList.value.length === 0) loadInvoices()
  if (tab === 'approval' && !approvalProgressData.value) loadApprovalProgress()
  if (tab === 'finance' && receivableList.value.length === 0 && !costSummary.value) loadFinance()
  if (tab === 'changes' && changeList.value.length === 0) loadChanges()
  if (tab === 'attachments' && attachmentList.value.length === 0) loadAttachments()
  if (tab === 'returns' && returnList.value.length === 0) loadReturns()
  if (tab === 'followups' && orderFollowups.value.length === 0) loadOrderFollowups()
  if (tab === 'logs' && orderLogs.value.length === 0) loadOrderLogs()
})

// ==================== 退货记录 ====================
const returnLoading = ref(false)
const returnList = ref([])
const returnStatusMap = { pending: '待审批', approved: '已审批', received: '已收货', completed: '已完成', rejected: '已驳回' }
function returnStatusLabel(status) { return returnStatusMap[status] || status || '-' }
function returnStatusType(status) {
  const map = { pending: 'warning', approved: 'primary', received: '', completed: 'success', rejected: 'info' }
  return map[status] || 'info'
}

async function loadReturns() {
  if (!orderId.value) return
  returnLoading.value = true
  try {
    const res = await getReturnOrderPage({ keyword: orderData.value.orderNo, pageNum: 1, pageSize: 50 })
    returnList.value = (res.data?.records || []).filter(r => r.orderId == orderId.value || !r.orderId)
  } catch {} finally { returnLoading.value = false }
}

// 从订单详情页申请退货（跳转到退货管理页面）
function openCreateReturn() {
  router.push({ path: '/order/return', query: { orderId: orderId.value } })
}

// ==================== 跟进记录 ====================
const followupTypeMap = { phone: '电话', visit: '拜访', online: '线上', other: '其他' }
function followupTypeTagType(type) {
  return { phone: '', visit: 'success', online: 'warning', other: 'info' }[type] || 'info'
}

const orderFollowups = ref([])
const orderFollowupLoading = ref(false)
const orderFollowupDialogVisible = ref(false)
const orderFollowupSubmitting = ref(false)
const orderFollowupForm = reactive({ followupType: 'phone', content: '', nextFollowupDate: '' })

async function loadOrderFollowups() {
  if (!orderId.value) return
  orderFollowupLoading.value = true
  try {
    const res = await getOrderFollowups(orderId.value)
    orderFollowups.value = res.data || []
  } catch {} finally { orderFollowupLoading.value = false }
}

function openOrderFollowupForm() {
  orderFollowupForm.followupType = 'phone'
  orderFollowupForm.content = ''
  orderFollowupForm.nextFollowupDate = ''
  orderFollowupDialogVisible.value = true
}

async function submitOrderFollowup() {
  if (!orderFollowupForm.content.trim()) {
    ElMessage.warning('请填写跟进内容')
    return
  }
  orderFollowupSubmitting.value = true
  try {
    await createOrderFollowup(orderId.value, { ...orderFollowupForm })
    ElMessage.success('添加成功')
    orderFollowupDialogVisible.value = false
    loadOrderFollowups()
  } catch {} finally { orderFollowupSubmitting.value = false }
}

async function handleDeleteOrderFollowup(row) {
  try {
    await ElMessageBox.confirm('确认删除该跟进记录？', '提示', { type: 'warning' })
    await deleteOrderFollowupApi(row.id)
    ElMessage.success('删除成功')
    loadOrderFollowups()
  } catch {}
}

// ==================== 操作日志 ====================
const logOperationMap = {
  create: '创建', update: '编辑', submit: '提交', approve: '审批通过',
  reject: '驳回', cancel: '取消', void: '作废', complete: '完成',
  change: '变更', shipment: '发货'
}
function logOperationTagType(op) {
  const map = {
    create: 'primary', update: 'info', submit: 'warning', approve: 'success',
    reject: 'danger', cancel: 'warning', void: 'info', complete: 'success',
    change: 'warning', shipment: 'primary'
  }
  return map[op] || 'info'
}

const orderLogs = ref([])
const orderLogLoading = ref(false)

async function loadOrderLogs() {
  if (!orderId.value) return
  orderLogLoading.value = true
  try {
    const res = await getOrderLogs(orderId.value)
    orderLogs.value = res.data || []
  } catch {} finally { orderLogLoading.value = false }
}

// ==================== 初始化 ====================
async function init() {
  if (orderId.value) {
    // 行级扩展字段配置, 早加载,以便明细表渲染时列已就绪
    itemExtFields.loadFieldConfigs()
    await loadOrderDetail()
    // 预加载发票以显示顶部发票总额
    loadInvoices()
  } else {
    ElMessage.warning('缺少订单ID参数')
  }
}
onMounted(init)
// keep-alive 缓存场景：每次激活该 tab 时若 orderId 已存在则刷新数据，避免显示空数据/旧告警
onActivated(() => {
  if (orderId.value && !orderData.id) init()
})
// query.id 变化（同一页面切换不同订单）也触发刷新
watch(() => orderId.value, (newId, oldId) => {
  if (newId && newId !== oldId) init()
})
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; height: 100%; }
.page-header { margin-bottom: 12px; }
.order-header-card { :deep(.el-card__body) { padding: 16px 20px; } }
.order-header {
  display: flex; justify-content: space-between; align-items: center;
  &__left { display: flex; align-items: center; gap: 16px; }
  &__right { display: flex; gap: 8px; }
}
.order-no { font-size: 20px; font-weight: 600; color: #333; margin: 0; }
.amount-text { font-size: 16px; font-weight: bold; color: #f56c6c; }
.section-title { font-size: 15px; font-weight: 600; color: #303133; margin: 20px 0 12px; padding-left: 8px; border-left: 3px solid #409eff; }
.text-success { color: #67c23a; font-weight: 600; }
.text-danger { color: #f56c6c; font-weight: 600; }
.change-card { :deep(.el-card__body) { padding: 12px 16px; } }
.change-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.change-type { font-weight: 600; color: #303133; }
.change-content { font-size: 14px; color: #606266; margin-bottom: 8px; line-height: 1.6; }
.change-meta { font-size: 12px; color: #909399; display: flex; gap: 16px; flex-wrap: wrap; }

/* 工序卡片 */
.step-cards-wrapper { padding: 12px 20px; }
.progress-expand { padding: 12px 20px; background: #fafafa; display: flex; flex-direction: column; gap: 16px; }
.progress-expand-section { background: #fff; border-radius: 6px; padding: 12px 16px; border: 1px solid #ebeef5; }
.progress-expand-title { font-size: 14px; font-weight: 500; color: #303133; margin-bottom: 10px; display: flex; align-items: center; gap: 10px; }
.progress-expand-subtitle { font-size: 12px; font-weight: 400; color: #909399; }
.progress-expand-hint { padding: 16px 0; text-align: center; color: #909399; font-size: 13px; }
/* 工序步骤条行 — 浅蓝底 */
.progress-expand :deep(.step-row td) { background: #f5faff !important; padding: 10px 16px !important; }
.progress-expand :deep(.step-row td .cell) {
  color: #303133;
  white-space: normal !important;
  text-overflow: clip !important;
  padding: 0 !important;
  overflow: hidden !important;
}
.step-strip { display: flex; flex-direction: column; gap: 10px; }
.step-strip-header {
  display: flex; align-items: center; gap: 10px;
  font-size: 13px;
}
.step-strip-wo { font-weight: 500; color: #303133; }
.step-strip-meta { color: #909399; font-size: 12px; }
.step-strip-steps { padding: 4px 0; }
.step-strip-title { font-size: 13px; font-weight: 500; }
.step-strip-desc { font-size: 12px; line-height: 1.5; }
.step-strip-assign { color: #409eff; font-size: 11px; }

/* 工单展开行 (工序卡片 — 紧凑, 左对齐, 自动换行) */
.wo-task-wrap { padding: 12px 16px; background: #fafafa; }
.wo-step-cards {
  display: flex; flex-wrap: wrap; gap: 10px;
  justify-content: flex-start;          /* 左对齐, 不居中 */
}
.wo-sc {
  position: relative;
  width: 200px; flex: 0 0 auto;          /* 固定 200px, 不拉伸 */
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #ebeef5; border-left: 3px solid #c0c4cc;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.03);
  font-size: 12px;
}
/* 状态色: 左侧条 + 序号圆点 */
.wo-sc.completed { border-left-color: #67c23a; }
.wo-sc.in_progress { border-left-color: #e6a23c; }
.wo-sc.ready { border-left-color: #409eff; }
.wo-sc.paused { border-left-color: #f56c6c; }
/* 逾期工序: 整张卡片变红 (优先级最高, 覆盖原状态色) */
.wo-sc.overdue {
  border-left-color: #f56c6c;
  border-color: #fbc4c4;
  background: #fef0f0;
}
.wo-sc-overdue-tag { transform: scale(0.85); transform-origin: left center; }
.wo-sc-head {
  display: flex; align-items: center; gap: 6px;
  margin-bottom: 6px; padding-bottom: 6px;
  border-bottom: 1px solid #f2f6fc;
}
.wo-sc-no {
  display: inline-flex; align-items: center; justify-content: center;
  width: 20px; height: 20px; border-radius: 50%;
  background: #c0c4cc; color: #fff;
  font-size: 11px; font-weight: 600; flex-shrink: 0;
}
.wo-sc.completed .wo-sc-no { background: #67c23a; }
.wo-sc.in_progress .wo-sc-no { background: #e6a23c; }
.wo-sc.ready .wo-sc-no { background: #409eff; }
.wo-sc.paused .wo-sc-no { background: #f56c6c; }
.wo-sc.overdue .wo-sc-no { background: #f56c6c; }      /* 逾期: 序号圆点也变红 */
.wo-sc-name {
  flex: 1; font-size: 13px; font-weight: 500; color: #303133;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.wo-sc-status { flex-shrink: 0; }
.wo-sc-row {
  display: flex; align-items: baseline; gap: 6px;
  margin-bottom: 4px; line-height: 1.4;
}
.wo-sc-row:last-child { margin-bottom: 0; }
.wo-sc-label {
  display: inline-block; min-width: 36px;
  color: #909399; font-size: 11px;
}
.wo-sc-val { color: #303133; }
.wo-sc-val b { color: #409eff; font-size: 14px; font-weight: 600; margin-right: 1px; }
.wo-sc-val i { color: #c0c4cc; margin: 0 2px; font-style: normal; }
.wo-sc-qc { display: flex; gap: 12px; }
.wo-sc-pass { color: #67c23a; }
.wo-sc-pass b { font-size: 13px; font-weight: 600; margin-left: 2px; }
.wo-sc-fail { color: #909399; }
.wo-sc-fail b { font-size: 13px; font-weight: 600; margin-left: 2px; }
.wo-sc-fail.has-defect { color: #f56c6c; }
.wo-sc-no-qc { color: #c0c4cc; font-size: 11px; font-style: italic; }
.wo-sc-outsource {
  position: absolute; top: 8px; right: 8px;
}
.step-cards-flow {
  display: flex; align-items: stretch; flex-wrap: wrap;
  justify-content: center;
  gap: 6px; padding: 4px 0; width: 100%;
}
.step-card-item {
  display: flex; align-items: stretch;
  flex: 1 1 130px; min-width: 120px; max-width: 170px;
  margin: 0;
}
.step-arrow { display: none; }
.step-card {
  flex: 1; width: 100%; min-width: 0;
  padding: 6px 8px;
  border: 1.5px solid #409eff; border-radius: 6px; background: #ecf5ff;
  text-align: center; font-family: inherit;
}
.step-card.outsource { border-color: #e6a23c; background: #fdf6ec; }
.step-card.qc { border-color: #67c23a; background: #f0f9eb; }
.step-card-header { display: flex; align-items: center; justify-content: center; gap: 4px; margin-bottom: 4px; }
.step-badge {
  display: inline-flex; align-items: center; justify-content: center;
  width: 16px; height: 16px; border-radius: 50%;
  background: #409eff; color: #fff; font-size: 10px; font-weight: 700; flex-shrink: 0;
}
.step-card.outsource .step-badge { background: #e6a23c; }
.step-name { font-weight: 600; font-size: 12px; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.step-card-body { font-size: 11px; color: #606266; line-height: 1.5; }
.step-info { margin-bottom: 1px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.step-assign { color: #409eff; font-weight: 500; }
.step-status-row { display: flex; align-items: center; justify-content: center; gap: 4px; }
.step-status-dot { width: 6px; height: 6px; border-radius: 50%; background: #909399; }
.step-status-dot.pending { background: #909399; }
.step-status-dot.ready { background: #409eff; }
.step-status-dot.in_progress { background: #e6a23c; }
.step-status-dot.completed { background: #67c23a; }
.step-status-dot.paused { background: #f56c6c; }
.step-card-tags { display: flex; justify-content: center; gap: 4px; margin-top: 4px; flex-wrap: wrap; }
.step-tag { font-size: 10px; padding: 1px 6px; border-radius: 3px; line-height: 16px; }
.step-tag-outsource { background: #faecd8; color: #e6a23c; border: 1px solid #e6a23c; }
.step-tag-outsourceable { background: #f4f4f5; color: #909399; border: 1px solid #dcdfe6; }
.step-tag-qc { background: #fdf6ec; color: #e6a23c; border: 1px solid #e6a23c; }
.step-tag-qc-pass { background: #e1f3d8; color: #67c23a; border: 1px solid #67c23a; }
.step-tag-qc-fail { background: #fde2e2; color: #f56c6c; border: 1px solid #f56c6c; }
.text-muted { color: #909399; }

/* 审批进度弹窗 */
.timeline-node {
  .node-title { display: flex; align-items: center; margin-bottom: 8px; .node-name { font-weight: 500; } }
  .node-records {
    .record-item {
      display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; margin-bottom: 4px;
      .approver-name { font-weight: 500; }
      .remark { color: #909399; }
      .action-time { color: #c0c4cc; margin-left: auto; }
    }
  }
  .waiting-text { font-size: 13px; color: #e6a23c; }
}
</style>
