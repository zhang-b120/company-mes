<template>
  <div class="page-container">
    <!-- 统计概览卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="4">
        <div class="stat-card" @click="query.status = ''; loadPlans()">
          <div class="stat-value">{{ planStats.totalPlanCount || 0 }}</div>
          <div class="stat-label">计划总数</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card" @click="query.status = 'draft'; loadPlans()">
          <div class="stat-value">{{ planStats.draftCount || 0 }}</div>
          <div class="stat-label">草稿</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card" @click="query.status = 'scheduled'; loadPlans()">
          <div class="stat-value">{{ planStats.scheduledCount || 0 }}</div>
          <div class="stat-label">待派发</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card" @click="query.status = 'confirmed'; loadPlans()">
          <div class="stat-value">{{ planStats.confirmedCount || 0 }}</div>
          <div class="stat-label">待执行</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card highlight-warning" @click="query.status = 'in_progress'; loadPlans()">
          <div class="stat-value">{{ planStats.inProgressCount || 0 }}</div>
          <div class="stat-label">执行中</div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card highlight-success" @click="query.status = 'completed'; loadPlans()">
          <div class="stat-value">{{ planStats.completedCount || 0 }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never">
      <!-- 顶部工具栏 -->
      <div class="tab-toolbar">
        <el-input v-model="query.planNo" placeholder="计划编号" clearable style="width: 150px"
          @clear="loadPlans" @keyup.enter="loadPlans" />
        <el-input v-model="query.planName" placeholder="计划名称" clearable style="width: 150px; margin-left: 8px"
          @clear="loadPlans" @keyup.enter="loadPlans" />
        <el-input v-model="query.materialNo" placeholder="物料编号" clearable style="width: 150px; margin-left: 8px"
          @clear="loadPlans" @keyup.enter="loadPlans" />
        <el-input v-model="query.materialName" placeholder="物料名称" clearable style="width: 150px; margin-left: 8px"
          @clear="loadPlans" @keyup.enter="loadPlans" />
        <el-input v-model="query.spec" placeholder="规格型号" clearable style="width: 150px; margin-left: 8px"
          @keyup.enter="loadPlans" />
        <el-input v-model="query.orderNo" placeholder="销售订单号" clearable style="width: 160px; margin-left: 8px"
          @keyup.enter="loadPlans" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px; margin-left: 8px">
          <el-option label="草稿" value="draft" />
          <el-option label="待派发" value="scheduled" />
          <el-option label="已派发" value="confirmed" />
          <el-option label="执行中" value="in_progress" />
          <el-option label="已完成" value="completed" />
          <el-option label="已取消" value="cancelled" />
        </el-select>
        <template v-for="fc in searchFieldConfigs" :key="fc.fieldKey">
          <el-select v-if="fc.fieldType === 'select'" v-model="query.extFields[fc.fieldKey]" :placeholder="fc.fieldLabel" clearable style="width: 140px; margin-left: 8px">
            <el-option v-for="opt in fc.fieldOptions ? JSON.parse(fc.fieldOptions) : []" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-input v-else v-model="query.extFields[fc.fieldKey]" :placeholder="fc.fieldLabel" clearable style="width: 140px; margin-left: 8px" @keyup.enter="loadPlans" />
        </template>
        <el-button type="primary" style="margin-left: 8px" @click="loadPlans">查询</el-button>
        <el-button @click="query.planNo = ''; query.planName = ''; query.materialNo = ''; query.materialName = ''; query.spec = ''; query.orderNo = ''; query.status = ''; query.extFields = {}; loadPlans()">重置</el-button>
        <el-button type="primary" plain @click="handleCreate" style="margin-left: 12px">新建计划</el-button>
        <el-button :icon="Setting" @click="colConfigDialogRef.open()" style="margin-left: 8px">列配置</el-button>
        <template v-if="selectedPlans.length">
          <el-button type="success" style="margin-left: 12px"
            :disabled="!selectedPlans.some(r => r.status === 'scheduled')"
            @click="handleBatchDispatch">
            批量派工 ({{ selectedPlans.filter(r => r.status === 'scheduled').length }})
          </el-button>
          <el-button type="danger"
            :disabled="!selectedPlans.some(r => ['draft','scheduled'].includes(r.status))"
            @click="handleBatchCancel">
            批量取消 ({{ selectedPlans.filter(r => ['draft','scheduled'].includes(r.status)).length }})
          </el-button>
          <el-button type="primary" plain @click="handleBatchExport">
            批量导出 ({{ selectedPlans.length }})
          </el-button>
        </template>
      </div>

      <!-- 计划列表 -->
      <el-table :data="planData" v-loading="loading" border stripe @row-click="handleViewDetail"
        @selection-change="onPlanSelectionChange" ref="planTableRef">
        <el-table-column type="selection" width="45" :selectable="isPlanSelectable" />
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'orderRels'">
              <template v-if="row.orderRels && row.orderRels.length">
                <el-button v-for="rel in row.orderRels" :key="rel.orderId"
                  link type="primary" size="small" style="margin-right: 4px"
                  @click.stop="openOrderDetail(rel.orderId)">
                  {{ rel.orderNo }}
                </el-button>
              </template>
              <span v-else class="text-muted">-</span>
            </template>
            <template v-else-if="col.key === 'totalCompletedQty'">{{ row.totalCompletedQty || 0 }}</template>
            <template v-else-if="col.key === 'pendingQty'">{{ (row.totalPlanQty || 0) - (row.totalCompletedQty || 0) }}</template>
            <template v-else-if="col.key === 'createType'">
              <el-tag :type="row.createType === 'mrp' ? 'primary' : 'info'" size="small">
                {{ row.createType === 'mrp' ? 'MRP' : '手动' }}
              </el-tag>
            </template>
            <template v-else-if="col.key === 'progress'">
              <el-progress :percentage="row.totalPlanQty > 0 ? Math.round((row.totalCompletedQty || 0) / row.totalPlanQty * 100) : 0"
                :stroke-width="14" text-inside />
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="planStatusType(row.status)" size="small">{{ planStatusText(row.status) }}</el-tag>
            </template>
            <template v-else-if="col.type === 'extension'">{{ getExtFieldValue(row, col.fieldKey) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleViewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'draft'" type="warning" link size="small" @click.stop="handleStartScheduling(row)">开始排产</el-button>
            <el-button v-if="row.status === 'scheduled'" type="success" link size="small" @click.stop="handleConfirmAndStart(row)">派发</el-button>
            <el-button type="info" link size="small" @click.stop="handleExportPlan(row)">导出</el-button>
            <el-button v-if="['draft','scheduled'].includes(row.status)" type="danger" link size="small" @click.stop="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" background
          layout="total, sizes, prev, pager, next, jumper" @size-change="loadPlans" @current-change="loadPlans" />
      </div>
    </el-card>

    <!-- 计划详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailData?.planNo || '计划详情'" size="85%">
      <template v-if="detailData">
        <!-- 可编辑的计划头 -->
        <el-descriptions :column="3" border style="margin-bottom: 16px">
          <el-descriptions-item label="计划编号">{{ detailData.planNo }}</el-descriptions-item>
          <el-descriptions-item label="计划名称">
            <template v-if="['draft','scheduled'].includes(detailData.status)">
              <el-input v-model="detailData.planName" size="small" style="width: 200px" @change="handleUpdatePlan" />
            </template>
            <template v-else>{{ detailData.planName }}</template>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="planStatusType(detailData.status)" size="small">{{ planStatusText(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始日期">
            <template v-if="['draft','scheduled'].includes(detailData.status)">
              <el-date-picker v-model="detailData.planDateFrom" type="date" value-format="YYYY-MM-DD" size="small" @change="handleUpdatePlan" />
            </template>
            <template v-else>{{ detailData.planDateFrom }}</template>
          </el-descriptions-item>
          <el-descriptions-item label="结束日期">
            <template v-if="['draft','scheduled'].includes(detailData.status)">
              <el-date-picker v-model="detailData.planDateTo" type="date" value-format="YYYY-MM-DD" size="small" @change="handleUpdatePlan" />
            </template>
            <template v-else>{{ detailData.planDateTo }}</template>
          </el-descriptions-item>
          <el-descriptions-item label="创建方式">{{ detailData.createType === 'mrp' ? 'MRP生成' : '手动创建' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 订单扩展字段 -->
        <template v-if="detailData.extFields && fieldConfigs.length">
          <div style="font-weight:600;margin:0 0 8px;color:#303133;font-size:14px;">订单扩展字段</div>
          <el-descriptions :column="3" border style="margin-bottom:16px">
            <el-descriptions-item v-for="fc in fieldConfigs" :key="fc.fieldKey" :label="fc.fieldLabel">
              {{ getExtFieldValue(detailData, fc.fieldKey) }}
            </el-descriptions-item>
          </el-descriptions>
        </template>

        <!-- 附件管理 -->
        <div style="margin-bottom: 16px">
          <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px">
            <span style="font-weight: 600; font-size: 14px">附件 ({{ attachmentList.length }})</span>
            <el-upload :show-file-list="false" :before-upload="beforeAttachmentUpload" :http-request="handleAttachmentUpload"
              accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg,.dwg,.dxf,.zip,.rar">
              <el-button type="primary" size="small" :loading="attachmentUploading">上传附件</el-button>
            </el-upload>
          </div>
          <el-table v-if="attachmentList.length" :data="attachmentList" border size="small" style="width: 100%">
            <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.fileType || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100" align="right">
              <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="上传时间" width="170" />
            <el-table-column label="操作" width="170" align="center">
              <template #default="{ row }">
                <el-button v-if="isImage(row.originalName || row.fileUrl)" type="success" link size="small"
                  @click.stop="handlePreviewAttachment(row)">预览</el-button>
                <el-button type="primary" link size="small" @click.stop="handleDownloadAttachment(row)">下载</el-button>
                <el-button type="danger" link size="small" @click.stop="handleDeleteAttachment(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 图片预览 -->
          <el-image-viewer v-if="attachPreviewVisible" :url-list="attachPreviewUrls"
            :initial-index="attachPreviewIndex" @close="attachPreviewVisible = false" />
        </div>

        <!-- 行项表格 -->
        <el-table :data="detailData.items" border stripe
          @row-click="handleProcessConfig" highlight-current-row
          :row-class-name="({row}) => processConfigItem && processConfigItem.id === row.id ? 'current-config-row' : ''">
          <el-table-column prop="productNo" label="物料编号" min-width="120" show-overflow-tooltip />
          <el-table-column prop="productName" label="物料名称" min-width="140" />
          <el-table-column label="规格" min-width="100">
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column label="图纸/图片" width="90" align="center">
            <template #default="{ row }">
              <template v-if="(row.productImages && row.productImages.length) || (row.productAttachmentUrls && row.productAttachmentUrls.length)">
                <el-popover placement="right" trigger="hover" :width="320">
                  <template #reference>
                    <el-button link type="primary" size="small" @click.stop>
                      <el-icon><Picture /></el-icon>
                      查看
                    </el-button>
                  </template>
                  <div v-if="row.productImages && row.productImages.length" style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:8px;">
                    <el-image v-for="(img, idx) in row.productImages" :key="idx"
                      :src="img" :preview-src-list="row.productImages" :initial-index="idx"
                      fit="contain" style="width:90px;height:90px;border:1px solid #ebeef5;border-radius:4px;" />
                  </div>
                  <div v-if="row.productAttachmentUrls && row.productAttachmentUrls.length">
                    <div v-for="(att, idx) in row.productAttachmentUrls" :key="idx" style="margin-bottom:4px;">
                      <a :href="att.url || att" target="_blank" style="color:#409eff;font-size:12px;">
                        <el-icon style="vertical-align:middle;margin-right:2px;"><Document /></el-icon>
                        {{ att.name || ('附件' + (idx+1)) }}
                      </a>
                    </div>
                  </div>
                </el-popover>
              </template>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="关联订单" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="row.orderRels && row.orderRels.length">
                <el-tag v-for="rel in row.orderRels" :key="rel.orderId" size="small" style="margin-right: 4px; margin-bottom: 2px; cursor: pointer" @click.stop="openOrderDetail(rel.orderId)">
                  {{ rel.orderNo }} ×{{ rel.allocatedQty }}
                </el-tag>
              </template>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="planQty" label="计划数量" width="100" align="right" />
          <el-table-column prop="releasedQty" label="已下达" width="80" align="right" />
          <el-table-column label="未开始" width="80" align="right">
            <template #default="{ row }">
              <span :class="{ 'text-warning': (row.planQty - (row.releasedQty || 0)) > 0 }">
                {{ Math.max(0, row.planQty - (row.releasedQty || 0)) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="completedQty" label="已完工" width="80" align="right" />
          <el-table-column label="物料状态" width="100" align="center"
            v-if="detailData && detailData.status !== 'draft'">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="handleViewItemMaterials(row)">
                {{ row._materialStatus || '查看' }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="planStartDate" label="计划开工" width="110" />
          <el-table-column prop="planEndDate" label="计划完工" width="110" />
          <el-table-column label="生产周期" width="100" align="center">
            <template #default="{ row }">
              <span v-if="row._processSteps && row._processSteps.length">{{ calcCycleTime(row._processSteps) }}天</span>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="最晚开工" width="110">
            <template #default="{ row }">
              <span :class="{ 'text-danger': isOverdue(row) }">{{ calcLatestStartDate(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="['info', 'warning', 'danger'][row.priority] || 'info'" size="small">
                {{ ['低', '中', '高'][row.priority] || '低' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="itemStatusType(row.status)" size="small">{{ itemStatusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="['draft','scheduled'].includes(detailData.status)" type="primary" link size="small"
                @click.stop="handleEditItem(row)">编辑</el-button>
              <el-button v-if="['draft','scheduled'].includes(detailData.status)" type="danger" link size="small"
                @click.stop="handleDeleteItem(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 工序维护内联区域（表格下方） -->
        <div v-if="processConfigItem" class="inline-process-config">
          <div class="inline-process-config-header">
            <div style="display: flex; align-items: center; gap: 12px">
              <span class="config-section-title">工序维护</span>
              <el-tag size="small">{{ processConfigItem.productName }} {{ $fmtSpec(processConfigItem.spec) }}</el-tag>
            </div>
            <div style="display: flex; align-items: center; gap: 8px">
              <el-button type="primary" size="small" @click="saveProcessConfig" :loading="processConfigSaving">保存配置</el-button>
              <el-button size="small" @click="closeProcessConfig">关闭</el-button>
            </div>
          </div>

          <!-- 订单追溯 -->
          <div v-if="processConfigItem.orderRels && processConfigItem.orderRels.length" style="margin-bottom: 12px">
            <strong>订单追溯：</strong>
            <el-tag v-for="rel in processConfigItem.orderRels" :key="rel.orderId" style="margin-right: 6px; cursor: pointer" size="small" @click="openOrderDetail(rel.orderId)">
              {{ rel.orderNo }} × {{ rel.allocatedQty }}
            </el-tag>
            <!-- 显示扩展字段（取第一条有 extFields 的订单） -->
            <template v-if="fieldConfigs.length">
              <template v-for="rel in processConfigItem.orderRels" :key="'ext-' + rel.orderId">
                <div v-if="rel.extFields" style="margin-top:8px">
                  <el-descriptions :column="2" border size="small">
                    <el-descriptions-item v-for="fc in fieldConfigs" :key="fc.fieldKey" :label="fc.fieldLabel">
                      {{ getExtFieldValue(rel, fc.fieldKey) }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </template>
            </template>
          </div>

          <!-- 计划日期维护 -->
          <el-descriptions :column="4" border size="small" style="margin-bottom: 16px">
            <el-descriptions-item label="产品">{{ processConfigItem.productName }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ $fmtSpec(processConfigItem.spec) }}</el-descriptions-item>
            <el-descriptions-item label="计划开工">
              <el-date-picker v-model="processConfigItem.planStartDate" type="date" value-format="YYYY-MM-DD"
                size="small" style="width: 140px" placeholder="选择日期" />
            </el-descriptions-item>
            <el-descriptions-item label="计划完工">
              <el-date-picker v-model="processConfigItem.planEndDate" type="date" value-format="YYYY-MM-DD"
                size="small" style="width: 140px" placeholder="选择日期" />
            </el-descriptions-item>
          </el-descriptions>

          <!-- 产品图纸 -->
          <template v-if="(processConfigItem.productImages && processConfigItem.productImages.length) || (processConfigItem.productAttachmentUrls && processConfigItem.productAttachmentUrls.length)">
            <div style="font-weight:600;margin:12px 0 8px;color:#303133;font-size:13px;">产品图纸</div>
            <div v-if="processConfigItem.productImages && processConfigItem.productImages.length" style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px;">
              <el-image
                v-for="(img, idx) in processConfigItem.productImages" :key="idx"
                :src="img" :preview-src-list="processConfigItem.productImages"
                :initial-index="idx" fit="contain"
                style="width:100px;height:100px;border:1px solid #ebeef5;border-radius:4px;"
              />
            </div>
            <div v-if="processConfigItem.productAttachmentUrls && processConfigItem.productAttachmentUrls.length" style="margin-bottom:8px;">
              <div v-for="(att, idx) in processConfigItem.productAttachmentUrls" :key="idx" style="margin-bottom:4px;">
                <a :href="att.url || att" target="_blank" style="color:#409eff;text-decoration:none;font-size:13px;">
                  <el-icon style="vertical-align:middle;margin-right:4px;"><Document /></el-icon>
                  {{ att.name || ('附件' + (idx + 1)) }}
                </a>
              </div>
            </div>
          </template>

          <!-- 选择工艺路线 -->
          <div class="config-section">
            <div class="config-section-header">
              <span class="config-section-title">工艺路线</span>
              <el-select v-model="selectedRouteId" placeholder="选择工艺路线" size="small" style="width: 400px"
                @change="onRouteChange" :loading="processConfigLoading" filterable>
                <el-option v-for="r in availableRoutes" :key="r.id"
                  :label="`${r.routeName || r.versionNo} - ${r.productName || ''}${r.status === 'active' ? ' (激活)' : ''}`" :value="r.id" />
              </el-select>
            </div>
          </div>

          <!-- 工序流程 -->
          <div class="config-section">
            <div class="config-section-header">
              <div style="display: flex; align-items: center; gap: 12px">
                <span class="config-section-title">工序流程</span>
                <el-radio-group v-model="stepViewMode" size="small">
                  <el-radio-button value="table">表格视图</el-radio-button>
                  <el-radio-button value="flow">流程视图</el-radio-button>
                  <el-radio-button value="gantt">甘特图</el-radio-button>
                </el-radio-group>
              </div>
              <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
                <el-button size="small" @click="autoScheduleByWorkHours" :disabled="!processConfigItem.planEndDate">
                  自动排产
                </el-button>
                <el-button size="small" @click="distributeTimeEvenly" :disabled="!processConfigItem.planStartDate || !processConfigItem.planEndDate">
                  时间均分
                </el-button>
                <el-button size="small" @click="distributeTimeByWeight" :disabled="!processConfigItem.planStartDate || !processConfigItem.planEndDate">
                  按工时权重分配
                </el-button>
                <span style="color: #909399; font-size: 12px">
                  生产周期: {{ calcCycleTime(processConfigSteps) }} 天
                </span>
              </div>
            </div>

            <!-- 表格视图 -->
            <el-table v-if="stepViewMode === 'table'" :data="processConfigSteps" border size="small"
              v-loading="processConfigLoading"
              :span-method="planStepSpanMethod" :row-class-name="planStepRowClass">
              <el-table-column label="并行组" width="70" align="center">
                <template #default="{ $index }">
                  <el-tag v-if="isParallelStep($index)" type="warning" size="small" effect="plain">并行</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="stepNo" label="序号" width="55" align="center" />
              <el-table-column prop="stepName" label="工序名称" min-width="100" show-overflow-tooltip />
              <el-table-column prop="standardTime" label="工时(天)" width="75" align="right" />
              <el-table-column label="工资比例" width="100" align="center">
                <template #default="{ row }">
                  <el-input-number v-model="row._wageRatio" :min="0" :precision="2" :step="0.1" size="small"
                                   controls-position="right" style="width: 90px" />
                </template>
              </el-table-column>
              <el-table-column label="处理方式" width="95">
                <template #default="{ row }">
                  <el-select v-model="row._handleType" size="small" @change="onHandleTypeChange(row)">
                    <el-option label="自制" value="self" />
                    <el-option label="外协" value="outsource" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="指派方式" width="95">
                <template #default="{ row }">
                  <el-select v-if="row._handleType !== 'outsource'" v-model="row._assignType" size="small" @change="onAssignTypeChange(row)">
                    <el-option label="班组" value="team" />
                    <el-option label="个人" value="person" />
                  </el-select>
                  <span v-else style="color:#c0c4cc;font-size:12px">—</span>
                </template>
              </el-table-column>
              <el-table-column label="指派" min-width="260">
                <template #default="{ row }">
                  <!-- 外协: 选择供应商 -->
                  <el-select v-if="row._handleType === 'outsource'" v-model="row._supplierId"
                    filterable clearable placeholder="选择外协供应商" size="small" style="width: 100%">
                    <el-option v-for="s in supplierOptions" :key="s.id" :label="s.supplierName || s.name" :value="s.id" />
                  </el-select>
                  <!-- 自制 + 班组 -->
                  <template v-else-if="row._assignType === 'team'">
                    <el-select v-model="row._teamId" filterable clearable
                      placeholder="选择班组" size="small" style="width: 100%"
                      @change="onPlanTeamChange(row)">
                      <el-option v-for="t in teamOptions" :key="t.id" :label="t.teamName" :value="t.id" />
                    </el-select>
                    <el-select v-if="row._teamId" v-model="row._memberIds" multiple collapse-tags collapse-tags-tooltip
                      filterable placeholder="选择允许报工的成员（默认全部）" size="small" style="width: 100%; margin-top: 4px">
                      <el-option v-for="m in (planTeamMembersMap[row._teamId] || [])"
                        :key="m.userId" :label="m.userName" :value="m.userId" />
                    </el-select>
                  </template>
                  <!-- 自制 + 个人 -->
                  <el-select v-else v-model="row._assigneeId" filterable clearable
                    placeholder="选择人员" size="small" style="width: 100%">
                    <el-option v-for="u in assigneeOptions" :key="u.id" :label="u.realName" :value="u.id" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="计划开始" width="180">
                <template #default="{ row }">
                  <el-date-picker v-model="row._planStartTime" type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ss" size="small" style="width: 100%" placeholder="开始时间" />
                </template>
              </el-table-column>
              <el-table-column label="计划结束" width="180">
                <template #default="{ row }">
                  <el-date-picker v-model="row._planEndTime" type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ss" size="small" style="width: 100%" placeholder="结束时间" />
                </template>
              </el-table-column>
              <el-table-column label="质检" width="100" align="center">
                <template #default="{ row }">
                  <div style="display: flex; align-items: center; justify-content: center; gap: 4px">
                    <el-tag v-if="row._qcTemplateId || row.qcTemplateId" type="success" size="small">需质检</el-tag>
                    <span v-else style="color:#c0c4cc;font-size:12px">—</span>
                    <el-button v-if="row._qcTemplateId || row.qcTemplateId"
                      link type="primary" size="small" @click="toggleQcDetail(row)">
                      {{ row._showQcDetail ? '收起' : '维护' }}
                    </el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="图纸" width="90" align="center">
                <template #default="{ row }">
                  <el-tooltip v-if="!row._planItemStepId" content="请先点击「保存配置」后再上传图纸" placement="top">
                    <el-button link disabled size="small">
                      <el-icon style="margin-right:2px"><Document /></el-icon>管理
                    </el-button>
                  </el-tooltip>
                  <el-button v-else link type="primary" size="small" @click="openDrawingDialog(row)">
                    <el-icon style="margin-right:2px"><Document /></el-icon>管理
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 流程视图 -->
            <div v-else-if="stepViewMode === 'flow'" class="plan-flow-editor" v-loading="processConfigLoading">
              <div class="plan-flow-canvas">
                <VueFlow
                  :key="planFlowKey"
                  v-model:nodes="planFlowNodes"
                  v-model:edges="planFlowEdges"
                  :node-types="planNodeTypes"
                  :default-edge-options="planEdgeOptions"
                  :nodes-draggable="false"
                  :nodes-connectable="false"
                  :edges-updatable="false"
                  fit-view-on-init
                  @node-click="onPlanNodeClick"
                  @pane-click="selectedPlanStepIndex = -1"
                >
                  <Background :gap="15" />
                </VueFlow>
              </div>
              <div class="plan-flow-panel" v-if="selectedPlanStep">
                <h4>{{ selectedPlanStep.stepName }}</h4>
                <el-form label-width="80px" size="small">
                  <el-form-item label="工时">{{ selectedPlanStep.standardTime || 0 }} 天</el-form-item>
                  <el-form-item label="开始时间">
                    <el-date-picker v-model="selectedPlanStep._planStartTime" type="datetime"
                      value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" placeholder="开始时间" />
                  </el-form-item>
                  <el-form-item label="结束时间">
                    <el-date-picker v-model="selectedPlanStep._planEndTime" type="datetime"
                      value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" placeholder="结束时间" />
                  </el-form-item>
                  <el-form-item label="处理方式">
                    <el-select v-model="selectedPlanStep._handleType" style="width: 100%"
                      @change="onHandleTypeChange(selectedPlanStep)">
                      <el-option label="自制" value="self" />
                      <el-option label="外协" value="outsource" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="指派方式">
                    <el-select v-model="selectedPlanStep._assignType" style="width: 100%"
                      @change="onAssignTypeChange(selectedPlanStep)">
                      <el-option label="班组" value="team" />
                      <el-option label="个人" value="person" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="指派">
                    <el-select v-if="selectedPlanStep._assignType === 'team'" v-model="selectedPlanStep._teamId"
                      filterable clearable placeholder="选择班组" style="width: 100%">
                      <el-option v-for="t in teamOptions" :key="t.id" :label="t.teamName" :value="t.id" />
                    </el-select>
                    <el-select v-else v-model="selectedPlanStep._assigneeId"
                      filterable clearable placeholder="选择人员" style="width: 100%">
                      <el-option v-for="u in assigneeOptions" :key="u.id" :label="u.realName" :value="u.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="质检">
                    <el-tag v-if="selectedPlanStep._qcTemplateId || selectedPlanStep.qcTemplateId" type="success" size="small">需质检（由质检模板决定）</el-tag>
                    <span v-else style="color:#909399;font-size:13px">无需质检</span>
                  </el-form-item>
                  <el-form-item v-if="selectedPlanStep._qcTemplateId || selectedPlanStep.qcTemplateId">
                    <el-button link type="primary" @click="toggleQcDetail(selectedPlanStep)">
                      {{ selectedPlanStep._showQcDetail ? '收起质检要求' : '维护质检要求' }}
                    </el-button>
                  </el-form-item>
                </el-form>
              </div>
              <div class="plan-flow-panel plan-flow-panel-empty" v-else>
                <el-empty description="点击工序节点编辑" :image-size="60" />
              </div>
            </div>

            <!-- 甘特图视图 -->
            <div v-else-if="stepViewMode === 'gantt'" class="plan-gantt-wrapper" v-loading="processConfigLoading">
              <div class="plan-gantt-legend">
                <span class="legend-item"><span class="legend-dot" style="background:#409eff"></span>自制</span>
                <span class="legend-item"><span class="legend-dot" style="background:#e6a23c"></span>外协</span>
                <span class="legend-item"><span class="legend-dot" style="background:#909399"></span>未排期</span>
              </div>
              <div ref="stepGanttContainer" class="step-gantt-container"></div>
            </div>
          </div>

          <!-- 质检要求维护 -->
          <div v-if="currentQcStep" class="config-section qc-detail-section">
            <div class="config-section-header">
              <span class="config-section-title">
                质检要求维护 - {{ currentQcStep.stepName }}
                <el-tag size="small" style="margin-left: 8px">{{ currentQcStep._qcTemplateName || '质检模板' }}</el-tag>
              </span>
            </div>
            <el-table :data="currentQcStep._qcItems || []" border size="small" v-loading="qcDetailLoading">
              <el-table-column prop="itemName" label="质检项目" min-width="120" show-overflow-tooltip />
              <el-table-column prop="itemType" label="类型" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.itemType === 'quantitative' ? 'primary' : 'success'" size="small">
                    {{ row.itemType === 'quantitative' ? '定量' : '定性' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="单位" width="80">
                <template #default="{ row }">
                  <el-input v-model="row.unit" size="small" placeholder="单位" @blur="debouncedSaveProcessConfig" />
                </template>
              </el-table-column>
              <el-table-column label="标准值" width="120">
                <template #default="{ row }">
                  <el-input v-model="row._standardValue" size="small" placeholder="标准值" @blur="debouncedSaveProcessConfig" />
                </template>
              </el-table-column>
              <el-table-column label="下限值" width="110">
                <template #default="{ row }">
                  <el-input v-model="row._toleranceMin" size="small" placeholder="下限" @blur="debouncedSaveProcessConfig" />
                </template>
              </el-table-column>
              <el-table-column label="上限值" width="110">
                <template #default="{ row }">
                  <el-input v-model="row._toleranceMax" size="small" placeholder="上限" @blur="debouncedSaveProcessConfig" />
                </template>
              </el-table-column>
              <el-table-column label="质检要求" min-width="180">
                <template #default="{ row }">
                  <el-input v-model="row._requirement" size="small" placeholder="填写质检要求" @blur="debouncedSaveProcessConfig" />
                </template>
              </el-table-column>
              <el-table-column label="关键特性" width="75" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.isCritical" type="danger" size="small">关键</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </template>
    </el-drawer>

    <!-- 新建计划对话框 -->
    <el-dialog v-model="createVisible" title="新建车间计划" width="540px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="计划名称">
          <el-input v-model="createForm.planName" placeholder="如: 第10周车间计划" />
        </el-form-item>
        <el-form-item label="物料/产品" required>
          <div style="display: flex; gap: 8px; align-items: center; width: 100%">
            <el-input :model-value="createForm.productLabel" placeholder="必选 - 点击右侧选择物料" readonly style="flex: 1" />
            <el-button type="primary" @click="createMaterialPickerVisible = true">选择物料</el-button>
          </div>
        </el-form-item>
        <el-form-item label="计划数量" required>
          <el-input-number v-model="createForm.planQty" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option label="低" :value="0" />
            <el-option label="中" :value="1" />
            <el-option label="高" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="createForm.planDateFrom" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="createForm.planDateTo" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 通用物料选择器(新建计划: 单选) -->
    <MaterialPickerDialog v-model="createMaterialPickerVisible" :multiple="false" title="选择物料/产品"
      @select="onCreatePlanMaterialPicked" />

    <!-- 新增/编辑行项对话框 -->
    <el-dialog v-model="itemDialogVisible" :title="itemDialogMode === 'add' ? '新增行项' : '编辑行项'" width="500px">
      <el-form :model="itemForm" label-width="100px">
        <el-form-item label="产品" v-if="itemDialogMode === 'add'">
          <el-select v-model="itemForm.productId" filterable remote
                     :remote-method="searchProducts" :loading="productSearching"
                     placeholder="选择产品" style="width: 100%">
            <el-option v-for="p in productOptions" :key="p.id" :label="`${p.productName} ${$fmtSpec(p.spec)}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品" v-else>{{ itemForm._productName }}</el-form-item>
        <el-form-item label="计划数量">
          <el-input-number v-model="itemForm.planQty" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="itemForm.priority" style="width: 100%">
            <el-option label="低" :value="0" />
            <el-option label="中" :value="1" />
            <el-option label="高" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开工">
          <el-date-picker v-model="itemForm.planStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划完工">
          <el-date-picker v-model="itemForm.planEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitItemForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 开始任务对话框（增强：工序指派） -->
    <el-dialog v-model="startVisible" title="开始生产任务" width="750px">
      <el-form label-width="100px">
        <el-form-item label="产品">{{ startItem?.productName }}</el-form-item>
        <el-form-item label="规格">{{ $fmtSpec(startItem?.spec) }}</el-form-item>
        <el-form-item label="可生产数量">{{ startItem ? Math.max(0, startItem.planQty - (startItem.releasedQty || 0)) : 0 }}</el-form-item>
        <el-form-item label="本次生产数量">
          <el-input-number v-model="startQty" :min="1"
            :max="startItem ? Math.max(1, startItem.planQty - (startItem.releasedQty || 0)) : 1" />
        </el-form-item>
      </el-form>
      <!-- 工序指派 -->
      <div v-if="startProcessSteps.length" style="margin-top: 12px">
        <div style="font-weight: 600; margin-bottom: 8px">工序指派（生产周期: {{ calcCycleTime(startProcessSteps) }} 天）</div>
        <el-table :data="startProcessSteps" border size="small">
          <el-table-column prop="stepNo" label="序号" width="60" align="center" />
          <el-table-column prop="stepName" label="工序" min-width="100" />
          <el-table-column prop="standardTime" label="工时(天)" width="80" align="right" />
          <el-table-column label="指派类型" width="95">
            <template #default="{ row }">
              <el-select v-model="row._assignType" size="small" @change="onAssignTypeChange(row)">
                <el-option label="班组" value="team" />
                <el-option label="个人" value="person" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="指派" min-width="150">
            <template #default="{ row }">
              <el-select v-if="row._assignType === 'team'" v-model="row._teamId" filterable clearable placeholder="选择班组" size="small" style="width: 100%">
                <el-option v-for="t in teamOptions" :key="t.id" :label="t.teamName" :value="t.id" />
              </el-select>
              <el-select v-else v-model="row._assigneeId" filterable clearable placeholder="选择人员" size="small" style="width: 100%">
                <el-option v-for="u in assigneeOptions" :key="u.id" :label="u.realName" :value="u.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="质检" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row._qcTemplateId || row.qcTemplateId" type="success" size="small">需质检</el-tag>
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-alert type="info" :closable="false" style="margin-top: 12px">
        <template #title>开始任务后将自动生成生产工单，包括BOM快照和工序任务。</template>
      </el-alert>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="startSubmitting" @click="submitStartTask">确认开始</el-button>
      </template>
    </el-dialog>

    <!-- 物料齐套检查对话框 -->
    <el-dialog v-model="materialCheckVisible" title="物料齐套检查" width="700px">
      <el-table :data="materialCheckData" v-loading="materialCheckLoading" border stripe>
        <el-table-column prop="materialName" label="物料名称" min-width="140" />
        <el-table-column label="规格" min-width="100">
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="requiredQty" label="需求量" width="100" align="right" />
        <el-table-column prop="availableQty" label="可用库存" width="100" align="right" />
        <el-table-column prop="shortage" label="缺口" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.shortage > 0 }">
              {{ row.shortage > 0 ? row.shortage : '充足' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.shortage > 0 ? 'danger' : 'success'" size="small">
              {{ row.shortage > 0 ? '缺料' : '齐套' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="materialCheckData.length" style="margin-top: 12px; display: flex; justify-content: space-between; align-items: center">
        <div>
          <el-tag v-if="materialCheckData.every(r => r.shortage <= 0)" type="success" size="large">全部物料齐套，可以开始生产</el-tag>
          <el-tag v-else type="danger" size="large">部分物料不足，建议先完成采购</el-tag>
        </div>
        <el-button v-if="materialCheckData.some(r => r.shortage > 0)" type="primary" @click="goToMrp">
          前往MRP采购需求
        </el-button>
      </div>
    </el-dialog>

    <!-- 行项物料锁定明细对话框 -->
    <el-dialog v-model="itemMaterialVisible" :title="`物料锁定明细 - ${itemMaterialTitle}`" width="750px">
      <el-table :data="itemMaterialData" v-loading="itemMaterialLoading" border stripe size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialNo" label="物料编号" min-width="120" show-overflow-tooltip />
        <el-table-column label="规格" min-width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="requiredQty" label="需求量" width="90" align="right" />
        <el-table-column prop="lockedQty" label="已锁定" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-success': row.lockedQty > 0 }">{{ row.lockedQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="shortageQty" label="缺料" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.shortageQty > 0 }">{{ row.shortageQty > 0 ? row.shortageQty : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="releasedToWoQty" label="已转工单" width="90" align="right" />
        <el-table-column prop="availableQty" label="当前可用" width="90" align="right" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.shortageQty > 0 ? 'danger' : 'success'" size="small">
              {{ row.shortageQty > 0 ? '缺料' : '已锁' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="itemMaterialData.length" style="margin-top: 12px">
        <el-tag v-if="itemMaterialData.every(r => r.shortageQty <= 0)" type="success">全部物料已锁定</el-tag>
        <el-tag v-else type="warning">部分物料锁定不足，库存不足部分需采购补充</el-tag>
      </div>
    </el-dialog>

    <!-- 转外协申请对话框 -->
    <el-dialog v-model="outsourceVisible" title="转外协申请" width="550px">
      <el-form v-if="outsourceForm" :model="outsourceForm" label-width="100px">
        <el-form-item label="产品">{{ outsourceForm._productName }}</el-form-item>
        <el-form-item label="规格">{{ outsourceForm._spec || '-' }}</el-form-item>
        <el-form-item label="外协工序">
          <el-select v-model="outsourceForm.stepName" placeholder="选择工序" style="width: 100%">
            <el-option v-for="s in outsourceForm._steps" :key="s.stepNo" :label="s.stepName" :value="s.stepName" />
          </el-select>
        </el-form-item>
        <el-form-item label="外协数量">
          <el-input-number v-model="outsourceForm.quantity" :min="1" style="width: 200px" />
        </el-form-item>
        <el-form-item label="需求日期">
          <el-date-picker v-model="outsourceForm.requiredDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="工艺要求">
          <el-input v-model="outsourceForm.processRequirement" type="textarea" :rows="2" placeholder="工艺要求说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outsourceVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOutsourceRequest">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 工时权重分配弹窗 -->
    <el-dialog v-model="weightDialogVisible" title="按工时权重分配" width="500px" :close-on-click-modal="false">
      <el-table :data="weightList" border size="small" style="width: 100%">
        <el-table-column prop="stepNo" label="序号" width="55" align="center" />
        <el-table-column prop="stepName" label="工序名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="权重" width="100" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row._weight" :min="0.1" :max="100" :step="0.1" :precision="1"
              size="small" controls-position="right" style="width: 85px" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="weightDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applyWeightDistribution">确定</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情弹窗 -->
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

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="PLANNING_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 工序图纸 -->
    <ProcessDrawingDialog
      v-model="drawingDialogVisible"
      mode="plan"
      :title="`工序图纸 - ${drawingTarget.stepName || ''}`"
      :product-id="drawingTarget.productId"
      :product-name="drawingTarget.productName"
      :step-no="drawingTarget.stepNo"
      :step-name="drawingTarget.stepName"
      :plan-item-step-id="drawingTarget.planItemStepId"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, markRaw, provide, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Document, Picture } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import { useExtFields } from '@/composables/useExtFields'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { isImage } from '@/utils/attachment'
import {
  getProductionPlanPage, getProductionPlanDetail, createProductionPlan, updateProductionPlan,
  confirmProductionPlan, confirmAndStartPlan, cancelProductionPlan, addPlanItem, updatePlanItem, deletePlanItem,
  releaseWorkOrder, checkPlanMaterial, getPlanItemMaterials,
  getPlanStatistics, exportProductionPlan, saveItemStepConfigs,exportProductionPlansBatch, getItemStepConfigs,
  getPlanAttachments, uploadPlanAttachment, deletePlanAttachment,
  batchConfirmAndStartPlans, batchCancelPlans
} from '@/api/mrp'
import { getSupplierSelect, getMaterialPage } from '@/api/master'
import { getProcessRoutes, getProcessRouteDetail } from '@/api/bom'
import { getTeamList, getTeamMembers } from '@/api/team'
import { getAssigneeOptions, assignTask, getWorkOrderTasks } from '@/api/production'
import { createOutsourceRequest } from '@/api/outsource'
import { getQcTemplateDetail } from '@/api/quality'
import { getSalesOrderDetail } from '@/api/order'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import dagre from 'dagre'
import { gantt } from 'dhtmlx-gantt'
import 'dhtmlx-gantt/codebase/dhtmlxgantt.css'
import PlanStepNode from './PlanStepNode.vue'
import ProcessDrawingDialog from '../components/ProcessDrawingDialog.vue'

const router = useRouter()

// ==================== 列配置 ====================
const PLANNING_DEFAULT_COLUMNS = [
  { key: 'planNo', label: '计划编号', minWidth: 180 },
  { key: 'planName', label: '计划名称', minWidth: 180 },
  { key: 'orderRels', label: '销售订单号', minWidth: 180 },
  { key: 'planDateFrom', label: '开始日期', minWidth: 120 },
  { key: 'planDateTo', label: '结束日期', minWidth: 120 },
  { key: 'productNos', label: '物料编号', minWidth: 150 },
  { key: 'productNames', label: '物料名称', minWidth: 200 },
  { key: 'totalPlanQty', label: '生产数量', minWidth: 110, align: 'right' },
  { key: 'totalCompletedQty', label: '完成数量', minWidth: 110, align: 'right' },
  { key: 'pendingQty', label: '待生产数量', minWidth: 110, align: 'right' },
  { key: 'createType', label: '创建方式', minWidth: 100, align: 'center' },
  { key: 'progress', label: '完成进度', minWidth: 160 },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('production_planning', PLANNING_DEFAULT_COLUMNS)
const { fieldConfigs, listFieldConfigs, searchFieldConfigs, loadFieldConfigs, getExtFieldValue } = useExtFields('production_plan')

// ==================== 统计 ====================
const planStats = ref({})
async function loadStats() {
  try {
    const { data } = await getPlanStatistics()
    planStats.value = data
  } catch { /* ignore */ }
}

// ==================== 计划列表 ====================
const planData = ref([])
const loading = ref(false)
const total = ref(0)
const query = ref({ planNo: '', planName: '', materialNo: '', materialName: '', spec: '', orderNo: '', status: '', extFields: {}, pageNum: 1, pageSize: 20 })

async function loadPlans() {
  loading.value = true
  try {
    const params = { ...query.value }
    delete params.extFields
    if (query.value.extFields) {
      Object.entries(query.value.extFields).forEach(([k, v]) => {
        if (v !== null && v !== undefined && v !== '') params[`ext_${k}`] = v
      })
    }
    const { data } = await getProductionPlanPage(params)
    planData.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

// ==================== 批量操作 ====================
const selectedPlans = ref([])
const planTableRef = ref(null)

function onPlanSelectionChange(rows) {
  selectedPlans.value = rows
}

// 已取消的计划不可勾选; 已完成可勾选(用于批量导出)
function isPlanSelectable(row) {
  return row.status !== 'cancelled'
}

async function handleBatchDispatch() {
  const canDispatch = selectedPlans.value.filter(r => r.status === 'scheduled')
  const cannotDispatch = selectedPlans.value.filter(r => r.status !== 'scheduled')
  if (!canDispatch.length) {
    ElMessage.warning('没有可派工的计划，仅"待派发"状态的计划可以派工')
    return
  }
  let msg = `确认批量派工 ${canDispatch.length} 个计划？`
  if (cannotDispatch.length) {
    msg += `\n\n（另有 ${cannotDispatch.length} 个计划状态不满足派工条件，将自动跳过）`
  }
  await ElMessageBox.confirm(msg, '批量派工确认')
  loading.value = true
  try {
    const { data } = await batchConfirmAndStartPlans(canDispatch.map(r => r.id))
    let resultMsg = `派工成功 ${data.successCount} 个计划，共创建 ${data.totalWorkOrderCount} 个工单`
    if (data.failCount > 0) {
      resultMsg += `\n失败 ${data.failCount} 个：\n${data.failMessages.join('\n')}`
      ElMessageBox.alert(resultMsg, '批量派工结果', { type: 'warning', confirmButtonText: '知道了' })
    } else {
      ElMessage.success(resultMsg)
    }
    loadPlans()
    loadStats()
  } finally { loading.value = false }
}

async function handleBatchCancel() {
  const canCancel = selectedPlans.value.filter(r => ['draft', 'scheduled'].includes(r.status))
  const cannotCancel = selectedPlans.value.filter(r => !['draft', 'scheduled'].includes(r.status))
  if (!canCancel.length) {
    ElMessage.warning('没有可取消的计划，仅"草稿"或"待派发"状态的计划可以取消')
    return
  }
  let msg = `确认批量取消 ${canCancel.length} 个计划？`
  if (cannotCancel.length) {
    msg += `\n\n（另有 ${cannotCancel.length} 个计划状态不满足取消条件，将自动跳过）`
  }
  await ElMessageBox.confirm(msg, '批量取消确认', { type: 'warning' })
  loading.value = true
  try {
    const { data } = await batchCancelPlans(canCancel.map(r => r.id))
    let resultMsg = `成功取消 ${data.successCount} 个计划`
    if (data.failCount > 0) {
      resultMsg += `\n失败 ${data.failCount} 个：\n${data.failMessages.join('\n')}`
      ElMessageBox.alert(resultMsg, '批量取消结果', { type: 'warning', confirmButtonText: '知道了' })
    } else {
      ElMessage.success(resultMsg)
    }
    loadPlans()
    loadStats()
  } finally { loading.value = false }
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailData = ref(null)

// ==================== 附件管理 ====================
const attachmentList = ref([])
const attachmentUploading = ref(false)

async function loadAttachments(planId) {
  try {
    const { data } = await getPlanAttachments(planId)
    attachmentList.value = data || []
  } catch { attachmentList.value = [] }
}

function beforeAttachmentUpload(file) {
  const maxSize = 50 * 1024 * 1024 // 50MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过50MB')
    return false
  }
  return true
}

async function handleAttachmentUpload({ file }) {
  if (!detailData.value?.id) return
  attachmentUploading.value = true
  try {
    await uploadPlanAttachment(detailData.value.id, file)
    ElMessage.success('上传成功')
    await loadAttachments(detailData.value.id)
  } catch { /* error handled by interceptor */ }
  finally { attachmentUploading.value = false }
}

async function handleDeleteAttachment(row) {
  try {
    await ElMessageBox.confirm('确定删除该附件？', '提示', { type: 'warning' })
    await deletePlanAttachment(row.id)
    ElMessage.success('删除成功')
    await loadAttachments(detailData.value.id)
  } catch { /* cancelled or error */ }
}

function handleDownloadAttachment(row) {
  const link = document.createElement('a')
  link.href = row.fileUrl
  link.download = row.originalName
  link.target = '_blank'
  link.click()
}

// 图片预览
const attachPreviewVisible = ref(false)
const attachPreviewUrls = ref([])
const attachPreviewIndex = ref(0)
function handlePreviewAttachment(row) {
  const imgs = attachmentList.value
    .filter(a => isImage(a.originalName || a.fileUrl))
    .map(a => a.fileUrl)
  attachPreviewUrls.value = imgs
  attachPreviewIndex.value = Math.max(0, imgs.indexOf(row.fileUrl))
  attachPreviewVisible.value = true
}

function formatFileSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}

async function handleViewDetail(row) {
  closeProcessConfig()
  attachmentList.value = []
  const { data } = await getProductionPlanDetail(row.id)
  detailData.value = data
  detailVisible.value = true
  loadItemProcessRoutes(data.items)
  loadAttachments(row.id)
}

// 开始排产：打开详情并自动展开第一个行项的工序配置
async function handleStartScheduling(row) {
  closeProcessConfig()
  attachmentList.value = []
  const { data } = await getProductionPlanDetail(row.id)
  detailData.value = data
  detailVisible.value = true
  loadItemProcessRoutes(data.items)
  loadAttachments(row.id)
  // 等待DOM更新后自动打开第一个行项的排产配置
  if (data.items && data.items.length > 0) {
    setTimeout(() => handleProcessConfig(data.items[0]), 300)
  }
}

// 异步加载每个行项的工序路线，合并后端已保存的指派配置
async function loadItemProcessRoutes(items) {
  if (!items) return
  for (const item of items) {
    // 将后端返回的 stepConfigs 建成 map { stepId: config }
    const savedConfigs = {}
    if (item.stepConfigs && item.stepConfigs.length) {
      item.stepConfigs.forEach(sc => { savedConfigs[sc.stepId] = sc })
    }
    // 优先使用已保存的路线ID
    const savedRouteId = item.stepConfigs?.[0]?.routeId || null
    try {
      const { data: routes } = await getProcessRoutes(item.productId)
      const targetRoute = savedRouteId
        ? routes?.find(r => r.id === savedRouteId)
        : routes?.find(r => r.status === 'active')
      if (targetRoute) {
        const { data: detail } = await getProcessRouteDetail(targetRoute.id)
        item._processSteps = (detail.steps || []).map(s => {
          const saved = savedConfigs[s.id] || {}
          return {
            ...s,
            _teamId: saved.teamId || s.teamId || null,
            _assigneeId: saved.assigneeId || null,
            _assignType: saved.assigneeId ? 'person' : 'team',
            _needQc: saved.needQc || false,
            _teamName: saved.teamName || s.teamName || '',
            _assigneeName: saved.assigneeName || '',
            _handleType: saved.handleType || (s.isOutsourceable === 1 ? 'outsource' : 'self'),
            _supplierId: saved.supplierId || null,
            _supplierName: saved.supplierName || '',
            _wageRatio: saved.wageRatio != null ? Number(saved.wageRatio) : 1,
            _memberIds: parseMemberIds(saved.assignedMemberIds)
          }
        })
      } else {
        item._processSteps = []
      }
    } catch {
      item._processSteps = []
    }
  }
}

// ==================== 更新计划头 ====================
async function handleUpdatePlan() {
  if (!detailData.value) return
  try {
    await updateProductionPlan(detailData.value.id, {
      planName: detailData.value.planName,
      planDateFrom: detailData.value.planDateFrom,
      planDateTo: detailData.value.planDateTo,
      remark: detailData.value.remark
    })
    ElMessage.success('已保存')
    loadPlans()
  } catch { /* ignore */ }
}

// ==================== 新建计划 ====================
const createVisible = ref(false)
const createMaterialPickerVisible = ref(false)
const createForm = ref({ planName: '', productId: null, productLabel: '', planQty: 1, priority: 1, planDateFrom: '', planDateTo: '', remark: '' })

function handleCreate() {
  createForm.value = { planName: '', productId: null, productLabel: '', planQty: 1, priority: 1, planDateFrom: '', planDateTo: '', remark: '' }
  productOptions.value = []
  createVisible.value = true
}

function onCreatePlanMaterialPicked(rows) {
  const m = Array.isArray(rows) ? rows[0] : rows
  if (!m) return
  createForm.value.productId = m.id
  const specText = typeof m.spec === 'string' && m.spec.startsWith('[') ? '' : (m.spec || '')
  createForm.value.productLabel = `${m.materialNo || ''} ${m.materialName || ''} ${specText}`.trim()
}

async function submitCreate() {
  const f = createForm.value
  if (!f.productId) return ElMessage.warning('请选择物料/产品')
  if (!f.planQty || f.planQty < 1) return ElMessage.warning('请输入计划数量')
  const payload = {
    planName: f.planName,
    planDateFrom: f.planDateFrom || undefined,
    planDateTo: f.planDateTo || undefined,
    remark: f.remark,
    items: [{
      productId: f.productId,
      planQty: f.planQty,
      priority: f.priority ?? 1,
      planStartDate: f.planDateFrom || undefined,
      planEndDate: f.planDateTo || undefined
    }]
  }
  await createProductionPlan(payload)
  ElMessage.success('创建成功')
  createVisible.value = false
  loadPlans()
  loadStats()
}

// ==================== 派发生产工单 ====================
async function handleConfirmAndStart(row) {
  await ElMessageBox.confirm(`确认派发计划"${row.planNo}"？派发后将锁定物料并为所有行项创建生产工单。`, '派发确认')
  const { data } = await confirmAndStartPlan(row.id)
  const woCount = data?.workOrderCount || 0
  ElMessage.success(`派发成功，共创建 ${woCount} 个生产工单`)
  loadPlans()
  loadStats()
}

async function handleCancel(row) {
  await ElMessageBox.confirm(
    `确认取消计划"${row.planNo}"?<br/><br/>` +
    `<small style="color:#909399">· 计划锁定的物料库存将归还<br/>` +
    `· 关联销售订单若无其他在跑计划, 状态会回退到"已确认"<br/>` +
    `· 系统会通知销售员</small>`,
    '取消计划',
    { dangerouslyUseHTMLString: true, type: 'warning' }
  )
  await cancelProductionPlan(row.id)
  ElMessage.success('已取消')
  loadPlans()
  loadStats()
}

// ==================== 行项 CRUD ====================
const itemDialogVisible = ref(false)
const itemDialogMode = ref('add')
const itemForm = ref({ productId: null, planQty: 1, priority: 1, planStartDate: '', planEndDate: '' })
const productOptions = ref([])

async function loadProductOptions() {
  // 改为 remote 按需搜索;不再首次全量拉取
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
      productOptions.value = (res.data?.records || []).map(m => ({ id: m.id, productName: m.materialName, spec: m.spec }))
    } catch {} finally { productSearching.value = false }
  }, 300)
}

function handleAddItem() {
  loadProductOptions()
  itemDialogMode.value = 'add'
  itemForm.value = { productId: null, planQty: 1, priority: 1, planStartDate: '', planEndDate: '' }
  itemDialogVisible.value = true
}

function handleEditItem(row) {
  itemDialogMode.value = 'edit'
  itemForm.value = {
    id: row.id,
    productId: row.productId,
    _productName: row.productName,
    planQty: row.planQty,
    priority: row.priority || 0,
    planStartDate: row.planStartDate || '',
    planEndDate: row.planEndDate || ''
  }
  itemDialogVisible.value = true
}

async function submitItemForm() {
  if (itemDialogMode.value === 'add') {
    if (!itemForm.value.productId) return ElMessage.warning('请选择产品')
    await addPlanItem(detailData.value.id, itemForm.value)
    ElMessage.success('添加成功')
  } else {
    await updatePlanItem(itemForm.value.id, itemForm.value)
    ElMessage.success('更新成功')
  }
  itemDialogVisible.value = false
  handleViewDetail({ id: detailData.value.id })
}

async function handleDeleteItem(row) {
  await ElMessageBox.confirm(`确认删除行项"${row.productName}"?`, '删除行项')
  await deletePlanItem(row.id)
  ElMessage.success('已删除')
  handleViewDetail({ id: detailData.value.id })
}

// ==================== 工序维护 ====================
const processConfigItem = ref(null)
const processConfigSteps = ref([])

// ==================== 工序图纸 ====================
const drawingDialogVisible = ref(false)
const drawingTarget = ref({ productId: null, productName: '', stepNo: null, stepName: '', planItemStepId: null })

function openDrawingDialog(row) {
  if (!row._planItemStepId) {
    ElMessage.warning('请先点击「保存配置」后再管理图纸')
    return
  }
  drawingTarget.value = {
    productId: processConfigItem.value?.productId,
    productName: processConfigItem.value?.productName,
    stepNo: row.stepNo,
    stepName: row.stepName,
    planItemStepId: row._planItemStepId
  }
  drawingDialogVisible.value = true
}
const processConfigLoading = ref(false)
const processConfigSaving = ref(false)
const weightDialogVisible = ref(false)
const weightList = ref([])
const teamOptions = ref([])
const assigneeOptions = ref([])
// teamId → [{userId, userName}]
const planTeamMembersMap = reactive({})

async function loadPlanTeamMembers(teamId) {
  if (!teamId || planTeamMembersMap[teamId]) return planTeamMembersMap[teamId] || []
  try {
    const res = await getTeamMembers(teamId)
    planTeamMembersMap[teamId] = Array.isArray(res.data) ? res.data : []
    return planTeamMembersMap[teamId]
  } catch {
    planTeamMembersMap[teamId] = []
    return []
  }
}

// 切换班组时:加载成员,默认全选
async function onPlanTeamChange(row) {
  if (row._assignType !== 'team' || !row._teamId) {
    row._memberIds = []
    return
  }
  const members = await loadPlanTeamMembers(row._teamId)
  row._memberIds = members.map(m => m.userId)
}

// 从后端 assignedMemberIds(JSON string) 解析为数组
function parseMemberIds(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.map(Number).filter(Number.isFinite)
  try {
    const arr = JSON.parse(raw)
    return Array.isArray(arr) ? arr.map(Number).filter(Number.isFinite) : []
  } catch {
    return []
  }
}
const availableRoutes = ref([])
const selectedRouteId = ref(null)
const currentQcStep = ref(null)
const qcDetailLoading = ref(false)

// ==================== 工序流程图 ====================
const stepViewMode = ref('table')
const stepGanttContainer = ref(null)
let stepGanttInitialized = false
let _ganttDragUpdating = false

watch(stepViewMode, (v) => {
  if (v === 'flow' && processConfigSteps.value.length) {
    nextTick(() => buildPlanFlowGraph())
  }
  if (v === 'gantt' && processConfigSteps.value.length) {
    nextTick(() => { initStepGantt(); renderStepGantt() })
  }
  if (v !== 'gantt' && stepGanttInitialized) {
    destroyStepGantt()
  }
})
const planNodeTypes = { planStep: markRaw(PlanStepNode) }
const planEdgeOptions = { type: 'smoothstep', animated: false, selectable: false, style: { stroke: '#b1b1b7', strokeWidth: 2 } }
const planFlowNodes = ref([])
const planFlowEdges = ref([])
const planFlowKey = ref(0)
const selectedPlanStepIndex = ref(-1)
const selectedPlanStep = computed(() => {
  if (selectedPlanStepIndex.value < 0) return null
  return processConfigSteps.value[selectedPlanStepIndex.value] || null
})

provide('planConfigSteps', processConfigSteps)
provide('planTeamOptions', teamOptions)
provide('planAssigneeOptions', assigneeOptions)

function onPlanNodeClick({ node }) {
  selectedPlanStepIndex.value = node.data._stepIndex
}

function onPlanTableRowClick(row) {
  const idx = processConfigSteps.value.indexOf(row)
  if (idx >= 0) selectedPlanStepIndex.value = idx
}

function isParallelStep(index) {
  const steps = processConfigSteps.value
  const group = steps[index]?.stepGroup
  if (group == null) return false
  return steps.filter(s => s.stepGroup === group).length > 1
}

function planStepSpanMethod({ rowIndex, columnIndex }) {
  if (columnIndex !== 0) return
  const steps = processConfigSteps.value
  const group = steps[rowIndex]?.stepGroup
  if (group == null) return
  const groupCount = steps.filter(s => s.stepGroup === group).length
  if (groupCount <= 1) return
  if (rowIndex > 0 && steps[rowIndex - 1]?.stepGroup === group) {
    return { rowspan: 0, colspan: 0 }
  }
  let span = 1
  while (rowIndex + span < steps.length && steps[rowIndex + span]?.stepGroup === group) {
    span++
  }
  return { rowspan: span, colspan: 1 }
}

function planStepRowClass({ rowIndex }) {
  const classes = []
  if (isParallelStep(rowIndex)) classes.push('parallel-row')
  if (rowIndex === selectedPlanStepIndex.value) classes.push('selected-step-row')
  return classes.join(' ')
}

function buildPlanFlowGraph() {
  const steps = processConfigSteps.value
  if (!steps.length) { planFlowNodes.value = []; planFlowEdges.value = []; return }
  const NODE_W = 180, NODE_H = 100
  // 按 stepGroup 分组
  const groupMap = new Map()
  steps.forEach((s, idx) => {
    const g = s.stepGroup ?? (idx + 1)
    if (!groupMap.has(g)) groupMap.set(g, [])
    groupMap.get(g).push(idx)
  })
  const sortedGroups = [...groupMap.entries()].sort(([a], [b]) => a - b)
  const nodes = steps.map((s, idx) => ({
    id: `ps-${idx}`, type: 'planStep', position: { x: 0, y: 0 }, data: { _stepIndex: idx }
  }))
  // 相邻组之间创建边
  const groupNodeIds = new Map()
  steps.forEach((s, idx) => {
    const g = s.stepGroup ?? (idx + 1)
    if (!groupNodeIds.has(g)) groupNodeIds.set(g, [])
    groupNodeIds.get(g).push(`ps-${idx}`)
  })
  const flowEdges = []
  for (let i = 0; i < sortedGroups.length - 1; i++) {
    const currIds = groupNodeIds.get(sortedGroups[i][0]) || []
    const nextIds = groupNodeIds.get(sortedGroups[i + 1][0]) || []
    for (const src of currIds) for (const tgt of nextIds) {
      flowEdges.push({ id: `pe-${src}-${tgt}`, source: src, target: tgt, type: 'smoothstep', style: { stroke: '#b1b1b7', strokeWidth: 2 } })
    }
  }
  // dagre 自动布局
  const dg = new dagre.graphlib.Graph()
  dg.setGraph({ rankdir: 'LR', nodesep: 40, ranksep: 100, marginx: 30, marginy: 30 })
  dg.setDefaultEdgeLabel(() => ({}))
  nodes.forEach(n => dg.setNode(n.id, { width: NODE_W, height: NODE_H }))
  flowEdges.forEach(e => dg.setEdge(e.source, e.target))
  dagre.layout(dg)
  nodes.forEach(n => { const p = dg.node(n.id); n.position = { x: p.x - NODE_W / 2, y: p.y - NODE_H / 2 } })
  planFlowNodes.value = nodes
  planFlowEdges.value = flowEdges
  planFlowKey.value++
}

// ==================== 工序甘特图 ====================
function initStepGantt() {
  if (!stepGanttContainer.value || stepGanttInitialized) return

  gantt.i18n.setLocale({
    date: {
      month_full: ['一月','二月','三月','四月','五月','六月','七月','八月','九月','十月','十一月','十二月'],
      month_short: ['1月','2月','3月','4月','5月','6月','7月','8月','9月','10月','11月','12月'],
      day_full: ['星期日','星期一','星期二','星期三','星期四','星期五','星期六'],
      day_short: ['日','一','二','三','四','五','六']
    },
    labels: {
      new_task: '新任务', icon_save: '保存', icon_cancel: '取消',
      icon_details: '详情', icon_edit: '编辑', icon_delete: '删除',
      confirm_closing: '', confirm_deleting: '确定删除吗？',
      section_description: '描述', section_time: '时间',
      confirm_link_deleting: '将被删除',
      link_from: '从', link_to: '到', link_start: '开始', link_end: '结束',
      minutes: '分', hours: '时', days: '天', weeks: '周', months: '月', years: '年'
    }
  })

  gantt.config.columns = [
    { name: 'text', label: '工序名称', tree: false, width: 140, resize: true },
    { name: 'duration_label', label: '工时(分)', width: 80, align: 'center', resize: true },
    { name: 'handle_type_label', label: '类型', width: 70, align: 'center', resize: true }
  ]

  gantt.config.scale_unit = 'day'
  gantt.config.date_scale = '%m月%d日'
  gantt.config.scale_height = 50
  gantt.config.subscales = [{ unit: 'hour', step: 4, date: '%H:00' }]
  gantt.config.min_column_width = 40

  gantt.config.autofit = false
  gantt.config.autosize = 'y'
  gantt.config.autosize_min_y = 250
  gantt.config.row_height = 32
  gantt.config.bar_height = 22
  gantt.config.grid_width = 300

  gantt.config.drag_move = true
  gantt.config.drag_resize = true
  gantt.config.drag_progress = false
  gantt.config.drag_links = false
  gantt.config.readonly = false
  gantt.config.details_on_create = false
  gantt.config.details_on_dblclick = false

  gantt.config.date_format = '%Y-%m-%d %H:%i:%s'
  gantt.config.xml_date = '%Y-%m-%d %H:%i:%s'

  gantt.plugins({ tooltip: true })
  gantt.templates.tooltip_text = function(start, end, task) {
    return `<b>${task.text}</b><br/>` +
      `<b>工时:</b> ${task.duration_label}<br/>` +
      `<b>类型:</b> ${task.handle_type_label}<br/>` +
      `<b>开始:</b> ${gantt.templates.tooltip_date_format(start)}<br/>` +
      `<b>结束:</b> ${gantt.templates.tooltip_date_format(end)}`
  }

  gantt.templates.task_text = function(start, end, task) {
    return task.text || ''
  }

  gantt.templates.task_class = function(start, end, task) {
    if (task._isPlaceholder) return 'step-gantt-placeholder'
    if (task._handleType === 'outsource') return 'step-gantt-outsource'
    return ''
  }

  gantt.init(stepGanttContainer.value)
  stepGanttInitialized = true

  gantt.attachEvent('onAfterTaskDrag', function(id) {
    const task = gantt.getTask(id)
    if (task && task._stepIndex !== undefined) {
      const step = processConfigSteps.value[task._stepIndex]
      if (step) {
        _ganttDragUpdating = true
        step._planStartTime = formatGanttDateToISO(task.start_date)
        step._planEndTime = formatGanttDateToISO(task.end_date)
        nextTick(() => { _ganttDragUpdating = false })
      }
    }
  })
}

function destroyStepGantt() {
  if (stepGanttInitialized) {
    gantt.clearAll()
    gantt.detachAllEvents()
    stepGanttInitialized = false
  }
}

function renderStepGantt() {
  if (!stepGanttInitialized) return
  gantt.clearAll()

  const steps = processConfigSteps.value
  if (!steps.length) return

  const item = processConfigItem.value
  const fallbackStart = item?.planStartDate
    ? new Date(item.planStartDate + 'T08:00:00')
    : new Date()

  // 按 stepGroup 分组
  const groupMap = new Map()
  steps.forEach((s, idx) => {
    const g = s.stepGroup ?? idx
    if (!groupMap.has(g)) groupMap.set(g, [])
    groupMap.get(g).push(idx)
  })

  const tasks = []
  let placeholderOffset = 0

  steps.forEach((step, idx) => {
    let startDate, endDate
    const isPlaceholder = !step._planStartTime || !step._planEndTime

    if (!isPlaceholder) {
      startDate = step._planStartTime.replace('T', ' ')
      endDate = step._planEndTime.replace('T', ' ')
    } else {
      const offsetMs = placeholderOffset * 60 * 60 * 1000
      const duration = (step.standardTime || 60) * 60 * 1000
      const s = new Date(fallbackStart.getTime() + offsetMs)
      const e = new Date(s.getTime() + duration)
      startDate = formatDateToGantt(s)
      endDate = formatDateToGantt(e)
      placeholderOffset += (step.standardTime || 60) / 60
    }

    let color = '#409eff'
    if (step._handleType === 'outsource') color = '#e6a23c'
    if (isPlaceholder) color = '#909399'

    tasks.push({
      id: idx + 1,
      text: step.stepName || `工序${step.stepNo}`,
      start_date: startDate,
      end_date: endDate,
      color: color,
      duration_label: `${step.standardTime || 0} 天`,
      handle_type_label: step._handleType === 'outsource' ? '外协' : '自制',
      _stepIndex: idx,
      _handleType: step._handleType,
      _isPlaceholder: isPlaceholder,
      _stepGroup: step.stepGroup
    })
  })

  gantt.parse({ data: tasks, links: [] })

  if (tasks.length > 0) {
    const firstStart = tasks.reduce((min, t) => {
      const d = new Date(t.start_date.replace(/-/g, '/'))
      return d < min ? d : min
    }, new Date(tasks[0].start_date.replace(/-/g, '/')))
    gantt.showDate(firstStart)
  }
}

function formatDateToGantt(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatGanttDateToISO(date) {
  if (!date) return null
  const d = new Date(date)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// 工序数据变化时刷新甘特图（如时间均分操作后）
watch(processConfigSteps, () => {
  if (stepViewMode.value === 'gantt' && stepGanttInitialized && !_ganttDragUpdating) {
    nextTick(() => renderStepGantt())
  }
}, { deep: true })

async function loadTeamAndAssigneeOptions() {
  if (!teamOptions.value.length) {
    try {
      const { data } = await getTeamList()
      teamOptions.value = data || []
    } catch { /* ignore */ }
  }
  if (!assigneeOptions.value.length) {
    try {
      const { data } = await getAssigneeOptions()
      assigneeOptions.value = data || []
    } catch { /* ignore */ }
  }
}

async function handleProcessConfig(row) {
  // 点击同一行则收起
  if (processConfigItem.value && processConfigItem.value.id === row.id) {
    closeProcessConfig()
    return
  }
  processConfigItem.value = row
  processConfigLoading.value = true
  currentQcStep.value = null
  await loadTeamAndAssigneeOptions()
  loadSupplierOptions()

  // 从后端已保存的 stepConfigs 构建 map
  const savedConfigs = {}
  if (row.stepConfigs && row.stepConfigs.length) {
    row.stepConfigs.forEach(sc => { savedConfigs[sc.stepId] = sc })
  }

  // 获取已保存的路线ID
  const savedRouteId = row.stepConfigs?.[0]?.routeId || null

  try {
    // 加载所有工艺路线（含产品名称），将当前产品的路线排在前面
    const { data: routes } = await getProcessRoutes()
    const sorted = (routes || []).slice().sort((a, b) => {
      const aMatch = a.productId === row.productId ? 0 : 1
      const bMatch = b.productId === row.productId ? 0 : 1
      return aMatch - bMatch
    })
    availableRoutes.value = sorted

    // 优先使用已保存的路线，否则用当前产品的激活路线
    const targetRoute = savedRouteId
      ? sorted.find(r => r.id === savedRouteId)
      : sorted.find(r => r.productId === row.productId && r.status === 'active')

    if (targetRoute) {
      selectedRouteId.value = targetRoute.id
      await loadRouteSteps(targetRoute.id, savedConfigs)
    } else {
      selectedRouteId.value = null
      processConfigSteps.value = []
    }
  } catch {
    processConfigSteps.value = []
    availableRoutes.value = []
  } finally { processConfigLoading.value = false }
}

function closeProcessConfig() {
  if (stepGanttInitialized) destroyStepGantt()
  processConfigItem.value = null
  processConfigSteps.value = []
  currentQcStep.value = null
  selectedRouteId.value = null
  availableRoutes.value = []
  planFlowNodes.value = []
  planFlowEdges.value = []
  selectedPlanStepIndex.value = -1
  stepViewMode.value = 'table'
}

// 加载路线的工序步骤，合并已保存配置
async function loadRouteSteps(routeId, savedConfigs = {}) {
  processConfigLoading.value = true
  try {
    const { data: detail } = await getProcessRouteDetail(routeId)
    processConfigSteps.value = (detail.steps || []).map(s => {
      const saved = savedConfigs[s.id] || {}
      // 解析已保存的质检要求JSON
      let qcReqs = []
      if (saved.qcRequirements) {
        try { qcReqs = JSON.parse(saved.qcRequirements) } catch { /* ignore */ }
      }
      return {
        ...s,
        // 关键: saved.id 是 biz_production_plan_item_step.id (排产工序的真实主键)；
        // 不能直接用 s.id（s.id 是路线工序id），否则会和其他计划的工序串扰
        _planItemStepId: saved.id || null,
        _teamId: saved.teamId || s.teamId || null,
        _assigneeId: saved.assigneeId || null,
        _assignType: saved.assigneeId ? 'person' : 'team',
        _needQc: saved.needQc || !!s.qcTemplateId,
        _handleType: saved.handleType || (s.isOutsourceable === 1 ? 'outsource' : 'self'),
        _supplierId: saved.supplierId || null,
        _supplierName: saved.supplierName || '',
        _planStartTime: saved.planStartTime || null,
        _planEndTime: saved.planEndTime || null,
        _qcTemplateId: saved.qcTemplateId || s.qcTemplateId || null,
        _qcTemplateName: '',
        _qcItems: [],
        _savedQcReqs: qcReqs,
        _showQcDetail: false,
        _wageRatio: saved.wageRatio != null ? Number(saved.wageRatio) : 1,
        _memberIds: parseMemberIds(saved.assignedMemberIds)
      }
    })
    // 预加载所有班组指派工序的成员列表,供成员多选下拉使用
    await Promise.all(
      processConfigSteps.value
        .filter(s => s._assignType === 'team' && s._teamId)
        .map(s => loadPlanTeamMembers(s._teamId))
    )
    currentQcStep.value = null
  } catch {
    processConfigSteps.value = []
  } finally {
    processConfigLoading.value = false
    selectedPlanStepIndex.value = -1
    nextTick(() => buildPlanFlowGraph())
  }
}

// 切换工艺路线
async function onRouteChange(routeId) {
  if (!routeId) return
  currentQcStep.value = null
  selectedPlanStepIndex.value = -1
  // 切换路线时清空已有配置
  await loadRouteSteps(routeId, {})
}

// 质检开关切换
function onQcToggle(row) {
  if (!row._needQc) {
    row._showQcDetail = false
    if (currentQcStep.value === row) currentQcStep.value = null
  }
}

// 展开/收起质检要求维护
async function toggleQcDetail(row) {
  if (row._showQcDetail) {
    row._showQcDetail = false
    currentQcStep.value = null
    return
  }
  // 收起其他行
  processConfigSteps.value.forEach(s => { s._showQcDetail = false })
  row._showQcDetail = true
  currentQcStep.value = row

  // 如果还没加载过质检模板的项目，则加载
  const templateId = row._qcTemplateId || row.qcTemplateId
  if (templateId && (!row._qcItems || !row._qcItems.length)) {
    qcDetailLoading.value = true
    try {
      const { data: tmpl } = await getQcTemplateDetail(templateId)
      row._qcTemplateName = tmpl.templateName || ''
      // 将模板项目映射为可编辑的质检要求
      const savedReqs = row._savedQcReqs || []
      row._qcItems = (tmpl.items || []).map(item => {
        // 查找已保存的对应要求
        const saved = savedReqs.find(r => r.itemName === item.itemName) || {}
        return {
          itemName: item.itemName,
          itemType: item.itemType || 'qualitative',
          unit: saved.unit || item.unit || '',
          isCritical: item.isCritical || 0,
          method: item.method || '',
          _standardValue: saved.standardValue ?? item.standardValue ?? '',
          _toleranceMin: saved.toleranceMin ?? item.toleranceMin ?? '',
          _toleranceMax: saved.toleranceMax ?? item.toleranceMax ?? '',
          _requirement: saved.requirement ?? ''
        }
      })
    } catch {
      row._qcItems = []
      ElMessage.warning('加载质检模板失败')
    } finally { qcDetailLoading.value = false }
  }
}

// 自动排产：根据工序默认标准工时（天），从计划完工时间向前倒推，无计划开工时间限制
function autoScheduleByWorkHours() {
  const item = processConfigItem.value
  const steps = processConfigSteps.value
  if (!item?.planEndDate || !steps.length) return

  const endDate = new Date(item.planEndDate + 'T00:00:00')
  // 从末道工序开始倒推
  let cursor = endDate.getTime()
  for (let i = steps.length - 1; i >= 0; i--) {
    const days = Math.max(steps[i].standardTime || 1, 1) // 至少1天
    const stepEnd = new Date(cursor)
    const stepStart = new Date(cursor - (days - 1) * 86400000)
    steps[i]._planStartTime = formatDate(stepStart) + 'T00:00:00'
    steps[i]._planEndTime = formatDate(stepEnd) + 'T23:59:59'
    // 下一道工序的结束日是当前工序开始日的前一天
    cursor = stepStart.getTime() - 86400000
  }
}

// 时间均分（按 stepGroup 分组,同组并行共享同一时间段;严格不超出 planEndDate）
// 天数充裕 → 每段整天分布,最后一段对齐 planEnd;天数不够 → 同一天可挂多段,不外溢
function distributeTimeEvenly() {
  const item = processConfigItem.value
  const steps = processConfigSteps.value
  if (!item?.planStartDate || !item?.planEndDate || !steps.length) return
  const startDate = new Date(item.planStartDate + 'T00:00:00')
  const endDate = new Date(item.planEndDate + 'T00:00:00')
  const totalDays = Math.round((endDate - startDate) / (1000 * 60 * 60 * 24)) + 1

  // 收集去重后的 stepGroup 序列（按当前 steps 出现顺序）
  const groupOrder = []
  steps.forEach(s => {
    const g = s.stepGroup ?? s._stepGroup ?? null
    if (g != null && !groupOrder.includes(g)) groupOrder.push(g)
  })
  const slots = groupOrder.length > 0 ? groupOrder.length : steps.length

  // 计算每个 slot 的起止天偏移,确保都落在 [0, totalDays-1]
  const slotRange = []
  for (let i = 0; i < slots; i++) {
    let startOffset = Math.floor(i * totalDays / slots)
    let endOffset = Math.floor((i + 1) * totalDays / slots) - 1
    if (endOffset < startOffset) endOffset = startOffset // 天数不足时同一天放多段
    if (i === slots - 1) endOffset = totalDays - 1       // 最后一段对齐 planEnd
    slotRange.push({ startOffset, endOffset })
  }

  steps.forEach((s, i) => {
    let slotIdx
    if (groupOrder.length > 0) {
      const g = s.stepGroup ?? s._stepGroup ?? null
      slotIdx = groupOrder.indexOf(g)
    } else {
      slotIdx = i
    }
    const range = slotRange[slotIdx]
    if (!range) return
    const sDay = new Date(startDate.getTime() + range.startOffset * 86400000)
    const eDay = new Date(startDate.getTime() + range.endOffset * 86400000)
    s._planStartTime = formatDate(sDay) + 'T00:00:00'
    s._planEndTime = formatDate(eDay) + 'T23:59:59'
  })
}

// 按工时权重分配时间 - 打开弹窗（同组并行只保留 1 行,权重取组内最大工时）
function distributeTimeByWeight() {
  const steps = processConfigSteps.value
  if (!steps.length) return
  // 按 stepGroup 聚合; 优先使用上次用户改过的 _stepWeight (保存在 step 对象上),否则取 standardTime
  const groupMap = new Map()
  steps.forEach(s => {
    const g = s.stepGroup ?? s._stepGroup ?? s.stepNo
    const cur = groupMap.get(g)
    const t = s.standardTime || 1
    const w = (s._stepWeight != null && s._stepWeight > 0) ? s._stepWeight : t
    if (!cur) {
      groupMap.set(g, { stepGroup: g, stepName: s.stepName, standardTime: t, _weight: w })
    } else {
      // 同组取最大值: 用户输入优先, 否则按 standardTime
      if (w > cur._weight) cur._weight = w
      if (t > cur.standardTime) cur.standardTime = t
    }
  })
  weightList.value = [...groupMap.values()]
  weightDialogVisible.value = true
}

// 确认权重分配（按 stepGroup 分组,同组并行共享同一时间段;按天计算,时分秒 00:00:00 / 23:59:59）
function applyWeightDistribution() {
  const item = processConfigItem.value
  const steps = processConfigSteps.value
  if (!item?.planStartDate || !item?.planEndDate || !steps.length) return
  const startDate = new Date(item.planStartDate + 'T00:00:00')
  const endDate = new Date(item.planEndDate + 'T00:00:00')
  const totalDays = Math.round((endDate - startDate) / (1000 * 60 * 60 * 24)) + 1

  // 按 weightList 顺序分配每个 group 的天数
  const totalWeight = weightList.value.reduce((sum, w) => sum + (w._weight || 1), 0)
  const groupRange = new Map() // group → { startDay, days }
  let cursorDay = 0
  weightList.value.forEach((w, i) => {
    const days = i === weightList.value.length - 1
        ? Math.max(1, totalDays - cursorDay) // 最后一组吃掉剩余天数
        : Math.max(1, Math.round(totalDays * ((w._weight || 1) / totalWeight)))
    groupRange.set(w.stepGroup, { startDay: cursorDay, days })
    cursorDay += days
  })

  // 把用户输入的权重写回每条 step,下次再打开弹窗能恢复
  const weightByGroup = new Map(weightList.value.map(w => [w.stepGroup, w._weight]))
  steps.forEach(s => {
    const g = s.stepGroup ?? s._stepGroup ?? s.stepNo
    const range = groupRange.get(g)
    if (!range) return
    const sDay = new Date(startDate.getTime() + range.startDay * 86400000)
    const eDay = new Date(startDate.getTime() + (range.startDay + range.days - 1) * 86400000)
    s._planStartTime = formatDate(sDay) + 'T00:00:00'
    s._planEndTime = formatDate(eDay) + 'T23:59:59'
    s._stepWeight = weightByGroup.get(g)
  })
  weightDialogVisible.value = false
}

function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

async function saveProcessConfig() {
  if (!processConfigItem.value) return
  const itemId = processConfigItem.value.id

  // 先保存行项的计划日期
  const itemDates = {
    planStartDate: processConfigItem.value.planStartDate,
    planEndDate: processConfigItem.value.planEndDate
  }
  try {
    await updatePlanItem(itemId, itemDates)
  } catch { /* ignore, continue saving steps */ }

  // 构建要保存到后端的数据
  const stepsToSave = processConfigSteps.value.map(step => {
    const team = teamOptions.value.find(t => t.id === step._teamId)
    const user = assigneeOptions.value.find(u => u.id === step._assigneeId)

    // 构建质检要求JSON
    let qcRequirements = null
    if ((step._qcTemplateId || step.qcTemplateId) && step._qcItems && step._qcItems.length) {
      qcRequirements = JSON.stringify(step._qcItems.map(qi => ({
        itemName: qi.itemName,
        itemType: qi.itemType,
        unit: qi.unit || '',
        standardValue: qi._standardValue || '',
        toleranceMin: qi._toleranceMin || '',
        toleranceMax: qi._toleranceMax || '',
        requirement: qi._requirement || ''
      })))
    }

    return {
      stepId: step.id,
      stepNo: step.stepNo,
      stepName: step.stepName,
      teamId: step._teamId || null,
      teamName: team?.teamName || '',
      assigneeId: step._assigneeId || null,
      assigneeName: user?.realName || '',
      needQc: !!(step._qcTemplateId || step.qcTemplateId),
      standardTime: step.standardTime || 0,
      piecePrice: step.piecePrice || 0,
      handleType: step._handleType || 'self',
      supplierId: step._handleType === 'outsource' ? (step._supplierId || null) : null,
      supplierName: step._handleType === 'outsource'
        ? (supplierOptions.value.find(s => s.id === step._supplierId)?.supplierName || supplierOptions.value.find(s => s.id === step._supplierId)?.name || '')
        : '',
      planStartTime: step._planStartTime ? step._planStartTime.replace(' ', 'T') : null,
      planEndTime: step._planEndTime ? step._planEndTime.replace(' ', 'T') : null,
      qcTemplateId: step._qcTemplateId || step.qcTemplateId || null,
      qcRequirements,
      routeId: selectedRouteId.value || null,
      wageRatio: step._wageRatio != null ? Number(step._wageRatio) : 1,
      // 班组指派时保存允许报工的成员白名单(JSON),个人指派不存
      assignedMemberIds: step._assignType === 'team' && Array.isArray(step._memberIds) && step._memberIds.length > 0
        ? JSON.stringify(step._memberIds)
        : null
    }
  })
  processConfigSaving.value = true
  try {
    await saveItemStepConfigs(itemId, stepsToSave)
    // 保存后回填 plan_item_step.id 到当前行（按 stepId 即路线工序id 匹配）
    try {
      const res = await getItemStepConfigs(itemId)
      const savedRows = res.data || []
      const byStepId = new Map(savedRows.map(r => [r.stepId, r]))
      processConfigSteps.value.forEach(row => {
        const matched = byStepId.get(row.id)
        if (matched) row._planItemStepId = matched.id
      })
    } catch { /* ignore - 用户可重新打开恢复 */ }
    // 更新行项的 _processSteps 显示
    if (processConfigItem.value._processSteps) {
      processConfigItem.value._processSteps = processConfigSteps.value.map(s => {
        const saved = stepsToSave.find(st => st.stepId === s.id) || {}
        return {
          ...s,
          _teamName: saved.teamName || '',
          _assigneeName: saved.assigneeName || '',
          _handleType: saved.handleType || 'self',
          _supplierName: saved.supplierName || '',
          _needQc: saved.needQc || false
        }
      })
    }
    // 同步更新行项的 stepConfigs 以便后续读取
    processConfigItem.value.stepConfigs = stepsToSave
    // 同步行项日期
    processConfigItem.value.planStartDate = itemDates.planStartDate
    processConfigItem.value.planEndDate = itemDates.planEndDate
    // 保存配置后状态可能变为待派发，刷新详情和列表
    if (detailData.value && detailData.value.status === 'draft') {
      detailData.value.status = 'scheduled'
    }
    loadPlans()
    loadStats()
    ElMessage.success('工序配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    processConfigSaving.value = false
  }
}

// 防抖保存：避免多个blur事件连续触发并发保存
let _saveTimer = null
function debouncedSaveProcessConfig() {
  if (_saveTimer) clearTimeout(_saveTimer)
  _saveTimer = setTimeout(() => {
    _saveTimer = null
    saveProcessConfig()
  }, 300)
}

// ==================== 供应商选项 + 处理方式辅助 ====================
const supplierOptions = ref([])

async function loadSupplierOptions() {
  if (supplierOptions.value.length) return
  try {
    const { data } = await getSupplierSelect()
    supplierOptions.value = data || []
  } catch { /* ignore */ }
}

function onHandleTypeChange(row) {
  if (row._handleType !== 'outsource') {
    row._supplierId = null
  }
}

function onAssignTypeChange(row) {
  if (row._assignType === 'team') {
    row._assigneeId = null
  } else {
    row._teamId = null
  }
}

// ==================== 转外协申请 ====================
const outsourceVisible = ref(false)
const outsourceForm = ref(null)

async function handleOutsourceRequest(row) {
  // 加载供应商和工序
  loadSupplierOptions()
  const steps = row._processSteps || []
  // 默认选择标记为外协的工序
  const outsourceSteps = steps.filter(s => s._handleType === 'outsource')
  outsourceForm.value = {
    _productName: row.productName,
    _spec: row.spec,
    _steps: steps,
    productId: row.productId,
    planItemId: row.id,
    stepName: outsourceSteps.length ? outsourceSteps[0].stepName : (steps[0]?.stepName || ''),
    quantity: Math.max(1, row.planQty - (row.releasedQty || 0)),
    requiredDate: row.planEndDate || '',
    processRequirement: ''
  }
  outsourceVisible.value = true
}

async function submitOutsourceRequest() {
  const form = outsourceForm.value
  if (!form.stepName) return ElMessage.warning('请选择外协工序')
  if (!form.quantity || form.quantity <= 0) return ElMessage.warning('请输入外协数量')
  try {
    await createOutsourceRequest({
      productId: form.productId,
      stepName: form.stepName,
      quantity: form.quantity,
      requiredDate: form.requiredDate,
      processRequirement: form.processRequirement
    })
    ElMessage.success('外协申请已提交')
    outsourceVisible.value = false
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '提交失败')
  }
}

// ==================== 生产周期 & 最晚开工日期 ====================
function calcCycleTime(steps) {
  if (!steps || !steps.length) return 0
  return steps.reduce((sum, s) => sum + (s.standardTime || 0), 0)
}

function calcLatestStartDate(item) {
  if (!item?.planEndDate || !item?._processSteps?.length) return '-'
  const cycleMinutes = calcCycleTime(item._processSteps)
  const cycleDays = Math.ceil(cycleMinutes / 480) // 8小时/天
  const endDate = new Date(item.planEndDate)
  endDate.setDate(endDate.getDate() - cycleDays)
  return endDate.toISOString().split('T')[0]
}

function isOverdue(item) {
  const latestStart = calcLatestStartDate(item)
  if (latestStart === '-') return false
  return new Date(latestStart) < new Date()
}

// ==================== 开始任务（增强：工序指派） ====================
const startVisible = ref(false)
const startItem = ref(null)
const startQty = ref(0)
const startSubmitting = ref(false)
const startProcessSteps = ref([])

async function handleStartTask(row) {
  startItem.value = row
  startQty.value = Math.max(1, row.planQty - (row.releasedQty || 0))
  await loadTeamAndAssigneeOptions()
  // 从后端已保存的 stepConfigs 构建 map
  const savedConfigs = {}
  if (row.stepConfigs && row.stepConfigs.length) {
    row.stepConfigs.forEach(sc => { savedConfigs[sc.stepId] = sc })
  }
  // 优先使用已保存的路线ID
  const savedRouteId = row.stepConfigs?.[0]?.routeId || null
  try {
    const { data: routes } = await getProcessRoutes(row.productId)
    const targetRoute = savedRouteId
      ? routes?.find(r => r.id === savedRouteId)
      : routes?.find(r => r.status === 'active')
    if (targetRoute) {
      const { data: detail } = await getProcessRouteDetail(targetRoute.id)
      startProcessSteps.value = (detail.steps || []).map(s => {
        const saved = savedConfigs[s.id] || {}
        return {
          ...s,
          _teamId: saved.teamId || s.teamId || null,
          _assigneeId: saved.assigneeId || null,
          _assignType: saved.assigneeId ? 'person' : 'team',
          _needQc: saved.needQc || false,
          _handleType: saved.handleType || (s.isOutsourceable === 1 ? 'outsource' : 'self'),
          _supplierId: saved.supplierId || null
        }
      })
    } else {
      startProcessSteps.value = []
    }
  } catch {
    startProcessSteps.value = []
  }
  startVisible.value = true
}

async function submitStartTask() {
  startSubmitting.value = true
  try {
    const { data: result } = await releaseWorkOrder(startItem.value.id, { releaseQty: startQty.value })
    const workOrderId = result?.workOrderId || result
    // 自动指派工序任务
    if (workOrderId && startProcessSteps.value.some(s => s._teamId || s._assigneeId)) {
      try {
        const { data: tasks } = await getWorkOrderTasks(workOrderId)
        if (tasks && tasks.length) {
          for (const step of startProcessSteps.value) {
            if (step._teamId || step._assigneeId) {
              const task = tasks.find(t => t.stepNo === step.stepNo)
              if (task) {
                await assignTask(workOrderId, task.id, {
                  teamId: step._teamId || null,
                  userId: step._assigneeId || null
                })
              }
            }
          }
        }
      } catch { /* 指派失败不影响主流程 */ }
    }
    ElMessage.success('生产任务已开始，工单已自动生成')
    startVisible.value = false
    handleViewDetail({ id: detailData.value.id })
    loadPlans()
    loadStats()
  } finally { startSubmitting.value = false }
}

// ==================== 物料齐套检查 ====================
const materialCheckVisible = ref(false)
const materialCheckData = ref([])
const materialCheckLoading = ref(false)

async function handleCheckMaterial(plan) {
  materialCheckVisible.value = true
  materialCheckLoading.value = true
  try {
    const { data } = await checkPlanMaterial(plan.id)
    materialCheckData.value = data || []
  } catch {
    materialCheckData.value = []
  } finally { materialCheckLoading.value = false }
}

function goToMrp() {
  materialCheckVisible.value = false
  router.push('/production/mrp')
}

// ==================== 行项物料锁定明细 ====================
const itemMaterialVisible = ref(false)
const itemMaterialData = ref([])
const itemMaterialLoading = ref(false)
const itemMaterialTitle = ref('')

async function handleViewItemMaterials(row) {
  itemMaterialTitle.value = row.productName || ''
  itemMaterialVisible.value = true
  itemMaterialLoading.value = true
  try {
    const { data } = await getPlanItemMaterials(row.id)
    itemMaterialData.value = data || []
  } catch {
    itemMaterialData.value = []
  } finally { itemMaterialLoading.value = false }
}

// ==================== 导出 ====================
async function handleExportPlan(row) {
  try {
    const res = await exportProductionPlan(row.id)
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a')
    a.href = url
    a.download = `车间计划_${row.planNo}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

async function handleBatchExport() {
  if (!selectedPlans.value.length) return ElMessage.warning('请先选择计划')
  const ids = selectedPlans.value.map(p => p.id).join(',')
  try {
    const res = await exportProductionPlansBatch(ids)
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a')
    a.href = url
    a.download = `车间计划_批量_${new Date().toISOString().slice(0,10)}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('批量导出失败')
  }
}

// ==================== 状态辅助 ====================
function planStatusType(s) {
  return { draft: 'info', scheduled: '', in_progress: 'warning', confirmed: 'primary', completed: 'success', cancelled: 'danger' }[s] || 'info'
}
function planStatusText(s) {
  return { draft: '草稿', scheduled: '待派发', confirmed: '已确认', in_progress: '执行中', completed: '已完成', cancelled: '已取消' }[s] || s
}
function itemStatusType(s) {
  return { pending: 'info', partial_released: 'warning', fully_released: 'primary', completed: 'success' }[s] || 'info'
}
function itemStatusText(s) {
  return { pending: '待生产', partial_released: '生产中', fully_released: '已开始', completed: '已完工' }[s] || s
}

// ==================== 订单详情弹窗 ====================
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
  } catch { orderDetailData.value = null }
  finally { orderDetailLoading.value = false }
}

onMounted(async () => {
  loadPlans()
  loadStats()
  await loadFieldConfigs()
  loadColumnConfig(listFieldConfigs.value)
})
</script>

<style scoped>
.page-container { padding: 16px; }
.table-card { margin-bottom: 16px; }
.tab-toolbar { display: flex; align-items: center; margin-bottom: 12px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
.text-warning { color: #e6a23c; font-weight: 600; }
.text-danger { color: #f56c6c; font-weight: 600; }
.stat-card {
  background: #f5f7fa; border-radius: 8px; padding: 16px; text-align: center; cursor: pointer;
  transition: all 0.3s; border: 1px solid #ebeef5;
}
.stat-card:hover { background: #ecf5ff; border-color: #409eff; }
.stat-card .stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-card .stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.stat-card.highlight-warning { border-color: #e6a23c; }
.stat-card.highlight-warning .stat-value { color: #e6a23c; }
.stat-card.highlight-success { border-color: #67c23a; }
.stat-card.highlight-success .stat-value { color: #67c23a; }

/* 工序维护配置区域 */
.config-section {
  margin-bottom: 16px;
}
.config-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding: 8px 0;
}
.config-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-left: 10px;
  border-left: 3px solid #409eff;
}
.qc-detail-section {
  background: #f9fafc;
  border-radius: 6px;
  padding: 12px;
  border: 1px solid #ebeef5;
}

/* 工序维护内联区域 */
.inline-process-config {
  margin-top: 16px;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
}
.inline-process-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

/* 当前正在配置工序的行高亮 */
:deep(.current-config-row) {
  background-color: #ecf5ff !important;
}

/* 工序流程图 */
.plan-flow-editor {
  display: flex;
  height: 350px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}
.plan-flow-canvas {
  flex: 1;
  position: relative;
}
.plan-flow-panel {
  width: 280px;
  padding: 12px 16px;
  border-left: 1px solid #ebeef5;
  overflow-y: auto;
  background: #fafbfc;
}
.plan-flow-panel h4 {
  margin: 0 0 12px 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.plan-flow-panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 工序表格 - 并行行高亮 */
:deep(.parallel-row) {
  background-color: #fdf6ec !important;
}
:deep(.selected-step-row) {
  background-color: #ecf5ff !important;
}

/* 工序甘特图视图 */
.plan-gantt-wrapper {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}
.plan-gantt-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fafbfc;
  font-size: 12px;
  color: #606266;
}
.plan-gantt-legend .legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.plan-gantt-legend .legend-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
}
.step-gantt-container {
  width: 100%;
  height: 350px;
}

/* 甘特图内部样式（需穿透 scoped） */
:deep(.step-gantt-placeholder) {
  opacity: 0.6;
}
:deep(.step-gantt-placeholder .gantt_task_content) {
  font-style: italic;
}
</style>
