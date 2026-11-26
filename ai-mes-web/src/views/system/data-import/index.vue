<template>
  <div class="data-import-page">
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; gap: 8px">
          <el-icon><Upload /></el-icon>
          <span style="font-weight: 600; font-size: 16px">历史数据导入</span>
          <span class="text-muted" style="font-size: 12px; margin-left: 8px">
            请按顺序导入：用户 → 客户/供应商 → 物料规格 → 物料 → 班组 → 工序 → 工艺路线/BOM → 订单
          </span>
          <div style="flex: 1"></div>
          <el-button type="primary" size="small" @click="handleDownloadAllPlainTemplates">
            <el-icon><Download /></el-icon>
            下载全部空白模板
          </el-button>
        </div>
      </template>

      <el-alert type="warning" :closable="false" style="margin-bottom: 20px">
        <template #title>
          <b>注意事项：</b>
          <span>1. 请先导入用户、客户、供应商等基础数据，再导入物料和订单类数据</span><br/>
          <span>2. 导入物料前，必须先导入物料规格（定义规格维度名称，如：材质、尺寸等）</span><br/>
          <span>3. 物料模板中固定列之后可自行添加规格列（表头填写规格名称，需与已导入的规格名称匹配）</span><br/>
          <span>4. BOM和工艺路线依赖物料和工序，请确保已先导入</span><br/>
          <span>5. 编号列留空时系统将自动生成；若填写则不能与已有编号重复</span><br/>
          <span>6. 时间列支持 yyyy-MM-dd 和 yyyy-MM-dd HH:mm:ss 格式，用于保留历史真实日期</span><br/>
          <span>7. 所有模板均含示例数据（浅蓝色背景），导入前请删除或替换为真实数据</span>
        </template>
      </el-alert>

      <el-tabs v-model="activeTab" type="border-card">
        <!-- 用户 -->
        <el-tab-pane name="user" label="用户">
          <import-panel
              type="user"
              title="用户"
              :columns="userColumns"
              :notes="['默认密码为 123456，用户登录后可自行修改', '部门和角色均为下拉选择，角色支持逗号分隔填写多个', '岗位组填写所属工作组，如一车间、质检室、销售一组等']"
          />
        </el-tab-pane>

        <!-- 客户 -->
        <el-tab-pane name="customer" label="客户">
          <import-panel
              type="customer"
              title="客户"
              :columns="customerColumns"
              :notes="['客户编号留空时自动生成', '客户名称不可重复']"
          />
        </el-tab-pane>

        <!-- 供应商 -->
        <el-tab-pane name="supplier" label="供应商">
          <import-panel
              type="supplier"
              title="供应商"
              :columns="supplierColumns"
              :notes="['供应商编号留空时自动生成', '供应商名称不可重复']"
          />
        </el-tab-pane>

        <!-- 物料规格 -->
        <el-tab-pane name="material-spec" label="物料规格">
          <import-panel
              type="material-spec"
              title="物料规格"
              :columns="materialSpecColumns"
              :notes="[
              '物料规格定义规格维度名称（如：材质、尺寸、颜色等）',
              '必须在导入物料之前先导入物料规格',
              '导入物料时，Excel表头中的规格名称将与此表匹配，未匹配的规格列将被忽略'
            ]"
          />
        </el-tab-pane>

        <!-- 物料 -->
        <el-tab-pane name="material" label="物料">
          <import-panel
              type="material"
              title="物料档案"
              :columns="materialColumns"
              :notes="[
              '物料模板的固定列之后，可自行添加规格列（表头填写规格名称）',
              '规格名称必须与已导入的物料规格表中的名称一致',
              '只有表头下方有数据的规格列才会被读取，空列将被忽略',
              '下载模板时会自动将已导入的规格名称作为动态列添加到表头'
            ]"
          />
        </el-tab-pane>

        <!-- 班组 -->
        <el-tab-pane name="team" label="班组">
          <import-panel
              type="team"
              title="班组"
              :columns="teamColumns"
              :notes="[
              '同一班组名称的多行表示该班组的多个成员（每行填一个成员姓名）',
              '组长姓名和成员姓名列有下拉选择，数据来源于系统已有员工',
              '班组级信息（车间、组长等）仅需在第一行填写'
            ]"
          />
        </el-tab-pane>

        <!-- 工序 -->
        <el-tab-pane name="process" label="工序">
          <import-panel
              type="process"
              title="工序库"
              :columns="processColumns"
          />
        </el-tab-pane>

        <!-- 工艺路线 -->
        <el-tab-pane name="process-route" label="工艺路线">
          <import-panel
              type="process-route"
              title="工艺路线"
              :columns="processRouteColumns"
              :notes="['同一路线名称的多行表示该路线下的多道工序', '工序名称必须已在工序库中存在']"
          />
        </el-tab-pane>

        <!-- BOM -->
        <el-tab-pane name="bom" label="BOM">
          <import-panel
              type="bom"
              title="BOM (物料清单)"
              :columns="bomColumns"
              :notes="['同一产品编号+版本号的多行表示该BOM下的多个子件', '子件物料编号必须已在物料档案中存在']"
          />
        </el-tab-pane>

        <!-- 销售订单 -->
        <el-tab-pane name="sales-order" label="销售订单">
          <import-panel
              type="sales-order"
              title="销售订单"
              :columns="salesOrderColumns"
              :notes="[
              '同一订单编号的多行表示该订单下的多个产品明细',
              '客户名称、业务员、产品编号均为下拉选择，产品名称自动带出',
              '导入后订单状态统一为【已确认】',
              '下单日期将作为该订单的创建时间保留'
            ]"
          />
        </el-tab-pane>

        <!-- 采购订单 -->
        <el-tab-pane name="purchase-order" label="采购订单">
          <import-panel
              type="purchase-order"
              title="采购订单"
              :columns="purchaseOrderColumns"
              :notes="[
              '同一采购单号的多行表示该订单下的多个物料明细',
              '供应商名称必须已在供应商档案中存在，物料编号必须已在物料档案中存在',
              '采购员可填写用户名或真实姓名',
              '下单日期将作为该订单的创建时间保留'
            ]"
          />
        </el-tab-pane>

        <!-- 库存台账 -->
        <el-tab-pane name="inventory" label="库存台账">
          <import-panel
              type="inventory"
              title="库存台账"
              :columns="inventoryColumns"
              :notes="[
              '物料编号 / 仓库名称 均为下拉选择',
              '物料名称通过 VLOOKUP 自动带出，无需手填',
              '已存在的(物料+仓库)记录将更新，否则新增',
              '可用数量留空时自动计算 = 在库数量 - 锁定数量',
              '辅助单位数量按物料档案的单位换算比例自动折算，无需手填',
              '最小/最大库存已在物料档案维护，此处不需要填写'
            ]"
          />
        </el-tab-pane>

        <!-- 设备台账 -->
        <el-tab-pane name="equipment" label="设备台账">
          <import-panel
              type="equipment"
              title="设备台账"
              :columns="equipmentColumns"
              :notes="[
              '设备编号留空时自动生成(前缀EQ)',
              '设备类别建议统一:液压支架/焊接设备/机加工设备/切割设备/起重设备/动力设备/检测设备 等',
              '状态: NORMAL(正常)/MAINTENANCE(维修中)/RETIRED(报废)',
              '供应商名称需已在供应商档案中存在'
            ]"
          />
        </el-tab-pane>

        <!-- 保养模版 -->
        <el-tab-pane name="maintenance-template" label="保养模版">
          <import-panel
              type="maintenance-template"
              title="保养模版"
              :columns="maintTemplateColumns"
              :notes="[
              '同一模板名称的多行表示多个保养项',
              '周期类型: DAY/WEEK/MONTH/QUARTER/YEAR',
              '周期值: 如 MONTH+1 表示每月, WEEK+2 表示每两周',
              '关联设备编号可填多个(逗号分隔),仅首行填写'
            ]"
          />
        </el-tab-pane>

        <!-- 点检模版 -->
        <el-tab-pane name="inspection-template" label="点检模版">
          <import-panel
              type="inspection-template"
              title="点检模版"
              :columns="inspTemplateColumns"
              :notes="[
              '同一模板名称的多行表示多个点检项',
              '点检类型: 0=日常点检, 1=定期点检, 2=专项点检',
              '关联设备编号可填多个(逗号分隔),仅首行填写'
            ]"
          />
        </el-tab-pane>

        <!-- 质检体系 -->
        <el-tab-pane name="qc-template" label="质检体系">
          <import-panel
              type="qc-template"
              title="质检体系 (模板+方案+项目)"
              :columns="qcTemplateColumns"
              :notes="[
              '一页汇总: 同一模板的多个检验项 → 多行,模板名列重复',
              '模板头字段(适用类型/抽样方案/抽样模式/默认模板 等)只需第一行填写',
              '抽样方案: 全检 / 抽检  —— 选抽检时需填抽样模式',
              '抽样模式: 首件 / 末件 / 固定数量(填抽检数量) / 按比例(填抽检比例%)',
              '检验类型: 计量型(填标准值+上下限+单位) / 文本型 / 单选(合格/不合格)',
              '适用产品编号、适用物料编号 必须是物料档案里已存在的编号'
            ]"
          />
        </el-tab-pane>

        <!-- 数据生成 -->
        <el-tab-pane name="simulate" label="数据生成">
          <el-alert type="info" :closable="false" style="margin-bottom: 16px">
            <template #title>
              根据已导入的基础数据，按步骤生成业务数据。流程：<b>配置 → 生成预览 → 导出Excel → 人工修改 → 导入</b>。请严格按顺序执行。
            </template>
          </el-alert>

          <!-- 通用配置区 -->
          <el-card shadow="never" style="margin-bottom: 16px">
            <template #header><span style="font-weight: 600">通用配置</span></template>
            <el-form :model="simConfig" label-width="120px" style="max-width: 700px">
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="工作时间">
                    <div style="display: flex; align-items: center; gap: 6px">
                      <el-time-select v-model="simConfig.workStartTime" :max-time="simConfig.workEndTime" placeholder="开始" start="06:00" step="00:30" end="12:00" style="width: 110px" />
                      <span>-</span>
                      <el-time-select v-model="simConfig.workEndTime" :min-time="simConfig.workStartTime" placeholder="结束" start="12:00" step="00:30" end="22:00" style="width: 110px" />
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="避开周末"><el-switch v-model="simConfig.skipWeekends" /></el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="避开节假日"><el-switch v-model="simConfig.skipHolidays" /></el-form-item>
                </el-col>
              </el-row>
              <!-- 人员和仓库移到各模块单独配置 -->
              <el-form-item v-if="simConfig.skipHolidays" label="节假日">
                <el-input v-model="simConfig.holidayStr" type="textarea" :rows="2" placeholder="每行一个日期 yyyy-MM-dd" style="max-width: 400px" />
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 步骤条 -->
          <el-steps :active="currentStep" finish-status="success" style="margin-bottom: 20px">
            <el-step v-for="m in simModules" :key="m.key" :title="m.name"
                     :status="moduleStatus[m.key]" @click.native="selectModule(m)" style="cursor: pointer" />
          </el-steps>

          <!-- 当前模块面板 -->
          <el-card v-if="currentModule" shadow="never">
            <template #header>
              <div style="display: flex; align-items: center; justify-content: space-between">
                <span style="font-weight: 600; font-size: 15px">{{ currentModule.order }}. {{ currentModule.name }}</span>
                <el-tag v-if="moduleStatus[currentModule.key] === 'success'" type="success" size="small">已导入</el-tag>
              </div>
            </template>

            <!-- 模块个性化配置 -->
            <el-form label-width="120px" style="max-width: 600px; margin-bottom: 16px">
              <!-- 创建人（③报工模块不需要，自动取报工人/班组长） -->
              <el-form-item v-if="currentModule.key !== 'work-report'" label="创建人" required>
                <el-select v-model="simConfig.creatorIds" multiple filterable placeholder="选择创建人" style="width: 100%" @focus="loadUsers">
                  <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
                </el-select>
              </el-form-item>

              <!-- ②工单、⑧返修：指派人在Excel中填写，此处不配置 -->
              <el-alert v-if="['work-order','rework'].includes(currentModule.key)" type="info" :closable="false" style="margin-bottom: 12px">
                <template #title>工序指派人(person)和指派班组(team)需在导出的Excel中手动填写</template>
              </el-alert>

              <!-- ③报工：说明 -->
              <el-alert v-if="currentModule.key === 'work-report'" type="info" :closable="false" style="margin-bottom: 12px">
                <template #title>创建人：个人报工默认为工序报工人，班组报工默认为班组长。处理人默认为质检检验员。</template>
              </el-alert>

              <!-- ③报工+质检：检验员 -->
              <el-form-item v-if="currentModule.key === 'work-report'" label="质检检验员" required>
                <el-select v-model="simConfig.inspectorIds" multiple filterable placeholder="选择检验员（同时作为不合格处理人）" style="width: 100%" @focus="loadUsers">
                  <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
                </el-select>
              </el-form-item>

              <!-- ④采购来料：提示 -->
              <el-alert v-if="currentModule.key === 'qc-task'" type="info" :closable="false" style="margin-bottom: 12px">
                <template #title>
                  导出后需人工填写：<b>receipt.xlsx</b> 中的「是否质检」(下拉选是/否)和「质检模板」(下拉选模板)
                </template>
              </el-alert>
              <!-- ④采购来料：收料延迟+处理延迟 -->
              <el-form-item v-if="currentModule.key === 'qc-task'" label="收料提前(天)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.receiptDelayDaysMin" :min="0" :max="10" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.receiptDelayDaysMax" :min="0" :max="10" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">相对采购订单交货日期提前N天收料</span>
                </div>
              </el-form-item>
              <el-form-item v-if="currentModule.key === 'qc-task'" label="处理延迟(时)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.receiptProcessHoursMin" :min="1" :max="24" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.receiptProcessHoursMax" :min="1" :max="24" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">收料后延迟N小时进行质检</span>
                </div>
              </el-form-item>
              <!-- ④采购来料：检验员 -->
              <el-form-item v-if="currentModule.key === 'qc-task'" label="检验员">
                <el-select v-model="simConfig.inspectorIds" multiple filterable placeholder="选择检验员（留空则从创建人中选取）" style="width: 100%" @focus="loadUsers">
                  <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
                </el-select>
              </el-form-item>

              <!-- ⑥外协收料：提示 -->
              <el-alert v-if="currentModule.key === 'outsource-receipt'" type="info" :closable="false" style="margin-bottom: 12px">
                <template #title>
                  导出后需人工填写：<b>outsource_receipt.xlsx</b> 中的「是否质检」(下拉选是/否)和「质检模板」(下拉选模板)
                </template>
              </el-alert>
              <!-- ⑤外协：审批人 -->
              <el-form-item v-if="currentModule.key === 'outsource'" label="审批人">
                <el-select v-model="simConfig.approverIds" multiple filterable placeholder="选择审批人（留空则从创建人中选取）" style="width: 100%" @focus="loadUsers">
                  <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
                </el-select>
              </el-form-item>

              <!-- ⑦外协收料：收货人 + 仓库 -->
              <el-form-item v-if="currentModule.key === 'outsource-receipt'" label="收货人">
                <el-select v-model="simConfig.receiverIds" multiple filterable placeholder="选择收货人（留空则从创建人中选取）" style="width: 100%" @focus="loadUsers">
                  <el-option v-for="u in userOptions" :key="u.id" :label="u.label" :value="u.id" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="currentModule.key === 'outsource-receipt'" label="收货仓库" required>
                <el-select v-model="simConfig.warehouseId" filterable placeholder="选择仓库" style="width: 100%" @focus="loadWarehouses">
                  <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
                </el-select>
              </el-form-item>

              <!-- 业务参数 -->
              <!-- ①生产计划：整单外协配置 -->
              <el-form-item v-if="currentModule.key === 'plan'" label="整单外协">
                <div style="display: flex; align-items: center; gap: 8px">
                  <el-switch v-model="simConfig.enableFullOutsource" />
                  <template v-if="simConfig.enableFullOutsource">
                    <span>比例</span>
                    <el-input-number v-model="simConfig.fullOutsourcePct" :min="5" :max="50" size="small" style="width: 80px" />
                    <span>%</span>
                    <span class="text-muted" style="font-size: 12px">该比例订单跳过计划，由⑤整单外协处理</span>
                  </template>
                </div>
              </el-form-item>
              <!-- ①生产计划：开始延迟 + 结束提前 -->
              <el-form-item v-if="currentModule.key === 'plan'" label="开始延迟天数">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.delayDaysMin" :min="0" :max="30" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.delayDaysMax" :min="0" :max="30" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">订单创建后延迟N个工作日开始</span>
                </div>
              </el-form-item>
              <el-form-item v-if="currentModule.key === 'plan'" label="结束提前天数">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.advanceDaysMin" :min="0" :max="30" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.advanceDaysMax" :min="0" :max="30" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">订单交期提前N个工作日结束</span>
                </div>
              </el-form-item>
              <!-- ②工单：延迟天数范围 -->
              <el-form-item v-if="currentModule.key === 'work-order'" label="延迟天数">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.woDelayMin" :min="0" :max="30" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.woDelayMax" :min="0" :max="30" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">相对计划创建时间推迟N个工作日</span>
                </div>
              </el-form-item>
              <!-- ③报工：报工时间段 -->
              <el-form-item v-if="currentModule.key === 'work-report'" label="报工时间段">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-time-select v-model="simConfig.reportStartTime" placeholder="开始" start="06:00" step="00:30" end="22:00" style="width: 120px" />
                  <span>~</span>
                  <el-time-select v-model="simConfig.reportEndTime" placeholder="结束" start="06:00" step="00:30" end="22:00" style="width: 120px" />
                  <span class="text-muted" style="font-size: 12px">每天报工的统一时间段</span>
                </div>
              </el-form-item>
              <!-- ③报工：每次报工占比 -->
              <el-form-item v-if="currentModule.key === 'work-report'" label="每次报工占比(%)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.reportBatchPctMin" :min="20" :max="100" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.reportBatchPctMax" :min="20" :max="100" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">每次报工数量占可报工数的比例，控制分批次数</span>
                </div>
              </el-form-item>
              <!-- ③+④质检延迟配置 -->
              <el-form-item v-if="currentModule.key === 'work-report'" label="质检延迟(小时)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.qcDelayHoursMin" :min="1" :max="48" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.qcDelayHoursMax" :min="1" :max="48" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">报工后延迟N小时质检，超出工作时间顺延</span>
                </div>
              </el-form-item>
              <el-form-item v-if="currentModule.key === 'work-report'" label="下道延迟(小时)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.nextStepDelayHoursMin" :min="1" :max="48" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.nextStepDelayHoursMax" :min="1" :max="48" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">质检完成后延迟N小时进入下道工序/入库</span>
                </div>
              </el-form-item>
              <!-- ③不合格处置配置（固定比例，三项之和=100%） -->
              <el-form-item v-if="currentModule.key === 'work-report'" label="处置比例">
                <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap">
                  <span>返修</span>
                  <el-input-number v-model="simConfig.reworkPct" :min="0" :max="100" size="small" style="width: 80px" />
                  <span>% 让步</span>
                  <el-input-number v-model="simConfig.concessionPct" :min="0" :max="100" size="small" style="width: 80px" />
                  <span>% 报废</span>
                  <b>{{ Math.max(0, 100 - simConfig.reworkPct - simConfig.concessionPct) }}%</b>
                </div>
              </el-form-item>
              <el-form-item v-if="currentModule.key === 'work-report'" label="整批处置(%)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.wholeBatchPct" :min="0" :max="100" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">整批处置概率，其余为仅不合格数</span>
                </div>
              </el-form-item>
              <!-- ③④共用：合格率(固定值) + 合格数占比(范围) -->
              <el-form-item v-if="['work-report','qc-task'].includes(currentModule.key)" label="合格率(%)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.passTaskPct" :min="0" :max="100" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">全合格任务占总任务数的比例</span>
                </div>
              </el-form-item>
              <el-form-item v-if="['work-report','qc-task'].includes(currentModule.key)" label="合格数占比(%)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.passQtyPctMin" :min="50" :max="99" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.passQtyPctMax" :min="50" :max="99" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">不合格任务中合格数量的占比</span>
                </div>
              </el-form-item>
              <!-- (旧⑤报废比例已合并到③不合格处置配置中) -->
              <!-- ⑥外协：外协比例范围 -->
              <!-- ⑤整单外协配置 -->
              <el-form-item v-if="currentModule.key === 'outsource'" label="外协交期(天)">
                <div style="display: flex; align-items: center; gap: 6px">
                  <el-input-number v-model="simConfig.outsourceLeadDaysMin" :min="5" :max="30" size="small" style="width: 100px" />
                  <span>~</span>
                  <el-input-number v-model="simConfig.outsourceLeadDaysMax" :min="5" :max="30" size="small" style="width: 100px" />
                  <span class="text-muted" style="font-size: 12px">外协生产周期(发料到收料)</span>
                </div>
              </el-form-item>
              <el-alert v-if="currentModule.key === 'outsource'" type="info" :closable="false" style="margin-bottom: 8px">
                <template #title>整单外协：自动读取未生成生产计划的confirmed订单（在①计划中被跳过的订单）</template>
              </el-alert>
            </el-form>

            <!-- ④销售发货：特殊UI（一键执行，不需要Excel） -->
            <template v-if="currentModule.key === 'shipment'">
              <el-alert type="info" :closable="false" style="margin-bottom: 12px">
                <template #title>
                  基于已完成工单自动生成：1.成品入库 2.发货记录 3.销售出库 4.回写订单状态
                </template>
              </el-alert>
              <el-form label-width="130px" style="max-width: 500px; margin-bottom: 12px">
                <el-form-item label="发货延迟(天)">
                  <div style="display: flex; align-items: center; gap: 6px">
                    <el-input-number v-model="simConfig.shipDelayDaysMin" :min="0" :max="30" size="small" style="width: 100px" />
                    <span>~</span>
                    <el-input-number v-model="simConfig.shipDelayDaysMax" :min="0" :max="30" size="small" style="width: 100px" />
                    <span class="text-muted" style="font-size: 12px">成品入库后延迟N天发货</span>
                  </div>
                </el-form-item>
              </el-form>
              <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px">
                <el-button type="primary" :loading="genLoading" @click="handleGenerate">
                  1. 扫描预览
                </el-button>
                <el-button type="warning" :loading="importLoading" @click="handleShipment">
                  2. 一键发货
                </el-button>
              </div>
              <div v-if="genResult" style="margin-bottom: 16px; font-size: 13px; color: #606266">
                已完成工单: <b>{{ genResult.completedWorkOrders }}</b> 个，
                可发货工单: <b>{{ genResult.shippableWorkOrders }}</b> 个，
                关联订单: <b>{{ genResult.relatedOrders }}</b> 个，
                发货延迟: <b>{{ genResult.shipDelayDays }}</b> 天
              </div>
            </template>

            <!-- ⑧库存台账：特殊UI -->
            <template v-else-if="currentModule.key === 'inventory'">
              <el-alert type="info" :closable="false" style="margin-bottom: 12px">
                <template #title>
                  重建内容：1.出入库历史（基于库存任务生成）2.库存台账（基于流水汇总）3.历史库存快照（按日生成）
                </template>
              </el-alert>
              <el-form label-width="130px" style="max-width: 500px; margin-bottom: 12px">
                <el-form-item label="处理延迟(小时)">
                  <div style="display: flex; align-items: center; gap: 6px">
                    <el-input-number v-model="simConfig.invProcessHoursMin" :min="1" :max="24" size="small" style="width: 100px" />
                    <span>~</span>
                    <el-input-number v-model="simConfig.invProcessHoursMax" :min="1" :max="24" size="small" style="width: 100px" />
                    <span class="text-muted" style="font-size: 12px">库存任务创建后延迟N小时处理</span>
                  </div>
                </el-form-item>
              </el-form>
              <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px">
                <el-button type="primary" :loading="genLoading" @click="handleGenerate">
                  1. 扫描预览
                </el-button>
                <el-button type="warning" :loading="importLoading" @click="handleInventoryRebuild">
                  2. 一键重建
                </el-button>
              </div>
              <div v-if="genResult" style="margin-bottom: 16px; font-size: 13px; color: #606266">
                库存任务: <b>{{ genResult.totalTasks }}</b> 个，
                库存流水: <b>{{ genResult.totalTransactions }}</b> 条，
                库存台账: <b>{{ genResult.inventoryRecords }}</b> 条，
                最早日期: <b>{{ genResult.earliestDate }}</b>
              </div>
            </template>

            <!-- 其他模块：标准操作流程 -->
            <template v-else>
              <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 16px">
                <el-button type="primary" :loading="genLoading" @click="handleGenerate">
                  1. 生成预览
                </el-button>
                <el-button type="success" :disabled="!genResult" :loading="exportLoading" @click="handleExport">
                  2. 导出Excel
                </el-button>
              </div>

              <div v-if="genResult" style="margin-bottom: 16px">
                <div style="font-size: 13px; color: #606266; margin-bottom: 8px">
                  预生成数据：共 <b>{{ genResult.totalRows }}</b> 行
                  <span v-for="(count, table) in genResult.tables" :key="table" style="margin-left: 12px">
                    {{ table }}: <b>{{ count }}</b>
                  </span>
                </div>
              </div>

              <div style="border-top: 1px solid #ebeef5; padding-top: 16px">
                <div style="font-weight: 600; margin-bottom: 8px">3. 上传修改后的Excel文件</div>
                <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
                  <input ref="importFileInput" type="file" accept=".xlsx,.xls" multiple style="font-size: 13px" @change="onImportFileChange" />
                  <el-button type="warning" :disabled="!importFiles.length" :loading="importLoading" @click="handleImport">
                    开始导入
                  </el-button>
                </div>
              </div>
            </template>

            <!-- 导入结果 -->
            <div v-if="importResult" style="margin-top: 12px">
              <div style="display: flex; gap: 16px; margin-bottom: 8px">
                <span style="color: #67C23A">成功: <b>{{ importResult.success }}</b></span>
                <span v-if="importResult.errors?.length" style="color: #F56C6C">错误: <b>{{ importResult.errors.length }}</b></span>
              </div>
              <div v-if="importResult.errors?.length" class="error-list">
                <div v-for="(err, idx) in importResult.errors" :key="idx" class="error-item">{{ err }}</div>
              </div>
            </div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, defineComponent, h } from 'vue'
import { ElMessage, ElMessageBox, ElButton, ElIcon } from 'element-plus'
import { Upload, Download, UploadFilled } from '@element-plus/icons-vue'
import { downloadTemplate, downloadAllPlainTemplates, importData, generateSimData, exportSimData, importSimData, getSimUsers } from '@/api/data-import'
import { getWarehouseList } from '@/api/system'

const activeTab = ref('user')

// 下载所有纯净模板(无下拉)
async function handleDownloadAllPlainTemplates() {
  try {
    const res = await downloadAllPlainTemplates()
    // request 拦截器直接返回 res.data,这里 res 就是 blob
    const blob = res instanceof Blob ? res : (res.data || res)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '所有导入模板.zip'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '下载失败')
  }
}

// ==================== 数据模拟生成 ====================

const simModules = [
  { key: 'plan', name: '车间计划', order: 1 },
  { key: 'work-order', name: '生产工单', order: 2 },
  { key: 'work-report', name: '报工+质检+处置', order: 3 },
  { key: 'outsource', name: '整单外协', order: 4 },
  { key: 'outsource-receipt', name: '外协收料', order: 5 },
  { key: 'shipment', name: '销售发货', order: 6 },
  { key: 'qc-task', name: '采购来料质检', order: 7 },
  { key: 'inventory', name: '库存台账', order: 8 },
  { key: 'maint-plan', name: '保养计划', order: 9 },
  { key: 'maint-task', name: '保养任务', order: 10 },
  { key: 'insp-plan', name: '点检计划', order: 11 },
  { key: 'insp-task', name: '点检任务', order: 12 },
  { key: 'repair-request', name: '报修申请', order: 13 },
  { key: 'repair-order', name: '维修工单', order: 14 },
]

const simConfig = reactive({
  workStartTime: '08:00',
  workEndTime: '17:00',
  skipWeekends: true,
  skipHolidays: false,
  holidayStr: '',
  // 各角色人员（每个模块按需配置）
  creatorIds: [],       // 创建人（所有模块必填）
  assigneeIds: [],      // 指派人（②工单、⑧返修）
  reporterIds: [],      // 报工人（③报工）
  inspectorIds: [],     // 检验员（④质检）
  handlerIds: [],       // 处理人（⑤质检处理）
  approverIds: [],      // 审批人（⑥外协）
  receiverIds: [],      // 收货人（⑦外协收料）
  warehouseId: undefined,  // 仓库（仅⑦外协收料）
  // 业务参数
  enableFullOutsource: false, fullOutsourcePct: 20, // ①整单外协
  delayDaysMin: 1, delayDaysMax: 5,       // ①计划开始延迟
  advanceDaysMin: 1, advanceDaysMax: 5,   // ①计划结束提前
  woDelayMin: 1, woDelayMax: 3,           // ②工单延迟
  reportBatchPctMin: 40, reportBatchPctMax: 50,       // ③每次报工占比%
  reportStartTime: '17:00', reportEndTime: '18:00', // ③报工时间段
  qcDelayHoursMin: 2, qcDelayHoursMax: 6,           // ③+④质检延迟(小时)
  nextStepDelayHoursMin: 1, nextStepDelayHoursMax: 4, // ③+④下道工序/入库延迟(小时)
  reworkPct: 40,              // 不合格中返修占比%（固定值）
  concessionPct: 30,          // 不合格中让步接收占比%（固定值）
  wholeBatchPct: 20,          // 整批处置概率%（固定值）
  receiptDelayDaysMin: 1, receiptDelayDaysMax: 3, // ④收料延迟天数
  receiptProcessHoursMin: 2, receiptProcessHoursMax: 8, // ④收料处理延迟小时
  passTaskPct: 90,                          // 全合格任务占比%
  passQtyPctMin: 80, passQtyPctMax: 95,    // 不合格任务中合格数占比%
  scrapRateMin: 20, scrapRateMax: 50,     // ⑤报废比例%
  shipDelayDaysMin: 1, shipDelayDaysMax: 3,           // ④发货延迟(天)
  invProcessHoursMin: 1, invProcessHoursMax: 4,       // ⑧处理延迟(小时)
  outsourceLeadDaysMin: 8, outsourceLeadDaysMax: 15, // ⑥外协交期(天)
})

const currentStep = ref(0)
const currentModule = ref(simModules[0])
const moduleStatus = reactive({})
const userOptions = ref([])
const warehouseOptions = ref([])
const genLoading = ref(false)
const genResult = ref(null)
const exportLoading = ref(false)
const importFiles = ref([])
const importLoading = ref(false)
const importResult = ref(null)

function selectModule(m) {
  currentModule.value = m
  currentStep.value = m.order - 1
  genResult.value = null
  importResult.value = null
  importFiles.value = []
  // 重置文件选择器DOM
  const fileInput = document.querySelector('input[type="file"][multiple]')
  if (fileInput) fileInput.value = ''
}

async function loadUsers() {
  if (userOptions.value.length > 0) return
  try { const res = await getSimUsers(); userOptions.value = res.data } catch { /* */ }
}

async function loadWarehouses() {
  if (warehouseOptions.value.length > 0) return
  try { const res = await getWarehouseList(); warehouseOptions.value = res.data || res } catch { /* */ }
}

function buildConfigPayload() {
  const holidays = simConfig.holidayStr ? simConfig.holidayStr.split('\n').map(s => s.trim()).filter(Boolean) : []
  return {
    workStartTime: simConfig.workStartTime,
    workEndTime: simConfig.workEndTime,
    skipWeekends: simConfig.skipWeekends,
    skipHolidays: simConfig.skipHolidays,
    holidays,
    // 各角色人员
    creatorIds: simConfig.creatorIds,
    assigneeIds: simConfig.assigneeIds,
    reporterIds: simConfig.reporterIds,
    inspectorIds: simConfig.inspectorIds,
    handlerIds: simConfig.handlerIds,
    approverIds: simConfig.approverIds,
    receiverIds: simConfig.receiverIds,
    warehouseId: simConfig.warehouseId,
    // 业务参数
    enableFullOutsource: simConfig.enableFullOutsource,
    fullOutsourcePct: simConfig.fullOutsourcePct,
    delayDaysMin: simConfig.delayDaysMin,
    delayDaysMax: simConfig.delayDaysMax,
    advanceDaysMin: simConfig.advanceDaysMin,
    advanceDaysMax: simConfig.advanceDaysMax,
    woDelayMin: simConfig.woDelayMin,
    woDelayMax: simConfig.woDelayMax,
    reportBatchPctMin: simConfig.reportBatchPctMin,
    reportBatchPctMax: simConfig.reportBatchPctMax,
    reportStartTime: simConfig.reportStartTime,
    reportEndTime: simConfig.reportEndTime,
    qcDelayHoursMin: simConfig.qcDelayHoursMin,
    qcDelayHoursMax: simConfig.qcDelayHoursMax,
    nextStepDelayHoursMin: simConfig.nextStepDelayHoursMin,
    nextStepDelayHoursMax: simConfig.nextStepDelayHoursMax,
    reworkPct: simConfig.reworkPct,
    concessionPct: simConfig.concessionPct,
    wholeBatchPct: simConfig.wholeBatchPct,
    receiptDelayDaysMin: simConfig.receiptDelayDaysMin,
    receiptDelayDaysMax: simConfig.receiptDelayDaysMax,
    receiptProcessHoursMin: simConfig.receiptProcessHoursMin,
    receiptProcessHoursMax: simConfig.receiptProcessHoursMax,
    passTaskPct: simConfig.passTaskPct,
    passQtyPctMin: simConfig.passQtyPctMin,
    passQtyPctMax: simConfig.passQtyPctMax,
    scrapRateMin: simConfig.scrapRateMin,
    scrapRateMax: simConfig.scrapRateMax,
    shipDelayDaysMin: simConfig.shipDelayDaysMin,
    shipDelayDaysMax: simConfig.shipDelayDaysMax,
    invProcessHoursMin: simConfig.invProcessHoursMin,
    invProcessHoursMax: simConfig.invProcessHoursMax,
    outsourceLeadDaysMin: simConfig.outsourceLeadDaysMin,
    outsourceLeadDaysMax: simConfig.outsourceLeadDaysMax,
  }
}

async function handleGenerate() {
  if (currentModule.value.key !== 'work-report' && !simConfig.creatorIds.length) return ElMessage.warning('请选择创建人')
  if (currentModule.value.key === 'outsource-receipt' && !simConfig.warehouseId) return ElMessage.warning('请选择收货仓库')
  genLoading.value = true
  genResult.value = null
  try {
    const res = await generateSimData(currentModule.value.key, buildConfigPayload())
    genResult.value = res.data
    ElMessage.success(`预生成完成：共 ${genResult.value.totalRows} 行`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '生成失败')
  } finally {
    genLoading.value = false
  }
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

async function handleExport() {
  exportLoading.value = true
  try {
    const blob = await exportSimData(currentModule.value.key)
    downloadBlob(blob, `${currentModule.value.order.toString().padStart(2, '0')}_${currentModule.value.name}.zip`)
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exportLoading.value = false
  }
}

function onImportFileChange(e) {
  importFiles.value = e.target.files ? Array.from(e.target.files) : []
}

async function handleImport() {
  if (!importFiles.value.length) return ElMessage.warning('请选择文件')
  try {
    await ElMessageBox.confirm(
        `确定将 ${importFiles.value.length} 个文件导入到"${currentModule.value.name}"？`,
        '确认导入', { type: 'warning' }
    )
  } catch { return }

  importLoading.value = true
  importResult.value = null
  try {
    const res = await importSimData(currentModule.value.key, importFiles.value)
    importResult.value = res.data
    moduleStatus[currentModule.value.key] = 'success'
    ElMessage.success(`导入完成：成功 ${importResult.value.success} 条`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

async function handleShipment() {
  try {
    await ElMessageBox.confirm('确定根据已完成工单执行成品入库和销售发货？', '确认发货', { type: 'warning' })
  } catch { return }

  importLoading.value = true
  importResult.value = null
  try {
    const res = await importSimData('shipment', [])
    importResult.value = res.data
    moduleStatus.shipment = 'success'
    ElMessage.success(`发货完成：入库 ${res.data.stockInCount} 条，发货 ${res.data.shipmentCount} 条`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '发货失败')
  } finally {
    importLoading.value = false
  }
}

async function handleInventoryRebuild() {
  try {
    await ElMessageBox.confirm('确定根据所有库存流水重建库存台账？', '确认重建', { type: 'warning' })
  } catch { return }

  importLoading.value = true
  importResult.value = null
  try {
    // 库存台账不需要上传文件，直接调用导入接口（后端会自动扫描流水重建）
    const res = await importSimData('inventory', [])
    importResult.value = res.data
    moduleStatus.inventory = 'success'
    const d = res.data || {}
    ElMessage.success(`重建完成：历史记录 ${d.historyCount ?? 0} 条，库存台账 ${d.inventoryCount ?? 0} 条，快照 ${d.snapshotCount ?? 0} 天`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '重建失败')
  } finally {
    importLoading.value = false
  }
}

// ==================== 列说明 ====================

const equipmentColumns = [
  { name: '设备编号', desc: '留空自动生成(EQ前缀)', required: false },
  { name: '设备名称', desc: '', required: true },
  { name: '型号', desc: '如 ZY4800/09/20', required: false },
  { name: '设备类别', desc: '如 液压支架/焊接设备/机加工设备', required: true },
  { name: '供应商名称', desc: '下拉选择', required: false },
  { name: '购入日期', desc: 'yyyy-MM-dd', required: false },
  { name: '启用日期', desc: 'yyyy-MM-dd', required: false },
  { name: '状态', desc: 'NORMAL/MAINTENANCE/RETIRED', required: false },
  { name: '部门', desc: '下拉选择', required: false },
  { name: '安装位置', desc: '如 一号车间', required: false },
  { name: '规格参数', desc: '', required: false },
  { name: '技术参数', desc: '', required: false },
  { name: '维护周期(天)', desc: '自动保养周期', required: false },
  { name: '设备原值', desc: '元', required: false },
  { name: '折旧年限', desc: '年', required: false },
  { name: '创建时间', desc: '', required: false },
  { name: '备注', desc: '', required: false },
]

const maintTemplateColumns = [
  { name: '模板名称', desc: '同名多行=多个保养项', required: true },
  { name: '适用设备类别', desc: '如 液压支架(仅首行)', required: false },
  { name: '周期类型', desc: 'DAY/WEEK/MONTH/QUARTER/YEAR(仅首行)', required: false },
  { name: '周期值', desc: '如 1 表示每1周(仅首行)', required: false },
  { name: '项目分组', desc: '如 液压系统/电气系统', required: false },
  { name: '项目名称', desc: '每行一个保养项', required: true },
  { name: '检查方法', desc: '', required: false },
  { name: '标准', desc: '', required: false },
  { name: '单位', desc: '', required: false },
  { name: '是否必填', desc: '0/1', required: false },
  { name: '是否需要拍照', desc: '0/1', required: false },
  { name: '排序号', desc: '', required: false },
  { name: '关联设备编号', desc: '逗号分隔(仅首行)', required: false },
  { name: '创建时间', desc: '(仅首行)', required: false },
  { name: '备注', desc: '(仅首行)', required: false },
]

const inspTemplateColumns = [
  { name: '模板名称', desc: '同名多行=多个点检项', required: true },
  { name: '适用设备类别', desc: '如 液压支架(仅首行)', required: false },
  { name: '点检类型', desc: '0日常/1定期/2专项(仅首行)', required: false },
  { name: '状态', desc: 'active/inactive(仅首行)', required: false },
  { name: '项目名称', desc: '每行一个点检项', required: true },
  { name: '检查标准', desc: '', required: false },
  { name: '检查方法', desc: '', required: false },
  { name: '是否必填', desc: '0/1', required: false },
  { name: '排序号', desc: '', required: false },
  { name: '关联设备编号', desc: '逗号分隔(仅首行)', required: false },
  { name: '创建时间', desc: '(仅首行)', required: false },
  { name: '备注', desc: '(仅首行)', required: false },
]

const userColumns = [
  { name: '用户名', desc: '登录账号，不可重复', required: true },
  { name: '真实姓名', desc: '', required: false },
  { name: '手机号', desc: '', required: false },
  { name: '部门', desc: '下拉选择', required: false },
  { name: '岗位组', desc: '如一车间、质检室、销售一组', required: false },
  { name: '角色', desc: '下拉选择（中文），多个用逗号分隔', required: false },
  { name: '入职日期', desc: 'yyyy-MM-dd', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss', required: false },
  { name: '备注', desc: '', required: false },
]

const customerColumns = [
  { name: '客户编号', desc: '留空自动生成', required: false },
  { name: '客户名称', desc: '不可重复', required: true },
  { name: '联系人', desc: '', required: false },
  { name: '电话', desc: '', required: false },
  { name: '邮箱', desc: '', required: false },
  { name: '地址', desc: '', required: false },
  { name: '结算方式', desc: '如 月结30天/预付', required: false },
  { name: '信用额度', desc: '', required: false },
  { name: '税率(%)', desc: '', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss', required: false },
  { name: '备注', desc: '', required: false },
]

const supplierColumns = [
  { name: '供应商编号', desc: '留空自动生成', required: false },
  { name: '供应商名称', desc: '不可重复', required: true },
  { name: '供应商类型', desc: '如 生产型/贸易型', required: false },
  { name: '供应商类别', desc: '下拉选择(只列叶子节点),需已在供应商类别中存在', required: false },
  { name: '联系人', desc: '', required: false },
  { name: '电话', desc: '', required: false },
  { name: '地址', desc: '', required: false },
  { name: '结算方式', desc: '如 月结60天/货到付款', required: false },
  { name: '开户行/银行账号/税号', desc: '', required: false },
  { name: '发票类型', desc: '如 增值税专用发票', required: false },
  { name: '加工能力', desc: '', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss', required: false },
  { name: '备注', desc: '', required: false },
]

const teamColumns = [
  { name: '班组名称', desc: '不可重复，同名多行=多个成员', required: true },
  { name: '车间', desc: '所属车间（仅首行填写）', required: false },
  { name: '组长姓名', desc: '下拉选择，仅首行填写', required: false },
  { name: '生效日期', desc: 'yyyy-MM-dd（仅首行填写）', required: false },
  { name: '成员姓名', desc: '下拉选择，每行一个成员', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss（仅首行填写）', required: false },
  { name: '备注', desc: '', required: false },
]

const qcTemplateColumns = [
  { name: '模板名称', desc: '同名多行=一个模板的多个检验项', required: true },
  { name: '适用类型', desc: '来料检/过程检/成品检/外协检（仅首行）', required: false },
  { name: '适用产品编号', desc: '物料档案中的成品编号（仅首行）', required: false },
  { name: '适用物料编号', desc: '物料档案中的原料编号（仅首行）', required: false },
  { name: '抽样方案', desc: '全检/抽检（仅首行）', required: true },
  { name: '抽样模式', desc: '首件/末件/固定数量/按比例（仅首行）', required: false },
  { name: '抽检数量', desc: '固定数量时填（仅首行）', required: false },
  { name: '抽检比例(%)', desc: '按比例时填，如 10（仅首行）', required: false },
  { name: '默认模板', desc: '是/否（仅首行）', required: false },
  { name: '检验项目名称', desc: '每行一个检验项', required: true },
  { name: '检验类型', desc: '计量型/文本型/单选', required: true },
  { name: '标准值', desc: '计量型必填', required: false },
  { name: '下限值', desc: '计量型可选', required: false },
  { name: '上限值', desc: '计量型可选', required: false },
  { name: '单位', desc: '计量型填，如 mm、HRC、MPa', required: false },
  { name: '检验方法', desc: '如 游标卡尺测量', required: false },
  { name: '默认值', desc: '检验时默认填入的值', required: false },
  { name: '是否必填', desc: '是/否', required: false },
  { name: '是否关键', desc: '是/否（关键项不合格=整体不合格）', required: false },
  { name: '排序号', desc: '同一模板内的显示顺序', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss（仅首行）', required: false },
]

const materialSpecColumns = [
  { name: '规格名称', desc: '如：材质、尺寸、颜色、厚度等', required: true },
]

const materialColumns = [
  { name: '物料编号', desc: '留空自动生成', required: false },
  { name: '物料名称', desc: '', required: true },
  { name: '物料类型', desc: '成品/半成品/原材料/辅料/包材/外购件', required: false },
  { name: '物料类别', desc: '下拉选择(需已在物料类别中存在),留空时按物料类型自动匹配顶层类目', required: false },
  { name: '主单位', desc: '如 kg/个/米', required: false },
  { name: '辅助单位 / 换算比例', desc: '1主单位=N辅助单位', required: false },
  { name: '物料属性', desc: 'purchase/production/sales，逗号分隔', required: false },
  { name: '物料来源', desc: 'purchase/manufacture/outsource', required: false },
  { name: '标准成本 / 安全库存', desc: '', required: false },
  { name: '最小/最大库存', desc: '', required: false },
  { name: '采购/生产提前期', desc: '天', required: false },
  { name: '最小批量/批量倍数', desc: '', required: false },
  { name: '默认仓库', desc: '下拉选择（需已在系统仓库中存在）', required: false },
  { name: '默认供应商', desc: '下拉选择（需已在供应商档案中存在）', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss', required: false },
  { name: '备注', desc: '', required: false },
  { name: '(动态规格列)', desc: '固定列之后可自行添加规格表头，如：材质、尺寸等', required: false },
]

const salesOrderColumns = [
  { name: '订单编号', desc: '留空自动生成，同编号多行=多明细', required: false },
  { name: '客户名称', desc: '下拉选择', required: true },
  { name: '联系人/收货地址/结算方式', desc: '订单级信息', required: false },
  { name: '交货日期', desc: 'yyyy-MM-dd', required: false },
  { name: '业务员', desc: '下拉选择', required: false },
  { name: '下单日期', desc: '保留历史创建时间', required: false },
  { name: '产品编号', desc: '下拉选择（仅成品）', required: true },
  { name: '产品名称', desc: '自动带出（只读参考列）', required: false },
  { name: '数量/单价/金额/税率', desc: '金额=数量×单价自动计算', required: true },
]

const bomColumns = [
  { name: '产品编号', desc: '下拉选择（仅成品），同产品+版本多行=多子件', required: true },
  { name: '产品名称', desc: '自动带出（只读参考列）', required: false },
  { name: '版本号', desc: '如 V1.0', required: false },
  { name: '版本状态', desc: 'draft/active/deprecated', required: false },
  { name: '层级', desc: '1=顶级/2=二级/3=三级...支持多级嵌套，上一层级的子项归属最近的父级', required: false },
  { name: '子件物料编号', desc: '下拉选择（全部物料）', required: true },
  { name: '子件物料名称', desc: '自动带出（只读参考列）', required: false },
  { name: '来源类型', desc: 'purchase(外购)/manufacture(自制)', required: false },
  { name: '数量', desc: '单位产品所需子件数量', required: true },
  { name: '损耗率(%)', desc: '系统自动计算实际用量', required: false },
  { name: '排序号', desc: '留空自动递增', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss', required: false },
]

const purchaseOrderColumns = [
  { name: '采购单号', desc: '留空自动生成，同单号多行=多明细', required: false },
  { name: '供应商名称', desc: '下拉选择', required: true },
  { name: '联系人/交货日期/付款条款', desc: '订单级信息', required: false },
  { name: '采购员', desc: '下拉选择', required: false },
  { name: '下单日期', desc: '保留历史创建时间', required: false },
  { name: '物料编号', desc: '下拉选择（全部物料）', required: true },
  { name: '物料名称', desc: '自动带出（只读参考列）', required: false },
  { name: '数量/单价/金额', desc: '金额可留空自动计算', required: true },
]

const inventoryColumns = [
  { name: '物料编号', desc: '下拉选择（需已在物料档案中存在）', required: true },
  { name: '物料名称', desc: '选择物料编号后自动带出，无需填写', required: false },
  { name: '仓库名称', desc: '下拉选择', required: true },
  { name: '在库数量', desc: '当前在库总数量（主单位）', required: true },
  { name: '可用数量', desc: '可用数量，留空自动计算=在库数量-锁定数量', required: false },
  { name: '锁定数量', desc: '锁定数量，留空默认 0', required: false },
]

const processColumns = [
  { name: '工序名称', desc: '不可重复', required: true },
  { name: '标准工时(天)', desc: '不填默认1天', required: false },
  { name: '计件单价', desc: '', required: false },
  { name: '绑定类型', desc: '个人/班组，不填则不绑定', required: false },
  { name: '绑定对象名称', desc: '个人填姓名(多人逗号分隔)，班组填班组名', required: false },
  { name: 'SOP描述', desc: '', required: false },
  { name: '入半成品库', desc: '0或1，不填默认0', required: false },
  { name: '可外协', desc: '0或1，不填默认0', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss', required: false },
]

const processRouteColumns = [
  { name: '路线名称', desc: '同名称多行=多道工序', required: true },
  { name: '产品编号', desc: '下拉选择，可为空表示通用路线', required: false },
  { name: '产品名称', desc: '自动带出（只读参考列）', required: false },
  { name: '版本号', desc: '', required: false },
  { name: '状态', desc: 'active/inactive', required: false },
  { name: '工序名称', desc: '下拉选择，自动带出标准工时和计件单价', required: true },
  { name: '工序序号', desc: '执行顺序，建议10/20/30', required: true },
  { name: '工序组', desc: '同组并行，默认1', required: false },
  { name: '覆盖标准工时(天)', desc: '选工序后自动填充，可手动覆盖', required: false },
  { name: '覆盖计件单价', desc: '选工序后自动填充，可手动覆盖', required: false },
  { name: '质检模板', desc: '下拉选择，一般只在最后一道工序上填写', required: false },
  { name: '创建时间', desc: 'yyyy-MM-dd HH:mm:ss', required: false },
]

// ==================== 导入面板组件 ====================

const ImportPanel = defineComponent({
  name: 'ImportPanel',
  props: {
    type: String,
    title: String,
    columns: Array,
    notes: { type: Array, default: () => [] }
  },
  setup(props) {
    const fileRef = ref(null)
    const loading = ref(false)
    const result = ref(null)

    function downloadBlob(blob, filename) {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    }

    async function handleDownloadTemplate() {
      try {
        const res = await downloadTemplate(props.type)
        downloadBlob(res, `${props.title}导入模板.xlsx`)
      } catch (e) {
        ElMessage.error('下载模板失败')
      }
    }

    function handleFileChange(e) {
      const files = e.target.files
      if (files && files.length > 0) {
        fileRef.value = files[0]
      }
    }

    async function handleImport() {
      if (!fileRef.value) {
        ElMessage.warning('请先选择文件')
        return
      }
      try {
        await ElMessageBox.confirm(
            `确定导入文件 "${fileRef.value.name}" 到 ${props.title}？\n导入后数据将直接写入数据库。`,
            '确认导入',
            { type: 'warning' }
        )
      } catch { return }

      loading.value = true
      result.value = null
      try {
        const resp = await importData(props.type, fileRef.value)
        const res = resp.data || resp
        result.value = res
        if (res.failed === 0) {
          ElMessage.success(`导入完成：成功 ${res.success} 条`)
        } else {
          ElMessage.warning(`导入完成：成功 ${res.data.success} 条，失败 ${res.data.failed} 条`)
        }
      } catch (e) {
        ElMessage.error(e.response?.data?.message || e.message || '导入失败')
      } finally {
        loading.value = false
      }
    }

    return () => h('div', { class: 'import-panel' }, [
      // 模板说明
      h('div', { class: 'panel-section' }, [
        h('div', { class: 'section-title' }, '模板字段说明'),
        h('table', { class: 'field-table' }, [
          h('thead', {}, h('tr', {}, [
            h('th', {}, '字段'),
            h('th', {}, '说明'),
            h('th', { style: 'width: 60px' }, '必填'),
          ])),
          h('tbody', {}, props.columns.map(col =>
              h('tr', {}, [
                h('td', {}, col.name),
                h('td', { class: 'text-muted' }, col.desc),
                h('td', { style: 'text-align: center' }, col.required
                    ? h('span', { style: 'color: #E6A23C; font-weight: 600' }, '是')
                    : h('span', { class: 'text-muted' }, '否')),
              ])
          )),
        ]),
        // 额外说明
        ...(props.notes.length > 0 ? [
          h('div', { style: 'margin-top: 8px; font-size: 12px; color: #909399' },
              props.notes.map(n => h('div', {}, ['· ', n])))
        ] : []),
      ]),
      // 操作区
      h('div', { class: 'panel-section', style: 'margin-top: 16px' }, [
        h('div', { class: 'section-title' }, '操作'),
        h('div', { style: 'display: flex; align-items: center; gap: 12px; flex-wrap: wrap' }, [
          h(ElButton, { type: 'primary', plain: true, icon: Download, onClick: handleDownloadTemplate }, () => '下载模板'),
          h('input', {
            type: 'file',
            accept: '.xlsx,.xls',
            style: 'font-size: 13px',
            onChange: handleFileChange,
          }),
          h(ElButton, {
            type: 'success',
            icon: UploadFilled,
            loading: loading.value,
            disabled: !fileRef.value,
            onClick: handleImport,
          }, () => '开始导入'),
        ]),
      ]),
      // 结果
      result.value ? h('div', { class: 'panel-section', style: 'margin-top: 16px' }, [
        h('div', { class: 'section-title' }, '导入结果'),
        h('div', { style: 'display: flex; gap: 24px; margin-bottom: 8px' }, [
          h('span', {}, ['总计: ', h('b', {}, result.value.total)]),
          h('span', { style: 'color: #67C23A' }, ['成功: ', h('b', {}, result.value.success)]),
          h('span', { style: 'color: #F56C6C' }, ['失败: ', h('b', {}, result.value.failed)]),
        ]),
        // 物料导入时显示匹配的规格列信息
        result.value.matchedSpecs && result.value.matchedSpecs.length > 0
            ? h('div', { style: 'margin-bottom: 8px; font-size: 12px; color: #409EFF' },
                '已匹配规格列: ' + result.value.matchedSpecs.join('、'))
            : null,
        result.value.unmatchedSpecs && result.value.unmatchedSpecs.length > 0
            ? h('div', { style: 'margin-bottom: 8px; font-size: 12px; color: #E6A23C' },
                '未匹配的规格列(已忽略): ' + result.value.unmatchedSpecs.join('、'))
            : null,
        result.value.errors && result.value.errors.length > 0
            ? h('div', { class: 'error-list' },
                result.value.errors.map((err, idx) =>
                    h('div', { key: idx, class: 'error-item' }, err)))
            : null,
      ]) : null,
    ])
  }
})

</script>

<style scoped lang="scss">
.data-import-page {
  padding: 0;
}

.import-panel {
  padding: 8px 0;
}

.panel-section {
  .section-title {
    font-weight: 600;
    font-size: 14px;
    margin-bottom: 10px;
    color: #303133;
  }
}

.field-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  th, td {
    padding: 6px 10px;
    border: 1px solid #ebeef5;
    text-align: left;
  }

  th {
    background: #f5f7fa;
    font-weight: 600;
    color: #606266;
  }
}

.error-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  padding: 8px 12px;
  background: #fef0f0;
  font-size: 12px;

  .error-item {
    color: #F56C6C;
    line-height: 1.8;
  }
}

.text-muted {
  color: #909399;
}
</style>
