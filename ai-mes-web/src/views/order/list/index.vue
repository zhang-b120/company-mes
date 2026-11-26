<template>
  <div class="page-container">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="4" v-for="card in statCards" :key="card.key">
        <el-card shadow="hover" class="stat-card"
          :class="{ active: isStatCardActive(card), clickable: card.action !== 'none', disabled: card.action === 'none' }"
          @click="onStatCardClick(card)">
          <div class="stat-value" :style="{ color: card.color }">{{ stats[card.key] ?? '-' }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="订单号" clearable @keyup.enter="handleSearch" style="width: 150px" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable @keyup.enter="handleSearch" style="width: 150px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务员">
          <el-select v-model="queryParams.salesmanId" placeholder="全部" clearable filterable style="width: 140px">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.name || u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker v-model="queryParams.dateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
        </el-form-item>
        <!-- 扩展字段查询条件 (订单头) -->
        <template v-for="fc in searchFieldConfigs" :key="fc.fieldKey">
          <el-form-item :label="fc.fieldLabel">
            <el-date-picker v-if="fc.fieldType === 'date'"
              v-model="queryParams.extFields[fc.fieldKey]" type="date"
              value-format="YYYY-MM-DD" :placeholder="`${fc.fieldLabel}`" style="width: 160px" clearable />
            <el-select v-else-if="fc.fieldType === 'select'"
              v-model="queryParams.extFields[fc.fieldKey]" :placeholder="`全部`" clearable style="width: 140px">
              <el-option v-for="opt in parseOptions(fc.fieldOptions)" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-input v-else v-model="queryParams.extFields[fc.fieldKey]"
              :placeholder="`${fc.fieldLabel}`" clearable style="width: 140px"
              @keyup.enter="handleSearch" />
          </el-form-item>
        </template>
        <!-- 扩展字段查询条件 (订单明细行级) -->
        <template v-for="fc in itemSearchFieldConfigs" :key="'item-' + fc.fieldKey">
          <el-form-item :label="`明细·${fc.fieldLabel}`">
            <el-date-picker v-if="fc.fieldType === 'date'"
              v-model="queryParams.itemExtFields[fc.fieldKey]" type="date"
              value-format="YYYY-MM-DD" :placeholder="`${fc.fieldLabel}`" style="width: 160px" clearable />
            <el-select v-else-if="fc.fieldType === 'select'"
              v-model="queryParams.itemExtFields[fc.fieldKey]" :placeholder="`全部`" clearable style="width: 140px">
              <el-option v-for="opt in itemExtFields.parseOptions(fc.fieldOptions)" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-input v-else v-model="queryParams.itemExtFields[fc.fieldKey]"
              :placeholder="`${fc.fieldLabel}`" clearable style="width: 140px"
              @keyup.enter="handleSearch" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="info" plain @click="advancedDrawerVisible = true">复杂查询</el-button>
        </el-form-item>
      </el-form>
      <div class="action-bar">
        <el-button v-permission="'order:add'" type="primary" @click="$router.push('/order/create')">新建订单</el-button>
        <el-button type="success" plain @click="aiOrderVisible = true">AI接单</el-button>
        <el-button :icon="Download" @click="handleExport()">{{ selectedRows.length > 0 ? `导出选中(${selectedRows.length})` : '导出全部' }}</el-button>
        <el-button :icon="Upload" @click="importVisible = true">导入</el-button>
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        <!-- 批量操作 -->
        <template v-if="selectedRows.length > 0">
          <el-divider direction="vertical" />
          <span style="color: #909399; font-size: 13px">已选 {{ selectedRows.length }} 项</span>
          <el-button v-permission="'order:edit'" type="primary" plain size="small" @click="handleBatchSubmit">批量提交</el-button>
          <el-button v-permission="'order:delete'" type="danger" plain size="small" @click="handleBatchDelete">批量删除</el-button>
          <el-button v-permission="'order:void'" type="warning" plain size="small" @click="handleBatchVoid">批量作废</el-button>
          <el-button v-if="selectedRows.some(r => r.status === 'confirmed' && r.masterPlanAdded !== 1)" type="primary" plain size="small" @click="handleBatchToPlanning">批量转车间计划</el-button>
        </template>
      </div>
    </el-card>

    <!-- 表格区域 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe
        max-height="calc(100vh - 320px)"
        @selection-change="handleSelectionChange" @expand-change="handleExpandChange" ref="tableRef" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-items-wrapper" :style="{ width: expandWidth + 'px' }">
              <div class="expand-items-toolbar">
                <el-button :icon="Setting" size="small" link @click="expandColConfigRef.open()">明细列配置</el-button>
              </div>
              <div class="expand-items-scroll">
                <table class="expand-items-table" border="1" cellspacing="0" cellpadding="0">
                  <thead>
                    <tr>
                      <th style="width:50px">#</th>
                      <th v-for="col in visibleItemColumns" :key="col.key" :style="{ width: (col.width || 110) + 'px' }">{{ col.label }}</th>
                    </tr>
                  </thead>
                  <tbody v-if="row._items?.length">
                    <tr v-for="(item, idx) in row._items" :key="idx">
                      <td>{{ idx + 1 }}</td>
                      <td v-for="col in visibleItemColumns" :key="col.key"
                          :title="renderExpandCellTitle(item, col)"
                          :style="cellStyle(col)">
                        <span v-html="renderExpandCell(item, col)"></span>
                      </td>
                    </tr>
                  </tbody>
                  <tbody v-else-if="row._itemsLoading">
                    <tr><td :colspan="visibleItemColumns.length + 1" style="text-align:center;color:#909399;padding:16px">加载中...</td></tr>
                  </tbody>
                  <tbody v-else>
                    <tr><td :colspan="visibleItemColumns.length + 1" style="text-align:center;color:#c0c4cc;padding:16px">暂无明细</td></tr>
                  </tbody>
                </table>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="selection" width="45" align="center" />
        <!-- 动态列：由列配置控制顺序和显隐 -->
        <el-table-column
          v-for="col in visibleColumns" :key="col.key"
          :label="col.label"
          :min-width="col.minWidth || 100"
          :align="col.align || 'left'"
          :show-overflow-tooltip="col.key !== 'orderNo'">
          <template #default="{ row }">
            <template v-if="col.key === 'orderNo'">
              <span v-if="isOverdue(row)" class="badge-overdue">逾</span>
              <span v-if="isDelayed(row)" class="badge-delayed" title="交货时间已延期">延</span>
              <span v-if="row.hasActiveChange" class="badge-change" title="该订单经过变更">变</span>
              <span v-if="isUnpaid(row)" class="badge-unpaid" title="未回款">欠</span>
              <el-button link type="primary" @click="goDetail(row)">{{ row.orderNo }}</el-button>
            </template>
            <template v-else-if="col.key === 'customerName'">
              <el-button link type="primary" @click="goCustomer(row)">{{ row.customerName }}</el-button>
            </template>
            <template v-else-if="col.key === 'productSummary'">
              {{ row.itemCount ? `${row.itemCount}项产品` : '-' }}
            </template>
            <template v-else-if="col.key === 'totalAmount'">
              <span style="font-weight: 500">{{ row.totalAmount != null ? `¥${Number(row.totalAmount).toFixed(2)}` : '-' }}</span>
            </template>
            <template v-else-if="col.key === 'paymentAmount'">
              <span style="color: #67c23a">{{ row.paymentAmount != null ? `¥${Number(row.paymentAmount).toFixed(2)}` : '-' }}</span>
            </template>
            <template v-else-if="col.key === 'invoiceAmount'">
              <span style="color: #e6a23c">{{ row.invoiceAmount != null && Number(row.invoiceAmount) > 0 ? `¥${Number(row.invoiceAmount).toFixed(2)}` : '-' }}</span>
            </template>
            <template v-else-if="col.key === 'deliveryDate'">
              <span :class="{ 'overdue-text': isOverdue(row) }">
                <el-icon v-if="isOverdue(row)" style="color: #f56c6c; vertical-align: middle; margin-right: 2px"><WarningFilled /></el-icon>
                {{ row.deliveryDate || '-' }}
              </span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" :effect="['cancelled','voided'].includes(row.status) ? 'plain' : 'light'">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
            <template v-else-if="col.key === 'productionProgress'">
              <el-progress v-if="!['draft','pending_approval','cancelled','voided'].includes(row.status)"
                :percentage="row.productionProgress || 0" :stroke-width="14" :text-inside="true"
                :color="progressColor(row.productionProgress || 0)" />
              <span v-else style="color: #c0c4cc">-</span>
            </template>
            <template v-else-if="col.key === 'createTime'">
              {{ (row.createTime || '').substring(0, 10) }}
            </template>
            <template v-else-if="col.type === 'extension'">
              {{ getExtField(row, col.fieldKey) }}
            </template>
            <template v-else>
              {{ row[col.key] || '-' }}
            </template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="400" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goDetail(row)">查看</el-button>
            <el-button v-permission="'order:edit'" link type="primary" v-if="row.status === 'draft'" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" v-if="row.status === 'draft'" @click="handleSubmit(row)">提交</el-button>
            <el-button v-permission="'order:approve'" link type="warning" v-if="row.status === 'pending_approval'" @click="handleApprove(row)">审批</el-button>
            <el-button link type="info"
              v-if="row.status === 'confirmed'"
              :disabled="row.masterPlanAdded === 1"
              @click="handleAddToMasterPlan(row)">
              {{ row.masterPlanAdded === 1 ? '已加入主计划' : '转主计划' }}
            </el-button>
            <el-button link type="primary" v-if="row.status === 'confirmed' && row.masterPlanAdded !== 1" @click="handleToPlanning(row)">转车间计划</el-button>
            <el-button link type="warning" v-if="row.status === 'confirmed' && row.masterPlanAdded !== 1" @click="handleToOutsource(row)">转外协</el-button>
            <el-button link type="success" v-if="['in_production','outsourcing','partial_shipped'].includes(row.status)" @click="handleComplete(row)">完成</el-button>
            <el-button link type="info" v-if="!['settled','cancelled','voided','draft'].includes(row.status)" @click="handleChange(row)">变更</el-button>
            <el-button v-permission="'order:void'" link type="warning" v-if="!['cancelled','voided','settled','shipped'].includes(row.status)" @click="handleVoid(row)">作废</el-button>
            <el-button v-permission="'order:delete'" link type="danger" v-if="row.status === 'draft'" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 复杂查询抽屉 -->
    <el-drawer v-model="advancedDrawerVisible" title="复杂查询" direction="rtl" size="400px" destroy-on-close>
      <el-form :model="advancedForm" label-width="90px" label-position="top">
        <el-form-item label="订单号">
          <el-input v-model="advancedForm.orderNo" placeholder="模糊搜索" clearable />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="advancedForm.customerId" placeholder="全部" clearable filterable style="width: 100%">
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="advancedForm.status" placeholder="全部" clearable style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务员">
          <el-select v-model="advancedForm.salesmanId" placeholder="全部" clearable filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.name || u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建人">
          <el-select v-model="advancedForm.createBy" placeholder="全部" clearable filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.name || u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单金额">
          <div style="display: flex; gap: 8px; align-items: center">
            <el-input-number v-model="advancedForm.totalAmountMin" :min="0" :precision="2" placeholder="最小" controls-position="right" style="flex: 1" />
            <span>-</span>
            <el-input-number v-model="advancedForm.totalAmountMax" :min="0" :precision="2" placeholder="最大" controls-position="right" style="flex: 1" />
          </div>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="advancedForm.dateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交货日期">
          <el-date-picker v-model="advancedForm.deliveryDateRange" type="daterange" range-separator="至"
            start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="advancedForm.productName" placeholder="模糊搜索" clearable />
        </el-form-item>
        <el-form-item label="产品编码">
          <el-input v-model="advancedForm.productCode" placeholder="模糊搜索" clearable />
        </el-form-item>
      </el-form>
      <div style="display: flex; gap: 8px; padding-top: 16px; border-top: 1px solid #ebeef5">
        <el-button type="primary" style="flex: 1" @click="doAdvancedSearch">查询</el-button>
        <el-button style="flex: 1" @click="resetAdvancedSearch">重置</el-button>
      </div>
    </el-drawer>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="formVisible" :title="formData.id ? '编辑订单' : '新增订单'" width="900px" destroy-on-close>
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="formData.customerId" placeholder="请选择客户" filterable style="width: 100%"
                @change="onCustomerChange">
                <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="formData.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收货地址" prop="shippingAddress">
              <el-input v-model="formData.shippingAddress" placeholder="请输入收货地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结算方式" prop="settlementType">
              <el-select v-model="formData.settlementType" placeholder="请选择或输入新结算方式" filterable allow-create
                default-first-option style="width: 100%">
                <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务员">
              <el-select v-model="formData.salesmanId" placeholder="请选择业务员" filterable clearable style="width: 100%">
                <el-option v-for="u in userOptions" :key="u.id" :label="u.name || u.realName" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交货日期">
              <el-date-picker v-model="formData.deliveryDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
          <!-- 动态扩展字段 -->
          <template v-for="fc in formFieldConfigs" :key="fc.fieldKey">
            <el-col :span="12">
              <el-form-item :label="fc.fieldLabel" :prop="`extFields.${fc.fieldKey}`"
                :rules="fc.required ? [{ required: true, message: `请填写${fc.fieldLabel}` }] : []">
                <el-date-picker v-if="fc.fieldType === 'date'"
                  v-model="formData.extFields[fc.fieldKey]" type="date"
                  placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
                <el-input-number v-else-if="fc.fieldType === 'number'"
                  v-model="formData.extFields[fc.fieldKey]" :precision="2"
                  controls-position="right" style="width: 100%" />
                <el-select v-else-if="fc.fieldType === 'select'"
                  v-model="formData.extFields[fc.fieldKey]" placeholder="请选择" style="width: 100%">
                  <el-option v-for="opt in parseOptions(fc.fieldOptions)" :key="opt" :label="opt" :value="opt" />
                </el-select>
                <el-input v-else v-model="formData.extFields[fc.fieldKey]" :placeholder="`请输入${fc.fieldLabel}`" />
              </el-form-item>
            </el-col>
          </template>
        </el-row>
        <!-- 信用额度预警 -->
        <el-alert v-if="creditWarning" type="warning" :closable="false" show-icon style="margin-bottom: 16px">
          <template #title>
            客户信用额度预警：信用额度 ¥{{ creditInfo.creditLimit }}，当前应收 ¥{{ creditInfo.currentOutstanding }}，
            本单 ¥{{ creditInfo.orderAmount }}，合计 ¥{{ creditInfo.newTotal }}，已超限
          </template>
        </el-alert>
        <el-divider content-position="left">订单明细</el-divider>
        <el-table :data="formData.items" border size="small">
          <el-table-column label="产品编号" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.productId">{{ getProductNoById(row.productId) || '-' }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="产品" min-width="180">
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="请选择产品" filterable remote
                         :remote-method="searchProducts" :loading="productSearching"
                         size="small" style="width: 100%">
                <el-option v-for="p in productOptions" :key="p.id" :label="p.materialName" :value="p.id" />
              </el-select>
              <!-- AI 识别但系统未匹配到的产品名提示, 引导用户手选/新建 -->
              <div v-if="!row.productId && row._aiName" style="color:#e6a23c;font-size:11px;margin-top:2px;line-height:1.3">
                ⚠ AI识别: {{ row._aiName }}{{ row._aiSpec ? ' / ' + row._aiSpec : '' }}(系统无, 请手选或新建)
              </div>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="1" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="换算数量" width="120" align="center">
            <template #default="{ row }">
              <span v-if="secondaryDisplay(row.quantity, row.unitConfig || getUnitConfigByProductId(row.productId))" style="color: #409eff; font-size: 13px">
                {{ secondaryDisplay(row.quantity, row.unitConfig || getUnitConfigByProductId(row.productId)) }}
              </span>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">
              <span style="color: #e6a23c; font-weight: 500">¥{{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="formData.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 10px; display: flex; justify-content: space-between; align-items: center">
          <el-button type="primary" plain size="small" @click="addItem">+ 添加产品行</el-button>
          <div style="font-size: 14px; font-weight: 500">
            合计金额：<span style="color: #e6a23c; font-size: 18px">¥{{ totalAmount }}</span>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button plain :loading="submitLoading" @click="handleFormSubmit('draft')">保存草稿</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit('submit')">提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveVisible" title="订单审批" width="480px" destroy-on-close>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="订单号">{{ approveForm.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ approveForm.customerName }}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ Number(approveForm.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.remark" type="textarea" :rows="3" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="danger" :loading="approveLoading" @click="doReject">驳回</el-button>
        <el-button type="primary" :loading="approveLoading" @click="doApprove">通过</el-button>
      </template>
    </el-dialog>

    <!-- 订单变更弹窗 -->
    <el-dialog v-model="changeVisible" title="发起订单变更" width="640px" destroy-on-close>
      <el-form :model="changeForm" :rules="changeRules" ref="changeFormRef" label-width="100px">
        <el-form-item label="变更类型" prop="changeType">
          <el-radio-group v-model="changeForm.changeType">
            <el-radio value="qty_increase">数量增加</el-radio>
            <el-radio value="qty_decrease">数量减少</el-radio>
            <el-radio value="date_change">交期变更</el-radio>
            <el-radio value="cancel">订单取消</el-radio>
            <el-radio value="product_replace">产品替换</el-radio>
            <el-radio value="add_product">新增产品行</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="变更说明" prop="description">
          <el-input v-model="changeForm.description" type="textarea" :rows="4" placeholder="请详细描述变更内容和原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeVisible = false">取消</el-button>
        <el-button type="primary" :loading="changeLoading" @click="submitChange">提交变更</el-button>
      </template>
    </el-dialog>

    <!-- AI接单弹窗 -->
    <el-dialog v-model="aiOrderVisible" title="AI智能接单" width="750px" destroy-on-close>
      <el-steps :active="aiStep" align-center style="margin-bottom: 24px">
        <el-step title="上传/输入" />
        <el-step title="AI解析" />
        <el-step title="确认结果" />
      </el-steps>
      <div v-if="aiStep === 0">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
          支持上传图纸(JPG/PNG/PDF)、Excel报价单、或直接粘贴文字描述，AI将自动解析生成订单草稿。
        </el-alert>
        <el-upload ref="aiUploadRef" :auto-upload="false" multiple :limit="3"
          accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png"
          :on-change="onAiFileChange" :on-remove="onAiFileRemove" :file-list="aiFileList"
          style="margin-bottom: 12px">
          <el-button plain><el-icon style="margin-right:4px"><Upload /></el-icon>上传文件</el-button>
          <template #tip><div class="el-upload__tip">支持 PDF / Word / Excel / 图片，最多3个</div></template>
        </el-upload>
        <el-divider style="margin: 8px 0">或粘贴文字</el-divider>
        <el-input v-model="aiInput" type="textarea" :rows="5"
          placeholder="请粘贴订单信息，如：&#10;客户：某某科技有限公司&#10;产品：304不锈钢法兰 DN50 数量100件 单价15元 交期3月15日" />
      </div>
      <div v-if="aiStep === 1" style="text-align: center; padding: 40px 0">
        <el-icon class="is-loading" :size="48" color="#409eff"><Loading /></el-icon>
        <p style="margin-top: 16px; color: #909399">AI正在解析订单信息...</p>
      </div>
      <div v-if="aiStep === 2">
        <div v-if="aiOrderList.length === 0" style="text-align:center;color:#909399;padding:24px">未识别到订单</div>
        <div v-for="(g, gi) in aiOrderList" :key="gi" class="ai-order-group" :class="{ active: aiSelectedIdx === gi }"
          @click="aiSelectedIdx = gi">
          <div class="ai-order-group-head">
            <el-radio :model-value="aiSelectedIdx" :label="gi" @click.stop="aiSelectedIdx = gi" style="margin-right:8px" />
            <span class="ai-order-group-no">{{ g.orderNo || '(自动生成)' }}</span>
            <span class="ai-order-group-cust">{{ g.customer?.name || '未识别' }}</span>
            <el-tag size="small"
              :type="(g.customer?.confidence ?? 0) > 0.9 ? 'success' : (g.customer?.confidence ?? 0) > 0.6 ? 'warning' : 'danger'">
              {{ ((g.customer?.confidence ?? 0) * 100).toFixed(0) }}%
            </el-tag>
            <span class="ai-order-group-meta">{{ (g.items || []).length }} 项 · 共 ¥{{ Number(g.totalAmount || 0).toFixed(2) }}</span>
          </div>
          <el-table :data="g.items || []" border size="small" style="margin-top: 6px">
            <el-table-column prop="productName" label="产品名称" />
            <el-table-column label="规格">
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="80" align="center" />
            <el-table-column prop="unitPrice" label="单价" width="90" align="right">
              <template #default="{ row }">¥{{ row.unitPrice }}</template>
            </el-table-column>
            <el-table-column prop="deliveryDate" label="交期" width="110" />
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="aiOrderVisible = false; aiStep = 0; aiFileList = []">取消</el-button>
        <el-button v-if="aiStep === 0" type="primary" :disabled="!aiInput.trim() && aiFileList.length === 0" @click="doAiParse">开始识别</el-button>
        <el-button v-if="aiStep === 2" plain @click="aiStep = 0">重新识别</el-button>
        <el-button v-if="aiStep === 2 && aiOrderList.length > 0" type="primary" @click="confirmAiOrder">
          确认创建当前订单{{ aiOrderList.length > 1 ? `(${aiSelectedIdx + 1}/${aiOrderList.length})` : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importVisible" title="导入订单" width="500px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        请先下载导入模板，按格式填写后上传。每行填写完整，客户名称+联系人+收货地址+订单交货日期相同的行合并为同一张订单。
      </el-alert>
      <el-upload ref="importUploadRef" drag accept=".xlsx,.xls" :auto-upload="false" :limit="1"
        :on-change="(file) => importFile = file.raw">
        <el-icon :size="48"><Upload /></el-icon>
        <div>将文件拖到此处，或<em>点击上传</em></div>
        <template #tip><div class="el-upload__tip">仅支持 .xlsx / .xls 文件</div></template>
      </el-upload>
      <template #footer>
        <el-button @click="handleDownloadTemplate">下载模板</el-button>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!importFile" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 缺失物料确认弹窗 -->
    <el-dialog v-model="missingDialogVisible" title="以下物料编号未找到，请补充信息后创建" width="680px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
        导入文件中以下产品编号在系统中不存在，请填写物料名称和单位后批量创建，然后将自动重新导入。
      </el-alert>
      <el-table :data="missingMaterials" border size="small" max-height="400">
        <el-table-column prop="materialNo" label="物料编号" width="160" />
        <el-table-column label="物料名称(必填)">
          <template #default="{ row }">
            <el-input v-model="row.materialName" placeholder="请输入物料名称" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="单位(必填)" width="150">
          <template #default="{ row }">
            <el-select v-model="row.unit" placeholder="选择单位" size="small" filterable>
              <el-option v-for="u in missingUnitOptions" :key="u.value" :label="u.label" :value="u.value" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="missingDialogVisible = false">取消(跳过)</el-button>
        <el-button type="primary" :loading="missingLoading" @click="handleCreateMissingAndReimport">创建物料并重新导入</el-button>
      </template>
    </el-dialog>

    <!-- 列配置弹窗 -->
    <ColumnConfigDialog
      ref="colConfigDialogRef"
      :all-columns="allColumns"
      :default-columns="ORDER_DEFAULT_COLUMNS"
      @save="saveConfig"
    />

    <!-- 展开行(订单明细)列配置弹窗 — 拖拽调整位置, 行级扩展字段也在里面 -->
    <ColumnConfigDialog
      ref="expandColConfigRef"
      :all-columns="allItemColumns"
      :default-columns="ORDER_ITEM_DEFAULT_COLUMNS"
      @save="saveItemConfig"
    />

    <!-- 转外协弹窗 -->
    <el-dialog v-model="outsourceVisible" title="整单转外协" width="780px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <template #title>整单外协：将订单所有产品交由外协供应商加工，系统将自动创建外协加工单。发料时按BOM自动展开原材料出库。</template>
      </el-alert>
      <el-form :model="outsourceForm" label-width="100px" style="margin-bottom: 12px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="外协供应商" required>
              <el-select v-model="outsourceForm.supplierId" placeholder="请选择供应商" style="width: 100%" filterable>
                <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="税率(%)">
              <el-input-number v-model="outsourceForm.taxRate" :min="0" :max="100" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="交付日期">
              <el-date-picker v-model="outsourceForm.deliveryDate" type="date" value-format="YYYY-MM-DD"
                placeholder="默认沿用订单交期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="outsourceForm.remark" placeholder="外协备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <h4 style="margin: 0 0 8px 0">订单明细（可调整外协单价）</h4>
      <el-table :data="outsourceItems" border size="small">
        <el-table-column prop="productName" label="产品" min-width="160" show-overflow-tooltip />
        <el-table-column label="规格" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column label="外协单价" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small"
              controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            {{ row.unitPrice != null && row.quantity != null ? `¥${(row.unitPrice * row.quantity).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="deliveryDate" label="交货日期" width="110" align="center" />
      </el-table>
      <div style="text-align: right; margin-top: 8px; font-size: 14px; color: #606266">
        合计金额：<span style="font-weight: 700; color: #f56c6c; font-size: 16px">¥{{ outsourceItemsTotal }}</span>
        <span style="margin-left: 8px; color: #909399; font-size: 12px">(含税，税率{{ outsourceForm.taxRate || 0 }}%)</span>
      </div>
      <template #footer>
        <el-button @click="outsourceVisible = false">取消</el-button>
        <el-button type="primary" :loading="outsourceLoading" @click="doConvertOutsource">确认转外协</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Upload, Loading, WarningFilled, Setting } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import { formatSpec } from '@/utils/format'
import {
  getSalesOrderPage, getSalesOrderDetail, getSalesOrderStatistics,
  createSalesOrder, updateSalesOrder, submitSalesOrder,
  approveSalesOrder, rejectSalesOrder, cancelSalesOrder,
  deleteSalesOrder, voidSalesOrder, completeSalesOrder, convertSalesOrderToPlan, convertSalesOrderToProductionPlan,
  exportSalesOrders, checkCreditLimit, createOrderChange, getOrderChanges,
  batchSubmitOrders, batchDeleteOrders, batchVoidOrders, batchConvertToWorkOrder,
  importSalesOrders, downloadOrderImportTemplate,
  batchConvertToOutsource
} from '@/api/order'
import { addOrderToMasterPlan } from '@/api/masterPlan'
import { useExtFields } from '@/composables/useExtFields'
import { getCustomerSelect, getMaterialSelect, getMaterialPage, getSupplierSelect, batchCreateMaterials, getUnitSelect } from '@/api/master'
import { getUserSelect, getSettlementTypes, saveSettlementTypes } from '@/api/system'
import { getMaterialStocks } from '@/api/inventory'
import { aiParseOrder } from '@/api/ai'
import { useAiChatStore } from '@/store/ai-chat'

const router = useRouter()
const aiChatStore = useAiChatStore()

// 多单位换算显示
function secondaryDisplay(qty, unitConfig) {
  if (!qty || !unitConfig) return ''
  try {
    const cfg = typeof unitConfig === 'string' ? JSON.parse(unitConfig) : unitConfig
    const c = cfg.conversions?.[0]
    if (c) return `${(qty * c.ratio).toFixed(2)} ${c.unit}`
    const units = cfg.units
    if (units && units.length > 1) {
      const sub = units.find(u => !u.isBase)
      if (sub) return `${(qty * sub.rate).toFixed(2)} ${sub.unit}`
    }
  } catch {}
  return ''
}

function getUnitConfigByProductId(productId) {
  if (!productId) return ''
  const p = productOptions.value.find(item => item.id === productId)
  return p?.unitConfig || ''
}

function getProductNoById(productId) {
  if (!productId) return ''
  const p = productOptions.value.find(item => item.id === productId)
  return p?.materialNo || p?.productNo || ''
}

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'salesOrder' && data.records?.length) {
    const rec = data.records[0]
    Object.keys(formData).forEach(k => { if (k !== 'items' && rec[k] !== undefined) formData[k] = rec[k] })
    if (rec.items && Array.isArray(rec.items)) { formData.items = rec.items }
    formVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入订单表单，请确认后提交')
  }
})

// ==================== 统计卡片 ====================
const stats = ref({})
const statCards = [
  { key: 'todayCount', label: '今日新增', color: '#409eff', filterStatus: undefined, action: 'today' },
  { key: 'pendingApproval', label: '待审批', color: '#e6a23c', filterStatus: 'pending_approval', action: 'status' },
  { key: 'inProduction', label: '生产中', color: '#409eff', filterStatus: 'in_production', action: 'status' },
  { key: 'pendingShip', label: '待发货', color: '#67c23a', filterStatus: 'completed,partial_shipped', action: 'status' },
  { key: 'overdue', label: '逾期', color: '#f56c6c', filterStatus: undefined, action: 'overdue' },
  { key: 'totalAmount', label: '本月金额', color: '#e6a23c', filterStatus: undefined, action: 'none' }
]

async function loadStats() {
  try {
    const res = await getSalesOrderStatistics()
    const d = res.data || {}
    stats.value = {
      todayCount: d.todayCount ?? 0,
      pendingApproval: d.pendingApproval ?? 0,
      inProduction: d.inProduction ?? 0,
      pendingShip: d.pendingShip ?? 0,
      overdue: d.overdue ?? 0,
      totalAmount: d.monthlyAmount != null ? `¥${(d.monthlyAmount / 10000).toFixed(1)}万` : '¥0'
    }
  } catch {}
}

function filterByStatus(status) {
  if (status) {
    queryParams.status = queryParams.status === status ? '' : status
    handleSearch()
  }
}

// ==================== 统计卡点击 ====================
function isStatCardActive(card) {
  if (card.action === 'status') return queryParams.status === card.filterStatus
  if (card.action === 'today') return !!queryParams._todayOnly
  if (card.action === 'overdue') return !!queryParams.overdueOnly
  return false
}

function onStatCardClick(card) {
  if (card.action === 'none') return
  if (card.action === 'status') return filterByStatus(card.filterStatus)
  if (card.action === 'today') {
    if (queryParams._todayOnly) {
      // 取消筛选
      queryParams._todayOnly = false
      queryParams.dateRange = null
    } else {
      queryParams._todayOnly = true
      queryParams.overdueOnly = false
      const today = new Date()
      const y = today.getFullYear(), m = String(today.getMonth() + 1).padStart(2, '0'), d = String(today.getDate()).padStart(2, '0')
      const dStr = `${y}-${m}-${d}`
      queryParams.dateRange = [dStr, dStr]
    }
    handleSearch()
    return
  }
  if (card.action === 'overdue') {
    queryParams.overdueOnly = !queryParams.overdueOnly
    if (queryParams.overdueOnly) queryParams._todayOnly = false
    handleSearch()
    return
  }
}

// ==================== 状态选项 ====================
const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '待审批', value: 'pending_approval' },
  { label: '已确认', value: 'confirmed' },
  { label: '已转计划', value: 'mrp_planned' },
  { label: '外协中', value: 'outsourcing' },
  { label: '生产中', value: 'in_production' },
  { label: '已完成', value: 'completed' },
  { label: '待发货', value: 'completed,partial_shipped' },
  { label: '部分发货', value: 'partial_shipped' },
  { label: '已发货', value: 'shipped' },
  { label: '已结算', value: 'settled' },
  { label: '已取消', value: 'cancelled' },
  { label: '已驳回', value: 'rejected' },
  { label: '已作废', value: 'voided' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))

function statusLabel(status) { return statusMap[status] || status || '-' }

function statusTagType(status) {
  const map = {
    draft: 'info', pending_approval: 'warning', confirmed: '', mrp_planned: 'primary',
    outsourcing: 'warning', in_production: '', partial_shipped: 'success', shipped: 'success',
    completed: 'success', settled: 'success', cancelled: 'danger', rejected: 'danger', voided: 'danger'
  }
  return map[status] || 'info'
}

function progressColor(pct) {
  if (pct >= 100) return '#67c23a'
  if (pct >= 50) return '#409eff'
  return '#e6a23c'
}

function isOverdue(row) {
  if (!row.deliveryDate || ['settled', 'cancelled', 'voided', 'shipped', 'completed'].includes(row.status)) return false
  return new Date(row.deliveryDate) < new Date()
}

// 延期: 当前交期晚于原始交期(因工单变更等被推后过)
function isDelayed(row) {
  if (!row.deliveryDate || !row.originalDeliveryDate) return false
  return new Date(row.deliveryDate) > new Date(row.originalDeliveryDate)
}

// 未回款: 已确认及之后状态(非取消) + paymentAmount < totalAmount
function isUnpaid(row) {
  if (!row.totalAmount || Number(row.totalAmount) <= 0) return false
  if (['draft','pending_approval','rejected','cancelled','voided','settled'].includes(row.status)) return false
  return Number(row.paymentAmount || 0) < Number(row.totalAmount)
}

// ==================== 查询与表格 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const tableRef = ref(null)
const expandWidth = ref(800)
const selectedRows = ref([])
const queryParams = reactive({
  orderNo: '', customerName: '', status: '', salesmanId: null, dateRange: null, overdueOnly: false, _todayOnly: false,
  pageNum: 1, pageSize: 20,
  extFields: {},
  itemExtFields: {}
})

function handleSelectionChange(rows) { selectedRows.value = rows }

async function loadData() {
  loading.value = true
  try {
    const params = {
      orderNo: queryParams.orderNo || undefined,
      customerName: queryParams.customerName || undefined,
      status: queryParams.status || undefined,
      salesmanId: queryParams.salesmanId || undefined,
      startDate: queryParams.dateRange?.[0] || undefined,
      endDate: queryParams.dateRange?.[1] || undefined,
      overdueOnly: queryParams.overdueOnly ? true : undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    // 附加扩展字段查询条件
    if (queryParams.extFields) {
      Object.entries(queryParams.extFields).forEach(([k, v]) => {
        if (v !== null && v !== undefined && v !== '') params[`ext_${k}`] = v
      })
    }
    // 附加订单明细行级扩展字段查询条件 (后端用 EXISTS 子查询匹配)
    if (queryParams.itemExtFields) {
      Object.entries(queryParams.itemExtFields).forEach(([k, v]) => {
        if (v !== null && v !== undefined && v !== '') params[`extItem_${k}`] = v
      })
    }
    const res = await getSalesOrderPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.orderNo = ''; queryParams.customerName = ''; queryParams.status = ''; queryParams.salesmanId = null
  queryParams.dateRange = null; queryParams.extFields = {}; queryParams.itemExtFields = {}; queryParams.pageNum = 1; loadData()
}

// ==================== 展开行加载明细 ====================
async function handleExpandChange(row, expandedRows) {
  if (expandedRows.includes(row)) {
    // 计算展开区域可用宽度（外层表格宽度 - 展开列缩进）
    await nextTick()
    const el = tableRef.value?.$el
    if (el) { expandWidth.value = el.clientWidth - 80 }
    if (!row._items) {
      row._itemsLoading = true
      try {
        const res = await getSalesOrderDetail(row.id)
        row._items = res.data?.items || []
        // 批量加载产品库存
        const ids = row._items.map(i => i.productId).filter(Boolean)
        if (ids.length) {
          try {
            const stockRes = await getMaterialStocks(ids)
            const map = stockRes.data || {}
            row._items.forEach(i => {
              const s = map[i.productId]
              if (s) i.availableQty = s.availableQty ?? s.totalQty
            })
          } catch {}
        }
      } catch { row._items = [] }
      finally { row._itemsLoading = false }
    }
  }
}

// ==================== 复杂查询 ====================
const advancedDrawerVisible = ref(false)
const advancedForm = reactive({
  orderNo: '', customerId: null, status: '', salesmanId: null,
  createBy: null, totalAmountMin: null, totalAmountMax: null,
  dateRange: null, deliveryDateRange: null,
  productName: '', productCode: ''
})

function doAdvancedSearch() {
  advancedDrawerVisible.value = false
  queryParams.pageNum = 1
  loading.value = true
  const params = {
    orderNo: advancedForm.orderNo || undefined,
    customerId: advancedForm.customerId || undefined,
    status: advancedForm.status || undefined,
    salesmanId: advancedForm.salesmanId || undefined,
    createBy: advancedForm.createBy || undefined,
    totalAmountMin: advancedForm.totalAmountMin || undefined,
    totalAmountMax: advancedForm.totalAmountMax || undefined,
    startDate: advancedForm.dateRange?.[0] || undefined,
    endDate: advancedForm.dateRange?.[1] || undefined,
    deliveryDateRange: advancedForm.deliveryDateRange ? advancedForm.deliveryDateRange.join(',') : undefined,
    productName: advancedForm.productName || undefined,
    productCode: advancedForm.productCode || undefined,
    pageNum: 1,
    pageSize: queryParams.pageSize
  }
  getSalesOrderPage(params).then(res => {
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  }).finally(() => { loading.value = false })
}

function resetAdvancedSearch() {
  advancedForm.orderNo = ''; advancedForm.customerId = null; advancedForm.status = ''
  advancedForm.salesmanId = null; advancedForm.createBy = null
  advancedForm.totalAmountMin = null; advancedForm.totalAmountMax = null
  advancedForm.dateRange = null; advancedForm.deliveryDateRange = null
  advancedForm.productName = ''; advancedForm.productCode = ''
}

// ==================== 导航 ====================
function goDetail(row, action) {
  const query = action ? { action } : {}
  router.push({ path: `/order/detail`, query: { id: row.id, ...query } })
}
function goCustomer(row) {
  if (row.customerId) router.push({ path: `/order/customer/detail`, query: { id: row.customerId, from: 'salesOrder' } })
}

// ==================== 导出 ====================
async function handleExport() {
  const ids = selectedRows.value.length > 0 ? selectedRows.value.map(r => r.id) : []
  const label = ids.length > 0 ? `导出选中的 ${ids.length} 条订单` : '导出全部订单'
  try {
    await ElMessageBox.confirm(label + '？', '导出确认', { type: 'info', confirmButtonText: '确定导出', cancelButtonText: '取消' })
  } catch { return }
  try {
    const res = await exportSalesOrders(ids)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = '销售订单.xlsx'; link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {}
}

// ==================== 下拉选项 ====================
const customerOptions = ref([])
const productOptions = ref([])
const userOptions = ref([])
const settlementTypeOptions = ref(['现结', '月结30天', '月结60天', '月结90天'])
async function loadSelectOptions() {
  // 物料/产品下拉数据量大,改为按需 remote 搜索;客户/用户/结算方式保留
  try {
    const [cusRes, userRes, stRes] = await Promise.all([
      getCustomerSelect(), getUserSelect(), getSettlementTypes()
    ])
    customerOptions.value = cusRes.data || []
    userOptions.value = userRes.data || []
    if (stRes.data?.configValue) settlementTypeOptions.value = JSON.parse(stRes.data.configValue)
  } catch {}
}

// 产品 remote 搜索 + 按 id 单条加载(用于回显)
const productSearching = ref(false)
let _prodSearchTimer = null
async function searchProducts(keyword) {
  if (_prodSearchTimer) clearTimeout(_prodSearchTimer)
  if (!keyword || !keyword.trim()) { productOptions.value = []; return }
  _prodSearchTimer = setTimeout(async () => {
    productSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      const list = res.data?.records || []
      // 合并:去重保留已选项+新搜索结果
      const map = new Map()
      productOptions.value.forEach(p => map.set(p.id, p))
      list.forEach(p => map.set(p.id, p))
      productOptions.value = Array.from(map.values())
    } catch {} finally { productSearching.value = false }
  }, 300)
}

// 按 ID 集合加载产品(用于编辑回显)
async function loadProductsByIds(ids) {
  const need = ids.filter(id => id && !productOptions.value.find(p => p.id === id))
  if (!need.length) return
  try {
    const res = await getMaterialPage({ pageNum: 1, pageSize: need.length, ids: need.join(',') })
    const list = res.data?.records || []
    const map = new Map()
    productOptions.value.forEach(p => map.set(p.id, p))
    list.forEach(p => map.set(p.id, p))
    productOptions.value = Array.from(map.values())
  } catch {}
}
// ==================== 扩展字段配置 ====================
const {
  listFieldConfigs, formFieldConfigs, searchFieldConfigs,
  loadFieldConfigs, getExtFieldValue, parseOptions, initExtFields, serializeExtFields
} = useExtFields('sales_order')
// 行级扩展字段配置 (订单明细) — 用于搜索表单
const itemExtFields = useExtFields('sales_order_item')
const itemSearchFieldConfigs = itemExtFields.searchFieldConfigs

function getExtField(row, key) { return getExtFieldValue(row, key) }

// ==================== 列配置 ====================
const ORDER_DEFAULT_COLUMNS = [
  { key: 'orderNo',            label: '订单号',   type: 'standard', minWidth: 200 },
  { key: 'customerName',       label: '客户名称', type: 'standard', minWidth: 140 },
  { key: 'productSummary',     label: '产品摘要', type: 'standard', minWidth: 140 },
  { key: 'totalAmount',        label: '总金额',   type: 'standard', minWidth: 110, align: 'right' },
  { key: 'paymentAmount',      label: '回款金额', type: 'standard', minWidth: 110, align: 'right' },
  { key: 'invoiceAmount',      label: '发票金额', type: 'standard', minWidth: 110, align: 'right' },
  { key: 'deliveryDate',       label: '交期',     type: 'standard', minWidth: 110, align: 'center' },
  { key: 'status',             label: '状态',     type: 'standard', minWidth: 110, align: 'center' },
  { key: 'productionProgress', label: '生产进度', type: 'standard', minWidth: 130, align: 'center' },
  { key: 'salesmanName',       label: '业务员',   type: 'standard', minWidth: 100 },
  { key: 'createByName',       label: '创建人',   type: 'standard', minWidth: 100 },
  { key: 'createTime',         label: '创建日期', type: 'standard', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('sales_order', ORDER_DEFAULT_COLUMNS)

// 展开行(订单明细)列配置 — 标准列 + 行级扩展字段, 支持拖拽排序+显隐
const ORDER_ITEM_DEFAULT_COLUMNS = [
  { key: 'productName',  label: '产品名称',     type: 'standard', width: 160 },
  { key: 'productCode',  label: '物料编号',     type: 'standard', width: 160 },
  { key: 'spec',         label: '规格',         type: 'standard', width: 130 },
  { key: 'quantity',     label: '数量',         type: 'standard', width: 80,  align: 'right' },
  { key: 'unit',         label: '单位',         type: 'standard', width: 60,  align: 'center' },
  { key: 'unitPrice',    label: '含税单价',     type: 'standard', width: 100, align: 'right' },
  { key: 'amount',       label: '含税金额',     type: 'standard', width: 110, align: 'right' },
  { key: 'availableQty', label: '可用库存',     type: 'standard', width: 90,  align: 'right' },
  { key: 'producedQty',  label: '已生产',       type: 'standard', width: 80,  align: 'right' },
  { key: 'shippedQty',   label: '已发货',       type: 'standard', width: 80,  align: 'right' },
  { key: 'remark',       label: '备注',         type: 'standard', width: 140 }
]
const expandColConfigRef = ref(null)
const { visibleColumns: visibleItemColumns, allColumns: allItemColumns,
        loadColumnConfig: loadItemColumnConfig, saveConfig: saveItemConfig
      } = useColumnConfig('sales_order_items_expanded', ORDER_ITEM_DEFAULT_COLUMNS)

// 展开行单元格内容渲染 - 标准列按 key 取对应字段, 扩展列从 extFields 取
function renderExpandCell(item, col) {
  if (col.type === 'extension') {
    return escapeHtml(itemExtFields.getExtFieldValue(item, col.fieldKey))
  }
  switch (col.key) {
    case 'productName':  return escapeHtml(item.productName || '-')
    case 'productCode':  return escapeHtml(item.productCode || '-')
    case 'spec':         return escapeHtml(item.spec ? formatSpec(item.spec) : '-')
    case 'quantity':     return item.quantity ?? '-'
    case 'unit':         return escapeHtml(item.unit || '-')
    case 'unitPrice':    return item.unitPrice != null ? `¥${Number(item.unitPrice).toFixed(2)}` : '-'
    case 'amount':       return item.amount != null ? `¥${Number(item.amount).toFixed(2)}` : '-'
    case 'availableQty': return item.availableQty ?? '-'
    case 'producedQty':  return item.producedQty ?? '-'
    case 'shippedQty':   return item.shippedQty ?? '-'
    case 'remark':       return escapeHtml(item.remark || '-')
    default:             return '-'
  }
}
function renderExpandCellTitle(item, col) {
  if (col.type === 'extension') return itemExtFields.getExtFieldValue(item, col.fieldKey)
  if (['spec', 'productName', 'productCode', 'remark'].includes(col.key)) return item[col.key] || ''
  return ''
}
function cellStyle(col) {
  const align = col.align || 'left'
  return { textAlign: align }
}
function escapeHtml(s) {
  if (s == null) return '-'
  return String(s).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]))
}

async function addSettlementType(val) {
  if (val && !settlementTypeOptions.value.includes(val)) {
    settlementTypeOptions.value.push(val)
    try { await saveSettlementTypes(settlementTypeOptions.value) } catch {}
  }
}

// ==================== 新增/编辑 ====================
const formVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null, customerId: null, contactName: '', shippingAddress: '',
  settlementType: '', salesmanId: null, deliveryDate: '', remark: '', items: [],
  extFields: {}
})
const formRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  settlementType: [{ required: true, message: '请选择结算方式', trigger: 'change' }]
}

const creditWarning = ref(false)
const creditInfo = ref({})

const totalAmount = computed(() => {
  return formData.items.reduce((sum, item) => sum + (item.quantity || 0) * (item.unitPrice || 0), 0).toFixed(2)
})

function onCustomerChange(customerId) {
  const cus = customerOptions.value.find(c => c.id === customerId)
  if (cus) {
    formData.contactName = cus.contactName || formData.contactName
    formData.shippingAddress = cus.address || formData.shippingAddress
    formData.settlementType = cus.settlementType || formData.settlementType
    checkCredit(customerId)
  }
}

async function checkCredit(customerId) {
  try {
    const res = await checkCreditLimit(customerId, totalAmount.value)
    const d = res.data || {}
    if (d.isOverLimit) {
      creditWarning.value = true
      creditInfo.value = d
    } else {
      creditWarning.value = false
    }
  } catch { creditWarning.value = false }
}

function resetForm() {
  formData.id = null; formData.customerId = null; formData.contactName = ''
  formData.shippingAddress = ''; formData.settlementType = ''; formData.salesmanId = null
  formData.deliveryDate = ''; formData.remark = ''; formData.items = []
  formData.extFields = {}; creditWarning.value = false
}

function handleEdit(row) {
  // 跳转到独立编辑页面
  router.push(`/order/edit?id=${row.id}`)
}

function addItem() {
  formData.items.push({ productId: null, quantity: 1, unitPrice: 0, deliveryDate: '', unitConfig: '' })
}

async function handleFormSubmit(mode) {
  await formRef.value.validate()
  if (formData.items.length === 0) { ElMessage.warning('请至少添加一个产品行'); return }
  if (creditWarning.value && mode === 'submit') {
    try {
      await ElMessageBox.confirm('客户信用额度已超限，确定仍然提交审批？', '信用超限提示', { type: 'warning', confirmButtonText: '仍然提交' })
    } catch { return }
  }
  submitLoading.value = true
  try {
    const payload = {
      customerId: formData.customerId, contactName: formData.contactName,
      shippingAddress: formData.shippingAddress, settlementType: formData.settlementType,
      salesmanId: formData.salesmanId, deliveryDate: formData.deliveryDate || undefined,
      remark: formData.remark, items: formData.items,
      extFields: serializeExtFields(formData.extFields)
    }
    if (formData.id) {
      await updateSalesOrder(formData.id, payload)
      if (mode === 'submit') await submitSalesOrder(formData.id)
      ElMessage.success(mode === 'submit' ? '已提交审批' : '更新成功')
    } else {
      const res = await createSalesOrder(payload)
      if (mode === 'submit' && res.data?.id) await submitSalesOrder(res.data.id)
      ElMessage.success(mode === 'submit' ? '已创建并提交审批' : '创建成功')
    }
    if (formData.settlementType) addSettlementType(formData.settlementType)
    formVisible.value = false; loadData(); loadStats()
  } finally { submitLoading.value = false }
}

// ==================== 提交 ====================
async function handleSubmit(row) {
  await ElMessageBox.confirm(`确认提交订单 ${row.orderNo} 进行审批？`, '提交确认', { type: 'info' })
  try { await submitSalesOrder(row.id); ElMessage.success('提交成功'); loadData(); loadStats() } catch {}
}

// ==================== 审批 ====================
const approveVisible = ref(false)
const approveLoading = ref(false)
const approveForm = reactive({ id: null, orderNo: '', customerName: '', totalAmount: 0, remark: '' })

function handleApprove(row) {
  approveForm.id = row.id; approveForm.orderNo = row.orderNo
  approveForm.customerName = row.customerName; approveForm.totalAmount = row.totalAmount
  approveForm.remark = ''; approveVisible.value = true
}

async function doApprove() {
  approveLoading.value = true
  try { await approveSalesOrder(approveForm.id, approveForm.remark); ElMessage.success('审批通过'); approveVisible.value = false; loadData(); loadStats() }
  finally { approveLoading.value = false }
}

async function handleAddToMasterPlan(row) {
  if (row.masterPlanAdded === 1) {
    ElMessage.info('该订单已加入主计划')
    return
  }
  try {
    await ElMessageBox.confirm(`确认将订单 ${row.orderNo} 加入"主生产计划"?`, '提示', { type: 'info' })
    await addOrderToMasterPlan(row.id)
    ElMessage.success('已加入主计划')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}

async function doReject() {
  if (!approveForm.remark) { ElMessage.warning('请输入驳回原因'); return }
  approveLoading.value = true
  try { await rejectSalesOrder(approveForm.id, approveForm.remark); ElMessage.success('已驳回'); approveVisible.value = false; loadData(); loadStats() }
  finally { approveLoading.value = false }
}

// ==================== 转计划单 ====================
async function handleConvertToPlan(row) {
  await ElMessageBox.confirm(`确定将订单 ${row.orderNo} 转为计划单？将派发到物料需求计划进行MRP运算。`, '转计划单', { type: 'info' })
  try { await convertSalesOrderToPlan(row.id); ElMessage.success('已转计划单'); loadData(); loadStats() } catch {}
}

// ==================== 转外协 ====================
const outsourceVisible = ref(false)
const outsourceLoading = ref(false)
const outsourceItems = ref([])
const supplierOptions = ref([])
const outsourceForm = reactive({ supplierId: null, taxRate: 13, deliveryDate: null, remark: '' })
const outsourceItemsTotal = computed(() => {
  return outsourceItems.value.reduce((sum, item) => sum + (item.unitPrice || 0) * (item.quantity || 0), 0).toFixed(2)
})
let outsourceOrderId = null

async function handleToOutsource(row) {
  outsourceOrderId = row.id
  outsourceForm.supplierId = null
  outsourceForm.taxRate = 13
  outsourceForm.deliveryDate = null
  outsourceForm.remark = ''
  // 加载订单明细
  try {
    const res = await getSalesOrderDetail(row.id)
    const items = res.data?.items || []
    outsourceItems.value = items.map(item => ({
      productId: item.productId, productName: item.productName, spec: item.spec || '-',
      quantity: item.quantity, unitPrice: item.unitPrice || 0,
      deliveryDate: item.deliveryDate, orderItemId: item.id
    }))
  } catch { outsourceItems.value = [] }
  // 加载供应商
  try {
    const res = await getSupplierSelect()
    supplierOptions.value = res.data || []
  } catch { supplierOptions.value = [] }
  outsourceVisible.value = true
}

async function doConvertOutsource() {
  if (!outsourceForm.supplierId) { ElMessage.warning('请选择外协供应商'); return }
  outsourceLoading.value = true
  try {
    const unitPrices = {}
    for (const item of outsourceItems.value) {
      unitPrices[item.orderItemId] = item.unitPrice
    }
    await batchConvertToOutsource({
      ids: [outsourceOrderId],
      supplierId: outsourceForm.supplierId,
      taxRate: outsourceForm.taxRate,
      deliveryDate: outsourceForm.deliveryDate,
      remark: outsourceForm.remark,
      unitPrices
    })
    ElMessage.success('已成功转为整单外协')
    outsourceVisible.value = false
    loadData()
    loadStats()
  } catch {} finally { outsourceLoading.value = false }
}

// ==================== 删除 ====================
async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除草稿订单 ${row.orderNo}？此操作不可恢复。`, '删除确认', { type: 'warning' })
  try { await deleteSalesOrder(row.id); ElMessage.success('删除成功'); loadData(); loadStats() } catch {}
}

// ==================== 作废 ====================
async function handleVoid(row) {
  const { value: reason } = await ElMessageBox.prompt(`确定作废订单 ${row.orderNo}？请输入作废原因：`, '作废订单',
    { confirmButtonText: '确定作废', cancelButtonText: '返回', inputPattern: /\S+/, inputErrorMessage: '作废原因不能为空', type: 'warning' })
  try { await voidSalesOrder(row.id, reason); ElMessage.success('订单已作废'); loadData(); loadStats() } catch {}
}

// ==================== 完成 ====================
async function handleComplete(row) {
  await ElMessageBox.confirm(`确定标记订单 ${row.orderNo} 为已完成？`, '订单完成', { type: 'info' })
  try { await completeSalesOrder(row.id); ElMessage.success('订单已完成'); loadData(); loadStats() } catch {}
}

// ==================== 批量操作 ====================
async function handleBatchSubmit() {
  const draftRows = selectedRows.value.filter(r => r.status === 'draft')
  if (draftRows.length === 0) { ElMessage.warning('请选择草稿状态的订单'); return }
  await ElMessageBox.confirm(`确定批量提交 ${draftRows.length} 个草稿订单？`, '批量提交', { type: 'info' })
  try { await batchSubmitOrders(draftRows.map(r => r.id)); ElMessage.success('批量提交成功'); loadData(); loadStats() } catch {}
}

async function handleBatchDelete() {
  const draftRows = selectedRows.value.filter(r => r.status === 'draft')
  if (draftRows.length === 0) { ElMessage.warning('请选择草稿状态的订单'); return }
  await ElMessageBox.confirm(`确定批量删除 ${draftRows.length} 个草稿订单？此操作不可恢复。`, '批量删除', { type: 'warning' })
  try { await batchDeleteOrders(draftRows.map(r => r.id)); ElMessage.success('批量删除成功'); loadData(); loadStats() } catch {}
}

async function handleBatchVoid() {
  const validRows = selectedRows.value.filter(r => !['cancelled', 'voided', 'settled', 'shipped'].includes(r.status))
  if (validRows.length === 0) { ElMessage.warning('没有可作废的订单'); return }
  const { value: reason } = await ElMessageBox.prompt(`确定批量作废 ${validRows.length} 个订单？`, '批量作废',
    { inputPattern: /\S+/, inputErrorMessage: '原因不能为空', type: 'warning' })
  try { await batchVoidOrders(validRows.map(r => r.id), reason); ElMessage.success('批量作废成功'); loadData(); loadStats() } catch {}
}

async function handleToPlanning(row) {
  await ElMessageBox.confirm(`将订单【${row.orderNo}】直接生成车间计划（跳过MRP运算），确定继续？`, '转车间计划', { type: 'info' })
  try {
    await convertSalesOrderToProductionPlan(row.id)
    ElMessage.success('已成功生成生产计划')
    loadData(); loadStats()
  } catch {}
}

async function handleBatchToPlanning() {
  const confirmedRows = selectedRows.value.filter(r => r.status === 'confirmed' && r.masterPlanAdded !== 1)
  if (confirmedRows.length === 0) { ElMessage.warning('请选择已确认状态且未加入主计划的订单'); return }
  await ElMessageBox.confirm(`将 ${confirmedRows.length} 个已确认订单直接生成车间计划（跳过MRP运算），确定继续？`, '批量转车间计划', { type: 'info' })
  try {
    await Promise.all(confirmedRows.map(r => convertSalesOrderToProductionPlan(r.id)))
    ElMessage.success(`已成功生成 ${confirmedRows.length} 个生产计划`)
    loadData(); loadStats()
  } catch {}
}

async function handleBatchToPlan() {
  const confirmedRows = selectedRows.value.filter(r => r.status === 'confirmed' && r.masterPlanAdded !== 1)
  if (confirmedRows.length === 0) { ElMessage.warning('请选择已确认状态且未加入主计划的订单'); return }
  await ElMessageBox.confirm(`将 ${confirmedRows.length} 个已确认订单转为生产计划，可直接排产无需MRP运算，确定继续？`, '批量转计划', { type: 'info' })
  try {
    await Promise.all(confirmedRows.map(r => convertSalesOrderToPlan(r.id)))
    ElMessage.success(`已成功转为生产计划，共 ${confirmedRows.length} 个订单`)
    loadData(); loadStats()
  } catch {}
}

// ==================== 变更 ====================
const changeVisible = ref(false)
const changeLoading = ref(false)
const changeFormRef = ref(null)
const changeForm = reactive({ orderId: null, changeType: 'qty_increase', description: '' })
const changeRules = {
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  description: [{ required: true, message: '请输入变更说明', trigger: 'blur' }]
}

async function handleChange(row) {
  // 智能识别: 已有 draft 直接跳回继续编辑; 已有 pending 提示等审批
  console.log('[handleChange] row=', row)
  try {
    const res = await getOrderChanges({ orderId: row.id })
    const list = res.data?.records || res.data?.list || res.data || []
    console.log('[handleChange] existing changes:', list)
    const draft = list.find(c => c.status === 'draft')
    const pending = list.find(c => c.status === 'pending')
    if (draft) {
      console.log('[handleChange] redirect to draft edit, changeId=', draft.id)
      ElMessage.info('该订单已有变更草稿, 跳回继续编辑')
      await router.push({ path: '/order/edit', query: { id: row.id, changeId: draft.id } })
      return
    }
    if (pending) {
      console.log('[handleChange] redirect to detail, pending change found')
      ElMessage.warning('该订单已有待审批的变更, 请先等审批人处理')
      await router.push({ path: '/order/detail', query: { id: row.id } })
      return
    }
  } catch (e) {
    console.warn('[handleChange] check existing failed:', e)
  }
  console.log('[handleChange] open new change dialog')
  changeForm.orderId = row.id; changeForm.changeType = 'qty_increase'; changeForm.description = ''
  changeVisible.value = true
}

async function submitChange() {
  await changeFormRef.value.validate()
  changeLoading.value = true
  try {
    // 新流程: 创建草稿 → 跳编辑页, 用户改完再提交审批
    const res = await createOrderChange({ orderId: changeForm.orderId, changeType: changeForm.changeType, changeReason: changeForm.description })
    console.log('[submitChange] createOrderChange response:', res)
    const changeId = res?.data?.changeId
    if (!changeId) {
      console.error('[submitChange] no changeId in response, res=', res)
      ElMessage.error('创建变更失败: 后端未返回 changeId')
      return
    }
    changeVisible.value = false
    ElMessage.success('请编辑订单后提交审批')
    console.log('[submitChange] router.push to /order/edit with changeId=', changeId)
    await router.push({ path: '/order/edit', query: { id: changeForm.orderId, changeId } })
  } catch (e) {
    console.error('[submitChange] error:', e)
  } finally { changeLoading.value = false }
}

// ==================== AI接单 ====================
const aiOrderVisible = ref(false)
const aiStep = ref(0)
const aiInput = ref('')
const aiResult = ref({})
const aiOrderList = ref([])         // 多订单分组数组
const aiSelectedIdx = ref(0)        // 当前选中要创建的那一组
const aiFileList = ref([])
function onAiFileChange(file) { aiFileList.value.push(file) }
function onAiFileRemove(file) { aiFileList.value = aiFileList.value.filter(f => f.uid !== file.uid) }

async function doAiParse() {
  aiStep.value = 1
  try {
    const rawFiles = aiFileList.value.map(f => f.raw).filter(Boolean)
    const res = await aiParseOrder(aiInput.value, rawFiles)
    const data = res.data || {}
    aiResult.value = data
    // 兼容: 优先用 orders 数组, 没有就把单订单字段包成单元素数组
    aiOrderList.value = Array.isArray(data.orders) && data.orders.length > 0
      ? data.orders
      : (data.customer || (data.items && data.items.length))
        ? [{ customer: data.customer, items: data.items, totalAmount: data.totalAmount }]
        : []
    aiSelectedIdx.value = 0
    aiStep.value = 2
  } catch {
    ElMessage.error('AI解析失败，请手动创建订单')
    aiStep.value = 0
  }
}

async function confirmAiOrder() {
  const group = aiOrderList.value[aiSelectedIdx.value]
  if (!group) { ElMessage.warning('请先选择要创建的订单'); return }
  // 先按产品名查系统物料库, 拿到 productId 才能让产品列下拉显示
  const items = group.items || []
  const matched = await Promise.all(items.map(async item => {
    if (!item.productName) return { item, hit: null }
    try {
      const res = await getMaterialPage({ materialName: item.productName.trim(), pageNum: 1, pageSize: 5 })
      const list = res.data?.records || []
      // 优先级: 完全匹配 > 包含 AI 名 > 第一个
      const hit = list.find(p => p.materialName === item.productName)
        || list.find(p => p.materialName?.includes(item.productName))
        || list[0] || null
      // 命中的物料塞进 productOptions, 下拉才能显示
      if (hit && !productOptions.value.find(p => p.id === hit.id)) {
        productOptions.value = [...productOptions.value, hit]
      }
      return { item, hit }
    } catch { return { item, hit: null } }
  }))

  aiOrderVisible.value = false; aiStep.value = 0
  resetForm()
  const customerName = group.customer?.name
  if (customerName) {
    const cus = customerOptions.value.find(c => c.name?.includes(customerName) || c.customerName?.includes(customerName))
    if (cus) { formData.customerId = cus.id; onCustomerChange(cus.id) }
  }
  if (group.contactName) formData.contactName = group.contactName
  if (group.shippingAddress) formData.shippingAddress = group.shippingAddress
  if (group.deliveryDate) formData.deliveryDate = group.deliveryDate
  if (group.remark) formData.remark = group.remark
  formData.items = matched.map(({ item, hit }) => ({
    productId: hit ? hit.id : null,
    quantity: item.qty || 1,
    unitPrice: item.unitPrice || 0,
    deliveryDate: item.deliveryDate || '',
    unitConfig: hit?.unitConfig || '',
    _aiName: item.productName || '',  // AI 识别的原始名字, 没匹配时给提示用
    _aiSpec: item.spec || ''
  }))
  // 提示哪些没匹配上, 让用户去手选/新建
  const unmatched = matched.filter(m => !m.hit && m.item.productName).map(m => m.item.productName)
  if (unmatched.length) {
    ElMessage.warning({
      message: `以下产品系统中未找到, 请手动选择或先到物料档案新建: ${unmatched.join('、')}`,
      duration: 8000
    })
  }
  formVisible.value = true
}

// ==================== 导入 ====================
const importVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const importUploadRef = ref(null)

// 缺失物料相关
const missingDialogVisible = ref(false)
const missingMaterials = ref([])
const missingLoading = ref(false)
const missingUnitOptions = ref([])

async function handleDownloadTemplate() {
  try {
    const res = await downloadOrderImportTemplate()
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = '订单导入模板.xlsx'; link.click()
    window.URL.revokeObjectURL(url)
  } catch {}
}

async function handleImport() {
  if (!importFile.value) return
  importLoading.value = true
  try {
    const res = await importSalesOrders(importFile.value)
    const d = res.data || {}
    const successMsg = `成功导入 ${d.successCount || 0} 个订单`
    const failMsg = d.failCount > 0 ? `，失败 ${d.failCount} 个` : ''
    const autoMsg = d.autoCreated?.length > 0 ? `<br/><br/>自动创建:<br/>${d.autoCreated.join('<br/>')}` : ''

    if (d.failFileUrl) {
      // 有失败数据：弹窗提示并提供下载
      ElMessageBox.confirm(
        `${successMsg}${failMsg}${autoMsg}<br/><br/>失败订单已生成Excel文件，是否下载查看？`,
        '导入完成',
        { confirmButtonText: '下载失败文件', cancelButtonText: '关闭', type: d.failCount > 0 ? 'warning' : 'success', dangerouslyUseHTMLString: true }
      ).then(async () => {
        try {
          const apiBase = import.meta.env.VITE_API_BASE || '/api'
          const url = d.failFileUrl.replace('/api/', apiBase + '/')
          const token = localStorage.getItem('token')
          const resp = await fetch(url, { headers: { 'Authorization': token ? `Bearer ${token}` : '' } })
          if (!resp.ok) throw new Error(resp.statusText)
          const blob = await resp.blob()
          const link = document.createElement('a')
          link.href = window.URL.createObjectURL(blob)
          link.download = '导入失败数据.xlsx'
          link.click()
          window.URL.revokeObjectURL(link.href)
        } catch (e) { ElMessage.error('下载失败: ' + (e.message || '未知错误')) }
      }).catch(() => {})
    } else {
      ElMessage.success(`${successMsg}${failMsg}`)
      if (autoMsg) ElMessage.info({ message: `自动创建: ${d.autoCreated.join('、')}`, duration: 5000 })
    }

    importVisible.value = false
    importFile.value = null
    loadData(); loadStats()
  } finally { importLoading.value = false }
}

async function handleCreateMissingAndReimport() {
  // 校验所有物料名称和单位已填写
  const incomplete = missingMaterials.value.find(m => !m.materialName?.trim() || !m.unit)
  if (incomplete) {
    ElMessage.warning('请填写所有物料的名称和单位')
    return
  }
  missingLoading.value = true
  try {
    const list = missingMaterials.value.map(m => ({
      materialNo: m.materialNo,
      materialName: m.materialName.trim(),
      unit: m.unit,
      category: 'finished',
      materialAttributes: '销售',
      sourceType: 'make'
    }))
    await batchCreateMaterials(list)
    ElMessage.success('物料创建成功，正在重新导入...')
    missingDialogVisible.value = false
    // 用保留的 importFile 重新导入
    await handleImport()
  } catch {
    ElMessage.error('物料创建失败，请检查后重试')
  } finally { missingLoading.value = false }
}

// ==================== 初始化 ====================
onMounted(async () => {
  loadData(); loadStats(); loadSelectOptions()
  await loadFieldConfigs()
  await itemExtFields.loadFieldConfigs()
  // 合并扩展字段列一起加载列配置
  loadColumnConfig(listFieldConfigs.value)
  // 展开行的明细列配置 — 把行级扩展字段一起合并
  loadItemColumnConfig(itemExtFields.listFieldConfigs.value)
})
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.ai-order-group {
  border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 12px;
  margin-bottom: 10px; cursor: pointer; transition: all 0.2s;
  &:hover { border-color: #c6e2ff; }
  &.active { border-color: #409eff; background: #ecf5ff; }
}
.ai-order-group-head {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  font-size: 13px;
}
.ai-order-group-no { font-weight: 600; color: #303133; }
.ai-order-group-cust { color: #409eff; font-weight: 500; }
.ai-order-group-meta { color: #909399; font-size: 12px; margin-left: auto; }
.stat-row { margin-bottom: 12px; }
.stat-card {
  text-align: center; transition: all 0.3s;
  &.clickable { cursor: pointer; &:hover { transform: translateY(-2px); } }
  &.disabled { cursor: default; }
  &.active { border-color: #409eff; background: #ecf5ff; }
  .stat-value { font-size: 24px; font-weight: 700; line-height: 1.4; }
  .stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
}
.search-card { margin-bottom: 12px; }
.action-bar { display: flex; gap: 8px; margin-top: 8px; align-items: center; }
.table-card {
  .pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
}
.overdue-text { color: #f56c6c; font-weight: 500; }
.badge-overdue {
  display: inline-block;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: middle;
  font-weight: 600;
}
.badge-change {
  display: inline-block;
  background: #e6a23c;
  color: #fff;
  font-size: 10px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: middle;
  font-weight: 600;
}
.badge-unpaid {
  display: inline-block;
  background: #c45656;
  color: #fff;
  font-size: 10px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: middle;
  font-weight: 600;
}
.badge-delayed {
  display: inline-block;
  background: #b88230;
  color: #fff;
  font-size: 10px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: middle;
  font-weight: 600;
}
.expand-items-wrapper {
  padding: 4px 0;
  box-sizing: border-box;
}
.expand-items-toolbar {
  padding: 0 0 6px 0;
  display: flex;
  justify-content: flex-end;
}
.expand-items-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  width: 100%;
}
.expand-items-table {
  border-collapse: collapse;
  min-width: 1260px;
  width: 100%;
  font-size: 13px;
  th, td {
    border: 1px solid #ebeef5;
    padding: 6px 8px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 200px;
  }
  th {
    background: #fafafa;
    font-weight: 600;
    text-align: center;
    color: #606266;
  }
  td { color: #606266; }
  tbody tr:hover td { background: #f5f7fa; }
}
</style>
