<template>
  <div class="page-container">
    <el-card class="table-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Tab 1: 成本中心 (FC01) -->
        <el-tab-pane label="成本中心" name="cost">
          <el-collapse v-model="costActiveCollapse">
            <!-- 工单成本列表 -->
            <el-collapse-item title="工单成本列表" name="woCost">
              <el-table :data="woCostData" v-loading="woCostLoading" border stripe>
                <el-table-column prop="workOrderNo" label="工单号" min-width="140">
                  <template #default="{ row }">
                    <el-button type="primary" link @click="handleViewCostDetail(row)">{{ row.workOrderNo }}</el-button>
                  </template>
                </el-table-column>
                <el-table-column prop="productName" label="产品" min-width="140" />
                <el-table-column prop="planQty" label="计划数" width="90" align="center" />
                <el-table-column prop="materialCost" label="材料成本" width="110" align="right">
                  <template #default="{ row }">{{ Number(row.materialCost || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="laborCost" label="人工成本" width="110" align="right">
                  <template #default="{ row }">{{ Number(row.laborCost || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="outsourceCost" label="外协成本" width="110" align="right">
                  <template #default="{ row }">{{ Number(row.outsourceCost || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="reworkCost" label="返工成本" width="110" align="right">
                  <template #default="{ row }">{{ Number(row.reworkCost || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="scrapLoss" label="报废损失" width="110" align="right">
                  <template #default="{ row }">{{ Number(row.scrapLoss || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="overhead" label="制造费用" width="110" align="right">
                  <template #default="{ row }">{{ Number(row.overhead || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="totalCost" label="总成本" width="120" align="right">
                  <template #default="{ row }">
                    <span style="font-weight: bold;">{{ Number(row.totalCost || 0).toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="budget" label="预算" width="120" align="right">
                  <template #default="{ row }">{{ Number(row.budget || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="variance" label="差异" width="120" align="right">
                  <template #default="{ row }">
                    <span :style="{ color: Number(row.variance || 0) > 0 ? '#f56c6c' : '', fontWeight: 600 }">
                      {{ Number(row.variance || 0).toFixed(2) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="handleViewCostDetail(row)">查看成本明细</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <div class="pagination-wrapper">
                <el-pagination
                  v-model:current-page="woCostQuery.pageNum"
                  v-model:page-size="woCostQuery.pageSize"
                  :total="woCostTotal"
                  :page-sizes="[10, 20, 50, 100]"
                  background
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="loadWoCosts"
                  @current-change="loadWoCosts"
                />
              </div>
            </el-collapse-item>

            <!-- 订单毛利列表 -->
            <el-collapse-item title="订单毛利列表" name="orderProfit">
              <el-table :data="orderProfitData" v-loading="orderProfitLoading" border stripe>
                <el-table-column prop="orderNo" label="订单号" min-width="140" />
                <el-table-column prop="customerName" label="客户" min-width="140" />
                <el-table-column prop="amount" label="金额" width="120" align="right">
                  <template #default="{ row }">{{ Number(row.amount || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="totalCost" label="总成本" width="120" align="right">
                  <template #default="{ row }">{{ Number(row.totalCost || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="profit" label="毛利" width="120" align="right">
                  <template #default="{ row }">
                    <span :style="{ color: Number(row.profit || 0) < 0 ? '#f56c6c' : '#67c23a', fontWeight: 600 }">
                      {{ Number(row.profit || 0).toFixed(2) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="marginRate" label="毛利率%" width="110" align="center">
                  <template #default="{ row }">
                    <span :style="{ color: Number(row.marginRate || 0) < 10 ? '#f56c6c' : '', fontWeight: 600 }">
                      {{ Number(row.marginRate || 0).toFixed(1) }}%
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="orderProfitStatusType(row.status)" size="small">
                      {{ orderProfitStatusText(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>

              <div class="pagination-wrapper">
                <el-pagination
                  v-model:current-page="orderProfitQuery.pageNum"
                  v-model:page-size="orderProfitQuery.pageSize"
                  :total="orderProfitTotal"
                  :page-sizes="[10, 20, 50, 100]"
                  background
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="loadOrderProfits"
                  @current-change="loadOrderProfits"
                />
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>

        <!-- Tab 2: 应收管理 -->
        <el-tab-pane label="应收管理" name="receivable">
          <!-- 搜索栏: 到期日范围 / 客户名称 / 状态 -->
          <el-form :model="receivableQuery" inline style="margin-bottom: 8px">
            <el-form-item label="时间">
              <el-date-picker v-model="receivableAsOfDate" type="date"
                placeholder="截至日期 (不选=今天)" value-format="YYYY-MM-DD" style="width: 200px" />
            </el-form-item>
            <el-form-item label="客户名称">
              <el-input v-model="receivableQuery.customerName" placeholder="请输入" clearable
                style="width: 150px" @keyup.enter="handleReceivableSearch" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="receivableQuery.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="待收款" value="pending" />
                <el-option label="部分收款" value="partial_received" />
                <el-option label="已收款" value="received" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleReceivableSearch">搜索</el-button>
              <el-button @click="handleReceivableReset">重置</el-button>
              <el-button type="success" plain @click="handleReceivableExport">导出明细</el-button>
            </el-form-item>
          </el-form>
          <!-- 当前结果合计 -->
          <el-row :gutter="12" style="margin-bottom: 8px">
            <el-col :span="8">
              <div class="stat-mini">
                <span class="lbl">应收合计</span>
                <span class="val" style="color:#409eff">¥{{ receivableSum.amount.toFixed(2) }}</span>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-mini">
                <span class="lbl">已收合计</span>
                <span class="val" style="color:#67c23a">¥{{ receivableSum.received.toFixed(2) }}</span>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-mini">
                <span class="lbl">未收合计</span>
                <span class="val" style="color:#f56c6c">¥{{ receivableSum.unreceived.toFixed(2) }}</span>
              </div>
            </el-col>
          </el-row>
          <el-table :data="receivableData" v-loading="receivableLoading" border stripe>
            <el-table-column prop="receivableNo" label="应收编号" min-width="140" />
            <el-table-column prop="customerName" label="客户名称" min-width="140" />
            <el-table-column prop="amount" label="应收金额" width="120" align="right">
              <template #default="{ row }">
                {{ row.amount?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="receivedAmount" label="已收金额" width="120" align="right">
              <template #default="{ row }">
                {{ row.receivedAmount?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="unreceivedAmount" label="未收金额" width="120" align="right">
              <template #default="{ row }">
                <span :style="{ color: row.unreceivedAmount > 0 ? '#f56c6c' : '' }">
                  {{ row.unreceivedAmount?.toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="120" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="financeStatusType(row.status)" size="small">
                  {{ financeStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'COMPLETED'"
                  type="primary"
                  link
                  size="small"
                  @click="handleReceivePayment(row)"
                >收款</el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="receivableQuery.pageNum"
              v-model:page-size="receivableQuery.pageSize"
              :total="receivableTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadReceivables"
              @current-change="loadReceivables"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 3: 应付管理 -->
        <el-tab-pane label="应付管理" name="payable">
          <el-table :data="payableData" v-loading="payableLoading" border stripe>
            <el-table-column prop="payableNo" label="应付编号" min-width="140" />
            <el-table-column prop="supplierName" label="供应商名称" min-width="140" />
            <el-table-column prop="amount" label="应付金额" width="120" align="right">
              <template #default="{ row }">
                {{ row.amount?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="paidAmount" label="已付金额" width="120" align="right">
              <template #default="{ row }">
                {{ row.paidAmount?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="unpaidAmount" label="未付金额" width="120" align="right">
              <template #default="{ row }">
                <span :style="{ color: row.unpaidAmount > 0 ? '#f56c6c' : '' }">
                  {{ row.unpaidAmount?.toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="到期日" width="120" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="financeStatusType(row.status)" size="small">
                  {{ financeStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'COMPLETED'"
                  type="primary"
                  link
                  size="small"
                  @click="handleMakePayment(row)"
                >付款</el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="payableQuery.pageNum"
              v-model:page-size="payableQuery.pageSize"
              :total="payableTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadPayables"
              @current-change="loadPayables"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 4: 收付款 -->
        <el-tab-pane label="收付款" name="payment">
          <el-tabs v-model="paymentSubTab" type="card">
            <!-- 收款记录 -->
            <el-tab-pane label="收款记录" name="receiveRecord">
              <el-table :data="receiveRecordData" v-loading="receiveRecordLoading" border stripe>
                <el-table-column prop="paymentNo" label="编号" min-width="140" />
                <el-table-column prop="receivableNo" label="关联应收" min-width="140" />
                <el-table-column prop="customerName" label="客户" min-width="140" />
                <el-table-column prop="amount" label="金额" width="120" align="right">
                  <template #default="{ row }">{{ Number(row.amount || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="paymentDate" label="收款日期" width="120" />
                <el-table-column prop="paymentMethod" label="方式" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag size="small">{{ paymentMethodText(row.paymentMethod) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="operatorName" label="操作人" width="100" />
              </el-table>

              <div class="pagination-wrapper">
                <el-pagination
                  v-model:current-page="receiveRecordQuery.pageNum"
                  v-model:page-size="receiveRecordQuery.pageSize"
                  :total="receiveRecordTotal"
                  :page-sizes="[10, 20, 50, 100]"
                  background
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="loadReceiveRecords"
                  @current-change="loadReceiveRecords"
                />
              </div>
            </el-tab-pane>

            <!-- 付款记录 -->
            <el-tab-pane label="付款记录" name="payRecord">
              <el-table :data="payRecordData" v-loading="payRecordLoading" border stripe>
                <el-table-column prop="paymentNo" label="编号" min-width="140" />
                <el-table-column prop="payableNo" label="关联应付" min-width="140" />
                <el-table-column prop="supplierName" label="供应商" min-width="140" />
                <el-table-column prop="amount" label="金额" width="120" align="right">
                  <template #default="{ row }">{{ Number(row.amount || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="paymentDate" label="付款日期" width="120" />
                <el-table-column prop="paymentMethod" label="方式" width="110" align="center">
                  <template #default="{ row }">
                    <el-tag size="small">{{ paymentMethodText(row.paymentMethod) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="operatorName" label="操作人" width="100" />
              </el-table>

              <div class="pagination-wrapper">
                <el-pagination
                  v-model:current-page="payRecordQuery.pageNum"
                  v-model:page-size="payRecordQuery.pageSize"
                  :total="payRecordTotal"
                  :page-sizes="[10, 20, 50, 100]"
                  background
                  layout="total, sizes, prev, pager, next, jumper"
                  @size-change="loadPayRecords"
                  @current-change="loadPayRecords"
                />
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <!-- Tab 5: 工资管理 -->
        <el-tab-pane label="工资管理" name="wage">
          <div class="tab-toolbar">
            <el-date-picker
              v-model="wageQuery.month"
              type="month"
              placeholder="请选择月份"
              value-format="YYYY-MM"
              style="width: 180px; margin-right: 12px;"
              @change="handleWageMonthChange"
            />
            <el-button type="success" :loading="wageGenerating" @click="handleGenerateWages">生成月度工资</el-button>
          </div>

          <el-table :data="wageData" v-loading="wageLoading" border stripe show-summary>
            <el-table-column prop="employeeName" label="姓名" min-width="100" />
            <el-table-column prop="month" label="月份" width="100" align="center" />
            <el-table-column prop="personalPieceWage" label="个人计件工资" width="130" align="right">
              <template #default="{ row }">
                {{ row.personalPieceWage?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="teamAllocatedWage" label="团队分配工资" width="130" align="right">
              <template #default="{ row }">
                {{ row.teamAllocatedWage?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="bonus" label="奖金" width="100" align="right">
              <template #default="{ row }">
                {{ row.bonus?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="deduction" label="扣款" width="100" align="right">
              <template #default="{ row }">
                {{ row.deduction?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="totalWage" label="合计工资" width="120" align="right">
              <template #default="{ row }">
                <span style="font-weight: bold;">{{ row.totalWage?.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="center">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openWageDetail(row)">查看明细</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="wageQuery.pageNum"
              v-model:page-size="wageQuery.pageSize"
              :total="wageTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadWages"
              @current-change="loadWages"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 6: 利润分析 -->
        <el-tab-pane label="利润分析" name="profit">
          <div class="tab-toolbar">
            <el-select v-model="profitQuery.dimension" placeholder="分析维度" style="width: 140px; margin-right: 12px;" @change="loadProfitAnalysis">
              <el-option label="按月份" value="month" />
              <el-option label="按客户" value="customer" />
              <el-option label="按产品" value="product" />
            </el-select>
            <el-date-picker v-model="profitDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 280px; margin-right: 12px;" @change="handleProfitDateChange" />
            <el-button type="primary" @click="loadProfitAnalysis">查询</el-button>
          </div>

          <!-- 汇总卡片 -->
          <el-row :gutter="16" style="margin-bottom: 16px" v-if="profitSummary">
            <el-col :span="6">
              <el-card shadow="hover" class="profit-card">
                <div class="profit-label">总营收</div>
                <div class="profit-value" style="color: #409eff">¥{{ Number(profitSummary.totalRevenue || 0).toFixed(2) }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="profit-card">
                <div class="profit-label">总成本</div>
                <div class="profit-value" style="color: #e6a23c">¥{{ Number(profitSummary.totalCost || 0).toFixed(2) }}</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="profit-card">
                <div class="profit-label">总利润</div>
                <div class="profit-value" :style="{ color: Number(profitSummary.totalProfit || 0) >= 0 ? '#67c23a' : '#f56c6c' }">
                  ¥{{ Number(profitSummary.totalProfit || 0).toFixed(2) }}
                </div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card shadow="hover" class="profit-card">
                <div class="profit-label">毛利率</div>
                <div class="profit-value" :style="{ color: Number(profitSummary.totalMarginRate || 0) < 10 ? '#f56c6c' : '#67c23a' }">
                  {{ Number(profitSummary.totalMarginRate || 0).toFixed(1) }}%
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-table :data="profitItems" v-loading="profitLoading" border stripe>
            <el-table-column prop="name" label="维度" min-width="160" />
            <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
            <el-table-column prop="revenue" label="营收" width="140" align="right">
              <template #default="{ row }">¥{{ Number(row.revenue || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="cost" label="成本" width="140" align="right">
              <template #default="{ row }">¥{{ Number(row.cost || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="profit" label="利润" width="140" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.profit || 0) >= 0 ? '#67c23a' : '#f56c6c', fontWeight: 600 }">
                  ¥{{ Number(row.profit || 0).toFixed(2) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="marginRate" label="毛利率" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isLowMargin ? 'danger' : 'success'" size="small">
                  {{ Number(row.marginRate || 0).toFixed(1) }}%
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 7: 会计科目 -->
        <el-tab-pane label="会计科目" name="account">
          <div class="tab-toolbar">
            <el-input
              v-model="accountKeyword"
              placeholder="搜索科目名称/编码"
              clearable
              style="width:220px; margin-right:8px"
              @keyup.enter="loadAccounts"
              @clear="loadAccounts"
            />
            <el-button type="primary" @click="handleAddAccount">新增科目</el-button>
          </div>
          <el-table :data="accountData" v-loading="accountLoading" border stripe>
            <el-table-column prop="accountCode" label="科目编码" width="120" />
            <el-table-column prop="accountName" label="科目名称" min-width="160" />
            <el-table-column prop="accountType" label="科目类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="accountTypeTag(row.accountType)" size="small">{{ accountTypeText(row.accountType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="direction" label="余额方向" width="100" align="center">
              <template #default="{ row }">{{ row.direction === 'debit' ? '借方' : '贷方' }}</template>
            </el-table-column>
            <el-table-column prop="level" label="级别" width="70" align="center" />
            <el-table-column prop="isLeaf" label="末级" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isLeaf ? 'success' : 'info'" size="small">{{ row.isLeaf ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="isEnabled" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">{{ row.isEnabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="openingBalance" label="期初余额" width="130" align="right">
              <template #default="{ row }">¥{{ Number(row.openingBalance || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="100" />
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleEditAccount(row)">编辑</el-button>
                <el-button type="warning" link size="small" @click="handleSetOpeningBalance(row)">期初余额</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteAccount(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 8: 记账凭证 -->
        <el-tab-pane label="记账凭证" name="voucher">
          <div class="tab-toolbar">
            <el-input
              v-model="voucherQuery.keyword"
              placeholder="搜索凭证号/摘要"
              clearable
              style="width:200px; margin-right:8px"
              @clear="loadVouchers"
              @keyup.enter="loadVouchers"
            />
            <el-date-picker
              v-model="voucherDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width:240px; margin-right:8px"
              @change="loadVouchers"
            />
            <el-select v-model="voucherQuery.status" clearable placeholder="全部状态" style="width:120px; margin-right:8px" @change="loadVouchers">
              <el-option label="草稿" value="draft" />
              <el-option label="已过账" value="posted" />
            </el-select>
            <el-button @click="loadVouchers">查询</el-button>
            <el-button type="primary" @click="handleAddVoucher" style="margin-left:8px">新增凭证</el-button>
          </div>
          <el-table :data="voucherData" v-loading="voucherLoading" border stripe>
            <el-table-column prop="voucherNo" label="凭证号" min-width="160">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleViewVoucher(row)">{{ row.voucherNo }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="voucherDate" label="凭证日期" width="120" />
            <el-table-column prop="voucherType" label="类型" width="90" align="center">
              <template #default="{ row }">{{ voucherTypeText(row.voucherType) }}</template>
            </el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="160" />
            <el-table-column prop="totalDebit" label="借方合计" width="120" align="right">
              <template #default="{ row }">¥{{ Number(row.totalDebit || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="totalCredit" label="贷方合计" width="120" align="right">
              <template #default="{ row }">¥{{ Number(row.totalCredit || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isReversed" type="danger" size="small">已冲红</el-tag>
                <el-tag v-else-if="row.status === 'posted'" type="success" size="small">已过账</el-tag>
                <el-tag v-else type="info" size="small">草稿</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="autoSourceType" label="来源" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.autoSourceType" type="warning" size="small">自动</el-tag>
                <span v-else class="text-muted">手动</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleViewVoucher(row)">查看</el-button>
                <el-button
                  v-if="row.status === 'draft'"
                  type="success" link size="small"
                  @click="handlePostVoucher(row)"
                >过账</el-button>
                <el-button
                  v-if="row.status === 'posted' && !row.isReversed"
                  type="warning" link size="small"
                  @click="handleReverseVoucher(row)"
                >冲红</el-button>
                <el-button
                  v-if="row.status === 'draft'"
                  type="danger" link size="small"
                  @click="handleDeleteVoucher(row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="voucherQuery.pageNum"
              v-model:page-size="voucherQuery.pageSize"
              :total="voucherTotal"
              :page-sizes="[10, 20, 50]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadVouchers"
              @current-change="loadVouchers"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 9: 账簿查询 -->
        <el-tab-pane label="账簿查询" name="ledger">
          <el-tabs v-model="ledgerSubTab" type="card">
            <!-- 科目余额表 -->
            <el-tab-pane label="科目余额表" name="trialBalance">
              <div class="tab-toolbar">
                <el-date-picker
                  v-model="ledgerDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  style="width:240px; margin-right:8px"
                />
                <el-button type="primary" @click="loadTrialBalance">查询</el-button>
              </div>
              <el-table :data="trialBalanceData" v-loading="ledgerLoading" border stripe>
                <el-table-column prop="accountCode" label="科目编码" width="120" />
                <el-table-column prop="accountName" label="科目名称" min-width="160" />
                <el-table-column prop="accountType" label="科目类型" width="100" align="center">
                  <template #default="{ row }">{{ accountTypeText(row.accountType) }}</template>
                </el-table-column>
                <el-table-column prop="direction" label="方向" width="70" align="center">
                  <template #default="{ row }">{{ row.direction === 'debit' ? '借' : '贷' }}</template>
                </el-table-column>
                <el-table-column prop="openingBalance" label="期初余额" width="130" align="right">
                  <template #default="{ row }">¥{{ Number(row.openingBalance || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="debitTotal" label="本期借方" width="130" align="right">
                  <template #default="{ row }">¥{{ Number(row.debitTotal || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="creditTotal" label="本期贷方" width="130" align="right">
                  <template #default="{ row }">¥{{ Number(row.creditTotal || 0).toFixed(2) }}</template>
                </el-table-column>
                <el-table-column prop="balance" label="期末余额" width="140" align="right">
                  <template #default="{ row }">
                    <span :style="{ color: Number(row.balance) < 0 ? '#f56c6c' : '', fontWeight: 600 }">
                      ¥{{ Number(row.balance || 0).toFixed(2) }}
                    </span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 明细账 -->
            <el-tab-pane label="明细账" name="ledgerDetail">
              <div class="tab-toolbar">
                <el-select
                  v-model="ledgerAccountId"
                  filterable
                  clearable
                  placeholder="选择科目"
                  style="width:220px; margin-right:8px"
                >
                  <el-option
                    v-for="acc in leafAccounts"
                    :key="acc.id"
                    :label="`${acc.accountCode} ${acc.accountName}`"
                    :value="acc.id"
                  />
                </el-select>
                <el-date-picker
                  v-model="ledgerDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                  style="width:240px; margin-right:8px"
                />
                <el-button type="primary" @click="loadLedgerDetail">查询</el-button>
              </div>
              <el-table :data="ledgerDetailData" v-loading="ledgerDetailLoading" border stripe>
                <el-table-column prop="voucherDate" label="日期" width="110" />
                <el-table-column prop="voucherNo" label="凭证号" width="160" />
                <el-table-column prop="summary" label="摘要" min-width="160" />
                <el-table-column prop="debitAmount" label="借方金额" width="140" align="right">
                  <template #default="{ row }">
                    <span v-if="row.debitAmount">¥{{ Number(row.debitAmount).toFixed(2) }}</span>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="creditAmount" label="贷方金额" width="140" align="right">
                  <template #default="{ row }">
                    <span v-if="row.creditAmount">¥{{ Number(row.creditAmount).toFixed(2) }}</span>
                    <span v-else class="text-muted">-</span>
                  </template>
                </el-table-column>
                <el-table-column prop="balance" label="余额" width="150" align="right">
                  <template #default="{ row }">
                    <span :style="{ color: Number(row.balance) < 0 ? '#f56c6c' : '', fontWeight: row.type === 'opening' ? 600 : 400 }">
                      ¥{{ Number(row.balance || 0).toFixed(2) }}
                    </span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <!-- Tab 10: 财务报表 -->
        <el-tab-pane label="财务报表" name="statement">
          <div class="tab-toolbar">
            <el-radio-group v-model="statementType" @change="handleStatementTypeChange">
              <el-radio-button label="income">利润表</el-radio-button>
              <el-radio-button label="balance">资产负债表</el-radio-button>
            </el-radio-group>
            <el-date-picker
              v-model="statementDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width:240px; margin-left:12px; margin-right:8px"
            />
            <el-button type="primary" @click="loadStatement">查询</el-button>
          </div>

          <!-- 利润表 -->
          <div v-if="statementType === 'income' && statementData" v-loading="statementLoading">
            <el-row :gutter="16" style="margin-bottom:16px">
              <el-col :span="6">
                <el-card shadow="hover" class="profit-card">
                  <div class="profit-label">营业收入</div>
                  <div class="profit-value">¥{{ Number(statementData.revenue || 0).toFixed(2) }}</div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="hover" class="profit-card">
                  <div class="profit-label">营业成本</div>
                  <div class="profit-value" style="color:#f56c6c">¥{{ Number(statementData.cost || 0).toFixed(2) }}</div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="hover" class="profit-card">
                  <div class="profit-label">毛利润</div>
                  <div class="profit-value" :style="{ color: Number(statementData.grossProfit) >= 0 ? '#67c23a' : '#f56c6c' }">
                    ¥{{ Number(statementData.grossProfit || 0).toFixed(2) }}
                  </div>
                </el-card>
              </el-col>
              <el-col :span="6">
                <el-card shadow="hover" class="profit-card">
                  <div class="profit-label">净利润</div>
                  <div class="profit-value" :style="{ color: Number(statementData.netProfit) >= 0 ? '#67c23a' : '#f56c6c' }">
                    ¥{{ Number(statementData.netProfit || 0).toFixed(2) }}
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </div>

          <!-- 资产负债表 -->
          <div v-if="statementType === 'balance' && statementData" v-loading="statementLoading">
            <el-row :gutter="16" style="margin-bottom:16px">
              <el-col :span="8">
                <el-card shadow="hover" class="profit-card">
                  <div class="profit-label">总资产</div>
                  <div class="profit-value">¥{{ Number(statementData.totalAsset || 0).toFixed(2) }}</div>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="hover" class="profit-card">
                  <div class="profit-label">总负债</div>
                  <div class="profit-value" style="color:#f56c6c">¥{{ Number(statementData.totalLiability || 0).toFixed(2) }}</div>
                </el-card>
              </el-col>
              <el-col :span="8">
                <el-card shadow="hover" class="profit-card">
                  <div class="profit-label">所有者权益</div>
                  <div class="profit-value" :style="{ color: Number(statementData.totalEquity) >= 0 ? '#67c23a' : '#f56c6c' }">
                    ¥{{ Number(statementData.totalEquity || 0).toFixed(2) }}
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </div>

          <div v-if="!statementData && !statementLoading" style="text-align:center; color:#909399; padding:40px">
            请选择日期范围并点击查询
          </div>

          <!-- 期末结账区 -->
          <div style="margin-top:24px; padding-top:16px; border-top:1px solid #ebeef5">
            <div style="display:flex; align-items:center; gap:12px">
              <span style="font-size:14px; font-weight:600; color:#303133">期末结账</span>
              <el-date-picker
                v-model="periodCloseMonth"
                type="month"
                placeholder="选择结账月份"
                value-format="YYYY-MM"
                style="width:160px"
              />
              <el-button type="danger" @click="handlePeriodClose" :loading="periodCloseLoading">执行结账</el-button>
              <el-button @click="loadPeriodCloseList">查看历史</el-button>
            </div>
            <el-table v-if="periodCloseList.length" :data="periodCloseList" border stripe size="small" style="margin-top:12px; max-width:600px">
              <el-table-column prop="period" label="结账期间" width="100" />
              <el-table-column prop="closeTime" label="结账时间" min-width="160" />
              <el-table-column label="结账凭证" width="120" align="center">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="handleViewVoucher({ id: row.voucherId, voucherNo: '结账凭证' })">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增/编辑科目对话框 -->
    <el-dialog v-model="accountDialogVisible" :title="accountForm.id ? '编辑科目' : '新增科目'" width="520px" destroy-on-close>
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-width="100px">
        <el-form-item label="科目编码" prop="accountCode">
          <el-input v-model="accountForm.accountCode" placeholder="如: 1001" :disabled="!!accountForm.id" />
        </el-form-item>
        <el-form-item label="科目名称" prop="accountName">
          <el-input v-model="accountForm.accountName" placeholder="如: 库存现金" />
        </el-form-item>
        <el-form-item label="科目类型" prop="accountType">
          <el-select v-model="accountForm.accountType" placeholder="请选择" style="width:100%">
            <el-option label="资产" value="asset" />
            <el-option label="负债" value="liability" />
            <el-option label="权益" value="equity" />
            <el-option label="收入" value="income" />
            <el-option label="费用" value="expense" />
            <el-option label="成本" value="cost" />
          </el-select>
        </el-form-item>
        <el-form-item label="余额方向" prop="direction">
          <el-radio-group v-model="accountForm.direction">
            <el-radio value="debit">借方</el-radio>
            <el-radio value="credit">贷方</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="层级" prop="level">
          <el-input-number v-model="accountForm.level" :min="1" :max="4" />
        </el-form-item>
        <el-form-item label="是否末级">
          <el-switch v-model="accountForm.isLeaf" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="accountForm.isEnabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="accountForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAccountSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 期初余额对话框 -->
    <el-dialog v-model="openingBalanceDialogVisible" title="设置期初余额" width="400px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="科目名称">
          <span style="font-weight:600">{{ openingBalanceForm.accountName }}</span>
        </el-form-item>
        <el-form-item label="期初余额">
          <el-input-number
            v-model="openingBalanceForm.openingBalance"
            :precision="2"
            :min="-999999999"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openingBalanceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="openingBalanceLoading" @click="handleOpeningBalanceSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增凭证对话框 -->
    <el-dialog v-model="voucherDialogVisible" title="新增记账凭证" width="860px" destroy-on-close>
      <el-form ref="voucherFormRef" :model="voucherForm" :rules="voucherFormRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="凭证日期" prop="voucherDate">
              <el-date-picker v-model="voucherForm.voucherDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="凭证类型" prop="voucherType">
              <el-select v-model="voucherForm.voucherType" style="width:100%">
                <el-option label="收款凭证" value="receipt" />
                <el-option label="付款凭证" value="payment" />
                <el-option label="转账凭证" value="transfer" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="摘要">
              <el-input v-model="voucherForm.summary" placeholder="凭证摘要" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 凭证明细行 -->
      <div style="margin-top:8px">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px">
          <span style="font-weight:600">凭证明细行</span>
          <el-button size="small" @click="addVoucherLine">+ 添加行</el-button>
        </div>
        <el-table :data="voucherLines" border size="small">
          <el-table-column label="行号" width="60" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="摘要" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.summary" size="small" placeholder="摘要" />
            </template>
          </el-table-column>
          <el-table-column label="科目" min-width="180">
            <template #default="{ row }">
              <el-select v-model="row.accountId" size="small" filterable placeholder="选择科目" style="width:100%">
                <el-option
                  v-for="acc in leafAccounts"
                  :key="acc.id"
                  :label="`${acc.accountCode} ${acc.accountName}`"
                  :value="acc.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="借方金额" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.debitAmount" size="small" :min="0" :precision="2" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="贷方金额" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.creditAmount" size="small" :min="0" :precision="2" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="removeVoucherLine($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="display:flex; justify-content:flex-end; gap:24px; margin-top:8px; font-weight:600; font-size:13px">
          <span>借方合计: ¥{{ voucherDebitTotal.toFixed(2) }}</span>
          <span>贷方合计: ¥{{ voucherCreditTotal.toFixed(2) }}</span>
          <span :style="{ color: voucherDebitTotal === voucherCreditTotal ? '#67c23a' : '#f56c6c' }">
            {{ voucherDebitTotal === voucherCreditTotal ? '借贷平衡 ✓' : '借贷不平衡 ✗' }}
          </span>
        </div>
      </div>
      <template #footer>
        <el-button @click="voucherDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleVoucherSubmit">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 凭证详情对话框 -->
    <el-dialog v-model="voucherDetailDialogVisible" title="凭证详情" width="800px" destroy-on-close>
      <div v-loading="voucherDetailLoading">
        <el-descriptions :column="3" border v-if="voucherDetailData">
          <el-descriptions-item label="凭证号">{{ voucherDetailData.voucherNo }}</el-descriptions-item>
          <el-descriptions-item label="凭证日期">{{ voucherDetailData.voucherDate }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ voucherTypeText(voucherDetailData.voucherType) }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ voucherDetailData.summary }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="voucherDetailData.status === 'posted' ? 'success' : 'info'" size="small">
              {{ voucherDetailData.status === 'posted' ? '已过账' : '草稿' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="借方合计">¥{{ Number(voucherDetailData.totalDebit || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="贷方合计">¥{{ Number(voucherDetailData.totalCredit || 0).toFixed(2) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="voucherDetailData?.lines || []" border stripe size="small" style="margin-top:16px">
          <el-table-column prop="lineNo" label="行号" width="60" align="center" />
          <el-table-column prop="summary" label="摘要" min-width="120" />
          <el-table-column label="科目" min-width="180">
            <template #default="{ row }">{{ row.accountCode }} {{ row.accountName }}</template>
          </el-table-column>
          <el-table-column prop="debitAmount" label="借方金额" width="130" align="right">
            <template #default="{ row }">{{ row.debitAmount > 0 ? '¥' + Number(row.debitAmount).toFixed(2) : '-' }}</template>
          </el-table-column>
          <el-table-column prop="creditAmount" label="贷方金额" width="130" align="right">
            <template #default="{ row }">{{ row.creditAmount > 0 ? '¥' + Number(row.creditAmount).toFixed(2) : '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="voucherDetailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- receive payment dialog (for receivable) -->
    <el-dialog v-model="receiveDialogVisible" title="收款" width="520px" destroy-on-close @close="resetReceiveForm">
      <el-form ref="receiveFormRef" :model="receiveForm" :rules="paymentRules" label-width="100px">
        <el-form-item label="收款金额" prop="amount">
          <el-input-number v-model="receiveForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款日期" prop="paymentDate">
          <el-date-picker
            v-model="receiveForm.paymentDate"
            type="date"
            placeholder="请选择收款日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="收款方式" prop="paymentMethod">
          <el-select v-model="receiveForm.paymentMethod" placeholder="请选择收款方式" style="width: 100%">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="支票" value="CHECK" />
            <el-option label="承兑汇票" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="交易流水号" prop="transactionNo">
          <el-input v-model="receiveForm.transactionNo" placeholder="请输入交易流水号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleReceiveSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- make payment dialog (for payable) -->
    <el-dialog v-model="payDialogVisible" title="付款" width="520px" destroy-on-close @close="resetPayForm">
      <el-form ref="payFormRef" :model="payForm" :rules="paymentRules" label-width="100px">
        <el-form-item label="付款金额" prop="amount">
          <el-input-number v-model="payForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款日期" prop="paymentDate">
          <el-date-picker
            v-model="payForm.paymentDate"
            type="date"
            placeholder="请选择付款日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="付款方式" prop="paymentMethod">
          <el-select v-model="payForm.paymentMethod" placeholder="请选择付款方式" style="width: 100%">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="支票" value="CHECK" />
            <el-option label="承兑汇票" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="交易流水号" prop="transactionNo">
          <el-input v-model="payForm.transactionNo" placeholder="请输入交易流水号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handlePaySubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- cost detail dialog -->
    <el-dialog v-model="costDetailDialogVisible" title="成本明细" width="720px" destroy-on-close>
      <div v-loading="costDetailLoading">
        <el-descriptions :column="2" border v-if="costDetailData">
          <el-descriptions-item label="工单号">{{ costDetailData.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="产品">{{ costDetailData.productName }}</el-descriptions-item>
          <el-descriptions-item label="计划数">{{ costDetailData.planQty }}</el-descriptions-item>
          <el-descriptions-item label="完成数">{{ costDetailData.completedQty }}</el-descriptions-item>
          <el-descriptions-item label="材料成本">¥{{ Number(costDetailData.materialCost || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="人工成本">¥{{ Number(costDetailData.laborCost || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="外协成本">¥{{ Number(costDetailData.outsourceCost || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="返工成本">¥{{ Number(costDetailData.reworkCost || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="报废损失">¥{{ Number(costDetailData.scrapLoss || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="制造费用">¥{{ Number(costDetailData.overhead || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="总成本">
            <span style="font-weight: bold; font-size: 16px;">¥{{ Number(costDetailData.totalCost || 0).toFixed(2) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="预算">¥{{ Number(costDetailData.budget || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="差异" :span="2">
            <span :style="{ color: Number(costDetailData.variance || 0) > 0 ? '#f56c6c' : '#67c23a', fontWeight: 'bold' }">
              ¥{{ Number(costDetailData.variance || 0).toFixed(2) }}
              <template v-if="Number(costDetailData.variance || 0) > 0">(超预算)</template>
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- cost breakdown items -->
        <div v-if="costDetailData && costDetailData.items && costDetailData.items.length" style="margin-top: 16px;">
          <h4 style="margin-bottom: 8px;">成本明细项</h4>
          <el-table :data="costDetailData.items" border stripe size="small">
            <el-table-column prop="category" label="类别" width="100" />
            <el-table-column prop="itemName" label="项目" min-width="160" />
            <el-table-column prop="qty" label="数量" width="80" align="center" />
            <el-table-column prop="unitPrice" label="单价" width="100" align="right">
              <template #default="{ row }">{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="120" align="right">
              <template #default="{ row }">{{ Number(row.amount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" />
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="costDetailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 工资明细抽屉 -->
    <el-drawer v-model="wageDetailVisible" :title="`${wageDetailData?.employeeName || ''} - ${wageDetailData?.month || ''} 工资明细`" size="700px">
      <template v-if="wageDetailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="员工姓名">{{ wageDetailData.employeeName }}</el-descriptions-item>
          <el-descriptions-item label="月份">{{ wageDetailData.month }}</el-descriptions-item>
          <el-descriptions-item label="出勤天数">{{ wageDetailData.workDays || '-' }} 天</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="wageStatusType(wageDetailData.status)">{{ wageStatusText(wageDetailData.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="wageDetailTab" style="margin-top: 16px">
          <el-tab-pane label="工资构成" name="breakdown">
            <div class="wage-breakdown">
              <div class="wage-item">
                <span class="wage-label">个人计件工资</span>
                <span class="wage-value">{{ Number(wageDetailData.personalPieceWage || 0).toFixed(2) }}</span>
              </div>
              <div class="wage-item">
                <span class="wage-label">团队分配工资</span>
                <span class="wage-value">{{ Number(wageDetailData.teamAllocatedWage || 0).toFixed(2) }}</span>
              </div>
              <div class="wage-item">
                <span class="wage-label">加班工资</span>
                <span class="wage-value">{{ Number(wageDetailData.overtimeWage || 0).toFixed(2) }}</span>
              </div>
              <div class="wage-item">
                <span class="wage-label">奖金</span>
                <span class="wage-value" style="color: #67c23a">{{ Number(wageDetailData.bonus || 0).toFixed(2) }}</span>
              </div>
              <div class="wage-item">
                <span class="wage-label">扣款</span>
                <span class="wage-value" style="color: #f56c6c">-{{ Number(wageDetailData.deduction || 0).toFixed(2) }}</span>
              </div>
              <el-divider />
              <div class="wage-item total">
                <span class="wage-label">合计应发</span>
                <span class="wage-value" style="color: #409eff; font-size: 20px">{{ Number(wageDetailData.totalWage || 0).toFixed(2) }}</span>
              </div>
            </div>
            <div v-if="wageDetailData.remark" style="margin-top: 16px">
              <div style="font-size: 13px; color: #909399; margin-bottom: 4px">备注</div>
              <div style="color: #606266">{{ wageDetailData.remark }}</div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="报工明细" name="reports">
            <div style="margin-bottom: 8px; font-size: 13px; color: #909399">
              共 <strong style="color:#409eff">{{ wageReportRows.length }}</strong> 条报工记录，合格 <strong style="color:#67c23a">{{ wageReportTotals.qty.toFixed(2) }}</strong> 件，金额 <strong style="color:#e6a23c">¥{{ wageReportTotals.amount.toFixed(2) }}</strong>
            </div>
            <el-table :data="wageReportRows" v-loading="wageReportLoading" border stripe size="small" max-height="500">
              <el-table-column prop="reportTime" label="报工时间" width="140" />
              <el-table-column prop="woNo" label="工单号" width="140" show-overflow-tooltip />
              <el-table-column prop="materialName" label="物料" min-width="120" show-overflow-tooltip />
              <el-table-column prop="processName" label="工序" width="90" show-overflow-tooltip />
              <el-table-column prop="piecePrice" label="单价" width="70" align="right">
                <template #default="{ row }">{{ Number(row.piecePrice || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="qualifiedQty" label="合格数" width="70" align="right" />
              <el-table-column prop="amount" label="金额" width="85" align="right">
                <template #default="{ row }">
                  <span style="color:#e6a23c">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!wageReportLoading && !wageReportRows.length" description="暂无报工记录" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  getReceivablePage,
  getPayablePage,
  exportReceivables,
  addPayment,
  getWagePage,
  generateMonthlyWages,
  getReportAnalysisList,
  getProfitAnalysis,
  getWoCostPage,
  getWoCostDetail,
  getOrderProfitPage,
  getPaymentPage,
  getAccountList,
  createAccount,
  updateAccount,
  deleteAccount,
  getVoucherPage,
  getVoucherDetail,
  createVoucher,
  postVoucher,
  deleteVoucher,
  getTrialBalance,
  getIncomeStatement,
  getBalanceSheet,
  reverseVoucher,
  setOpeningBalance,
  getLedger,
  periodClose,
  getPeriodCloseList
} from '@/api/finance'

// --- state ---
const activeTab = ref('cost')
const submitLoading = ref(false)

// cost center tab - wo cost
const costActiveCollapse = ref(['woCost', 'orderProfit'])
const woCostLoading = ref(false)
const woCostData = ref([])
const woCostTotal = ref(0)
const woCostQuery = reactive({ pageNum: 1, pageSize: 10 })

// cost center tab - order profit
const orderProfitLoading = ref(false)
const orderProfitData = ref([])
const orderProfitTotal = ref(0)
const orderProfitQuery = reactive({ pageNum: 1, pageSize: 10 })

// cost detail dialog
const costDetailDialogVisible = ref(false)
const costDetailLoading = ref(false)
const costDetailData = ref(null)

// receivable tab
const receivableLoading = ref(false)
const receivableData = ref([])
const receivableTotal = ref(0)
const receivableAsOfDate = ref('')
const receivableQuery = reactive({
  customerName: '', status: '',
  pageNum: 1, pageSize: 10
})
const receivableSum = reactive({ amount: 0, received: 0, unreceived: 0 })

// payable tab
const payableLoading = ref(false)
const payableData = ref([])
const payableTotal = ref(0)
const payableQuery = reactive({ pageNum: 1, pageSize: 10 })

// payment tab
const paymentSubTab = ref('receiveRecord')
const receiveRecordLoading = ref(false)
const receiveRecordData = ref([])
const receiveRecordTotal = ref(0)
const receiveRecordQuery = reactive({ pageNum: 1, pageSize: 10, type: 'RECEIVE' })
const payRecordLoading = ref(false)
const payRecordData = ref([])
const payRecordTotal = ref(0)
const payRecordQuery = reactive({ pageNum: 1, pageSize: 10, type: 'PAY' })

// profit tab
const profitLoading = ref(false)
const profitItems = ref([])
const profitSummary = ref(null)
const profitQuery = reactive({ dimension: 'month' })
const profitDateRange = ref(null)

// wage tab
const wageLoading = ref(false)
const wageGenerating = ref(false)
const wageData = ref([])
const wageTotal = ref(0)
const wageQuery = reactive({ month: '', pageNum: 1, pageSize: 10 })

// 工资明细抽屉
const wageDetailVisible = ref(false)
const wageDetailData = ref(null)
const wageDetailTab = ref('breakdown')
const wageReportRows = ref([])
const wageReportLoading = ref(false)
const wageReportTotals = reactive({ qty: 0, amount: 0 })

async function openWageDetail(row) {
  wageDetailData.value = row
  wageDetailTab.value = 'breakdown'
  wageReportRows.value = []
  wageReportTotals.qty = 0; wageReportTotals.amount = 0
  wageDetailVisible.value = true
  if (!row?.employeeName || !row?.month) return
  const [y, m] = row.month.split('-')
  const lastDay = new Date(Number(y), Number(m), 0).getDate()
  wageReportLoading.value = true
  try {
    const res = await getReportAnalysisList({
      startDate: `${row.month}-01`,
      endDate: `${row.month}-${String(lastDay).padStart(2, '0')}`,
      employeeName: row.employeeName,
      pageNum: 1,
      pageSize: 1000
    })
    wageReportRows.value = res.data?.records || []
    wageReportTotals.qty = wageReportRows.value.reduce((s, r) => s + Number(r.qualifiedQty || 0), 0)
    wageReportTotals.amount = wageReportRows.value.reduce((s, r) => s + Number(r.amount || 0), 0)
  } catch {} finally { wageReportLoading.value = false }
}

// account tab
const accountLoading = ref(false)
const accountData = ref([])
const accountKeyword = ref('')
const accountDialogVisible = ref(false)
const accountFormRef = ref(null)
const accountForm = ref({ id: null, accountCode: '', accountName: '', accountType: '', direction: 'debit', level: 1, isLeaf: true, isEnabled: true, remark: '' })
const accountRules = {
  accountCode: [{ required: true, message: '请输入科目编码', trigger: 'blur' }],
  accountName: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
  accountType: [{ required: true, message: '请选择科目类型', trigger: 'change' }],
  direction: [{ required: true, message: '请选择余额方向', trigger: 'change' }]
}

// voucher tab
const voucherLoading = ref(false)
const voucherData = ref([])
const voucherTotal = ref(0)
const voucherDateRange = ref(null)
const voucherQuery = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 10 })
const voucherDialogVisible = ref(false)
const voucherFormRef = ref(null)
const voucherForm = ref({ voucherDate: '', voucherType: 'transfer', summary: '' })
const voucherFormRules = {
  voucherDate: [{ required: true, message: '请选择凭证日期', trigger: 'change' }],
  voucherType: [{ required: true, message: '请选择凭证类型', trigger: 'change' }]
}
const voucherLines = ref([
  { summary: '', accountId: null, debitAmount: 0, creditAmount: 0 },
  { summary: '', accountId: null, debitAmount: 0, creditAmount: 0 }
])
const voucherDetailDialogVisible = ref(false)
const voucherDetailLoading = ref(false)
const voucherDetailData = ref(null)

// ledger tab
const ledgerSubTab = ref('trialBalance')
const ledgerLoading = ref(false)
const ledgerDateRange = ref(null)
const trialBalanceData = ref([])
// 明细账
const ledgerAccountId = ref(null)
const ledgerDetailData = ref([])
const ledgerDetailLoading = ref(false)

// statement tab
const statementType = ref('income')
const statementLoading = ref(false)
const statementData = ref(null)
const statementDateRange = ref(null)
// 期末结账
const periodCloseDialogVisible = ref(false)
const periodCloseMonth = ref('')
const periodCloseLoading = ref(false)
const periodCloseList = ref([])

// 期初余额对话框
const openingBalanceDialogVisible = ref(false)
const openingBalanceForm = ref({ accountId: null, accountName: '', openingBalance: 0 })
const openingBalanceLoading = ref(false)

// computed
const leafAccounts = computed(() => accountData.value.filter(a => a.isLeaf))
const voucherDebitTotal = computed(() => voucherLines.value.reduce((sum, l) => sum + (l.debitAmount || 0), 0))
const voucherCreditTotal = computed(() => voucherLines.value.reduce((sum, l) => sum + (l.creditAmount || 0), 0))

// receive payment dialog
const receiveDialogVisible = ref(false)
const receiveFormRef = ref(null)
const receiveForm = ref({
  receivableId: undefined,
  type: 'RECEIVE',
  amount: 0,
  paymentDate: '',
  paymentMethod: undefined,
  transactionNo: ''
})

// pay dialog
const payDialogVisible = ref(false)
const payFormRef = ref(null)
const payForm = ref({
  payableId: undefined,
  type: 'PAY',
  amount: 0,
  paymentDate: '',
  paymentMethod: undefined,
  transactionNo: ''
})

// shared payment rules
const paymentRules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  paymentDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }]
}

// --- helpers ---
function financeStatusType(status) {
  const map = { PENDING: 'warning', PARTIAL: '', COMPLETED: 'success', OVERDUE: 'danger' }
  return map[status] || 'info'
}
function financeStatusText(status) {
  const map = { PENDING: '待收付', PARTIAL: '部分收付', COMPLETED: '已完成', OVERDUE: '已逾期' }
  return map[status] || status
}
function wageStatusType(status) {
  const map = { DRAFT: 'info', CONFIRMED: 'warning', PAID: 'success' }
  return map[status] || 'info'
}
function wageStatusText(status) {
  const map = { DRAFT: '草稿', CONFIRMED: '已确认', PAID: '已发放' }
  return map[status] || status
}
function orderProfitStatusType(status) {
  const map = { PENDING: 'warning', IN_PROGRESS: '', COMPLETED: 'success', CLOSED: 'info' }
  return map[status] || 'info'
}
function orderProfitStatusText(status) {
  const map = { PENDING: '待处理', IN_PROGRESS: '进行中', COMPLETED: '已完成', CLOSED: '已关闭' }
  return map[status] || status
}
function paymentMethodText(method) {
  const map = { BANK_TRANSFER: '银行转账', WECHAT: '微信', ALIPAY: '支付宝', CASH: '现金', CHECK: '支票', ACCEPTANCE: '承兑汇票' }
  return map[method] || method
}

// --- methods ---

// cost center - wo cost
async function loadWoCosts() {
  woCostLoading.value = true
  try {
    const res = await getWoCostPage(woCostQuery)
    woCostData.value = res.data.records || []
    woCostTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    woCostLoading.value = false
  }
}

// cost center - order profit
async function loadOrderProfits() {
  orderProfitLoading.value = true
  try {
    const res = await getOrderProfitPage(orderProfitQuery)
    orderProfitData.value = res.data.records || []
    orderProfitTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    orderProfitLoading.value = false
  }
}

// cost detail dialog
async function handleViewCostDetail(row) {
  costDetailDialogVisible.value = true
  costDetailLoading.value = true
  costDetailData.value = null
  try {
    const res = await getWoCostDetail(row.id)
    costDetailData.value = res.data || {}
  } catch {
    // handled by interceptor
  } finally {
    costDetailLoading.value = false
  }
}

function buildReceivableParams(extra = {}) {
  return {
    customerName: receivableQuery.customerName || undefined,
    status: receivableQuery.status || undefined,
    asOfDate: receivableAsOfDate.value || undefined,
    ...extra
  }
}

async function loadReceivables() {
  receivableLoading.value = true
  try {
    const res = await getReceivablePage(buildReceivableParams({
      pageNum: receivableQuery.pageNum, pageSize: receivableQuery.pageSize
    }))
    receivableData.value = res.data.records || []
    receivableTotal.value = res.data.total || 0
    // 当前页合计 (与 Excel 导出的全量合计区分: 这里只反映分页可见数据)
    receivableSum.amount = receivableData.value.reduce((s, r) => s + Number(r.amount || 0), 0)
    receivableSum.received = receivableData.value.reduce((s, r) => s + Number(r.receivedAmount || 0), 0)
    receivableSum.unreceived = receivableData.value.reduce((s, r) => s + Number(r.unreceivedAmount || 0), 0)
  } catch {
    // handled by interceptor
  } finally {
    receivableLoading.value = false
  }
}

function handleReceivableSearch() {
  receivableQuery.pageNum = 1
  loadReceivables()
}

function handleReceivableReset() {
  receivableAsOfDate.value = ''
  receivableQuery.customerName = ''
  receivableQuery.status = ''
  receivableQuery.pageNum = 1
  loadReceivables()
}

async function handleReceivableExport() {
  try {
    const r = await exportReceivables(buildReceivableParams())
    const url = window.URL.createObjectURL(new Blob([r.data || r]))
    const a = document.createElement('a')
    a.href = url
    a.download = `应收明细_${new Date().toISOString().slice(0,10)}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

async function loadPayables() {
  payableLoading.value = true
  try {
    const res = await getPayablePage(payableQuery)
    payableData.value = res.data.records || []
    payableTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    payableLoading.value = false
  }
}

// payment tab
async function loadReceiveRecords() {
  receiveRecordLoading.value = true
  try {
    const res = await getPaymentPage(receiveRecordQuery)
    receiveRecordData.value = res.data.records || []
    receiveRecordTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    receiveRecordLoading.value = false
  }
}

async function loadPayRecords() {
  payRecordLoading.value = true
  try {
    const res = await getPaymentPage(payRecordQuery)
    payRecordData.value = res.data.records || []
    payRecordTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    payRecordLoading.value = false
  }
}

async function loadWages() {
  wageLoading.value = true
  try {
    const res = await getWagePage(wageQuery)
    wageData.value = res.data.records || []
    wageTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    wageLoading.value = false
  }
}

async function handleGenerateWages() {
  // 默认当月;如果用户已选则使用已选月份
  let month = wageQuery.month
  if (!month) {
    const now = new Date()
    month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  }
  try {
    await ElMessageBox.confirm(
      `将根据 ${month} 的报工记录生成/更新当月工资草稿（已确认/审批/发放的不会被覆盖）`,
      '生成月度工资', { type: 'info' }
    )
  } catch { return }
  wageGenerating.value = true
  try {
    await generateMonthlyWages(month)
    ElMessage.success('生成成功')
    wageQuery.month = month
    loadWages()
  } catch {
    // handled by interceptor
  } finally { wageGenerating.value = false }
}

async function loadProfitAnalysis() {
  profitLoading.value = true
  try {
    const params = { dimension: profitQuery.dimension }
    if (profitDateRange.value && profitDateRange.value.length === 2) {
      params.startDate = profitDateRange.value[0]
      params.endDate = profitDateRange.value[1]
    }
    const res = await getProfitAnalysis(params)
    const data = res.data || {}
    profitItems.value = data.items || []
    profitSummary.value = data
  } catch {} finally {
    profitLoading.value = false
  }
}

function handleProfitDateChange() {
  loadProfitAnalysis()
}

function handleTabChange(tab) {
  if (tab === 'cost') {
    loadWoCosts()
    loadOrderProfits()
  } else if (tab === 'receivable') {
    loadReceivables()
  } else if (tab === 'payable') {
    loadPayables()
  } else if (tab === 'payment') {
    loadReceiveRecords()
    loadPayRecords()
  } else if (tab === 'profit') {
    loadProfitAnalysis()
  } else if (tab === 'wage') {
    loadWages()
  } else if (tab === 'account') {
    loadAccounts()
  } else if (tab === 'voucher') {
    loadVouchers()
    loadAccounts()
  } else if (tab === 'ledger') {
    if (accountData.value.length === 0) loadAccounts()
  } else if (tab === 'statement') {
    loadPeriodCloseList()
  }
}

// =====================================================================
// 会计科目
// =====================================================================
async function loadAccounts() {
  accountLoading.value = true
  try {
    const res = await getAccountList({ keyword: accountKeyword.value })
    accountData.value = res.data || []
  } catch {} finally {
    accountLoading.value = false
  }
}

function handleAddAccount() {
  accountForm.value = { id: null, accountCode: '', accountName: '', accountType: '', direction: 'debit', level: 1, isLeaf: true, isEnabled: true, remark: '' }
  accountDialogVisible.value = true
}

function handleEditAccount(row) {
  accountForm.value = { ...row }
  accountDialogVisible.value = true
}

async function handleDeleteAccount(row) {
  await ElMessageBox.confirm(`确认删除科目"${row.accountName}"？`, '确认', { type: 'warning' })
  try {
    await deleteAccount(row.id)
    ElMessage.success('删除成功')
    loadAccounts()
  } catch {}
}

async function handleAccountSubmit() {
  await accountFormRef.value.validate()
  submitLoading.value = true
  try {
    if (accountForm.value.id) {
      await updateAccount(accountForm.value.id, accountForm.value)
    } else {
      await createAccount(accountForm.value)
    }
    ElMessage.success('保存成功')
    accountDialogVisible.value = false
    loadAccounts()
  } catch {} finally {
    submitLoading.value = false
  }
}

function accountTypeText(type) {
  const map = { asset: '资产', liability: '负债', equity: '权益', income: '收入', expense: '费用', cost: '成本' }
  return map[type] || type
}

function accountTypeTag(type) {
  const map = { asset: '', liability: 'danger', equity: 'warning', income: 'success', expense: 'info', cost: '' }
  return map[type] || 'info'
}

// =====================================================================
// 记账凭证
// =====================================================================
async function loadVouchers() {
  voucherLoading.value = true
  try {
    const params = { ...voucherQuery }
    if (voucherDateRange.value && voucherDateRange.value.length === 2) {
      params.startDate = voucherDateRange.value[0]
      params.endDate = voucherDateRange.value[1]
    }
    const res = await getVoucherPage(params)
    voucherData.value = res.data.records || []
    voucherTotal.value = res.data.total || 0
  } catch {} finally {
    voucherLoading.value = false
  }
}

function handleAddVoucher() {
  voucherForm.value = { voucherDate: '', voucherType: 'transfer', summary: '' }
  voucherLines.value = [
    { summary: '', accountId: null, debitAmount: 0, creditAmount: 0 },
    { summary: '', accountId: null, debitAmount: 0, creditAmount: 0 }
  ]
  if (accountData.value.length === 0) loadAccounts()
  voucherDialogVisible.value = true
}

function addVoucherLine() {
  voucherLines.value.push({ summary: '', accountId: null, debitAmount: 0, creditAmount: 0 })
}

function removeVoucherLine(index) {
  if (voucherLines.value.length <= 2) {
    ElMessage.warning('至少需要2行')
    return
  }
  voucherLines.value.splice(index, 1)
}

async function handleVoucherSubmit() {
  await voucherFormRef.value.validate()
  if (Math.abs(voucherDebitTotal.value - voucherCreditTotal.value) > 0.001) {
    ElMessage.error('借贷方金额不平衡，请检查')
    return
  }
  if (voucherDebitTotal.value <= 0) {
    ElMessage.error('借方合计必须大于0')
    return
  }
  submitLoading.value = true
  try {
    await createVoucher({ voucher: voucherForm.value, lines: voucherLines.value })
    ElMessage.success('凭证保存成功')
    voucherDialogVisible.value = false
    loadVouchers()
  } catch {} finally {
    submitLoading.value = false
  }
}

async function handleViewVoucher(row) {
  voucherDetailDialogVisible.value = true
  voucherDetailLoading.value = true
  voucherDetailData.value = null
  try {
    const res = await getVoucherDetail(row.id)
    voucherDetailData.value = res.data
  } catch {} finally {
    voucherDetailLoading.value = false
  }
}

async function handlePostVoucher(row) {
  await ElMessageBox.confirm(`确认过账凭证"${row.voucherNo}"？过账后不可修改。`, '确认过账', { type: 'warning' })
  try {
    await postVoucher(row.id)
    ElMessage.success('过账成功')
    loadVouchers()
  } catch {}
}

async function handleDeleteVoucher(row) {
  await ElMessageBox.confirm(`确认删除草稿凭证"${row.voucherNo}"？`, '确认', { type: 'warning' })
  try {
    await deleteVoucher(row.id)
    ElMessage.success('删除成功')
    loadVouchers()
  } catch {}
}

function voucherTypeText(type) {
  const map = { receipt: '收款凭证', payment: '付款凭证', transfer: '转账凭证' }
  return map[type] || type
}

// =====================================================================
// 账簿查询
// =====================================================================
async function loadTrialBalance() {
  ledgerLoading.value = true
  try {
    const params = {}
    if (ledgerDateRange.value && ledgerDateRange.value.length === 2) {
      params.startDate = ledgerDateRange.value[0]
      params.endDate = ledgerDateRange.value[1]
    }
    const res = await getTrialBalance(params)
    trialBalanceData.value = res.data || []
  } catch {} finally {
    ledgerLoading.value = false
  }
}

// =====================================================================
// 财务报表
// =====================================================================
async function loadStatement() {
  statementLoading.value = true
  statementData.value = null
  try {
    const params = {}
    if (statementDateRange.value && statementDateRange.value.length === 2) {
      params.startDate = statementDateRange.value[0]
      params.endDate = statementDateRange.value[1]
      params.asOfDate = statementDateRange.value[1]
    }
    if (statementType.value === 'income') {
      const res = await getIncomeStatement(params)
      statementData.value = res.data
    } else {
      const res = await getBalanceSheet(params)
      statementData.value = res.data
    }
  } catch {} finally {
    statementLoading.value = false
  }
}

function handleStatementTypeChange() {
  statementData.value = null
}

// =====================================================================
// 期初余额
// =====================================================================
function handleSetOpeningBalance(row) {
  openingBalanceForm.value = {
    accountId: row.id,
    accountName: `${row.accountCode} ${row.accountName}`,
    openingBalance: Number(row.openingBalance || 0)
  }
  openingBalanceDialogVisible.value = true
}

async function handleOpeningBalanceSubmit() {
  openingBalanceLoading.value = true
  try {
    await setOpeningBalance(openingBalanceForm.value.accountId, openingBalanceForm.value.openingBalance)
    ElMessage.success('期初余额设置成功')
    openingBalanceDialogVisible.value = false
    loadAccounts()
  } catch {} finally {
    openingBalanceLoading.value = false
  }
}

// =====================================================================
// 凭证冲红
// =====================================================================
async function handleReverseVoucher(row) {
  await ElMessageBox.confirm(
    `确认对凭证"${row.voucherNo}"执行冲红？冲红后系统将自动生成一张红字凭证并过账。`,
    '确认冲红', { type: 'warning' }
  )
  try {
    await reverseVoucher(row.id)
    ElMessage.success('冲红成功')
    loadVouchers()
  } catch {}
}

// =====================================================================
// 明细账
// =====================================================================
async function loadLedgerDetail() {
  if (!ledgerAccountId.value) {
    ElMessage.warning('请先选择科目')
    return
  }
  ledgerDetailLoading.value = true
  try {
    const params = { accountId: ledgerAccountId.value }
    if (ledgerDateRange.value && ledgerDateRange.value.length === 2) {
      params.startDate = ledgerDateRange.value[0]
      params.endDate = ledgerDateRange.value[1]
    }
    const res = await getLedger(params)
    ledgerDetailData.value = res.data || []
  } catch {} finally {
    ledgerDetailLoading.value = false
  }
}

// =====================================================================
// 期末结账
// =====================================================================
async function handlePeriodClose() {
  if (!periodCloseMonth.value) {
    ElMessage.warning('请选择结账月份')
    return
  }
  await ElMessageBox.confirm(
    `确认对 ${periodCloseMonth.value} 执行期末结账？结账后系统将自动生成损益结转凭证。`,
    '确认期末结账', { type: 'warning' }
  )
  periodCloseLoading.value = true
  try {
    await periodClose(periodCloseMonth.value)
    ElMessage.success('期末结账成功')
    loadPeriodCloseList()
  } catch {} finally {
    periodCloseLoading.value = false
  }
}

async function loadPeriodCloseList() {
  try {
    const res = await getPeriodCloseList()
    periodCloseList.value = res.data || []
  } catch {}
}

function handleWageMonthChange() {
  wageQuery.pageNum = 1
  loadWages()
}

// receive payment
function handleReceivePayment(row) {
  receiveForm.value = {
    relatedType: 'receivable',
    relatedId: row.id,
    amount: row.unreceivedAmount || 0,
    paymentDate: '',
    paymentMethod: undefined,
    transactionNo: ''
  }
  receiveDialogVisible.value = true
}

async function handleReceiveSubmit() {
  await receiveFormRef.value.validate()
  submitLoading.value = true
  try {
    await addPayment(receiveForm.value)
    ElMessage.success('收款成功')
    receiveDialogVisible.value = false
    loadReceivables()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetReceiveForm() {
  receiveFormRef.value?.resetFields()
}

// make payment
function handleMakePayment(row) {
  payForm.value = {
    relatedType: 'payable',
    relatedId: row.id,
    amount: row.unpaidAmount || 0,
    paymentDate: '',
    paymentMethod: undefined,
    transactionNo: ''
  }
  payDialogVisible.value = true
}

async function handlePaySubmit() {
  await payFormRef.value.validate()
  submitLoading.value = true
  try {
    await addPayment(payForm.value)
    ElMessage.success('付款成功')
    payDialogVisible.value = false
    loadPayables()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetPayForm() {
  payFormRef.value?.resetFields()
}

// --- lifecycle ---
onMounted(() => {
  loadWoCosts()
  loadOrderProfits()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.table-card {
  flex: 1;
}
.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: #c0c4cc;
}
.stat-mini {
  background: #f8f9fb;
  border-radius: 6px;
  padding: 8px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  .lbl { color: #909399; font-size: 13px; }
  .val { font-weight: 600; font-size: 15px; }
}
.profit-card {
  text-align: center;
  :deep(.el-card__body) { padding: 16px; }
}
.profit-label {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
}
.profit-value {
  font-size: 22px;
  font-weight: bold;
}
.wage-breakdown {
  padding: 8px 4px;
}
.wage-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 14px;
}
.wage-item .wage-label {
  color: #606266;
}
.wage-item .wage-label.sub {
  color: #909399;
  font-size: 13px;
  padding-left: 16px;
}
.wage-item .wage-value {
  font-weight: 500;
  color: #303133;
}
.wage-item.total .wage-label {
  color: #303133;
  font-weight: 600;
  font-size: 15px;
}
</style>
