<template>
  <div class="page-container">
    <!-- 统计卡片区域 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <StatCard
          title="待排产"
          :value="statistics.pending"
          icon="Clock"
          color="#909399"
          clickable
          @click="filterByStatus('pending')"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <StatCard
          title="生产中"
          :value="statistics.inProgress"
          icon="Loading"
          color="#E6A23C"
          clickable
          @click="filterByStatus('in_progress')"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <StatCard
          title="已完工"
          :value="statistics.completed"
          icon="CircleCheck"
          color="#67C23A"
          clickable
          @click="filterByStatus('completed')"
        />
      </el-col>
      <el-col :xs="12" :sm="6">
        <StatCard
          title="逾期"
          :value="statistics.overdue"
          icon="WarningFilled"
          color="#F56C6C"
          clickable
          @click="filterByStatus('overdue')"
        />
      </el-col>
    </el-row>

    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="工单号">
          <el-input v-model="queryParams.woNo" placeholder="工单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品编号">
          <el-input v-model="queryParams.productNo" placeholder="产品编号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="queryParams.productName" placeholder="产品名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="queryParams.spec" placeholder="规格型号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="销售订单号">
          <el-input v-model="queryParams.orderNo" placeholder="销售订单号" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <template v-for="fc in searchFieldConfigs" :key="fc.fieldKey">
          <el-form-item :label="fc.fieldLabel">
            <el-select v-if="fc.fieldType === 'select'" v-model="queryParams.extFields[fc.fieldKey]" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="opt in fc.fieldOptions ? JSON.parse(fc.fieldOptions) : []" :key="opt" :label="opt" :value="opt" />
            </el-select>
            <el-input v-else v-model="queryParams.extFields[fc.fieldKey]" :placeholder="fc.fieldLabel" clearable style="width: 140px" @keyup.enter="handleSearch" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button v-permission="'production:wo:add'" type="primary" plain @click="handleAdd">手动建单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 视图切换 -->
    <div class="view-toggle">
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="table">
          <el-icon style="vertical-align: middle; margin-right: 4px"><Grid /></el-icon>
          <span style="vertical-align: middle">表格</span>
        </el-radio-button>
        <el-radio-button value="kanban">
          <el-icon style="vertical-align: middle; margin-right: 4px"><Operation /></el-icon>
          <span style="vertical-align: middle">看板</span>
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 看板视图 -->
    <div v-if="viewMode === 'kanban'" class="kanban-container" v-loading="kanbanLoading">
      <div class="kanban-column" v-for="col in kanbanColumns" :key="col.status">
        <div class="kanban-column-header" :style="{ borderTopColor: col.color }">
          <span>{{ col.label }}</span>
          <el-badge :value="col.items.length" type="info" />
        </div>
        <div class="kanban-column-body">
          <el-card
            v-for="item in col.items"
            :key="item.id"
            shadow="hover"
            class="kanban-card"
            :class="{ 'kanban-card--overdue': isOverdue(item) }"
            @click="handleView(item)"
          >
            <div class="kanban-card-header">
              <div class="kanban-card-title">{{ item.woNo }}</div>
              <el-tag v-if="isOverdue(item)" type="danger" size="small" effect="dark" class="kanban-overdue-tag">逾期</el-tag>
            </div>
            <div class="kanban-card-product">{{ item.productName }}</div>
            <el-progress
              :percentage="item.planQty ? Math.min(Math.round((item.completedQty || 0) / item.planQty * 100), 100) : 0"
              :stroke-width="8"
              :color="item.completedQty >= item.planQty ? '#67C23A' : '#409EFF'"
            />
            <div class="kanban-card-countdown" v-if="item.deliveryDate">
              <el-icon :size="12"><Timer /></el-icon>
              <span :class="{ 'text-danger': deliveryCountdown(item) < 0 }">
                {{ deliveryCountdownText(item) }}
              </span>
            </div>
            <div class="kanban-card-footer">
              <el-tag :type="priorityTagType(item.priority)" size="small">{{ priorityLabel(item.priority) }}</el-tag>
              <span class="kanban-card-date">{{ item.deliveryDate || '-' }}</span>
            </div>
          </el-card>
          <el-empty v-if="col.items.length === 0" description="暂无工单" :image-size="40" />
        </div>
      </div>
    </div>

    <!-- 表格区域 -->
    <el-card v-if="viewMode === 'table'" shadow="never" class="table-card">
      <div style="display: flex; justify-content: flex-end; margin-bottom: 8px">
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" :show-overflow-tooltip="col.key !== 'woNo'">
          <template #default="{ row }">
            <template v-if="col.key === 'woNo'">
              <span v-if="isWoOverdue(row)" class="badge-mini badge-overdue">逾</span>
              <span v-else-if="isWoPostponed(row)" class="badge-mini badge-postponed">延</span>
              <el-button link type="primary" @click="handleView(row)">{{ row.woNo }}</el-button>
            </template>
            <template v-else-if="col.key === 'orderNo'">
              <el-button v-if="row.orderId && row.orderNo" link type="primary" size="small" @click="openOrderDetail(row.orderId)">{{ row.orderNo }}</el-button>
              <span v-else>{{ row.orderNo || '-' }}</span>
            </template>
            <template v-else-if="col.key === 'priority'">
              <el-tag :type="priorityTagType(row.priority)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'productSpec'">{{ $fmtSpec(row.productSpec) }}</template>
            <template v-else-if="col.type === 'extension'">{{ getExtFieldValue(row, col.fieldKey) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button
              v-permission="'production:wo:add'"
              link
              type="warning"
              v-if="row.status !== 'cancelled'"
              @click="handleAppend(row)"
            >追加</el-button>
            <el-button
              v-permission="'production:wo:edit'"
              link
              type="danger"
              v-if="row.status !== 'completed' && row.status !== 'cancelled'"
              @click="handleCancel(row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情抽屉（增强版） -->
    <el-drawer v-model="detailVisible" title="工单详情" size="1100px" destroy-on-close>
      <template v-if="detailData">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item label="工单号">{{ detailData.woNo }}</el-descriptions-item>
          <el-descriptions-item label="销售订单号">{{ detailData.salesOrderNo || detailData.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物料编号">{{ detailData.productNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ detailData.productName }}</el-descriptions-item>
          <el-descriptions-item label="规格" :span="2">{{ $fmtSpec(detailData.productSpec) }}</el-descriptions-item>
          <el-descriptions-item label="计划数量">{{ detailData.planQty }}</el-descriptions-item>
          <el-descriptions-item label="完成数量">{{ detailData.completedQty }}</el-descriptions-item>
          <el-descriptions-item label="优先级">
            <el-tag :type="priorityTagType(detailData.priority)" size="small">{{ priorityLabel(detailData.priority) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailData.status)">{{ statusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="生产模式">
            {{ detailData.productionMode === 'MTO' ? '按单生产' : '备库生产' }}
          </el-descriptions-item>
          <el-descriptions-item label="计划开始">{{ detailData.plannedStart || detailData.planStartTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划结束">{{ detailData.plannedEnd || detailData.planEndTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="交货日期">{{ detailData.deliveryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 产品图纸 -->
        <template v-if="(detailData.productImages && detailData.productImages.length) || (detailData.productAttachmentUrls && detailData.productAttachmentUrls.length)">
          <div style="font-weight:600;margin:16px 0 8px;color:#303133;font-size:14px;">产品图纸</div>
          <div v-if="detailData.productImages && detailData.productImages.length" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px;">
            <el-image
              v-for="(img, idx) in detailData.productImages" :key="idx"
              :src="img" :preview-src-list="detailData.productImages"
              :initial-index="idx" fit="contain"
              style="width:120px;height:120px;border:1px solid #ebeef5;border-radius:4px;"
            />
          </div>
          <div v-if="detailData.productAttachmentUrls && detailData.productAttachmentUrls.length">
            <div v-for="(att, idx) in detailData.productAttachmentUrls" :key="idx" style="margin-bottom:4px;">
              <a :href="att.url || att" target="_blank" style="color:#409eff;text-decoration:none;font-size:13px;">
                <el-icon style="vertical-align:middle;margin-right:4px;"><Document /></el-icon>
                {{ att.name || ('附件' + (idx + 1)) }}
              </a>
            </div>
          </div>
        </template>

        <!-- 扩展字段 (汇总: 工单 + 销售订单头 + 销售订单明细行) -->
        <template v-if="detailData.extFields && allInheritedFieldConfigs.length">
          <div style="font-weight:600;margin:16px 0 8px;color:#303133;font-size:14px;">扩展字段</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item v-for="fc in allInheritedFieldConfigs" :key="fc.fieldKey" :label="fc.fieldLabel">
              {{ getExtFieldValue(detailData, fc.fieldKey) || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <!-- 操作按钮 -->
        <div style="margin-top: 16px; display: flex; gap: 8px" v-if="detailData.status !== 'cancelled'">
          <el-button type="warning" size="small" @click="openWoChange">工单变更</el-button>
          <el-button type="warning" size="small" plain @click="handleAppend(detailData)">追加工单</el-button>
          <el-button type="primary" size="small" v-if="detailData.status === 'pending'" @click="handleStartWo">开始生产</el-button>
          <el-button
            v-if="detailData.status === 'in_progress'"
            type="success"
            size="small"
            :disabled="issueAllDone"
            @click="openIssueMaterial"
          >{{ issueAllDone ? '已领齐' : '领料' }}</el-button>
          <el-button
            v-if="detailData.status === 'in_progress'"
            type="warning"
            size="small"
            @click="openReturnMaterial"
          >退料</el-button>
          <!-- 生产入库按钮:末道工序质检通过后自动入库,此按钮仅作为手动补充
               显示条件:工单状态为 生产中/已完工,且仍有未生成入库任务的完成数量 -->
          <el-button
            v-if="(detailData.completedQty || 0) > 0
                  && ['in_progress','completed'].includes(detailData.status)
                  && (detailData.completedQty || 0) > (detailData.stockInTaskQty || 0)"
            type="success"
            size="small"
            @click="openStockInDialog"
          >生产入库</el-button>
        </div>

        <!-- 完成进度条 -->
        <div class="progress-section">
          <div class="progress-label">
            <span>完成进度</span>
            <span>{{ detailData.completedQty || 0 }} / {{ detailData.planQty || 0 }}</span>
          </div>
          <el-progress
            :percentage="completionPercentage"
            :color="completionPercentage >= 100 ? '#67C23A' : '#409EFF'"
            :stroke-width="20"
            :text-inside="true"
          />
          <div v-if="(detailData.completedQty || 0) > 0" style="margin-top: 8px; font-size: 13px; color: #606266; display: flex; gap: 24px; flex-wrap: wrap; align-items: center">
            <span>库存任务:
              <el-tag :type="invTaskStatusTagType[detailData.invTaskStatus] || 'info'" size="small">
                {{ invTaskStatusMap[detailData.invTaskStatus] || '未生成' }}
              </el-tag>
            </span>
            <span>已生成任务: <strong>{{ detailData.stockInTaskQty || 0 }}</strong></span>
            <span>已确认入库: <strong>{{ detailData.stockInQty || 0 }}</strong></span>
          </div>
        </div>

        <!-- 工艺路线（直接展示在产品信息下方） -->
        <div class="route-section">
          <div class="route-section-header">
            <span class="route-section-title">工艺路线</span>
          </div>
          <el-table :data="taskList" v-loading="taskLoading" border stripe size="small" row-key="id">
            <el-table-column prop="stepNo" label="序号" width="60" align="center" />
            <el-table-column label="工序名称" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <el-badge v-if="drawingSummary[row.id]?.hasNewVersion" value="图纸变更" type="warning" class="drawing-badge">
                  <span>{{ row.stepName }}</span>
                </el-badge>
                <span v-else>{{ row.stepName }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="standardTime" label="标准工时(天)" width="105" align="right" />
            <el-table-column prop="piecePrice" label="计件单价" width="90" align="right">
              <template #default="{ row }">{{ row.piecePrice ?? 0 }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="taskStatusTagType(row.status)" size="small">{{ taskStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="质检" width="120" align="center">
              <template #default="{ row }">
                <template v-if="row.qcTemplateId">
                  <div style="display:flex;flex-direction:column;gap:2px;align-items:center">
                    <el-button v-if="row.qcStatus === 'pending'" link size="small" type="warning" @click="showQcRequirements(row)">待检</el-button>
                    <el-button v-else-if="row.qcStatus === 'pass'" link size="small" type="success" @click="showQcRequirements(row)">合格</el-button>
                    <el-button v-else-if="row.qcStatus === 'fail'" link size="small" type="danger" @click="showQcRequirements(row)">不合格</el-button>
                    <el-button v-else link size="small" @click="showQcRequirements(row)">查看要求</el-button>
                    <el-button v-if="row.qcTaskId && row.qcStatus !== 'pending'" link size="small" type="primary" @click="showQcRecords(row)">查记录</el-button>
                  </div>
                </template>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="图纸" width="100" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openWoDrawingDialog(row)">
                  <span v-if="drawingSummary[row.id]?.latest">v{{ drawingSummary[row.id].latest.version }} · 查看</span>
                  <span v-else>上传</span>
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="外协" width="90" align="center">
              <template #default="{ row }">
                <!-- 显示外协状态的条件:工艺路线标记可外协,或已被"工单变更"转外协 -->
                <template v-if="row.isOutsourceable || row.isOutsourced || row.outsourceStatus">
                  <el-tag v-if="!row.outsourceStatus" type="info" size="small">可外协</el-tag>
                  <el-tag v-else-if="row.outsourceStatus === 'requested'" type="warning" size="small">待审批</el-tag>
                  <el-tag v-else-if="row.outsourceStatus === 'ordered'" type="" size="small">外协中</el-tag>
                  <el-tag v-else-if="row.outsourceStatus === 'completed'" type="success" size="small">外协完成</el-tag>
                  <el-tag v-else size="small">{{ row.outsourceStatus }}</el-tag>
                </template>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="指派方式" width="85" align="center">
              <template #default="{ row }">
                <span v-if="row.assignType === 'team'">班组</span>
                <span v-else-if="row.assignType === 'person'">个人</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="指派" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <el-popover
                  v-if="row.assignType === 'team' && row.assignedTeamId"
                  placement="right"
                  :width="280"
                  trigger="click"
                  @show="loadTeamMembers(row.assignedTeamId)"
                >
                  <template #reference>
                    <el-button link type="primary" size="small">{{ row.assignedTeamName || '-' }}</el-button>
                  </template>
                  <div v-loading="teamMembersLoading" style="min-height: 40px">
                    <div style="font-weight: 600; margin-bottom: 8px; color: #303133">{{ row.assignedTeamName }} - 成员列表</div>
                    <el-table v-if="teamMembersList.length" :data="teamMembersList" size="small" border stripe>
                      <el-table-column prop="userName" label="姓名" min-width="70">
                        <template #default="{ row: m }">
                          <span>{{ m.userName }}</span>
                          <el-tag v-if="m.isLeader" type="warning" size="small" style="margin-left: 4px">组长</el-tag>
                        </template>
                      </el-table-column>
                      <el-table-column prop="weight" label="权重" width="65" align="center">
                        <template #default="{ row: m }">{{ m.weight != null ? (m.weight * 100).toFixed(0) + '%' : '-' }}</template>
                      </el-table-column>
                    </el-table>
                    <el-empty v-else description="暂无成员" :image-size="40" />
                  </div>
                </el-popover>
                <span v-else-if="row.assignType === 'team'">{{ row.assignedTeamName || '-' }}</span>
                <span v-else-if="row.assignType === 'person'">{{ row.assignedUserName || '-' }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="planQty" label="计划数" width="75" align="center" />
            <el-table-column label="已报工/合格" width="100" align="center">
              <template #default="{ row }">
                <template v-if="row.qcTemplateId">
                  <span>{{ row.reportedQty ?? 0 }}</span>
                  <span class="text-muted" style="font-size: 11px"> / {{ row.qualifiedQty ?? 0 }}</span>
                </template>
                <template v-else>{{ row.qualifiedQty ?? 0 }}</template>
              </template>
            </el-table-column>
            <el-table-column prop="defectQty" label="不良数" width="75" align="center">
              <template #default="{ row }">{{ row.defectQty ?? 0 }}</template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="160">
              <template #default="{ row }">{{ row.startTime ? $fmtDate(row.startTime) : '--' }}</template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" width="160">
              <template #default="{ row }">{{ row.endTime ? $fmtDate(row.endTime) : '--' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="isReportableTask(row)"
                  link type="success" size="small"
                  :disabled="!!row._semiBlockReason"
                  :title="row._semiBlockReason || ''"
                  @click="openReportTask(row)"
                >报工</el-button>
                <!-- 半成品入库: 合格数 > 已入库 (允许 completed 状态后补入库 — 后端会再校验"下道是否已直接消耗") -->
                <el-button
                  v-if="canShowSemiStockIn(row)"
                  link type="warning" size="small"
                  @click="openSemiStockIn(row)"
                >半成品入库</el-button>
                <!-- 撤销入库: 已入库 > 已被领走 → 还有可撤数量 -->
                <el-button
                  v-if="row.status !== 'cancelled'
                        && Number(row.semiStockedQty || 0) > Number(row.semiIssuedQty || 0)"
                  link type="danger" size="small"
                  @click="openSemiRevoke(row)"
                >撤入库</el-button>
                <!-- 领料: 上道工序入过半成品库 且 本道还未领料 时显示 (completed 后无需领料) -->
                <el-button
                  v-if="row.status !== 'completed' && row.status !== 'cancelled'
                        && row._semiBlockReason"
                  link type="primary" size="small"
                  @click="openSemiIssue(row)"
                >领料</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 其他信息 Tabs -->
        <el-tabs v-model="detailActiveTab" @tab-change="handleTabChange" class="detail-tabs">
          <!-- Tab 1: BOM快照 -->
          <el-tab-pane label="BOM快照" name="bom">
            <el-table :data="bomList" v-loading="bomLoading" border stripe size="small">
              <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="spec" label="规格型号" min-width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ $fmtSpec(row.spec) || row.materialCode || '-' }}</template>
              </el-table-column>
              <el-table-column prop="unit" label="单位" width="80" align="center">
                <template #default="{ row }">{{ row.unit || '-' }}</template>
              </el-table-column>
              <el-table-column prop="requiredQty" label="需求数量" width="100" align="center">
                <template #default="{ row }">{{ row.requiredQty ?? row.quantity ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="换算数量" width="110" align="center">
                <template #default="{ row }">
                  <span v-if="secondaryDisplay(row.requiredQty ?? row.quantity, row.unitConfig)" style="color:#909399;font-size:12px">{{ secondaryDisplay(row.requiredQty ?? row.quantity, row.unitConfig) }}</span>
                  <span v-else style="color:#c0c4cc">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="issuedTaskQty" label="任务数" width="85" align="center">
                <template #default="{ row }">
                  <span :style="(row.issuedTaskQty||0)>0?'color:#409eff':'color:#c0c4cc'">{{ row.issuedTaskQty ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="issuedQty" label="已领数量" width="90" align="center">
                <template #default="{ row }">
                  <span :style="(row.issuedQty||0)>0?'color:#67c23a;font-weight:600':'color:#c0c4cc'">{{ row.issuedQty ?? 0 }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="returnedTaskQty" label="退料任务" width="85" align="center">
                <template #default="{ row }">
                  <span v-if="(row.returnedTaskQty||0)>0" style="color:#e6a23c">{{ row.returnedTaskQty }}</span>
                  <span v-else style="color:#c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="returnedQty" label="已退数量" width="85" align="center">
                <template #default="{ row }">
                  <span v-if="(row.returnedQty||0)>0" style="color:#f56c6c;font-weight:600">{{ row.returnedQty }}</span>
                  <span v-else style="color:#c0c4cc">—</span>
                </template>
              </el-table-column>
              <el-table-column label="领料进度" min-width="130">
                <template #default="{ row }">
                  <el-progress
                    :percentage="row.requiredQty > 0 ? Math.min(Math.round(((row.issuedQty || 0) / row.requiredQty) * 100), 100) : 0"
                    :stroke-width="10"
                    :color="(row.issuedQty || 0) >= row.requiredQty ? '#67C23A' : '#409EFF'"
                  />
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- Tab 2: 成本明细 -->
          <el-tab-pane label="成本明细" name="cost">
            <el-descriptions :column="2" border v-loading="costLoading" v-if="woCost">
              <el-descriptions-item label="物料成本">¥{{ Number(woCost.materialCost || 0).toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="人工成本">¥{{ Number(woCost.laborCost || 0).toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="外协成本">¥{{ Number(woCost.outsourceCost || 0).toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="返工成本">¥{{ Number(woCost.reworkCost || 0).toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="报废损失">¥{{ Number(woCost.scrapLoss || 0).toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="制造费用">¥{{ Number(woCost.overhead || 0).toFixed(2) }}</el-descriptions-item>
              <el-descriptions-item label="总成本" :span="2">
                <span style="font-size: 18px; font-weight: 700; color: #f56c6c">¥{{ Number(woCost.totalCost || 0).toFixed(2) }}</span>
              </el-descriptions-item>
            </el-descriptions>
            <el-empty v-if="!costLoading && !woCost" description="暂无成本数据" />
          </el-tab-pane>

          <!-- Tab 3: 外协进度 -->
          <el-tab-pane label="外协进度" name="outsource" v-if="hasOutsourceTasks">
            <div v-loading="outsourceLoading">
              <div v-if="outsourceData.outsourceTasks && outsourceData.outsourceTasks.length > 0">
                <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 12px">
                  <span style="font-size: 14px; color: #606266">外协工序进度</span>
                  <el-tag size="small">{{ outsourceData.completedOutsource || 0 }} / {{ outsourceData.totalOutsource || 0 }} 完成</el-tag>
                </div>
                <div v-for="(ot, idx) in outsourceData.outsourceTasks" :key="idx" class="outsource-task-card">
                  <div class="outsource-task-header">
                    <span class="outsource-task-name">{{ ot.stepName }}</span>
                    <el-tag v-if="!ot.outsourceStatus" type="info" size="small">可外协</el-tag>
                    <el-tag v-else-if="ot.outsourceStatus === 'requested'" type="warning" size="small">待审批</el-tag>
                    <el-tag v-else-if="ot.outsourceStatus === 'ordered'" size="small">外协中</el-tag>
                    <el-tag v-else-if="ot.outsourceStatus === 'completed'" type="success" size="small">外协完成</el-tag>
                  </div>
                  <el-descriptions :column="3" border size="small" style="margin-top: 8px">
                    <el-descriptions-item label="计划数">{{ ot.planQty ?? '-' }}</el-descriptions-item>
                    <el-descriptions-item label="合格数">{{ ot.qualifiedQty ?? 0 }}</el-descriptions-item>
                    <el-descriptions-item label="虚拟半成品库存">
                      <span :style="{ color: ot.virtualSemiQty > 0 ? '#67C23A' : '#909399' }">
                        {{ ot.virtualSemiQty ?? '-' }}
                      </span>
                    </el-descriptions-item>
                  </el-descriptions>
                  <!-- 进度步骤 -->
                  <el-steps :active="outsourceStepActive(ot)" size="small" style="margin-top: 12px" finish-status="success">
                    <el-step title="前道报工" />
                    <el-step title="外协申请" />
                    <el-step title="外协加工" />
                    <el-step title="收货质检" />
                    <el-step title="后道激活" />
                  </el-steps>
                </div>
              </div>
              <el-empty v-else description="暂无外协工序" />
            </div>
          </el-tab-pane>

          <!-- Tab 4: 生产计划附件 -->
          <el-tab-pane label="计划附件" name="attachments">
            <div v-loading="attachmentLoading">
              <el-table v-if="planAttachments.length > 0" :data="planAttachments" border stripe size="small">
                <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
                <el-table-column label="文件类型" width="100" align="center">
                  <template #default="{ row }">{{ row.fileType || '-' }}</template>
                </el-table-column>
                <el-table-column label="文件大小" width="100" align="center">
                  <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="120" align="center">
                  <template #default="{ row }">
                    <el-button v-if="row.fileUrl && isPreviewable(row.fileType)" link type="primary" size="small" @click="previewOrDownload(row)">预览</el-button>
                    <el-button v-if="row.fileUrl" link type="success" size="small" @click="downloadFile(row)">下载</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无计划附件" />

              <!-- 计划附件图片预览 -->
              <el-image-viewer v-if="planAttachPreviewVisible" :url-list="planAttachPreviewUrls"
                :initial-index="planAttachPreviewIndex" @close="planAttachPreviewVisible = false" />
            </div>
          </el-tab-pane>

          <!-- Tab 6: 报工记录 -->
          <el-tab-pane label="报工记录" name="workreport">
            <div v-loading="woReportLoading">
              <el-table :data="woReportList" border stripe size="small">
                <el-table-column prop="reportNo" label="报工单号" width="150" show-overflow-tooltip />
                <el-table-column prop="stepName" label="工序" min-width="110" show-overflow-tooltip />
                <el-table-column prop="employeeName" label="报工人" width="90" />
                <el-table-column label="类型" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.reportType === 'team' ? 'warning' : ''">
                      {{ row.reportType === 'team' ? '班组' : '个人' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="qualifiedQty" label="合格数" width="80" align="center" />
                <el-table-column label="不良数" width="80" align="center">
                  <template #default="{ row }">
                    <span :style="row.defectQty > 0 ? 'color:#F56C6C' : ''">{{ row.defectQty }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'rejected' ? 'danger' : 'success'">
                      {{ row.status === 'rejected' ? '已驳回' : '已报工' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="报工时间" min-width="150">
                  <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="80" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="row.status !== 'rejected'" link type="danger" size="small" @click="onRejectWoReport(row)">驳回</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!woReportLoading && woReportList.length === 0" description="暂无报工记录" />
            </div>
          </el-tab-pane>

          <!-- Tab: 库存任务 -->
          <el-tab-pane label="库存任务" name="invTasks">
            <div v-loading="invTasksLoading">
              <el-empty v-if="!invTasksLoading && invTasksList.length === 0" description="暂无库存任务" />
              <el-table v-else :data="invTasksList" border stripe size="small" row-key="id">
                <el-table-column prop="taskNo" label="任务编号" min-width="160" show-overflow-tooltip />
                <el-table-column label="类型" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="invTypeTagType(row.invType)" size="small">{{ invTypeLabel(row.invType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="85" align="center">
                  <template #default="{ row }">
                    <el-tag :type="invTaskStatusType(row.status)" size="small">{{ invTaskStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="warehouseName" label="仓库" width="100" show-overflow-tooltip />
                <el-table-column label="物料明细" min-width="240">
                  <template #default="{ row }">
                    <div v-for="(item, idx) in (row.items || [])" :key="idx" style="line-height: 1.8">
                      <span v-if="item.materialNo" style="color:#909399; margin-right:4px">{{ item.materialNo }}</span>
                      <span>{{ item.materialName }}</span>
                      <span v-if="item.spec" style="color:#909399; margin-left:4px">{{ $fmtSpec(item.spec) }}</span>
                      <span style="margin-left:8px; font-weight:600">{{ item.quantity }}</span>
                      <span style="color:#909399">{{ item.unit }}</span>
                      <span v-if="(item.processedQty || 0) > 0" style="margin-left:8px; color:#67c23a">
                        已处理: {{ item.processedQty }}
                      </span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip />
                <el-table-column label="创建时间" width="150">
                  <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <!-- Tab: 采购进度 -->
          <el-tab-pane label="采购进度" name="purchase" v-if="purchaseProgressList.length > 0 || hasShortageHistory">
            <div v-loading="purchaseProgressLoading">
              <el-alert type="info" :closable="false" style="margin-bottom: 12px">
                <template #title>
                  本工单领料缺料触发的采购申请、采购订单与到货情况。到货后可回工单继续领料。
                </template>
              </el-alert>
              <el-table :data="purchaseProgressList" border stripe size="small">
                <el-table-column prop="requestNo" label="申请单号" width="160" show-overflow-tooltip />
                <el-table-column prop="materialNo" label="物料编号" width="120" show-overflow-tooltip />
                <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
                <el-table-column label="规格" min-width="110" show-overflow-tooltip>
                  <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
                </el-table-column>
                <el-table-column prop="quantity" label="申请数" width="80" align="right" />
                <el-table-column prop="unit" label="单位" width="55" align="center" />
                <el-table-column label="需求日期" width="100" align="center">
                  <template #default="{ row }">{{ row.expectedDate || '-' }}</template>
                </el-table-column>
                <el-table-column label="阶段" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="purchaseStageTag(row.stage)" size="small">{{ row.stage }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="采购订单" min-width="140">
                  <template #default="{ row }">
                    <span v-if="row.poNo">{{ row.poNo }}</span>
                    <span v-else style="color:#c0c4cc">未下单</span>
                  </template>
                </el-table-column>
                <el-table-column label="到货 / 合格 / 入库" width="160" align="center">
                  <template #default="{ row }">
                    <span :style="{ color: Number(row.stockInQty) >= Number(row.quantity) ? '#67c23a' : '#909399' }">
                      {{ row.actualQty }} / {{ row.qualifiedQty }} / {{ row.stockInQty }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="用途" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.purpose || '-' }}</template>
                </el-table-column>
                <el-table-column label="创建时间" width="160">
                  <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-if="purchaseProgressList.length === 0" description="暂无采购进度" />
            </div>
          </el-tab-pane>

          <!-- Tab 5: 变更历史 -->
          <el-tab-pane label="变更历史" name="history">
            <div v-loading="historyLoading">
              <el-timeline v-if="historyList.length > 0">
                <el-timeline-item
                  v-for="(item, index) in historyList"
                  :key="index"
                  :timestamp="item.changeTime || item.createTime"
                  placement="top"
                  :type="changeTypeColor(item.changeType)"
                >
                  <el-card shadow="never" class="history-card">
                    <div class="history-header">
                      <el-tag size="small" :type="changeTypeColor(item.changeType)">
                        {{ changeTypeLabel(item.changeType) }}
                      </el-tag>
                      <span class="history-operator">{{ item.operator || item.operatorName || '-' }}</span>
                    </div>
                    <div class="history-content">{{ formatChangeContent(item) }}</div>
                  </el-card>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无变更记录" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 新增对话框 (V4.0) -->
    <el-dialog v-model="formVisible" title="新建生产工单" width="640px" destroy-on-close>
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="110px">
        <el-form-item label="生产模式" prop="productionMode">
          <el-radio-group v-model="formData.productionMode">
            <el-radio value="MTO">按单生产 (MTO)</el-radio>
            <el-radio value="MTS">备库生产 (MTS)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="formData.productionMode === 'MTO'" label="关联订单" prop="orderId">
          <el-select
            v-model="formData.orderId"
            placeholder="搜索销售订单号"
            filterable
            remote
            :remote-method="searchSalesOrders"
            :loading="salesOrderLoading"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in salesOrderOptions"
              :key="item.id"
              :label="`${item.orderNo} - ${item.customerName || ''}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" prop="productId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="formData.productName" readonly placeholder="请选择产品(成品)" style="flex: 1;" />
            <el-button type="primary" @click="woProductPickerVisible = true">选择产品</el-button>
          </div>
        </el-form-item>
        <el-form-item label="计划数量" prop="planQty">
          <el-input-number v-model="formData.planQty" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划开始日期" prop="planStartDate">
          <el-date-picker v-model="formData.planStartDate" type="date" placeholder="选择计划开始日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划完工日期" prop="planEndDate">
          <el-date-picker v-model="formData.planEndDate" type="date" placeholder="选择计划完工日期" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注信息" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" style="margin-top: 8px">
        <template #title>
          创建后系统将自动: 快照BOM + 快照工艺路线 → 分解工序任务 → 锁定物料
        </template>
      </el-alert>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确认创建</el-button>
      </template>
    </el-dialog>
    <!-- 追加工单对话框 -->
    <el-dialog v-model="appendVisible" title="追加工单" width="480px" destroy-on-close>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="源工单号">{{ appendSourceWo.woNo }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ appendSourceWo.productName }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{ $fmtSpec(appendSourceWo.productSpec) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原计划数量">{{ appendSourceWo.planQty }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="appendForm" :rules="appendRules" ref="appendFormRef" label-width="100px">
        <el-form-item label="追加数量" prop="planQty">
          <el-input-number v-model="appendForm.planQty" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="appendForm.remark" type="textarea" :rows="2" placeholder="可选填写备注" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" style="margin-top: 8px">
        <template #title>
          追加工单将复制源工单的产品、工艺路线等信息，工单号为: {{ appendSourceWo.woNo }}追{{ String(appendSeqPreview).padStart(3, '0') }}
        </template>
      </el-alert>
      <template #footer>
        <el-button @click="appendVisible = false">取消</el-button>
        <el-button type="primary" :loading="appendLoading" @click="handleAppendSubmit">确认追加</el-button>
      </template>
    </el-dialog>
    <!-- 工单变更弹窗 -->
    <el-dialog v-model="woChangeVisible" title="工单变更" width="640px" destroy-on-close>
      <el-steps :active="woChangeStep" finish-status="success" align-center style="margin-bottom: 24px">
        <el-step title="选择变更类型" />
        <el-step title="填写变更内容" />
        <el-step title="确认提交" />
      </el-steps>

      <!-- Step 1: 选择变更类型 -->
      <div v-if="woChangeStep === 0">
        <el-radio-group v-model="woChangeForm.changeType" class="wo-change-radio">
          <el-radio value="outsource">工序转外协</el-radio>
          <el-radio value="modify_qty">修改计划数量</el-radio>
          <el-radio value="modify_date">修改交货日期</el-radio>
        </el-radio-group>
      </div>

      <!-- Step 2: 填写变更内容 -->
      <div v-if="woChangeStep === 1">
        <el-form :model="woChangeForm" label-width="100px">
          <!-- 工序转外协 -->
          <template v-if="woChangeForm.changeType === 'outsource'">
            <el-form-item label="选择工序" required>
              <el-select v-model="woChangeForm.processTaskId" placeholder="请选择工序" style="width: 100%" @change="onOutsourceTaskChange">
                <el-option v-for="t in taskList" :key="t.id" :label="`${t.stepNo}-${t.stepName}`" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="外协供应商" required>
              <el-select v-model="woChangeForm.outsourceSupplierId" placeholder="请选择供应商" filterable style="width: 100%">
                <el-option v-for="s in outsourceSupplierOptions" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="加工数量" required>
              <el-input-number v-model="woChangeForm.outsourceQty" :min="0.0001" :precision="4" controls-position="right" style="width: 200px" />
              <span style="margin-left:8px;color:#909399;font-size:12px">默认取工序计划数</span>
            </el-form-item>
            <el-form-item label="不含税单价" required>
              <el-input-number v-model="woChangeForm.outsourceUnitPrice" :min="0" :precision="2" controls-position="right" style="width: 200px" />
              <span style="margin-left:8px;color:#909399;font-size:12px">元 / 件</span>
            </el-form-item>
            <el-form-item label="税率(%)">
              <el-input-number v-model="woChangeForm.outsourceTaxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width: 200px" />
            </el-form-item>
            <el-form-item label="交付日期" required>
              <el-date-picker v-model="woChangeForm.outsourceDeliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择交付日期" style="width:200px" />
            </el-form-item>
            <el-form-item label="外协原因">
              <el-input v-model="woChangeForm.reason" type="textarea" :rows="2" placeholder="请输入外协原因" />
            </el-form-item>
          </template>

          <!-- 修改计划数量 -->
          <template v-if="woChangeForm.changeType === 'modify_qty'">
            <el-form-item label="当前数量">{{ detailData?.planQty }}</el-form-item>
            <el-form-item label="新计划数量">
              <el-input-number v-model="woChangeForm.newPlanQty" :min="1" controls-position="right" style="width: 200px" />
            </el-form-item>
          </template>

          <!-- 修改交货日期 -->
          <template v-if="woChangeForm.changeType === 'modify_date'">
            <el-form-item label="当前交期">{{ detailData?.deliveryDate || '-' }}</el-form-item>
            <el-form-item label="新交货日期">
              <el-date-picker v-model="woChangeForm.newDeliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
            </el-form-item>
          </template>

          <!-- 通用变更原因 -->
          <el-form-item label="变更原因" v-if="woChangeForm.changeType !== 'outsource'">
            <el-input v-model="woChangeForm.reason" type="textarea" :rows="2" placeholder="请输入变更原因" />
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: 确认提交 -->
      <div v-if="woChangeStep === 2">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="变更类型">{{ woChangeTypeLabel(woChangeForm.changeType) }}</el-descriptions-item>
          <el-descriptions-item label="变更原因">{{ woChangeForm.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否需要审批">
            <el-tag :type="woChangeNeedApproval ? 'warning' : 'success'" size="small">
              {{ woChangeNeedApproval ? '需要审批' : '直接执行' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert type="info" :closable="false" style="margin-top: 12px">
          提交后需要上级审批才能生效
        </el-alert>
      </div>

      <template #footer>
        <el-button v-if="woChangeStep > 0" @click="woChangeStep--">上一步</el-button>
        <el-button @click="woChangeVisible = false">取消</el-button>
        <el-button v-if="woChangeStep < 2" type="primary" @click="woChangeNext" :disabled="!woChangeForm.changeType">下一步</el-button>
        <el-button v-if="woChangeStep === 2" type="primary" :loading="woChangeLoading" @click="submitWoChange">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- 领料弹窗 -->
    <el-dialog v-model="issueVisible" title="生产领料" width="760px" destroy-on-close>
      <div v-if="detailData" style="margin-bottom: 12px; color: #606266">
        <span>工单号: {{ detailData.woNo }}</span>
        <span style="margin-left: 24px">产品: {{ detailData.productName }}</span>
      </div>

      <!-- 无BOM：手动领料模式 -->
      <template v-if="!issueHasBom">
        <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
          <template #title>该产品无BOM信息，请手动添加需领取的物料</template>
        </el-alert>
        <el-table :data="issueItems" border stripe size="small" v-loading="issueLoading">
          <el-table-column label="物料编号" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.materialNo">{{ row.materialNo }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="物料" min-width="160">
            <template #default="{ row }">
              <span v-if="row.materialName" style="margin-right: 4px">{{ row.materialName }}</span>
              <span v-else style="color:#c0c4cc">(未选)</span>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="80" show-overflow-tooltip>
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="55" align="center" />
          <el-table-column label="领取数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.issueQty" :min="0.001" :precision="3" size="small" controls-position="right" style="width: 100px" />
            </template>
          </el-table-column>
          <el-table-column label="仓库" width="140">
            <template #default="{ row }">
              <el-select v-model="row.warehouseId" size="small" style="width: 130px" placeholder="选择仓库"
                         @change="refreshIssueRowAvailable(row)">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="可用库存" width="90" align="right">
            <template #default="{ row }">
              <span :style="{ color: (row.availableQty || 0) >= (row.issueQty || 0) ? '#67c23a' : '#f56c6c', fontWeight: 600 }">
                {{ row.availableQty ?? '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeIssueRow($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 10px">
          <el-button type="primary" plain size="small" @click="addManualIssueRow">+ 新增物料</el-button>
        </div>
      </template>

      <!-- 有BOM：按BOM快照领料 -->
      <template v-else>
        <el-table :data="issueItems" border stripe size="small" v-loading="issueLoading" row-key="materialId">
          <el-table-column type="expand" width="40">
            <template #default="{ row }">
              <div v-if="row.substitutes && row.substitutes.length" style="padding: 8px 24px; background:#f6f8fa">
                <div style="margin-bottom:6px; color:#606266; font-size:12px">
                  替代料（主料不足时可以按比例领取替代料；主料等效量 = 领取量 ÷ 比率）
                </div>
                <el-table :data="row.substitutes" border size="small" stripe>
                  <el-table-column prop="materialNo" label="编码" width="110" show-overflow-tooltip />
                  <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
                  <el-table-column label="规格" width="90" show-overflow-tooltip>
                    <template #default="{ row: sub }">{{ $fmtSpec(sub.spec) }}</template>
                  </el-table-column>
                  <el-table-column prop="unit" label="单位" width="55" align="center" />
                  <el-table-column prop="priority" label="优先级" width="70" align="center" />
                  <el-table-column label="比率" width="70" align="center">
                    <template #default="{ row: sub }">1 : {{ sub.ratio }}</template>
                  </el-table-column>
                  <el-table-column label="可用" width="70" align="center">
                    <template #default="{ row: sub }">
                      <span :style="{ color: (sub.availableQty || 0) < (sub.subIssueQty || 0) ? '#F56C6C' : '#67C23A' }">
                        {{ sub.availableQty ?? '-' }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="本次领（替代料单位）" width="140">
                    <template #default="{ row: sub }">
                      <el-input-number v-model="sub.subIssueQty" :min="0" :precision="3" size="small"
                                       controls-position="right" style="width: 120px" />
                    </template>
                  </el-table-column>
                  <el-table-column label="仓库" width="140">
                    <template #default="{ row: sub }">
                      <el-select v-model="sub.subWarehouseId" size="small" style="width: 130px" placeholder="选择仓库"
                                 @change="refreshIssueRowAvailable(sub, true)">
                        <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
                      </el-select>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div v-else style="padding: 8px 24px; color:#909399">该物料未配置替代料</div>
            </template>
          </el-table-column>
          <el-table-column width="50" align="center">
            <template #header>
              <el-checkbox v-model="issueSelectAll" @change="toggleIssueSelectAll" />
            </template>
            <template #default="{ row }">
              <el-checkbox v-model="row.checked" :disabled="row.remaining <= 0" />
            </template>
          </el-table-column>
          <el-table-column label="物料名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.materialNo" style="color:#909399; margin-right:4px">{{ row.materialNo }}</span>{{ row.materialName || (row.isManual ? '(未选)' : '') }}
              <el-tag v-if="row.isManual" type="warning" size="small" effect="plain" style="margin-left:4px">手动</el-tag>
              <el-tag v-else-if="row.substitutes && row.substitutes.length" type="warning" size="small" effect="plain"
                      style="margin-left:4px" @click.stop>可替代</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="80" show-overflow-tooltip>
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column label="来源" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.locked" type="success" size="small">锁库</el-tag>
              <el-tag v-else type="" size="small">库存</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="requiredQty" label="需求" width="70" align="center" />
          <el-table-column prop="issuedTaskQty" label="领料任务数" width="90" align="center">
            <template #default="{ row }">
              <span :style="(row.issuedTaskQty||0)>0?'color:#409eff':'color:#c0c4cc'">{{ row.issuedTaskQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="issuedQty" label="已领" width="70" align="center" />
          <el-table-column label="本次领" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.issueQty" :min="0" :max="row.maxIssue" size="small" controls-position="right" style="width: 90px"
                @change="onIssueQtyChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="仓库" width="130">
            <template #default="{ row }">
              <el-select v-model="row.warehouseId" size="small" style="width: 120px"
                         :disabled="!!row.lockedWarehouseId"
                         @change="refreshIssueRowAvailable(row)">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="可用" width="65" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.availableQty < row.issueQty ? '#F56C6C' : '#67C23A' }">{{ row.availableQty ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ row, $index }">
              <el-button v-if="row.isManual" link type="danger" size="small" @click="removeIssueRow($index)">删除</el-button>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-alert type="info" :closable="false" style="margin-top: 12px">
          <template #title>领料将生成"生产领料"库存任务，仓管在库存任务中处理后完成出库；主料不足时可展开行选择已配置的替代料</template>
        </el-alert>
      </template>

      <template #footer>
        <el-button @click="issueVisible = false">取消</el-button>
        <el-button type="primary" :loading="issueSubmitting" @click="submitIssueMaterial">确认领料</el-button>
      </template>
    </el-dialog>

    <!-- 无 BOM 缺料请购对话框 -->
    <el-dialog v-model="shortageDialogVisible" title="物料不足, 请发起采购申请" width="760px" destroy-on-close append-to-body>
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
        <template #title>
          以下物料在库不足,请确认<b>需求日期</b>与<b>用途</b>后由系统生成采购申请,采购到货后再回来领料。
        </template>
      </el-alert>
      <el-table :data="shortageItems" border size="small" stripe style="margin-bottom: 12px" max-height="280">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialNo" label="物料编号" width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="needQty" label="需求" width="80" align="right" />
        <el-table-column prop="stockQty" label="在库" width="80" align="right" />
        <el-table-column prop="shortageQty" label="缺数" width="80" align="right">
          <template #default="{ row }">
            <span style="color: #f56c6c; font-weight: 600">{{ row.shortageQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column label="状态" min-width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.alreadyRequested" type="info" size="small">已有未办结申请</el-tag>
            <el-tag v-else type="warning" size="small">本次新建</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="100px" :model="shortageForm">
        <el-form-item label="需求日期" required>
          <el-date-picker v-model="shortageForm.expectedDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="希望采购到货的日期" :disabled-date="d => d < new Date(Date.now() - 86400000)"
                          style="width: 100%" />
        </el-form-item>
        <el-form-item label="用途" required>
          <el-input v-model="shortageForm.purpose" type="textarea" :rows="2"
                    placeholder="例: 生产XX产品 (工单 WOXX)" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shortageDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="shortageDialogLoading" @click="submitShortageRequest">生成采购申请</el-button>
      </template>
    </el-dialog>

    <!-- 退料弹窗 -->
    <el-dialog v-model="returnVisible" title="生产退料" width="720px" destroy-on-close>
      <div v-loading="returnLoading">
        <el-alert type="info" :closable="false" style="margin-bottom:12px">
          退料将生成"生产退料"库存任务，任务处理后物料回库。
        </el-alert>
        <el-table :data="returnItems" border size="small">
          <el-table-column prop="materialNo" label="物料编码" width="110" show-overflow-tooltip />
          <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="规格" width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ $fmtSpec(row.spec) || '-' }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="55" align="center" />
          <el-table-column prop="issuedTaskQty" label="领料任务数" width="90" align="center">
            <template #default="{ row }">
              <span :style="(row.issuedTaskQty||0)>0?'color:#409eff':'color:#c0c4cc'">{{ row.issuedTaskQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="issuedQty" label="已领数量" width="90" align="center" />
          <el-table-column prop="returnedTaskQty" label="退料任务数" width="90" align="center">
            <template #default="{ row }">
              <span :style="(row.returnedTaskQty||0)>0?'color:#e6a23c':'color:#c0c4cc'">{{ row.returnedTaskQty ?? 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="returnedQty" label="已退数量" width="90" align="center">
            <template #default="{ row }">
              <span v-if="(row.returnedQty||0)>0" style="color:#f56c6c">{{ row.returnedQty }}</span>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column label="本次退料" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.returnQty" :min="0" :precision="4" :max="Math.max(row.maxReturnQty, 0)" size="small" controls-position="right" style="width:110px" />
            </template>
          </el-table-column>
          <el-table-column label="入库仓库" min-width="130">
            <template #default="{ row }">
              <el-select v-model="row.warehouseId" size="small" style="width:120px" placeholder="请选择">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="returnItems.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="returnSubmitting" @click="submitReturnMaterial">确认退料</el-button>
      </template>
    </el-dialog>

    <!-- 报工弹窗 -->
    <el-dialog v-model="reportVisible" :title="reportTaskData?.assignType === 'team' ? '班组报工' : '工序报工'" :width="reportTaskData?.assignType === 'team' ? '650px' : '500px'" destroy-on-close>
      <div v-if="reportTaskData" style="margin-bottom: 16px; color: #606266">
        <div>
          工序: {{ reportTaskData.stepName }} (第{{ reportTaskData.stepNo }}道)
          <el-tag v-if="reportTaskData.assignType === 'team'" type="primary" size="small" style="margin-left: 8px">{{ reportTaskData.assignedTeamName }}</el-tag>
          <el-tag v-else-if="reportTaskData.assignType === 'person'" type="success" size="small" style="margin-left: 8px">{{ reportTaskData.assignedUserName }}</el-tag>
        </div>
        <div style="margin-top: 8px">
          计划数: {{ reportTaskData.planQty }}
          &emsp;已报工: {{ reportTaskData.qcTemplateId ? (reportTaskData.reportedQty ?? 0) : (reportTaskData.qualifiedQty ?? 0) }}
          <template v-if="reportTaskData.qcTemplateId">&emsp;已质检合格: {{ reportTaskData.qualifiedQty ?? 0 }}</template>
          &emsp;已不良: {{ reportTaskData.defectQty ?? 0 }}
        </div>
      </div>
      <el-form :model="reportForm" label-width="90px">
        <el-form-item label="合格数量">
          <el-input-number v-model="reportForm.qualifiedQty" :min="0" :max="reportMaxQty != null ? reportMaxQty : undefined" controls-position="right" style="width: 200px" @change="onReportQtyChange" />
          <span v-if="reportMaxQty != null" style="margin-left: 8px; color: #909399; font-size: 12px">上限: {{ reportMaxQty }}</span>
        </el-form-item>
        <el-form-item label="不良数量">
          <el-input-number v-model="reportForm.defectQty" :min="0" controls-position="right" style="width: 200px" @change="onReportDefectChange" />
        </el-form-item>
        <el-form-item label="工时(天)">
          <el-input-number v-model="reportForm.workHours" :min="0" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reportForm.remark" type="textarea" :rows="2" placeholder="可选备注" />
        </el-form-item>
      </el-form>
      <!-- 班组成员分配表 -->
      <div v-if="reportTaskData?.assignType === 'team' && reportMembers.length > 0" style="margin-top: 4px">
        <div style="font-weight: 600; margin-bottom: 8px; color: #303133">成员报工分配</div>
        <el-table :data="reportMembers" border stripe size="small">
          <el-table-column prop="userName" label="姓名" min-width="90">
            <template #default="{ row }">
              {{ row.userName }}
              <el-tag v-if="row.isLeader" type="warning" size="small" style="margin-left: 4px">组长</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="权重" width="70" align="center">
            <template #default="{ row }">{{ row.weight != null ? (row.weight * 100).toFixed(0) + '%' : '-' }}</template>
          </el-table-column>
          <el-table-column label="合格数" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.allocatedQty" :min="0" :precision="2" size="small" controls-position="right" style="width: 100px" />
            </template>
          </el-table-column>
          <el-table-column label="不良数" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.allocatedDefect" :min="0" :precision="2" size="small" controls-position="right" style="width: 100px" />
            </template>
          </el-table-column>
        </el-table>
        <div style="text-align: right; margin-top: 6px; color: #909399; font-size: 12px">
          合格合计: {{ reportMembersQualifiedTotal }} / {{ reportForm.qualifiedQty }}
          <span v-if="reportMembersQualifiedTotal !== reportForm.qualifiedQty" style="color: #F56C6C">（不一致）</span>
          &emsp;不良合计: {{ reportMembersDefectTotal }} / {{ reportForm.defectQty }}
          <span v-if="reportMembersDefectTotal !== reportForm.defectQty" style="color: #F56C6C">（不一致）</span>
        </div>
      </div>
      <el-alert v-if="reportTaskData?.qcTemplateId" type="info" :closable="false" style="margin-top: 8px">
        <template #title>该工序配有质检模板，报工后将自动创建工序质检任务</template>
      </el-alert>
      <el-alert v-if="reportLimitInfo" :type="reportLimitInfo.maxReportableQty > 0 ? 'warning' : 'error'" :closable="false" style="margin-top: 8px">
        <template #title>{{ reportLimitInfo.reason }}（最大可报: {{ reportLimitInfo.maxReportableQty }}）</template>
      </el-alert>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="reportSubmitting" @click="submitReport">确认报工</el-button>
      </template>
    </el-dialog>

    <!-- 销售订单详情弹窗 -->
    <el-dialog v-model="orderDetailVisible" title="销售订单详情" width="900px" destroy-on-close>
      <div v-loading="orderDetailLoading">
        <template v-if="orderDetailData">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="订单号">{{ orderDetailData.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ orderDetailData.customerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ orderDetailData.contactName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务员">{{ orderDetailData.salesmanName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">{{ orderDetailData.shippingAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结算方式">{{ orderDetailData.settlementType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="交货日期">{{ orderDetailData.deliveryDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="总金额">
              <span style="color: #f56c6c; font-weight: 600">¥{{ Number(orderDetailData.totalAmount || 0).toFixed(2) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="回款金额">
              <span :style="{ color: Number(orderDetailData.paymentAmount || 0) > 0 ? '#67C23A' : '#f56c6c', fontWeight: 600 }">
                ¥{{ Number(orderDetailData.paymentAmount || 0).toFixed(2) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ orderDetailData.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ orderDetailData.remark || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div style="font-weight: 600; margin: 16px 0 8px; color: #303133; font-size: 14px; padding-left: 10px; border-left: 3px solid #409EFF">订单明细</div>
          <el-table :data="orderDetailData.items || []" border stripe size="small">
            <el-table-column type="index" label="#" width="45" align="center" />
            <el-table-column prop="productName" label="产品名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="productCode" label="产品编号" min-width="140" show-overflow-tooltip />
            <el-table-column label="规格型号" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="70" align="center" />
            <el-table-column prop="unit" label="单位" width="55" align="center" />
            <el-table-column label="单价" width="80" align="right">
              <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="税率" width="60" align="center">
              <template #default="{ row }">{{ row.taxRate != null ? (row.taxRate * 100).toFixed(0) + '%' : '-' }}</template>
            </el-table-column>
            <el-table-column label="含税金额" width="95" align="right">
              <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="deliveryDate" label="交货日期" width="100" />
            <el-table-column label="已生产" width="65" align="center">
              <template #default="{ row }">{{ row.producedQty ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="已发货" width="65" align="center">
              <template #default="{ row }">{{ row.shippedQty ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="已入库" width="65" align="center">
              <template #default="{ row }">{{ row.warehouseInQty ?? 0 }}</template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="暂无数据" />
      </div>
    </el-dialog>

    <!-- 质检要求弹窗 -->
    <el-dialog v-model="qcReqVisible" title="工序质检要求" width="650px" destroy-on-close>
      <div v-loading="qcReqLoading">
        <template v-if="qcReqData?.hasQc">
          <el-descriptions :column="2" border style="margin-bottom: 16px">
            <el-descriptions-item label="模板名称">{{ qcReqData.templateName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="模板编码">{{ qcReqData.templateCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="抽样方式">{{ qcReqData.sampleMode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="抽样比例">{{ qcReqData.sampleRate ? (qcReqData.sampleRate * 100) + '%' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="AQL等级">{{ qcReqData.aqlLevel || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div style="font-weight: 600; margin-bottom: 8px; color: #303133">检验项目</div>
          <el-table :data="qcReqData.items || []" border stripe size="small">
            <el-table-column prop="itemName" label="检验项" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.itemName }}</span>
                <el-tag v-if="row.isRequired === 1" type="danger" size="small" effect="plain" style="margin-left:4px">必填</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="itemType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.itemType === 'quantitative'" size="small">计量</el-tag>
                <el-tag v-else-if="row.itemType === 'text'" type="info" size="small">文本</el-tag>
                <el-tag v-else-if="row.itemType === 'radio'" type="warning" size="small">单选</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="standardValue" label="标准值" width="90" align="center">
              <template #default="{ row }">
                <span v-if="row.itemType === 'quantitative'">{{ row.standardValue || '-' }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="公差范围" width="110" align="center">
              <template #default="{ row }">
                <template v-if="row.itemType === 'quantitative'">
                  <span v-if="row.toleranceMin || row.toleranceMax">{{ row.toleranceMin ?? '' }} ~ {{ row.toleranceMax ?? '' }}</span>
                  <span v-else class="text-muted">-</span>
                </template>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="60" align="center">
              <template #default="{ row }">
                <span v-if="row.itemType === 'quantitative'">{{ row.unit || '-' }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="method" label="检验方法" min-width="100" show-overflow-tooltip />
            <el-table-column label="关键项" width="65" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isCritical === 1" type="danger" size="small">是</el-tag>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!qcReqData.items?.length" description="暂无检验项目" :image-size="40" />
        </template>
        <el-empty v-else description="该工序未配置质检要求" />
      </div>
    </el-dialog>

    <!-- 质检记录弹窗 -->
    <el-dialog v-model="qcRecordVisible" title="工序质检记录" width="760px" destroy-on-close>
      <div v-loading="qcRecordLoading">
        <template v-if="qcRecordData">
          <el-descriptions :column="3" border style="margin-bottom:16px" size="small">
            <el-descriptions-item label="任务编号">{{ qcRecordData.taskNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工序名称">{{ qcRecordData.stepName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验结果">
              <el-tag v-if="qcRecordData.result === 'pass'" type="success" size="small">合格</el-tag>
              <el-tag v-else-if="qcRecordData.result === 'fail'" type="danger" size="small">不合格</el-tag>
              <el-tag v-else-if="qcRecordData.result === 'concession'" type="warning" size="small">让步接收</el-tag>
              <span v-else class="text-muted">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="计划数量">{{ qcRecordData.planQty ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="合格数量">
              <span style="color:#67c23a;font-weight:600">{{ qcRecordData.passQty ?? '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="不合格数量">
              <span style="color:#f56c6c;font-weight:600">{{ qcRecordData.failQty ?? '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="检验员">{{ qcRecordData.inspectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验时间">{{ qcRecordData.inspectedAt ? formatDateTime(qcRecordData.inspectedAt) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="结论">{{ qcRecordData.conclusion || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="qcRecordData.results?.length">
            <div style="font-weight:600;margin-bottom:8px;color:#303133">检验项目明细</div>
            <el-table :data="qcRecordData.results" border stripe size="small" style="margin-bottom:16px">
              <el-table-column prop="itemName" label="检验项" min-width="120" show-overflow-tooltip />
              <el-table-column prop="itemType" label="类型" width="70" align="center">
                <template #default="{ row: r }">
                  <el-tag v-if="r.itemType === 'quantitative'" size="small">计量</el-tag>
                  <el-tag v-else-if="r.itemType === 'text'" type="info" size="small">文本</el-tag>
                  <el-tag v-else-if="r.itemType === 'radio'" type="warning" size="small">单选</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="标准值/范围" width="120" align="center">
                <template #default="{ row: r }">
                  <template v-if="r.itemType === 'quantitative'">
                    <div v-if="r.standardValue">{{ r.standardValue }}</div>
                    <div v-if="r.toleranceMin || r.toleranceMax" style="font-size:11px;color:#909399">
                      {{ r.toleranceMin ?? '' }} ~ {{ r.toleranceMax ?? '' }} {{ r.unit || '' }}
                    </div>
                    <span v-if="!r.standardValue && !r.toleranceMin && !r.toleranceMax" class="text-muted">-</span>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="actualValue" label="实测值" width="90" align="center">
                <template #default="{ row: r }">
                  <span v-if="r.actualValue != null">{{ r.actualValue }}</span>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="判定" width="70" align="center">
                <template #default="{ row: r }">
                  <el-tag v-if="r.isPass === 1" type="success" size="small">合格</el-tag>
                  <el-tag v-else-if="r.isPass === 0" type="danger" size="small">不合格</el-tag>
                  <span v-else class="text-muted">-</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip>
                <template #default="{ row: r }">{{ r.remark || '-' }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div v-if="qcRecordData.remark" style="color:#606266;font-size:13px">
            <span style="font-weight:600">整体备注：</span>{{ qcRecordData.remark }}
          </div>
        </template>
        <el-empty v-else description="暂无质检记录" />
      </div>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="WORK_ORDER_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 成品入库弹窗 -->
    <el-dialog v-model="stockInVisible" title="成品入库" width="440px" destroy-on-close>
      <div style="margin-bottom: 12px; color: #606266; font-size: 13px">
        <div>工单号: {{ detailData.woNo }}</div>
        <div style="margin-top: 4px">
          完工数量: <strong>{{ detailData.completedQty || 0 }}</strong> &emsp;
          已生成任务: <strong>{{ detailData.stockInTaskQty || 0 }}</strong> &emsp;
          已确认入库: <strong>{{ detailData.stockInQty || 0 }}</strong>
        </div>
        <div style="margin-top: 4px; color: #409eff">
          可入库数量: <strong>{{ stockInMaxQty }}</strong>
        </div>
      </div>
      <el-form :model="stockInForm" label-width="90px">
        <el-form-item label="入库数量" required>
          <el-input-number v-model="stockInForm.qty" :min="0.0001" :max="stockInMaxQty" :precision="4" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="目标仓库">
          <el-select v-model="stockInForm.warehouseId" placeholder="默认产品仓库" clearable style="width: 200px">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockInVisible = false">取消</el-button>
        <el-button type="primary" :loading="stockInSubmitting" @click="submitStockIn">确认入库</el-button>
      </template>
    </el-dialog>

    <!-- ========== 半成品入库 ========== -->
    <el-dialog v-model="semiStockInVisible" title="半成品入库" width="520px" destroy-on-close append-to-body>
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="工序">{{ semiForm.stepNo }} - {{ semiForm.stepName }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ semiForm.productName }}</el-descriptions-item>
        <el-descriptions-item label="合格数">{{ semiForm.qualified }}</el-descriptions-item>
        <el-descriptions-item label="已入库">{{ semiForm.stocked }}</el-descriptions-item>
        <el-descriptions-item label="可入数" :span="2">
          <span style="color:#67c23a; font-weight:600">{{ semiForm.maxIn }} 件</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-form :model="semiForm" label-width="90px">
        <el-form-item label="入库数量">
          <el-input-number v-model="semiForm.qty" :min="0.0001" :max="semiForm.maxIn" :precision="4"
            controls-position="right" style="width: 200px" />
          <el-button type="primary" link size="small" style="margin-left: 8px"
            @click="semiForm.qty = semiForm.maxIn">全部入库</el-button>
        </el-form-item>
        <el-form-item label="入库仓库" required>
          <el-select v-model="semiForm.warehouseId" placeholder="请选择仓库" style="width: 260px">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <div style="font-size:12px;color:#909399;line-height:1.6">
          ⚠️ 提交后会生成 <b>"半成品入库"库存任务</b>(状态待处理), 仓库人员在"库存管理 → 库存任务"中点击处理后才真正落库.<br>
          建档约定: 半成品物料编号 <b>SEMI_{{ semiForm.productNo }}_{{ semiForm.stepName }}</b>, 名称 <b>{{ semiForm.productName }}_{{ semiForm.stepName }}</b>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="semiStockInVisible = false">取消</el-button>
        <el-button type="primary" :loading="semiSubmitting" @click="submitSemiStockIn">确认入库</el-button>
      </template>
    </el-dialog>

    <!-- ========== 撤销半成品入库 ========== -->
    <el-dialog v-model="semiRevokeVisible" title="撤销半成品入库" width="480px" destroy-on-close append-to-body>
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="工序">{{ semiForm.stepNo }} - {{ semiForm.stepName }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ semiForm.productName }}</el-descriptions-item>
        <el-descriptions-item label="已入库">{{ semiForm.stocked }}</el-descriptions-item>
        <el-descriptions-item label="已被领走">{{ semiForm.issued }}</el-descriptions-item>
        <el-descriptions-item label="可撤数量" :span="2">
          <span style="color:#f56c6c; font-weight:600">{{ semiForm.maxRevoke }} 件</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-form :model="semiForm" label-width="90px">
        <el-form-item label="撤销数量">
          <el-input-number v-model="semiForm.qty" :min="0.0001" :max="semiForm.maxRevoke" :precision="4"
            controls-position="right" style="width: 200px" />
          <el-button type="primary" link size="small" style="margin-left: 8px"
            @click="semiForm.qty = semiForm.maxRevoke">全部撤回</el-button>
        </el-form-item>
        <div style="font-size:12px;color:#909399">已被下道工序领走的部分无法撤销.</div>
      </el-form>
      <template #footer>
        <el-button @click="semiRevokeVisible = false">取消</el-button>
        <el-button type="danger" :loading="semiSubmitting" @click="submitSemiRevoke">确认撤销</el-button>
      </template>
    </el-dialog>

    <!-- ========== 半成品领料 (下道工序领上道工序入的半成品) ========== -->
    <el-dialog v-model="semiIssueVisible" title="半成品领料" width="520px" destroy-on-close append-to-body>
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="本工序">{{ semiForm.stepNo }} - {{ semiForm.stepName }}</el-descriptions-item>
        <el-descriptions-item label="本工序计划">{{ semiForm.planQty }}</el-descriptions-item>
        <el-descriptions-item label="上道工序" :span="2">
          {{ semiForm.prevStepNo }} - {{ semiForm.prevStepName }}
        </el-descriptions-item>
        <el-descriptions-item label="上道已入库">{{ semiForm.prevStocked }}</el-descriptions-item>
        <el-descriptions-item label="上道已被领">{{ semiForm.prevIssued }}</el-descriptions-item>
        <el-descriptions-item label="可领数量" :span="2">
          <span style="color:#409eff; font-weight:600">{{ semiForm.maxIssue }} 件</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-form :model="semiForm" label-width="90px">
        <el-form-item label="领料数量">
          <el-input-number v-model="semiForm.qty" :min="0.0001" :max="semiForm.maxIssue" :precision="4"
            controls-position="right" style="width: 200px" />
          <el-button type="primary" link size="small" style="margin-left: 8px"
            @click="semiForm.qty = Math.min(semiForm.maxIssue, semiForm.planQty)">按本工序计划</el-button>
        </el-form-item>
        <el-form-item label="出库仓库" required>
          <el-select v-model="semiForm.warehouseId" placeholder="请选择仓库" style="width: 260px">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <div style="font-size:12px;color:#909399">
          ⚠️ 提交后会生成 <b>"半成品领料"库存任务</b>(状态待处理), 仓库人员在"库存管理 → 库存任务"中处理后才真正出库.<br>
          仓管处理完成后, 本工序"报工"按钮自动解禁.
        </div>
      </el-form>
      <template #footer>
        <el-button @click="semiIssueVisible = false">取消</el-button>
        <el-button type="primary" :loading="semiSubmitting" @click="submitSemiIssue">确认领料</el-button>
      </template>
    </el-dialog>

    <!-- 产品选择弹窗 -->
    <MaterialPickerDialog v-model="woProductPickerVisible" title="选择产品" :multiple="false"
                          @select="onWoProductSelected" />

    <!-- 领料专用物料选择弹窗 (多选, 一次加多行, 显示可用库存) -->
    <MaterialPickerDialog v-model="issuePickerVisible" title="选择领料物料" :multiple="true" :show-stock="true"
                          @select="onIssueMaterialsPicked" />

    <!-- 工序图纸 -->
    <ProcessDrawingDialog
      v-model="woDrawingDialogVisible"
      mode="wo"
      :title="`工序图纸 - ${woDrawingTarget.stepName || ''}`"
      :product-id="woDrawingTarget.productId"
      :product-name="woDrawingTarget.productName"
      :step-no="woDrawingTarget.stepNo"
      :step-name="woDrawingTarget.stepName"
      :process-task-id="woDrawingTarget.processTaskId"
      :work-order-id="woDrawingTarget.workOrderId"
      @changed="loadDrawingSummary"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Document } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import ProcessDrawingDialog from '../components/ProcessDrawingDialog.vue'
import {
  getWorkOrderPage,
  getWorkOrderDetail,
  createWorkOrder,
  cancelWorkOrder,
  getWorkOrderStatistics,
  getWorkOrderStats,
  getWorkOrderTasks,
  getWoDrawingSummary,
  getWorkOrderBom,
  getWorkOrderChangeHistory,
  getWorkOrderOutsourceStatus,
  getIssueStatus,
  issueMaterial,
  checkIssueShortage,
  generatePurchaseRequestsForShortage,
  getWorkOrderPurchaseProgress,
  returnMaterial,
  reportTask,
  getReportLimit,
  getQcRequirements,
  getPlanAttachments,
  createWorkOrderStockInTask,
  appendWorkOrder
} from '@/api/production'
import { getInvTasksByWorkOrder, getAvailableQty } from '@/api/inventory'
import { getWorkReportPage, rejectWorkReport,
  semiStockIn, semiStockRevoke, semiStockIssue, semiStockCheckReport } from '@/api/workreport'
import { getQcTaskDetail } from '@/api/quality'
import { formatDateTime } from '@/utils/format'
import { isImage } from '@/utils/attachment'
import { getMaterialPage } from '@/api/master'
import { getTeamMembers } from '@/api/team'
import { getSalesOrderPage, getSalesOrderDetail } from '@/api/order'
import { getWoCost } from '@/api/finance'
import StatCard from '@/components/StatCard.vue'
import { useAiChatStore } from '@/store/ai-chat'
import { useExtFields } from '@/composables/useExtFields'

const aiChatStore = useAiChatStore()

function secondaryDisplay(qty, unitConfig) {
  if (!qty || !unitConfig) return ''
  try {
    const cfg = JSON.parse(unitConfig)
    const c = cfg.conversions?.[0]
    if (c) return `${(qty * c.ratio).toFixed(4)} ${c.unit}`
  } catch {}
  return ''
}

// ==================== 列配置 ====================
const WORK_ORDER_DEFAULT_COLUMNS = [
  { key: 'woNo', label: '工单号', minWidth: 200 },
  { key: 'orderNo', label: '销售订单号', minWidth: 160 },
  { key: 'productNo', label: '产品编号', minWidth: 130 },
  { key: 'productName', label: '产品名称', minWidth: 150 },
  { key: 'productSpec', label: '规格型号', minWidth: 120 },
  { key: 'planQty', label: '计划数量', minWidth: 100, align: 'center' },
  { key: 'completedQty', label: '完成数量', minWidth: 100, align: 'center' },
  { key: 'deliveryDate', label: '交货日期', minWidth: 120 },
  { key: 'priority', label: '优先级', minWidth: 90, align: 'center' },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('production_work_order', WORK_ORDER_DEFAULT_COLUMNS)
const { fieldConfigs, listFieldConfigs, searchFieldConfigs, loadFieldConfigs, getExtFieldValue } = useExtFields('work_order')
// 工单 ext_fields 是 work_order + sales_order + sales_order_item 三个模块的合并 JSON,
// 详情展示时把三套字段配置合并成一份, 让用户能看到所有继承自订单的字段
const orderExt = useExtFields('sales_order')
const orderItemExt = useExtFields('sales_order_item')
const allInheritedFieldConfigs = computed(() => {
  const seen = new Set()
  const out = []
  ;[fieldConfigs.value, orderExt.fieldConfigs.value, orderItemExt.fieldConfigs.value].forEach(arr => {
    (arr || []).forEach(fc => {
      if (fc.enabled && !seen.has(fc.fieldKey)) { seen.add(fc.fieldKey); out.push(fc) }
    })
  })
  return out
})

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'workOrder' && data.records?.length) {
    const rec = data.records[0]
    formData.productionMode = rec.productionMode || 'MTO'
    formData.orderId = rec.orderId || null
    formData.productId = rec.productId || null
    formData.productName = rec.productName || ''
    formData.planQty = rec.planQty || 1
    formData.planStartDate = rec.planStartDate || ''
    formData.planEndDate = rec.planEndDate || ''
    formData.remark = rec.remark || ''
    formVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入工单表单，请确认后提交')
  }
})

// ==================== 状态选项 ====================
const statusOptions = [
  { label: '待排产', value: 'pending' },
  { label: '已排产', value: 'scheduled' },
  { label: '生产中', value: 'in_progress' },
  { label: '生产完成', value: 'completed' },
  { label: '已取消', value: 'cancelled' }
]
const invTaskStatusMap = {
  null: '未生成',
  undefined: '未生成',
  '': '未生成',
  partial: '部分生成',
  generated: '全部生成'
}
const invTaskStatusTagType = {
  null: 'info',
  undefined: 'info',
  '': 'info',
  partial: 'warning',
  generated: 'success'
}
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))

function statusLabel(status) {
  return statusMap[status] || status || '-'
}

function statusTagType(status) {
  const map = {
    pending: 'info',
    scheduled: 'warning',
    in_progress: '',
    completed: 'success',
    cancelled: 'danger'
  }
  return map[status] || 'info'
}

function priorityLabel(priority) {
  const map = { 0: '低', 1: '普通', 2: '高', 3: '紧急' }
  return map[priority] ?? priority ?? '-'
}

function priorityTagType(priority) {
  const map = { 0: 'info', 1: '', 2: 'warning', 3: 'danger' }
  return map[priority] ?? 'info'
}

function taskStatusLabel(status) {
  const map = { pending: '待开始', ready: '就绪', in_progress: '进行中', pending_qc: '待质检', completed: '已完成', skipped: '已跳过' }
  return map[status] || status || '-'
}

function taskStatusTagType(status) {
  const map = { pending: 'info', ready: '', in_progress: 'warning', pending_qc: 'danger', completed: 'success', skipped: 'info' }
  return map[status] || 'info'
}

function changeTypeLabel(type) {
  const map = {
    create: '创建',
    status_change: '状态变更',
    qty_change: '数量变更',
    schedule: '排产',
    assign: '派工',
    cancel: '取消',
    update: '更新',
    remark: '备注',
    outsource: '工序转外协',
    modify_qty: '修改计划数量',
    modify_date: '修改交货日期',
    modify_params: '参数变更',
    modify_person: '责任人变更'
  }
  return map[type] || type || '变更'
}

// 把变更历史的 changeContent(可能是 JSON,也可能是中文摘要)统一格式化为中文
function formatChangeContent(item) {
  const raw = item?.changeContent || item?.content || ''
  if (!raw) return '-'
  const trimmed = String(raw).trim()
  // 已是中文摘要(后端新写的格式以"【...】"开头) → 原样
  if (!trimmed.startsWith('{')) return raw
  // 否则尝试解析 JSON 转中文
  let body
  try { body = JSON.parse(trimmed) } catch { return raw }

  const type = item?.changeType || body.changeType
  const parts = []
  parts.push(`【${changeTypeLabel(type)}】`)

  if (type === 'modify_qty') {
    if (body.newPlanQty != null) parts.push(`新计划数量: ${body.newPlanQty}`)
  } else if (type === 'modify_date') {
    if (body.newDeliveryDate) parts.push(`新交货日期: ${body.newDeliveryDate}`)
  } else if (type === 'outsource') {
    if (body.processTaskId) {
      const t = (taskList.value || []).find(x => x.id === body.processTaskId)
      if (t) parts.push(`工序: ${t.stepName};`)
      else parts.push(`工序ID: ${body.processTaskId};`)
    }
    if (body.outsourceSupplierId) {
      const s = (outsourceSupplierOptions.value || []).find(x => x.id === body.outsourceSupplierId)
      if (s) parts.push(`供应商: ${s.name};`)
      else parts.push(`供应商ID: ${body.outsourceSupplierId};`)
    }
    if (body.outsourceQty != null) parts.push(`数量: ${body.outsourceQty};`)
    if (body.outsourceUnitPrice != null) parts.push(`单价: ¥${body.outsourceUnitPrice};`)
    if (body.outsourceTaxRate != null) parts.push(`税率: ${body.outsourceTaxRate}%;`)
    if (body.outsourceDeliveryDate) parts.push(`交付日期: ${body.outsourceDeliveryDate}`)
  } else {
    // 未知类型 → 把 key 简单列出来
    Object.keys(body).forEach(k => {
      if (k === 'changeType' || k === 'workOrderId' || k === 'reason') return
      const v = body[k]
      if (v != null && v !== '') parts.push(`${k}: ${v};`)
    })
  }

  if (body.reason) parts.push(`/ 原因: ${body.reason}`)
  return parts.join(' ')
}

function changeTypeColor(type) {
  const map = {
    create: 'success',
    status_change: 'warning',
    qty_change: '',
    schedule: '',
    assign: '',
    cancel: 'danger',
    update: 'info',
    remark: 'info'
  }
  return map[type] || 'info'
}

// ==================== 视图模式 ====================
const viewMode = ref('table')
const kanbanLoading = ref(false)
const kanbanData = ref([])

const kanbanColumns = computed(() => {
  const columns = [
    { status: 'pending', label: '待排产', color: '#909399', items: [] },
    { status: 'scheduled', label: '排产中', color: '#E6A23C', items: [] },
    { status: 'in_progress', label: '生产中', color: '#409EFF', items: [] },
    { status: 'completed', label: '已完工', color: '#67C23A', items: [] }
  ]
  for (const item of kanbanData.value) {
    const col = columns.find(c => c.status === item.status)
    if (col) col.items.push(item)
  }
  return columns
})

// 交期倒计时 & 延期判断
function deliveryCountdown(item) {
  if (!item.deliveryDate) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const delivery = new Date(item.deliveryDate)
  delivery.setHours(0, 0, 0, 0)
  return Math.ceil((delivery - today) / (1000 * 60 * 60 * 24))
}

function deliveryCountdownText(item) {
  const days = deliveryCountdown(item)
  if (days === null) return '-'
  if (days < 0) return `已逾期 ${Math.abs(days)} 天`
  if (days === 0) return '今日交付'
  return `剩余 ${days} 天`
}

function isOverdue(item) {
  if (item.status === 'completed' || item.status === 'cancelled') return false
  const days = deliveryCountdown(item)
  return days !== null && days < 0
}

watch(viewMode, (mode) => {
  if (mode === 'kanban' && kanbanData.value.length === 0) {
    loadKanbanData()
  }
})

async function loadKanbanData() {
  kanbanLoading.value = true
  try {
    const res = await getWorkOrderPage({ pageNum: 1, pageSize: 200 })
    kanbanData.value = (res.data?.records || res.data?.list || []).filter(
      w => w.status !== 'cancelled'
    )
  } finally {
    kanbanLoading.value = false
  }
}

// ==================== 统计卡片 ====================
const statistics = reactive({
  pending: 0,
  inProgress: 0,
  completed: 0,
  overdue: 0
})

async function loadStatistics() {
  try {
    // Try the new stats endpoint first, fallback to statistics
    let res
    try {
      res = await getWorkOrderStats()
    } catch {
      res = await getWorkOrderStatistics()
    }
    const data = res.data || {}
    statistics.pending = data.pending ?? data.pendingCount ?? 0
    statistics.inProgress = data.inProgress ?? data.in_progress ?? data.inProgressCount ?? 0
    statistics.completed = data.completed ?? data.completedCount ?? 0
    statistics.overdue = data.overdue ?? data.overdueCount ?? data.delayed ?? data.delayedCount ?? 0
  } catch {
    // ignore - statistics are optional
  }
}

function filterByStatus(status) {
  if (status === 'overdue') {
    // 延期/逾期:清空具体状态过滤,改为后端 overdue 标志
    queryParams.status = ''
    queryParams.overdue = true
  } else {
    queryParams.status = status
    queryParams.overdue = undefined
  }
  queryParams.pageNum = 1
  loadData()
}

// ==================== 查询与表格 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  woNo: '',
  productNo: '',
  productName: '',
  spec: '',
  orderNo: '',
  status: '',
  overdue: undefined,
  extFields: {},
  pageNum: 1,
  pageSize: 20
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      woNo: queryParams.woNo || undefined,
      productNo: queryParams.productNo || undefined,
      productName: queryParams.productName || undefined,
      spec: queryParams.spec || undefined,
      orderNo: queryParams.orderNo || undefined,
      status: queryParams.status || undefined,
      overdue: queryParams.overdue || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    if (queryParams.extFields) {
      Object.entries(queryParams.extFields).forEach(([k, v]) => {
        if (v !== null && v !== undefined && v !== '') params[`ext_${k}`] = v
      })
    }
    const res = await getWorkOrderPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.woNo = ''
  queryParams.productNo = ''
  queryParams.productName = ''
  queryParams.spec = ''
  queryParams.orderNo = ''
  queryParams.status = ''
  queryParams.overdue = undefined
  queryParams.extFields = {}
  queryParams.pageNum = 1
  loadData()
}

// ==================== 逾期/延期判定 ====================
// 逾期:未完工/未取消 且 当前时间已超过 deliveryDate
function isWoOverdue(row) {
  if (!row || !row.deliveryDate) return false
  if (row.status === 'completed' || row.status === 'cancelled') return false
  return new Date(row.deliveryDate) < new Date(new Date().toDateString())
}
// 延期:当前交货日期晚于工单创建时的原始交货日期
function isWoPostponed(row) {
  if (!row || !row.deliveryDate || !row.originalDeliveryDate) return false
  return new Date(row.deliveryDate) > new Date(row.originalDeliveryDate)
}

// ==================== 详情抽屉 ====================
const detailVisible = ref(false)
const detailData = ref(null)
const detailActiveTab = ref('bom')

// Tab 子数据
const taskList = ref([])
const taskLoading = ref(false)
const bomList = ref([])
const bomLoading = ref(false)
const historyList = ref([])
const historyLoading = ref(false)
const woReportList = ref([])
const woReportLoading = ref(false)

// 当前查看的工单ID
const currentWoId = ref(null)

// 完成进度百分比
const completionPercentage = computed(() => {
  if (!detailData.value || !detailData.value.planQty) return 0
  const pct = Math.round((detailData.value.completedQty || 0) / detailData.value.planQty * 100)
  return Math.min(pct, 100)
})

function isReportableTask(row) {
  return !row.isOutsourceable
    && (row.status === 'ready'
      || row.status === 'in_progress'
      || (row.status === 'pending_qc' && row.qcTemplateId))
}

function getNextProcessTask(row) {
  const currentStep = Number(row.stepNo || 0)
  return (taskList.value || [])
    .filter(t => t.id !== row.id && t.status !== 'cancelled' && Number(t.stepNo || 0) > currentStep)
    .sort((a, b) => Number(a.stepNo || 0) - Number(b.stepNo || 0))[0]
}

function hasProcessReported(row) {
  return Number(row.reportedQty || 0) > 0
    || Number(row.qualifiedQty || 0) > 0
    || Number(row.defectQty || 0) > 0
}

function canShowSemiStockIn(row) {
  const next = getNextProcessTask(row)
  return row.status !== 'cancelled'
    && next
    && !hasProcessReported(next)
    && Number(row.qualifiedQty || 0) > Number(row.semiStockedQty || 0)
}

async function handleView(row) {
  try {
    const res = await getWorkOrderDetail(row.id)
    detailData.value = res.data
    currentWoId.value = row.id
    detailActiveTab.value = 'bom'

    // Reset tab data
    taskList.value = []
    bomList.value = []
    historyList.value = []
    woReportList.value = []
    invTasksList.value = []
    woCost.value = null
    planAttachments.value = []
    purchaseProgressList.value = []
    hasShortageHistory.value = false

    detailVisible.value = true

    // 预加载工序任务（工艺路线tab常用）
    loadTasks()
    // 默认 tab 是 BOM 快照,但 el-tabs 程序化设置初始值时不会触发 tab-change,
    // 所以这里要主动 loadBom 一次,否则首次打开看到的是空表
    loadBom()
    // 预探查采购进度: 有数据则显示 tab (避免无关工单页面塞个空 tab)
    loadPurchaseProgress()
    // 检查领料状态
    if (detailData.value?.status === 'in_progress') {
      getIssueStatus(row.id).then(res => {
        issueAllDone.value = !!res.data?.allIssued
      }).catch(() => {})
    }
  } catch {
    // error handled by interceptor
  }
}

async function handleTabChange(tabName) {
  if (tabName === 'bom' && bomList.value.length === 0) {
    await loadBom()
  } else if (tabName === 'cost' && !woCost.value) {
    await loadCost()
  } else if (tabName === 'outsource') {
    await loadOutsourceStatus()
  } else if (tabName === 'attachments' && planAttachments.value.length === 0) {
    await loadPlanAttachments()
  } else if (tabName === 'history' && historyList.value.length === 0) {
    await loadHistory()
  } else if (tabName === 'workreport') {
    await loadWoReports()
  } else if (tabName === 'invTasks') {
    await loadInvTasks()
  } else if (tabName === 'purchase') {
    await loadPurchaseProgress()
  }
}

// ==================== 采购进度 ====================
const purchaseProgressList = ref([])
const purchaseProgressLoading = ref(false)
// 表头有进度则展示 tab; 没有则隐藏 — 一旦加载过并发现有数据, hasShortageHistory 会保持 true
const hasShortageHistory = ref(false)

async function loadPurchaseProgress() {
  if (!currentWoId.value) return
  purchaseProgressLoading.value = true
  try {
    const res = await getWorkOrderPurchaseProgress(currentWoId.value)
    purchaseProgressList.value = res.data || []
    if (purchaseProgressList.value.length > 0) hasShortageHistory.value = true
  } catch {
    purchaseProgressList.value = []
  } finally {
    purchaseProgressLoading.value = false
  }
}

function purchaseStageTag(stage) {
  switch (stage) {
    case '已到货': return 'success'
    case '部分到货': return 'warning'
    case '已下采购单': return 'primary'
    case '待下采购单': return ''
    case '待审批': return 'warning'
    case '草稿': return 'info'
    case '已驳回':
    case '已取消': return 'danger'
    default: return 'info'
  }
}

async function loadTasks() {
  if (!currentWoId.value) return
  taskLoading.value = true
  try {
    const res = await getWorkOrderTasks(currentWoId.value)
    taskList.value = res.data || []
  } catch {
    taskList.value = detailData.value?.tasks || []
  } finally {
    taskLoading.value = false
  }
  loadDrawingSummary()
  // 异步检查每道工序是否被上道半成品入库阻塞 (未领料 → 报工按钮置灰)
  loadSemiBlockReasons()
}

async function loadSemiBlockReasons() {
  const list = taskList.value || []
  if (!list.length) return
  await Promise.all(list.map(async t => {
    if (!t.id || t.status === 'completed' || t.status === 'cancelled') return
    try {
      const res = await semiStockCheckReport(t.id)
      t._semiBlockReason = res.data?.blocked ? (res.data?.reason || '需领料') : ''
    } catch { t._semiBlockReason = '' }
  }))
  // 触发响应式
  taskList.value = [...list]
}

// ========== 半成品入库 / 撤销 / 领料 弹窗状态 ==========
const semiStockInVisible = ref(false)
const semiRevokeVisible = ref(false)
const semiIssueVisible = ref(false)
const semiSubmitting = ref(false)
const semiForm = reactive({
  taskId: null, stepNo: null, stepName: '',
  productNo: '', productName: '',
  qualified: 0, stocked: 0, issued: 0, planQty: 0,
  maxIn: 0, maxRevoke: 0, maxIssue: 0,
  // 领料弹窗用 — 上道工序信息
  prevStepNo: null, prevStepName: '', prevStocked: 0, prevIssued: 0,
  qty: 0, warehouseId: null
})

function fillProductContext() {
  // 工单详情里取产品编号 / 名 (用于建档命名展示)
  semiForm.productNo = detailData.value?.productNo || detailData.value?.materialNo || ''
  semiForm.productName = detailData.value?.productName || detailData.value?.materialName || ''
}

async function openSemiStockIn(row) {
  await loadWarehouseOptions()
  const qualified = Number(row.qualifiedQty || 0)
  const stocked = Number(row.semiStockedQty || 0)
  const issued = Number(row.semiIssuedQty || 0)
  // 下道工序已直接消耗数 (= 下道报工 - 已被领料)
  const next = (taskList.value || [])
    .filter(t => t.id !== row.id && t.status !== 'cancelled' && Number(t.stepNo || 0) > Number(row.stepNo || 0))
    .sort((a, b) => Number(a.stepNo || 0) - Number(b.stepNo || 0))[0]
  let nextDirect = 0
  if (next) {
    const nextReported = Number(next.reportedQty || 0) > 0 ? Number(next.reportedQty || 0) : Number(next.qualifiedQty || 0)
    const nextTotal = nextReported + Number(next.defectQty || 0)
    nextDirect = Math.max(0, nextTotal - issued)
  }
  const max = Math.max(0, Number((qualified - stocked - nextDirect).toFixed(4)))
  if (max <= 0) {
    ElMessage.warning(`合格数已全部流转: 合格 ${qualified} - 已入 ${stocked} - 下道已直接消耗 ${nextDirect}`)
    return
  }
  Object.assign(semiForm, {
    taskId: row.id, stepNo: row.stepNo, stepName: row.stepName,
    qualified, stocked, maxIn: max, qty: max,
    warehouseId: null
  })
  fillProductContext()
  semiStockInVisible.value = true
}

async function openSemiRevoke(row) {
  const stocked = Number(row.semiStockedQty || 0)
  const issued = Number(row.semiIssuedQty || 0)
  const max = Number((stocked - issued).toFixed(4))
  if (max <= 0) { ElMessage.warning('已被下道工序领走, 无可撤数量'); return }
  Object.assign(semiForm, {
    taskId: row.id, stepNo: row.stepNo, stepName: row.stepName,
    stocked, issued, maxRevoke: max, qty: max
  })
  fillProductContext()
  semiRevokeVisible.value = true
}

async function openSemiIssue(row) {
  await loadWarehouseOptions()
  // 找上道工序 (同 stepGroup ≠ cancelled 中 stepNo < 当前的最大那条)
  const sorted = (taskList.value || [])
    .filter(t => t.id !== row.id && t.status !== 'cancelled' && Number(t.stepNo || 0) < Number(row.stepNo || 0))
    .sort((a, b) => Number(b.stepNo || 0) - Number(a.stepNo || 0))
  const prev = sorted[0]
  if (!prev) { ElMessage.warning('未找到上道工序'); return }
  const prevStocked = Number(prev.semiStockedQty || 0)
  const prevIssued = Number(prev.semiIssuedQty || 0)
  const maxIssue = Number((prevStocked - prevIssued).toFixed(4))
  if (maxIssue <= 0) { ElMessage.warning('上道工序无可领半成品 (未入库或已被领完)'); return }
  Object.assign(semiForm, {
    taskId: row.id, stepNo: row.stepNo, stepName: row.stepName,
    planQty: Number(row.planQty || 0),
    prevStepNo: prev.stepNo, prevStepName: prev.stepName,
    prevStocked, prevIssued, maxIssue,
    qty: Math.min(maxIssue, Number(row.planQty || 0) || maxIssue),
    warehouseId: null
  })
  fillProductContext()
  semiIssueVisible.value = true
}

async function submitSemiStockIn() {
  if (!semiForm.qty || semiForm.qty <= 0) { ElMessage.warning('数量必须大于 0'); return }
  if (!semiForm.warehouseId) { ElMessage.warning('请选择入库仓库'); return }
  semiSubmitting.value = true
  try {
    await semiStockIn(semiForm.taskId, { qty: semiForm.qty, warehouseId: semiForm.warehouseId })
    ElMessage.success(`已提交入库任务 ${semiForm.qty} 件, 待仓管处理后正式入库`)
    semiStockInVisible.value = false
    loadTasks()
  } finally { semiSubmitting.value = false }
}

async function submitSemiRevoke() {
  if (!semiForm.qty || semiForm.qty <= 0) { ElMessage.warning('数量必须大于 0'); return }
  semiSubmitting.value = true
  try {
    await semiStockRevoke(semiForm.taskId, { qty: semiForm.qty })
    ElMessage.success(`撤销成功: ${semiForm.qty} 件`)
    semiRevokeVisible.value = false
    loadTasks()
  } finally { semiSubmitting.value = false }
}

async function submitSemiIssue() {
  if (!semiForm.qty || semiForm.qty <= 0) { ElMessage.warning('数量必须大于 0'); return }
  if (!semiForm.warehouseId) { ElMessage.warning('请选择出库仓库'); return }
  semiSubmitting.value = true
  try {
    await semiStockIssue(semiForm.taskId, { qty: semiForm.qty, warehouseId: semiForm.warehouseId })
    ElMessage.success(`已提交领料任务 ${semiForm.qty} 件, 待仓管处理后正式出库`)
    semiIssueVisible.value = false
    loadTasks()
  } finally { semiSubmitting.value = false }
}

// ==================== 工序图纸 ====================
const drawingSummary = ref({})
const woDrawingDialogVisible = ref(false)
const woDrawingTarget = ref({ productId: null, productName: '', stepNo: null, stepName: '', processTaskId: null, workOrderId: null })

async function loadDrawingSummary() {
  if (!taskList.value.length) {
    drawingSummary.value = {}
    return
  }
  const taskIds = taskList.value.map(t => t.id).filter(n => n != null)
  if (!taskIds.length) return
  try {
    const res = await getWoDrawingSummary(taskIds)
    drawingSummary.value = res.data || {}
  } catch {
    drawingSummary.value = {}
  }
}

function openWoDrawingDialog(row) {
  woDrawingTarget.value = {
    productId: detailData.value?.productId,
    productName: detailData.value?.productName,
    stepNo: row.stepNo,
    stepName: row.stepName,
    processTaskId: row.id,
    workOrderId: currentWoId.value
  }
  woDrawingDialogVisible.value = true
}

async function loadBom() {
  if (!currentWoId.value) return
  bomLoading.value = true
  try {
    const res = await getWorkOrderBom(currentWoId.value)
    bomList.value = res.data || []
  } catch {
    // Fallback to detail data if separate API not available
    bomList.value = detailData.value?.bomItems || []
  } finally {
    bomLoading.value = false
  }
}

async function loadHistory() {
  if (!currentWoId.value) return
  historyLoading.value = true
  try {
    const res = await getWorkOrderChangeHistory(currentWoId.value)
    historyList.value = res.data || []
  } catch {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

async function loadWoReports() {
  if (!currentWoId.value) return
  woReportLoading.value = true
  try {
    const res = await getWorkReportPage({ workOrderId: currentWoId.value, pageNum: 1, pageSize: 200 })
    woReportList.value = res.data?.records || res.data?.list || []
  } catch {
    woReportList.value = []
  } finally {
    woReportLoading.value = false
  }
}

async function onRejectWoReport(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入驳回原因', '驳回报工', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '原因(选填)',
      inputType: 'textarea'
    })
    await rejectWorkReport(row.id, reason || '')
    ElMessage.success('已驳回')
    await loadWoReports()
    // 报工驳回会回滚工序任务的报工数,刷新工单详情让 completedQty 同步显示
    if (currentWoId.value) {
      const res = await getWorkOrderDetail(currentWoId.value)
      detailData.value = res.data
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '驳回失败')
  }
}

// ==================== 库存任务 ====================
const invTasksList = ref([])
const invTasksLoading = ref(false)

async function loadInvTasks() {
  if (!currentWoId.value) return
  invTasksLoading.value = true
  try {
    const res = await getInvTasksByWorkOrder(currentWoId.value)
    invTasksList.value = res.data || []
  } catch {
    invTasksList.value = []
  } finally {
    invTasksLoading.value = false
  }
}

function invTypeLabel(type) {
  const map = { production_in: '成品入库', production_out: '生产领料', production_return: '生产退料' }
  return map[type] || type || '-'
}
function invTypeTagType(type) {
  const map = { production_in: 'success', production_out: 'warning', production_return: '' }
  return map[type] || 'info'
}
function invTaskStatusLabel(status) {
  const map = { pending: '待处理', partial: '部分处理', completed: '已完成', returned: '已退回' }
  return map[status] || status || '-'
}
function invTaskStatusType(status) {
  const map = { pending: 'warning', partial: '', completed: 'success', returned: 'danger' }
  return map[status] || 'info'
}

// ==================== 外协进度 ====================
const outsourceData = ref({})
const outsourceLoading = ref(false)
const hasOutsourceTasks = computed(() => {
  return taskList.value.some(t => t.isOutsourceable)
})

async function loadOutsourceStatus() {
  if (!currentWoId.value) return
  outsourceLoading.value = true
  try {
    const res = await getWorkOrderOutsourceStatus(currentWoId.value)
    outsourceData.value = res.data || {}
  } catch {
    outsourceData.value = {}
  } finally {
    outsourceLoading.value = false
  }
}

function outsourceStepActive(ot) {
  if (!ot.outsourceStatus) return 0
  if (ot.outsourceStatus === 'requested') return 1
  if (ot.outsourceStatus === 'ordered') return 2
  if (ot.outsourceStatus === 'completed' && ot.status !== 'completed') return 3
  if (ot.status === 'completed') return 5
  return 2
}

// ==================== 新增工单 (V4.0) ====================
const formVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const formData = reactive({
  productionMode: 'MTO',
  orderId: null,
  productId: null,
  productName: '',
  planQty: 1,
  planStartDate: '',
  planEndDate: '',
  remark: ''
})

// 产品选择弹窗
const woProductPickerVisible = ref(false)
function onWoProductSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  formData.productId = m.id
  formData.productName = m.materialName || m.name || ''
}

const formRules = {
  productionMode: [{ required: true, message: '请选择生产模式', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  planQty: [{ required: true, message: '请输入计划数量', trigger: 'blur' }],
  planStartDate: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  planEndDate: [{ required: true, message: '请选择计划完工日期', trigger: 'change' }]
}

// 关联订单搜索
const salesOrderOptions = ref([])
const salesOrderLoading = ref(false)

async function searchSalesOrders(query) {
  if (!query || query.length < 1) {
    salesOrderOptions.value = []
    return
  }
  salesOrderLoading.value = true
  try {
    const res = await getSalesOrderPage({ keyword: query, pageNum: 1, pageSize: 20 })
    salesOrderOptions.value = res.data?.records || res.data?.list || []
  } catch {
    salesOrderOptions.value = []
  } finally {
    salesOrderLoading.value = false
  }
}

function handleAdd() {
  formData.productionMode = 'MTO'
  formData.orderId = null
  formData.productId = null
  formData.productName = ''
  formData.planQty = 1
  formData.planStartDate = ''
  formData.planEndDate = ''
  formData.remark = ''
  salesOrderOptions.value = []
  formVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      productionMode: formData.productionMode,
      productId: formData.productId,
      planQty: formData.planQty,
      planStartDate: formData.planStartDate,
      planEndDate: formData.planEndDate,
      remark: formData.remark || undefined
    }
    // Only include orderId for MTO mode
    if (formData.productionMode === 'MTO' && formData.orderId) {
      payload.orderId = formData.orderId
    }
    const res = await createWorkOrder(payload)
    const r = res?.data || {}
    if (!r.hasBom && !r.hasRoute) {
      ElMessage.warning(`工单 ${r.woNo || ''} 创建成功，但所选产品未配置生效BOM和工艺路线，物料/工序均未生成`)
    } else if (!r.hasBom) {
      ElMessage.warning(`工单 ${r.woNo || ''} 已创建，工序已生成 ${r.stepCount} 步，但未找到生效BOM,物料未生成`)
    } else if (!r.hasRoute) {
      ElMessage.warning(`工单 ${r.woNo || ''} 已创建，BOM物料已生成 ${r.bomCount} 项，但未找到生效工艺路线,工序未生成`)
    } else {
      ElMessage.success(`工单 ${r.woNo || ''} 创建成功，已自动匹配 BOM ${r.bomCount} 项 / 工序 ${r.stepCount} 步`)
    }
    formVisible.value = false
    loadData()
    loadStatistics()
    // Refresh kanban if in kanban mode
    if (viewMode.value === 'kanban') {
      loadKanbanData()
    }
  } finally {
    submitLoading.value = false
  }
}

// ==================== 追加工单 ====================
const appendVisible = ref(false)
const appendLoading = ref(false)
const appendFormRef = ref(null)
const appendSourceWo = reactive({ woNo: '', productName: '', productSpec: '', planQty: 0, id: null })
const appendForm = reactive({ planQty: 0, remark: '' })
const appendSeqPreview = ref(1)
const appendRules = {
  planQty: [{ required: true, message: '请输入追加数量', trigger: 'blur' }]
}

function handleAppend(row) {
  appendSourceWo.id = row.id
  appendSourceWo.woNo = row.woNo
  appendSourceWo.productName = row.productName || ''
  appendSourceWo.productSpec = row.productSpec || ''
  appendSourceWo.planQty = row.planQty || 0
  appendForm.planQty = 0
  appendForm.remark = ''
  // 预估序号
  const baseNo = row.woNo.includes('追') ? row.woNo.substring(0, row.woNo.indexOf('追')) : row.woNo
  const existCount = tableData.value.filter(r => r.woNo && r.woNo.startsWith(baseNo + '追')).length
  appendSeqPreview.value = existCount + 1
  appendVisible.value = true
}

async function handleAppendSubmit() {
  await appendFormRef.value.validate()
  if (appendForm.planQty <= 0) {
    ElMessage.warning('追加数量必须大于0')
    return
  }
  appendLoading.value = true
  try {
    await appendWorkOrder(appendSourceWo.id, { planQty: appendForm.planQty })
    ElMessage.success('追加工单创建成功')
    appendVisible.value = false
    loadData()
    loadStatistics()
    if (viewMode.value === 'kanban') loadKanbanData()
  } finally {
    appendLoading.value = false
  }
}

// ==================== 成本明细 ====================
const woCost = ref(null)
const costLoading = ref(false)

async function loadCost() {
  if (!currentWoId.value) return
  costLoading.value = true
  try {
    const res = await getWoCost(currentWoId.value)
    woCost.value = res.data || null
  } catch {
    woCost.value = null
  } finally {
    costLoading.value = false
  }
}

// ==================== 开始生产 ====================
async function handleStartWo() {
  await ElMessageBox.confirm('确认开始生产该工单？', '提示', { type: 'info' })
  try {
    const { startWorkOrder } = await import('@/api/production')
    await startWorkOrder(currentWoId.value)
    ElMessage.success('工单已开始生产')
    const res = await getWorkOrderDetail(currentWoId.value)
    detailData.value = res.data
    loadData()
    loadStatistics()
  } catch {}
}

// ==================== 工单变更 ====================
const woChangeVisible = ref(false)
const woChangeStep = ref(0)
const woChangeLoading = ref(false)
const woChangeForm = reactive({
  changeType: '',
  processTaskId: null,
  reason: '',
  newPlanQty: 0,
  newDeliveryDate: '',
  // 工序转外协 — 一步建外协订单需要的字段
  outsourceSupplierId: null,
  outsourceQty: null,
  outsourceUnitPrice: null,
  outsourceTaxRate: 13,
  outsourceDeliveryDate: ''
})

const outsourceSupplierOptions = ref([])
async function loadOutsourceSuppliers() {
  if (outsourceSupplierOptions.value.length) return
  try {
    const { getSupplierSelect } = await import('@/api/master')
    const res = await getSupplierSelect()
    outsourceSupplierOptions.value = res.data || []
  } catch { outsourceSupplierOptions.value = [] }
}

// 选择工序后,默认把外协数量预填为该工序的计划数
function onOutsourceTaskChange(taskId) {
  const t = taskList.value.find(x => x.id === taskId)
  if (t && (woChangeForm.outsourceQty == null || woChangeForm.outsourceQty <= 0)) {
    woChangeForm.outsourceQty = Number(t.planQty || detailData.value?.planQty || 0)
  }
}

// 工单变更是否需要审批 — 由"系统设置 → 审批流 → 工单变更"开关控制
// 参数变更/责任人变更始终直接执行,与后端 BizWorkOrderService.createChange 一致
const woChangeApprovalEnabled = ref(false)
const woChangeNeedApproval = computed(() => {
  if (!woChangeApprovalEnabled.value) return false
  if (woChangeForm.changeType === 'modify_params' || woChangeForm.changeType === 'modify_person') return false
  return true
})

async function loadWoChangeApprovalSwitch() {
  try {
    const { getConfigByGroup } = await import('@/api/system')
    const res = await getConfigByGroup('approval')
    const list = res.data || []
    const cfg = list.find(c => c.configKey === 'approval_wo_change')
    if (!cfg || !cfg.configValue) { woChangeApprovalEnabled.value = false; return }
    const v = String(cfg.configValue).trim()
    if (v === 'true') { woChangeApprovalEnabled.value = true; return }
    if (v === 'false') { woChangeApprovalEnabled.value = false; return }
    try {
      const json = JSON.parse(v)
      woChangeApprovalEnabled.value = json.enabled === true
    } catch { woChangeApprovalEnabled.value = false }
  } catch { woChangeApprovalEnabled.value = false }
}

function woChangeTypeLabel(type) {
  const map = {
    outsource: '工序转外协', modify_qty: '修改计划数量', modify_date: '修改交货日期'
  }
  return map[type] || type
}

function openWoChange() {
  woChangeStep.value = 0
  woChangeForm.changeType = ''
  woChangeForm.processTaskId = null
  woChangeForm.reason = ''
  woChangeForm.newPlanQty = detailData.value?.planQty || 0
  woChangeForm.newDeliveryDate = detailData.value?.deliveryDate || ''
  woChangeForm.outsourceSupplierId = null
  woChangeForm.outsourceQty = null
  woChangeForm.outsourceUnitPrice = null
  woChangeForm.outsourceTaxRate = 13
  woChangeForm.outsourceDeliveryDate = detailData.value?.deliveryDate || ''
  woChangeVisible.value = true
  // 每次打开都拉一次审批开关,确保实时
  loadWoChangeApprovalSwitch()
  loadOutsourceSuppliers()

  if (taskList.value.length === 0) loadTasks()
}

function woChangeNext() {
  if (woChangeStep.value === 0 && !woChangeForm.changeType) {
    ElMessage.warning('请选择变更类型')
    return
  }
  woChangeStep.value++
}

async function submitWoChange() {
  // 外协场景前端必填校验
  if (woChangeForm.changeType === 'outsource') {
    if (!woChangeForm.processTaskId) { ElMessage.warning('请选择需要外协的工序'); return }
    if (!woChangeForm.outsourceSupplierId) { ElMessage.warning('请选择外协供应商'); return }
    if (!(woChangeForm.outsourceQty > 0)) { ElMessage.warning('加工数量必须大于 0'); return }
    if (woChangeForm.outsourceUnitPrice == null || woChangeForm.outsourceUnitPrice < 0) { ElMessage.warning('请填写含税单价'); return }
    if (!woChangeForm.outsourceDeliveryDate) { ElMessage.warning('请选择交付日期'); return }
  }
  woChangeLoading.value = true
  try {
    const payload = {
      workOrderId: currentWoId.value,
      changeType: woChangeForm.changeType,
      reason: woChangeForm.reason,
      processTaskId: woChangeForm.processTaskId,
      newPlanQty: woChangeForm.newPlanQty,
      newDeliveryDate: woChangeForm.newDeliveryDate,
      // 外协订单字段(仅 changeType=outsource 时使用)
      outsourceSupplierId: woChangeForm.outsourceSupplierId,
      outsourceQty: woChangeForm.outsourceQty,
      outsourceUnitPrice: woChangeForm.outsourceUnitPrice,
      outsourceTaxRate: woChangeForm.outsourceTaxRate,
      outsourceDeliveryDate: woChangeForm.outsourceDeliveryDate
    }
    const { default: request } = await import('@/utils/request')
    await request.post(`/production/work-orders/${currentWoId.value}/changes`, payload)
    ElMessage.success(woChangeNeedApproval.value ? '变更申请已提交，等待审批' : '变更已执行')
    woChangeVisible.value = false
    const res = await getWorkOrderDetail(currentWoId.value)
    detailData.value = res.data
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    woChangeLoading.value = false
  }
}

// ==================== 取消 ====================
async function handleCancel(row) {
  await ElMessageBox.confirm('确认取消该工单？取消后不可恢复。', '提示', { type: 'warning' })
  try {
    await cancelWorkOrder(row.id)
    ElMessage.success('工单已取消')
    loadData()
    loadStatistics()
  } catch {
    // error handled by interceptor
  }
}

// ==================== 销售订单详情弹窗 ====================
const orderDetailVisible = ref(false)
const orderDetailLoading = ref(false)
const orderDetailData = ref(null)
async function openOrderDetail(orderId) {
  orderDetailVisible.value = true
  orderDetailLoading.value = true
  orderDetailData.value = null
  try {
    const res = await getSalesOrderDetail(orderId)
    orderDetailData.value = res.data || null
  } catch {
    orderDetailData.value = null
  } finally {
    orderDetailLoading.value = false
  }
}

// ==================== 领料 ====================
// 班组成员 popover
const teamMembersList = ref([])
const teamMembersLoading = ref(false)
async function loadTeamMembers(teamId) {
  teamMembersLoading.value = true
  teamMembersList.value = []
  try {
    const res = await getTeamMembers(teamId)
    teamMembersList.value = res.data || []
  } catch {
    teamMembersList.value = []
  } finally {
    teamMembersLoading.value = false
  }
}

const issueVisible = ref(false)
const issueLoading = ref(false)
const issueSubmitting = ref(false)
const issueItems = ref([])
const issueSelectAll = ref(true)
const issueAllDone = ref(false)
const issueHasBom = ref(true)
const warehouseOptions = ref([])
const materialOptions = ref([])

// 物料 remote 搜索(领料弹窗用)
const materialSearching = ref(false)
let _matSearchTimer = null
async function searchManualMaterials(keyword) {
  if (_matSearchTimer) clearTimeout(_matSearchTimer)
  if (!keyword || !keyword.trim()) { materialOptions.value = []; return }
  _matSearchTimer = setTimeout(async () => {
    materialSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      materialOptions.value = (res.data?.records || []).map(m => ({ id: m.id, materialName: m.materialName, spec: m.spec, unit: m.unit, unitConfig: m.unitConfig, defaultWarehouseId: m.defaultWarehouseId }))
    } catch {} finally { materialSearching.value = false }
  }, 300)
}

// 按 BOM 比例自动填充其他物料的领料数量
function onIssueQtyChange(changedRow) {
  if (!issueHasBom.value) return
  const required = Number(changedRow.requiredQty || 0)
  if (required <= 0) return
  const ratio = Number(changedRow.issueQty || 0) / required
  if (!isFinite(ratio) || ratio <= 0) return
  issueItems.value.forEach(row => {
    if (row === changedRow) return
    const need = Number(row.requiredQty || 0)
    if (need <= 0) return
    // 按比例推算,保留 4 位小数精度后截到整数(避免累计误差),同时不超过 maxIssue
    const calc = Math.round(need * ratio * 10000) / 10000
    const max = Number(row.maxIssue ?? calc)
    row.issueQty = Math.min(calc, max)
  })
}

async function loadWarehouseOptions() {
  if (warehouseOptions.value.length > 0) return
  try {
    const { getWarehouseList } = await import('@/api/system')
    const res = await getWarehouseList()
    warehouseOptions.value = (res.data || []).map(w => ({ id: w.id, name: w.warehouseName || w.name }))
  } catch {
    warehouseOptions.value = []
  }
}

async function openIssueMaterial() {
  issueLoading.value = true
  issueVisible.value = true
  loadWarehouseOptions()
  // 物料下拉改 remote 按需搜索,不再预加载全量
  try {
    const res = await getIssueStatus(currentWoId.value)
    const data = res.data || {}
    issueAllDone.value = !!data.allIssued
    issueHasBom.value = data.hasBom !== false
    issueItems.value = (data.items || []).map(item => {
      const remaining = Math.max((item.requiredQty || 0) - (item.issuedQty || 0), 0)
      const locked = !!item.locked
      const lockedRemainingQty = item.lockedRemainingQty || 0
      const maxIssue = locked
        ? Math.min(remaining, lockedRemainingQty)
        : Math.min(remaining, item.availableQty ?? 0)

      // 主计划指定的优先替代料
      const prefSubId = item.preferredSubstituteId
      const prefSub = prefSubId ? (item.substitutes || []).find(s => s.substituteId === prefSubId) : null

      const substitutes = (item.substitutes || []).map(s => {
        const isPref = prefSub && s.substituteId === prefSubId
        const ratio = Number(s.ratio) || 1
        // 优先替代料：按需求量换算出替代料数量，上限为可用库存
        const prefQty = isPref ? Math.min(Number((remaining * ratio).toFixed(4)), s.availableQty ?? 0) : 0
        return {
          ...s,
          subIssueQty: isPref ? prefQty : 0,
          subWarehouseId: s.defaultWarehouseId || (warehouseOptions.value[0]?.id) || null
        }
      })
      // 若已有优先替代料，主料默认不领
      const usePref = prefSub != null
      return {
        ...item,
        substitutes,
        remaining,
        locked,
        lockedRemainingQty,
        maxIssue,
        issueQty: usePref ? 0 : (maxIssue > 0 ? maxIssue : 0),
        checked: usePref ? false : (maxIssue > 0),
        warehouseId: item.lockedWarehouseId || item.defaultWarehouseId || (warehouseOptions.value[0]?.id) || null,
        lockedWarehouseId: item.lockedWarehouseId || null,
        availableQty: item.availableQty ?? null
      }
    })
    issueSelectAll.value = issueItems.value.every(i => i.checked)

    // 后端按"锁库仓/默认仓"返回的 availableQty 与前端 row.warehouseId 落点(可能兜底为 warehouseOptions[0])
    // 不一定是同一个仓库,会出现"一进来显示的可用与所选仓库实际可用不一致"。
    // 加载完成后按每行实际选中的仓库再拉一次实时可用,确保初始显示就是正确数字。
    await Promise.all(issueItems.value.flatMap(row => {
      const tasks = []
      if (row.materialId && row.warehouseId) tasks.push(refreshIssueRowAvailable(row))
      if (Array.isArray(row.substitutes)) {
        row.substitutes.forEach(sub => {
          if (sub.materialId && sub.subWarehouseId) {
            tasks.push(refreshIssueRowAvailable(sub, true))
          }
        })
      }
      return tasks
    }))
  } catch {
    issueItems.value = []
  } finally {
    issueLoading.value = false
  }
}

/**
 * 切换仓库后刷新该行的实时可用库存
 * @param row 主料行 / 替代料行 / 手动行
 * @param isSubstitute 是否替代料行(替代料行字段名为 sub*)
 */
async function refreshIssueRowAvailable(row, isSubstitute = false) {
  const matId = row.materialId
  const whId = isSubstitute ? row.subWarehouseId : row.warehouseId
  if (!matId || !whId) return
  try {
    const res = await getAvailableQty(matId, whId)
    row.availableQty = res.data ?? 0
  } catch {
    // 接口失败不阻断,保留旧值
  }
}

// 打开物料选择弹窗
const issuePickerVisible = ref(false)
function addManualIssueRow() {
  issuePickerVisible.value = true
}

// 选完物料后批量加行 + 自动查可用库存
async function onIssueMaterialsPicked(rows) {
  const existingIds = new Set(issueItems.value.map(i => i.materialId).filter(Boolean))
  const newRows = []
  for (const m of rows) {
    if (existingIds.has(m.id)) continue  // 已有的不重复加
    const whId = m.defaultWarehouseId || warehouseOptions.value[0]?.id || null
    let availableQty = null
    if (whId) {
      try { const r = await getAvailableQty(m.id, whId); availableQty = r.data ?? 0 } catch {}
    }
    newRows.push({
      isManual: true,
      materialId: m.id,
      materialNo: m.materialNo || '',
      materialName: m.materialName || m.name || '',
      spec: m.spec || '',
      unit: m.unit || '',
      warehouseId: whId,
      issueQty: 1,
      availableQty,
      checked: true
    })
  }
  if (newRows.length === 0) {
    ElMessage.info('所选物料已全部在领料列表中')
    return
  }
  issueItems.value.push(...newRows)
  ElMessage.success(`已添加 ${newRows.length} 个物料`)
}

function removeIssueRow(index) {
  issueItems.value.splice(index, 1)
}

async function onManualMaterialChange(row) {
  if (!row.materialId) return
  const mat = materialOptions.value.find(m => m.id === row.materialId)
  if (mat) {
    row.materialNo = mat.materialNo || ''
    row.materialName = mat.materialName || mat.name || ''
    row.spec = mat.spec || ''
    row.unit = mat.unit || ''
    if (!row.warehouseId && mat.defaultWarehouseId) {
      row.warehouseId = mat.defaultWarehouseId
    }
  }
  // 物料/仓库就绪后查实时可用
  await refreshIssueRowAvailable(row)
}

function toggleIssueSelectAll(val) {
  issueItems.value.forEach(item => {
    if (item.remaining > 0) item.checked = val
  })
}

async function submitIssueMaterial() {
  const items = []
  issueItems.value.forEach(i => {
    // 主料行
    if ((i.checked || i.isManual || !issueHasBom.value) && i.issueQty > 0 && i.materialId && i.warehouseId) {
      items.push({ materialId: i.materialId, warehouseId: i.warehouseId, qty: i.issueQty })
    }
    // 替代料行（每个替代料独立提交，附带主料ID和比率）
    if (issueHasBom.value && Array.isArray(i.substitutes)) {
      i.substitutes.forEach(s => {
        if ((s.subIssueQty || 0) > 0 && s.materialId && s.subWarehouseId) {
          items.push({
            materialId: s.materialId,
            warehouseId: s.subWarehouseId,
            qty: s.subIssueQty,
            substituteForMaterialId: i.materialId,
            substituteRatio: s.ratio
          })
        }
      })
    }
  })
  if (items.length === 0) {
    // 调试:告诉用户具体哪个条件没满足
    const raw = issueItems.value[0]
    if (raw) {
      const reasons = []
      if (!raw.checked && !raw.isManual && issueHasBom.value) reasons.push('未勾选')
      if (!(raw.issueQty > 0)) reasons.push('数量≤0')
      if (!raw.materialId) reasons.push('未选物料')
      if (!raw.warehouseId) reasons.push('未选仓库')
      return ElMessage.warning('无法领料: ' + (reasons.length > 0 ? reasons.join('、') : '未知原因'))
    }
    return ElMessage.warning('请先添加物料')
  }
  issueSubmitting.value = true
  try {
    // 无 BOM 模式: 先做缺料预检, 缺料则弹出"缺料请购"对话框, 不直接领料.
    if (!issueHasBom.value) {
      const checkRes = await checkIssueShortage(currentWoId.value, items)
      const shortageList = checkRes.data || []
      if (shortageList.length > 0) {
        openShortageDialog(shortageList)
        return
      }
    }
    await issueMaterial(currentWoId.value, items)
    ElMessage.success('领料成功')
    issueVisible.value = false
    await loadBom()
    const statusRes = await getIssueStatus(currentWoId.value)
    issueAllDone.value = !!statusRes.data?.allIssued
  } catch {
    // handled by interceptor
  } finally {
    issueSubmitting.value = false
  }
}

// ==================== 无 BOM 缺料请购对话框 ====================
const shortageDialogVisible = ref(false)
const shortageDialogLoading = ref(false)
const shortageItems = ref([])
const shortageForm = reactive({
  expectedDate: '',
  purpose: ''
})

function openShortageDialog(list) {
  shortageItems.value = list
  // 默认需求日期 = 工单交期, 没有则今天 + 7 天
  const defaultDate = detailData.value?.deliveryDate
    ? String(detailData.value.deliveryDate).slice(0, 10)
    : new Date(Date.now() + 7 * 86400_000).toISOString().slice(0, 10)
  shortageForm.expectedDate = defaultDate
  // 默认用途: 生产 XX 产品 (工单 WOXX)
  const prodName = detailData.value?.productName || '相关产品'
  shortageForm.purpose = `生产${prodName} (工单 ${detailData.value?.woNo || ''})`
  shortageDialogVisible.value = true
}

async function submitShortageRequest() {
  if (!shortageForm.expectedDate) { ElMessage.warning('请选择需求日期'); return }
  if (!shortageForm.purpose?.trim()) { ElMessage.warning('请填写用途'); return }
  const valid = shortageItems.value.filter(s => !s.alreadyRequested)
  if (valid.length === 0) {
    ElMessage.warning('所选缺料均已存在未办结的采购申请, 无需再次提交')
    return
  }
  shortageDialogLoading.value = true
  try {
    const res = await generatePurchaseRequestsForShortage(currentWoId.value, {
      items: valid,
      expectedDate: shortageForm.expectedDate,
      purpose: shortageForm.purpose.trim()
    })
    ElMessage.success(`已生成 ${res.data || 0} 条采购申请, 请待采购到货后再领料`)
    shortageDialogVisible.value = false
    issueVisible.value = false
  } catch {} finally {
    shortageDialogLoading.value = false
  }
}


// ==================== 退料 ====================
const returnVisible = ref(false)
const returnLoading = ref(false)
const returnSubmitting = ref(false)
const returnItems = ref([])

async function openReturnMaterial() {
  if (returnVisible.value || returnLoading.value) return
  returnLoading.value = true
  returnVisible.value = true
  loadWarehouseOptions()
  try {
    const res = await getIssueStatus(currentWoId.value)
    const data = res.data || {}
    // 展示已领料的物料(issuedQty 或 issuedTaskQty > 0)
    returnItems.value = (data.items || [])
      .filter(item => (item.issuedQty || 0) > 0 || (item.issuedTaskQty || 0) > 0)
      .map(item => {
        const issued = Math.max(item.issuedQty || 0, item.issuedTaskQty || 0)
        const maxReturnQty = Math.max(
          issued - (item.returnedQty || 0), 0
        )
        return {
          ...item,
          maxReturnQty,
          returnQty: maxReturnQty > 0 ? Number(maxReturnQty) : 0,
          warehouseId: item.lockedWarehouseId || item.defaultWarehouseId || (warehouseOptions.value[0]?.id) || null
        }
      })
    if (returnItems.value.length === 0) {
      ElMessage.warning('暂无可退料的物料（需先完成领料）')
      returnVisible.value = false
    }
  } catch {
    returnItems.value = []
  } finally {
    returnLoading.value = false
  }
}

async function submitReturnMaterial() {
  const items = returnItems.value
    .filter(i => i.returnQty > 0 && i.materialId && i.warehouseId)
    .map(i => ({ materialId: i.materialId, warehouseId: i.warehouseId, qty: i.returnQty }))
  if (items.length === 0) return ElMessage.warning('请填写退料数量并选择仓库')
  returnSubmitting.value = true
  try {
    await returnMaterial(currentWoId.value, items)
    ElMessage.success('退料任务生成成功')
    returnVisible.value = false
    await loadBom()
    const res = await getIssueStatus(currentWoId.value)
    issueAllDone.value = !!res.data?.allIssued
  } catch {
    // handled by interceptor
  } finally {
    returnSubmitting.value = false
  }
}

// ==================== 成品入库 ====================
const stockInVisible = ref(false)
const stockInSubmitting = ref(false)
const stockInForm = reactive({ qty: null, warehouseId: null })

const stockInMaxQty = computed(() => {
  if (!detailData.value) return 0
  const completed = Number(detailData.value.completedQty) || 0
  const tasked = Number(detailData.value.stockInTaskQty) || 0
  return Math.max(0, completed - tasked)
})

function openStockInDialog() {
  stockInForm.qty = stockInMaxQty.value > 0 ? stockInMaxQty.value : null
  stockInForm.warehouseId = detailData.value?.productDefaultWarehouseId || null
  loadWarehouseOptions()
  stockInVisible.value = true
}

async function submitStockIn() {
  if (!stockInForm.qty || stockInForm.qty <= 0) {
    ElMessage.warning('请输入入库数量')
    return
  }
  stockInSubmitting.value = true
  try {
    await createWorkOrderStockInTask(detailData.value.id, {
      qty: stockInForm.qty,
      warehouseId: stockInForm.warehouseId || undefined
    })
    ElMessage.success('入库任务已创建，请前往库存任务处理')
    stockInVisible.value = false
    const res = await getWorkOrderDetail(detailData.value.id)
    detailData.value = res.data
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    stockInSubmitting.value = false
  }
}

// ==================== 工序报工 ====================
const reportVisible = ref(false)
const reportSubmitting = ref(false)
const reportTaskData = ref(null)
const reportMembers = ref([])
const reportLimitInfo = ref(null)
const reportMaxQty = ref(null)
const reportForm = reactive({
  qualifiedQty: 0,
  defectQty: 0,
  workHours: 0,
  remark: ''
})

const reportMembersQualifiedTotal = computed(() => {
  return Math.round(reportMembers.value.reduce((sum, m) => sum + (m.allocatedQty || 0), 0) * 100) / 100
})
const reportMembersDefectTotal = computed(() => {
  return Math.round(reportMembers.value.reduce((sum, m) => sum + (m.allocatedDefect || 0), 0) * 100) / 100
})

async function openReportTask(task) {
  reportTaskData.value = task
  reportForm.defectQty = 0
  reportForm.workHours = 0
  reportForm.remark = ''
  reportMembers.value = []
  reportLimitInfo.value = null
  reportMaxQty.value = null

  // 获取报工数量限制
  let maxFromLimit = null
  try {
    const limitRes = await getReportLimit(currentWoId.value, task.id)
    const limitData = limitRes.data || {}
    maxFromLimit = limitData.maxReportableQty != null ? Number(limitData.maxReportableQty) : null
    if (maxFromLimit != null) {
      reportLimitInfo.value = limitData
      reportMaxQty.value = maxFromLimit
    }
  } catch {
    // 限制查询失败，按剩余计划数
  }

  // 半成品流程: 拿"已领料数 - 已报工"作为更严的上限
  let maxFromSemi = null
  try {
    const semiRes = await semiStockCheckReport(task.id)
    if (semiRes.data?.maxReportable != null) {
      maxFromSemi = Number(semiRes.data.maxReportable)
      // 跟原 maxFromLimit 取小, 作为最终硬上限
      if (reportMaxQty.value == null || maxFromSemi < reportMaxQty.value) {
        reportMaxQty.value = maxFromSemi
      }
    }
  } catch { /* 没限制即无 */ }

  // 有质检模板时，已提交量记在 reportedQty（含待质检）；无质检时记在 qualifiedQty
  const pendingQcContinue = task.status === 'pending_qc' && task.qcTemplateId
  if (pendingQcContinue) {
    reportMaxQty.value = maxFromSemi != null ? maxFromSemi : null
  }
  const submittedQty = task.qcTemplateId ? (task.reportedQty || 0) : (task.qualifiedQty || 0)
  const planRemaining = Math.max((task.planQty || 0) - submittedQty, 0)
  let remaining = pendingQcContinue
    ? (reportMaxQty.value != null ? reportMaxQty.value : 0)
    : planRemaining
  if (!pendingQcContinue && maxFromLimit != null) remaining = Math.min(remaining, maxFromLimit)
  if (maxFromSemi != null) remaining = Math.min(remaining, maxFromSemi)
  reportForm.qualifiedQty = remaining
  reportVisible.value = true

  // 班组模式: 加载成员并按权重分配
  if (task.assignType === 'team' && task.assignedTeamId) {
    try {
      const res = await getTeamMembers(task.assignedTeamId)
      const members = (res.data || []).map(m => ({
        ...m,
        allocatedQty: 0,
        allocatedDefect: 0
      }))
      reportMembers.value = members
      distributeByWeight('qualified', remaining)
    } catch {
      reportMembers.value = []
    }
  }
}

/** 按权重分配数量到各成员，field: 'qualified' | 'defect' */
function distributeByWeight(field, totalQty) {
  const members = reportMembers.value
  const key = field === 'defect' ? 'allocatedDefect' : 'allocatedQty'
  if (!members.length || totalQty <= 0) {
    members.forEach(m => { m[key] = 0 })
    return
  }
  const totalWeight = members.reduce((sum, m) => sum + (m.weight || 0), 0)
  if (totalWeight <= 0) {
    const avg = Math.round(totalQty / members.length * 100) / 100
    members.forEach((m, i) => {
      m[key] = i < members.length - 1 ? avg : Math.round((totalQty - avg * (members.length - 1)) * 100) / 100
    })
    return
  }
  let allocated = 0
  members.forEach((m, i) => {
    if (i < members.length - 1) {
      m[key] = Math.round(totalQty * (m.weight || 0) / totalWeight * 100) / 100
      allocated += m[key]
    } else {
      m[key] = Math.round((totalQty - allocated) * 100) / 100
    }
  })
}

function onReportQtyChange(val) {
  if (reportTaskData.value?.assignType === 'team' && reportMembers.value.length > 0) {
    distributeByWeight('qualified', val || 0)
  }
}

function onReportDefectChange(val) {
  if (reportTaskData.value?.assignType === 'team' && reportMembers.value.length > 0) {
    distributeByWeight('defect', val || 0)
  }
}

async function submitReport() {
  if (reportForm.qualifiedQty <= 0 && reportForm.defectQty <= 0) {
    return ElMessage.warning('合格数量和不良数量不能都为0')
  }
  const task = reportTaskData.value
  const isTeam = task.assignType === 'team' && reportMembers.value.length > 0

  // 班组模式校验分配合计
  if (isTeam) {
    if (reportForm.qualifiedQty > 0 && reportMembersQualifiedTotal.value !== reportForm.qualifiedQty) {
      return ElMessage.warning('成员合格数合计（' + reportMembersQualifiedTotal.value + '）与总合格数（' + reportForm.qualifiedQty + '）不一致')
    }
    if (reportForm.defectQty > 0 && reportMembersDefectTotal.value !== reportForm.defectQty) {
      return ElMessage.warning('成员不良数合计（' + reportMembersDefectTotal.value + '）与总不良数（' + reportForm.defectQty + '）不一致')
    }
  }

  reportSubmitting.value = true
  try {
    const data = {
      qualifiedQty: reportForm.qualifiedQty,
      defectQty: reportForm.defectQty,
      workHours: reportForm.workHours || undefined,
      remark: reportForm.remark || undefined
    }
    if (isTeam) {
      const totalAllocated = reportMembers.value.reduce((s, m) => s + (m.allocatedQty || 0), 0)
      data.assignType = 'team'
      data.teamId = task.assignedTeamId
      data.members = reportMembers.value.map(m => ({
        userId: m.userId,
        weight: totalAllocated > 0 ? (m.allocatedQty || 0) / totalAllocated : m.weight
      }))
    }
    await reportTask(currentWoId.value, task.id, data)
    ElMessage.success('报工成功')
    reportVisible.value = false
    await loadTasks()
    const res = await getWorkOrderDetail(currentWoId.value)
    detailData.value = res.data
  } catch {
    // handled by interceptor
  } finally {
    reportSubmitting.value = false
  }
}

// ==================== 质检要求 ====================
const qcReqVisible = ref(false)
const qcReqLoading = ref(false)
const qcReqData = ref(null)

async function showQcRequirements(task) {
  qcReqVisible.value = true
  qcReqLoading.value = true
  qcReqData.value = null
  try {
    const res = await getQcRequirements(currentWoId.value, task.id)
    qcReqData.value = res.data || { hasQc: false }
  } catch {
    qcReqData.value = { hasQc: false }
  } finally {
    qcReqLoading.value = false
  }
}

// ==================== 质检记录 ====================
const qcRecordVisible = ref(false)
const qcRecordLoading = ref(false)
const qcRecordData = ref(null)

async function showQcRecords(task) {
  if (!task.qcTaskId) return
  qcRecordVisible.value = true
  qcRecordLoading.value = true
  qcRecordData.value = null
  try {
    const res = await getQcTaskDetail(task.qcTaskId)
    qcRecordData.value = res.data || null
  } catch {
    qcRecordData.value = null
  } finally {
    qcRecordLoading.value = false
  }
}

// ==================== 生产计划附件 ====================
const planAttachments = ref([])
const attachmentLoading = ref(false)

async function loadPlanAttachments() {
  if (!currentWoId.value) return
  attachmentLoading.value = true
  try {
    const res = await getPlanAttachments(currentWoId.value)
    planAttachments.value = res.data || []
  } catch {
    planAttachments.value = []
  } finally {
    attachmentLoading.value = false
  }
}

function formatFileSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function isPreviewable(fileType) {
  if (!fileType) return false
  return /^image\/|^application\/pdf/.test(fileType)
}

// 图片预览
const planAttachPreviewVisible = ref(false)
const planAttachPreviewUrls = ref([])
const planAttachPreviewIndex = ref(0)

function isImageFile(file) {
  if (file.fileType && /^image\//.test(file.fileType)) return true
  return isImage(file.originalName || file.fileUrl || '')
}

function previewOrDownload(file) {
  if (isImageFile(file)) {
    const imgs = planAttachments.value.filter(isImageFile).map(f => f.fileUrl)
    planAttachPreviewUrls.value = imgs
    planAttachPreviewIndex.value = Math.max(0, imgs.indexOf(file.fileUrl))
    planAttachPreviewVisible.value = true
  } else if (isPreviewable(file.fileType)) {
    // PDF 等其他可预览类型在新窗口打开
    window.open(file.fileUrl, '_blank')
  } else {
    downloadFile(file)
  }
}

function downloadFile(file) {
  const a = document.createElement('a')
  a.href = file.fileUrl
  a.download = file.originalName || 'download'
  a.click()
}

// ==================== 初始化 ====================
const route = useRoute()

onMounted(async () => {
  loadData()
  loadStatistics()
  await loadFieldConfigs()
  // 为详情页"扩展字段"汇总展示, 加载销售订单头/明细的字段配置
  orderExt.loadFieldConfigs()
  orderItemExt.loadFieldConfigs()
  loadColumnConfig(listFieldConfigs.value)
  // 支持从外部跳转并自动打开工单详情
  if (route.query.id) {
    handleView({ id: route.query.id })
  }
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}
.badge-mini {
  display: inline-block;
  color: #fff;
  font-size: 10px;
  line-height: 1;
  padding: 2px 4px;
  border-radius: 3px;
  margin-right: 4px;
  vertical-align: middle;
  font-weight: 600;
}
.badge-overdue { background: #f56c6c; }
.badge-postponed { background: #e6a23c; }
.stat-row {
  margin-bottom: 16px;
}
.search-card {
  margin-bottom: 16px;
}
.table-card {
  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}

/* 视图切换 */
.view-toggle {
  margin-bottom: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 看板视图 */
.kanban-container {
  display: flex;
  gap: 16px;
  min-height: 400px;
  overflow-x: auto;
  padding-bottom: 8px;
}
.kanban-column {
  flex: 1;
  min-width: 240px;
  background: #f5f7fa;
  border-radius: 8px;
  border-top: 3px solid #ddd;
  display: flex;
  flex-direction: column;
}
.kanban-column-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.kanban-column-body {
  padding: 8px 12px;
  flex: 1;
  overflow-y: auto;
  max-height: 520px;
}
.kanban-card {
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color 0.2s;

  :deep(.el-card__body) {
    padding: 12px;
  }
}
.kanban-card--overdue {
  border-left: 3px solid #F56C6C;
}
.kanban-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.kanban-card-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}
.kanban-overdue-tag {
  flex-shrink: 0;
}
.kanban-card-product {
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}
.kanban-card-countdown {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 11px;
  color: #909399;

  .text-danger {
    color: #F56C6C;
    font-weight: 600;
  }
}
.kanban-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.kanban-card-date {
  font-size: 11px;
  color: #909399;
}

/* 工艺路线区域 */
.route-section {
  margin-top: 20px;
}
.route-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.route-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  position: relative;
  padding-left: 10px;
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 2px;
    bottom: 2px;
    width: 3px;
    background: #409EFF;
    border-radius: 2px;
  }
}
.route-section-actions {
  display: flex;
  gap: 8px;
}

/* 详情Tabs */
.detail-tabs {
  margin-top: 24px;
}

/* 完成进度 */
.progress-section {
  margin-top: 24px;
  padding: 16px;
  background: #f9fafc;
  border-radius: 8px;
}
.progress-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

/* 变更历史 */
.history-card {
  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}
.history-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.history-operator {
  font-size: 13px;
  color: #909399;
}
.history-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.6;
}

/* 工单变更 */
.wo-change-radio {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 外协进度卡片 */
.outsource-task-card {
  padding: 16px;
  margin-bottom: 16px;
  background: #f9fafc;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}
.outsource-task-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.outsource-task-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.text-muted {
  color: #c0c4cc;
}
.drawing-badge {
  :deep(.el-badge__content) {
    font-size: 10px;
    padding: 0 4px;
    height: 16px;
    line-height: 16px;
    transform: translate(80%, -50%);
  }
}
</style>
