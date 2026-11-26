<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <div class="stat-card" :class="{ active: activeTab === 'orders' }" @click="switchTab('orders')">
          <div class="stat-value">{{ stats.pendingOrderCount || 0 }}</div>
          <div class="stat-label">待运算订单</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" :class="{ active: activeTab === 'runs' }" @click="switchTab('runs')">
          <div class="stat-value">{{ stats.totalRunCount || 0 }}</div>
          <div class="stat-label">运行次数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card highlight-warning" :class="{ active: activeTab === 'purchase' }" @click="switchTab('purchase', 'pending')">
          <div class="stat-value">{{ stats.pendingPurchaseCount || 0 }}</div>
          <div class="stat-label">待确认采购</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card highlight-warning" :class="{ active: activeTab === 'production' }" @click="switchTab('production', 'pending')">
          <div class="stat-value">{{ stats.pendingProductionCount || 0 }}</div>
          <div class="stat-label">待确认生产</div>
        </div>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">

        <!-- Tab 1: 待运算订单 -->
        <el-tab-pane name="orders">
          <template #label>
            待运算订单
            <el-badge v-if="plannedOrders.length" :value="plannedOrders.length" :max="99" class="tab-badge" />
          </template>
          <div class="tab-toolbar">
            <el-button type="primary" :disabled="!plannedOrders.length" @click="handleRunMrp">
              运行MRP ({{ plannedOrders.length }})
            </el-button>
            <span style="margin-left: auto; color: #909399; font-size: 13px">
              以下订单已从销售订单【转计划单】派发，点击运行将对所有订单执行MRP运算
            </span>
          </div>

          <el-table :data="plannedOrders" v-loading="ordersLoading" border stripe row-key="id">
            <el-table-column prop="orderNo" label="订单编号" min-width="160" />
            <el-table-column prop="customerName" label="客户" min-width="160" />
            <el-table-column prop="deliveryDate" label="交期" width="120" />
            <el-table-column prop="totalAmount" label="金额" width="120" align="right">
              <template #default="{ row }">¥{{ row.totalAmount?.toFixed(2) || '0.00' }}</template>
            </el-table-column>
            <el-table-column label="产品明细" min-width="250">
              <template #default="{ row }">
                <div v-for="(item, i) in (row.items || [])" :key="i" class="order-item-line">
                  {{ item.productName }} × {{ item.quantity }}
                  <span v-if="item.spec" class="text-muted"> ({{ $fmtSpec(item.spec) }})</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="80" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" size="small" @click.stop="handleRemoveFromPool(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!ordersLoading && !plannedOrders.length" description="暂无待运算订单，请先在销售订单中【转计划单】" />
        </el-tab-pane>

        <!-- Tab 2: MRP运行记录 -->
        <el-tab-pane label="运行记录" name="runs">
          <div class="tab-toolbar">
            <el-input v-model="runQuery.keyword" placeholder="搜索运行编号" clearable style="width: 200px" @clear="loadRuns" @keyup.enter="loadRuns" />
            <el-button type="primary" style="margin-left: 8px" @click="loadRuns">查询</el-button>
            <el-button @click="runQuery.keyword = ''; loadRuns()">重置</el-button>
          </div>

          <el-table :data="runData" v-loading="runLoading" border stripe @row-click="handleRunClick"
            style="cursor: pointer">
            <el-table-column prop="runNo" label="运行编号" min-width="180" />
            <el-table-column prop="runType" label="运行类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.runType === 'scheduled' ? 'info' : 'primary'" size="small">
                  {{ row.runType === 'scheduled' ? '定时' : '手动' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="runTime" label="运行时间" width="170" />
            <el-table-column prop="orderCount" label="订单数" width="80" align="center" />
            <el-table-column prop="resultCount" label="建议数" width="80" align="center" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="mrpStatusType(row.status)" size="small">{{ mrpStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="170" />
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="runQuery.pageNum" v-model:page-size="runQuery.pageSize"
              :total="runTotal" :page-sizes="[10, 20, 50]" background
              layout="total, sizes, prev, pager, next, jumper" @size-change="loadRuns" @current-change="loadRuns" />
          </div>
        </el-tab-pane>

        <!-- Tab 3: 采购建议 -->
        <el-tab-pane name="purchase">
          <template #label>
            采购需求
            <el-badge v-if="stats.pendingPurchaseCount" :value="stats.pendingPurchaseCount" :max="99" class="tab-badge" />
          </template>
          <div class="tab-toolbar">
            <el-select v-model="purchaseQuery.status" placeholder="状态筛选" clearable style="width: 120px" @change="loadPurchaseSuggestions">
              <el-option label="待确认" value="pending" />
              <el-option label="已转采购" value="transferred" />
              <el-option label="已忽略" value="ignored" />
            </el-select>
            <el-date-picker v-model="purchaseDateRange" type="daterange" range-separator="至"
              start-placeholder="需求开始" end-placeholder="需求结束" value-format="YYYY-MM-DD"
              style="width: 260px; margin-left: 12px" @change="loadPurchaseSuggestions" />
            <el-button type="success" plain :disabled="!purchaseSelection.length" @click="handleBatchConfirmPurchase" style="margin-left: 12px">
              批量转采购申请 ({{ purchaseSelection.length }})
            </el-button>
            <el-button plain @click="handleExportPurchase" style="margin-left: 8px">导出</el-button>
          </div>

          <el-table :data="purchaseData" v-loading="purchaseLoading" border
            @selection-change="handlePurchaseSelectionChange" :row-class-name="purchaseRowClassName">
            <el-table-column type="selection" width="50" :selectable="row => row.status === 'pending'" />
            <el-table-column prop="materialNo" label="物料编号" min-width="120" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column label="规格" min-width="100">
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="suggestQty" label="建议数量" width="100" align="right" />
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column prop="requiredDate" label="需求日期" width="120">
              <template #default="{ row }">
                <span :class="urgencyClass(row.requiredDate)">{{ row.requiredDate }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="suggestOrderDate" label="建议下单日" width="120" />
            <el-table-column prop="supplierName" label="供应商" min-width="120" />
            <el-table-column label="来源订单" min-width="140">
              <template #default="{ row }">
                <template v-if="row.sourceOrders && row.sourceOrders.length">
                  <span v-for="(o, i) in row.sourceOrders" :key="o.id">
                    <el-button link type="primary" size="small" @click.stop="handleViewOrder(o.id)">{{ o.orderNo }}</el-button>
                    <span v-if="i < row.sourceOrders.length - 1" class="text-muted">, </span>
                  </span>
                </template>
                <span v-else-if="row.sourceOrderNos" class="text-muted">{{ row.sourceOrderNos }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="suggestionStatusType(row.status)" size="small">{{ suggestionStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button type="primary" link size="small" @click="handleEditPurchase(row)">编辑</el-button>
                  <el-button type="success" link size="small" @click="handleConfirmPurchase(row)">转采购申请</el-button>
                  <el-button type="danger" link size="small" @click="handleIgnore('purchase', row)">忽略</el-button>
                </template>
                <span v-else class="text-muted">{{ row.status === 'transferred' ? '已转采购' : '已忽略' }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="purchaseQuery.pageNum" v-model:page-size="purchaseQuery.pageSize"
              :total="purchaseTotal" :page-sizes="[10, 20, 50]" background
              layout="total, sizes, prev, pager, next, jumper" @size-change="loadPurchaseSuggestions" @current-change="loadPurchaseSuggestions" />
          </div>
        </el-tab-pane>

        <!-- Tab 4: 生产建议 -->
        <el-tab-pane name="production">
          <template #label>
            生产需求
            <el-badge v-if="stats.pendingProductionCount" :value="stats.pendingProductionCount" :max="99" class="tab-badge" />
          </template>
          <div class="tab-toolbar">
            <el-select v-model="productionQuery.status" placeholder="状态筛选" clearable style="width: 120px" @change="loadProductionSuggestions">
              <el-option label="待确认" value="pending" />
              <el-option label="已转计划" value="transferred" />
              <el-option label="已忽略" value="ignored" />
            </el-select>
            <el-date-picker v-model="productionDateRange" type="daterange" range-separator="至"
              start-placeholder="需求开始" end-placeholder="需求结束" value-format="YYYY-MM-DD"
              style="width: 260px; margin-left: 12px" @change="loadProductionSuggestions" />
            <el-button type="primary" plain :disabled="!productionSelection.length" @click="handleCreatePlanFromSuggestions" style="margin-left: 12px">
              转入生产计划 ({{ productionSelection.length }})
            </el-button>
            <el-button plain @click="handleExportProduction" style="margin-left: 8px">导出</el-button>
          </div>

          <!-- 按竞争物料分组展示 -->
          <div v-loading="productionLoading">
            <div v-for="section in productionSections" :key="section.key" class="production-section">
              <!-- 竞争组标题栏 -->
              <div v-if="section.isGroup" class="group-header" :class="section.colorClass">
                <div class="group-header-main">
                  <span class="group-header-icon">⚠</span>
                  <span class="group-header-title">竞争物料: {{ section.material }}</span>
                  <el-tag v-if="section.stockInfo" size="small" effect="plain" round style="margin-left: 8px"
                    :type="calcGroupRemaining(section).remaining > 0 ? 'info' : 'danger'">
                    库存: {{ section.stockInfo.stock }} {{ section.stockInfo.unit }}
                    <template v-if="calcGroupRemaining(section).consumed > 0">
                      &nbsp;| 已分配: {{ calcGroupRemaining(section).consumed }}
                      &nbsp;| 剩余: <b>{{ calcGroupRemaining(section).remaining }}</b>
                    </template>
                  </el-tag>
                  <el-tag type="danger" size="small" effect="dark" round style="margin-left: 4px">
                    {{ section.activeCount }} 个需求竞争
                  </el-tag>
                </div>
                <div class="group-header-actions">
                  <el-button size="small" @click="distributeGroup(section, 'average')">平均分配</el-button>
                  <el-button size="small" @click="distributeGroup(section, 'proportional')">按需求比例分配</el-button>
                  <el-button size="small" type="info" plain @click="resetGroupActualQty(section)">重置为0</el-button>
                  <el-button size="small" type="primary" :loading="groupSaving[section.key]"
                    :disabled="!hasGroupChanges(section)" @click="saveGroupActualQty(section)">保存分配</el-button>
                </div>
              </div>

              <!-- 数据表格 -->
              <el-table :data="section.items" border size="default"
                :class="{ 'grouped-table': section.isGroup }"
                @selection-change="(val) => handleSectionSelection(section.key, val)">
                <el-table-column type="selection" width="50" :selectable="row => row.status === 'pending'" />
                <el-table-column prop="productNo" label="产品编号" min-width="120" />
                <el-table-column prop="productName" label="产品名称" min-width="140" />
                <el-table-column label="规格" min-width="80">
                  <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
                </el-table-column>
                <el-table-column prop="grossQty" label="总需求" width="100" align="right">
                  <template #default="{ row }">
                    <span v-if="row.grossQty != null">{{ row.grossQty }}</span>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
                <el-table-column width="120" align="right">
                  <template #header>
                    <span>最大可生产</span>
                    <el-tooltip content="单个生产需求使用全部库存时，最多可生产数量" placement="top">
                      <el-icon style="margin-left: 2px; vertical-align: middle; cursor: help; color: #909399"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </template>
                  <template #default="{ row }">
                    <span v-if="row.maxProducibleQty != null" :class="{ 'text-warning': row.materialConstrained }">
                      {{ row.maxProducibleQty }}
                    </span>
                    <span v-else style="color: #67c23a">充足</span>
                  </template>
                </el-table-column>
                <el-table-column width="160" align="center">
                  <template #header>
                    <span>实际生产数量</span>
                    <el-tooltip content="可根据实际情况自行调整，向下取整" placement="top">
                      <el-icon style="margin-left: 2px; vertical-align: middle; cursor: help; color: #909399"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </template>
                  <template #default="{ row }">
                    <template v-if="section.isGroup && row.status === 'pending'">
                      <el-input-number v-model="row._editActualQty" :min="0"
                        :max="row.maxProducibleQty ?? 999999" :precision="0" :step="1"
                        size="small" style="width: 130px" controls-position="right"
                        @change="onGroupActualQtyChange(section)" />
                    </template>
                    <template v-else>
                      <span style="font-size: 15px; font-weight: 600; color: #409eff">
                        {{ row.actualQty ?? 0 }}
                      </span>
                      <span class="text-muted" style="margin-left: 2px">{{ row.unit }}</span>
                    </template>
                  </template>
                </el-table-column>
                <el-table-column prop="requiredDate" label="需求日期" width="120">
                  <template #default="{ row }">
                    <span :class="urgencyClass(row.requiredDate)">{{ row.requiredDate }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="isSemiFinished" label="类型" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.isSemiFinished ? 'warning' : 'success'" size="small">
                      {{ row.isSemiFinished ? '组件' : '成品' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="来源订单" min-width="120">
                  <template #default="{ row }">
                    <template v-if="row.sourceOrders && row.sourceOrders.length">
                      <span v-for="(o, i) in row.sourceOrders" :key="o.id">
                        <el-button link type="primary" size="small" @click.stop="handleViewOrder(o.id)">{{ o.orderNo }}</el-button>
                        <span v-if="i < row.sourceOrders.length - 1" class="text-muted">, </span>
                      </span>
                    </template>
                    <span v-else-if="row.sourceOrderNos" class="text-muted">{{ row.sourceOrderNos }}</span>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="suggestionStatusType(row.status)" size="small">{{ suggestionStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <template v-if="row.status === 'pending'">
                      <el-button type="primary" link size="small" @click="handleEditProduction(row)">编辑</el-button>
                      <el-button type="success" link size="small" @click="handleTransferSingleProduction(row)">转车间计划</el-button>
                      <el-button type="danger" link size="small" @click="handleIgnore('production', row)">忽略</el-button>
                    </template>
                    <span v-else class="text-muted">{{ row.status === 'transferred' ? '已转计划' : '已忽略' }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <el-empty v-if="!productionLoading && !productionData.length" description="暂无生产需求" />
          </div>

          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="productionQuery.pageNum" v-model:page-size="productionQuery.pageSize"
              :total="productionTotal" :page-sizes="[10, 20, 50]" background
              layout="total, sizes, prev, pager, next, jumper" @size-change="loadProductionSuggestions" @current-change="loadProductionSuggestions" />
          </div>
        </el-tab-pane>

        <!-- Tab 5: 需求总览 -->
        <el-tab-pane label="需求总览" name="demands">
          <div class="tab-toolbar">
            <el-select v-model="demandRunId" placeholder="选择MRP运行批次" clearable style="width: 280px" @change="loadDemands">
              <el-option v-for="r in runData" :key="r.id" :label="`${r.runNo} (${r.runTime})`" :value="r.id" />
            </el-select>
            <el-select v-model="demandTypeFilter" placeholder="物料类型" clearable style="width: 120px; margin-left: 12px">
              <el-option label="全部" value="" />
              <el-option label="外购" value="purchase" />
              <el-option label="自制" value="manufacture" />
            </el-select>
          </div>

          <el-table :data="filteredDemandData" v-loading="demandLoading" border stripe>
            <el-table-column prop="itemType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="demandTypeTag(row.itemType)" size="small">{{ demandTypeText(row.itemType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="物料/产品" min-width="160">
              <template #default="{ row }">
                {{ row.materialName || row.productName || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="grossQty" label="毛需求" width="100" align="right" />
            <el-table-column prop="onHandQty" label="库存" width="100" align="right" />
            <el-table-column prop="inTransitQty" label="在途" width="100" align="right" />
            <el-table-column prop="inProductionQty" label="在制" width="100" align="right" />
            <el-table-column prop="netQty" label="净需求" width="100" align="right">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.netQty > 0 }">{{ row.netQty }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="adjustedQty" label="修正后" width="100" align="right" />
            <el-table-column label="状态" width="130" align="center">
              <template #default="{ row }">
                <template v-if="row.itemType === 'manufacture'">
                  <el-tag v-if="Number(row.netQty) <= 0 && Number(row.onHandQty) >= Number(row.grossQty)" type="info" size="small">在手库存已覆盖</el-tag>
                  <el-tag v-else-if="Number(row.netQty) <= 0 && Number(row.inProductionQty) > 0" type="warning" size="small">在制量已覆盖</el-tag>
                  <el-tag v-else-if="Number(row.netQty) <= 0" type="info" size="small">已覆盖</el-tag>
                  <el-tag v-else-if="row.adjustedQty != null" type="success" size="small">已生成建议</el-tag>
                  <el-tag v-else type="danger" size="small">原材料不足</el-tag>
                </template>
                <template v-else>
                  <el-tag v-if="Number(row.netQty) <= 0" type="info" size="small">库存已覆盖</el-tag>
                  <el-tag v-else type="success" size="small">已生成建议</el-tag>
                </template>
              </template>
            </el-table-column>
            <el-table-column prop="requiredDate" label="需求日期" width="120" />
          </el-table>
        </el-tab-pane>

      </el-tabs>
    </el-card>

    <!-- MRP运算配置弹窗 -->
    <el-dialog v-model="mrpConfigVisible" title="MRP运算配置" width="480px" :close-on-click-modal="false">
      <div style="margin-bottom: 16px; color: #606266; font-size: 14px">
        共 <b>{{ plannedOrders.length }}</b> 个待运算订单，请配置运算参数：
      </div>
      <el-form label-width="120px">
        <el-form-item label="考虑在途">
          <el-switch v-model="mrpConfig.considerInTransit" />
          <span class="config-hint">扣减采购订单未到货量 + 未转单的采购申请量</span>
        </el-form-item>
        <el-form-item label="考虑安全库存">
          <el-switch v-model="mrpConfig.considerSafetyStock" />
          <span class="config-hint">库存低于安全库存时，额外补充差额</span>
        </el-form-item>
        <el-form-item label="考虑替代料">
          <el-switch v-model="mrpConfig.considerSubstitute" />
          <span class="config-hint">用BOM替代料的库存抵扣主料需求</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mrpConfigVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRunMrp">开始运算</el-button>
      </template>
    </el-dialog>

    <!-- 编辑采购建议对话框 -->
    <el-dialog v-model="purchaseEditVisible" title="编辑采购建议" width="500px">
      <el-form v-if="editingPurchase" label-width="100px">
        <el-form-item label="物料">{{ editingPurchase.materialName }} ({{ editingPurchase.materialNo }})</el-form-item>
        <el-form-item label="规格">{{ $fmtSpec(editingPurchase.spec) }}</el-form-item>
        <el-form-item label="建议数量">
          <el-input-number v-model="editingPurchase.suggestQty" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="editingPurchase.supplierId" filterable clearable placeholder="选择供应商" style="width: 100%">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求日期">
          <el-date-picker v-model="editingPurchase.requiredDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="purchaseEditVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditPurchase">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑生产建议对话框 -->
    <el-dialog v-model="productionEditVisible" title="编辑生产建议" width="560px">
      <el-form v-if="editingProduction" label-width="110px">
        <el-form-item label="产品">{{ editingProduction.productName }} ({{ editingProduction.productNo }})</el-form-item>
        <el-form-item label="规格">{{ $fmtSpec(editingProduction.spec) }}</el-form-item>
        <el-form-item label="总需求">
          <span style="font-weight: 600">{{ editingProduction.grossQty ?? '-' }}</span>
          <span class="text-muted" style="margin-left: 4px">{{ editingProduction.unit }}</span>
        </el-form-item>
        <el-form-item label="净需求">
          <span style="font-weight: 600; color: #409eff">{{ editingProduction.netQty ?? '-' }}</span>
          <span class="text-muted" style="margin-left: 4px">{{ editingProduction.unit }}</span>
          <span class="text-muted" style="margin-left: 8px; font-size: 12px">
            （总需求 {{ editingProduction.grossQty }}
            <template v-if="editingProduction.onHandQty > 0"> − 在手库存 {{ editingProduction.onHandQty }}</template>
            <template v-if="editingProduction.inProductionQty > 0"> − 已在产 {{ editingProduction.inProductionQty }}</template>
            <template v-if="editingProduction.plannedQty > 0"> − 已转计划 {{ editingProduction.plannedQty }}</template>
            ）
          </span>
        </el-form-item>
        <el-form-item label="最大可生产">
          <span v-if="editingProduction.maxProducibleQty != null" :style="{ color: editingProduction.materialConstrained ? '#e6a23c' : '#67c23a', fontWeight: 600 }">
            {{ editingProduction.maxProducibleQty }}
          </span>
          <span v-else style="color: #67c23a; font-weight: 600">充足</span>
          <span class="text-muted" style="margin-left: 4px">{{ editingProduction.unit }}</span>
          <span class="text-muted" style="margin-left: 8px; font-size: 12px">（使用全部库存时）</span>
        </el-form-item>
        <el-form-item label="实际生产数量">
          <el-input-number v-model="editingProduction.actualQty" :min="0"
            :max="editingProduction.maxProducibleQty ?? 999999" :precision="0" :step="1" style="width: 200px" />
          <span class="text-muted" style="margin-left: 4px">{{ editingProduction.unit }}</span>
        </el-form-item>
        <el-alert v-if="editingProduction.competitionGroup" type="warning" :closable="false"
          style="margin-bottom: 12px">
          竞争组 {{ editingProduction.competitionGroup }}：修改实际生产数量后，组内其他需求的最大可生产数量将自动重算，确保所有需求合计不超过库存。
        </el-alert>
        <el-form-item label="需求日期">
          <el-date-picker v-model="editingProduction.requiredDate" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editingProduction.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productionEditVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEditProduction">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情抽屉 -->
    <el-drawer v-model="orderDetailVisible" title="订单详情" size="720px" destroy-on-close>
      <template v-if="orderDetailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ orderDetailData.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ orderDetailData.customerName }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ orderDetailData.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ orderDetailData.shippingAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ orderDetailData.settlementType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务员">{{ orderDetailData.salesmanName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总金额">
            {{ orderDetailData.totalAmount != null ? `¥${Number(orderDetailData.totalAmount).toFixed(2)}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="交货日期">{{ orderDetailData.deliveryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="salesOrderStatusType(orderDetailData.status)" size="small">{{ salesOrderStatusText(orderDetailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ orderDetailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin: 20px 0 12px">订单明细</h4>
        <el-table :data="orderDetailData.items || []" border stripe size="small">
          <el-table-column prop="productName" label="产品名称" min-width="140" />
          <el-table-column label="规格" min-width="80">
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column prop="unitPrice" label="单价" width="100" align="right">
            <template #default="{ row }">{{ row.unitPrice != null ? `¥${Number(row.unitPrice).toFixed(2)}` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="110" align="right">
            <template #default="{ row }">{{ row.amount != null ? `¥${Number(row.amount).toFixed(2)}` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="deliveryDate" label="交货日期" width="120" />
        </el-table>
      </template>
      <div v-else v-loading="orderDetailLoading" style="min-height: 200px" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import {
  runMrp, getMrpRunPage, getMrpRunDetail, getPlannedOrders as fetchPlannedOrders,
  getPurchaseSuggestionPage, updatePurchaseSuggestion, confirmPurchaseSuggestion, batchConfirmPurchaseSuggestions,
  getProductionSuggestionPage, updateProductionSuggestion,
  ignoreSuggestion, createPlanFromSuggestions,
  getMrpStatistics, exportPurchaseSuggestions, exportProductionSuggestions,
  removeFromDemandPool
} from '@/api/mrp'
import { getSupplierSelect } from '@/api/master'
import { getSalesOrderDetail } from '@/api/order'

const activeTab = ref('orders')

// ==================== 统计 ====================
const stats = ref({})

async function loadStatistics() {
  try {
    const { data } = await getMrpStatistics()
    stats.value = data || {}
  } catch { stats.value = {} }
}

function switchTab(tab, status) {
  activeTab.value = tab
  if (tab === 'purchase' && status) { purchaseQuery.value.status = status }
  if (tab === 'production' && status) { productionQuery.value.status = status }
  handleTabChange(tab)
}

// ==================== 待运算订单 ====================
const plannedOrders = ref([])
const ordersLoading = ref(false)

async function loadPlannedOrders() {
  ordersLoading.value = true
  try {
    const { data } = await fetchPlannedOrders()
    plannedOrders.value = data || []
  } finally { ordersLoading.value = false }
}

// ==================== 从需求池移除 ====================
async function handleRemoveFromPool(row) {
  await ElMessageBox.confirm(`确认将订单"${row.orderNo}"从MRP需求池中移除？移除后该订单将不再参与MRP运算。`, '移除确认', { type: 'warning' })
  try {
    await removeFromDemandPool(row.id)
    ElMessage.success('已从需求池移除')
    loadPlannedOrders()
    loadStatistics()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '移除失败')
  }
}

// ==================== 运行MRP ====================
const mrpConfigVisible = ref(false)
const mrpConfig = ref({
  considerInTransit: true,
  considerSafetyStock: false,
  considerSubstitute: false
})

function handleRunMrp() {
  if (!plannedOrders.value.length) {
    ElMessage.warning('暂无待运算订单')
    return
  }
  mrpConfigVisible.value = true
}

async function submitRunMrp() {
  try {
    const orderIds = plannedOrders.value.map(o => o.id)
    await runMrp({
      orderIds,
      considerInTransit: mrpConfig.value.considerInTransit,
      considerSafetyStock: mrpConfig.value.considerSafetyStock,
      considerSubstitute: mrpConfig.value.considerSubstitute
    })
    mrpConfigVisible.value = false
    ElMessage.success('MRP运算完成，请查看采购需求和生产需求')
    loadPlannedOrders()
    loadRuns()
    loadStatistics()
    activeTab.value = 'purchase'
    loadPurchaseSuggestions()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || 'MRP运算失败')
  }
}

// ==================== MRP运行记录 ====================
const runData = ref([])
const runLoading = ref(false)
const runTotal = ref(0)
const runQuery = ref({ keyword: '', pageNum: 1, pageSize: 20 })

async function loadRuns() {
  runLoading.value = true
  try {
    const { data } = await getMrpRunPage(runQuery.value)
    runData.value = data.records
    runTotal.value = data.total
  } finally { runLoading.value = false }
}

function handleRunClick(row) {
  demandRunId.value = row.id
  activeTab.value = 'demands'
  loadDemands()
}

// ==================== 采购建议 ====================
const purchaseData = ref([])
const purchaseLoading = ref(false)
const purchaseTotal = ref(0)
const purchaseQuery = ref({ status: 'pending', pageNum: 1, pageSize: 20 })
const purchaseSelection = ref([])
const purchaseDateRange = ref(null)

async function loadPurchaseSuggestions() {
  purchaseLoading.value = true
  try {
    const params = { ...purchaseQuery.value }
    if (purchaseDateRange.value && purchaseDateRange.value.length === 2) {
      params.dateFrom = purchaseDateRange.value[0]
      params.dateTo = purchaseDateRange.value[1]
    }
    const { data } = await getPurchaseSuggestionPage(params)
    purchaseData.value = data.records
    purchaseTotal.value = data.total
  } finally { purchaseLoading.value = false }
}

function handlePurchaseSelectionChange(val) { purchaseSelection.value = val }

async function handleConfirmPurchase(row) {
  await ElMessageBox.confirm(`确认将"${row.materialName}"的采购建议(${row.suggestQty})转为采购申请?`, '确认转换')
  await confirmPurchaseSuggestion(row.id)
  ElMessage.success('已转为采购申请')
  loadPurchaseSuggestions()
  loadStatistics()
}

async function handleBatchConfirmPurchase() {
  await ElMessageBox.confirm(`确认将选中的 ${purchaseSelection.value.length} 条采购建议转为采购申请?`, '批量确认')
  await batchConfirmPurchaseSuggestions(purchaseSelection.value.map(r => r.id))
  ElMessage.success('批量转换成功')
  loadPurchaseSuggestions()
  loadStatistics()
}

// ==================== 编辑采购建议 ====================
const purchaseEditVisible = ref(false)
const editingPurchase = ref(null)
const supplierOptions = ref([])

async function loadSupplierOptions() {
  try {
    const { data } = await getSupplierSelect()
    supplierOptions.value = data || []
  } catch { supplierOptions.value = [] }
}

function handleEditPurchase(row) {
  editingPurchase.value = { ...row }
  purchaseEditVisible.value = true
  if (!supplierOptions.value.length) loadSupplierOptions()
}

async function submitEditPurchase() {
  const { id, suggestQty, supplierId, requiredDate } = editingPurchase.value
  await updatePurchaseSuggestion(id, { suggestQty, supplierId, requiredDate })
  ElMessage.success('已更新')
  purchaseEditVisible.value = false
  loadPurchaseSuggestions()
}

// ==================== 导出 ====================
async function handleExportPurchase() {
  try {
    const params = { status: purchaseQuery.value.status }
    if (purchaseDateRange.value && purchaseDateRange.value.length === 2) {
      params.dateFrom = purchaseDateRange.value[0]
      params.dateTo = purchaseDateRange.value[1]
    }
    const res = await exportPurchaseSuggestions(params)
    downloadBlob(res, '采购建议.xlsx')
  } catch { ElMessage.error('导出失败') }
}

async function handleExportProduction() {
  try {
    const params = { status: productionQuery.value.status }
    if (productionDateRange.value && productionDateRange.value.length === 2) {
      params.dateFrom = productionDateRange.value[0]
      params.dateTo = productionDateRange.value[1]
    }
    const res = await exportProductionSuggestions(params)
    downloadBlob(res, '生产建议.xlsx')
  } catch { ElMessage.error('导出失败') }
}

function downloadBlob(res, filename) {
  const blob = new Blob([res.data || res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  window.URL.revokeObjectURL(url)
}

// ==================== 生产建议 ====================
const productionData = ref([])
const productionLoading = ref(false)
const productionTotal = ref(0)
const productionQuery = ref({ status: 'pending', pageNum: 1, pageSize: 20 })
const productionSelection = ref([])
const productionDateRange = ref(null)

// 按竞争物料分组：同一个竞争物料下的多个产品放在一个 section
const sectionSelections = ref({})

const productionSections = computed(() => {
  const data = productionData.value
  if (!data.length) return []

  // 区分有竞争和无竞争的行（contestedMaterials 是数组）
  const competingItems = data.filter(item => item.contestedMaterials && item.contestedMaterials.length)
  const normalItems = data.filter(item => !item.contestedMaterials || !item.contestedMaterials.length)

  // 按每个竞争物料分组
  const materialGroups = new Map()
  competingItems.forEach(item => {
    const materials = Array.isArray(item.contestedMaterials) ? item.contestedMaterials : []
    materials.forEach(mat => {
      if (!mat) return
      if (!materialGroups.has(mat)) materialGroups.set(mat, [])
      if (!materialGroups.get(mat).find(existing => existing.id === item.id)) {
        materialGroups.get(mat).push(item)
      }
    })
  })

  const colorClasses = ['group-color-red', 'group-color-orange', 'group-color-green', 'group-color-blue', 'group-color-gray']
  let colorIdx = 0
  const sections = []
  const itemsInGroups = new Set()

  // 构建竞争组 section（仅多项的组才是竞争）
  materialGroups.forEach((items, material) => {
    if (items.length < 2) return
    items.forEach(i => itemsInGroups.add(i.id))

    const activeItems = items.filter(i => i.status === 'pending')
    let remainingWarning = ''
    if (activeItems.length === 1) {
      const single = activeItems[0]
      const allMats = Array.isArray(single.contestedMaterials) ? single.contestedMaterials : []
      const otherMats = allMats.filter(m => m && m !== material)
      if (otherMats.length > 0) {
        remainingWarning = `"${single.productName}"还存在其他竞争关系（竞争物料: ${otherMats.join(', ')}），请注意处理`
      }
    }

    // 从第一个有库存信息的item提取该物料的库存
    let stockInfo = null
    for (const item of items) {
      const stocks = item.contestedMaterialStocks
      if (stocks && stocks[material]) {
        stockInfo = stocks[material]
        break
      }
    }

    sections.push({
      key: `group-${material}`,
      isGroup: true,
      material,
      stockInfo,
      activeCount: activeItems.length,
      remainingWarning,
      colorClass: colorClasses[colorIdx % colorClasses.length],
      items
    })
    colorIdx++
  })

  // 竞争物料只有单项的（不构成竞争），归入普通区
  const orphans = competingItems.filter(i => !itemsInGroups.has(i.id))
  const allNormal = [...normalItems, ...orphans]

  if (allNormal.length) {
    sections.push({
      key: 'normal',
      isGroup: false,
      material: '',
      activeCount: 0,
      remainingWarning: '',
      colorClass: '',
      items: allNormal
    })
  }

  return sections
})

function handleSectionSelection(sectionKey, val) {
  sectionSelections.value[sectionKey] = val
  // 聚合所有 section 的选择（按 id 去重，同一需求可能出现在多个竞争组）
  const allSelected = new Map()
  Object.values(sectionSelections.value).forEach(rows => {
    rows.forEach(row => allSelected.set(row.id, row))
  })
  productionSelection.value = Array.from(allSelected.values())
}

async function loadProductionSuggestions() {
  productionLoading.value = true
  try {
    const params = { ...productionQuery.value }
    if (productionDateRange.value && productionDateRange.value.length === 2) {
      params.dateFrom = productionDateRange.value[0]
      params.dateTo = productionDateRange.value[1]
    }
    const { data } = await getProductionSuggestionPage(params)
    // 为每行初始化行内编辑字段
    ;(data.records || []).forEach(r => {
      r._editActualQty = r.actualQty ?? 0
      r._origActualQty = r.actualQty ?? 0
    })
    productionData.value = data.records
    productionTotal.value = data.total
    sectionSelections.value = {}
    groupSaving.value = {}
  } finally { productionLoading.value = false }
}

// ==================== 竞争组分配 ====================
const groupSaving = ref({})

/** 计算竞争组的已分配和剩余物料 */
function calcGroupRemaining(section) {
  const stock = Number(section.stockInfo?.stock) || 0
  let consumed = 0
  const pendingItems = section.items.filter(i => i.status === 'pending')
  for (const item of pendingItems) {
    const actualQty = item._editActualQty ?? 0
    // 该产品对该竞争物料的单位消耗
    const perUnit = item.perUnitMaterialNeeds?.[section.material] ?? 0
    consumed += actualQty * perUnit
  }
  consumed = Math.round(consumed * 10000) / 10000
  return { stock, consumed, remaining: Math.round((stock - consumed) * 10000) / 10000 }
}

/** 行内 actualQty 变更回调 */
function onGroupActualQtyChange(section) {
  // 仅做响应式触发，calcGroupRemaining 会自动重算
}

/** 平均分配 / 按需求比例分配 */
function distributeGroup(section, mode) {
  const stock = Number(section.stockInfo?.stock) || 0
  const pendingItems = section.items.filter(i => i.status === 'pending')
  if (!pendingItems.length) return

  if (mode === 'average') {
    // 每个需求均分库存，各自按自己的单位消耗折算，向下取整
    // 算法：假设每个需求分配 qty_i 个产品，且 sum(qty_i * perUnit_i) <= stock
    // 平均分配：每个需求分到 stock / N 份物料，再除以各自 perUnit 得到产品数量
    const sharePerItem = stock / pendingItems.length
    for (const item of pendingItems) {
      const perUnit = item.perUnitMaterialNeeds?.[section.material] || 1
      const maxProd = item.maxProducibleQty ?? 999999
      item._editActualQty = Math.min(Math.floor(sharePerItem / perUnit), maxProd)
    }
  } else if (mode === 'proportional') {
    // 按总需求 (grossQty) 比例分配
    const totalGross = pendingItems.reduce((s, i) => s + (Number(i.grossQty) || 0), 0)
    if (totalGross <= 0) {
      // 退化为平均分配
      distributeGroup(section, 'average')
      return
    }
    for (const item of pendingItems) {
      const ratio = (Number(item.grossQty) || 0) / totalGross
      const materialShare = stock * ratio
      const perUnit = item.perUnitMaterialNeeds?.[section.material] || 1
      const maxProd = item.maxProducibleQty ?? 999999
      item._editActualQty = Math.min(Math.floor(materialShare / perUnit), maxProd)
    }
  }
}

/** 重置组内所有为 0 */
function resetGroupActualQty(section) {
  section.items.filter(i => i.status === 'pending').forEach(i => { i._editActualQty = 0 })
}

/** 检查组内是否有变更 */
function hasGroupChanges(section) {
  return section.items.some(i => i.status === 'pending' && i._editActualQty !== i._origActualQty)
}

/** 批量保存竞争组内所有变更的 actualQty */
async function saveGroupActualQty(section) {
  // 先在前端校验物料不超库存
  const info = calcGroupRemaining(section)
  if (info.remaining < 0) {
    ElMessage.error(`物料[${section.material}]分配超出库存: 已分配 ${info.consumed}, 库存 ${info.stock}`)
    return
  }
  groupSaving.value[section.key] = true
  try {
    const changedItems = section.items.filter(i => i.status === 'pending' && i._editActualQty !== i._origActualQty)
    for (const item of changedItems) {
      await updateProductionSuggestion(item.id, { actualQty: item._editActualQty })
      item.actualQty = item._editActualQty
      item._origActualQty = item._editActualQty
    }
    ElMessage.success(`竞争组[${section.material}]分配已保存`)
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '保存失败')
    loadProductionSuggestions()
  } finally {
    groupSaving.value[section.key] = false
  }
}

async function handleUpdateSemiHandle(row) {
  await updateProductionSuggestion(row.id, { semiHandleType: row.semiHandleType })
  ElMessage.success('已更新')
}

async function handleCreatePlanFromSuggestions() {
  await ElMessageBox.confirm(`确认将选中的 ${productionSelection.value.length} 条生产建议转入生产计划?`, '转入生产计划')
  const { data } = await createPlanFromSuggestions({
    suggestionIds: productionSelection.value.map(r => r.id)
  })
  ElMessage.success(`已创建 ${data.count} 个生产计划`)
  loadProductionSuggestions()
  loadStatistics()
}

// ==================== 编辑生产建议 ====================
const productionEditVisible = ref(false)
const editingProduction = ref(null)

function handleEditProduction(row) {
  editingProduction.value = { ...row }
  productionEditVisible.value = true
}

async function submitEditProduction() {
  const { id, suggestQty, actualQty, requiredDate, remark } = editingProduction.value
  const flooredQty = actualQty != null ? Math.floor(actualQty) : null
  await updateProductionSuggestion(id, { suggestQty, actualQty: flooredQty, requiredDate, remark })
  productionEditVisible.value = false
  ElMessage.success('已更新')
  loadProductionSuggestions()
}

// ==================== 单条转车间计划 ====================
async function handleTransferSingleProduction(row) {
  await ElMessageBox.confirm(`确认将"${row.productName}"的生产建议(${row.suggestQty})转入车间计划?`, '转入车间计划')
  const { data } = await createPlanFromSuggestions({
    suggestionIds: [row.id]
  })
  ElMessage.success(`已创建生产计划`)
  loadProductionSuggestions()
  loadStatistics()
}

// ==================== 忽略建议 ====================
async function handleIgnore(type, row) {
  const name = type === 'purchase' ? row.materialName : row.productName
  await ElMessageBox.confirm(`确认忽略"${name}"的建议?`, '忽略建议')
  await ignoreSuggestion(type, row.id)
  ElMessage.success('已忽略')
  if (type === 'purchase') loadPurchaseSuggestions()
  else loadProductionSuggestions()
  loadStatistics()
}

// ==================== 查看来源订单详情 ====================
const orderDetailVisible = ref(false)
const orderDetailData = ref(null)
const orderDetailLoading = ref(false)

async function handleViewOrder(orderId) {
  orderDetailVisible.value = true
  orderDetailData.value = null
  orderDetailLoading.value = true
  try {
    const { data } = await getSalesOrderDetail(orderId)
    orderDetailData.value = data
  } catch {
    ElMessage.error('获取订单详情失败')
    orderDetailVisible.value = false
  } finally { orderDetailLoading.value = false }
}

// ==================== 需求总览 ====================
const demandData = ref([])
const demandLoading = ref(false)
const demandRunId = ref(null)
const demandTypeFilter = ref('')

async function loadDemands() {
  if (!demandRunId.value) { demandData.value = []; return }
  demandLoading.value = true
  try {
    const { data } = await getMrpRunDetail(demandRunId.value)
    demandData.value = data.demands || []
  } finally { demandLoading.value = false }
}

const filteredDemandData = computed(() => {
  if (!demandTypeFilter.value) return demandData.value
  return demandData.value.filter(d => d.itemType === demandTypeFilter.value)
})

// ==================== Tab切换 ====================
function handleTabChange(tab) {
  if (tab === 'orders') loadPlannedOrders()
  else if (tab === 'runs') loadRuns()
  else if (tab === 'purchase') loadPurchaseSuggestions()
  else if (tab === 'production') loadProductionSuggestions()
  else if (tab === 'demands') {
    if (!runData.value.length) loadRuns()
    if (!demandRunId.value && runData.value.length) {
      demandRunId.value = runData.value[0].id
      loadDemands()
    }
  }
}

// ==================== 交期紧急度 ====================
function urgencyClass(dateStr) {
  if (!dateStr) return ''
  const today = new Date()
  const target = new Date(dateStr)
  const diff = Math.ceil((target - today) / (1000 * 60 * 60 * 24))
  if (diff <= 3) return 'urgency-critical'
  if (diff <= 7) return 'urgency-warning'
  return ''
}

function purchaseRowClassName({ row }) {
  if (row.status !== 'pending') return ''
  return urgencyClass(row.requiredDate) ? 'urgency-row' : ''
}

// ==================== 状态辅助 ====================
const salesOrderStatusMap = { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', mrp_planned: '已转计划',
  outsourcing: '外协中', in_production: '生产中', partial_shipped: '部分发货', shipped: '已发货',
  completed: '已完成', settled: '已结算', cancelled: '已取消', rejected: '已驳回', voided: '已作废' }
function salesOrderStatusText(s) { return salesOrderStatusMap[s] || s || '-' }
function salesOrderStatusType(s) {
  const map = { draft: 'info', pending_approval: 'warning', confirmed: '', mrp_planned: 'primary',
    outsourcing: 'warning', in_production: '', partial_shipped: 'success', shipped: 'success',
    completed: 'success', settled: 'success', cancelled: 'danger', rejected: 'danger', voided: 'danger' }
  return map[s] || 'info'
}
function mrpStatusType(s) { return { running: 'warning', completed: 'success', failed: 'danger' }[s] || 'info' }
function mrpStatusText(s) { return { running: '运行中', completed: '已完成', failed: '失败' }[s] || s }
function suggestionStatusType(s) { return { pending: 'warning', confirmed: 'primary', transferred: 'success', ignored: 'info' }[s] || 'info' }
function suggestionStatusText(s) { return { pending: '待确认', confirmed: '已确认', transferred: '已转换', ignored: '已忽略' }[s] || s }
function demandTypeTag(t) { return { purchase: '', manufacture: 'success', raw: '', semi_finished: 'warning', finished: 'success' }[t] || 'info' }
function demandTypeText(t) { return { purchase: '外购', manufacture: '自制', raw: '原料', semi_finished: '半成品', finished: '成品' }[t] || t }

onMounted(() => {
  loadPlannedOrders()
  loadStatistics()
  loadRuns()
})
</script>

<style scoped>
.page-container { padding: 16px; }
.table-card { margin-bottom: 16px; }
.tab-toolbar { display: flex; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 4px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
.text-muted { color: #999; font-size: 12px; }
.text-danger { color: #f56c6c; font-weight: 600; }
.order-item-line { font-size: 13px; line-height: 1.6; }
.tab-badge { margin-left: 6px; }

.stat-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}
.stat-card:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15); }
.stat-card.active { border-color: #409eff; background: #ecf5ff; }
.stat-card .stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-card .stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.stat-card.highlight-warning .stat-value { color: #e6a23c; }

.urgency-critical { color: #f56c6c; font-weight: 600; }
.urgency-warning { color: #e6a23c; font-weight: 600; }
.text-warning { color: #e6a23c; font-weight: 600; }
:deep(.urgency-row) { background-color: #fef0f0 !important; }
.config-hint { color: #909399; font-size: 12px; margin-left: 12px; }

/* 竞争组分区样式 */
.production-section { margin-bottom: 16px; }
.production-section:last-child { margin-bottom: 0; }

.group-header {
  padding: 10px 16px;
  border-radius: 6px 6px 0 0;
  border: 1px solid #e4e7ed;
  border-bottom: none;
}
.group-header-main { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.group-header-icon { font-size: 16px; }
.group-header-title { font-weight: 600; font-size: 14px; color: #303133; }
.group-header-hint { color: #909399; font-size: 12px; margin-left: auto; }
.group-header-actions { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.group-warning { margin-top: 6px; color: #e6a23c; font-size: 12px; padding-left: 24px; }

/* 竞争组颜色主题 */
.group-color-red { background: #fef0f0; border-color: #f56c6c; }
.group-color-red .group-header-title { color: #f56c6c; }
.group-color-orange { background: #fdf6ec; border-color: #e6a23c; }
.group-color-orange .group-header-title { color: #e6a23c; }
.group-color-green { background: #f0f9eb; border-color: #67c23a; }
.group-color-green .group-header-title { color: #67c23a; }
.group-color-blue { background: #ecf5ff; border-color: #409eff; }
.group-color-blue .group-header-title { color: #409eff; }
.group-color-gray { background: #f4f4f5; border-color: #909399; }
.group-color-gray .group-header-title { color: #909399; }

/* 竞争组表格：移除上方圆角以和 header 衔接 */
.grouped-table { border-radius: 0 0 6px 6px; }
</style>
