<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="质检项目管理" name="item">
          <ItemPage v-if="activeTab === 'item'" />
        </el-tab-pane>
        <el-tab-pane label="质检模板管理" name="template">
          <TemplatePage v-if="activeTab === 'template'" />
        </el-tab-pane>
        <el-tab-pane label="质检任务" name="task">
          <TaskPage v-if="activeTab === 'task'" />
        </el-tab-pane>
        <el-tab-pane label="质检记录" name="record">
          <RecordPage v-if="activeTab === 'record'" />
        </el-tab-pane>
        <el-tab-pane label="质检分析" name="analysis">
          <AnalysisPage v-if="activeTab === 'analysis'" />
        </el-tab-pane>
        <el-tab-pane label="废料统计" name="waste">
          <WastePage v-if="activeTab === 'waste'" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import ItemPage from './item/index.vue'
import TemplatePage from './template/index.vue'
import TaskPage from './task/index.vue'
import RecordPage from './record/index.vue'
import AnalysisPage from './analysis/index.vue'
import WastePage from './waste/index.vue'

const activeTab = ref('item')
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
