<template>
  <div class="page-container">
    <!-- 来源页签 -->
    <el-tabs v-model="activeSourceTab" type="border-card" @tab-change="onSourceTabChange" style="margin-bottom: 0">
      <el-tab-pane label="生产报工" name="work_order" />
      <el-tab-pane label="采购收料" name="purchase_receipt" />
      <el-tab-pane label="外协收料" name="outsource_receipt" />
      <el-tab-pane label="返修检验" name="rework_order" />
    </el-tabs>

    <!-- stat cards -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <StatCard title="待检验" :value="statCounts.pending" icon="Clock" color="#e6a23c"
          clickable @click="filterByCard('pending')" />
      </el-col>
      <el-col :span="6">
        <StatCard title="检验中" :value="statCounts.inProgress" icon="Loading" color="#409eff"
          clickable @click="filterByCard('inspecting')" />
      </el-col>
      <el-col :span="6">
        <StatCard title="今日通过" :value="statCounts.todayPass" icon="CircleCheck" color="#67c23a"
          clickable @click="filterByCard('today_pass')" />
      </el-col>
      <el-col :span="6">
        <StatCard title="今日不合格" :value="statCounts.todayFail" icon="CircleClose" color="#f56c6c"
          clickable @click="filterByCard('today_fail')" />
      </el-col>
    </el-row>

    <!-- search bar -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="任务编号">
          <el-input
            v-model="queryParams.taskNo"
            placeholder="请输入任务编号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="工单号">
          <el-input
            v-model="queryParams.workOrderNo"
            placeholder="请输入工单号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <!-- 检验类型已由来源页签自动关联 -->
        <el-form-item label="状态">
          <el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 140px">
            <el-option label="待检验" value="pending" />
            <el-option label="检验中" value="inspecting" />
            <el-option label="检测完成" value="completed" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增任务</el-button>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- table -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="taskNo" label="任务编号" min-width="175" show-overflow-tooltip />
        <el-table-column prop="qcType" label="检验类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="qcTypeTagType(row.qcType)" size="small" effect="plain">
              {{ qcTypeText(row.qcType) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 来料检字段 -->
        <el-table-column v-if="showCol('incoming')" prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="showCol('incoming')" prop="supplierName" label="供应商" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="showCol('incoming')" label="采购单号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.purchaseOrderNo" type="primary" link @click="openDocDetail('purchase', row)">{{ row.purchaseOrderNo }}</el-button>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <!-- 过程检字段 -->
        <el-table-column v-if="showCol('process')" label="工单号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.workOrderNo" type="primary" link @click="openDocDetail('workOrder', row)">{{ row.workOrderNo }}</el-button>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showCol('process')" prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="showCol('process')" prop="stepName" label="工序" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="showCol('process')" label="执行人" width="100" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.inspectorName || row.reporterName || '-' }}
          </template>
        </el-table-column>
        <!-- 成品检字段 -->
        <el-table-column v-if="showCol('final')" label="工单号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.workOrderNo" type="primary" link @click="openDocDetail('workOrder', row)">{{ row.workOrderNo }}</el-button>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showCol('final')" prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <!-- 外协检字段 -->
        <el-table-column v-if="showCol('outsource')" label="工单号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.workOrderNo" type="primary" link @click="openDocDetail('workOrder', row)">{{ row.workOrderNo }}</el-button>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showCol('outsource')" prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="showCol('outsource')" label="外协单号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.outsourceOrderNo" type="primary" link @click="openDocDetail('outsource', row)">{{ row.outsourceOrderNo }}</el-button>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showCol('outsource')" prop="supplierName" label="供应商" min-width="120" show-overflow-tooltip />
        <el-table-column v-if="showCol('outsource')" prop="stepName" label="工序" min-width="120" show-overflow-tooltip />
        <!-- 返修检字段 -->
        <el-table-column v-if="activeSourceTab === 'rework_order'" prop="reworkOrderNo" label="返修单号" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="activeSourceTab === 'rework_order'" label="原工单号" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.workOrderNo" type="primary" link @click="openDocDetail('workOrder', row)">{{ row.workOrderNo }}</el-button>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column v-if="activeSourceTab === 'rework_order'" prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="activeSourceTab === 'rework_order'" prop="stepName" label="工序" min-width="120" show-overflow-tooltip />
        <!-- 未选类型时的通用列 -->
        <template v-if="!queryParams.qcType">
          <el-table-column label="关联对象" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="row.qcType === 'incoming'">{{ row.materialName }}</template>
              <template v-else>{{ row.productName }}</template>
            </template>
          </el-table-column>
          <el-table-column label="关联单号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="row.qcType === 'incoming'">
                <el-button v-if="row.purchaseOrderNo" type="primary" link @click="openDocDetail('purchase', row)">{{ row.purchaseOrderNo }}</el-button>
              </template>
              <template v-else-if="row.qcType === 'outsource'">
                <el-button v-if="row.outsourceOrderNo" type="primary" link @click="openDocDetail('outsource', row)">{{ row.outsourceOrderNo }}</el-button>
              </template>
              <template v-else>
                <el-button v-if="row.workOrderNo" type="primary" link @click="openDocDetail('workOrder', row)">{{ row.workOrderNo }}</el-button>
              </template>
            </template>
          </el-table-column>
        </template>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'result'">
              <el-tag :type="statusTagType(row.result)" size="small">{{ statusText(row.result) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'createTime'">{{ $fmtDate(row.createTime) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.result === 'pending' || row.result === 'inspecting'"
              type="primary"
              link
              size="small"
              @click="handleOpenInspection(row)"
            >开始检验</el-button>
            <el-button
              v-if="['completed','pass','fail'].includes(row.result)"
              type="info"
              link
              size="small"
              @click="handleOpenInspection(row)"
            >查看详情</el-button>
            <el-button
              v-if="row.disposalStatus === 'undisposed' && row.qcType === 'process'"
              type="danger"
              link
              size="small"
              @click="handleOpenDispose(row)"
            >处置</el-button>
            <el-button
              v-if="row.disposalStatus === 'undisposed' && row.qcType === 'rework'"
              type="danger"
              link
              size="small"
              @click="goToReworkDisposal(row)"
            >处置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- create task dialog -->
    <el-dialog
      v-model="addDialogVisible"
      title="新增检验任务"
      width="560px"
      destroy-on-close
      @close="resetAddForm"
    >
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="检验类型" prop="qcType">
          <el-select v-model="addForm.qcType" placeholder="请选择" style="width: 100%">
            <el-option label="来料检验" value="incoming" />
            <el-option label="过程检验" value="process" />
            <el-option label="成品检验" value="final" />
            <el-option label="外协检验" value="outsource" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单" prop="workOrderId">
          <el-select v-model="addForm.workOrderId" filterable remote :remote-method="searchWorkOrders"
            :loading="woSelectLoading" placeholder="输入工单号搜索" style="width: 100%" @change="onWorkOrderChange">
            <el-option v-for="w in workOrderOptions" :key="w.id" :label="`${w.woNo} - ${w.productName || ''}`" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" prop="productId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="addForm.productName" readonly placeholder="选择产品" style="flex: 1;" />
            <el-button type="primary" @click="taskProductPickerVisible = true">选择产品</el-button>
          </div>
        </el-form-item>
        <el-form-item label="检验模板" prop="templateId">
          <el-select v-model="addForm.templateId" placeholder="选择检验模板" style="width: 100%" clearable>
            <el-option v-for="t in templateOptions" :key="t.id" :label="t.templateName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验员" prop="inspectorId">
          <el-select v-model="addForm.inspectorId" filterable placeholder="选择检验员" style="width: 100%">
            <el-option v-for="u in inspectorOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划数量" prop="planQty">
          <el-input-number v-model="addForm.planQty" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAddSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 提交检验结果弹窗已移除（集成到检验执行弹窗中） -->

    <!-- dispose dialog for failed QC tasks -->
    <el-dialog
      v-model="disposeDialogVisible"
      title="质检不合格处置"
      width="680px"
      destroy-on-close
      @close="resetDisposeForm"
    >
      <el-descriptions :column="2" border style="margin-bottom: 20px;" size="small">
        <el-descriptions-item label="任务编号">{{ disposeTask.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="工单号">{{ disposeTask.workOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ disposeTask.productName }}</el-descriptions-item>
        <el-descriptions-item label="检验类型">{{ qcTypeText(disposeTask.qcType) }}</el-descriptions-item>
      </el-descriptions>

      <el-form ref="disposeFormRef" :model="disposeForm" :rules="disposeRules" label-width="100px">
        <el-form-item label="处置范围" prop="scope">
          <el-radio-group v-model="disposeForm.scope">
            <el-radio label="all">整批（{{ disposeTask.planQty || 0 }}）</el-radio>
            <el-radio label="defect">仅不合格（{{ disposeTask.failQty || 0 }}）</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="处置方式" prop="handlePath">
          <el-radio-group v-model="disposeForm.handlePath">
            <el-radio label="repair">返修</el-radio>
            <el-radio label="concession">让步接收</el-radio>
            <el-radio label="scrap">报废</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 返修: 工序列表 + 指派 -->
        <template v-if="disposeForm.handlePath === 'repair'">
          <el-form-item label="返修工序">
            <div style="width: 100%">
              <div v-for="(step, idx) in disposeForm.repairSteps" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px; align-items: center">
                <el-select v-model="step.processId" placeholder="选择工序" filterable style="flex: 1" @change="val => onRepairStepSelect(idx, val)">
                  <el-option v-for="p in processOptions" :key="p.id" :label="p.name" :value="p.id" />
                </el-select>
                <el-button type="danger" link @click="disposeForm.repairSteps.splice(idx, 1)" :disabled="disposeForm.repairSteps.length <= 1">删除</el-button>
              </div>
              <el-button type="primary" link @click="disposeForm.repairSteps.push({ processId: null, stepName: '' })">+ 添加工序</el-button>
            </div>
          </el-form-item>
          <el-form-item label="指派班组">
            <el-select v-model="disposeForm.assignedTeamId" placeholder="请选择班组" clearable style="width: 100%" @change="onTeamChange">
              <el-option v-for="t in teamOptions" :key="t.id" :label="t.teamName" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="指派人员">
            <el-select v-model="disposeForm.assignedUserId" placeholder="请选择人员" clearable style="width: 100%" @change="onUserChange">
              <el-option v-for="u in teamMembers" :key="u.userId" :label="u.realName" :value="u.userId" />
            </el-select>
          </el-form-item>
          <el-alert type="info" :closable="false" style="margin-bottom: 16px">
            将创建返修工单，剩余数量为本道工序合格数（下道工序可报工数）
          </el-alert>
        </template>

        <!-- 让步接收 -->
        <template v-if="disposeForm.handlePath === 'concession'">
          <el-form-item label="让步原因" prop="concessionReason">
            <el-input v-model="disposeForm.concessionReason" type="textarea" :rows="2" placeholder="请输入让步接收原因（必填）" />
          </el-form-item>
          <el-alert type="info" :closable="false" style="margin-bottom: 16px">
            让步接收：处置数量视为合格，计入本道工序合格数
          </el-alert>
        </template>

        <!-- 报废 -->
        <template v-if="disposeForm.handlePath === 'scrap'">
          <el-form-item label="报废原因" prop="scrapReason">
            <el-input v-model="disposeForm.scrapReason" type="textarea" :rows="2" placeholder="请输入报废原因" />
          </el-form-item>
          <el-alert type="info" :closable="false" style="margin-bottom: 16px">
            将生成报废入库库存任务，剩余数量为本道工序合格数（下道工序可报工数）
          </el-alert>
        </template>

        <el-form-item label="备注">
          <el-input v-model="disposeForm.remark" type="textarea" :rows="2" placeholder="处置备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disposeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="disposeSubmitLoading" @click="handleDisposeSubmit">确认处置</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="QUALITY_TASK_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 项目库选择弹窗已移除（改用模板导入） -->

    <!-- document detail drawer -->
    <el-drawer v-model="docDetailVisible" :title="docDetailTitle" size="700px" destroy-on-close>
      <div v-loading="docDetailLoading">
        <template v-if="docDetail">
          <!-- 工单详情 -->
          <template v-if="docDetailType === 'workOrder'">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="工单号">{{ docDetail.workOrderNo }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ woStatusText(docDetail.status) }}</el-descriptions-item>
              <el-descriptions-item label="产品名称">{{ docDetail.productName }}</el-descriptions-item>
              <el-descriptions-item label="物料编码">{{ docDetail.materialCode }}</el-descriptions-item>
              <el-descriptions-item label="计划数量">{{ docDetail.planQty }}</el-descriptions-item>
              <el-descriptions-item label="完工数量">{{ docDetail.completedQty }}</el-descriptions-item>
              <el-descriptions-item label="开始日期">{{ docDetail.startDate }}</el-descriptions-item>
              <el-descriptions-item label="结束日期">{{ docDetail.endDate }}</el-descriptions-item>
              <el-descriptions-item label="优先级">{{ docDetail.priority }}</el-descriptions-item>
              <el-descriptions-item label="客户">{{ docDetail.customerName || '--' }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ docDetail.remark || '--' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="docDetail.tasks && docDetail.tasks.length" style="margin-top: 16px">
              <h4 style="margin-bottom: 8px">工艺路线</h4>
              <el-table :data="docDetail.tasks" border size="small">
                <el-table-column prop="stepNo" label="序号" width="60" align="center" />
                <el-table-column prop="stepName" label="工序名称" min-width="120" />
                <el-table-column prop="status" label="状态" width="80" align="center" />
                <el-table-column prop="qualifiedQty" label="合格数" width="80" align="center" />
                <el-table-column prop="defectQty" label="不良数" width="80" align="center" />
              </el-table>
            </div>
          </template>
          <!-- 外协单详情 -->
          <template v-else-if="docDetailType === 'outsource'">
            <el-tabs v-model="docDetailTab">
              <el-tab-pane label="基本信息" name="basic">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="加工单号">{{ docDetail.orderNo }}</el-descriptions-item>
                  <el-descriptions-item label="供应商">{{ docDetail.supplierName }}</el-descriptions-item>
                  <el-descriptions-item label="产品名称">{{ docDetail.productName }}</el-descriptions-item>
                  <el-descriptions-item label="工序名称">{{ docDetail.stepName }}</el-descriptions-item>
                  <el-descriptions-item label="总数量">{{ docDetail.totalQty }}</el-descriptions-item>
                  <el-descriptions-item label="单价">&yen;{{ Number(docDetail.unitPrice || 0).toFixed(2) }}</el-descriptions-item>
                  <el-descriptions-item label="总金额(含税)">
                    <span style="font-weight: 700; color: #f56c6c">&yen;{{ Number(docDetail.totalAmount || 0).toFixed(2) }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="税率">{{ docDetail.taxRate || 0 }}%</el-descriptions-item>
                  <el-descriptions-item label="税额">&yen;{{ Number(docDetail.taxAmount || 0).toFixed(2) }}</el-descriptions-item>
                  <el-descriptions-item label="发货数量">{{ docDetail.deliveryQty || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="状态">
                    <el-tag :type="outsourceStatusType(docDetail.status)" size="small">{{ outsourceStatusText(docDetail.status) }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="交付日期">{{ docDetail.deliveryDate }}</el-descriptions-item>
                  <el-descriptions-item label="创建时间">{{ docDetail.createTime }}</el-descriptions-item>
                </el-descriptions>
                <div style="margin-top: 16px; padding: 12px; background: #f5f7fa; border-radius: 8px">
                  <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; color: #606266">
                    <span>加工进度</span>
                    <span>{{ docDetail.qualifiedQty || 0 }} / {{ docDetail.totalQty || 0 }}</span>
                  </div>
                  <el-progress
                    :percentage="docDetail.totalQty ? Math.min(Math.round(((docDetail.qualifiedQty || 0) / docDetail.totalQty) * 100), 100) : 0"
                    :stroke-width="16"
                    :text-inside="true"
                    :color="(docDetail.qualifiedQty || 0) >= (docDetail.totalQty || 0) ? '#67C23A' : '#409EFF'"
                  />
                </div>
              </el-tab-pane>
              <el-tab-pane label="发料记录" name="delivery">
                <el-table :data="docDetail.deliveries || []" border stripe size="small">
                  <el-table-column type="index" label="#" width="50" align="center" />
                  <el-table-column prop="materialName" label="物料" min-width="140" />
                  <el-table-column prop="quantity" label="发料数量" width="100" align="center" />
                  <el-table-column prop="batchNo" label="批次号" min-width="120" />
                  <el-table-column prop="createTime" label="发料时间" min-width="160" />
                  <el-table-column prop="remark" label="备注" min-width="120" />
                </el-table>
                <el-empty v-if="!(docDetail.deliveries || []).length" description="暂无发料记录" />
              </el-tab-pane>
              <el-tab-pane label="收货/质检" name="receipt">
                <el-table :data="docDetail.receipts || []" border stripe size="small">
                  <el-table-column type="index" label="#" width="50" align="center" />
                  <el-table-column prop="quantity" label="收货数量" width="100" align="center" />
                  <el-table-column prop="qualifiedQty" label="合格数" width="90" align="center" />
                  <el-table-column label="不合格数" width="90" align="center">
                    <template #default="{ row }">
                      <span :style="{ color: (row.quantity - (row.qualifiedQty || 0)) > 0 ? '#f56c6c' : '#67c23a' }">
                        {{ row.quantity - (row.qualifiedQty || 0) }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="qcStatus" label="质检状态" width="100" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.qcStatus === 'passed' ? 'success' : row.qcStatus === 'failed' ? 'danger' : 'warning'" size="small">
                        {{ { pending: '待检', passed: '合格', failed: '不合格' }[row.qcStatus] || row.qcStatus || '待检' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createTime" label="收货时间" min-width="160" />
                  <el-table-column prop="remark" label="备注" min-width="120" />
                </el-table>
                <el-empty v-if="!(docDetail.receipts || []).length" description="暂无收货记录" />
              </el-tab-pane>
              <el-tab-pane label="费用结算" name="cost">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="单价">&yen;{{ Number(docDetail.unitPrice || 0).toFixed(2) }}</el-descriptions-item>
                  <el-descriptions-item label="合格数量">{{ docDetail.qualifiedQty || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="结算金额">
                    <span style="font-weight: 700; color: #f56c6c">
                      &yen;{{ (Number(docDetail.unitPrice || 0) * Number(docDetail.qualifiedQty || 0)).toFixed(2) }}
                    </span>
                  </el-descriptions-item>
                  <el-descriptions-item label="结算状态">
                    <el-tag :type="docDetail.status === 'completed' ? 'success' : 'info'" size="small">
                      {{ docDetail.status === 'completed' ? '已结算' : '待结算' }}
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </el-tab-pane>
            </el-tabs>
          </template>
          <!-- 采购单详情 -->
          <template v-else-if="docDetailType === 'purchase'">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="采购单号">{{ docDetail.poNo || docDetail.orderNo }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ docDetail.statusText || docDetail.status }}</el-descriptions-item>
              <el-descriptions-item label="供应商">{{ docDetail.supplierName }}</el-descriptions-item>
              <el-descriptions-item label="总金额">{{ docDetail.totalAmount }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ docDetail.createTime }}</el-descriptions-item>
              <el-descriptions-item label="备注">{{ docDetail.remark || '--' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="docDetail.items && docDetail.items.length" style="margin-top: 16px">
              <h4 style="margin-bottom: 8px">采购明细</h4>
              <el-table :data="docDetail.items" border size="small">
                <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
                <el-table-column prop="quantity" label="数量" width="80" align="center" />
                <el-table-column prop="unitPrice" label="单价" width="100" align="center" />
                <el-table-column prop="amount" label="金额" width="100" align="center" />
              </el-table>
            </div>
          </template>
        </template>
      </div>
    </el-drawer>

    <!-- fullscreen inspection execution dialog -->
    <el-dialog
      v-model="inspectionDialogVisible"
      title="检验执行"
      fullscreen
      destroy-on-close
      class="inspection-dialog"
      @close="resetInspectionDialog"
    >
      <div v-loading="inspectionLoading" class="inspection-wrapper">
        <!-- left panel: 70% -->
        <div class="inspection-left">
          <!-- task info header - 按质检类型展示不同的追溯信息 -->
          <el-card shadow="never" class="inspection-info-card">
            <el-descriptions :column="4" border size="small">
              <el-descriptions-item label="任务编号">{{ inspectionTask.taskNo }}</el-descriptions-item>
              <el-descriptions-item label="检验类型">
                <el-tag :type="qcTypeTagType(inspectionTask.qcType)" size="small" effect="plain">
                  {{ qcTypeText(inspectionTask.qcType) }}
                </el-tag>
              </el-descriptions-item>
              <!-- 来料检 -->
              <template v-if="inspectionTask.qcType === 'incoming'">
                <el-descriptions-item label="物料名称">{{ inspectionTask.materialName }}</el-descriptions-item>
                <el-descriptions-item label="供应商">{{ inspectionTask.supplierName }}</el-descriptions-item>
                <el-descriptions-item label="采购单号">{{ inspectionTask.purchaseOrderNo }}</el-descriptions-item>
                <el-descriptions-item label="批次号">{{ inspectionTask.batchNo }}</el-descriptions-item>
              </template>
              <!-- 过程检 -->
              <template v-else-if="inspectionTask.qcType === 'process'">
                <el-descriptions-item label="工单号">{{ inspectionTask.workOrderNo }}</el-descriptions-item>
                <el-descriptions-item label="产品名称">{{ inspectionTask.productName }}</el-descriptions-item>
                <el-descriptions-item label="工序名称">{{ inspectionTask.stepName }}</el-descriptions-item>
                <el-descriptions-item label="执行人">{{ inspectionTask.assigneeName }}</el-descriptions-item>
              </template>
              <!-- 成品检 -->
              <template v-else-if="inspectionTask.qcType === 'final'">
                <el-descriptions-item label="工单号">{{ inspectionTask.workOrderNo }}</el-descriptions-item>
                <el-descriptions-item label="产品名称">{{ inspectionTask.productName }}</el-descriptions-item>
                <el-descriptions-item label="批次号">{{ inspectionTask.batchNo }}</el-descriptions-item>
                <el-descriptions-item label="数量">{{ inspectionTask.planQty }}</el-descriptions-item>
              </template>
              <!-- 外协检 -->
              <template v-else-if="inspectionTask.qcType === 'outsource'">
                <el-descriptions-item label="工单号">{{ inspectionTask.workOrderNo }}</el-descriptions-item>
                <el-descriptions-item label="产品名称">{{ inspectionTask.productName }}</el-descriptions-item>
                <el-descriptions-item label="外协单号">{{ inspectionTask.outsourceOrderNo }}</el-descriptions-item>
                <el-descriptions-item label="供应商">{{ inspectionTask.supplierName }}</el-descriptions-item>
                <el-descriptions-item label="工序名称">{{ inspectionTask.stepName }}</el-descriptions-item>
              </template>
              <!-- 默认 -->
              <template v-else>
                <el-descriptions-item label="产品名称">{{ inspectionTask.productName || inspectionTask.materialName }}</el-descriptions-item>
                <el-descriptions-item label="批次号">{{ inspectionTask.batchNo }}</el-descriptions-item>
              </template>
              <el-descriptions-item label="检验模板">{{ inspectionTask.templateName }}</el-descriptions-item>
              <el-descriptions-item label="检验员">{{ inspectionTask.inspectorName }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="statusTagType(inspectionTask.result)" size="small">
                  {{ statusText(inspectionTask.result) }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- inspection items table -->
          <el-card shadow="never" class="inspection-items-card">
            <template #header>
              <div class="inspection-items-header">
                <span>检验项目明细</span>
                <el-tag size="small" type="info">共 {{ inspectionItems.length }} 项</el-tag>
                <el-tag v-if="inspectionSampleQty > 0" size="small" type="warning" style="margin-left:8px">
                  质检数量: {{ inspectionSampleQty }}
                </el-tag>
                <div v-if="!isReadonly" style="margin-left: auto; display: flex; gap: 8px; align-items: center">
                  <el-select
                    v-model="selectedTemplateId"
                    :placeholder="inspectionItems.length === 0 ? '请选择质检模板（必选）' : '更换质检模板'"
                    :class="{ 'is-required-highlight': inspectionItems.length === 0 }"
                    clearable
                    filterable
                    size="small"
                    style="width: 220px"
                    @change="onTemplateSelected"
                  >
                    <el-option v-for="t in templateOptions" :key="t.id" :label="t.templateName" :value="t.id" />
                  </el-select>
                </div>
              </div>
            </template>
            <el-table :data="inspectionItems" border stripe size="small" max-height="calc(100vh - 340px)">
              <el-table-column type="index" label="#" width="45" align="center" />
              <el-table-column prop="itemName" label="检验项目" min-width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  <span>{{ row.itemName }}</span>
                  <el-tag v-if="row.isRequired === 1" type="danger" size="small" effect="plain" style="margin-left:4px">必填</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="75" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.itemType === 'quantitative'" size="small">计量</el-tag>
                  <el-tag v-else-if="row.itemType === 'text'" type="info" size="small">文本</el-tag>
                  <el-tag v-else-if="row.itemType === 'radio'" type="warning" size="small">单选</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="standardValue" width="100" align="center">
                <template #header><span style="color:#f56c6c">* </span>标准值</template>
                <template #default="{ row }">
                  <template v-if="row.itemType === 'quantitative'">
                    <el-input v-if="!isReadonly" v-model="row.standardValue" size="small" placeholder="标准值" style="width: 80px" />
                    <span v-else>{{ row.standardValue || '--' }}</span>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="toleranceMin" width="100" align="center">
                <template #header><span style="color:#f56c6c">* </span>下限值</template>
                <template #default="{ row }">
                  <template v-if="row.itemType === 'quantitative'">
                    <el-input v-if="!isReadonly" v-model="row.toleranceMin" size="small" placeholder="下限" style="width: 80px" />
                    <span v-else>{{ row.toleranceMin || '--' }}</span>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="toleranceMax" width="100" align="center">
                <template #header><span style="color:#f56c6c">* </span>上限值</template>
                <template #default="{ row }">
                  <template v-if="row.itemType === 'quantitative'">
                    <el-input v-if="!isReadonly" v-model="row.toleranceMax" size="small" placeholder="上限" style="width: 80px" />
                    <span v-else>{{ row.toleranceMax || '--' }}</span>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="80" align="center">
                <template #default="{ row }">
                  <template v-if="row.itemType === 'quantitative'">
                    <el-input v-if="!isReadonly" v-model="row.unit" size="small" placeholder="单位" style="width: 60px" />
                    <span v-else>{{ row.unit || '--' }}</span>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="method" label="质检要求" min-width="130" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-input v-if="!isReadonly" v-model="row.method" size="small" placeholder="质检要求" />
                  <span v-else>{{ row.method || '--' }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 检验记录（合格/不合格维度录入）— 移至左侧 -->
          <el-card shadow="never" class="inspection-conclusion-card" style="margin-top: 12px">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span>检验记录
                  <span v-if="inspectionSampleQty > 0" style="font-size:12px;color:#909399;font-weight:normal;margin-left:6px">
                    剩余可填: {{ inspectionRemainingQty }} / {{ inspectionSampleQty }}
                  </span>
                </span>
                <div v-if="!isReadonly" style="display:flex;gap:6px">
                  <el-button type="danger" size="small" plain
                             :disabled="inspectionRemainingQty <= 0"
                             @click="addInspectionRecord('unqualified')">+ 不合格</el-button>
                  <el-button type="success" size="small" plain
                             :disabled="inspectionRemainingQty <= 0"
                             @click="addInspectionRecord('qualified')">+ 合格</el-button>
                </div>
              </div>
            </template>
            <div v-for="(rec, idx) in inspectionRecords" :key="idx"
              style="border:1px solid #ebeef5;border-radius:6px;padding:12px;margin-bottom:10px">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
                <div style="display:flex;align-items:center;gap:8px">
                  <span style="font-weight:600">检验数</span>
                  <el-tag :type="rec.recordType === 'unqualified' ? 'danger' : 'success'" size="small">
                    {{ rec.recordType === 'unqualified' ? '不合格' : '合格' }}
                  </el-tag>
                </div>
                <div style="display:flex;align-items:center;gap:6px">
                  <el-input-number v-model="rec.quantity" :min="0" :max="maxQtyForRecord(rec)"
                    :precision="4" :disabled="isReadonly"
                    size="small" style="width:130px" @change="recalcQtyFromRecords" />
                </div>
              </div>
              <div v-for="(item, iIdx) in (rec.items || [])" :key="iIdx"
                style="display:flex;align-items:center;gap:8px;margin-bottom:4px;padding-left:12px;font-size:13px;color:#606266">
                <span style="min-width:80px">
                  <span v-if="item.isRequired === 1" style="color:#f56c6c">* </span>
                  {{ item.itemName }}
                </span>
                <!-- 计量型：输入实测值 -->
                <template v-if="item.itemType === 'quantitative' || !item.itemType">
                  <el-input v-model="item.actualValue" :disabled="isReadonly" size="small" style="width:80px"
                    :placeholder="item.isRequired === 1 ? '实测值(必填)' : '实测值'" />
                  <span style="color:#909399;font-size:12px">
                    {{ item.toleranceMin || '' }}{{ item.toleranceMin && item.toleranceMax ? '~' : '' }}{{ item.toleranceMax || '' }}
                    {{ item.standardValue ? '标准:' + item.standardValue : '' }}
                  </span>
                </template>
                <!-- 文本型：输入文本描述 -->
                <template v-else-if="item.itemType === 'text'">
                  <el-input v-model="item.actualValue" :disabled="isReadonly" size="small" style="width:200px"
                    :placeholder="item.isRequired === 1 ? '检验描述(必填)' : '检验描述'" />
                </template>
                <!-- 单选项：合格/不合格 -->
                <template v-else-if="item.itemType === 'radio'">
                  <el-radio-group v-model="item.actualValue" :disabled="isReadonly" size="small">
                    <el-radio label="合格">合格</el-radio>
                    <el-radio label="不合格">不合格</el-radio>
                  </el-radio-group>
                </template>
              </div>
              <div v-if="!isReadonly" style="text-align:right;margin-top:6px">
                <el-button type="danger" link size="small" @click="removeInspectionRecord(idx)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="inspectionRecords.length === 0" description="点击上方按钮添加检验记录" :image-size="40" />
          </el-card>
        </div>

        <!-- right panel: 30% -->
        <div class="inspection-right">
          <!-- 数量汇总 -->
          <el-card shadow="never" class="inspection-conclusion-card">
            <template #header>数量汇总</template>
            <div style="padding:10px;background:#f5f7fa;border-radius:6px">
              <el-row :gutter="12">
                <el-col :span="6">
                  <div style="text-align:center">
                    <div style="font-size:12px;color:#909399">总数量</div>
                    <div style="font-size:18px;font-weight:600">{{ inspectionTask.planQty || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div style="text-align:center">
                    <div style="font-size:12px;color:#909399">质检数量</div>
                    <div style="font-size:18px;font-weight:600;color:#409eff">{{ inspectionSampleQty }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div style="text-align:center">
                    <div style="font-size:12px;color:#909399">合格数</div>
                    <div style="font-size:18px;font-weight:600;color:#67c23a">{{ inspectionPassQty }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div style="text-align:center">
                    <div style="font-size:12px;color:#909399">不合格数</div>
                    <div style="font-size:18px;font-weight:600;color:#f56c6c">{{ inspectionFailQty }}</div>
                  </div>
                </el-col>
              </el-row>
              <el-progress v-if="inspectionSampleQty > 0" style="margin-top:8px"
                :percentage="Math.min(100, Math.round((inspectionPassQty + inspectionFailQty) / inspectionSampleQty * 100))"
                :color="inspectionPassQty + inspectionFailQty >= inspectionSampleQty ? '#67c23a' : '#409eff'"
                :stroke-width="8" />
            </div>

            <!-- 备注 -->
            <el-form label-position="top" style="margin-top:12px">
              <el-form-item label="检验备注">
                <el-input v-model="inspectionRemark" :disabled="isReadonly" type="textarea" :rows="3" placeholder="检验备注..." />
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 处置记录（仅在已完成且有处置时展示） -->
          <el-card v-if="inspectionTask.disposalRecords && inspectionTask.disposalRecords.length" shadow="never" style="margin-bottom:12px">
            <template #header>处置记录</template>
            <div v-for="(d, idx) in inspectionTask.disposalRecords" :key="idx"
              style="border:1px solid #ebeef5;border-radius:6px;padding:10px;margin-bottom:8px">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="处置方式">
                  <el-tag :type="{ repair: 'primary', concession: 'warning', scrap: 'danger' }[d.disposalType]" size="small">
                    {{ { repair: '返修', concession: '让步接收', scrap: '报废' }[d.disposalType] || d.disposalType }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="处置数量">{{ d.quantity }}</el-descriptions-item>
                <el-descriptions-item label="处置原因" :span="2">{{ d.disposalReason || '-' }}</el-descriptions-item>
                <el-descriptions-item label="处置时间">{{ $fmtDate(d.processedAt) }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag :type="d.status === 'processed' ? 'success' : 'info'" size="small">
                    {{ d.status === 'processed' ? '已处置' : '待处置' }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </el-card>

          <!-- submit button -->
          <el-button
            v-if="!isReadonly"
            type="primary"
            size="large"
            class="inspection-submit-btn"
            :loading="inspectionSubmitLoading"
            @click="handleInspectionSubmit"
          >
            <el-icon style="margin-right: 6px"><CircleCheck /></el-icon>
            提交检验结果
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 产品选择弹窗 -->
    <MaterialPickerDialog v-model="taskProductPickerVisible" title="选择产品" :multiple="false"
                          @select="onTaskProductSelected" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { getQcTaskPage, getQcTaskDetail, createQcTask, submitQcResult, disposeQcTask, getQcTemplateSelect, getQcTemplateDetail, getQcSchemeDetail } from '@/api/quality'
import { getWorkOrderDetail, getWorkOrderPage } from '@/api/production'
import { getOutsourceOrderDetail } from '@/api/outsource'
import { getPurchaseOrderDetail, getPurchaseOrderDetailByNo } from '@/api/purchase'
import { getTeamList, getTeamMembers } from '@/api/team'
import { getProcessSelect } from '@/api/master'
import { getUserSelect } from '@/api/system'
import StatCard from '@/components/StatCard.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

const QUALITY_TASK_DEFAULT_COLUMNS = [
  { key: 'batchNo', label: '批次号', minWidth: 120 },
  { key: 'templateName', label: '检验模板', minWidth: 130 },
  { key: 'planQty', label: '数量', minWidth: 80, align: 'center' },
  { key: 'passQty', label: '合格数', minWidth: 80, align: 'center' },
  { key: 'failQty', label: '不合格数', minWidth: 80, align: 'center' },
  { key: 'inspectorName', label: '检验员', minWidth: 100 },
  { key: 'result', label: '状态', minWidth: 90, align: 'center' },
  { key: 'createTime', label: '创建时间', minWidth: 170 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('quality_task', QUALITY_TASK_DEFAULT_COLUMNS)

// --- state ---
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)

// 来源页签
const activeSourceTab = ref('work_order')
function onSourceTabChange(tab) {
  activeSourceTab.value = tab
  queryParams.relatedOrderType = tab
  // 根据来源自动关联检验类型
  if (tab === 'work_order') queryParams.qcType = 'process'
  else if (tab === 'purchase_receipt') queryParams.qcType = 'incoming'
  else if (tab === 'outsource_receipt') queryParams.qcType = 'outsource'
  else if (tab === 'rework_order') queryParams.qcType = 'rework'
  queryParams.pageNum = 1
  loadData()
  loadStats()
}

const queryParams = reactive({
  taskNo: '',
  workOrderNo: '',
  qcType: 'process',
  relatedOrderType: 'work_order',
  result: '',
  pageNum: 1,
  pageSize: 10
})

// --- stat counts computed from loaded table data ---
// 卡片统计: 不能跟着筛选变, 单独拉一份"按源类型分类"的全量数据
const statsList = ref([])
const statCounts = computed(() => {
  const list = statsList.value
  const today = new Date().toISOString().slice(0, 10)
  return {
    pending: list.filter(r => r.result === 'pending').length,
    inProgress: list.filter(r => r.result === 'inspecting').length,
    todayPass: list.filter(r => r.result === 'pass' && r.createTime && r.createTime.startsWith(today)).length,
    todayFail: list.filter(r => r.result === 'fail' && r.createTime && r.createTime.startsWith(today)).length
  }
})

async function loadStats() {
  try {
    // 拉所有任务(只按源类型分组,不带 result/date 等筛选), 用于卡片计数
    const params = {
      qcType: queryParams.qcType || undefined,
      relatedOrderType: queryParams.relatedOrderType || undefined,
      pageNum: 1,
      pageSize: 99999
    }
    const res = await getQcTaskPage(params)
    statsList.value = res.data?.records || []
  } catch { statsList.value = [] }
}

// --- helpers ---
function statusTagType(status) {
  if (!status) return 'info'
  const map = { pending: 'warning', inspecting: '', pass: 'success', fail: 'danger', completed: 'success' }
  return map[status.toLowerCase()] || 'info'
}
function statusText(status) {
  if (!status) return '-'
  const map = { pending: '待检验', inspecting: '检验中', pass: '合格', fail: '不合格', completed: '检测完成' }
  return map[status.toLowerCase()] || status
}
function qcTypeTagType(qcType) {
  const map = { incoming: '', process: 'success', final: 'warning', outsource: 'danger', rework: 'primary' }
  return map[qcType] || 'info'
}
function qcTypeText(qcType) {
  const map = { incoming: '来料检验', process: '过程检验', final: '成品检验', outsource: '外协检验', rework: '返修检验' }
  return map[qcType] || qcType || '--'
}

/** 根据当前筛选的检验类型决定是否显示该类型的列 */
function showCol(type) {
  return queryParams.qcType === type
}

// --- load data ---
async function loadData() {
  loading.value = true
  try {
    const params = {
      taskNo: queryParams.taskNo || undefined,
      workOrderNo: queryParams.workOrderNo || undefined,
      qcType: queryParams.qcType || undefined,
      relatedOrderType: queryParams.relatedOrderType || undefined,
      result: queryParams.result || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await getQcTaskPage(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function filterByCard(kind) {
  // pending / inspecting → 仅按 result 过滤; today_pass / today_fail → 加上今日日期范围
  const today = new Date().toISOString().slice(0, 10)
  if (kind === 'pending') {
    queryParams.result = queryParams.result === 'pending' ? '' : 'pending'
    queryParams.startDate = ''
    queryParams.endDate = ''
  } else if (kind === 'inspecting') {
    queryParams.result = queryParams.result === 'inspecting' ? '' : 'inspecting'
    queryParams.startDate = ''
    queryParams.endDate = ''
  } else if (kind === 'today_pass') {
    queryParams.result = 'pass'
    queryParams.startDate = today
    queryParams.endDate = today
  } else if (kind === 'today_fail') {
    queryParams.result = 'fail'
    queryParams.startDate = today
    queryParams.endDate = today
  }
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.taskNo = ''
  queryParams.workOrderNo = ''
  queryParams.result = ''
  queryParams.pageNum = 1
  // 保持当前页签的过滤条件
  onSourceTabChange(activeSourceTab.value)
}

// --- create task dialog ---
const addDialogVisible = ref(false)
const addFormRef = ref(null)
const addForm = ref({
  qcType: 'process',
  workOrderId: null,
  productId: null,
  productName: '',
  templateId: null,
  inspectorId: null,
  planQty: 1,
  remark: ''
})

// 产品选择弹窗
const taskProductPickerVisible = ref(false)
function onTaskProductSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  addForm.value.productId = m.id
  addForm.value.productName = m.materialName || m.name || ''
}
const addRules = {
  qcType: [{ required: true, message: '请选择检验类型', trigger: 'change' }],
  workOrderId: [{ required: true, message: '请选择工单', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  inspectorId: [{ required: true, message: '请选择检验员', trigger: 'change' }],
  planQty: [{ required: true, message: '请输入计划数量', trigger: 'blur' }]
}

// 下拉选项（templateOptions 复用后文声明的同名变量）
const workOrderOptions = ref([])
const woSelectLoading = ref(false)
const inspectorOptions = ref([])

async function searchWorkOrders(keyword) {
  woSelectLoading.value = true
  try {
    const res = await getWorkOrderPage({ keyword: keyword || undefined, pageNum: 1, pageSize: 30 })
    workOrderOptions.value = res.data?.records || []
  } catch {} finally { woSelectLoading.value = false }
}

function onWorkOrderChange(id) {
  const wo = workOrderOptions.value.find(w => w.id === id)
  if (wo) {
    if (wo.productId) {
      addForm.value.productId = wo.productId
      addForm.value.productName = wo.productName || ''
    }
    if (wo.planQty) addForm.value.planQty = Number(wo.planQty)
  }
}

async function loadAddOptions() {
  try {
    const [tRes, uRes] = await Promise.all([
      getQcTemplateSelect({ status: 'active' }).catch(() => ({ data: [] })),
      getUserSelect()
    ])
    templateOptions.value = tRes.data || []
    inspectorOptions.value = uRes.data || []
  } catch {}
}

function handleAdd() {
  addForm.value = {
    qcType: 'process',
    workOrderId: null,
    productId: null,
    templateId: null,
    inspectorId: null,
    planQty: 1,
    remark: ''
  }
  loadAddOptions()
  searchWorkOrders('')
  addDialogVisible.value = true
}

async function handleAddSubmit() {
  await addFormRef.value.validate()
  submitLoading.value = true
  try {
    const payload = { ...addForm.value }
    // 根据检验类型自动填充关联类型和关联单ID,保证列表 tab 能筛到
    if (['process', 'final'].includes(payload.qcType)) {
      payload.relatedOrderType = 'work_order'
      payload.relatedOrderId = payload.workOrderId
    } else if (payload.qcType === 'incoming') {
      payload.relatedOrderType = 'purchase_receipt'
    } else if (payload.qcType === 'outsource') {
      payload.relatedOrderType = 'outsource_receipt'
    }
    await createQcTask(payload)
    ElMessage.success('创建成功')
    addDialogVisible.value = false
    // 切到对应 tab 让用户看到刚创建的任务
    if (payload.relatedOrderType && payload.relatedOrderType !== queryParams.relatedOrderType) {
      activeSourceTab.value = payload.relatedOrderType
      onSourceTabChange(payload.relatedOrderType)
    } else {
      loadData()
    }
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetAddForm() {
  addFormRef.value?.resetFields()
}

// --- fullscreen inspection dialog ---
const inspectionDialogVisible = ref(false)
const inspectionLoading = ref(false)
const inspectionSubmitLoading = ref(false)
const inspectionTask = ref({})
const inspectionItems = ref([])
const inspectionConclusion = ref('')
const inspectionRemark = ref('')
const inspectionPassQty = ref(0)
const inspectionFailQty = ref(0)

const isReadonly = computed(() => {
  const result = inspectionTask.value.result
  return result === 'completed' || result === 'pass' || result === 'fail'
})

/**
 * 计算质检数量：优先用已保存的sampleQty，否则根据模板关联的质检方案计算
 */
async function calcSampleQty(detail) {
  // 1. 已保存的sampleQty
  if (detail.sampleQty && Number(detail.sampleQty) > 0) {
    inspectionSampleQty.value = Number(detail.sampleQty)
    return
  }
  // 2. onTemplateSelected已计算过（非0说明已计算）
  if (inspectionSampleQty.value > 0) return
  // 3. 通过模板→质检方案计算
  const templateId = detail.templateId || selectedTemplateId.value
  if (!templateId) {
    inspectionSampleQty.value = Number(detail.planQty || 0)
    return
  }
  try {
    const tmplRes = await getQcTemplateDetail(templateId)
    const tmpl = tmplRes.data || {}
    if (!tmpl.schemeId) {
      inspectionSampleQty.value = Number(detail.planQty || 0)
      return
    }
    const schemeRes = await getQcSchemeDetail(tmpl.schemeId)
    const scheme = schemeRes.data || {}
    const planQty = Number(detail.planQty || 0)
    if (scheme.schemeType === 'full') {
      inspectionSampleQty.value = planQty
    } else if (scheme.schemeType === 'sampling') {
      switch (scheme.samplingMode) {
        case 'first_piece':
        case 'last_piece':
          inspectionSampleQty.value = 1
          break
        case 'custom_qty':
          inspectionSampleQty.value = Math.min(Number(scheme.samplingQty || planQty), planQty)
          break
        case 'ratio':
          if (scheme.samplingRatio) {
            const raw = planQty * Number(scheme.samplingRatio) / 100
            const sq = scheme.ratioRoundUp ? Math.ceil(raw) : Math.floor(raw)
            inspectionSampleQty.value = Math.max(1, Math.min(sq, planQty))
          } else {
            inspectionSampleQty.value = planQty
          }
          break
        default:
          inspectionSampleQty.value = planQty
      }
    } else {
      inspectionSampleQty.value = planQty
    }
  } catch {
    inspectionSampleQty.value = Number(detail.planQty || 0)
  }
}

// isWithinTolerance / onActualValueChange 已移除

/** Summary computed from inspection items */
const inspectionSummary = computed(() => {
  const items = inspectionItems.value
  const total = items.length
  const filled = items.filter(i => i.actualValue != null && i.actualValue !== '').length
  const passed = items.filter(i => i.itemResult === 'PASS').length
  const failed = items.filter(i => i.itemResult === 'FAIL').length
  const judged = passed + failed
  const passRate = judged > 0 ? Math.round((passed / judged) * 100) : 0
  return { total, filled, passed, failed, passRate }
})

// 质检数量（根据质检方案计算）
const inspectionSampleQty = ref(0)

// 检验记录（合格/不合格维度多条录入，参照需求图片）
const inspectionRecords = ref([])

function addInspectionRecord(type) {
  inspectionRecords.value.push({
    recordType: type, // 'qualified' or 'unqualified'
    quantity: 0,
    remark: '',
    // 每条记录独立的检验项填写值
    items: inspectionItems.value.map(item => ({
      itemName: item.itemName,
      itemType: item.itemType || 'quantitative',
      isRequired: item.isRequired || 0,
      standardValue: item.standardValue || '',
      toleranceMin: item.toleranceMin || '',
      toleranceMax: item.toleranceMax || '',
      actualValue: item.defaultValue || ''
    }))
  })
}

function removeInspectionRecord(index) {
  inspectionRecords.value.splice(index, 1)
  recalcQtyFromRecords()
}

function recalcQtyFromRecords() {
  const records = inspectionRecords.value
  const passTotal = records.filter(r => r.recordType === 'qualified')
      .reduce((sum, r) => sum + Number(r.quantity || 0), 0)
  const failTotal = records.filter(r => r.recordType === 'unqualified')
      .reduce((sum, r) => sum + Number(r.quantity || 0), 0)
  inspectionPassQty.value = passTotal
  inspectionFailQty.value = failTotal
  // 自动设置结论
  if (failTotal > 0) {
    inspectionConclusion.value = 'FAILED'
  } else if (records.length > 0) {
    inspectionConclusion.value = 'PASSED'
  }
}

/**
 * 单条检验记录数量上限 = 质检数量 - 其他所有记录已填数量
 * 防止"合格 + 不合格"合计超过 sampleQty(抽检)/planQty(全检)
 */
function maxQtyForRecord(rec) {
  const cap = Number(inspectionSampleQty.value || 0)
  if (cap <= 0) return undefined  // 无上限信息时不限制
  const otherTotal = inspectionRecords.value
      .filter(r => r !== rec)
      .reduce((sum, r) => sum + Number(r.quantity || 0), 0)
  const left = cap - otherTotal
  return left > 0 ? left : 0
}

/** 还能再加记录的剩余数量(用于禁用 +合格 / +不合格 按钮) */
const inspectionRemainingQty = computed(() => {
  const cap = Number(inspectionSampleQty.value || 0)
  if (cap <= 0) return Infinity
  const used = Number(inspectionPassQty.value || 0) + Number(inspectionFailQty.value || 0)
  return Math.max(0, cap - used)
})

/** Open the fullscreen inspection dialog and load task detail */
async function handleOpenInspection(row) {
  inspectionDialogVisible.value = true
  inspectionLoading.value = true
  inspectionConclusion.value = ''
  inspectionRemark.value = ''
  inspectionPassQty.value = 0
  inspectionFailQty.value = 0
  inspectionSampleQty.value = Number(row.sampleQty || 0)
  inspectionTask.value = { ...row }
  inspectionItems.value = []
  inspectionRecords.value = []
  selectedTemplateId.value = null
  await loadTemplateOptions()

  try {
    // If the task is pending, start it (mark as inspecting)
    if (row.result === 'pending') {
      await submitQcResult(row.id, { results: [], conclusion: null })
      row.result = 'inspecting'
      inspectionTask.value.result = 'inspecting'
    }

    const res = await getQcTaskDetail(row.id)
    const detail = res.data || {}

    // Merge detail data into task info
    Object.assign(inspectionTask.value, detail)

    // Load inspection items from results (auto-generated from template or already submitted)
    const items = detail.results || detail.items || detail.inspectionItems || []
    inspectionItems.value = items.map(item => ({
      ...item,
      itemType: item.itemType || item.dataType || 'quantitative',
      isRequired: item.isRequired || 0,
      actualValue: item.actualValue ?? item.defaultValue ?? null,
      itemResult: item.isPass === 1 ? 'PASS' : item.isPass === 0 ? 'FAIL' : (item.itemResult || '')
    }))

    // Pre-fill conclusion if already set
    if (detail.conclusion) {
      inspectionConclusion.value = detail.conclusion
    }
    if (detail.remark) {
      inspectionRemark.value = detail.remark
    }

    // 加载已有的检验记录
    if (detail.records && detail.records.length > 0) {
      inspectionRecords.value = detail.records.map(r => {
        // 解析已保存的检验项填写值
        let savedItems = []
        if (r.defectItems) {
          try {
            savedItems = typeof r.defectItems === 'string' ? JSON.parse(r.defectItems) : r.defectItems
          } catch {}
        }
        // 根据 recordType 决定本条记录的预期值方向
        // 数据库里 qc_result 只有一份(全任务的总结果),但 qc_record 区分合格/不合格两条,
        // 所以前端要按 recordType 强制覆盖,避免合格记录显示"不合格"
        const isQualifiedRec = (r.recordType || 'qualified') === 'qualified'
        const fallbackForType = (itemType) => {
          const t = (itemType || '').toLowerCase()
          if (t === 'radio') return isQualifiedRec ? '合格' : '不合格'
          if (t === 'text' || t === 'input') return isQualifiedRec ? '正常' : '存在异常'
          return ''
        }
        // 以当前inspectionItems为基础，合并已保存的值
        const mergedItems = inspectionItems.value.map(item => {
          const saved = savedItems.find(s => s.itemName === item.itemName)
          // 优先级:
          // 1. defectItems JSON 里手动填的(用户真实编辑过) → 直接用
          // 2. 没有 → 按 recordType 自动填(合格记录全合格,不合格记录全不合格)
          let actualValue
          if (saved && saved.actualValue) {
            actualValue = saved.actualValue
          } else {
            actualValue = fallbackForType(item.itemType) || item.defaultValue || ''
          }
          return {
            itemName: item.itemName,
            itemType: item.itemType || 'quantitative',
            isRequired: item.isRequired || 0,
            standardValue: item.standardValue || '',
            toleranceMin: item.toleranceMin || '',
            toleranceMax: item.toleranceMax || '',
            actualValue
          }
        })
        return {
          recordType: r.recordType || 'qualified',
          quantity: Number(r.quantity || 0),
          remark: r.remark || '',
          items: mergedItems
        }
      })
      recalcQtyFromRecords()
    }

    // 如果有模板但检验项为空，自动加载模板检验项
    if (inspectionItems.value.length === 0 && detail.templateId) {
      selectedTemplateId.value = detail.templateId
      await onTemplateSelected(detail.templateId)
    } else if (inspectionItems.value.length === 0 && !detail.templateId) {
      ElMessage.warning('请先选择质检模板导入检验项')
    }

    // 计算质检数量（始终执行，不跳过）
    await calcSampleQty(detail)
  } catch {
    // handled by interceptor
  } finally {
    inspectionLoading.value = false
  }
}

/** Submit inspection results from the fullscreen dialog */
async function handleInspectionSubmit() {
  // 强制要求有检验项（即必须选择了模板）
  if (inspectionItems.value.length === 0) {
    ElMessage.warning('请先选择质检模板导入检验项')
    return
  }

  // 计量型检验项：标准值、下限值、上限值至少填一项
  const missingStd = inspectionItems.value.find(i => i.itemType === 'quantitative' && !i.standardValue && !i.toleranceMin && !i.toleranceMax)
  if (missingStd) {
    ElMessage.warning(`计量型检验项「${missingStd.itemName}」的标准值、下限值、上限值至少填写一项`)
    return
  }

  // 要求至少有一条检验记录
  if (inspectionRecords.value.length === 0) {
    ElMessage.warning('请至少添加一条检验记录（合格或不合格）')
    return
  }

  // 检验记录合计不得超过质检数量(抽检 → sampleQty, 全检 → planQty)
  const cap = Number(inspectionSampleQty.value || 0)
  const total = Number(inspectionPassQty.value || 0) + Number(inspectionFailQty.value || 0)
  if (cap > 0 && total > cap) {
    ElMessage.warning(`合格 + 不合格 合计 (${total}) 不能超过质检数量 (${cap})`)
    return
  }

  // 每条记录的必填检验项实测值必填
  for (let rIdx = 0; rIdx < inspectionRecords.value.length; rIdx++) {
    const rec = inspectionRecords.value[rIdx]
    const recLabel = rec.recordType === 'unqualified' ? '不合格' : '合格'
    const missingItem = (rec.items || []).find(i => i.isRequired === 1 && (!i.actualValue || i.actualValue === ''))
    if (missingItem) {
      ElMessage.warning(`第${rIdx + 1}条${recLabel}记录中，必填检验项「${missingItem.itemName}」未填写`)
      return
    }
  }

  try {
    await ElMessageBox.confirm(
      `确认提交检验结果？合格数: ${inspectionPassQty.value}，不合格数: ${inspectionFailQty.value}`,
      '确认提交',
      { confirmButtonText: '确认提交', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // user cancelled
  }

  inspectionSubmitLoading.value = true
  try {
    await submitQcResult(inspectionTask.value.id, {
      templateId: selectedTemplateId.value || inspectionTask.value.templateId || undefined,
      sampleQty: inspectionSampleQty.value || undefined,
      conclusion: inspectionConclusion.value,
      remark: inspectionRemark.value,
      passQty: inspectionPassQty.value,
      failQty: inspectionFailQty.value,
      // 检验项结果
      results: inspectionItems.value.map(item => ({
        templateItemId: item.templateItemId || item.id,
        itemName: item.itemName,
        itemType: item.itemType,
        standardValue: item.standardValue,
        toleranceMin: item.toleranceMin,
        toleranceMax: item.toleranceMax,
        unit: item.unit,
        method: item.method,
        defaultValue: item.defaultValue,
        isRequired: item.isRequired,
        isCritical: item.isCritical,
        actualValue: item.actualValue != null ? String(item.actualValue) : '',
        isPass: item.itemResult === 'PASS' ? 1 : 0
      })),
      // 检验记录（合格/不合格维度）
      records: inspectionRecords.value.map(rec => ({
        recordType: rec.recordType,
        quantity: rec.quantity,
        remark: rec.remark || '',
        defectItems: (rec.items || []).map(it => ({
          itemName: it.itemName,
          itemType: it.itemType,
          isRequired: it.isRequired,
          standardValue: it.standardValue,
          actualValue: it.actualValue || ''
        }))
      }))
    })
    ElMessage.success('检验结果提交成功')
    inspectionDialogVisible.value = false
    loadData()
    // 不再自动弹出处置弹窗，用户通过列表"处置"按钮手动操作
  } catch {
    // handled by interceptor
  } finally {
    inspectionSubmitLoading.value = false
  }
}

function resetInspectionDialog() {
  inspectionTask.value = {}
  inspectionItems.value = []
  inspectionConclusion.value = ''
  inspectionRemark.value = ''
}

// --- dispose dialog ---
const disposeDialogVisible = ref(false)
const disposeSubmitLoading = ref(false)
const disposeFormRef = ref(null)
const disposeTask = ref({})
const disposeForm = ref({
  scope: 'defect',
  handlePath: 'repair',
  repairSteps: [{ processId: null, stepName: '' }],
  assignedTeamId: null,
  assignedUserId: null,
  concessionReason: '',
  scrapReason: '',
  remark: ''
})
const disposeRules = {
  scope: [{ required: true, message: '请选择处置范围', trigger: 'change' }],
  handlePath: [{ required: true, message: '请选择处置方式', trigger: 'change' }],
  concessionReason: [{ required: true, message: '请输入让步原因', trigger: 'blur' }],
  scrapReason: [{ required: true, message: '请输入报废原因', trigger: 'blur' }]
}
const teamOptions = ref([])
const teamMembers = ref([])
const woProcessTasks = ref([])
const processOptions = ref([])

// onDisposeTypeChange 已移除（返工选项已删除）

async function loadProcessOptions() {
  if (processOptions.value.length > 0) return
  try {
    const res = await getProcessSelect()
    processOptions.value = res.data || []
  } catch { /* ignore */ }
}

function onRepairStepSelect(idx, processId) {
  const p = processOptions.value.find(o => o.id === processId)
  if (p) {
    disposeForm.value.repairSteps[idx].stepName = p.name
  }
}

async function loadTeamOptions() {
  try {
    const res = await getTeamList()
    teamOptions.value = res.data || []
  } catch { /* ignore */ }
}

async function onTeamChange(teamId) {
  disposeForm.value.assignedUserId = null
  teamMembers.value = []
  if (teamId) {
    try {
      const res = await getTeamMembers(teamId)
      teamMembers.value = res.data || []
    } catch { /* ignore */ }
    const team = teamOptions.value.find(t => t.id === teamId)
    disposeForm.value.assignedTeamName = team ? team.teamName : ''
  }
}

function onUserChange(userId) {
  const member = teamMembers.value.find(m => m.userId === userId)
  disposeForm.value.assignedUserName = member ? member.realName : ''
}

async function loadWoProcessTasks() {
  const woId = disposeTask.value.workOrderId
  if (!woId) return
  try {
    const res = await getWorkOrderDetail(woId)
    woProcessTasks.value = (res.data?.tasks || []).filter(t => t.status !== 'cancelled')
  } catch { /* ignore */ }
}

const router = useRouter()
function goToReworkDisposal(row) {
  router.push({ path: '/quality/disposal' })
}

async function handleOpenDispose(row) {
  disposeTask.value = { ...row }
  disposeForm.value = {
    scope: 'defect',
    handlePath: 'repair',
    repairSteps: [{ processId: null, stepName: '' }],
    assignedTeamId: null,
    assignedUserId: null,
    concessionReason: '',
    scrapReason: '',
    remark: ''
  }
  teamMembers.value = []
  loadTeamOptions()
  loadProcessOptions()
  disposeDialogVisible.value = true
}

async function handleDisposeSubmit() {
  const form = disposeForm.value
  if (!form.handlePath) { ElMessage.warning('请选择处置方式'); return }
  if (form.handlePath === 'concession' && !form.concessionReason) { ElMessage.warning('请输入让步原因'); return }
  if (form.handlePath === 'scrap' && !form.scrapReason) { ElMessage.warning('请输入报废原因'); return }

  // 计算处置数量
  const quantity = form.scope === 'all' ? Number(disposeTask.value.planQty || 0) : Number(disposeTask.value.failQty || 0)
  const pathText = { repair: '返修', concession: '让步接收', scrap: '报废' }
  const scopeText = form.scope === 'all' ? '整批' : '仅不合格'

  try {
    await ElMessageBox.confirm(
      `确认对该质检任务执行【${pathText[form.handlePath]}】处置？\n范围：${scopeText}，数量：${quantity}`,
      '确认处置',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  disposeSubmitLoading.value = true
  try {
    const payload = {
      handlePath: form.handlePath,
      quantity,
      scope: form.scope,
      remark: form.remark
    }
    if (form.handlePath === 'repair') {
      payload.repairSteps = form.repairSteps.filter(s => s.stepName)
      payload.assignedTeamId = form.assignedTeamId
      payload.assignedUserId = form.assignedUserId
    } else if (form.handlePath === 'concession') {
      payload.concessionReason = form.concessionReason
    } else if (form.handlePath === 'scrap') {
      payload.scrapReason = form.scrapReason
    }

    await disposeQcTask(disposeTask.value.id, payload)
    ElMessage.success('处置成功！')
    disposeDialogVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    disposeSubmitLoading.value = false
  }
}

function resetDisposeForm() {
  disposeTask.value = {}
  woProcessTasks.value = []
  teamOptions.value = []
  teamMembers.value = []
}

// --- document detail drawer ---
const docDetailVisible = ref(false)
const docDetailLoading = ref(false)
const docDetailType = ref('')
const docDetailTab = ref('basic')
const docDetail = ref(null)

const docDetailTitle = computed(() => {
  const map = { workOrder: '工单详情', outsource: '外协加工单详情', purchase: '采购订单详情' }
  return map[docDetailType.value] || '单据详情'
})

function woStatusText(status) {
  const map = { pending: '待排产', scheduled: '已排产', in_progress: '生产中', completed: '已完工', closed: '已关闭' }
  return map[status] || status || '--'
}

function outsourceStatusType(status) {
  const map = { draft: 'info', confirmed: '', processing: 'warning', completed: 'success', cancelled: 'danger' }
  return map[status] || 'info'
}

function outsourceStatusText(status) {
  const map = { draft: '草稿', confirmed: '已确认', processing: '加工中', completed: '已完成', cancelled: '已取消' }
  return map[status] || status || '--'
}

async function openDocDetail(type, row) {
  docDetailType.value = type
  docDetailTab.value = 'basic'
  docDetail.value = null
  docDetailVisible.value = true
  docDetailLoading.value = true
  try {
    let res
    if (type === 'workOrder') {
      if (!row.workOrderId) { ElMessage.warning('工单ID不存在'); docDetailVisible.value = false; return }
      res = await getWorkOrderDetail(row.workOrderId)
    } else if (type === 'outsource') {
      if (!row.relatedOrderId) { ElMessage.warning('外协单ID不存在'); docDetailVisible.value = false; return }
      res = await getOutsourceOrderDetail(row.relatedOrderId)
    } else if (type === 'purchase') {
      if (row.relatedOrderId) {
        res = await getPurchaseOrderDetail(row.relatedOrderId)
      } else if (row.purchaseOrderNo) {
        res = await getPurchaseOrderDetailByNo(row.purchaseOrderNo)
      } else {
        ElMessage.warning('采购单信息不存在'); docDetailVisible.value = false; return
      }
    }
    docDetail.value = res?.data || null
  } catch {
    ElMessage.error('获取单据详情失败')
  } finally {
    docDetailLoading.value = false
  }
}

// --- template import ---
const selectedTemplateId = ref(null)
const templateOptions = ref([])

async function loadTemplateOptions() {
  if (templateOptions.value.length > 0) return
  try {
    const res = await getQcTemplateSelect()
    templateOptions.value = res.data || []
  } catch { /* ignore */ }
}

async function onTemplateSelected(templateId) {
  if (!templateId) return
  try {
    const res = await getQcTemplateDetail(templateId)
    const tmpl = res.data || {}
    const items = tmpl.items || []
    // 清空旧检验项，导入新的
    inspectionItems.value = items.map(item => ({
      templateItemId: item.id,
      itemName: item.itemName || '',
      itemType: item.itemType || 'quantitative',
      standardValue: item.standardValue || '',
      toleranceMin: item.toleranceMin || '',
      toleranceMax: item.toleranceMax || '',
      unit: item.unit || '',
      method: item.method || '',
      defaultValue: item.defaultValue || '',
      isRequired: item.isRequired || 0,
      isCritical: item.isCritical || 0,
      actualValue: item.defaultValue || '',
      itemResult: ''
    }))

    // 计算质检数量（复用统一函数）
    inspectionSampleQty.value = 0  // 重置，让calcSampleQty重新计算
    await calcSampleQty({ ...inspectionTask.value, templateId })

    ElMessage.success(`已导入 ${items.length} 个检验项目`)
  } catch {
    ElMessage.error('导入模板失败')
  }
}

// 已移除废弃函数

// --- lifecycle ---
onMounted(() => {
  loadData()
  loadStats()
  loadColumnConfig()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stat-row {
  flex-shrink: 0;
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

/* --- Fullscreen inspection dialog --- */
.inspection-dialog {
  :deep(.el-dialog__body) {
    padding: 16px;
    height: calc(100vh - 55px);
    overflow: hidden;
  }
}

.inspection-wrapper {
  display: flex;
  gap: 16px;
  height: 100%;
}

.inspection-left {
  flex: 7;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.inspection-right {
  flex: 3;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.inspection-info-card {
  flex-shrink: 0;
  :deep(.el-card__body) {
    padding: 12px;
  }
}

.inspection-items-card {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  :deep(.el-card__body) {
    flex: 1;
    overflow: hidden;
    padding: 0 12px 12px;
  }
}

.inspection-items-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.inspection-summary-card {
  :deep(.el-card__body) {
    padding: 16px;
  }
}

.summary-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.summary-item {
  text-align: center;
  padding: 12px 0;
  background: #f5f7fa;
  border-radius: 8px;
}

.summary-item__label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.summary-item__value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.pass-rate-bar {
  margin-top: 8px;
}

.pass-rate-label {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
  font-weight: 500;
}

.inspection-conclusion-card {
  :deep(.el-card__body) {
    padding: 16px;
  }
}

.conclusion-radio-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.inspection-submit-btn {
  width: 100%;
  flex-shrink: 0;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

.is-required-highlight {
  :deep(.el-input__wrapper) {
    box-shadow: 0 0 0 1px #f56c6c inset;
  }
}
</style>
