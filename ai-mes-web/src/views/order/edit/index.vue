<template>
  <div class="page-container">
    <el-page-header @back="$router.push('/order/list')" title="返回订单列表"
      :content="changeMode ? '订单变更' : '编辑销售订单'" style="margin-bottom: 16px" />

    <!-- 变更模式说明: 让业务员明白当前操作不会立刻影响订单 -->
    <el-alert
      v-if="changeMode"
      type="warning"
      show-icon
      :closable="false"
      title="当前是「订单变更」编辑模式: 改动不会立刻影响正式订单, 保存的内容会留作变更草稿, 点「提交变更审批」后由审批人审批通过, 才会真正应用到订单数据(并自动联动调整生产计划/工单/外协)。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never">
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <!-- 信用额度预警 -->
        <el-alert v-if="creditWarning.show" :title="creditWarning.message" type="warning" show-icon :closable="false" style="margin-bottom: 16px" />

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="订单号">
              <el-input v-model="formData.orderNo" placeholder="留空则自动生成" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                <el-input :value="selectedCustomer?.customerName || ''" placeholder="请选择客户" readonly style="flex: 1" />
                <el-button type="primary" plain @click="openCustomerPicker">选择客户</el-button>
              </div>
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
            <el-form-item label="业务员" prop="salesmanId">
              <el-select v-model="formData.salesmanId" placeholder="请选择业务员" filterable style="width: 100%">
                <el-option v-for="u in userOptions" :key="u.id" :label="u.name || u.realName" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交货日期" prop="deliveryDate">
              <el-date-picker v-model="formData.deliveryDate" type="date" placeholder="选择交货日期"
                value-format="YYYY-MM-DD" style="width: 100%"
                :disabled-date="(time) => time.getTime() < Date.now() - 86400000" />
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

        <!-- 客户信息卡 -->
        <el-descriptions v-if="selectedCustomer" :column="3" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="客户编号">{{ selectedCustomer.customerNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ settlementLabel(selectedCustomer.settlementType) }}</el-descriptions-item>
          <el-descriptions-item label="信用额度">{{ selectedCustomer.creditLimit > 0 ? `¥${selectedCustomer.creditLimit}` : '不限' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">订单明细</el-divider>

        <el-table :data="formData.items" border size="small">
          <el-table-column label="产品编号" width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.productNo">{{ row.productNo }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="产品" min-width="200">
            <template #default="{ row }">
              <div style="display: flex; gap: 4px; align-items: center">
                <el-input
                  :value="row.productName || ''"
                  placeholder="请选择产品" readonly size="small" style="flex: 1" />
                <el-button size="small" type="primary" plain @click="openProductPicker(row)">选择</el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="规格" width="100">
            <template #default="{ row }">
              <span style="color: #909399">{{ $fmtSpec(row.productSpec) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="可用库存" width="80" align="center">
            <template #default="{ row }">
              <span style="color: #909399">{{ row.availableQty ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="1" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="换算数量" width="120" align="center">
            <template #default="{ row }">
              <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #409eff; font-size: 13px">
                {{ secondaryDisplay(row.quantity, row.unitConfig) }}
              </span>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="含税单价(元)" width="140">
            <template #default="{ row }">
              <div style="display: flex; gap: 2px; align-items: center">
                <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" controls-position="right" style="flex: 1" />
                <el-button v-if="row.productId" size="small" type="info" link @click="showPriceHistory(row)" title="历史价格">
                  <el-icon><Clock /></el-icon>
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="税率(%)" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.taxRate" :min="0" :max="100" :precision="2" size="small"
                controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="不含税单价" width="110" align="right">
            <template #default="{ row }">
              <span style="color: #909399; font-size: 13px">¥{{ ((row.unitPrice || 0) / (1 + (row.taxRate || 0) / 100)).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="含税金额" width="110" align="right">
            <template #default="{ row }">
              <span style="color: #e6a23c; font-weight: 500">¥{{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <!-- 行级扩展字段(由 sales_order_item 模块的字段配置动态渲染) -->
          <el-table-column v-for="fc in itemFormFieldConfigs" :key="fc.fieldKey" :label="fc.fieldLabel" min-width="130">
            <template #default="{ row }">
              <el-date-picker v-if="fc.fieldType === 'date'"
                v-model="row.extFields[fc.fieldKey]" type="date" size="small" style="width: 100%"
                value-format="YYYY-MM-DD" :placeholder="`选择${fc.fieldLabel}`" />
              <el-input-number v-else-if="fc.fieldType === 'number'"
                v-model="row.extFields[fc.fieldKey]" :precision="2" size="small"
                controls-position="right" style="width: 100%" />
              <el-select v-else-if="fc.fieldType === 'select'"
                v-model="row.extFields[fc.fieldKey]" size="small" style="width: 100%" placeholder="请选择">
                <el-option v-for="opt in itemExtFields.parseOptions(fc.fieldOptions)" :key="opt" :label="opt" :value="opt" />
              </el-select>
              <el-input v-else v-model="row.extFields[fc.fieldKey]" size="small" :placeholder="fc.fieldLabel" />
            </template>
          </el-table-column>
          <el-table-column label="备注" width="130">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="small" placeholder="行备注" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="items-footer">
          <el-button type="primary" plain size="small" @click="addItem">+ 添加产品行</el-button>
          <div class="total-amount">
            含税合计金额: <span class="amount">¥{{ totalAmount.toFixed(2) }}</span>
          </div>
        </div>

        <!-- 附件上传 -->
        <el-divider content-position="left">附件</el-divider>
        <el-upload ref="uploadRef" :auto-upload="false" multiple :limit="5" accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.png"
          :on-change="onFileChange" :on-remove="onFileRemove" :file-list="fileList">
          <el-button size="small" type="primary" plain>选择文件</el-button>
          <template #tip><div class="el-upload__tip">支持 PDF/Word/Excel/图片，最多5个文件</div></template>
        </el-upload>
      </el-form>

      <div class="form-actions">
        <el-button @click="$router.push('/order/list')">取消</el-button>
        <!-- 普通编辑(草稿订单): 直接保存 -->
        <el-button v-if="!changeMode" type="primary" :loading="submitLoading" @click="handleSubmit('save')">保存</el-button>
        <!-- 变更模式(?changeId): 三个按钮替代 -->
        <template v-else>
          <el-button :loading="submitLoading" @click="discardChange">放弃变更</el-button>
          <el-button type="primary" plain :loading="submitLoading" @click="saveChangeDraft">保存草稿</el-button>
          <el-button type="success" :loading="submitLoading" @click="submitChangeForApproval">提交变更审批</el-button>
        </template>
      </div>
    </el-card>

    <!-- ==================== 客户选择弹窗 ==================== -->
    <el-dialog v-model="customerPickerVisible" title="选择客户" width="820px" destroy-on-close>
      <div style="margin-bottom: 12px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
        <el-input v-model="customerPickerQuery.keyword" placeholder="客户名称" clearable style="width: 180px"
          @keyup.enter="searchCustomerPicker" />
        <el-input v-model="customerPickerQuery.customerNo" placeholder="客户编码" clearable style="width: 150px"
          @keyup.enter="searchCustomerPicker" />
        <el-button type="primary" @click="searchCustomerPicker">查询</el-button>
        <el-button @click="resetCustomerPicker">重置</el-button>
        <el-button type="success" style="margin-left: auto" @click="newCustomerVisible = true">+ 新增客户</el-button>
      </div>
      <el-table :data="customerPickerList" border size="small" v-loading="customerPickerLoading"
        @row-dblclick="selectCustomer" highlight-current-row style="width: 100%" height="360">
        <el-table-column prop="customerNo" label="客户编码" width="130" />
        <el-table-column prop="customerName" label="客户名称" min-width="150" />
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="settlementType" label="结算方式" width="110" />
        <el-table-column label="操作" width="70" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="selectCustomer(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 10px; display: flex; justify-content: flex-end">
        <el-pagination small background layout="total, prev, pager, next"
          :total="customerPickerTotal" :page-size="customerPickerQuery.pageSize"
          v-model:current-page="customerPickerQuery.pageNum"
          @current-change="loadCustomerPicker" />
      </div>
    </el-dialog>

    <!-- 新增客户弹窗 -->
    <el-dialog v-model="newCustomerVisible" title="新增客户" width="500px" destroy-on-close append-to-body>
      <el-form :model="newCustomer" :rules="newCustomerRules" ref="newCustomerRef" label-width="90px">
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model="newCustomer.customerName" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="newCustomer.contactName" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="newCustomer.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="newCustomer.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="newCustomer.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="结算方式">
          <el-select v-model="newCustomer.settlementType" placeholder="请选择" filterable allow-create style="width: 100%">
            <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="信用额度">
          <el-input-number v-model="newCustomer.creditLimit" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="newCustomer.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newCustomerVisible = false">取消</el-button>
        <el-button type="primary" :loading="newCustomerLoading" @click="doCreateCustomer">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 产品选择弹窗 ==================== -->
    <el-dialog v-model="productPickerVisible" title="选择产品" width="1000px" destroy-on-close @open="onProductPickerOpen">
      <div style="display: flex; gap: 12px; height: 460px;">
        <!-- 左侧分类树 -->
        <div style="width: 200px; min-width: 200px; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px;">
          <div style="font-size: 13px; font-weight: 600; color: #303133;">物料类别</div>
          <el-input v-model="productCategoryFilter" placeholder="搜索类别" clearable size="small"
            @input="(v) => productCategoryTreeRef?.filter(v)" />
          <el-tree
            ref="productCategoryTreeRef"
            :data="productCategoryTree"
            :props="{ children: 'children', label: 'categoryName' }"
            node-key="id"
            highlight-current
            default-expand-all
            :filter-node-method="(v, d) => !v || d.categoryName.includes(v)"
            style="flex: 1;"
            @node-click="(d) => { productPickerQuery.categoryId = d.id; searchProductPicker() }"
          />
          <el-button v-if="productPickerQuery.categoryId" type="info" link size="small"
            @click="() => { productPickerQuery.categoryId = undefined; productCategoryTreeRef?.setCurrentKey(null); searchProductPicker() }">
            清除筛选
          </el-button>
        </div>
        <!-- 右侧内容 -->
        <div style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px;">
          <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: center;">
            <el-input v-model="productPickerQuery.materialNo" placeholder="产品编号" clearable style="width: 150px"
              @keyup.enter="searchProductPicker" />
            <el-input v-model="productPickerQuery.materialName" placeholder="产品名称" clearable style="width: 150px"
              @keyup.enter="searchProductPicker" />
            <el-input v-model="productPickerQuery.spec" placeholder="规格(单值或 压力等级:300 材质:NBR)" clearable style="width: 250px"
              @keyup.enter="searchProductPicker" />
            <el-button type="primary" @click="searchProductPicker">查询</el-button>
            <el-button @click="resetProductPicker">重置</el-button>
            <el-button type="success" style="margin-left: auto" @click="openNewMaterial">+ 新增物料</el-button>
          </div>
          <el-table ref="productPickerTableRef" :data="productPickerList" border size="small" v-loading="productPickerLoading"
            :row-key="row => row.id"
            @selection-change="productPickerSelected = $event"
            @row-dblclick="(row) => confirmProductPicker([row])" style="flex: 1;">
            <el-table-column type="selection" width="40" reserve-selection />
            <!-- 全部去掉 show-overflow-tooltip,内容长时自动换行显示完整 -->
            <el-table-column prop="materialNo" label="产品编号" min-width="180" class-name="cell-wrap" />
            <el-table-column prop="materialName" label="产品名称" min-width="220" class-name="cell-wrap" />
            <el-table-column label="规格型号" min-width="180" class-name="cell-wrap">
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="65" align="center" />
            <el-table-column prop="standardCost" label="参考单价" width="100" align="right">
              <template #default="{ row }">{{ row.standardCost ? `¥${Number(row.standardCost).toFixed(2)}` : '-' }}</template>
            </el-table-column>
          </el-table>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <el-pagination small background layout="total, prev, pager, next"
              :total="productPickerTotal" :page-size="productPickerQuery.pageSize"
              v-model:current-page="productPickerQuery.pageNum"
              @current-change="loadProductPicker" />
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="font-size: 13px; color: #606266;">已选 {{ productPickerSelected.length }} 条</span>
              <el-button @click="productPickerVisible = false">取消</el-button>
              <el-button type="primary" :disabled="productPickerSelected.length === 0" @click="confirmProductPicker()">确认选择</el-button>
            </div>
          </div>
        </div>
      </div>
      <template #footer></template>
    </el-dialog>

    <!-- 新增物料弹窗 -->
    <el-dialog v-model="newMaterialVisible" title="新增物料" width="800px" destroy-on-close append-to-body>
      <el-form :model="newMaterial" :rules="newMaterialRules" ref="newMaterialRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物料编号" prop="materialNo">
              <el-input v-model="newMaterial.materialNo" placeholder="留空则自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称" prop="materialName">
              <el-input v-model="newMaterial.materialName" placeholder="请输入物料名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物料类型" prop="materialType">
              <el-select v-model="newMaterial.materialType" placeholder="请选择物料类型" style="width: 100%" @change="handleNewMaterialTypeChange">
                <el-option v-for="t in materialTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料类别">
              <el-tree-select v-model="newMaterial.categoryId" :data="newMaterialCategoryTree"
                :props="{ children: 'children', label: 'categoryName', value: 'id' }"
                placeholder="请选择物料类别" clearable check-strictly style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规格型号">
          <MultiSpecEditor v-model="newMaterial.spec" />
        </el-form-item>
        <el-form-item label="计量单位" prop="unit">
          <MultiUnitEditor v-model="newMaterial.unit" v-model:unit-config="newMaterial.unitConfig" :unit-options="unitOptions" />
        </el-form-item>
        <el-form-item label="物料图片">
          <el-upload :action="uploadUrl" :headers="uploadHeaders" list-type="picture-card"
            :class="{ 'hide-upload': newMaterialImageFileList.length >= 1 }"
            v-model:file-list="newMaterialImageFileList" :on-success="onNewMaterialImageSuccess"
            :before-upload="beforeNewMaterialImageUpload" :limit="1" accept="image/*">
            <el-icon :size="24"><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="物料属性">
          <el-checkbox-group v-model="newMaterial.materialAttributesList">
            <el-checkbox value="purchase" label="采购" />
            <el-checkbox value="production" label="生产" />
            <el-checkbox value="sales" label="销售" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="允许锁库">
          <el-switch v-model="newMaterial.allowStockLock" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="newMaterial.responsibleUserId" placeholder="请选择负责人" clearable filterable style="width: 100%">
            <el-option v-for="u in newMaterialUserOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>

        <template v-if="newMaterial.materialType === 'finished'">
          <el-divider content-position="left">成品信息</el-divider>
          <el-form-item label="图纸编号">
            <el-input v-model="newMaterial.drawingNo" placeholder="请输入图纸编号" />
          </el-form-item>
          <el-form-item label="生产模式">
            <el-radio-group v-model="newMaterial.productionMode">
              <el-radio value="MTS">按库存生产(MTS)</el-radio>
              <el-radio value="MTO">按订单生产(MTO)</el-radio>
              <el-radio value="MIXED">混合模式(MIXED)</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="newMaterial.productionMode === 'MTS'">
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="目标库存"><el-input-number v-model="newMaterial.targetStock" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="补货触发点"><el-input-number v-model="newMaterial.reorderPoint" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="每次补货量"><el-input-number v-model="newMaterial.reorderQty" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            </el-row>
          </template>
        </template>

        <el-divider content-position="left">库存与成本</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="标准成本"><el-input-number v-model="newMaterial.standardCost" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最小库存"><el-input-number v-model="newMaterial.minStock" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最大库存"><el-input-number v-model="newMaterial.maxStock" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="默认仓库">
              <el-select v-model="newMaterial.defaultWarehouseId" placeholder="请选择" clearable style="width: 100%">
                <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认供应商">
              <el-select v-model="newMaterial.defaultSupplierId" placeholder="搜索供应商" clearable filterable style="width: 100%">
                <el-option v-for="item in supplierOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="保质期天数"><el-input-number v-model="newMaterial.shelfLifeDays" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item></el-col>
        </el-row>

        <el-divider content-position="left">MRP参数</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="物料来源">
              <el-select v-model="newMaterial.sourceType" placeholder="采购" style="width: 100%">
                <el-option label="采购" value="purchase" />
                <el-option label="自制" value="manufacture" />
                <el-option label="委外" value="outsource" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="采购提前期"><el-input-number v-model="newMaterial.purchaseLeadTime" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="生产提前期"><el-input-number v-model="newMaterial.productionLeadTime" :min="0" :precision="0" controls-position="right" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="最小批量"><el-input-number v-model="newMaterial.minOrderQty" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="批量倍数"><el-input-number v-model="newMaterial.orderQtyMultiple" :min="0" :precision="2" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="参与MRP"><el-switch v-model="newMaterial.mrpEnabled" /></el-form-item></el-col>
        </el-row>

        <el-form-item label="图纸附件">
          <el-upload :action="uploadUrl" :headers="uploadHeaders"
            v-model:file-list="newMaterialAttachmentFileList" :on-success="onNewMaterialAttachmentSuccess"
            :limit="10" multiple>
            <el-button type="primary" plain size="small"><el-icon><Plus /></el-icon> 上传图纸</el-button>
            <template #tip><div style="font-size: 12px; color: #909399;">支持 PDF、DWG、DXF、图片等格式，单文件不超过20MB</div></template>
          </el-upload>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="newMaterial.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="newMaterialVisible = false">取消</el-button>
        <el-button type="primary" :loading="newMaterialLoading" @click="doCreateMaterial">保存</el-button>
      </template>
    </el-dialog>

    <!-- 历史价格弹窗 -->
    <el-dialog v-model="priceHistoryVisible" title="历史价格" width="580px" destroy-on-close>
      <el-table :data="priceHistoryData" border size="small" v-loading="priceHistoryLoading">
        <el-table-column prop="orderNo" label="订单号" min-width="140" />
        <el-table-column prop="unitPrice" label="单价" width="100" align="right">
          <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column prop="orderDate" label="订单日期" width="120">
          <template #default="{ row }">{{ (row.orderDate || '').substring(0, 10) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!priceHistoryLoading && priceHistoryData.length === 0" description="暂无历史价格记录" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Plus } from '@element-plus/icons-vue'
import { createSalesOrder, updateSalesOrder, submitSalesOrder, checkCreditLimit, getPriceHistory, uploadOrderAttachment, getSalesOrderDetail,
  getOrderChangeDiff, saveOrderChangeDraft, submitOrderChange, discardOrderChangeDraft } from '@/api/order'
import { useExtFields } from '@/composables/useExtFields'
import { getCustomerPage, createCustomer, getMaterialPage, getMaterialCategoryTree, createMaterial, getUnitSelect, getSupplierSelect } from '@/api/master'
import { getUserSelect, getSettlementTypes, saveSettlementTypes, getWarehouseSelect } from '@/api/system'
import { getMaterialStocks } from '@/api/inventory'
import { useAiChatStore } from '@/store/ai-chat'
import { useUserStore } from '@/store/user'
import MultiSpecEditor from '@/components/MultiSpecEditor.vue'
import MultiUnitEditor from '@/components/MultiUnitEditor.vue'

const router = useRouter()
const route = useRoute()
const editingId = ref(null)
const aiChatStore = useAiChatStore()
const userStore = useUserStore()

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

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'salesOrder' && data.records?.length) {
    const rec = data.records[0]
    Object.keys(formData).forEach(k => { if (k !== 'items' && rec[k] !== undefined) formData[k] = rec[k] })
    if (rec.items && Array.isArray(rec.items)) { formData.items = rec.items }
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入订单表单，请确认后提交')
  }
})

const settlementTypeOptions = ref(['现结', '月结30天', '月结60天', '月结90天'])
function settlementLabel(t) { return t || '-' }
async function addSettlementType(val) {
  if (val && !settlementTypeOptions.value.includes(val)) {
    settlementTypeOptions.value.push(val)
    try { await saveSettlementTypes(settlementTypeOptions.value) } catch {}
  }
}

// ==================== 基础选项 ====================
const userOptions = ref([])
const selectedCustomer = ref(null)

async function loadSelectOptions() {
  try {
    const [userRes, stRes] = await Promise.all([getUserSelect(), getSettlementTypes()])
    userOptions.value = userRes.data || []
    if (stRes.data?.configValue) settlementTypeOptions.value = JSON.parse(stRes.data.configValue)
    // 业务员默认选中当前登录用户
    if (!formData.salesmanId && userStore.userInfo?.id) {
      formData.salesmanId = userStore.userInfo.id
    }
  } catch {}
}

// ==================== 表单 ====================
const formRef = ref(null)
const submitLoading = ref(false)
const formData = reactive({
  orderNo: '', customerId: null, contactName: '', shippingAddress: '', settlementType: '',
  salesmanId: null, deliveryDate: '', remark: '', extFields: {},
  items: [{ productId: null, productNo: '', productName: '', productSpec: '', quantity: 1, unitPrice: 0, taxRate: 13, deliveryDate: '', remark: '', availableQty: null, extFields: {} }]
})

// ==================== 扩展字段配置 ====================
const { formFieldConfigs, loadFieldConfigs, parseOptions, serializeExtFields, initExtFields } = useExtFields('sales_order')
// 行级扩展字段
const itemExtFields = useExtFields('sales_order_item')
const itemFormFieldConfigs = itemExtFields.formFieldConfigs

const formRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  contactName: [],
  shippingAddress: [],
  settlementType: [{ required: true, message: '请选择结算方式', trigger: 'change' }],
  salesmanId: [{ required: true, message: '请选择业务员', trigger: 'change' }],
  deliveryDate: [{ required: true, message: '请选择交货日期', trigger: 'change' }]
}

const totalAmount = computed(() => {
  // unitPrice 现在就是含税单价, 含税合计 = qty × 含税单价
  return formData.items.reduce((sum, item) => sum + (item.quantity || 0) * (item.unitPrice || 0), 0)
})

function addItem() {
  formData.items.push({ productId: null, productNo: '', productName: '', productSpec: '', quantity: 1, unitPrice: 0, taxRate: 13, deliveryDate: '', remark: '', availableQty: null, unitConfig: '', extFields: {} })
}
function removeItem(idx) {
  if (formData.items.length <= 1) { ElMessage.warning('至少保留一条明细'); return }
  formData.items.splice(idx, 1)
}

// ==================== 信用额度 ====================
const creditWarning = reactive({ show: false, message: '' })

async function doCreditCheck() {
  if (!formData.customerId || totalAmount.value <= 0) { creditWarning.show = false; return }
  try {
    const res = await checkCreditLimit(formData.customerId, totalAmount.value)
    const d = res.data || {}
    if (d.isOverLimit) {
      creditWarning.show = true
      creditWarning.message = `信用预警：信用额度 ¥${Number(d.creditLimit || 0).toFixed(2)}，当前应收 ¥${Number(d.currentOutstanding || 0).toFixed(2)}，加本单后 ¥${Number(d.newTotal || 0).toFixed(2)}，已超限`
    } else { creditWarning.show = false }
  } catch { creditWarning.show = false }
}

let creditTimer = null
watch(totalAmount, () => { clearTimeout(creditTimer); creditTimer = setTimeout(doCreditCheck, 800) })

// ==================== 历史价格 ====================
const priceHistoryVisible = ref(false)
const priceHistoryLoading = ref(false)
const priceHistoryData = ref([])

async function showPriceHistory(row) {
  priceHistoryVisible.value = true
  priceHistoryLoading.value = true
  try {
    const res = await getPriceHistory(row.productId, formData.customerId)
    priceHistoryData.value = res.data || []
  } catch {
    priceHistoryData.value = []
  } finally { priceHistoryLoading.value = false }
}

// ==================== 附件 ====================
const uploadRef = ref(null)
const fileList = ref([])
function onFileChange(file) { fileList.value.push(file) }
function onFileRemove(file) { fileList.value = fileList.value.filter(f => f.uid !== file.uid) }

async function uploadAttachments(orderId, orderNo) {
  const rawFiles = fileList.value.map(f => f.raw).filter(Boolean)
  if (rawFiles.length === 0) return
  for (const raw of rawFiles) {
    try {
      await uploadOrderAttachment(orderId, raw)
    } catch {
      ElMessage.warning(`附件「${raw.name}」上传失败，订单已保存`)
    }
  }
}

// ==================== 提交 ====================
// 变更模式: 把当前 form 包成 { order, items } payload (items 带原行 id 用于级联匹配)
// 同时把显示用的 customerName / productName / productNo / spec 也带上, 草稿重载和审批 diff 视图都要看
function buildChangePayload() {
  return {
    order: {
      orderNo: formData.orderNo?.trim() || undefined,
      customerId: formData.customerId,
      customerName: selectedCustomer.value?.customerName || '',
      contactName: formData.contactName,
      shippingAddress: formData.shippingAddress,
      settlementType: formData.settlementType,
      salesmanId: formData.salesmanId,
      deliveryDate: formData.deliveryDate || undefined,
      remark: formData.remark,
      extFields: serializeExtFields(formData.extFields)
    },
    items: formData.items.map((item, i) => ({
      id: editingItemIds.value[i] || null,  // 保留旧 id 让 update 走 cascade 而非 insert
      productId: item.productId,
      productNo: item.productNo,
      productCode: item.productNo,
      productName: item.productName,
      productSpec: item.productSpec,
      spec: item.productSpec,
      unit: item.unit,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      taxRate: item.taxRate,
      deliveryDate: item.deliveryDate || undefined,
      remark: item.remark,
      extFields: itemExtFields.serializeExtFields(item.extFields)
    })).filter(item => item.productId)  // 过滤未选产品的空行
  }
}

async function saveChangeDraft() {
  await formRef.value.validate()
  const validCount = formData.items.filter(i => i.productId).length
  if (validCount === 0) { ElMessage.warning('请至少添加一条有效的订单明细'); return }
  const skipped = formData.items.length - validCount
  if (skipped > 0) ElMessage.info(`已自动忽略 ${skipped} 条未选产品的明细`)
  submitLoading.value = true
  try {
    await saveOrderChangeDraft(changeId.value, buildChangePayload())
    ElMessage.success('草稿已保存')
  } finally { submitLoading.value = false }
}

async function submitChangeForApproval() {
  await formRef.value.validate()
  const validCount = formData.items.filter(i => i.productId).length
  if (validCount === 0) { ElMessage.warning('请至少添加一条有效的订单明细'); return }
  const skipped = formData.items.length - validCount
  if (skipped > 0) ElMessage.info(`已自动忽略 ${skipped} 条未选产品的明细`)
  submitLoading.value = true
  try {
    // 提交前先存一次最新草稿
    await saveOrderChangeDraft(changeId.value, buildChangePayload())
    const res = await submitOrderChange(changeId.value)
    if (res?.data?.approvalEnabled) {
      ElMessage.success('变更已提交审批，审批人将在「我的待办」看到')
    } else {
      ElMessage.warning('变更已提交，但「订单变更」未配审批流，请管理员到系统-审批流程管理新增')
    }
    router.push({ path: '/order/detail', query: { id: editingId.value } })
  } finally { submitLoading.value = false }
}

async function discardChange() {
  try {
    await ElMessageBox.confirm('放弃此次变更编辑? 草稿内容将丢失', '放弃变更', { type: 'warning' })
  } catch { return }
  submitLoading.value = true
  try {
    await discardOrderChangeDraft(changeId.value)
    ElMessage.success('已放弃变更')
    router.push({ path: '/order/detail', query: { id: editingId.value } })
  } finally { submitLoading.value = false }
}

async function handleSubmit(mode) {
  await formRef.value.validate()
  const validItems = formData.items.filter(i => i.productId)
  if (validItems.length === 0) { ElMessage.warning('请至少添加一条有效的订单明细'); return }
  const skipped = formData.items.length - validItems.length
  if (skipped > 0) ElMessage.info(`已自动忽略 ${skipped} 条未选产品的明细`)
  if (creditWarning.show && mode === 'submit') {
    try { await ElMessageBox.confirm('该客户信用额度已超限，确定仍然提交？', '信用超限提示', { type: 'warning', confirmButtonText: '仍然提交' }) }
    catch { return }
  }

  submitLoading.value = true
  try {
    const payload = {
      orderNo: formData.orderNo?.trim() || undefined,
      customerId: formData.customerId, contactName: formData.contactName,
      shippingAddress: formData.shippingAddress, settlementType: formData.settlementType,
      salesmanId: formData.salesmanId, deliveryDate: formData.deliveryDate || undefined,
      remark: formData.remark,
      extFields: serializeExtFields(formData.extFields),
      items: validItems.map(item => ({
        productId: item.productId, quantity: item.quantity,
        unitPrice: item.unitPrice, taxRate: item.taxRate,
        deliveryDate: item.deliveryDate || undefined, remark: item.remark,
        extFields: itemExtFields.serializeExtFields(item.extFields)
      }))
    }
    if (!editingId.value) {
      ElMessage.error('未取到订单ID')
      return
    }
    await updateSalesOrder(editingId.value, payload)
    await uploadAttachments(editingId.value, formData.orderNo || 'ORDER')
    ElMessage.success('订单已更新')
    if (formData.settlementType) addSettlementType(formData.settlementType)
    router.push('/order/list')
  } finally { submitLoading.value = false }
}

// ==================== 编辑模式：加载已有订单 ====================
async function loadOrderForEdit(id) {
  try {
    const res = await getSalesOrderDetail(id)
    const d = res.data || {}
    formData.orderNo = d.orderNo || ''
    formData.customerId = d.customerId
    formData.contactName = d.contactName || ''
    formData.shippingAddress = d.shippingAddress || ''
    formData.settlementType = d.settlementType || ''
    formData.salesmanId = d.salesmanId
    formData.deliveryDate = d.deliveryDate || ''
    formData.remark = d.remark || ''
    initExtFields(formData, d.extFields)
    // 客户回显
    if (d.customerId) {
      selectedCustomer.value = {
        id: d.customerId,
        customerName: d.customerName || `客户#${d.customerId}`
      }
    }
    // 明细回显; 保留每行 id 用于变更模式下 cascade 匹配
    editingItemIds.value = (d.items || []).map(it => it.id || null)
    formData.items = (d.items || []).map(item => {
      let extFields = {}
      if (item.extFields) {
        try { extFields = typeof item.extFields === 'string' ? JSON.parse(item.extFields) : item.extFields } catch {}
      }
      return {
        productId: item.productId,
        productNo: item.productNo || item.productCode || '',
        productName: item.productName || item.materialName || '',
        productSpec: item.productSpec || item.spec || '',
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        taxRate: item.taxRate ?? 13,
        unit: item.unit || '',
        unitConfig: item.unitConfig || '',
        deliveryDate: item.deliveryDate || '',
        remark: item.remark || '',
        availableQty: item.availableQty ?? null,
        extFields
      }
    })
    if (formData.items.length === 0) {
      formData.items = [{ productId: null, productNo: '', productName: '', productSpec: '', quantity: 1, unitPrice: 0, taxRate: 13, deliveryDate: '', remark: '', availableQty: null, extFields: {} }]
    }
  } catch (e) {
    ElMessage.error('加载订单失败')
  }
}

// ==================== 客户选择弹窗 ====================
const customerPickerVisible = ref(false)
const customerPickerLoading = ref(false)
const customerPickerList = ref([])
const customerPickerTotal = ref(0)
const customerPickerQuery = reactive({ keyword: '', customerNo: '', pageNum: 1, pageSize: 10 })

function openCustomerPicker() {
  customerPickerVisible.value = true
  loadCustomerPicker()
}

async function loadCustomerPicker() {
  customerPickerLoading.value = true
  try {
    const res = await getCustomerPage({ ...customerPickerQuery, status: 'active' })
    customerPickerList.value = res.data?.records || res.data?.list || []
    customerPickerTotal.value = res.data?.total || 0
  } finally { customerPickerLoading.value = false }
}

function searchCustomerPicker() {
  customerPickerQuery.pageNum = 1
  loadCustomerPicker()
}

function resetCustomerPicker() {
  customerPickerQuery.keyword = ''
  customerPickerQuery.customerNo = ''
  customerPickerQuery.pageNum = 1
  loadCustomerPicker()
}

function selectCustomer(row) {
  formData.customerId = row.id
  selectedCustomer.value = row
  if (row.contactName) formData.contactName = row.contactName
  if (row.address) formData.shippingAddress = row.address
  if (row.settlementType) formData.settlementType = row.settlementType
  customerPickerVisible.value = false
  doCreditCheck()
}

// ==================== 新增客户 ====================
const newCustomerVisible = ref(false)
const newCustomerLoading = ref(false)
const newCustomerRef = ref(null)
const newCustomer = reactive({ customerName: '', contactName: '', phone: '', email: '', address: '', settlementType: '', creditLimit: 0, remark: '' })
const newCustomerRules = {
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }]
}

async function doCreateCustomer() {
  await newCustomerRef.value.validate()
  newCustomerLoading.value = true
  try {
    await createCustomer({ ...newCustomer })
    ElMessage.success('客户创建成功')
    newCustomerVisible.value = false
    Object.assign(newCustomer, { customerName: '', contactName: '', phone: '', email: '', address: '', settlementType: '', creditLimit: 0, remark: '' })
    loadCustomerPicker()
  } finally { newCustomerLoading.value = false }
}

// ==================== 产品选择弹窗 ====================
const productPickerVisible = ref(false)
const productPickerLoading = ref(false)
const productPickerList = ref([])
const productPickerTotal = ref(0)
const productPickerSelected = ref([])
const productPickerTableRef = ref(null)
const productCategoryTree = ref([])
const productCategoryTreeRef = ref(null)
const productCategoryFilter = ref('')
const productPickerQuery = reactive({ materialNo: '', materialName: '', spec: '', categoryId: undefined, pageNum: 1, pageSize: 10 })
let currentPickerRow = null

async function onProductPickerOpen() {
  productPickerQuery.materialNo = ''
  productPickerQuery.materialName = ''
  productPickerQuery.spec = ''
  productPickerQuery.categoryId = undefined
  productPickerQuery.pageNum = 1
  productPickerSelected.value = []
  productCategoryFilter.value = ''
  // 清空 table 内部跨页保留的勾选,避免上次的勾选残留到本次
  if (productPickerTableRef.value && productPickerTableRef.value.clearSelection) {
    productPickerTableRef.value.clearSelection()
  }
  if (!productCategoryTree.value.length) {
    const res = await getMaterialCategoryTree()
    productCategoryTree.value = res.data || []
  }
  loadProductPicker()
}

function openProductPicker(row) {
  currentPickerRow = row
  productPickerVisible.value = true
}

async function loadProductPicker() {
  productPickerLoading.value = true
  try {
    const res = await getMaterialPage({
      materialNo: productPickerQuery.materialNo || undefined,
      materialName: productPickerQuery.materialName || undefined,
      spec: productPickerQuery.spec || undefined,
      categoryId: productPickerQuery.categoryId || undefined,
      pageNum: productPickerQuery.pageNum,
      pageSize: productPickerQuery.pageSize,
      materialAttribute: 'sales'
    })
    productPickerList.value = res.data?.records || res.data?.list || []
    productPickerTotal.value = res.data?.total || 0
  } finally { productPickerLoading.value = false }
}

function searchProductPicker() {
  productPickerQuery.pageNum = 1
  loadProductPicker()
}

function resetProductPicker() {
  productPickerQuery.materialNo = ''
  productPickerQuery.materialName = ''
  productPickerQuery.spec = ''
  productPickerQuery.categoryId = undefined
  productPickerQuery.pageNum = 1
  productCategoryFilter.value = ''
  productCategoryTreeRef.value?.setCurrentKey(null)
  loadProductPicker()
}

function selectProduct(row) {
  // kept for backward compat (auto-select after create material)
  confirmProductPicker([row])
}

function confirmProductPicker(rows) {
  const selected = rows || productPickerSelected.value
  if (!selected.length) return
  selected.forEach((row, idx) => {
    const target = idx === 0 && currentPickerRow ? currentPickerRow : (() => {
      const item = { productId: null, productNo: '', productName: '', productSpec: '', quantity: 1, unitPrice: 0, taxRate: 13, deliveryDate: '', remark: '', availableQty: null, unitConfig: '' }
      formData.items.push(item)
      return item
    })()
    target.productId = row.id
    target.productNo = row.materialNo || ''
    target.productName = row.materialName
    target.productSpec = row.spec || ''
    target.unitConfig = row.unitConfig || ''
    if (row.standardCost && !target.unitPrice) {
      target.unitPrice = Number(row.standardCost)
    }
    loadProductStock(target)
  })
  productPickerVisible.value = false
}

async function loadProductStock(row) {
  if (!row.productId) return
  try {
    const res = await getMaterialStocks([row.productId])
    const stock = res.data?.[row.productId]
    row.availableQty = stock ? Number(stock.availableQty || 0) : 0
  } catch { row.availableQty = 0 }
}

// ==================== 新增物料 ====================
const newMaterialVisible = ref(false)
const newMaterialLoading = ref(false)
const newMaterialRef = ref(null)
const unitOptions = ref([])
const warehouseOptions = ref([])
const supplierOptions = ref([])
const supplierSearchLoading = ref(false)
const newMaterialImageFileList = ref([])
const newMaterialAttachmentFileList = ref([])
const materialTypeOptions = [
  { label: '成品', value: 'finished' }, { label: '半成品', value: 'semi_finished' },
  { label: '原材料', value: 'raw_material' }, { label: '辅料', value: 'auxiliary' },
  { label: '包材', value: 'packaging' }, { label: '外购件', value: 'purchased' }
]
const newMaterialCategoryTree = ref([])

const getNewMaterialDefault = () => ({
  materialNo: '', materialName: '', spec: '', materialType: undefined, unit: undefined, unitConfig: '',
  categoryId: undefined, images: [], materialAttributesList: ['sales'], allowStockLock: true,
  drawingNo: '', responsibleUserId: userStore.userInfo?.id || undefined,
  productionMode: undefined, targetStock: undefined, reorderPoint: undefined, reorderQty: undefined,
  standardCost: undefined, minStock: undefined, maxStock: undefined,
  defaultWarehouseId: undefined, defaultSupplierId: undefined, shelfLifeDays: undefined,
  sourceType: 'purchase', purchaseLeadTime: 0, productionLeadTime: 0,
  minOrderQty: undefined, orderQtyMultiple: undefined, mrpEnabled: true,
  remark: ''
})
const newMaterial = reactive(getNewMaterialDefault())
const newMaterialRules = {
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  unit: [{ required: true, message: '请选择计量单位', trigger: 'change' }]
}
const newMaterialUserOptions = ref([])

const uploadUrl = (import.meta.env.VITE_API_BASE || '/api') + '/common/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + (localStorage?.getItem('token') || '') }))

async function openNewMaterial() {
  Object.assign(newMaterial, getNewMaterialDefault())
  newMaterialImageFileList.value = []
  newMaterialAttachmentFileList.value = []
  newMaterialVisible.value = true
  try {
    const [unitRes, whRes, supRes, catRes, userRes] = await Promise.all([
      unitOptions.value.length ? null : getUnitSelect(),
      warehouseOptions.value.length ? null : getWarehouseSelect(),
      supplierOptions.value.length ? null : getSupplierSelect(),
      newMaterialCategoryTree.value.length ? null : getMaterialCategoryTree(),
      newMaterialUserOptions.value.length ? null : getUserSelect()
    ])
    if (unitRes) unitOptions.value = unitRes.data || []
    if (whRes) warehouseOptions.value = whRes.data || []
    if (supRes) supplierOptions.value = supRes.data || []
    if (catRes) newMaterialCategoryTree.value = catRes.data || []
    if (userRes) newMaterialUserOptions.value = userRes.data || []
  } catch {}
}

function handleNewMaterialTypeChange() {
  if (newMaterial.materialType !== 'finished') {
    newMaterial.productionMode = undefined; newMaterial.targetStock = undefined
    newMaterial.reorderPoint = undefined; newMaterial.reorderQty = undefined
  }
}

function onNewMaterialImageSuccess(res, file) {
  if (res.code === 200 || res.code === 0) file.url = res.data?.url || res.data || ''
  else { ElMessage.error(res.msg || '上传失败'); const i = newMaterialImageFileList.value.indexOf(file); if (i > -1) newMaterialImageFileList.value.splice(i, 1) }
}
function beforeNewMaterialImageUpload(file) {
  if (!file.type.startsWith('image/')) { ElMessage.error('只能上传图片文件'); return false }
  if (file.size / 1024 / 1024 >= 5) { ElMessage.error('图片大小不能超过5MB'); return false }
  return true
}
function onNewMaterialAttachmentSuccess(res, file) {
  if (res.code === 200 || res.code === 0) file.url = res.data?.url || res.data || ''
  else { ElMessage.error(res.msg || '上传失败'); const i = newMaterialAttachmentFileList.value.indexOf(file); if (i > -1) newMaterialAttachmentFileList.value.splice(i, 1) }
}

async function doCreateMaterial() {
  await newMaterialRef.value.validate()
  newMaterialLoading.value = true
  try {
    const data = { ...newMaterial }
    data.images = newMaterialImageFileList.value.map(f => f.url || f.response?.data?.url || '').filter(Boolean)
    if (data.images.length > 0) data.imageUrl = data.images[0]
    data.attachmentUrls = newMaterialAttachmentFileList.value.map(f => ({ name: f.name, url: f.url || f.response?.data?.url || '' })).filter(a => a.url)
    data.materialAttributes = (data.materialAttributesList || []).join(',')
    delete data.materialAttributesList
    const res = await createMaterial(data)
    const newId = res.data
    ElMessage.success('物料创建成功')
    newMaterialVisible.value = false
    await loadProductPicker()
    if (newId && currentPickerRow) {
      const found = productPickerList.value.find(p => p.id === newId)
      if (found) selectProduct(found)
    }
  } finally { newMaterialLoading.value = false }
}

// ==================== 变更模式 ====================
// URL 带 ?changeId=Y → 变更模式: 数据从草稿加载, 三个按钮替代普通保存
const changeId = ref(null)
const changeMode = computed(() => !!changeId.value)
const editingItemIds = ref([])

async function loadChangeDraftIfAny(cid, oid) {
  // 1) 先加载 detail 获取 customerName/productName 等 join 字段
  await loadOrderForEdit(oid)
  // 2) 再读 diff: 有 proposed 草稿就 overlay
  try {
    const res = await getOrderChangeDiff(cid)
    const data = res.data || {}
    const source = data.proposed
    if (source) overlaySnapshot(source)
  } catch (e) {
    console.warn('[loadChangeDraftIfAny] failed:', e)
  }
  return true
}

function overlaySnapshot(snap) {
  const o = snap.order || {}
  if (o.orderNo !== undefined) formData.orderNo = o.orderNo || ''
  if (o.customerId !== undefined) formData.customerId = o.customerId
  if (o.contactName !== undefined) formData.contactName = o.contactName || ''
  if (o.shippingAddress !== undefined) formData.shippingAddress = o.shippingAddress || ''
  if (o.settlementType !== undefined) formData.settlementType = o.settlementType || ''
  if (o.salesmanId !== undefined) formData.salesmanId = o.salesmanId
  if (o.deliveryDate !== undefined) formData.deliveryDate = o.deliveryDate || ''
  if (o.remark !== undefined) formData.remark = o.remark || ''
  if (o.extFields !== undefined) initExtFields(formData, o.extFields)
  if (o.customerId && (!selectedCustomer.value || selectedCustomer.value.id !== o.customerId)) {
    selectedCustomer.value = { id: o.customerId, customerName: o.customerName || selectedCustomer.value?.customerName || '' }
  }
  const items = snap.items || []
  if (items.length) {
    const oldById = new Map()
    formData.items.forEach((it, i) => {
      const id = editingItemIds.value[i]
      if (id != null) oldById.set(id, { item: it, idx: i })
    })
    const merged = []
    const mergedIds = []
    for (const sn of items) {
      const matched = sn.id != null ? oldById.get(sn.id) : null
      if (matched) {
        merged.push({
          ...matched.item,
          productId: sn.productId ?? matched.item.productId,
          productNo: sn.productNo || sn.productCode || matched.item.productNo,
          productName: sn.productName || sn.materialName || matched.item.productName,
          productSpec: sn.productSpec || sn.spec || matched.item.productSpec,
          quantity: sn.quantity ?? matched.item.quantity,
          unitPrice: sn.unitPrice ?? matched.item.unitPrice,
          taxRate: sn.taxRate ?? matched.item.taxRate ?? 13,
          deliveryDate: sn.deliveryDate ?? matched.item.deliveryDate,
          remark: sn.remark ?? matched.item.remark
        })
        mergedIds.push(sn.id)
      } else {
        merged.push({
          productId: sn.productId,
          productNo: sn.productNo || sn.productCode || '',
          productName: sn.productName || sn.materialName || '',
          productSpec: sn.productSpec || sn.spec || '',
          quantity: sn.quantity,
          unitPrice: sn.unitPrice,
          taxRate: sn.taxRate ?? 13,
          deliveryDate: sn.deliveryDate || '',
          unitConfig: sn.unitConfig || '',
          remark: sn.remark || ''
        })
        mergedIds.push(sn.id || null)
      }
    }
    formData.items = merged
    editingItemIds.value = mergedIds
  }
}

// ==================== 初始化 ====================
async function initFromRoute() {
  await loadSelectOptions()
  await loadFieldConfigs()
  await itemExtFields.loadFieldConfigs()
  const id = route.query.id ? Number(route.query.id) : null
  if (!id) {
    ElMessage.error('缺少订单ID')
    router.replace('/order/list')
    return
  }
  editingId.value = id
  changeId.value = route.query.changeId ? Number(route.query.changeId) : null
  if (changeMode.value) {
    await loadChangeDraftIfAny(changeId.value, id)
  } else {
    await loadOrderForEdit(id)
  }
}
onMounted(initFromRoute)
// keep-alive 缓存场景：tab 重新激活时如果 url 仍带 id 但表单为空,则重新加载
onActivated(() => {
  const id = route.query.id ? Number(route.query.id) : null
  const cid = route.query.changeId ? Number(route.query.changeId) : null
  if (id && !editingId.value) {
    editingId.value = id
    changeId.value = cid
    if (cid) loadChangeDraftIfAny(cid, id); else loadOrderForEdit(id)
  }
})
// 监听 query.id 变化（tab 缓存场景）
watch(() => [route.query.id, route.query.changeId], async ([newId, newCid], [oldId]) => {
  if (newId === oldId || !newId) return
  editingId.value = Number(newId)
  changeId.value = newCid ? Number(newCid) : null
  if (changeId.value) await loadChangeDraftIfAny(changeId.value, Number(newId))
  else await loadOrderForEdit(Number(newId))
})
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.items-footer {
  display: flex; justify-content: space-between; align-items: center; margin-top: 12px;
}
.total-amount {
  font-size: 14px; color: #606266;
  .amount { font-size: 20px; font-weight: 700; color: #f56c6c; }
}
.form-actions {
  display: flex; justify-content: flex-end; margin-top: 24px; padding-top: 16px;
  border-top: 1px solid #ebeef5; gap: 8px;
}
:deep(.cell-wrap .cell) {
  white-space: normal;
  word-break: break-all;
  line-height: 1.5;
}
</style>