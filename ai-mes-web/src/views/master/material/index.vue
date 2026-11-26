<template>
  <div class="page-container">
    <div class="main-layout">
      <!-- ========== 左侧分类树 ========== -->
      <div class="category-panel">
        <div class="category-header">
          <span>物料类别</span>
          <el-button type="primary" link size="small" @click="handleAddCategory">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
        <el-input v-model="categoryFilter" placeholder="搜索类别" clearable size="small" style="margin-bottom: 8px" />
        <el-tree ref="categoryTreeRef" :data="categoryTree" :props="{ children: 'children', label: 'categoryName' }"
          node-key="id" highlight-current default-expand-all :filter-node-method="filterCategoryNode"
          @node-click="handleCategoryClick">
          <template #default="{ node, data }">
            <span class="tree-node">
              <span>{{ node.label }}</span>
              <span class="tree-actions" @click.stop>
                <el-button type="primary" link size="small" @click="handleEditCategory(data)">
                  <el-icon size="12"><Edit /></el-icon>
                </el-button>
                <el-button type="danger" link size="small" @click="handleDeleteCategory(data)">
                  <el-icon size="12"><Delete /></el-icon>
                </el-button>
              </span>
            </span>
          </template>
        </el-tree>
        <el-button v-if="selectedCategoryId" type="info" link size="small" style="margin-top: 8px"
          @click="clearCategoryFilter">清除筛选</el-button>
      </div>

      <!-- ========== 右侧内容 ========== -->
      <div class="content-panel">
        <!-- 搜索栏 -->
        <div class="search-bar">
          <el-form :model="queryParams" inline>
            <el-form-item label="物料编号">
              <el-input v-model="queryParams.materialNo" placeholder="物料编号" clearable
                style="width: 150px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable
                style="width: 150px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="规格">
              <el-input v-model="queryParams.spec" placeholder="规格" clearable
                style="width: 150px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="物料类型">
              <el-select v-model="queryParams.materialTypes" placeholder="全部类型" clearable multiple
                collapse-tags collapse-tags-tooltip style="width: 260px" @change="handleTypeFilterChange">
                <el-option v-for="t in materialTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="showProductionModeFilter" label="生产模式">
              <el-select v-model="queryParams.productionMode" placeholder="全部" clearable style="width: 150px">
                <el-option label="按库存生产(MTS)" value="MTS" />
                <el-option label="按订单生产(MTO)" value="MTO" />
                <el-option label="混合模式(MIXED)" value="MIXED" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100px">
                <el-option label="启用" value="active" />
                <el-option label="停用" value="inactive" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 工具栏 -->
        <div class="toolbar">
          <div class="toolbar-left">
            <el-button v-permission="'master:material:add'" type="primary" @click="handleAdd">新增物料</el-button>
            <el-button class="ai-btn" @click="aiFilingVisible = true">
              <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI建档
            </el-button>
            <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
            <el-button type="warning" :icon="Upload" @click="importDialogVisible = true">导入</el-button>
            <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
          </div>
          <div class="toolbar-right">
            <span class="total-count">共 {{ total }} 条物料</span>
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button value="table"><el-icon><List /></el-icon></el-radio-button>
              <el-radio-button value="card"><el-icon><Grid /></el-icon></el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- ========== 表格视图 ========== -->
        <template v-if="viewMode === 'table'">
          <el-table :data="tableData" v-loading="loading" border stripe size="small">
            <el-table-column
              v-for="col in visibleColumns" :key="col.key"
              :label="col.label"
              :min-width="col.minWidth || 100"
              :width="col.width"
              :align="col.align || 'left'"
              show-overflow-tooltip>
              <template #default="{ row }">
                <template v-if="col.key === 'materialNo'">
                  <el-button type="primary" link @click="openDetail(row)">{{ row.materialNo }}</el-button>
                </template>
                <template v-else-if="col.key === 'image'">
                  <el-image v-if="getFirstImage(row)" :src="getFirstImage(row)"
                    :preview-src-list="getImageList(row)" preview-teleported fit="cover"
                    style="width: 40px; height: 40px; border-radius: 4px" />
                  <div v-else class="table-image-placeholder">
                    <el-icon :size="18" color="#c0c4cc"><Picture /></el-icon>
                  </div>
                </template>
                <template v-else-if="col.key === 'spec'">{{ formatSpec(row.spec) }}</template>
                <template v-else-if="col.key === 'materialType'">
                  <el-tag :type="typeTagMap[row.materialType]" size="small">
                    {{ materialTypeMap[row.materialType] || row.materialType }}
                  </el-tag>
                </template>
                <template v-else-if="col.key === 'unit'">{{ formatUnit(row) }}</template>
                <template v-else-if="col.key === 'materialAttributes'">
                  <template v-if="row.materialAttributes">
                    <el-tag v-for="attr in parseAttributes(row.materialAttributes)" :key="attr"
                      size="small" :type="attrTagType[attr]" style="margin: 1px 2px">
                      {{ attrLabelMap[attr] || attr }}
                    </el-tag>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
                <template v-else-if="col.key === 'minStock'">{{ row.minStock != null ? row.minStock : '-' }}</template>
                <template v-else-if="col.key === 'maxStock'">{{ row.maxStock != null ? row.maxStock : '-' }}</template>
                <template v-else-if="col.key === '_totalQty'">
                  <span :class="row._totalQty === 0 ? 'text-muted' : ''">{{ row._totalQty ?? '-' }}</span>
                </template>
                <template v-else-if="col.key === '_availableQty'">
                  <span :class="row._availableQty <= 0 ? 'text-danger' : ''">{{ row._availableQty ?? '-' }}</span>
                </template>
                <template v-else-if="col.key === 'status'">
                  <el-switch v-model="row.status" active-value="active" inactive-value="inactive"
                    size="small" @change="handleToggleStatus(row)" />
                </template>
                <template v-else-if="col.key === 'hasProcess'">
                  <template v-if="row.materialType === 'finished'">
                    <el-icon v-if="row.hasProcess" color="#67c23a" :size="18"><CircleCheckFilled /></el-icon>
                    <el-icon v-else color="#c0c4cc" :size="18"><CircleCloseFilled /></el-icon>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
                <template v-else-if="col.key === 'productionMode'">
                  <template v-if="row.materialType === 'finished'">
                    <el-tag v-if="row.productionMode" size="small" :type="productionModeTagType[row.productionMode]">
                      {{ productionModeMap[row.productionMode] }}
                    </el-tag>
                    <span v-else class="text-muted">-</span>
                  </template>
                  <span v-else class="text-muted">-</span>
                </template>
                <template v-else>{{ row[col.key] || '-' }}</template>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-permission="'master:material:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
                <el-button type="info" link size="small" @click="handleCopy(row)">复制</el-button>
                <el-button v-if="row.materialType === 'finished' || row.materialType === 'semi_finished'"
                  type="success" link size="small" @click="handleBom(row)">BOM</el-button>
                <el-button v-if="row.materialType === 'finished'"
                  type="warning" link size="small" @click="handleProcess(row)">工艺</el-button>
                <el-button v-permission="'master:material:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- ========== 卡片视图 ========== -->
        <template v-else>
          <div v-loading="loading" class="card-grid">
            <el-card v-for="item in tableData" :key="item.id" shadow="hover" class="material-card"
              @click="openDetail(item)">
              <div class="card-image">
                <el-image v-if="getFirstImage(item)" :src="getFirstImage(item)" fit="cover"
                  style="width: 100%; height: 140px" />
                <div v-else class="card-image-placeholder">
                  <el-icon :size="40" color="#c0c4cc"><Picture /></el-icon>
                </div>
              </div>
              <div class="card-body">
                <div class="card-no">{{ item.materialNo }}</div>
                <div class="card-title">{{ item.materialName }}</div>
                <div class="card-spec">{{ formatSpec(item.spec) || '-' }}</div>
                <div class="card-tags">
                  <el-tag :type="typeTagMap[item.materialType]" size="small">
                    {{ materialTypeMap[item.materialType] }}
                  </el-tag>
                </div>
                <div class="card-price">
                  <span v-if="item.standardCost" class="price-value">¥{{ Number(item.standardCost).toFixed(2) }}</span>
                  <span v-else class="text-muted">未定价</span>
                </div>
              </div>
              <div class="card-actions" @click.stop>
                <el-button type="primary" link size="small" @click="handleEdit(item)">编辑</el-button>
                <el-button type="info" link size="small" @click="handleCopy(item)">复制</el-button>
                <el-button type="danger" link size="small" @click="handleDelete(item)">删除</el-button>
              </div>
            </el-card>
            <el-empty v-if="!loading && tableData.length === 0" description="暂无数据" />
          </div>
        </template>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
            :page-sizes="[10, 20, 50, 100]" :total="total"
            layout="total, sizes, prev, pager, next, jumper" background
            @size-change="loadData" @current-change="loadData" />
        </div>
      </div>
    </div>

    <!-- ========== 详情抽屉 ========== -->
    <el-drawer v-model="detailVisible" :title="detailData?.materialName || '物料详情'" size="600px">
      <template v-if="detailData">
        <div v-if="getImageList(detailData).length" class="detail-images">
          <el-image v-for="(img, idx) in getImageList(detailData)" :key="idx"
            :src="img" :preview-src-list="getImageList(detailData)" :initial-index="idx"
            fit="contain" class="detail-image-item" />
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="物料编号">{{ detailData.materialNo }}</el-descriptions-item>
          <el-descriptions-item label="物料名称">{{ detailData.materialName }}</el-descriptions-item>
          <el-descriptions-item label="规格型号">{{ formatSpec(detailData.spec) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物料类型">
            <el-tag :type="typeTagMap[detailData.materialType]" size="small">
              {{ materialTypeMap[detailData.materialType] }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="计量单位">{{ formatUnit(detailData) }}</el-descriptions-item>
          <el-descriptions-item label="物料类别">{{ detailData.categoryName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="物料属性">
            <template v-if="detailData.materialAttributes">
              <el-tag v-for="attr in parseAttributes(detailData.materialAttributes)" :key="attr"
                size="small" :type="attrTagType[attr]" style="margin: 1px 2px">{{ attrLabelMap[attr] || attr }}</el-tag>
            </template>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="允许锁库">
            <el-tag :type="detailData.allowStockLock ? 'success' : 'danger'" size="small">
              {{ detailData.allowStockLock ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标准成本(含税)">
            {{ detailData.standardCost ? '¥' + Number(detailData.standardCost).toFixed(2) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="最小库存">{{ detailData.minStock ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最大库存">{{ detailData.maxStock ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="默认仓库">{{ detailData.defaultWarehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="默认供应商">{{ detailData.defaultSupplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="保质期">
            {{ detailData.shelfLifeDays ? detailData.shelfLifeDays + ' 天' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailData.status === 'active' ? 'success' : 'danger'" size="small">
              {{ detailData.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </el-descriptions-item>
          <template v-if="detailData.materialType === 'finished'">
            <el-descriptions-item label="生产模式">{{ productionModeMap[detailData.productionMode] || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工艺状态">
              <el-tag :type="detailData.hasProcess ? 'success' : 'info'" size="small">
                {{ detailData.hasProcess ? '已配置' : '未配置' }}
              </el-tag>
            </el-descriptions-item>
          </template>
          <template v-if="detailData.materialType === 'finished' || detailData.materialType === 'semi_finished'">
            <el-descriptions-item label="BOM状态">
              <el-tag :type="detailData.hasBom ? 'success' : 'info'" size="small">
                {{ detailData.hasBom ? '已配置' : '未配置' }}
              </el-tag>
            </el-descriptions-item>
          </template>
          <el-descriptions-item label="创建时间">{{ detailData.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <!-- 图纸附件 -->
        <template v-if="detailData.attachmentUrls && detailData.attachmentUrls.length">
          <div style="margin-top: 16px; font-weight: 500; margin-bottom: 8px">图纸附件</div>
          <AttachmentList :value="detailData.attachmentUrls" />
        </template>
        <div class="detail-actions">
          <el-button type="primary" @click="handleEdit(detailData); detailVisible = false">编辑</el-button>
          <el-button type="info" @click="handleCopy(detailData); detailVisible = false">复制</el-button>
          <el-button v-if="detailData.materialType === 'finished' || detailData.materialType === 'semi_finished'"
            type="success" @click="handleBom(detailData); detailVisible = false">管理BOM</el-button>
          <el-button v-if="detailData.materialType === 'finished'"
            type="warning" @click="handleProcess(detailData); detailVisible = false">管理工艺</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- ========== 新增/编辑弹窗 ========== -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="800px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物料编号" prop="materialNo">
              <el-input v-model="form.materialNo" placeholder="留空则自动生成" :disabled="!!editingId">
                <template #append>
                  <el-tooltip content="成品P+6位 / 原料M+6位，留空自动生成" placement="top">
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="form.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物料类型" prop="materialType">
              <el-select v-model="form.materialType" placeholder="请选择物料类型" style="width: 100%"
                @change="handleFormTypeChange">
                <el-option v-for="t in materialTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料类别" prop="categoryId">
              <el-tree-select v-model="form.categoryId" :data="categoryTree"
                :props="{ children: 'children', label: 'categoryName', value: 'id' }"
                placeholder="请选择物料类别" clearable check-strictly style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规格型号" prop="spec">
          <MultiSpecEditor v-model="form.spec" />
        </el-form-item>
        <el-form-item label="计量单位" prop="unit">
          <MultiUnitEditor v-model="form.unit" v-model:unit-config="form.unitConfig" :unit-options="unitOptions" />
        </el-form-item>
        <el-form-item label="物料图片">
          <AttachmentUploader v-model="form.images" list-type="picture-card" accept="image/*"
            :limit="1" button-text="上传图片" tip="仅支持图片,最多 1 张,不超过 5MB" :max-m-b="5" />
        </el-form-item>
        <el-form-item label="物料属性">
          <el-checkbox-group v-model="form.materialAttributesList">
            <el-checkbox value="purchase" label="采购" />
            <el-checkbox value="production" label="生产" />
            <el-checkbox value="sales" label="销售" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="允许锁库">
          <el-switch v-model="form.allowStockLock" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.responsibleUserId" placeholder="请选择负责人" clearable filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>

        <template v-if="form.materialType === 'finished'">
          <el-divider content-position="left">成品信息</el-divider>
          <el-form-item label="图纸编号">
            <el-input v-model="form.drawingNo" placeholder="请输入图纸编号" />
          </el-form-item>
          <el-form-item label="生产模式" prop="productionMode">
            <el-radio-group v-model="form.productionMode">
              <el-radio value="MTS">按库存生产(MTS)</el-radio>
              <el-radio value="MTO">按订单生产(MTO)</el-radio>
              <el-radio value="MIXED">混合模式(MIXED)</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="form.productionMode === 'MTS'">
            <el-row :gutter="16">
              <el-col :span="8">
                <el-form-item label="目标库存"><el-input-number v-model="form.targetStock" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="补货触发点"><el-input-number v-model="form.reorderPoint" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="每次补货量"><el-input-number v-model="form.reorderQty" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item>
              </el-col>
            </el-row>
          </template>
        </template>

        <el-divider content-position="left">库存与成本</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="标准成本(含税)"><el-input-number v-model="form.standardCost" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最小库存"><el-input-number v-model="form.minStock" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大库存"><el-input-number v-model="form.maxStock" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="默认仓库">
              <el-select v-model="form.defaultWarehouseId" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认供应商">
              <el-select v-model="form.defaultSupplierId" placeholder="搜索供应商" clearable filterable
                remote :remote-method="searchSupplier" :loading="supplierSearchLoading" style="width: 100%">
                <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="保质期天数"><el-input-number v-model="form.shelfLifeDays" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">MRP参数</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="物料来源">
              <el-select v-model="form.sourceType" placeholder="采购" style="width: 100%">
                <el-option label="采购" value="purchase" />
                <el-option label="自制" value="manufacture" />
                <el-option label="委外" value="outsource" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="采购提前期">
              <el-input-number v-model="form.purchaseLeadTime" :min="0" :precision="0" controls-position="right" style="width: 100%" />
              <div class="form-tip">天</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="生产提前期">
              <el-input-number v-model="form.productionLeadTime" :min="0" :precision="0" controls-position="right" style="width: 100%" />
              <div class="form-tip">天</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="最小批量"><el-input-number v-model="form.minOrderQty" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批量倍数"><el-input-number v-model="form.orderQtyMultiple" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="参与MRP">
              <el-switch v-model="form.mrpEnabled" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="图纸附件">
          <AttachmentUploader v-model="form.attachmentUrls" button-text="上传图纸"
            tip="支持 PDF、DWG、DXF、图片等格式,单文件不超过 20MB" :max-m-b="20" :limit="10" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- ========== AI建档对话框 ========== -->
    <el-dialog v-model="aiFilingVisible" title="AI智能建档" width="700px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        输入物料描述信息，AI将自动提取物料属性并生成建档数据。
      </el-alert>
      <el-input v-model="aiDescription" type="textarea" :rows="5"
        placeholder="请输入物料描述，例如：&#10;不锈钢304法兰盘，DN50规格，用于化工管道连接，成品类型，按订单生产模式，单位为件，标准成本120元。" />
      <div style="margin-top: 12px; text-align: right">
        <el-button type="primary" :loading="aiFilingLoading" :disabled="!aiDescription.trim()"
          class="ai-btn" @click="handleAiFiling">
          <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI生成
        </el-button>
      </div>
      <template v-if="aiFilingResult">
        <el-divider content-position="left">生成结果</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="物料名称">{{ aiFilingResult.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规格型号">{{ $fmtSpec(aiFilingResult.spec) }}</el-descriptions-item>
          <el-descriptions-item label="物料类型">{{ materialTypeMap[aiFilingResult.materialType] || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计量单位">{{ aiFilingResult.unit || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="aiFilingVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!aiFilingResult" class="ai-btn" @click="handleAiFilingConfirm">确认建档</el-button>
      </template>
    </el-dialog>

    <!-- ========== 导入弹窗 ========== -->
    <el-dialog v-model="importDialogVisible" title="导入物料" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">请先下载模板，按模板格式填写后上传。</el-alert>
      <div style="margin-bottom: 16px;">
        <el-button type="primary" link @click="handleDownloadTemplate"><el-icon style="margin-right: 4px"><Download /></el-icon>下载导入模板</el-button>
      </div>
      <el-upload ref="uploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleFileChange" drag>
        <el-icon :size="40" color="#c0c4cc"><Upload /></el-icon>
        <div style="margin-top: 8px; color: #909399">将文件拖到此处，或点击上传</div>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- ========== BOM弹窗 ========== -->
    <BomDialog
      v-model="bomDialogVisible"
      :product-id="bomDialogProduct?.id"
      :product-name="bomDialogProduct?.name"
    />

    <!-- ========== 工艺路线弹窗 ========== -->
    <ProcessRouteDialog
      v-model="processDialogVisible"
      :product-id="processDialogProduct?.id"
      :product-name="processDialogProduct?.name"
    />

    <!-- ========== 分类编辑弹窗 ========== -->
    <el-dialog :title="categoryDialogTitle" v-model="categoryDialogVisible" width="480px" destroy-on-close>
      <el-form ref="categoryFormRef" :model="categoryForm" :rules="categoryRules" label-width="80px">
        <el-form-item label="类别名称" prop="categoryName">
          <el-input v-model="categoryForm.categoryName" placeholder="请输入类别名称" />
        </el-form-item>
        <el-form-item label="类别编码" prop="categoryCode">
          <el-input v-model="categoryForm.categoryCode" placeholder="请输入类别编码" />
        </el-form-item>
        <el-form-item label="父级类别">
          <el-tree-select v-model="categoryForm.parentId" :data="categoryTree"
            :props="{ children: 'children', label: 'categoryName', value: 'id' }"
            placeholder="无（顶级类别）" clearable check-strictly style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySubmitLoading" @click="handleCategorySubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog
      ref="colConfigDialogRef"
      :all-columns="allColumns"
      :default-columns="MASTER_MATERIAL_DEFAULT_COLUMNS"
      @save="saveConfig"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheckFilled, CircleCloseFilled, MagicStick, Download, Upload,
  Picture, Plus, List, Grid, Delete, QuestionFilled, Edit, Setting
} from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import {
  getMaterialPage, getMaterialDetail, createMaterial, updateMaterial, deleteMaterial,
  exportMaterials, importMaterials, downloadMaterialTemplate,
  getSupplierSelect, getUnitSelect,
  getMaterialCategoryTree, createMaterialCategory, updateMaterialCategory, deleteMaterialCategory
} from '@/api/master'
import { getWarehouseSelect, getUserSelect } from '@/api/system'
import { useUserStore } from '@/store/user'
import { getMaterialStocks } from '@/api/inventory'
import { aiProductFiling } from '@/api/ai'
import MultiSpecEditor from '@/components/MultiSpecEditor.vue'
import MultiUnitEditor from '@/components/MultiUnitEditor.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import AttachmentList from '@/components/AttachmentList.vue'
import ProcessRouteDialog from './ProcessRouteDialog.vue'
import BomDialog from './BomDialog.vue'
import { useAiChatStore } from '@/store/ai-chat'

const router = useRouter()
const aiChatStore = useAiChatStore()
const userStore = useUserStore()
const userOptions = ref([])

const MASTER_MATERIAL_DEFAULT_COLUMNS = [
  { key: 'materialNo', label: '编号', minWidth: 130 },
  { key: 'image', label: '图片', width: 70, align: 'center' },
  { key: 'materialName', label: '名称', minWidth: 160 },
  { key: 'spec', label: '规格型号', minWidth: 140 },
  { key: 'drawingNo', label: '图纸编号', width: 120 },
  { key: 'materialType', label: '物料类型', width: 100, align: 'center' },
  { key: 'unit', label: '计量单位', minWidth: 140 },
  { key: 'materialAttributes', label: '物料属性', width: 160, align: 'center' },
  { key: 'minStock', label: '最小库存', width: 90, align: 'right' },
  { key: 'maxStock', label: '最大库存', width: 90, align: 'right' },
  { key: '_totalQty', label: '当前库存', width: 90, align: 'right' },
  { key: '_availableQty', label: '可用库存', width: 90, align: 'right' },
  { key: 'defaultSupplierName', label: '默认供应商', minWidth: 130 },
  { key: 'remark', label: '备注', minWidth: 140 },
  { key: 'status', label: '状态', width: 80, align: 'center' },
  { key: 'hasProcess', label: '工艺', width: 60, align: 'center' },
  { key: 'productionMode', label: '生产模式', width: 100, align: 'center' },
  { key: '_createByName', label: '创建人', width: 100, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('master_material', MASTER_MATERIAL_DEFAULT_COLUMNS)

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'material' && data.records?.length) {
    dialogTitle.value = '新增物料'; editingId.value = null; resetForm()
    Object.keys(form).forEach(k => { if (data.records[0][k] !== undefined) form[k] = data.records[0][k] })
    dialogVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入物料表单，请确认后提交')
  }
})


const materialTypeOptions = [
  { label: '成品', value: 'finished' }, { label: '半成品', value: 'semi_finished' },
  { label: '原材料', value: 'raw_material' }, { label: '辅料', value: 'auxiliary' },
  { label: '包材', value: 'packaging' }, { label: '外购件', value: 'purchased' }
]
const materialTypeMap = { finished: '成品', semi_finished: '半成品', raw_material: '原材料', auxiliary: '辅料', packaging: '包材', purchased: '外购件' }
const typeTagMap = { finished: '', semi_finished: 'success', raw_material: 'warning', auxiliary: 'info', packaging: 'danger', purchased: '' }
const productionModeMap = { MTS: '按库存生产', MTO: '按订单生产', MIXED: '混合模式' }
const productionModeTagType = { MTS: 'success', MTO: 'warning', MIXED: 'info' }
const attrLabelMap = { purchase: '采购', production: '生产', sales: '销售' }
const attrTagType = { purchase: 'warning', production: 'success', sales: '' }

// ==================== 分类树 ====================
const categoryTreeRef = ref(null)
const categoryTree = ref([])
const categoryFilter = ref('')
const selectedCategoryId = ref(null)

watch(categoryFilter, (val) => { categoryTreeRef.value?.filter(val) })

function filterCategoryNode(value, data) {
  if (!value) return true
  return data.categoryName.includes(value)
}
function handleCategoryClick(data) {
  selectedCategoryId.value = data.id
  queryParams.pageNum = 1
  loadData()
}
function clearCategoryFilter() {
  selectedCategoryId.value = null
  categoryTreeRef.value?.setCurrentKey(null)
  queryParams.pageNum = 1
  loadData()
}
async function loadCategoryTree() {
  try { const res = await getMaterialCategoryTree(); categoryTree.value = res.data || [] } catch {}
}

const categoryDialogVisible = ref(false)
const categoryDialogTitle = ref('新增类别')
const categoryFormRef = ref(null)
const categorySubmitLoading = ref(false)
const editingCategoryId = ref(null)
const categoryForm = reactive({ categoryName: '', categoryCode: '', parentId: null, sortOrder: 0 })
const categoryRules = {
  categoryName: [{ required: true, message: '请输入类别名称', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请输入类别编码', trigger: 'blur' }]
}

function handleAddCategory() {
  editingCategoryId.value = null; categoryDialogTitle.value = '新增类别'
  categoryForm.categoryName = ''; categoryForm.categoryCode = ''; categoryForm.parentId = null; categoryForm.sortOrder = 0
  categoryDialogVisible.value = true
}
function handleEditCategory(data) {
  editingCategoryId.value = data.id; categoryDialogTitle.value = '编辑类别'
  categoryForm.categoryName = data.categoryName; categoryForm.categoryCode = data.categoryCode
  categoryForm.parentId = data.parentId || null; categoryForm.sortOrder = data.sortOrder || 0
  categoryDialogVisible.value = true
}
async function handleDeleteCategory(data) {
  try {
    await ElMessageBox.confirm(`确认删除类别「${data.categoryName}」？`, '提示', { type: 'warning' })
    await deleteMaterialCategory(data.id)
    ElMessage.success('删除成功')
    loadCategoryTree()
    if (selectedCategoryId.value === data.id) { selectedCategoryId.value = null; loadData() }
  } catch {}
}
async function handleCategorySubmit() {
  await categoryFormRef.value.validate()
  categorySubmitLoading.value = true
  try {
    if (editingCategoryId.value) { await updateMaterialCategory(editingCategoryId.value, categoryForm); ElMessage.success('更新成功') }
    else { await createMaterialCategory(categoryForm); ElMessage.success('新增成功') }
    categoryDialogVisible.value = false; loadCategoryTree()
  } catch {} finally { categorySubmitLoading.value = false }
}

// ==================== 查询 ====================
const queryParams = reactive({ materialNo: '', materialName: '', spec: '', materialTypes: [], productionMode: undefined, status: undefined, pageNum: 1, pageSize: 20 })
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
let userSelectMap = null
const viewMode = ref('table')

const showProductionModeFilter = computed(() => queryParams.materialTypes.length > 0 && queryParams.materialTypes.includes('finished'))
function handleTypeFilterChange() { if (!showProductionModeFilter.value) queryParams.productionMode = undefined }

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (params.materialTypes?.length > 0) params.materialTypes = params.materialTypes.join(',')
    else delete params.materialTypes
    if (selectedCategoryId.value) params.categoryId = selectedCategoryId.value
    const res = await getMaterialPage(params)
    const records = res.data?.records || []
    total.value = res.data?.total || 0
    // 批量查库存汇总 + 创建人名称
    if (records.length > 0) {
      try {
        const ids = records.map(r => r.id)
        const [stockRes, userRes] = await Promise.all([getMaterialStocks(ids), userSelectMap ? null : getUserSelect()])
        const stockMap = stockRes.data || {}
        // 缓存用户列表
        if (!userSelectMap && userRes) {
          const users = userRes.data || []
          userSelectMap = Object.fromEntries(users.map(u => [u.id, u.realName || u.username]))
        }
        records.forEach(r => {
          const s = stockMap[r.id]
          r._totalQty = s ? s.totalQty : 0
          r._availableQty = s ? s.availableQty : 0
          r._createByName = r.createBy ? (userSelectMap[r.createBy] || '') : ''
        })
      } catch {}
    }
    tableData.value = records
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.materialNo = ''; queryParams.materialName = ''; queryParams.spec = ''; queryParams.materialTypes = []; queryParams.productionMode = undefined; queryParams.status = undefined
  queryParams.pageNum = 1; selectedCategoryId.value = null; categoryTreeRef.value?.setCurrentKey(null); loadData()
}

// ==================== 工具函数 ====================
function getFirstImage(row) { return row.imageUrl || (row.images?.length > 0 ? row.images[0] : '') }
function getImageList(row) { return row.images?.length > 0 ? row.images : row.imageUrl ? [row.imageUrl] : [] }
function formatSpec(spec) {
  if (!spec) return '-'
  try { const p = JSON.parse(spec); if (Array.isArray(p)) return p.map(d => `${d.name}:${(d.values || [d.value]).filter(Boolean).join('/')}`).join(' ') } catch {}
  return spec
}
function formatUnit(row) {
  if (!row.unitConfig) return row.unit || '-'
  try {
    const cfg = JSON.parse(row.unitConfig)
    if (cfg.baseUnit && cfg.conversions?.length > 0) {
      const convStr = cfg.conversions.map(c => `1${cfg.baseUnit}=${c.ratio}${c.unit}`).join(', ')
      return `${cfg.baseUnit} (${convStr})`
    }
  } catch {}
  return row.unit || '-'
}
function parseAttributes(attrs) { return attrs ? attrs.split(',').map(a => a.trim()).filter(Boolean) : [] }

// ==================== 详情抽屉 ====================
const detailVisible = ref(false)
const detailData = ref(null)
async function openDetail(row) {
  try { const res = await getMaterialDetail(row.id); detailData.value = res.data || row } catch { detailData.value = row }
  detailVisible.value = true
}

// ==================== 弹窗 ====================
const dialogVisible = ref(false)
const dialogTitle = ref('新增物料')
const formRef = ref(null)
const submitLoading = ref(false)
const editingId = ref(null)

const getDefaultForm = () => ({
  materialNo: '', materialName: '', spec: '', materialType: undefined, unit: undefined, unitConfig: '',
  categoryId: undefined, images: [], materialAttributesList: [], allowStockLock: true,
  drawingNo: '', responsibleUserId: userStore.userInfo?.id || undefined,
  productionMode: undefined, targetStock: undefined, reorderPoint: undefined, reorderQty: undefined,
  standardCost: undefined, minStock: undefined, maxStock: undefined,
  defaultWarehouseId: undefined, defaultSupplierId: undefined, shelfLifeDays: undefined,
  sourceType: 'purchase', purchaseLeadTime: 0, productionLeadTime: 0,
  minOrderQty: undefined, orderQtyMultiple: undefined, mrpEnabled: true,
  remark: ''
})
const form = reactive(getDefaultForm())
const rules = {
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  unit: [{ required: true, message: '请选择计量单位', trigger: 'change' }],
  productionMode: []
}

function resetForm() { const d = getDefaultForm(); Object.keys(d).forEach(k => { form[k] = d[k] }) }
function handleFormTypeChange() {
  if (form.materialType !== 'finished') { form.productionMode = undefined; form.targetStock = undefined; form.reorderPoint = undefined; form.reorderQty = undefined }
}
function handleAdd() { resetForm(); editingId.value = null; dialogTitle.value = '新增物料'; dialogVisible.value = true }
async function handleEdit(row) {
  resetForm(); editingId.value = row.id; dialogTitle.value = '编辑物料'
  // 列表查询为性能不返 unitConfig 等大字段, 编辑前先拉完整详情, 否则多单位/图片等字段空白
  let full = row
  try { const res = await getMaterialDetail(row.id); full = res.data || row } catch {}
  Object.keys(form).forEach(k => { if (full[k] !== undefined) form[k] = full[k] })
  form.materialAttributesList = full.materialAttributes ? full.materialAttributes.split(',').map(a => a.trim()).filter(Boolean) : []
  form.allowStockLock = full.allowStockLock !== false
  form.images = getImageList(full)
  form.attachmentUrls = full.attachmentUrls || []
  dialogVisible.value = true
}
function handleCopy(row) {
  resetForm(); editingId.value = null; dialogTitle.value = '复制物料（新建）'
  Object.keys(form).forEach(k => { if (row[k] !== undefined) form[k] = row[k] })
  form.materialAttributesList = row.materialAttributes ? row.materialAttributes.split(',').map(a => a.trim()).filter(Boolean) : []
  form.materialNo = ''; form.materialName = (row.materialName || '') + ' (副本)'
  form.images = getImageList(row)
  form.attachmentUrls = row.attachmentUrls || []
  dialogVisible.value = true; ElMessage.info('已复制物料信息，修改后保存为新物料')
}

async function handleSubmit() {
  await formRef.value.validate(); submitLoading.value = true
  try {
    const d = { ...form }
    // form.images 为 URL 字符串数组,form.attachmentUrls 为 {name,url} 数组（由 AttachmentUploader 维护）
    if (Array.isArray(d.images) && d.images.length > 0) d.imageUrl = d.images[0]
    d.materialAttributes = (d.materialAttributesList || []).join(','); delete d.materialAttributesList
    if (editingId.value) { await updateMaterial(editingId.value, d); ElMessage.success('更新成功') }
    else { await createMaterial(d); ElMessage.success('新增成功') }
    dialogVisible.value = false; loadData()
  } catch {} finally { submitLoading.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除物料「${row.materialName}」？此操作不可恢复。`, '提示', { type: 'warning' })
    await deleteMaterial(row.id); ElMessage.success('删除成功'); loadData()
  } catch {}
}

async function handleToggleStatus(row) {
  try { await updateMaterial(row.id, { status: row.status }); ElMessage.success(row.status === 'active' ? '已启用' : '已停用') }
  catch { row.status = row.status === 'active' ? 'inactive' : 'active' }
}


// ==================== BOM弹窗 ====================
const bomDialogVisible = ref(false)
const bomDialogProduct = ref(null)
function handleBom(row) {
  bomDialogProduct.value = row
  bomDialogVisible.value = true
}

// ==================== 工艺路线弹窗 ====================
const processDialogVisible = ref(false)
const processDialogProduct = ref(null)
function handleProcess(row) {
  processDialogProduct.value = row
  processDialogVisible.value = true
}

// ==================== 导入导出 ====================
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const uploadRef = ref(null)

function handleFileChange(file) { importFile.value = file.raw }
function downloadBlob(data, filename) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob); const link = document.createElement('a')
  link.href = url; link.download = filename; link.click(); window.URL.revokeObjectURL(url)
}
async function handleExport() { try { const res = await exportMaterials(); downloadBlob(res, '物料列表.xlsx'); ElMessage.success('导出成功') } catch {} }
async function handleDownloadTemplate() { try { const res = await downloadMaterialTemplate(); downloadBlob(res, '物料导入模板.xlsx') } catch {} }
async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importLoading.value = true
  try { await importMaterials(importFile.value); ElMessage.success('导入成功'); importDialogVisible.value = false; importFile.value = null; loadData() }
  catch {} finally { importLoading.value = false }
}

// ==================== AI建档 ====================
const aiFilingVisible = ref(false)
const aiDescription = ref('')
const aiFilingLoading = ref(false)
const aiFilingResult = ref(null)

async function handleAiFiling() {
  if (!aiDescription.value.trim()) return; aiFilingLoading.value = true; aiFilingResult.value = null
  try { const res = await aiProductFiling(aiDescription.value); aiFilingResult.value = res.data; ElMessage.success('AI生成完成') }
  catch { ElMessage.error('AI生成失败') } finally { aiFilingLoading.value = false }
}
function handleAiFilingConfirm() {
  if (!aiFilingResult.value) return; const r = aiFilingResult.value
  resetForm(); editingId.value = null; dialogTitle.value = '新增物料'
  form.materialName = r.name || ''; form.spec = r.spec || ''; form.materialType = r.materialType || r.category || undefined
  form.unit = r.unit || undefined; form.productionMode = r.productionMode || undefined
  form.standardCost = r.standardCost || undefined; form.remark = r.remark || ''
  aiFilingVisible.value = false; dialogVisible.value = true; ElMessage.success('已填入物料信息')
}

// ==================== 下拉数据 ====================
const warehouseOptions = ref([])
const supplierOptions = ref([])
const supplierSearchLoading = ref(false)
const unitOptions = ref([])

async function loadOptions() {
  try {
    const [w, s, u, users] = await Promise.all([getWarehouseSelect(), getSupplierSelect(), getUnitSelect(), getUserSelect()])
    warehouseOptions.value = w.data || []; supplierOptions.value = s.data || []; unitOptions.value = u.data || []
    userOptions.value = users.data || []
  } catch {}
}
async function searchSupplier(query) {
  if (!query) { try { const r = await getSupplierSelect(); supplierOptions.value = r.data || [] } catch {}; return }
  supplierSearchLoading.value = true
  try { const r = await getSupplierSelect({ keyword: query }); supplierOptions.value = r.data || [] } catch {} finally { supplierSearchLoading.value = false }
}

onMounted(() => { loadData(); loadOptions(); loadCategoryTree(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.main-layout { display: flex; gap: 16px; }

.category-panel {
  width: 240px; min-width: 240px; background: #fff; border: 1px solid #ebeef5; border-radius: 4px;
  padding: 12px; height: fit-content; max-height: calc(100vh - 120px); overflow-y: auto;
}
.category-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; font-size: 14px; margin-bottom: 8px; }
.tree-node { display: flex; justify-content: space-between; align-items: center; width: 100%; font-size: 13px; }
.tree-actions { display: none; }
:deep(.el-tree-node__content:hover) .tree-actions { display: inline-flex; }

.content-panel { flex: 1; min-width: 0; }
.search-bar { margin-bottom: 12px; :deep(.el-form-item) { margin-bottom: 8px; } }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.toolbar-left { display: flex; gap: 8px; flex-wrap: wrap; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.total-count { color: #909399; font-size: 13px; white-space: nowrap; }

.ai-btn {
  background: #7c3aed !important; border-color: #7c3aed !important; color: #fff !important;
  &:hover, &:focus { background: #6d28d9 !important; border-color: #6d28d9 !important; }
  &.is-disabled { opacity: 0.6; }
}

.table-image-placeholder { width: 40px; height: 40px; background: #f5f7fa; border-radius: 4px; display: flex; align-items: center; justify-content: center; margin: 0 auto; }
.text-muted { color: #c0c4cc; }
.text-danger { color: #f56c6c; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }

.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; min-height: 200px; }
.material-card {
  cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;
  &:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); }
  :deep(.el-card__body) { padding: 0; }
}
.card-image-placeholder { width: 100%; height: 140px; background: #f5f7fa; display: flex; align-items: center; justify-content: center; }
.card-body { padding: 12px; }
.card-no { font-size: 12px; color: #909399; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-title { font-weight: 600; font-size: 14px; margin-bottom: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-spec { font-size: 12px; color: #606266; margin-bottom: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-tags { display: flex; gap: 4px; margin-bottom: 8px; flex-wrap: wrap; }
.card-price { .price-value { color: #e6a23c; font-weight: 500; font-size: 14px; } }
.card-actions { padding: 8px 12px; border-top: 1px solid #f0f0f0; display: flex; gap: 4px; }

.detail-images { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; justify-content: center; }
.detail-image-item { width: 120px; height: 120px; border-radius: 8px; border: 1px solid #ebeef5; }
.detail-actions { margin-top: 16px; display: flex; gap: 8px; }
.upload-tip { font-size: 12px; color: #909399; margin-top: 4px; }
.hide-upload :deep(.el-upload--picture-card) { display: none; }
:deep(.el-upload--picture-card) { width: 100px; height: 100px; }
:deep(.el-upload-list--picture-card .el-upload-list__item) { width: 100px; height: 100px; }

.attachment-list { display: flex; flex-direction: column; gap: 6px; }
.attachment-item {
  display: flex; align-items: center; padding: 6px 10px;
  background: #f5f7fa; border-radius: 4px; font-size: 13px;
}
.attachment-name {
  flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 8px;
}
</style>
