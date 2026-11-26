<template>
  <div class="page-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="售后单号">
          <el-input v-model="queryParams.serviceNo" placeholder="售后单号" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品">
          <el-input v-model="queryParams.productName" placeholder="产品名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="保修">
          <el-select v-model="queryParams.inWarranty" placeholder="全部" clearable style="width: 100px">
            <el-option label="保内" :value="1" />
            <el-option label="保外" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="draft" />
            <el-option label="处理中" value="processing" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" @click="handleCreate">新建售后单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="serviceNo" label="售后单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="salesOrderNo" label="关联订单" min-width="130" show-overflow-tooltip />
        <el-table-column label="保修" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.inWarranty === 1 ? 'success' : 'warning'" size="small">
              {{ row.inWarranty === 1 ? '保内' : '保外' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalPartCost" label="配件成本" width="90" align="right">
          <template #default="{ row }">¥{{ Number(row.totalPartCost || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="totalChargeAmount" label="收费" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.inWarranty === 0" style="color:#e6a23c; font-weight:600">¥{{ Number(row.totalChargeAmount || 0).toFixed(2) }}</span>
            <span v-else style="color:#909399">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理人" width="100" />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 'completed' && row.status !== 'cancelled'" type="warning" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'completed' && row.status !== 'cancelled'" type="danger" link size="small" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新建/编辑 抽屉 -->
    <el-drawer v-model="editVisible" :title="editForm.id ? '编辑售后单' : '新建售后单'" size="780px" destroy-on-close>
      <el-form :model="editForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="客户" required>
              <el-input :model-value="editForm.customerName" readonly placeholder="点右侧选择客户">
                <template #append>
                  <el-button @click="openCustomerPicker">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联订单">
              <el-input :model-value="editForm.salesOrderNo" readonly :placeholder="editForm.customerId ? '可选,点右侧选该客户的订单' : '先选客户'">
                <template #append>
                  <el-button :disabled="!editForm.customerId" @click="openOrderPicker">选择</el-button>
                  <el-button v-if="editForm.salesOrderId" @click="clearOrder">清除</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="产品" required>
              <el-input :model-value="editForm.productName" readonly :placeholder="editForm.salesOrderId ? '点右侧选订单内的产品' : '点右侧选择产品'">
                <template #append>
                  <el-button @click="openProductPicker">选择</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保修状态" required>
              <el-radio-group v-model="editForm.inWarranty">
                <el-radio :label="1">保内</el-radio>
                <el-radio :label="0">保外</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="故障描述">
          <el-input v-model="editForm.issueDescription" type="textarea" :rows="2" placeholder="客户反馈的问题/故障现象" />
        </el-form-item>
        <el-form-item label="处理方式">
          <el-input v-model="editForm.handlingMethod" type="textarea" :rows="2" placeholder="维修/更换/退货 等处理过程" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" placeholder="备注" />
        </el-form-item>
      </el-form>

      <!-- 已保存的售后单才显示配件清单和操作 -->
      <template v-if="editForm.id">
        <el-tabs v-model="detailTab" class="aftersale-tabs" @tab-change="onDetailTabChange">
          <el-tab-pane label="配件清单" name="parts">
            <el-table :data="parts" border size="small" v-loading="partsLoading">
              <el-table-column prop="materialNo" label="物料编号" width="110" />
              <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
              <el-table-column label="规格" min-width="110" show-overflow-tooltip>
                <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="55" align="center" />
              <el-table-column prop="quantity" label="数量" width="80" align="right" />
              <el-table-column v-if="editForm.inWarranty === 0" prop="unitPrice" label="单价" width="80" align="right">
                <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column v-if="editForm.inWarranty === 0" prop="totalPrice" label="金额" width="90" align="right">
                <template #default="{ row }">¥{{ Number(row.totalPrice || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="出库" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.issued ? 'success' : 'info'" size="small">{{ row.issued ? '已出库' : '待出库' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关联车间计划" min-width="140" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.planItemId" type="primary" size="small">{{ row.planNo || ('计划 #' + row.planItemId) }}</el-tag>
                  <el-tag v-else-if="row.workOrderId" type="success" size="small" closable
                          @close="handleLinkWorkOrder(row, null)">工单 #{{ row.workOrderId }}</el-tag>
                  <span v-else style="color: #c0c4cc; font-size: 12px">-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" align="center">
                <template #default="{ row }">
                  <el-button v-if="!row.issued" type="warning" link size="small" @click="openEditPart(row)">编辑</el-button>
                  <el-button v-if="!row.workOrderId && !row.planItemId" type="primary" link size="small" @click="openLinkWorkOrder(row)">关联工单</el-button>
                  <el-button v-if="!row.workOrderId && !row.planItemId" type="success" link size="small" @click="openCreateWorkOrder(row)">转车间计划</el-button>
                  <el-button v-if="!row.issued" type="danger" link size="small" @click="deletePart(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div style="margin-top: 12px; display: flex; gap: 8px">
              <el-button type="primary" plain size="small" @click="openAddPart">+ 添加配件</el-button>
              <el-button type="success" size="small" @click="openIssueParts"
                         :disabled="!parts.some(p => !p.issued)">配件出库</el-button>
              <el-button v-if="editForm.status !== 'completed'" type="primary" size="small" @click="handleComplete">完成售后</el-button>
              <div style="margin-left: auto; line-height: 32px">
                <span style="color:#606266">配件成本: </span>
                <b style="color:#1d4ed8">¥{{ Number(editForm.totalPartCost || 0).toFixed(2) }}</b>
                <template v-if="editForm.inWarranty === 0">
                  <span style="margin-left: 16px; color:#606266">客户收费: </span>
                  <b style="color:#e6a23c">¥{{ Number(editForm.totalChargeAmount || 0).toFixed(2) }}</b>
                </template>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane name="related">
            <template #label>
              关联任务
              <el-badge v-if="(related.invTasks?.length || 0) + (related.workOrders?.length || 0) + (related.productionPlans?.length || 0) > 0"
                        :value="(related.invTasks?.length || 0) + (related.workOrders?.length || 0) + (related.productionPlans?.length || 0)"
                        type="primary" class="rel-badge" />
            </template>
            <div v-loading="relatedLoading">
              <div style="font-weight:600; color:#1d4ed8; margin: 8px 0">配件出库任务</div>
              <el-table :data="related.invTasks || []" border size="small" v-if="(related.invTasks || []).length">
                <el-table-column prop="taskNo" label="任务编号" min-width="140" />
                <el-table-column prop="invType" label="类型" width="100" />
                <el-table-column prop="status" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="invTaskStatusTag(row.status)" size="small">{{ invTaskStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="创建时间" min-width="160">
                  <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="还没有出库任务, 点'配件出库'生成" :image-size="60" />

              <div style="font-weight:600; color:#1d4ed8; margin: 16px 0 8px">关联车间计划</div>
              <el-table :data="related.productionPlans || []" border size="small" v-if="(related.productionPlans || []).length">
                <el-table-column prop="planNo" label="计划编号" min-width="140" />
                <el-table-column prop="forPartName" label="为配件" min-width="130" show-overflow-tooltip />
                <el-table-column prop="planQty" label="计划数" width="80" align="right" />
                <el-table-column prop="releasedQty" label="已下达" width="80" align="right" />
                <el-table-column prop="completedQty" label="完成数" width="80" align="right" />
                <el-table-column prop="planEndDate" label="完成日期" width="110">
                  <template #default="{ row }">{{ row.planEndDate || '-' }}</template>
                </el-table-column>
                <el-table-column prop="planStatus" label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="{ draft:'info', scheduled:'warning', confirmed:'primary', in_progress:'warning', completed:'success', cancelled:'danger' }[row.planStatus] || ''" size="small">
                      {{ { draft:'草稿', scheduled:'待派发', confirmed:'已确认', in_progress:'生产中', completed:'已完成', cancelled:'已取消' }[row.planStatus] || row.planStatus }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="配件需新做时, 可在配件行点'转车间计划'" :image-size="60" />

              <template v-if="(related.workOrders || []).length">
                <div style="font-weight:600; color:#1d4ed8; margin: 16px 0 8px">关联生产工单</div>
                <el-table :data="related.workOrders" border size="small">
                  <el-table-column prop="woNo" label="工单号" min-width="140" />
                  <el-table-column prop="forPartName" label="为配件" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="planQty" label="计划数" width="80" align="right" />
                  <el-table-column prop="completedQty" label="完成数" width="80" align="right" />
                  <el-table-column prop="deliveryDate" label="交货日期" width="120">
                    <template #default="{ row }">{{ row.deliveryDate || '-' }}</template>
                  </el-table-column>
                  <el-table-column prop="status" label="状态" width="100" align="center" />
                </el-table>
              </template>
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>

      <template #footer>
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ editForm.id ? '保存' : '创建' }}</el-button>
      </template>
    </el-drawer>

    <!-- 添加/编辑配件 弹窗 -->
    <el-dialog v-model="partDialogVisible" :title="partForm.id ? '编辑配件' : '添加配件'" width="560px" destroy-on-close>
      <el-form :model="partForm" label-width="90px">
        <el-form-item label="配件" required>
          <el-select v-model="partForm.materialId" filterable remote :remote-method="searchMaterial"
                     placeholder="搜索物料" style="width: 100%" :disabled="!!partForm.id">
            <el-option v-for="m in materialOptions" :key="m.id" :label="m.materialName" :value="m.id">
              <span>{{ m.materialName }}</span>
              <span style="float:right; color:#909399; font-size:12px">{{ m.materialNo }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="partForm.quantity" :min="0.001" :precision="3" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="editForm.inWarranty === 0" label="单价(收费)">
          <el-input-number v-model="partForm.unitPrice" :min="0" :precision="2" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="partForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="partDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="partSaving" @click="submitPart">{{ partForm.id ? '保存' : '添加' }}</el-button>
      </template>
    </el-dialog>

    <!-- 关联工单弹窗 -->
    <el-dialog v-model="linkWoVisible" title="关联生产工单" width="560px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="选择工单">
          <el-select v-model="linkWoId" filterable remote :remote-method="searchWorkOrder"
                     placeholder="按工单号搜索" style="width: 100%">
            <el-option v-for="w in workOrderOptions" :key="w.id" :label="w.woNo" :value="w.id">
              <span>{{ w.woNo }}</span>
              <span style="float:right; color:#909399; font-size:12px">{{ w.productName }} ×{{ w.planQty }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false">
          关联工单仅做溯源标记, 用于售后单详情展示"这个配件由哪张工单新做的".
          工单本身需要单独在生产模块创建/排产/报工.
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="linkWoVisible = false">取消</el-button>
        <el-button type="primary" :loading="linkWoLoading" @click="submitLinkWorkOrder">确认关联</el-button>
      </template>
    </el-dialog>

    <!-- 转车间计划弹窗 -->
    <el-dialog v-model="createWoVisible" title="为配件转车间计划" width="500px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="配件">
          <el-input :model-value="createWoTargetPart?.materialName" disabled />
        </el-form-item>
        <el-form-item label="计划数量">
          <el-input :model-value="createWoTargetPart?.quantity" disabled />
        </el-form-item>
        <el-form-item label="完成日期" required>
          <el-date-picker v-model="createWoDeliveryDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="希望完工的日期" style="width: 100%" />
        </el-form-item>
        <el-alert type="info" :closable="false">
          创建后自动关联到本配件, 计划为草稿状态, 需到 [MRP→车间计划] 排产/下达工单/报工.
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="createWoVisible = false">取消</el-button>
        <el-button type="primary" :loading="createWoLoading" @click="submitCreateWorkOrder">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 配件出库弹窗 -->
    <el-dialog v-model="issueVisible" title="配件出库" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="出库仓库" required>
          <el-select v-model="issueWarehouseId" placeholder="选择仓库" style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName || w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false">
          所有待出库的配件会一次性生成一个 service_out 库存任务, 仓管在库存任务里处理.
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" :loading="issueLoading" @click="submitIssue">确认出库</el-button>
      </template>
    </el-dialog>

    <!-- 客户选择弹窗 -->
    <el-dialog v-model="customerPickerVisible" title="选择客户" width="720px" destroy-on-close append-to-body>
      <div style="display:flex; gap:8px; margin-bottom:12px">
        <el-input v-model="customerPickerKw" placeholder="客户名称 / 联系人" clearable style="width: 260px" @keyup.enter="loadCustomerPicker(1)" />
        <el-button type="primary" @click="loadCustomerPicker(1)">搜索</el-button>
      </div>
      <el-table :data="customerPickerList" v-loading="customerPickerLoading" border highlight-current-row size="small" @row-dblclick="onPickCustomer">
        <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="onPickCustomer(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex; justify-content:flex-end; margin-top:12px">
        <el-pagination v-model:current-page="customerPickerPage" :page-size="customerPickerSize"
                       :total="customerPickerTotal" background layout="total, prev, pager, next"
                       @current-change="loadCustomerPicker" />
      </div>
    </el-dialog>

    <!-- 订单选择弹窗（按客户过滤） -->
    <el-dialog v-model="orderPickerVisible" :title="`选择订单 - ${editForm.customerName}`" width="800px" destroy-on-close append-to-body>
      <div style="display:flex; gap:8px; margin-bottom:12px">
        <el-input v-model="orderPickerKw" placeholder="订单号" clearable style="width: 220px" @keyup.enter="loadOrderPicker(1)" />
        <el-button type="primary" @click="loadOrderPicker(1)">搜索</el-button>
      </div>
      <el-table :data="orderPickerList" v-loading="orderPickerLoading" border highlight-current-row size="small" @row-dblclick="onPickOrder">
        <el-table-column prop="orderNo" label="订单号" min-width="160" />
        <el-table-column label="交货日期" width="120">
          <template #default="{ row }">{{ $fmtDateOnly(row.deliveryDate) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="金额" width="100" align="right">
          <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="onPickOrder(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex; justify-content:flex-end; margin-top:12px">
        <el-pagination v-model:current-page="orderPickerPage" :page-size="orderPickerSize"
                       :total="orderPickerTotal" background layout="total, prev, pager, next"
                       @current-change="loadOrderPicker" />
      </div>
    </el-dialog>

    <!-- 产品选择弹窗（有订单=订单明细; 无订单=全部物料） -->
    <el-dialog v-model="productPickerVisible"
               :title="productPickerMode === 'order' ? `选择产品 - ${editForm.salesOrderNo}` : '选择产品'"
               width="800px" destroy-on-close append-to-body>
      <div v-if="productPickerMode === 'material'" style="display:flex; gap:8px; margin-bottom:12px">
        <el-input v-model="productPickerKw" placeholder="物料名称 / 编号" clearable style="width: 240px" @keyup.enter="loadMaterialPicker(1)" />
        <el-button type="primary" @click="loadMaterialPicker(1)">搜索</el-button>
      </div>
      <el-table :data="productPickerList" v-loading="productPickerLoading" border highlight-current-row size="small" @row-dblclick="onPickProduct">
        <el-table-column :prop="productPickerMode === 'order' ? 'productCode' : 'materialNo'" label="物料编号" width="120" />
        <el-table-column :prop="productPickerMode === 'order' ? 'productName' : 'materialName'" label="物料名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="规格" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column v-if="productPickerMode === 'order'" prop="quantity" label="订单数量" width="90" align="right" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="onPickProduct(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!productPickerLoading && !productPickerList.length"
                :description="productPickerMode === 'order' ? '此订单暂无明细' : '未匹配到物料'" :image-size="60" />
      <div v-if="productPickerMode === 'material'" style="display:flex; justify-content:flex-end; margin-top:12px">
        <el-pagination v-model:current-page="productPickerPage" :page-size="productPickerSize"
                       :total="productPickerTotal" background layout="total, prev, pager, next"
                       @current-change="loadMaterialPicker" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getServiceOrderPage, getServiceOrderDetail, createServiceOrder, updateServiceOrder,
  cancelServiceOrder, completeServiceOrder,
  getServiceOrderParts, addServiceOrderPart, updateServiceOrderPart, deleteServiceOrderPart,
  issueServiceOrderParts,
  linkServiceOrderPartWorkOrder, getServiceOrderRelatedTasks,
  createWorkOrderForServiceOrderPart
} from '@/api/aftersale'
import { getCustomerPage, getMaterialPage } from '@/api/master'
import { getSalesOrderPage, getSalesOrderDetail } from '@/api/order'
import { getWorkOrderPage } from '@/api/production'
import { getWarehouseList } from '@/api/system'

const STATUS_MAP = {
  draft: { label: '草稿', type: 'info' },
  processing: { label: '处理中', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}
function statusLabel(s) { return STATUS_MAP[s]?.label || s || '-' }
function statusTag(s) { return STATUS_MAP[s]?.type || '' }

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  serviceNo: '', customerName: '', productName: '',
  inWarranty: undefined, status: '', pageNum: 1, pageSize: 20
})

async function loadData() {
  loading.value = true
  try {
    const res = await getServiceOrderPage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { serviceNo: '', customerName: '', productName: '', inWarranty: undefined, status: '', pageNum: 1 })
  loadData()
}

// ===== 客户/订单/产品 弹窗选择器 =====
// 客户
const customerPickerVisible = ref(false)
const customerPickerLoading = ref(false)
const customerPickerList = ref([])
const customerPickerKw = ref('')
const customerPickerPage = ref(1)
const customerPickerSize = ref(10)
const customerPickerTotal = ref(0)

function openCustomerPicker() {
  customerPickerKw.value = ''
  customerPickerPage.value = 1
  customerPickerVisible.value = true
  loadCustomerPicker(1)
}
async function loadCustomerPicker(page) {
  if (page) customerPickerPage.value = page
  customerPickerLoading.value = true
  try {
    const res = await getCustomerPage({
      keyword: customerPickerKw.value || undefined,
      pageNum: customerPickerPage.value, pageSize: customerPickerSize.value
    })
    customerPickerList.value = res.data?.records || []
    customerPickerTotal.value = res.data?.total || 0
  } catch { customerPickerList.value = [] } finally { customerPickerLoading.value = false }
}
function onPickCustomer(row) {
  editForm.customerId = row.id
  editForm.customerName = row.customerName
  // 切换客户清空订单/产品
  editForm.salesOrderId = null; editForm.salesOrderNo = ''
  editForm.productId = null; editForm.productNo = ''; editForm.productName = ''; editForm.spec = ''
  customerPickerVisible.value = false
}

// 订单 (按客户过滤)
const orderPickerVisible = ref(false)
const orderPickerLoading = ref(false)
const orderPickerList = ref([])
const orderPickerKw = ref('')
const orderPickerPage = ref(1)
const orderPickerSize = ref(10)
const orderPickerTotal = ref(0)

function openOrderPicker() {
  if (!editForm.customerId) return ElMessage.warning('请先选择客户')
  orderPickerKw.value = ''
  orderPickerPage.value = 1
  orderPickerVisible.value = true
  loadOrderPicker(1)
}
async function loadOrderPicker(page) {
  if (page) orderPickerPage.value = page
  orderPickerLoading.value = true
  try {
    const res = await getSalesOrderPage({
      customerId: editForm.customerId,
      orderNo: orderPickerKw.value || undefined,
      pageNum: orderPickerPage.value, pageSize: orderPickerSize.value
    })
    orderPickerList.value = res.data?.records || []
    orderPickerTotal.value = res.data?.total || 0
  } catch { orderPickerList.value = [] } finally { orderPickerLoading.value = false }
}
function onPickOrder(row) {
  editForm.salesOrderId = row.id
  editForm.salesOrderNo = row.orderNo
  // 切换订单清空产品
  editForm.productId = null; editForm.productNo = ''; editForm.productName = ''; editForm.spec = ''
  orderPickerVisible.value = false
}

// 清除已选订单（保留客户）
function clearOrder() {
  editForm.salesOrderId = null
  editForm.salesOrderNo = ''
  // 订单清空时如果产品来自订单明细,这里不强制清空产品(用户可能要保留原产品)
}

// 产品 (有订单=订单明细; 无订单=全部物料)
const productPickerVisible = ref(false)
const productPickerLoading = ref(false)
const productPickerList = ref([])
const productPickerMode = ref('order')  // 'order' | 'material'
const productPickerKw = ref('')
const productPickerPage = ref(1)
const productPickerSize = ref(10)
const productPickerTotal = ref(0)

async function openProductPicker() {
  productPickerList.value = []
  if (editForm.salesOrderId) {
    productPickerMode.value = 'order'
    productPickerVisible.value = true
    productPickerLoading.value = true
    try {
      const res = await getSalesOrderDetail(editForm.salesOrderId)
      productPickerList.value = res.data?.items || []
    } catch { productPickerList.value = [] } finally { productPickerLoading.value = false }
  } else {
    productPickerMode.value = 'material'
    productPickerKw.value = ''
    productPickerPage.value = 1
    productPickerTotal.value = 0
    productPickerVisible.value = true
    loadMaterialPicker(1)
  }
}
async function loadMaterialPicker(page) {
  if (page) productPickerPage.value = page
  productPickerLoading.value = true
  try {
    const res = await getMaterialPage({
      keyword: productPickerKw.value || undefined,
      pageNum: productPickerPage.value, pageSize: productPickerSize.value
    })
    productPickerList.value = res.data?.records || []
    productPickerTotal.value = res.data?.total || 0
  } catch { productPickerList.value = [] } finally { productPickerLoading.value = false }
}
function onPickProduct(row) {
  if (productPickerMode.value === 'order') {
    editForm.productId = row.productId
    editForm.productNo = row.productCode || ''
    editForm.productName = row.productName || ''
  } else {
    editForm.productId = row.id
    editForm.productNo = row.materialNo || ''
    editForm.productName = row.materialName || ''
  }
  editForm.spec = row.spec || ''
  productPickerVisible.value = false
}

// ===== 配件物料搜索 =====
const materialOptions = ref([])
async function searchMaterial(kw) {
  if (!kw) { materialOptions.value = []; return }
  try {
    const res = await getMaterialPage({ keyword: kw, pageNum: 1, pageSize: 30 })
    materialOptions.value = res.data?.records || []
  } catch { materialOptions.value = [] }
}

// ===== 仓库 =====
const warehouseOptions = ref([])
async function loadWarehouses() {
  try {
    const res = await getWarehouseList()
    warehouseOptions.value = res.data || []
  } catch {}
}

// ===== 编辑/新建 =====
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null, serviceNo: '', customerId: null, customerName: '',
  salesOrderId: null, salesOrderNo: '',
  productId: null, productNo: '', productName: '', spec: '',
  inWarranty: 1, issueDescription: '', handlingMethod: '',
  status: 'draft', handlerId: null, handlerName: '',
  totalPartCost: 0, totalChargeAmount: 0, remark: ''
})

function resetForm() {
  Object.assign(editForm, {
    id: null, serviceNo: '', customerId: null, customerName: '',
    salesOrderId: null, salesOrderNo: '',
    productId: null, productNo: '', productName: '', spec: '',
    inWarranty: 1, issueDescription: '', handlingMethod: '',
    status: 'draft', handlerId: null, handlerName: '',
    totalPartCost: 0, totalChargeAmount: 0, remark: ''
  })
  parts.value = []
  detailTab.value = 'parts'
  related.value = { invTasks: [], workOrders: [], productionPlans: [] }
}

function handleCreate() {
  resetForm()
  editVisible.value = true
}

async function openEdit(row) {
  await openDetail(row)
}
async function openDetail(row) {
  resetForm()
  const res = await getServiceOrderDetail(row.id)
  Object.assign(editForm, res.data)
  parts.value = res.data?.parts || []
  editVisible.value = true
}

async function handleSave() {
  if (!editForm.customerId) return ElMessage.warning('请选择客户')
  if (!editForm.productId) return ElMessage.warning('请选择产品')
  saving.value = true
  try {
    if (editForm.id) {
      await updateServiceOrder(editForm.id, editForm)
      ElMessage.success('已保存')
    } else {
      const res = await createServiceOrder(editForm)
      ElMessage.success('已创建')
      editForm.id = res.data
    }
    loadData()
  } catch {} finally { saving.value = false }
}

async function handleCancel(row) {
  await ElMessageBox.confirm(`确认取消售后单「${row.serviceNo}」?`, '取消售后', { type: 'warning' })
  await cancelServiceOrder(row.id)
  ElMessage.success('已取消')
  loadData()
}

async function handleComplete() {
  await ElMessageBox.confirm('确认完成售后? 完成后不可再编辑', '完成售后', { type: 'info' })
  await completeServiceOrder(editForm.id)
  ElMessage.success('已完成')
  editForm.status = 'completed'
  loadData()
}

// ===== 配件 =====
const parts = ref([])
const partsLoading = ref(false)
const partDialogVisible = ref(false)
const partSaving = ref(false)
const partForm = reactive({ id: null, materialId: null, quantity: 1, unitPrice: 0, remark: '' })

async function refreshParts() {
  if (!editForm.id) return
  partsLoading.value = true
  try {
    const res = await getServiceOrderParts(editForm.id)
    parts.value = res.data || []
    // 重新加载售后单(更新成本/收费)
    const detRes = await getServiceOrderDetail(editForm.id)
    editForm.totalPartCost = detRes.data?.totalPartCost
    editForm.totalChargeAmount = detRes.data?.totalChargeAmount
    // 关联 tab 数据也同步刷
    if (detailTab.value === 'related') refreshRelated()
  } catch {} finally { partsLoading.value = false }
}

function openAddPart() {
  Object.assign(partForm, { id: null, materialId: null, quantity: 1, unitPrice: 0, remark: '' })
  partDialogVisible.value = true
}
function openEditPart(row) {
  Object.assign(partForm, {
    id: row.id, materialId: row.materialId,
    quantity: Number(row.quantity),
    unitPrice: Number(row.unitPrice),
    remark: row.remark
  })
  materialOptions.value = [{ id: row.materialId, materialName: row.materialName, materialNo: row.materialNo }]
  partDialogVisible.value = true
}
async function submitPart() {
  if (!partForm.materialId) return ElMessage.warning('请选择配件')
  partSaving.value = true
  try {
    if (partForm.id) {
      await updateServiceOrderPart(partForm.id, { quantity: partForm.quantity, unitPrice: partForm.unitPrice, remark: partForm.remark })
    } else {
      await addServiceOrderPart(editForm.id, partForm)
    }
    ElMessage.success('已保存')
    partDialogVisible.value = false
    refreshParts()
  } catch {} finally { partSaving.value = false }
}
async function deletePart(row) {
  await ElMessageBox.confirm(`删除配件「${row.materialName}」?`, '提示', { type: 'warning' })
  await deleteServiceOrderPart(row.id)
  refreshParts()
}

// ===== 关联工单 / 关联任务 tab =====
const detailTab = ref('parts')
const related = ref({ invTasks: [], workOrders: [] })
const relatedLoading = ref(false)

function invTaskStatusLabel(s) {
  return ({ pending: '待处理', partial: '部分处理', completed: '已完成', cancelled: '已取消' })[s] || s
}
function invTaskStatusTag(s) {
  return ({ pending: 'info', partial: 'warning', completed: 'success', cancelled: 'danger' })[s] || ''
}

async function onDetailTabChange(name) {
  if (name === 'related') await refreshRelated()
}
async function refreshRelated() {
  if (!editForm.id) return
  relatedLoading.value = true
  try {
    const res = await getServiceOrderRelatedTasks(editForm.id)
    related.value = res.data || { invTasks: [], workOrders: [] }
  } catch {} finally { relatedLoading.value = false }
}

const linkWoVisible = ref(false)
const linkWoLoading = ref(false)
const linkWoId = ref(null)
const linkWoTargetPart = ref(null)
const workOrderOptions = ref([])

async function searchWorkOrder(kw) {
  if (!kw) { workOrderOptions.value = []; return }
  try {
    const res = await getWorkOrderPage({ woNo: kw, pageNum: 1, pageSize: 30 })
    workOrderOptions.value = res.data?.records || []
  } catch { workOrderOptions.value = [] }
}

function openLinkWorkOrder(part) {
  linkWoTargetPart.value = part
  linkWoId.value = part.workOrderId || null
  workOrderOptions.value = []
  linkWoVisible.value = true
}
async function submitLinkWorkOrder() {
  if (!linkWoId.value) return ElMessage.warning('请选择工单')
  linkWoLoading.value = true
  try {
    await linkServiceOrderPartWorkOrder(linkWoTargetPart.value.id, linkWoId.value)
    ElMessage.success('已关联')
    linkWoVisible.value = false
    refreshParts()
  } catch {} finally { linkWoLoading.value = false }
}
async function handleLinkWorkOrder(part, workOrderId) {
  // 用于 tag close 解除关联
  if (workOrderId === null) {
    await ElMessageBox.confirm(`解除工单 #${part.workOrderId} 与配件 ${part.materialName} 的关联?`, '提示', { type: 'warning' })
  }
  await linkServiceOrderPartWorkOrder(part.id, workOrderId)
  refreshParts()
}

// ===== 新建工单 =====
const createWoVisible = ref(false)
const createWoLoading = ref(false)
const createWoTargetPart = ref(null)
const createWoDeliveryDate = ref('')

function openCreateWorkOrder(part) {
  createWoTargetPart.value = part
  createWoDeliveryDate.value = new Date(Date.now() + 7 * 86400_000).toISOString().slice(0, 10)
  createWoVisible.value = true
}
async function submitCreateWorkOrder() {
  if (!createWoDeliveryDate.value) return ElMessage.warning('请选择交货日期')
  createWoLoading.value = true
  try {
    const res = await createWorkOrderForServiceOrderPart(createWoTargetPart.value.id, {
      deliveryDate: createWoDeliveryDate.value
    })
    ElMessage.success('已创建车间计划并关联到该配件')
    createWoVisible.value = false
    refreshParts()
  } catch {} finally { createWoLoading.value = false }
}

// ===== 出库 =====
const issueVisible = ref(false)
const issueLoading = ref(false)
const issueWarehouseId = ref(null)
function openIssueParts() {
  issueWarehouseId.value = null
  issueVisible.value = true
}
async function submitIssue() {
  if (!issueWarehouseId.value) return ElMessage.warning('请选择仓库')
  issueLoading.value = true
  try {
    const res = await issueServiceOrderParts(editForm.id, { warehouseId: issueWarehouseId.value })
    ElMessage.success(`已生成出库任务, 处理 ${res.data || 0} 个配件`)
    issueVisible.value = false
    refreshParts()
  } catch {} finally { issueLoading.value = false }
}

onMounted(() => { loadData(); loadWarehouses() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 12px; }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
