<template>
  <div class="page-container">
    <!-- search bar -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="工单号">
          <el-input
            v-model="queryParams.workOrderNo"
            placeholder="请输入工单号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input
            v-model="queryParams.productName"
            placeholder="请输入产品名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input
            v-model="queryParams.batchNo"
            placeholder="请输入批次号"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询追溯</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- traceability result -->
    <el-card class="table-card" shadow="never" v-loading="loading">
      <template v-if="traceData">
        <!-- basic info -->
        <el-descriptions :column="3" border style="margin-bottom: 24px;">
          <el-descriptions-item label="工单号">{{ traceData.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ traceData.productName }}</el-descriptions-item>
          <el-descriptions-item label="批次号">{{ traceData.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ traceData.customerName }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ traceData.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ traceData.quantity }}</el-descriptions-item>
        </el-descriptions>

        <!-- timeline -->
        <h4 style="margin-bottom: 16px;">质量追溯时间线</h4>
        <el-timeline>
          <!-- order -->
          <el-timeline-item
            v-if="traceData.order"
            timestamp=""
            placement="top"
            type="primary"
            :hollow="false"
          >
            <el-card shadow="hover">
              <h4>订单下达</h4>
              <p>订单号: {{ traceData.order.orderNo }}</p>
              <p>客户: {{ traceData.order.customerName }}</p>
              <p>下单时间: {{ traceData.order.createTime }}</p>
            </el-card>
          </el-timeline-item>

          <!-- work order -->
          <el-timeline-item
            v-if="traceData.workOrder"
            timestamp=""
            placement="top"
            type="success"
            :hollow="false"
          >
            <el-card shadow="hover">
              <h4>生产工单</h4>
              <p>工单号: {{ traceData.workOrder.workOrderNo }}</p>
              <p>计划数量: {{ traceData.workOrder.planQty }}</p>
              <p>计划开始: {{ traceData.workOrder.planStartTime }}</p>
              <p>计划结束: {{ traceData.workOrder.planEndTime }}</p>
            </el-card>
          </el-timeline-item>

          <!-- materials used -->
          <el-timeline-item
            v-if="traceData.materials && traceData.materials.length"
            timestamp=""
            placement="top"
            type="warning"
            :hollow="false"
          >
            <el-card shadow="hover">
              <h4>领料记录</h4>
              <el-table :data="traceData.materials" border size="small" style="margin-top: 8px;">
                <el-table-column prop="materialName" label="物料名称" min-width="120" />
                <el-table-column prop="materialCode" label="物料编码" min-width="120" />
                <el-table-column prop="batchNo" label="物料批次" min-width="120" />
                <el-table-column prop="quantity" label="领料数量" width="100" align="right" />
                <el-table-column prop="issueTime" label="领料时间" min-width="160" />
              </el-table>
            </el-card>
          </el-timeline-item>

          <!-- process steps -->
          <el-timeline-item
            v-if="traceData.processSteps && traceData.processSteps.length"
            timestamp=""
            placement="top"
            type="primary"
            :hollow="false"
          >
            <el-card shadow="hover">
              <h4>生产工序</h4>
              <el-steps :active="activeStep" finish-status="success" style="margin-top: 12px;">
                <el-step
                  v-for="step in traceData.processSteps"
                  :key="step.stepNo"
                  :title="step.stepName"
                  :description="`${step.operator || '-'} | ${step.completeTime || '进行中'}`"
                />
              </el-steps>
            </el-card>
          </el-timeline-item>

          <!-- QC results -->
          <el-timeline-item
            v-if="traceData.qcResults && traceData.qcResults.length"
            timestamp=""
            placement="top"
            type="danger"
            :hollow="false"
          >
            <el-card shadow="hover">
              <h4>质量检验</h4>
              <el-table :data="traceData.qcResults" border size="small" style="margin-top: 8px;">
                <el-table-column prop="taskNo" label="检验任务" min-width="140" />
                <el-table-column prop="inspectType" label="检验类型" width="100" />
                <el-table-column prop="inspector" label="检验员" width="100" />
                <el-table-column prop="result" label="检验结果" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" size="small">
                      {{ row.result === 'PASS' ? '合格' : '不合格' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="inspectTime" label="检验时间" min-width="160" />
              </el-table>
            </el-card>
          </el-timeline-item>

          <!-- shipping -->
          <el-timeline-item
            v-if="traceData.shipping"
            timestamp=""
            placement="top"
            type="success"
            :hollow="false"
          >
            <el-card shadow="hover">
              <h4>发货记录</h4>
              <p>发货单号: {{ traceData.shipping.shippingNo }}</p>
              <p>发货数量: {{ traceData.shipping.quantity }}</p>
              <p>发货日期: {{ traceData.shipping.shippingDate }}</p>
              <p>物流单号: {{ traceData.shipping.logisticsNo || '-' }}</p>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </template>

      <el-empty v-else-if="!loading" description="请输入查询条件进行质量追溯" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import request from '@/utils/request'

// --- state ---
const loading = ref(false)
const traceData = ref(null)

const queryParams = reactive({
  workOrderNo: '',
  productName: '',
  batchNo: ''
})

const activeStep = computed(() => {
  if (!traceData.value?.processSteps) return 0
  const steps = traceData.value.processSteps
  const lastCompleted = steps.findLastIndex(s => s.completeTime)
  return lastCompleted >= 0 ? lastCompleted + 1 : 0
})

// --- methods ---
async function handleSearch() {
  if (!queryParams.workOrderNo && !queryParams.productName && !queryParams.batchNo) {
    return
  }
  loading.value = true
  try {
    const res = await request.get('/quality/traceability', {
      params: {
        workOrderNo: queryParams.workOrderNo || undefined,
        productName: queryParams.productName || undefined,
        batchNo: queryParams.batchNo || undefined
      }
    })
    traceData.value = res.data || null
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryParams.workOrderNo = ''
  queryParams.productName = ''
  queryParams.batchNo = ''
  traceData.value = null
}
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
</style>
