<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in statCards" :key="s.key">
        <div class="stat-card" @click="s.filterStatus && filterByStatus(s.filterStatus)">
          <el-icon class="icon" :color="s.color"><component :is="s.icon" /></el-icon>
          <div class="info">
            <div class="label">{{ s.label }}</div>
            <div class="value">{{ stats[s.key] || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="加工单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工单编号">
          <el-input v-model="queryParams.workOrderNo" placeholder="工单编号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="queryParams.stepName" placeholder="工序名称" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="queryParams.productName" placeholder="产品名称" clearable style="width: 170px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="queryParams.supplierId" placeholder="全部" clearable filterable style="width: 170px">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="交付日期">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增加工单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never">
      <div style="display: flex; justify-content: flex-end; margin-bottom: 10px">
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'orderNo'">
              <el-button type="primary" link @click="openDetail(row)">{{ row.orderNo }}</el-button>
            </template>
            <template v-else-if="col.key === 'sourceType'">
              <el-tag v-if="row.isFullOutsource" type="warning" size="small">销售订单</el-tag>
              <el-tag v-else size="small">生产工单</el-tag>
            </template>
            <template v-else-if="col.key === 'sourceNo'">
              <el-button v-if="row.sourceNo" type="primary" link size="small" @click="handleViewSource(row)">{{ row.sourceNo }}</el-button>
              <span v-else>-</span>
            </template>
            <template v-else-if="col.key === 'deliveryTaskOutQty'">
              <span style="color: #e6a23c">{{ row.deliveryTaskQty || 0 }}</span> /
              <span style="color: #67c23a">{{ row.deliveryOutQty || 0 }}</span>
            </template>
            <!-- receiptTaskInQty 列已移除，改用外协收料单 -->
            <template v-else-if="col.key === 'totalAmount'">{{ row.totalAmount ? '¥' + Number(row.totalAmount).toFixed(2) : '-' }}</template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
            </template>
            <template v-else-if="col.key === 'deliveryDate'">
              <span :style="{ color: isOverdue(row) ? '#f56c6c' : '' }">{{ row.deliveryDate || '-' }}</span>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'draft'" link size="small" @click="handleEditDraft(row)">编辑</el-button>
            <el-button v-if="row.status === 'draft'" type="primary" link size="small" @click="handleSubmitForApproval(row)">提交审批</el-button>
            <el-button v-if="row.status === 'pending_approval'" type="success" link size="small" @click="handleApprove(row)">审批</el-button>
            <el-button v-if="row.status === 'pending_approval'" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="['confirmed','processing'].includes(row.status)" type="warning" link size="small" @click="openDeliveryDialog(row)">发料</el-button>
            <el-button v-if="['confirmed','processing','partial_receipt'].includes(row.status)" type="info" link size="small" @click="openGenerateReceiptDialog(row)">生成收料单</el-button>
            <el-button v-if="!['completed','cancelled'].includes(row.status)" type="danger" link size="small" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" background
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="`外协加工单 ${detail.orderNo || ''}`" size="750px">
      <template v-if="detail.id">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="加工单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.status)" size="small">{{ statusMap[detail.status] || detail.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ detail.productName }}</el-descriptions-item>
          <el-descriptions-item label="计划数量">{{ detail.quantity }}</el-descriptions-item>
          <el-descriptions-item label="交付日期">{{ detail.deliveryDate }}</el-descriptions-item>
          <el-descriptions-item label="含税单价">¥{{ Number(detail.unitPrice || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ Number(detail.taxRate || 13).toFixed(2) }}%</el-descriptions-item>
          <el-descriptions-item label="不含税单价">¥{{ (Number(detail.unitPrice || 0) / (1 + Number(detail.taxRate || 13) / 100)).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="含税总金额">¥{{ Number(detail.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="发料任务/出库">{{ detail.deliveryTaskQty || 0 }} / {{ detail.deliveryOutQty || 0 }}</el-descriptions-item>
          <!-- 收货任务/入库字段已移除 -->
          <el-descriptions-item label="工艺要求" v-if="detail.processRequirement">{{ detail.processRequirement }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab">
          <!-- 发料记录 -->
          <el-tab-pane label="发料记录" name="delivery">
            <div style="margin-bottom: 8px; text-align: right">
              <el-button v-if="['confirmed','processing'].includes(detail.status)" type="primary" size="small" @click="openDeliveryDialog(detail)">新增发料</el-button>
            </div>
            <el-table :data="deliveryList" size="small" border stripe>
              <el-table-column prop="deliveryNo" label="发料单号" min-width="140" />
              <el-table-column prop="materialName" label="物料" min-width="120" />
              <el-table-column prop="quantity" label="发料数量" width="90" align="right" />
              <el-table-column prop="warehouseName" label="出库仓库" width="100" />
              <el-table-column prop="operatorName" label="操作人" width="80" />
              <el-table-column prop="createTime" label="时间" min-width="155" />
            </el-table>
            <div style="margin-top: 8px; color: #909399; font-size: 13px">
              合计发料: <strong>{{ deliveryList.reduce((s, r) => s + Number(r.quantity || 0), 0) }}</strong>
            </div>
          </el-tab-pane>

          <!-- 收货记录 -->
          <el-tab-pane label="收货记录" name="receipt">
            <div style="margin-bottom: 8px; text-align: right">
              <el-button v-if="['confirmed','processing'].includes(detail.status)" type="primary" size="small" @click="openReceiptDialog(detail)">新增收货</el-button>
            </div>
            <el-table :data="receiptList" size="small" border stripe>
              <el-table-column prop="receiptNo" label="收货单号" min-width="140" />
              <el-table-column prop="actualQty" label="收货数量" width="90" align="right" />
              <el-table-column prop="qualifiedQty" label="合格数量" width="90" align="right">
                <template #default="{ row }">
                  <span :style="{ color: row.qualifiedQty < row.actualQty ? '#f56c6c' : '#67c23a' }">{{ row.qualifiedQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="qcStatus" label="质检状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="qcStatusTag[row.qcStatus]" size="small">{{ qcStatusMap[row.qcStatus] || row.qcStatus || '待检' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="operatorName" label="操作人" width="80" />
              <el-table-column prop="createTime" label="时间" min-width="155" />
            </el-table>
            <div style="margin-top: 8px; color: #909399; font-size: 13px">
              合计收货: <strong>{{ receiptList.reduce((s, r) => s + Number(r.actualQty || 0), 0) }}</strong>
              | 合格: <strong style="color: #67c23a">{{ receiptList.reduce((s, r) => s + Number(r.qualifiedQty || 0), 0) }}</strong>
            </div>
          </el-tab-pane>

          <!-- 质检信息 -->
          <el-tab-pane label="质检信息" name="qc">
            <el-table :data="qcTasks" size="small" border stripe>
              <el-table-column prop="taskNo" label="质检单号" min-width="140" />
              <el-table-column prop="qcType" label="类型" width="80">
                <template #default="{ row }">{{ row.qcType === 'outsource' ? '外协检' : row.qcType }}</template>
              </el-table-column>
              <el-table-column prop="planQty" label="计划数量" width="90" align="right" />
              <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
              <el-table-column prop="defectQty" label="不合格" width="80" align="right">
                <template #default="{ row }">
                  <span :style="{ color: row.defectQty > 0 ? '#f56c6c' : '' }">{{ row.defectQty || 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="result" label="结果" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.result" :type="row.result === 'pass' ? 'success' : 'danger'" size="small">
                    {{ row.result === 'pass' ? '通过' : '不通过' }}
                  </el-tag>
                  <span v-else style="color: #c0c4cc">待检</span>
                </template>
              </el-table-column>
              <el-table-column prop="inspectorName" label="质检员" width="80" />
              <el-table-column prop="inspectTime" label="检验时间" min-width="155" />
            </el-table>
            <el-empty v-if="!qcTasks.length" description="暂无质检记录" />
          </el-tab-pane>

          <!-- 结算信息 -->
          <el-tab-pane label="结算信息" name="settlement">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="合同总额">¥{{ Number(detail.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="合格数量">{{ detail.qualifiedQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="结算金额">
                <span style="color: #e6a23c; font-weight: 600">
                  ¥{{ (Number(detail.unitPrice || 0) * Number(detail.qualifiedQty || 0)).toFixed(2) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="已付金额">
                <span style="color: #67c23a; font-weight: 600">¥{{ Number(detail.paidAmount || 0).toFixed(2) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="待付金额">
                <span style="color: #f56c6c; font-weight: 600">
                  ¥{{ Math.max(0, Number(detail.unitPrice || 0) * Number(detail.qualifiedQty || 0) - Number(detail.paidAmount || 0)).toFixed(2) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="结算状态">
                <el-tag :type="detail.settlementStatus === 'settled' ? 'success' : 'warning'" size="small">
                  {{ detail.settlementStatus === 'settled' ? '已结算' : '未结算' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
            <el-empty v-if="detail.status !== 'completed'" description="加工单完成后可进行结算" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增外协加工单" width="600px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联工单">
          <el-select v-model="form.workOrderId" placeholder="请选择关联工单(可选,选后可联动选工序)" filterable clearable
                     style="width: 100%" @change="onWorkOrderChange">
            <el-option v-for="item in workOrders" :key="item.id"
                       :label="`${item.woNo || item.orderNo || item.workOrderNo}${item.productName ? ' - ' + item.productName : ''}`"
                       :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.workOrderId" label="工序" prop="taskId">
          <el-select v-model="form.taskId" placeholder="请选择该工单下的工序" filterable clearable
                     style="width: 100%" :loading="taskLoading" @change="onTaskChange">
            <el-option v-for="t in taskOptions" :key="t.id"
                       :label="`第${t.stepNo}道 ${t.stepName}${t.qualifiedQty != null ? ' (合格 ' + t.qualifiedQty + '/' + t.planQty + ')' : ''}`"
                       :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" prop="productId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :model-value="form.productName" readonly placeholder="选择关联工单/工序后自动带出,或手动选择" style="flex: 1;" />
            <el-button type="primary" :disabled="!!form.workOrderId" @click="osProductPickerVisible = true">选择产品</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="form.stepName" label="工序名称">
          <el-input :model-value="form.stepName" readonly />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="1" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="含税单价" prop="unitPrice">
              <el-input-number v-model="form.unitPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="交付日期" prop="deliveryDate">
          <el-date-picker v-model="form.deliveryDate" type="date" placeholder="请选择" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="加工要求">
          <el-input v-model="form.processRequirement" type="textarea" :rows="3" placeholder="请输入加工要求" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发料弹窗 -->
    <el-dialog v-model="deliveryDialogVisible" title="外协发料" width="500px" destroy-on-close>
      <el-form ref="deliveryFormRef" :model="deliveryForm" :rules="deliveryRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="deliveryForm.materialId" placeholder="请选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%">
            <el-option v-for="item in materialOptions" :key="item.id"
              :label="`${item.name}${item.spec ? ' [' + $fmtSpec(item.spec) + ']' : ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="出库仓库" prop="warehouseId">
          <el-select v-model="deliveryForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="deliveryForm.quantity" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="deliveryForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliveryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="deliveryLoading" @click="submitDelivery">确认发料</el-button>
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

    <!-- 编辑草稿弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑外协加工单" width="520px" destroy-on-close>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="供应商" required>
          <el-select v-model="editForm.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="含税单价" required>
          <el-input-number v-model="editForm.unitPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="editForm.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交付日期">
          <el-date-picker v-model="editForm.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工艺要求">
          <el-input v-model="editForm.processRequirement" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 关联单据详情弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="sourceDetailVisible" :relatedOrderNo="sourceDetailOrderNo" :invType="sourceDetailInvType" />

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :visible-keys="visibleColumns.map(c=>c.key)" @save="saveConfig" />

    <!-- 产品选择弹窗 -->
    <MaterialPickerDialog v-model="osProductPickerVisible" title="选择产品" :multiple="false"
                          @select="onOsProductSelected" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Document, Loading, CircleCheck, View, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import { getOutsourceOrderPage, createOutsourceOrder, updateOutsourceOrder, submitOutsourceOrder, approveOutsourceOrder, rejectOutsourceOrder, cancelOutsourceOrder, getOutsourceOrderDetail, createOutsourceDelivery, generateOutsourceReceipts } from '@/api/outsource'
import { getSupplierSelect, getMaterialPage } from '@/api/master'
import { getWarehouseSelect } from '@/api/system'
import { getWorkOrderPage, getWorkOrderTasks } from '@/api/production'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

const OUTSOURCE_ORDER_DEFAULT_COLUMNS = [
  { key: 'orderNo', label: '加工单号', minWidth: 175 },
  { key: 'sourceType', label: '来源', minWidth: 90, align: 'center' },
  { key: 'sourceNo', label: '关联单据号', minWidth: 150 },
  { key: 'supplierName', label: '供应商', minWidth: 140 },
  { key: 'productName', label: '产品名称', minWidth: 140 },
  { key: 'quantity', label: '计划数量', minWidth: 90, align: 'right' },
  { key: 'deliveryTaskOutQty', label: '发料任务/出库', minWidth: 120, align: 'center' },
  { key: 'totalAmount', label: '总金额', minWidth: 100, align: 'right' },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
  { key: 'deliveryDate', label: '交付日期', minWidth: 115 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('outsource_order', OUTSOURCE_ORDER_DEFAULT_COLUMNS)

const statusMap = { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', processing: '加工中', partial_receipt: '部分收料', fully_receipt: '全部收料', completed: '已完成', cancelled: '已取消' }
const qcStatusMap = { pending: '待检', pass: '通过', passed: '通过', fail: '不通过', failed: '不通过' }
const qcStatusTag = { pending: 'warning', pass: 'success', passed: 'success', fail: 'danger', failed: 'danger' }
function statusTagType(s) { return { draft: 'info', pending_approval: 'warning', confirmed: 'primary', processing: '', partial_receipt: 'warning', fully_receipt: 'success', completed: 'success', cancelled: 'danger' }[s] || 'info' }
function isOverdue(row) { return row.deliveryDate && !['completed','cancelled'].includes(row.status) && new Date(row.deliveryDate) < new Date() }

const statCards = [
  { key: 'total', label: '全部外协单', color: '#409eff', icon: Document },
  { key: 'pendingApproval', label: '待审批', color: '#e6a23c', icon: Loading, filterStatus: 'pending_approval' },
  { key: 'processing', label: '加工中', color: '#909399', icon: View, filterStatus: 'processing' },
  { key: 'completed', label: '已完成', color: '#67c23a', icon: CircleCheck, filterStatus: 'completed' }
]
const stats = reactive({ total: 0, pendingApproval: 0, processing: 0, completed: 0 })

function filterByStatus(status) { queryParams.status = status; queryParams.pageNum = 1; loadData() }

// 查询
const queryParams = reactive({ keyword: '', status: undefined, supplierId: undefined, workOrderNo: '', stepName: '', productName: '', dateRange: null, pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0)

const supplierOptions = ref([])
const materialOptions = ref([]); const warehouseOptions = ref([]); const workOrders = ref([])

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (params.dateRange && params.dateRange.length === 2) {
      params.startDate = params.dateRange[0]
      params.endDate = params.dateRange[1]
    }
    delete params.dateRange
    const res = await getOutsourceOrderPage(params)
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
    // 简单统计
    stats.total = total.value
    stats.pendingApproval = tableData.value.filter(r => r.status === 'pending_approval').length
    stats.processing = tableData.value.filter(r => r.status === 'processing').length
    stats.completed = tableData.value.filter(r => r.status === 'completed').length
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.keyword = ''; queryParams.status = undefined; queryParams.supplierId = undefined; queryParams.workOrderNo = ''; queryParams.stepName = ''; queryParams.productName = ''; queryParams.dateRange = null; queryParams.pageNum = 1; loadData() }

async function loadOptions() {
  // 物料下拉改 remote 按需搜索;其他保留(数据小)
  try {
    const [supRes, whRes, woRes] = await Promise.all([
      getSupplierSelect(), getWarehouseSelect(),
      getWorkOrderPage({ pageNum: 1, pageSize: 200 }).catch(() => ({ data: { records: [] } }))
    ])
    supplierOptions.value = supRes.data || []
    warehouseOptions.value = whRes.data || []
    workOrders.value = woRes.data?.records || []
  } catch {}
}

const materialSearching = ref(false)
let _matSearchTimer = null
async function searchMaterials(keyword) {
  if (_matSearchTimer) clearTimeout(_matSearchTimer)
  if (!keyword || !keyword.trim()) { materialOptions.value = []; return }
  _matSearchTimer = setTimeout(async () => {
    materialSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      materialOptions.value = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName, spec: m.spec }))
    } catch {} finally { materialSearching.value = false }
  }, 300)
}

// 详情抽屉
const detailVisible = ref(false); const detail = ref({}); const detailTab = ref('delivery')
const deliveryList = ref([]); const receiptList = ref([]); const qcTasks = ref([])

async function openDetail(row) {
  detailTab.value = 'delivery'
  try {
    const res = await getOutsourceOrderDetail(row.id)
    detail.value = res.data || row
    deliveryList.value = detail.value.deliveries || []
    receiptList.value = detail.value.receipts || []
    qcTasks.value = detail.value.qcTasks || []
  } catch { detail.value = row; deliveryList.value = []; receiptList.value = []; qcTasks.value = [] }
  detailVisible.value = true
}

// 新增弹窗
const dialogVisible = ref(false); const formRef = ref(null); const submitLoading = ref(false)
const form = reactive({
  supplierId: undefined, productId: undefined, productName: '', quantity: 1, unitPrice: 0,
  deliveryDate: '', workOrderId: undefined, taskId: undefined, stepName: '',
  processRequirement: '', remark: ''
})

// 产品选择弹窗
const osProductPickerVisible = ref(false)
function onOsProductSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  form.productId = m.id
  form.productName = m.materialName || m.name || ''
}

// 关联工单 → 工序联动
const taskOptions = ref([])
const taskLoading = ref(false)
async function onWorkOrderChange(woId) {
  // 切换工单时清空工序与产品(产品改为由工单/工序带出)
  form.taskId = undefined
  form.stepName = ''
  taskOptions.value = []
  if (!woId) {
    // 清空时,产品也清空,允许用户重新手动选择
    form.productId = undefined
    form.productName = ''
    return
  }
  // 自动带出工单产品
  const wo = workOrders.value.find(w => w.id === woId)
  if (wo) {
    form.productId = wo.productId || undefined
    form.productName = wo.productName || ''
  }
  // 加载该工单的工序列表(只展示外协可承接的工序)
  taskLoading.value = true
  try {
    const res = await getWorkOrderTasks(woId)
    const list = res.data || []
    // 优先展示标记为可外协 / 未完成 的工序;其它也保留以便用户手选
    taskOptions.value = list.filter(t => t.status !== 'cancelled')
  } catch {
    taskOptions.value = []
  } finally {
    taskLoading.value = false
  }
}
function onTaskChange(taskId) {
  const t = taskOptions.value.find(x => x.id === taskId)
  if (t) {
    form.stepName = t.stepName || ''
    // 数量默认带入该工序剩余可外协数量(planQty - qualifiedQty),无则用 planQty
    const plan = Number(t.planQty || 0)
    const qualified = Number(t.qualifiedQty || 0)
    const remain = Math.max(plan - qualified, 0)
    form.quantity = remain > 0 ? remain : (plan > 0 ? plan : 1)
  } else {
    form.stepName = ''
  }
}

const rules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }],
  deliveryDate: [{ required: true, message: '请选择交付日期', trigger: 'change' }]
}
function resetForm() {
  form.supplierId = undefined; form.productId = undefined; form.productName = ''; form.quantity = 1
  form.unitPrice = 0; form.deliveryDate = ''; form.workOrderId = undefined
  form.taskId = undefined; form.stepName = ''
  form.processRequirement = ''; form.remark = ''
  taskOptions.value = []
}
function handleAdd() { resetForm(); dialogVisible.value = true }

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    await createOutsourceOrder({ ...form })
    ElMessage.success('创建成功'); dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

async function handleSubmitForApproval(row) {
  await ElMessageBox.confirm(`提交加工单「${row.orderNo}」进行审批？`, '提交审批', { type: 'info' })
  try { await submitOutsourceOrder(row.id); ElMessage.success('已提交审批'); loadData() } catch {}
}

async function handleApprove(row) {
  await ElMessageBox.confirm(`审批通过加工单「${row.orderNo}」？`, '审批通过', { type: 'success' })
  try { await approveOutsourceOrder(row.id, {}); ElMessage.success('审批通过'); loadData() } catch {}
}

async function handleReject(row) {
  const { value: reason } = await ElMessageBox.prompt('请输入驳回原因：', '驳回加工单', {
    confirmButtonText: '确认驳回', cancelButtonText: '取消',
    inputPattern: /\S+/, inputErrorMessage: '驳回原因不能为空', type: 'warning'
  })
  try { await rejectOutsourceOrder(row.id, { remark: reason }); ElMessage.success('已驳回'); loadData() } catch {}
}

async function handleCancel(row) {
  await ElMessageBox.confirm(`确定取消加工单「${row.orderNo}」？`, '取消加工单', { type: 'warning' })
  try { await cancelOutsourceOrder(row.id); ElMessage.success('已取消'); loadData() } catch {}
}

// 编辑草稿
const editDialogVisible = ref(false)
const editForm = reactive({ supplierId: undefined, unitPrice: 0, taxRate: 13, deliveryDate: '', processRequirement: '', remark: '' })
const editingId = ref(null)
function handleEditDraft(row) {
  editingId.value = row.id
  editForm.supplierId = row.supplierId; editForm.unitPrice = row.unitPrice || 0
  editForm.taxRate = row.taxRate != null ? row.taxRate : 13; editForm.deliveryDate = row.deliveryDate || ''
  editForm.processRequirement = row.processRequirement || ''; editForm.remark = row.remark || ''
  editDialogVisible.value = true
}
async function submitEdit() {
  try {
    await updateOutsourceOrder(editingId.value, { ...editForm })
    ElMessage.success('保存成功'); editDialogVisible.value = false; loadData()
  } catch {}
}

// 查看关联单据
// 关联单据详情弹窗
const sourceDetailVisible = ref(false)
const sourceDetailOrderNo = ref('')
const sourceDetailInvType = ref('')

function handleViewSource(row) {
  if (row.isFullOutsource && row.salesOrderNo) {
    sourceDetailOrderNo.value = row.salesOrderNo
    sourceDetailInvType.value = 'sales_out'
  } else if (row.workOrderNo) {
    sourceDetailOrderNo.value = row.workOrderNo
    sourceDetailInvType.value = 'production_out'
  } else {
    return
  }
  sourceDetailVisible.value = true
}

// 发料弹窗
const deliveryDialogVisible = ref(false); const deliveryFormRef = ref(null); const deliveryLoading = ref(false)
const currentOrderId = ref(null)
const deliveryForm = reactive({ materialId: undefined, warehouseId: undefined, quantity: 1, remark: '' })
const deliveryRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

function openDeliveryDialog(row) {
  currentOrderId.value = row.id
  deliveryForm.materialId = undefined; deliveryForm.warehouseId = 999  // 默认外协中转仓
  deliveryForm.quantity = 1; deliveryForm.remark = ''
  deliveryDialogVisible.value = true
}

async function submitDelivery() {
  await deliveryFormRef.value.validate()
  deliveryLoading.value = true
  try {
    await createOutsourceDelivery(currentOrderId.value, { deliveryQty: deliveryForm.quantity, items: [{ materialId: deliveryForm.materialId, actualQty: deliveryForm.quantity, warehouseId: deliveryForm.warehouseId }] })
    ElMessage.success('发料成功'); deliveryDialogVisible.value = false
    loadData()
    if (detailVisible.value) openDetail({ id: currentOrderId.value })
  } finally { deliveryLoading.value = false }
}

// 生成收料单弹窗
const generateReceiptVisible = ref(false)
const generateReceiptLoading = ref(false)
const generateReceiptRow = ref({})
const generateReceiptForm = reactive({ qty: 1, batchNo: '' })

function openGenerateReceiptDialog(row) {
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
    loadData()
  } finally { generateReceiptLoading.value = false }
}

onMounted(() => { loadOptions(); loadData(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 16px 20px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,.08); cursor: pointer; transition: box-shadow .3s;
  &:hover { box-shadow: 0 4px 12px rgba(0,0,0,.15); }
  .icon { font-size: 36px; }
  .info .label { font-size: 13px; color: #909399; }
  .info .value { font-size: 24px; font-weight: 600; margin-top: 4px; }
}
</style>
