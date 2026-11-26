<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat-card" @click="filterByStatus(undefined)">
            <el-icon class="icon" :size="36" color="#409EFF"><Document /></el-icon>
            <div class="info">
              <div class="label">全部申请</div>
              <div class="value" style="color: #409EFF">{{ statistics.total }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" @click="filterByStatus('pending')">
            <el-icon class="icon" :size="36" color="#E6A23C"><Clock /></el-icon>
            <div class="info">
              <div class="label">待审批</div>
              <div class="value" style="color: #E6A23C">{{ statistics.pending }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" @click="filterByStatus('approved')">
            <el-icon class="icon" :size="36" color="#67C23A"><CircleCheck /></el-icon>
            <div class="info">
              <div class="label">已批准</div>
              <div class="value" style="color: #67C23A">{{ statistics.approved }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" @click="filterByStatus('rejected')">
            <el-icon class="icon" :size="36" color="#F56C6C"><CircleClose /></el-icon>
            <div class="info">
              <div class="label">已驳回</div>
              <div class="value" style="color: #F56C6C">{{ statistics.rejected }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="申请单号">
          <el-input
            v-model="queryParams.requestNo"
            placeholder="申请单号"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input
            v-model="queryParams.materialName"
            placeholder="物料名称"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" value="draft" />
            <el-option label="待审批" value="pending" />
            <el-option label="已批准" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已下单" value="ordered" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="advancedDrawer = true">高级搜索</el-button>
          <el-button v-permission="'purchase:request:add'" type="primary" plain @click="handleAdd">新增申请</el-button>
          <el-button
            v-permission="'purchase:request:issue'"
            type="success"
            plain
            :disabled="selectedRows.length === 0 || !selectedRows.every(r => r.status === 'approved')"
            @click="openIssueDialog"
          >下发采购订单</el-button>
          <el-button type="primary" plain style="background: #7c3aed; border-color: #7c3aed; color: #fff" @click="openAiPurchase">
            <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI采购建议
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 高级搜索抽屉 -->
    <el-drawer v-model="advancedDrawer" title="高级搜索" size="380px" direction="rtl">
      <el-form :model="queryParams" label-width="100px">
        <el-form-item label="申请编号">
          <el-input v-model="queryParams.requestNo" placeholder="申请编号" clearable />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="queryParams.sourceType" placeholder="全部" clearable style="width: 100%">
            <el-option label="物料需求" value="material_demand" />
            <el-option label="生产工单" value="work_order" />
            <el-option label="主计划" value="master_plan" />
            <el-option label="销售订单" value="sales_order" />
            <el-option label="MRP" value="mrp" />
            <el-option label="其他申请" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input v-model="queryParams.sourceNo" placeholder="关联来源单号" clearable />
        </el-form-item>
        <el-form-item label="申请人">
          <el-select v-model="queryParams.applicantId" placeholder="全部" clearable filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.name" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="推荐供应商">
          <el-select v-model="queryParams.suggestedSupplierId" placeholder="全部" clearable filterable style="width: 100%">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预期到货">
          <el-date-picker v-model="queryParams.expectedDateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 100%" />
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

    <!-- 下发采购订单弹窗 -->
    <el-dialog v-model="issueDialogVisible" title="下发采购订单" width="760px" destroy-on-close @close="resetIssueForm">
      <el-form ref="issueFormRef" :model="issueForm" :rules="issueRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="订单号">
              <el-input v-model="issueForm.poNo" placeholder="不填则自动生成（PO+时间戳）" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-select v-model="issueForm.supplierId" placeholder="可留空, 后续在采购订单页面再补" filterable clearable
                style="width: 100%" @change="onIssueSupplierChange">
                <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交货日期" prop="deliveryDate">
              <el-date-picker v-model="issueForm.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="issueForm.contactName" placeholder="供应商联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="付款方式">
              <el-select v-model="issueForm.paymentTerms" placeholder="请选择或输入付款方式" filterable allow-create style="width: 100%">
                <el-option v-for="t in paymentTermsOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="issueForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="订单明细" required>
          <div style="width: 100%">
            <el-table :data="issueItems" border size="small" style="margin-bottom: 10px">
              <el-table-column prop="materialNo" label="物料编码" width="100" show-overflow-tooltip />
              <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
              <el-table-column prop="spec" label="规格" width="90" show-overflow-tooltip>
                <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="60" align="center" />
              <el-table-column label="数量" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="0.01" :precision="4" size="small" controls-position="right" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="单价" width="140">
                <template #default="{ row }">
                  <div style="display: flex; align-items: center; gap: 4px">
                    <el-input-number v-model="row.unitPrice" :min="0" :precision="4" size="small" controls-position="right" style="width: 105px" />
                    <el-tooltip content="历史采购价" placement="top">
                      <el-icon style="cursor: pointer; color: #409eff; flex-shrink: 0" @click="showPriceHistory(row)"><Clock /></el-icon>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="小计" width="80" align="right">
                <template #default="{ row }">{{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}</template>
              </el-table-column>
            </el-table>
            <div style="text-align: right; font-weight: 600; color: #e6a23c">
              合计: ¥{{ issueItems.reduce((s, r) => s + (r.quantity || 0) * (r.unitPrice || 0), 0).toFixed(2) }}
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="issueLoading" @click="handleIssueSubmit">确认下发</el-button>
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

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <div style="display: flex; justify-content: flex-end; margin-bottom: 8px">
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="40" />
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'sourceType'">
              <el-tag v-if="row.sourceType" :type="sourceTypeTagType(row.sourceType)" size="small">
                {{ sourceTypeLabel(row.sourceType) }}
              </el-tag>
              <span v-else>-</span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">
                {{ statusText(row.status) }}
              </el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
            <!-- 草稿: 编辑 + 提交 + 删除 -->
            <el-button v-if="row.status === 'draft'" type="primary" link size="small" @click="handleEditDraft(row)">编辑</el-button>
            <el-button v-if="row.status === 'draft'" type="success" link size="small" @click="handleSubmitDraft(row)">提交</el-button>
            <!-- 待审批: 审批/驳回 -->
            <el-button v-permission="'purchase:request:approve'" v-if="row.status === 'pending'" type="success" link size="small" @click="handleApprove(row)">审批</el-button>
            <el-button v-permission="'purchase:request:reject'" v-if="row.status === 'pending'" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
            <!-- 草稿 + 待审批 都可删除 -->
            <el-button v-if="row.status === 'draft' || row.status === 'pending'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增采购申请" width="820px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型" style="width: 100%">
                <el-option label="物料需求" value="material_demand" />
                <el-option label="其他申请" value="other" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源单号">
              <el-input v-model="form.sourceNo" placeholder="关联单号（选填）" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请人" prop="applicantId">
              <el-select v-model="form.applicantId" placeholder="请选择申请人" filterable style="width: 100%">
                <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.name" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需求日期" prop="requiredDate">
              <el-date-picker v-model="form.requiredDate" type="date" placeholder="请选择需求日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用途">
              <el-input v-model="form.purpose" placeholder="请输入用途（选填）" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="申请原因">
              <el-input v-model="form.applyReason" type="textarea" :rows="2" placeholder="请输入申请原因（选填）" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注（选填）" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="物料明细" required>
              <el-button type="primary" plain size="small" @click="materialPickerVisible = true">+ 添加物料</el-button>
              <el-table :data="form.items" border size="small" style="margin-top:8px;width:100%">
                <el-table-column prop="materialNo" label="物料编码" min-width="120" show-overflow-tooltip />
                <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
                <el-table-column label="规格" min-width="120" show-overflow-tooltip>
                  <template #default="{ row }">{{ $fmtSpec(row.materialSpec) }}</template>
                </el-table-column>
                <el-table-column prop="unit" label="单位" width="70" align="center" />
                <el-table-column label="申请数量" width="140">
                  <template #default="{ row }">
                    <el-input-number v-model="row.quantity" :min="0.01" :precision="2" size="small"
                                     controls-position="right" style="width:120px" />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="60" align="center">
                  <template #default="{ $index }">
                    <el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>请点击上方"添加物料"选择物料（支持多选）</template>
              </el-table>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="detailVisible" title="采购申请详情" width="680px" destroy-on-close>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="申请编号" :span="2">{{ viewRow.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="来源类型">
          <el-tag v-if="viewRow.sourceType" :type="sourceTypeTagType(viewRow.sourceType)" size="small">
            {{ sourceTypeLabel(viewRow.sourceType) }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="来源单号">{{ viewRow.sourceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ viewRow.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(viewRow.status)" size="small">{{ statusText(viewRow.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ viewRow.materialNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ viewRow.materialName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ $fmtSpec(viewRow.spec) }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ viewRow.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请数量">{{ viewRow.quantity }}</el-descriptions-item>
        <el-descriptions-item label="需求日期">{{ viewRow.requiredDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用途">{{ viewRow.purpose || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预估单价">
          <span v-if="viewRow.estimatedPrice" style="color:#e6a23c;font-weight:600">
            ¥{{ Number(viewRow.estimatedPrice).toFixed(2) }}
          </span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="推荐供应商">{{ viewRow.suggestedSupplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请原因" :span="2">{{ viewRow.applyReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ viewRow.approvedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见">{{ viewRow.approveRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewRow.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="PURCHASE_REQUEST_DEFAULT_COLUMNS" @save="saveConfig" />

    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料" :multiple="true" @select="onMaterialSelected" />

    <!-- AI采购建议对话框 -->
    <el-dialog v-model="aiPurchaseVisible" title="AI采购建议" width="850px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        输入当前库存数据或低库存物料信息，AI将分析并给出采购建议。
      </el-alert>
      <el-input
        v-model="aiInventoryText"
        type="textarea"
        :rows="5"
        placeholder="请输入库存数据，例如：&#10;物料A：当前库存50，安全库存200&#10;物料B：当前库存10，安全库存100&#10;物料C：当前库存0，安全库存80"
      />
      <div style="margin-top: 12px; text-align: right">
        <el-button type="primary" :loading="aiPurchaseLoading" :disabled="!aiInventoryText.trim()" style="background: #7c3aed; border-color: #7c3aed" @click="handleAiPurchase">
          <el-icon style="margin-right: 4px"><MagicStick /></el-icon>分析建议
        </el-button>
      </div>

      <!-- 建议结果 -->
      <template v-if="aiPurchaseSuggestions && aiPurchaseSuggestions.length">
        <el-divider content-position="left">采购建议</el-divider>
        <el-table :data="aiPurchaseSuggestions" border stripe size="small">
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="currentStock" label="当前库存" width="100" align="center" />
          <el-table-column prop="suggestedQty" label="建议采购量" width="110" align="center" />
          <el-table-column prop="urgency" label="紧急程度" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="urgencyTagType(row.urgency)" size="small">{{ row.urgency }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="建议原因" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="aiPurchaseVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!aiPurchaseSuggestions || !aiPurchaseSuggestions.length" style="background: #7c3aed; border-color: #7c3aed" @click="handleBatchCreate">批量创建申请</el-button>
      </template>
    </el-dialog>

    <!-- 草稿编辑弹窗 -->
    <el-dialog v-model="editDraftVisible" title="编辑草稿采购申请" width="540px" destroy-on-close append-to-body>
      <el-form :model="editDraftForm" label-width="100px">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="申请单号">{{ editDraftForm.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ editDraftForm.materialName }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ $fmtSpec(editDraftForm.spec) }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ editDraftForm.unit || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="数量" required>
          <el-input-number v-model="editDraftForm.quantity" :min="0.0001" :precision="4" style="width: 200px" />
        </el-form-item>
        <el-form-item label="预计到货日">
          <el-date-picker v-model="editDraftForm.expectedDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="预估单价">
          <el-input-number v-model="editDraftForm.estimatedPrice" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editDraftForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDraftVisible = false">取消</el-button>
        <el-button type="primary" :loading="editDraftSubmitting" @click="submitEditDraft">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Document, Clock, CircleCheck, CircleClose, Setting } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

const PURCHASE_REQUEST_DEFAULT_COLUMNS = [
  { key: 'requestNo', label: '申请编号', minWidth: 160 },
  { key: 'sourceType', label: '来源类型', minWidth: 105, align: 'center' },
  { key: 'sourceNo', label: '来源单号', minWidth: 175 },
  { key: 'applicantName', label: '申请人', minWidth: 100 },
  { key: 'materialName', label: '物料名称', minWidth: 140 },
  { key: 'quantity', label: '数量', minWidth: 80, align: 'right' },
  { key: 'purpose', label: '用途', minWidth: 100 },
  { key: 'requiredDate', label: '需求日期', minWidth: 115 },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('purchase_request', PURCHASE_REQUEST_DEFAULT_COLUMNS)
import {
  getPurchaseRequestPage,
  createPurchaseRequest,
  approvePurchaseRequest,
  rejectPurchaseRequest,
  deletePurchaseRequest,
  updatePurchaseRequestDraft,
  submitPurchaseRequestDraft,
  issueToOrder,
  getPurchasePriceHistory
} from '@/api/purchase'
import { getMaterialSelect, getSupplierSelect } from '@/api/master'
import { getUserSelect } from '@/api/system'
import { purchaseSuggestion } from '@/api/ai'
import { useUserStore } from '@/store/user'

// 状态映射（后端使用小写状态值）
import { purchaseRequestStatusMap, getStatusInfo } from '@/utils/status'
import { formatDateTime } from '@/utils/format'

const userStore = useUserStore()

function statusTagType(status) {
  return getStatusInfo(purchaseRequestStatusMap, status).type
}
function statusText(status) {
  return getStatusInfo(purchaseRequestStatusMap, status).label
}

// 来源类型映射
const sourceTypeMap = {
  material_demand: { label: '物料需求', type: 'primary' },
  work_order: { label: '生产工单', type: 'warning' },
  master_plan: { label: '主计划', type: 'success' },
  sales_order: { label: '销售订单', type: 'success' },
  mrp: { label: 'MRP', type: 'success' },
  other: { label: '其他申请', type: 'info' }
}
function sourceTypeLabel(type) {
  return sourceTypeMap[type]?.label || type || '-'
}
function sourceTypeTagType(type) {
  return sourceTypeMap[type]?.type || 'info'
}

// 下拉选项
const materialOptions = ref([])
const supplierOptions = ref([])
const userOptions = ref([])

// 高级搜索
const advancedDrawer = ref(false)

// 多选
const selectedRows = ref([])

// 查询参数
const queryParams = reactive({
  status: undefined,
  requestNo: '',
  materialName: '',
  sourceType: undefined,
  sourceNo: '',
  applicantId: undefined,
  suggestedSupplierId: undefined,
  expectedDateRange: null,
  createTimeRange: null,
  pageNum: 1,
  pageSize: 10
})

// 表格数据
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// 统计数据
const statCounts = reactive({ total: 0, pending: 0, approved: 0, rejected: 0 })

const statistics = computed(() => ({
  total: statCounts.total,
  pending: statCounts.pending,
  approved: statCounts.approved,
  rejected: statCounts.rejected
}))

async function loadStatistics() {
  try {
    const [allRes, pendingRes, approvedRes, rejectedRes] = await Promise.all([
      getPurchaseRequestPage({ pageNum: 1, pageSize: 1 }),
      getPurchaseRequestPage({ pageNum: 1, pageSize: 1, status: 'pending' }),
      getPurchaseRequestPage({ pageNum: 1, pageSize: 1, status: 'approved' }),
      getPurchaseRequestPage({ pageNum: 1, pageSize: 1, status: 'rejected' })
    ])
    statCounts.total = allRes.data?.total || 0
    statCounts.pending = pendingRes.data?.total || 0
    statCounts.approved = approvedRes.data?.total || 0
    statCounts.rejected = rejectedRes.data?.total || 0
  } catch {
    // fallback: count from current page data
    statCounts.total = total.value
    statCounts.pending = tableData.value.filter(r => r.status === 'pending').length
    statCounts.approved = tableData.value.filter(r => r.status === 'approved').length
    statCounts.rejected = tableData.value.filter(r => r.status === 'rejected').length
  }
}

function filterByStatus(status) {
  queryParams.status = status
  queryParams.pageNum = 1
  loadData()
}

// ==================== 查看详情 ====================
const detailVisible = ref(false)
const viewRow = ref({})

function handleView(row) {
  // 补充供应商名称（从已加载的选项中查找）
  const sup = supplierOptions.value.find(s => s.id === row.suggestedSupplierId)
  viewRow.value = { ...row, suggestedSupplierName: sup?.name || row.suggestedSupplierName || '' }
  detailVisible.value = true
}

// 弹窗相关
const dialogVisible = ref(false)
const formRef = ref(null)
const submitLoading = ref(false)

const getDefaultForm = () => ({
  requiredDate: '',
  sourceType: '',
  sourceNo: '',
  applicantId: userStore.userInfo?.id || null,
  applyReason: '',
  purpose: '',
  remark: '',
  items: []
})

const materialPickerVisible = ref(false)
function onMaterialSelected(mats) {
  if (!Array.isArray(mats) || mats.length === 0) return
  const existingIds = new Set(form.items.map(i => i.materialId))
  mats.forEach(mat => {
    if (existingIds.has(mat.id)) return
    form.items.push({
      materialId: mat.id,
      materialNo: mat.materialNo || '',
      materialName: mat.materialName || mat.name || '',
      materialSpec: mat.spec || '',
      unit: mat.unit || '',
      quantity: 1
    })
  })
}
function removeItem(index) {
  form.items.splice(index, 1)
}

const form = reactive(getDefaultForm())

const rules = {
  requiredDate: [{ required: true, message: '请选择需求日期', trigger: 'change' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  applicantId: [{ required: true, message: '请选择申请人', trigger: 'change' }]
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (params.expectedDateRange?.length === 2) {
      params.expectedDateStart = params.expectedDateRange[0]
      params.expectedDateEnd = params.expectedDateRange[1]
    }
    delete params.expectedDateRange
    if (params.createTimeRange?.length === 2) {
      params.createTimeStart = params.createTimeRange[0]
      params.createTimeEnd = params.createTimeRange[1]
    }
    delete params.createTimeRange
    const res = await getPurchaseRequestPage(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
    loadStatistics()
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  // 物料下拉数据量可能很大,改为按需加载;供应商/用户保留(数据量小)
  try {
    const [supRes, userRes] = await Promise.all([getSupplierSelect(), getUserSelect()])
    supplierOptions.value = supRes.data || []
    userOptions.value = userRes.data || []
  } catch {}
}

// AI 填表/下发时如果需要按物料名匹配,按需查
async function loadMaterialOptionsLazy() {
  if (materialOptions.value.length > 0) return
  try {
    const matRes = await getMaterialSelect()
    materialOptions.value = matRes.data || []
  } catch {}
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.status = undefined
  queryParams.requestNo = ''
  queryParams.materialName = ''
  queryParams.sourceType = undefined
  queryParams.sourceNo = ''
  queryParams.applicantId = undefined
  queryParams.suggestedSupplierId = undefined
  queryParams.expectedDateRange = null
  queryParams.createTimeRange = null
  queryParams.pageNum = 1
  loadData()
}

function resetForm() {
  const defaults = getDefaultForm()
  Object.keys(defaults).forEach(key => {
    form[key] = defaults[key]
  })
  form.items = []
}

function handleAdd() {
  resetForm()
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  // 先过滤掉没选物料的空行
  const validItems = (form.items || []).filter(it => it.materialId)
  if (validItems.length === 0) {
    ElMessage.warning('请至少添加一种物料')
    return
  }
  const skipped = (form.items?.length || 0) - validItems.length
  if (skipped > 0) ElMessage.info(`已自动忽略 ${skipped} 条未选物料的明细`)
  // 已选物料但数量缺/非法的仍然报错 (业务必填)
  const invalidItem = validItems.find(it => !it.quantity || it.quantity <= 0)
  if (invalidItem) {
    ElMessage.warning(`物料「${invalidItem.materialName || invalidItem.materialId}」未设置数量`)
    return
  }
  submitLoading.value = true
  try {
    // 每个物料生成一条独立的采购申请，共享申请表头字段
    const headerFields = {
      sourceType: form.sourceType,
      sourceNo: form.sourceNo,
      applicantId: form.applicantId,
      requiredDate: form.requiredDate,
      applyReason: form.applyReason,
      purpose: form.purpose,
      remark: form.remark
    }
    for (const it of validItems) {
      await createPurchaseRequest({
        ...headerFields,
        materialId: it.materialId,
        materialName: it.materialName,
        materialSpec: it.materialSpec,
        quantity: it.quantity
      })
    }
    ElMessage.success(`采购申请创建成功（${validItems.length} 条）`)
    dialogVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}


function handleDelete(row) {
  ElMessageBox.confirm(`确定删除申请「${row.requestNo}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await deletePurchaseRequest(row.id)
        ElMessage.success('已删除')
        loadData()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

// ========== 草稿编辑 / 提交 ==========
const editDraftVisible = ref(false)
const editDraftSubmitting = ref(false)
const editDraftForm = reactive({
  id: null, requestNo: '', materialName: '', spec: '', unit: '',
  quantity: 0, expectedDate: '', estimatedPrice: 0, remark: ''
})
function handleEditDraft(row) {
  Object.assign(editDraftForm, {
    id: row.id, requestNo: row.requestNo,
    materialName: row.materialName, spec: row.spec, unit: row.unit,
    quantity: Number(row.quantity || 0),
    expectedDate: row.requiredDate || row.expectedDate || '',
    estimatedPrice: Number(row.estimatedPrice || 0),
    remark: row.remark || ''
  })
  editDraftVisible.value = true
}
async function submitEditDraft() {
  if (!editDraftForm.quantity || editDraftForm.quantity <= 0) { ElMessage.warning('请输入数量'); return }
  editDraftSubmitting.value = true
  try {
    await updatePurchaseRequestDraft(editDraftForm.id, {
      quantity: editDraftForm.quantity,
      expectedDate: editDraftForm.expectedDate || null,
      estimatedPrice: editDraftForm.estimatedPrice || null,
      remark: editDraftForm.remark
    })
    ElMessage.success('草稿已更新')
    editDraftVisible.value = false
    loadData()
  } finally { editDraftSubmitting.value = false }
}
function handleSubmitDraft(row) {
  ElMessageBox.confirm(`确认提交申请「${row.requestNo}」? 提交后将进入审批流程, 不能再编辑.`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await submitPurchaseRequestDraft(row.id)
        ElMessage.success('已提交')
        loadData()
      } catch {}
    })
    .catch(() => {})
}

function handleApprove(row) {
  ElMessageBox.confirm(`确定审批通过申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await approvePurchaseRequest(row.id, {})
        ElMessage.success('审批通过')
        loadData()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

function handleReject(row) {
  ElMessageBox.confirm(`确定驳回申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await rejectPurchaseRequest(row.id, {})
        ElMessage.success('已驳回')
        loadData()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

// ==================== 下发采购订单 ====================
const issueDialogVisible = ref(false)
const issueLoading = ref(false)
const issueFormRef = ref(null)
const issueForm = reactive({ poNo: '', supplierId: undefined, contactName: '', deliveryDate: '', paymentTerms: '', remark: '' })
const issueItems = ref([])
const issueRules = {
  deliveryDate: [{ required: true, message: '请选择交货日期', trigger: 'change' }]
}
const paymentTermsOptions = ['现结', '月结30天', '月结60天', '月结90天']

function onIssueSupplierChange(supplierId) {
  const sup = supplierOptions.value.find(s => s.id === supplierId)
  if (sup) {
    issueForm.contactName = sup.contactName || ''
    issueForm.paymentTerms = sup.settlementType || ''
  }
}

// 价格历史
const priceHistoryVisible = ref(false)
const priceHistoryLoading = ref(false)
const priceHistoryList = ref([])

function resetIssueForm() {
  Object.assign(issueForm, { poNo: '', supplierId: undefined, contactName: '', deliveryDate: '', paymentTerms: '', remark: '' })
  issueItems.value = []
}

async function openIssueDialog() {
  resetIssueForm()
  await loadMaterialOptionsLazy()
  issueItems.value = selectedRows.value.map(r => {
    const mat = materialOptions.value.find(m => m.id === r.materialId)
    return {
      requestId: r.id,
      materialId: r.materialId,
      materialNo: mat?.materialNo || '',
      materialName: r.materialName || mat?.name || '',
      spec: r.spec || mat?.spec || '',
      unit: r.unit || mat?.unit || '',
      quantity: Number(r.quantity) || 0,
      unitPrice: Number(r.estimatedPrice) || 0
    }
  })
  issueDialogVisible.value = true
}

async function showPriceHistory(row) {
  if (!row.materialId) return
  priceHistoryList.value = []
  priceHistoryVisible.value = true
  priceHistoryLoading.value = true
  try {
    const res = await getPurchasePriceHistory({ materialId: row.materialId })
    priceHistoryList.value = res.data || []
  } finally {
    priceHistoryLoading.value = false
  }
}

async function handleIssueSubmit() {
  await issueFormRef.value.validate()
  if (!issueItems.value.length) { ElMessage.warning('没有可下发的物料明细'); return }
  issueLoading.value = true
  try {
    await issueToOrder({
      requestIds: selectedRows.value.map(r => r.id),
      ...issueForm,
      items: issueItems.value
    })
    ElMessage.success('下发成功，采购订单已创建')
    issueDialogVisible.value = false
    loadData()
  } finally {
    issueLoading.value = false
  }
}

// ==================== AI采购建议 ====================
const aiPurchaseVisible = ref(false)
const aiInventoryText = ref('')
const aiPurchaseLoading = ref(false)
const aiPurchaseSuggestions = ref(null)

function openAiPurchase() {
  aiInventoryText.value = ''
  aiPurchaseSuggestions.value = null
  aiPurchaseVisible.value = true
}

function urgencyTagType(urgency) {
  if (!urgency) return 'info'
  const u = urgency.toLowerCase()
  if (u.includes('高') || u.includes('紧急') || u === 'high') return 'danger'
  if (u.includes('中') || u === 'medium') return 'warning'
  return 'success'
}

async function handleAiPurchase() {
  if (!aiInventoryText.value.trim()) return
  aiPurchaseLoading.value = true
  aiPurchaseSuggestions.value = null
  try {
    const res = await purchaseSuggestion(aiInventoryText.value)
    aiPurchaseSuggestions.value = res.data?.suggestions || res.data || []
    ElMessage.success('分析完成')
  } catch {
    ElMessage.error('AI分析失败，请重试')
  } finally {
    aiPurchaseLoading.value = false
  }
}

async function handleBatchCreate() {
  if (!aiPurchaseSuggestions.value || !aiPurchaseSuggestions.value.length) return
  await ElMessageBox.confirm(
    `将批量创建 ${aiPurchaseSuggestions.value.length} 条采购申请，是否继续？`,
    '确认批量创建',
    { type: 'warning' }
  )
  await loadMaterialOptionsLazy()
  try {
    for (const item of aiPurchaseSuggestions.value) {
      const matched = materialOptions.value.find(m => m.name === item.materialName)
      await createPurchaseRequest({
        materialId: matched ? matched.id : undefined,
        quantity: item.suggestedQty,
        requiredDate: item.requiredDate || '',
        remark: `[AI建议] ${item.reason || ''}`
      })
    }
    ElMessage.success('批量创建成功')
    aiPurchaseVisible.value = false
    loadData()
  } catch {
    ElMessage.error('批量创建失败')
  }
}

onMounted(() => {
  loadOptions()
  loadData()
  loadColumnConfig()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}
.table-card {
  flex: 1;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: #c0c4cc;
}

/* 统计卡片 */
.stat-cards {
  margin-bottom: 0;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.3s;
  cursor: pointer;
}
.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.stat-card .icon {
  font-size: 36px;
}
.stat-card .info .label {
  font-size: 13px;
  color: #909399;
}
.stat-card .info .value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 4px;
}
</style>
