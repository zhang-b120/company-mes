<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="订单编号" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="queryParams.supplierId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" value="draft" />
            <el-option label="待审批" value="pending_approval" />
            <el-option label="已确认" value="confirmed" />
            <el-option label="部分收料" value="partial_receipt" />
            <el-option label="已全部收料" value="fully_receipt" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="办结" value="closed" />
            <el-option label="已作废" value="voided" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="advancedDrawer = true">高级搜索</el-button>
          <el-button v-permission="'purchase:order:add'" type="primary" plain @click="handleAdd">新增订单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 高级搜索抽屉 -->
    <el-drawer v-model="advancedDrawer" title="高级搜索" size="380px" direction="rtl">
      <el-form :model="queryParams" label-width="90px">
        <el-form-item label="订单编号">
          <el-input v-model="queryParams.poNo" placeholder="订单编号" clearable />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="queryParams.supplierId" placeholder="全部" clearable filterable style="width: 100%">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款方式">
          <el-input v-model="queryParams.paymentTerms" placeholder="如：月结30天" clearable />
        </el-form-item>
        <el-form-item label="交货日期">
          <el-date-picker v-model="queryParams.deliveryDateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="queryParams.createTimeRange" type="datetimerange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="advancedDrawer = false; handleReset()">重置</el-button>
        <el-button type="primary" @click="advancedDrawer = false; handleSearch()">查询</el-button>
      </template>
    </el-drawer>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <span v-if="selectedRows.length > 0" class="selection-info">已选 {{ selectedRows.length }} 条</span>
        </div>
        <div class="toolbar-right">
          <!-- 批量生成库存任务已移除，收料单从采购订单操作栏生成 -->
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </div>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="40" />
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'totalAmount'">{{ row.totalAmount != null ? row.totalAmount.toFixed(2) : '-' }}</template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
            <!-- returnStatus/invTaskGenStatus/invTaskProcessStatus 列已移除 -->
            <template v-else-if="col.key === 'sourceRequestCount'">
              <el-tag v-if="row.sourceRequestCount > 0" type="primary" size="small"
                style="cursor: pointer" @click="viewSourceRequests(row)">
                {{ row.sourceRequestCount }}条申请
              </el-tag>
              <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewDetail(row)">查看</el-button>
            <template v-if="row.status !== 'voided'">
              <el-button v-permission="'purchase:order:add'"
                v-if="row.status === 'draft'"
                type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button v-permission="'purchase:order:receipt'"
                v-if="(row.status === 'confirmed' || row.status === 'partial_receipt') && row.receiptable !== false"
                type="success" link size="small" @click="handleOpenReceipt(row)">生成收料单</el-button>
              <el-button v-permission="'purchase:order:approve'"
                v-if="['confirmed','partial_receipt','fully_receipt'].includes(row.status)"
                type="warning" link size="small" @click="handleClose(row)">办结</el-button>
              <el-button v-permission="'purchase:return:add'"
                v-if="row.status === 'closed'"
                type="danger" link size="small" @click="$router.push({ path: '/purchase/returns', query: { orderId: row.id } })">退货</el-button>
              <el-button v-permission="'purchase:order:submit'" v-if="row.status === 'draft' || row.status === 'rejected'"
                type="warning" link size="small" @click="handleSubmitApproval(row)">提交审批</el-button>
              <el-button v-permission="'purchase:order:approve'" v-if="row.status === 'pending_approval'"
                type="success" link size="small" @click="handleApprove(row)">审批通过</el-button>
              <el-button v-permission="'purchase:order:approve'" v-if="row.status === 'pending_approval'"
                type="danger" link size="small" @click="handleReject(row)">审批驳回</el-button>
              <el-button v-if="row.status === 'rejected'"
                type="danger" link size="small" @click="handleVoid(row)">作废</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑订单弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="订单号">
              <el-input v-model="form.poNo" :placeholder="editingId ? '' : '不填则自动生成（PO+时间戳）'" :disabled="!!editingId" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable style="width: 100%"
                @change="onSupplierChange">
                <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交货日期" prop="deliveryDate">
              <el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.contactName" placeholder="供应商联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="付款方式">
              <el-select v-model="form.paymentTerms" placeholder="请选择或输入付款方式" filterable allow-create style="width: 100%">
                <el-option v-for="t in paymentTermsOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="附件">
          <AttachmentUploader v-model="form.attachmentUrls" :limit="5" tip="支持任意格式,最多 5 个,单个不超过 50MB" />
        </el-form-item>
        <el-form-item label="订单明细" required>
          <div style="width: 100%">
            <el-table :data="form.items" border size="small" style="margin-bottom: 10px">
              <el-table-column label="物料编号" width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.materialNo">{{ row.materialNo }}</span>
                  <span v-else style="color:#c0c4cc">-</span>
                </template>
              </el-table-column>
              <el-table-column label="物料" min-width="180">
                <template #default="{ row }">
                  <div style="display:flex;gap:4px;align-items:center">
                    <el-input
                      :value="row.materialName || ''"
                      placeholder="请选择物料" readonly size="small" style="flex:1" />
                    <el-button size="small" type="primary" plain @click="openMaterialPicker(row)">选</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="规格" width="90">
                <template #default="{ row }">
                  <span style="color: #606266; font-size: 12px">{{ $fmtSpec(row.spec) || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单位" width="60" align="center">
                <template #default="{ row }">
                  <span style="color: #606266; font-size: 12px">{{ row.unit || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="0.01" :precision="4" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="换算数量" width="120">
                <template #default="{ row }">
                  <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
                    {{ secondaryDisplay(row.quantity, row.unitConfig) }}
                  </span>
                  <span v-else style="color: #c0c4cc">-</span>
                </template>
              </el-table-column>
              <el-table-column label="含税单价" width="150">
                <template #default="{ row }">
                  <div style="display: flex; align-items: center; gap: 4px">
                    <el-input-number v-model="row.unitPrice" :min="0" :precision="4" size="small" controls-position="right" style="width: 105px" />
                    <el-tooltip content="历史采购价" placement="top">
                      <el-icon style="cursor: pointer; color: #409eff; flex-shrink: 0" @click="showPriceHistory(row)"><Clock /></el-icon>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="税率(%)" width="100">
                <template #default="{ row }">
                  <el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="不含税单价" width="100" align="right">
                <template #default="{ row }">
                  <span style="color:#909399; font-size:13px">{{ ((row.unitPrice || 0) / (1 + (row.taxRate || 0) / 100)).toFixed(4) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="含税小计" width="100" align="right">
                <template #default="{ row }">{{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="60" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeItem($index)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button type="primary" link size="small" @click="addItem">+ 添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 价格历史弹窗 -->
    <el-dialog v-model="priceHistoryVisible" title="历史采购价" width="560px" append-to-body>
      <el-table :data="priceHistoryList" size="small" border stripe v-loading="priceHistoryLoading">
        <el-table-column prop="poNo" label="采购订单" min-width="130" />
        <el-table-column prop="supplierName" label="供应商" min-width="110" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right">
          <template #default="{ row }">¥{{ row.unitPrice }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="createTime" label="时间" min-width="155" />
      </el-table>
      <template #footer>
        <el-button @click="priceHistoryVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="PURCHASE_ORDER_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 审批备注弹窗 -->
    <el-dialog v-model="remarkDialogVisible" :title="remarkDialogTitle" width="420px" append-to-body>
      <el-input v-model="remarkInput" type="textarea" :rows="3" placeholder="请输入审批意见（可选）" />
      <template #footer>
        <el-button @click="remarkDialogVisible = false">取消</el-button>
        <el-button :type="remarkDialogType" :loading="remarkLoading" @click="doRemarkAction">确定</el-button>
      </template>
    </el-dialog>

    <!-- 关联申请弹窗 -->
    <el-dialog v-model="sourceRequestsVisible" title="关联的采购申请" width="720px" append-to-body>
      <el-table :data="sourceRequestsList" border stripe size="small" v-loading="sourceRequestsLoading">
        <el-table-column prop="requestNo" label="申请编号" min-width="140">
          <template #default="{ row }">
            <el-button link type="primary" style="padding:0" @click="openRequestDetail(row)">
              {{ row.requestNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="materialNo" label="物料编码" width="110" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="规格" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="quantity" label="申请数量" width="90" align="right" />
        <el-table-column prop="approvedAt" label="审批时间" min-width="155" />
      </el-table>
      <template #footer>
        <el-button @click="sourceRequestsVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批量收料弹窗 -->
    <el-dialog v-model="receiptDetailVisible" title="生成采购收料单" width="900px" destroy-on-close v-loading="receiptDetailLoading">
      <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="订单编号">{{ detail.poNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span style="color: #e6a23c; font-weight: 600">¥{{ detail.totalAmount?.toFixed(2) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <div style="font-weight:600;color:#606266;margin-bottom:8px">收料明细（填写本次收料数量）</div>
      <el-table :data="batchReceiptItems" border size="small">
        <el-table-column prop="materialNo" label="物料编码" width="110" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="110" show-overflow-tooltip />
        <el-table-column label="规格" width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="50" align="center" />
        <el-table-column prop="quantity" label="订单数量" width="80" align="right" />
        <el-table-column label="换算数量" width="120" align="right">
          <template #default="{ row }">
            <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
              {{ secondaryDisplay(row.quantity, row.unitConfig) }}
            </span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="已生成收料" width="100" align="right">
          <template #default="{ row }">
            <span :style="Number(row.receiptQty||0)>=Number(row.quantity||0)?'color:#67c23a;font-weight:600':''">
              {{ row.receiptQty || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="本次数量" width="130">
          <template #default="{ row }">
            <el-input-number
              v-if="Number(row.quantity||0) > Number(row.receiptQty||0)"
              v-model="row.actualQty" :min="0"
              :max="Number(row.quantity||0)-Number(row.receiptQty||0)"
              :precision="4" size="small" controls-position="right" style="width:100%" />
            <span v-else style="color:#67c23a;font-size:12px">已全部生成</span>
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="120">
          <template #default="{ row }">
            <el-input v-if="Number(row.quantity||0) > Number(row.receiptQty||0)"
              v-model="row.batchNo" size="small" placeholder="可选" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="receiptDetailVisible = false">关闭</el-button>
        <el-button type="primary" :loading="batchReceiptLoading" @click="doGenerateReceipts">生成收料单</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="采购订单详情" size="820px" direction="rtl" destroy-on-close>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="订单编号">{{ detail.poNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span style="color: #e6a23c; font-weight: 600">¥{{ detail.totalAmount?.toFixed(2) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ detail.paymentTerms || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ statusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <!-- 库存任务状态已移除 -->
        <el-descriptions-item label="审批意见">{{ detail.approveRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs style="margin-top: 16px">
        <el-tab-pane label="订单明细">
          <el-table :data="detail.items || []" border stripe size="small">
            <el-table-column prop="materialNo" label="物料编码" width="100" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="规格" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column prop="quantity" label="数量" width="90" align="right" />
            <el-table-column label="换算数量" width="120" align="right">
              <template #default="{ row }">
                <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
                  {{ secondaryDisplay(row.quantity, row.unitConfig) }}
                </span>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="含税单价" width="100" align="right" />
            <el-table-column prop="taxRate" label="税率" width="70" align="center">
              <template #default="{ row }">{{ row.taxRate != null ? `${row.taxRate}%` : '13%' }}</template>
            </el-table-column>
            <el-table-column label="不含税单价" width="100" align="right">
              <template #default="{ row }">
                <span style="color:#909399; font-size:13px">{{ row.unitPrice != null ? (Number(row.unitPrice) / (1 + Number(row.taxRate || 13) / 100)).toFixed(4) : '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="含税金额" width="100" align="right">
              <template #default="{ row }">{{ row.amount?.toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="receiptQty" label="已收料" width="85" align="right" />
            <el-table-column prop="stockInQty" label="入库数量" width="85" align="right" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="来源申请">
          <el-table v-if="detail.sourceRequests?.length" :data="detail.sourceRequests" border stripe size="small">
            <el-table-column prop="requestNo" label="申请编号" min-width="140">
              <template #default="{ row }">
                <el-button link type="primary" style="padding:0" @click="openRequestDetail(row)">
                  {{ row.requestNo }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="materialNo" label="物料编码" width="110" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="110" show-overflow-tooltip />
            <el-table-column label="规格" width="80" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="55" align="center" />
            <el-table-column prop="quantity" label="申请数量" width="90" align="right" />
            <el-table-column prop="approvedAt" label="审批时间" min-width="155" />
          </el-table>
          <el-empty v-else description="暂无关联申请单" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="库存任务记录">
          <el-table :data="detailInvTasks" v-loading="detailInvTasksLoading" border stripe size="small">
            <el-table-column prop="taskNo" label="任务单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="invTaskStatusTagType(row.status)" size="small">{{ invTaskStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="batchNo" label="批次号" width="120" show-overflow-tooltip />
            <el-table-column prop="materialSummary" label="物料明细" min-width="180" show-overflow-tooltip />
            <el-table-column prop="createTime" label="创建时间" width="160">
              <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="退回原因" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.returnReason" style="color: #f56c6c">{{ row.returnReason }}</span>
                <span v-else style="color:#c0c4cc">—</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="附件">
          <AttachmentList v-if="detail.attachmentUrls?.length" :value="detail.attachmentUrls" />
          <el-empty v-else description="暂无附件" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="采购收料单" lazy>
          <el-table :data="detail.receipts || []" border stripe size="small">
            <el-table-column prop="receiptNo" label="收料单号" min-width="150" show-overflow-tooltip />
            <el-table-column prop="actualQty" label="收料数量" width="90" align="right" />
            <el-table-column prop="status" label="收料状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="{ pending_receive: 'info', receiving: 'warning', received: 'success', closed: '' }[row.status]" size="small">
                  {{ { pending_receive: '待收料', receiving: '收料中', received: '已收料', closed: '办结' }[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="qcStatus" label="质检状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="{ none: 'info', pending_qc: 'warning', qc_done: 'success' }[row.qcStatus]" size="small">
                  {{ { none: '无质检', pending_qc: '待质检', qc_done: '已质检' }[row.qcStatus] || row.qcStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
            <el-table-column prop="defectQty" label="不合格数" width="80" align="right" />
            <el-table-column prop="stockInQty" label="入库数" width="80" align="right" />
            <el-table-column prop="createTime" label="创建时间" width="160">
              <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(detail.receipts || []).length" description="暂无收料单" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="财务信息" lazy>
          <h4 style="margin: 0 0 12px; font-size: 14px; color: #303133">应付账款</h4>
          <el-table :data="detailPayableList" v-loading="detailPayableLoading" border stripe size="small">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="payableNo" label="应付单号" min-width="160" show-overflow-tooltip />
            <el-table-column label="应付金额" width="120" align="right">
              <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="已付金额" width="120" align="right">
              <template #default="{ row }">¥{{ Number(row.paidAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="未付金额" width="120" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.unpaidAmount || 0) > 0 ? '#f56c6c' : '#67c23a', fontWeight: 500 }">
                  ¥{{ Number(row.unpaidAmount || 0).toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'paid' ? 'success' : row.status === 'overdue' ? 'danger' : 'warning'" size="small">
                  {{ { pending: '待付款', partial_paid: '部分付款', paid: '已付清', overdue: '已逾期' }[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="120" align="center" />
            <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'paid'" type="primary" link size="small"
                  @click="openMakePayment(row)">付款</el-button>
                <span v-else style="color: #c0c4cc">已结清</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!detailPayableLoading && detailPayableList.length === 0" description="暂无应付账款" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 付款登记弹窗 -->
    <el-dialog v-model="makePayVisible" title="付款登记" width="520px" destroy-on-close append-to-body>
      <el-form :model="makePayForm" label-width="100px">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="应付单号">{{ makePayForm.payableNo }}</el-descriptions-item>
          <el-descriptions-item label="应付金额">¥{{ Number(makePayForm.amount || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="已付">¥{{ Number(makePayForm.paid || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="未付">
            <span style="color:#f56c6c;font-weight:600">¥{{ Number(makePayForm.unpaid || 0).toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item label="付款金额" required>
          <el-input-number v-model="makePayForm.payAmount" :min="0.01" :max="Number(makePayForm.unpaid || 0)"
            :precision="2" style="width: 200px" />
          <el-button type="primary" link size="small" style="margin-left: 8px"
            @click="makePayForm.payAmount = Number(makePayForm.unpaid || 0)">全额</el-button>
        </el-form-item>
        <el-form-item label="付款日期" required>
          <el-date-picker v-model="makePayForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="付款方式" required>
          <el-select v-model="makePayForm.paymentMethod" placeholder="请选择" style="width: 200px">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="支票" value="CHECK" />
            <el-option label="承兑汇票" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="makePayForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="makePayVisible = false">取消</el-button>
        <el-button type="primary" :loading="makePaySubmitting" @click="submitMakePayment">确认付款</el-button>
      </template>
    </el-dialog>


    <!-- 采购申请单详情子弹窗 -->
    <el-dialog v-model="requestDetailVisible" title="采购申请详情" width="640px"
      destroy-on-close append-to-body>
      <div v-if="requestDetailLoading" style="text-align:center;padding:40px 0">
        <el-icon class="is-loading" size="28"><Clock /></el-icon>
      </div>
      <el-descriptions v-else :column="2" border size="small">
        <el-descriptions-item label="申请编号" :span="2">{{ requestDetail.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ requestDetail.materialNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ requestDetail.materialName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ $fmtSpec(requestDetail.spec) }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ requestDetail.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请数量">{{ requestDetail.quantity }}</el-descriptions-item>
        <el-descriptions-item label="需求日期">{{ requestDetail.requiredDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预估单价">
          <span v-if="requestDetail.estimatedPrice" style="color:#e6a23c;font-weight:600">
            ¥{{ Number(requestDetail.estimatedPrice).toFixed(2) }}
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="推荐供应商">{{ requestDetail.suggestedSupplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="requestStatusTagType(requestDetail.status)" size="small">
            {{ requestStatusText(requestDetail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ requestDetail.approvedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见" :span="2">{{ requestDetail.approveRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ requestDetail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="requestDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料" @select="onMaterialSelected" />

    <!-- 退货对话框已移除 -->
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Setting } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import AttachmentList from '@/components/AttachmentList.vue'

const PURCHASE_ORDER_DEFAULT_COLUMNS = [
  { key: 'poNo', label: '订单编号', minWidth: 160 },
  { key: 'supplierName', label: '供应商', minWidth: 140 },
  { key: 'totalAmount', label: '总金额', minWidth: 110, align: 'right' },
  { key: 'deliveryDate', label: '交货日期', minWidth: 115 },
  { key: 'status', label: '状态', minWidth: 105, align: 'center' },
  { key: 'sourceRequestCount', label: '关联申请', minWidth: 90, align: 'center' },
  { key: 'createTime', label: '创建时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('purchase_order', PURCHASE_ORDER_DEFAULT_COLUMNS)
import {
  getPurchaseOrderPage,
  createPurchaseOrder,
  submitPurchaseOrder,
  approvePurchaseOrder,
  rejectPurchaseOrder,
  getPurchaseOrderDetail,
  getPurchasePriceHistory,
  generatePurchaseReceipts,
  getPurchaseRequestDetail,
  closePurchaseOrder,
  voidPurchaseOrder,
  updatePurchaseOrder,
} from '@/api/purchase'
import { getSupplierSelect, getMaterialSelect } from '@/api/master'
import { getWarehouseList, getWarehouseLocationList } from '@/api/system'
import { getInvTasksByPurchaseOrder } from '@/api/inventory'
import { getPayablePage, addPayment } from '@/api/finance'


function statusTagType(status) {
  const map = { draft: 'info', pending_approval: 'warning', confirmed: 'primary', partial_receipt: '', fully_receipt: 'success', rejected: 'danger', closed: 'success', voided: 'info' }
  return map[status] || 'info'
}
function statusText(status) {
  const map = { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', partial_receipt: '部分收料', fully_receipt: '已全部收料', rejected: '已驳回', closed: '办结', voided: '已作废' }
  return map[status] || status || '-'
}
// returnStatus/invTaskGenStatus/invTaskProcessStatus 辅助函数已移除
function invTaskStatusText(s) {
  return { pending: '待处理', completed: '已完成', returned: '已退回' }[s] || s || '-'
}
function invTaskStatusTagType(s) {
  return { pending: 'warning', completed: 'success', returned: 'danger' }[s] || 'info'
}

const supplierOptions = ref([])
const materialOptions = ref([])
const warehouseOptions = ref([])
const paymentTermsOptions = ['现结', '月结30天', '月结60天', '月结90天']

function getMaterialField(materialId, field) {
  if (!materialId) return ''
  const mat = materialOptions.value.find(m => m.id === materialId)
  return mat ? (mat[field] || '') : ''
}

function secondaryDisplay(qty, unitConfig) {
  if (!qty || !unitConfig) return ''
  try {
    const cfg = JSON.parse(unitConfig)
    const c = cfg.conversions?.[0]
    if (c) return `${(qty * c.ratio).toFixed(4)} ${c.unit}`
  } catch {}
  return ''
}

const materialPickerVisible = ref(false)
let currentPickerRow = null
function openMaterialPicker(row) {
  currentPickerRow = row
  materialPickerVisible.value = true
}
function onMaterialSelected(mats) {
  if (!currentPickerRow || !mats?.length) return
  mats.forEach((mat, idx) => {
    const target = idx === 0 ? currentPickerRow : (() => {
      const row = { materialId: undefined, materialNo: '', materialName: '', spec: '', unit: '', unitConfig: '', quantity: 1, unitPrice: 0, taxRate: 13 }
      form.items.push(row)
      return row
    })()
    target.materialId = mat.id
    target.materialNo = mat.materialNo || ''
    target.materialName = mat.materialName
    target.spec = mat.spec || ''
    target.unit = mat.unit || ''
    target.unitConfig = mat.unitConfig || ''
  })
}

function onSupplierChange(supplierId) {
  const sup = supplierOptions.value.find(s => s.id === supplierId)
  if (sup) {
    form.contactName = sup.contactName || ''
    form.paymentTerms = sup.settlementType || ''
  }
}

// ==================== 多选 ====================
const selectedRows = ref([])

// 高级搜索
const advancedDrawer = ref(false)

const queryParams = reactive({
  keyword: '', status: undefined, poNo: '', supplierId: undefined, paymentTerms: '',
  deliveryDateRange: null, createTimeRange: null, pageNum: 1, pageSize: 10
})

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (params.deliveryDateRange?.length === 2) {
      params.deliveryDateStart = params.deliveryDateRange[0]
      params.deliveryDateEnd = params.deliveryDateRange[1]
    }
    delete params.deliveryDateRange
    if (params.createTimeRange?.length === 2) {
      params.createTimeStart = params.createTimeRange[0]
      params.createTimeEnd = params.createTimeRange[1]
    }
    delete params.createTimeRange
    const res = await getPurchaseOrderPage(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

async function loadOptions() {
  // 物料数据量大,改为按需加载;供应商/仓库保留
  try {
    const [supRes, whRes] = await Promise.all([getSupplierSelect(), getWarehouseList()])
    supplierOptions.value = supRes.data || []
    warehouseOptions.value = whRes.data || []
  } catch {}
}

async function loadMaterialOptionsLazy() {
  if (materialOptions.value.length > 0) return
  try {
    const matRes = await getMaterialSelect()
    materialOptions.value = matRes.data || []
  } catch {}
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { keyword: '', status: undefined, poNo: '', supplierId: undefined, paymentTerms: '', deliveryDateRange: null, createTimeRange: null, pageNum: 1 })
  loadData()
}

// ==================== 新增/编辑订单 ====================
const dialogVisible = ref(false)
const dialogTitle = ref('新增采购订单')
const editingId = ref(null)
const formRef = ref(null)
const submitLoading = ref(false)

const getDefaultForm = () => ({
  poNo: '', supplierId: undefined, deliveryDate: '', contactName: '', paymentTerms: '', remark: '',
  attachmentUrls: [],
  items: [{ materialId: undefined, materialNo: '', materialName: '', spec: '', unit: '', unitConfig: '', quantity: 1, unitPrice: 0, taxRate: 13 }]
})
const form = reactive(getDefaultForm())
const rules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  deliveryDate: [{ required: true, message: '请选择交货日期', trigger: 'change' }]
}

function addItem() { form.items.push({ materialId: undefined, materialNo: '', materialName: '', spec: '', unit: '', unitConfig: '', quantity: 1, unitPrice: 0, taxRate: 13 }) }
function removeItem(index) {
  if (form.items.length <= 1) { ElMessage.warning('至少保留一条明细'); return }
  form.items.splice(index, 1)
}
function resetForm() {
  editingId.value = null
  dialogTitle.value = '新增采购订单'
  const d = getDefaultForm(); Object.keys(d).forEach(k => { form[k] = d[k] })
}
function handleAdd() { loadMaterialOptionsLazy(); resetForm(); dialogVisible.value = true }

async function handleEdit(row) {
  loadMaterialOptionsLazy()
  resetForm()
  editingId.value = row.id
  dialogTitle.value = '编辑采购订单'
  try {
    const res = await getPurchaseOrderDetail(row.id)
    const d = res.data || {}
    form.poNo = d.poNo || ''
    form.supplierId = d.supplierId || undefined
    form.deliveryDate = d.deliveryDate || ''
    form.contactName = d.contactName || ''
    form.paymentTerms = d.paymentTerms || ''
    form.remark = d.remark || ''
    form.attachmentUrls = d.attachmentUrls || []
    form.items = (d.items || []).map(item => ({
      materialId: item.materialId,
      materialNo: item.materialNo || '',
      materialName: item.materialName || '',
      spec: item.spec || '',
      unit: item.unit || '',
      unitConfig: item.unitConfig || getMaterialField(item.materialId, 'unitConfig'),
      quantity: item.quantity || 1,
      unitPrice: item.unitPrice || 0
    }))
    if (!form.items.length) form.items.push({ materialId: undefined, materialNo: '', materialName: '', spec: '', unit: '', unitConfig: '', quantity: 1, unitPrice: 0, taxRate: 13 })
    dialogVisible.value = true
  } catch { ElMessage.error('加载订单信息失败') }
}

async function handleSubmit() {
  await formRef.value.validate()
  const validItems = form.items.filter(i => i.materialId)
  if (!validItems.length) { ElMessage.warning('请至少添加一条订单明细'); return }
  submitLoading.value = true
  try {
    const payload = { supplierId: form.supplierId, contactName: form.contactName, deliveryDate: form.deliveryDate, paymentTerms: form.paymentTerms, remark: form.remark, attachmentUrls: form.attachmentUrls, items: validItems }
    if (editingId.value) {
      await updatePurchaseOrder(editingId.value, payload)
      ElMessage.success('采购订单更新成功')
    } else {
      await createPurchaseOrder({ poNo: form.poNo || undefined, ...payload })
      ElMessage.success('采购订单创建成功')
    }
    dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// ==================== 价格历史 ====================
const priceHistoryVisible = ref(false)
const priceHistoryLoading = ref(false)
const priceHistoryList = ref([])

async function showPriceHistory(row) {
  if (!row.materialId) return
  priceHistoryList.value = []; priceHistoryVisible.value = true; priceHistoryLoading.value = true
  try {
    const res = await getPurchasePriceHistory({ materialId: row.materialId })
    priceHistoryList.value = res.data || []
  } finally { priceHistoryLoading.value = false }
}

// ==================== 审批 ====================
const remarkDialogVisible = ref(false)
const remarkDialogTitle = ref('')
const remarkDialogType = ref('primary')
const remarkInput = ref('')
const remarkLoading = ref(false)
const remarkRow = ref(null)
const remarkAction = ref('')

function handleSubmitApproval(row) {
  ElMessageBox.confirm(`确定提交订单「${row.poNo}」审批？`, '提示', { type: 'warning' })
    .then(async () => {
      await submitPurchaseOrder(row.id); ElMessage.success('已提交审批'); loadData()
    }).catch(() => {})
}

function handleApprove(row) {
  remarkRow.value = row; remarkAction.value = 'approve'
  remarkInput.value = ''; remarkDialogTitle.value = '审批通过'; remarkDialogType.value = 'success'
  remarkDialogVisible.value = true
}

function handleReject(row) {
  remarkRow.value = row; remarkAction.value = 'reject'
  remarkInput.value = ''; remarkDialogTitle.value = '审批驳回'; remarkDialogType.value = 'danger'
  remarkDialogVisible.value = true
}

async function doRemarkAction() {
  remarkLoading.value = true
  try {
    if (remarkAction.value === 'approve') {
      await approvePurchaseOrder(remarkRow.value.id, { remark: remarkInput.value })
      ElMessage.success('审批通过')
    } else {
      await rejectPurchaseOrder(remarkRow.value.id, { remark: remarkInput.value })
      ElMessage.success('已驳回')
    }
    remarkDialogVisible.value = false; loadData()
    if (detailVisible.value) handleViewDetail(remarkRow.value)
  } finally { remarkLoading.value = false }
}

// ==================== 快速入库（从列表打开） ====================
const receiptDetailVisible = ref(false)
const receiptDetailLoading = ref(false)

async function handleOpenReceipt(row) {
  loadMaterialOptionsLazy()
  receiptDetailLoading.value = true
  receiptDetailVisible.value = true
  try {
    const res = await getPurchaseOrderDetail(row.id)
    detail.value = res.data || {}
    // 初始化批量收料行，预填物料默认仓库
    const itemsWithWh = (detail.value.items || []).map(item => {
      return {
        ...item,
        actualQty: Math.max(0, Number(item.quantity || 0) - Number(item.receiptQty || 0)),
        batchNo: ''
      }
    })
    batchReceiptItems.value = itemsWithWh
  } catch {} finally {
    receiptDetailLoading.value = false
  }
}

// ==================== 关联申请弹窗 ====================
const sourceRequestsVisible = ref(false)
const sourceRequestsLoading = ref(false)
const sourceRequestsList = ref([])

async function viewSourceRequests(row) {
  sourceRequestsList.value = []
  sourceRequestsVisible.value = true
  sourceRequestsLoading.value = true
  try {
    const res = await getPurchaseOrderDetail(row.id)
    sourceRequestsList.value = res.data?.sourceRequests || []
  } catch {} finally {
    sourceRequestsLoading.value = false
  }
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref({})
const detailInvTasks = ref([])
const detailInvTasksLoading = ref(false)
const detailPayableList = ref([])
const detailPayableLoading = ref(false)

async function handleViewDetail(row) {
  try {
    const res = await getPurchaseOrderDetail(row.id)
    detail.value = res.data || {}
    detailVisible.value = true
    loadDetailInvTasks(row.id)
    loadDetailPayables(row.id)
  } catch {}
}

async function loadDetailInvTasks(orderId) {
  detailInvTasksLoading.value = true
  detailInvTasks.value = []
  try {
    const res = await getInvTasksByPurchaseOrder(orderId)
    detailInvTasks.value = res.data || []
  } catch {} finally {
    detailInvTasksLoading.value = false
  }
}

async function loadDetailPayables(orderId) {
  detailPayableLoading.value = true
  detailPayableList.value = []
  try {
    const res = await getPayablePage({ sourceType: 'purchase_order', sourceId: orderId, pageNum: 1, pageSize: 100 })
    detailPayableList.value = res.data?.records || []
  } catch {} finally {
    detailPayableLoading.value = false
  }
}

// ==================== 付款登记 ====================
const makePayVisible = ref(false)
const makePaySubmitting = ref(false)
const makePayForm = reactive({
  payableId: null, payableNo: '',
  amount: 0, paid: 0, unpaid: 0,
  payAmount: 0, paymentDate: new Date().toISOString().slice(0, 10),
  paymentMethod: 'BANK_TRANSFER', remark: ''
})
function openMakePayment(row) {
  const unpaid = Number(row.unpaidAmount != null ? row.unpaidAmount
    : (Number(row.amount || 0) - Number(row.paidAmount || 0)))
  Object.assign(makePayForm, {
    payableId: row.id,
    payableNo: row.payableNo,
    amount: row.amount,
    paid: row.paidAmount || 0,
    unpaid,
    payAmount: unpaid,
    paymentDate: new Date().toISOString().slice(0, 10),
    paymentMethod: 'BANK_TRANSFER',
    remark: ''
  })
  makePayVisible.value = true
}
async function submitMakePayment() {
  if (!makePayForm.payAmount || makePayForm.payAmount <= 0) {
    ElMessage.warning('请输入付款金额'); return
  }
  if (!makePayForm.paymentDate) { ElMessage.warning('请选择付款日期'); return }
  if (!makePayForm.paymentMethod) { ElMessage.warning('请选择付款方式'); return }
  makePaySubmitting.value = true
  try {
    await addPayment({
      relatedType: 'payable',
      relatedId: makePayForm.payableId,
      amount: makePayForm.payAmount,
      paymentDate: makePayForm.paymentDate,
      paymentMethod: makePayForm.paymentMethod,
      remark: makePayForm.remark
    })
    ElMessage.success('付款成功')
    makePayVisible.value = false
    // detail.value.id 是当前打开详情的采购订单 id, 直接 reload
    if (detail.value?.id) loadDetailPayables(detail.value.id)
  } finally { makePaySubmitting.value = false }
}

// ==================== 采购申请单详情（来源申请跳转） ====================
const requestDetailVisible = ref(false)
const requestDetailLoading = ref(false)
const requestDetail = ref({})

async function openRequestDetail(row) {
  if (!row.requestId) {
    ElMessage.warning('该申请记录缺少关联ID，无法查看详情')
    return
  }
  requestDetail.value = {}
  requestDetailLoading.value = true
  requestDetailVisible.value = true
  try {
    const res = await getPurchaseRequestDetail(row.requestId)
    requestDetail.value = res.data || {}
  } catch {
    ElMessage.error('加载采购申请详情失败')
    requestDetailVisible.value = false
  } finally {
    requestDetailLoading.value = false
  }
}

function requestStatusTagType(status) {
  const map = { pending: 'warning', approved: 'success', rejected: 'danger', ordered: '', completed: 'success', cancelled: 'danger' }
  return map[status] || 'info'
}
function requestStatusText(status) {
  const map = { pending: '待审批', approved: '已批准', rejected: '已驳回', ordered: '已下单', completed: '已完成', cancelled: '已取消' }
  return map[status] || status || '-'
}

// ==================== 批量收料 ====================
const batchReceiptItems = ref([])
const batchReceiptLoading = ref(false)

async function onBatchReceiptWarehouseChange(row) {
  row.locationId = undefined
  row.locationOptions = []
  if (!row.warehouseId) return
  try {
    const res = await getWarehouseLocationList(row.warehouseId)
    row.locationOptions = res.data || []
  } catch { row.locationOptions = [] }
}

async function doGenerateReceipts() {
  const pendingItems = batchReceiptItems.value.filter(i =>
    Number(i.quantity || 0) > Number(i.receiptQty || 0) && Number(i.actualQty || 0) > 0
  )
  if (pendingItems.length === 0) { ElMessage.warning('请至少填写一条收料数量'); return }
  batchReceiptLoading.value = true
  try {
    await generatePurchaseReceipts(detail.value.id, {
      items: pendingItems.map(i => ({
        orderItemId: i.id,
        materialId: i.materialId,
        qty: i.actualQty,
        batchNo: i.batchNo || undefined
      }))
    })
    ElMessage.success('收料单生成成功，请前往"采购收料单"页面进行收料操作')
    receiptDetailVisible.value = false
    loadData()
  } finally { batchReceiptLoading.value = false }
}

// ==================== 办结 ====================
function handleClose(row) {
  ElMessageBox.confirm(`确定办结订单「${row.poNo}」？办结后不可恢复。`, '提示', { type: 'warning' })
    .then(async () => {
      await closePurchaseOrder(row.id)
      ElMessage.success('已办结')
      loadData()
    }).catch(() => {})
}

// ==================== 作废 ====================
function handleVoid(row) {
  ElMessageBox.confirm(`确定作废订单「${row.poNo}」吗？作废后不可恢复。`, '提示', { type: 'warning' })
    .then(async () => {
      await voidPurchaseOrder(row.id)
      ElMessage.success('已作废')
      loadData()
    }).catch(() => {})
}

onMounted(() => { loadOptions(); loadData(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card {
  flex: 1;
  .table-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    .selection-info { font-size: 13px; color: #606266; }
  }
}
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
