<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Tab 1: 企业信息 -->
        <el-tab-pane label="企业信息" name="company">
          <el-form :model="companyForm" label-width="120px" style="max-width: 600px;" v-loading="companyLoading">
            <el-form-item label="企业名称">
              <el-input v-model="companyForm.company_name" placeholder="请输入企业名称" />
            </el-form-item>
            <el-form-item label="企业简称">
              <el-input v-model="companyForm.company_short_name" placeholder="请输入企业简称" />
            </el-form-item>
            <el-form-item label="行业">
              <el-select v-model="companyForm.company_industry" placeholder="请选择行业" style="width: 100%;">
                <el-option label="机械制造" value="机械制造" />
                <el-option label="电子制造" value="电子制造" />
                <el-option label="汽车零部件" value="汽车零部件" />
                <el-option label="食品加工" value="食品加工" />
                <el-option label="化工" value="化工" />
                <el-option label="其他" value="其他" />
              </el-select>
            </el-form-item>
            <el-form-item label="企业规模">
              <el-select v-model="companyForm.company_scale" placeholder="请选择企业规模" style="width: 100%;">
                <el-option label="小型(50人以下)" value="小型(50人以下)" />
                <el-option label="中型(50-300人)" value="中型(50-300人)" />
                <el-option label="大型(300人以上)" value="大型(300人以上)" />
              </el-select>
            </el-form-item>
            <el-form-item label="统一社会信用代码">
              <el-input v-model="companyForm.company_credit_code" placeholder="请输入统一社会信用代码" />
            </el-form-item>
            <el-form-item label="法定代表人">
              <el-input v-model="companyForm.company_legal_person" placeholder="请输入法定代表人" />
            </el-form-item>
            <el-form-item label="联系电话">
              <el-input v-model="companyForm.company_phone" placeholder="请输入联系电话" />
            </el-form-item>
            <el-form-item label="企业地址">
              <el-input v-model="companyForm.company_address" placeholder="请输入企业地址" />
            </el-form-item>
            <el-form-item label="企业Logo">
              <el-upload
                class="logo-uploader"
                :action="uploadUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                :on-success="handleLogoSuccess"
                accept="image/*"
              >
                <el-image v-if="companyForm.company_logo" :src="companyForm.company_logo"
                  :preview-src-list="[companyForm.company_logo]" preview-teleported
                  :hide-on-click-modal="true"
                  style="width: 120px; height: 120px; border-radius: 8px; cursor: pointer" fit="contain"
                  @click.stop />
                <el-icon v-else style="width: 120px; height: 120px; border: 1px dashed #dcdfe6; border-radius: 8px; display: flex; align-items: center; justify-content: center; font-size: 28px; color: #c0c4cc"><Plus /></el-icon>
              </el-upload>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="companySaving" @click="saveCompany">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: 审批流配置 -->
        <el-tab-pane label="审批流配置" name="approval">
          <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
            配置各业务模块的审批流程。审批人可按角色或指定用户设置。
          </el-alert>
          <el-table :data="approvalRules" v-loading="approvalLoading" border stripe>
            <el-table-column prop="businessName" label="业务类型" width="140" />
            <el-table-column prop="description" label="说明" min-width="200" />
            <el-table-column label="是否启用审批" width="130" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="saveApprovalRule(row)" />
              </template>
            </el-table-column>
            <el-table-column label="超时时间(小时)" width="140">
              <template #default="{ row }">
                <el-input-number v-model="row.timeoutHours" :min="0" :max="168" :step="4" :disabled="!row.enabled" placeholder="0=不限时" style="width: 100%;" @change="saveApprovalRule(row)" />
              </template>
            </el-table-column>
            <el-table-column label="自动审批金额上限" width="180">
              <template #default="{ row }">
                <el-input-number v-if="row.hasAmountLimit" v-model="row.autoApproveLimit" :min="0" :step="1000" :disabled="!row.enabled" style="width: 100%;" @change="saveApprovalRule(row)" />
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 3: 编号规则 SYS04 -->
        <el-tab-pane label="编号规则" name="numbering">
          <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
            配置各业务单据的自动编号规则。修改规则仅影响新单据，历史单据编号不变。
          </el-alert>
          <el-table :data="numberRules" v-loading="numberLoading" border stripe>
            <el-table-column prop="businessName" label="单据类型" width="160" />
            <el-table-column prop="prefix" label="前缀" width="100" align="center">
              <template #default="{ row }">
                <span style="font-weight: bold;">{{ row.prefix }}</span>
              </template>
            </el-table-column>
            <el-table-column label="日期格式" width="140" align="center">
              <template #default="{ row }">
                <span>{{ row.dateFormat === 'none' ? '无日期' : row.dateFormat }}</span>
              </template>
            </el-table-column>
            <el-table-column label="序号位数" width="100" align="center">
              <template #default="{ row }">
                <span>{{ row.seqLength }}位</span>
              </template>
            </el-table-column>
            <el-table-column label="示例" min-width="200">
              <template #default="{ row }">
                <span class="number-preview">{{ generatePreview(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openNumberRuleDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 4: 通知配置 SYS05 -->
        <el-tab-pane label="通知配置" name="notification">
          <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
            配置各业务场景的通知方式和接收角色。
          </el-alert>
          <el-table :data="notifyScenarios" v-loading="notifyLoading" border stripe>
            <el-table-column prop="scenarioName" label="通知场景" width="160" />
            <el-table-column label="站内消息" width="120" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.webEnabled" @change="handleNotifyScenarioChange(row)" />
              </template>
            </el-table-column>
            <el-table-column label="微信推送" width="120" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.wechatEnabled" @change="handleNotifyScenarioChange(row)" />
              </template>
            </el-table-column>
            <el-table-column label="接收角色" min-width="200">
              <template #default="{ row }">
                <el-tag v-for="role in row.receiverRoles" :key="role" size="small" style="margin-right: 4px;">{{ getRoleName(role) }}</el-tag>
                <span v-if="!row.receiverRoles || row.receiverRoles.length === 0" class="text-muted">未设置</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openNotifyDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 5: 扩展字段配置 -->
        <el-tab-pane label="扩展字段" name="extFields">
          <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
            为各业务模块添加自定义扩展字段，字段值以 JSON 格式存储。配置后可在对应模块的列表和表单中自动展示。
          </el-alert>
          <!-- 模块选择 -->
          <div style="display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap;">
            <el-radio-group v-model="extFieldModule" @change="loadFieldConfigs">
              <el-radio-button v-for="m in extFieldModules" :key="m.value" :value="m.value">{{ m.label }}</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="openFieldConfigDialog(null)">新增字段</el-button>
          </div>
          <el-table :data="fieldConfigs" v-loading="fieldConfigLoading" border stripe>
            <el-table-column prop="fieldKey" label="字段Key" width="150" />
            <el-table-column prop="fieldLabel" label="显示名称" width="140" />
            <el-table-column prop="fieldType" label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ { text:'文本', number:'数字', date:'日期', select:'下拉' }[row.fieldType] || row.fieldType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="列表显示" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.showInList ? 'success' : 'info'">{{ row.showInList ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="表单显示" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.showInForm ? 'success' : 'info'">{{ row.showInForm ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="查询条件" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.showInSearch ? 'warning' : 'info'">{{ row.showInSearch ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="必填" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.required ? 'danger' : 'info'">{{ row.required ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled ? 'success' : 'danger'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openFieldConfigDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteFieldConfig(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 6: 数据备份 SYS07 -->
        <el-tab-pane label="数据备份" name="backup">
          <!-- 顶部: 立即备份 + 自动备份配置 -->
          <el-card shadow="never" style="margin-bottom: 16px;">
            <template #header>
              <div style="display: flex; align-items: center; justify-content: space-between;">
                <span>备份配置</span>
                <el-button type="success" :icon="backupCreating ? '' : undefined" :loading="backupCreating" @click="handleManualBackup">立即备份</el-button>
              </div>
            </template>
            <el-form :model="backupConfigForm" label-width="100px" style="max-width: 600px;" v-loading="backupConfigLoading">
              <el-form-item label="自动备份">
                <el-switch v-model="backupConfigForm.enabled" />
              </el-form-item>
              <el-form-item v-if="backupConfigForm.enabled" label="备份频率">
                <el-select v-model="backupConfigForm.frequency" style="width: 200px;">
                  <el-option label="每日" value="daily" />
                  <el-option label="每周" value="weekly" />
                  <el-option label="每月" value="monthly" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="backupConfigForm.enabled" label="备份时间">
                <el-time-select v-model="backupConfigForm.backupTime" start="00:00" end="23:30" step="00:30" placeholder="选择时间" />
              </el-form-item>
              <el-form-item label="保留份数">
                <el-input-number v-model="backupConfigForm.keepCount" :min="1" :max="30" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="backupConfigSaving" @click="handleSaveBackupConfig">保存配置</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 底部: 备份记录表 -->
          <el-card shadow="never">
            <template #header><span>备份记录</span></template>
            <el-table :data="backupRecords" v-loading="backupRecordsLoading" border stripe>
              <el-table-column prop="backupNo" label="备份编号" width="180" show-overflow-tooltip />
              <el-table-column prop="backupTime" label="备份时间" width="180" />
              <el-table-column label="类型" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.type === 'auto' ? '' : 'success'" size="small">{{ row.type === 'auto' ? '自动' : '手动' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="dataSize" label="数据量" width="120" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'success' ? 'success' : (row.status === 'running' ? 'warning' : 'danger')" size="small">
                    {{ row.status === 'success' ? '成功' : (row.status === 'running' ? '进行中' : '失败') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleDownloadBackup(row)" :disabled="row.status !== 'success'">下载</el-button>
                  <el-button link type="warning" @click="handleRestoreBackup(row)" :disabled="row.status !== 'success'">恢复</el-button>
                  <el-button link type="danger" @click="handleDeleteBackup(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div style="margin-top: 12px; display: flex; justify-content: flex-end;">
              <el-pagination
                v-model:current-page="backupPage.current"
                v-model:page-size="backupPage.size"
                :total="backupPage.total"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @size-change="loadBackupRecords"
                @current-change="loadBackupRecords"
              />
            </div>
          </el-card>
        </el-tab-pane>

        <!-- Tab 6: 高级配置 -->
        <el-tab-pane label="高级配置" name="advanced">
          <div style="margin-bottom: 16px;">
            <el-button type="primary" plain size="small" @click="openConfigDialog(null)">新增配置</el-button>
          </div>
          <el-table :data="advancedConfigs" v-loading="advancedLoading" border stripe>
            <el-table-column prop="configKey" label="配置键" min-width="200" show-overflow-tooltip />
            <el-table-column prop="configValue" label="配置值" min-width="260" show-overflow-tooltip />
            <el-table-column prop="configGroup" label="分组" width="120" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openConfigDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDeleteConfig(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 编号规则编辑弹窗 -->
    <el-dialog v-model="numberRuleDialogVisible" title="编辑编号规则" width="520px" destroy-on-close>
      <el-form :model="numberRuleForm" label-width="100px">
        <el-form-item label="单据类型">
          <el-input :model-value="numberRuleForm.businessName" disabled />
        </el-form-item>
        <el-form-item label="前缀">
          <el-input v-model="numberRuleForm.prefix" placeholder="请输入前缀" maxlength="6" />
        </el-form-item>
        <el-form-item label="是否含日期">
          <el-switch v-model="numberRuleForm.hasDate" />
        </el-form-item>
        <el-form-item v-if="numberRuleForm.hasDate" label="日期格式">
          <el-select v-model="numberRuleForm.dateFormat" style="width: 100%;">
            <el-option label="YYYYMMDD" value="YYYYMMDD" />
            <el-option label="YYMMDD" value="YYMMDD" />
          </el-select>
        </el-form-item>
        <el-form-item label="序号位数">
          <el-select v-model="numberRuleForm.seqLength" style="width: 100%;">
            <el-option :label="3" :value="3" />
            <el-option :label="4" :value="4" />
            <el-option :label="5" :value="5" />
            <el-option :label="6" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item label="实时预览">
          <span class="number-preview">{{ generatePreview(numberRuleForm) }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="numberRuleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="numberRuleSaving" @click="handleNumberRuleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 通知配置编辑弹窗 -->
    <el-dialog v-model="notifyDialogVisible" title="编辑通知配置" width="520px" destroy-on-close>
      <el-form :model="notifyDialogForm" label-width="120px">
        <el-form-item label="通知场景">
          <el-input :model-value="notifyDialogForm.scenarioName" disabled />
        </el-form-item>
        <el-form-item label="站内消息">
          <el-switch v-model="notifyDialogForm.webEnabled" />
        </el-form-item>
        <el-form-item label="微信推送">
          <el-switch v-model="notifyDialogForm.wechatEnabled" />
        </el-form-item>
        <el-form-item label="接收角色">
          <el-checkbox-group v-model="notifyDialogForm.receiverRoles">
            <el-checkbox v-for="r in allRoles" :key="r.roleCode" :label="r.roleCode">
              {{ r.roleName || r.roleCode }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="allRoles.length === 0" style="color:#c0c4cc; font-size:12px">
            未加载到角色 - 请检查角色管理是否有启用的角色
          </div>
        </el-form-item>
        <el-form-item v-if="notifyDialogForm.scenarioKey === 'ai_brief'" label="推送时间">
          <el-time-select v-model="notifyDialogForm.pushTime" start="06:00" end="22:00" step="00:30" placeholder="选择时间" />
        </el-form-item>
        <el-form-item label="每日最大推送数">
          <el-input-number v-model="notifyDialogForm.maxDailyPush" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="notifyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="notifySaving" @click="handleNotifyDialogSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 扩展字段弹窗 -->
    <el-dialog v-model="fieldConfigDialogVisible" :title="fieldConfigForm.id ? '编辑扩展字段' : '新增扩展字段'" width="520px" destroy-on-close>
      <el-form :model="fieldConfigForm" :rules="fieldConfigRules" ref="fieldConfigFormRef" label-width="90px">
        <el-form-item label="所属模块">
          <el-tag>{{ extFieldModules.find(m => m.value === extFieldModule)?.label || extFieldModule }}</el-tag>
        </el-form-item>
        <el-form-item label="字段Key" prop="fieldKey">
          <el-input v-model="fieldConfigForm.fieldKey" :disabled="!!fieldConfigForm.id" placeholder="英文字母/数字/下划线，如 po_number" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">作为JSON存储的key，创建后不可修改</div>
        </el-form-item>
        <el-form-item label="显示名称" prop="fieldLabel">
          <el-input v-model="fieldConfigForm.fieldLabel" placeholder="如：客户PO号" />
        </el-form-item>
        <el-form-item label="字段类型" prop="fieldType">
          <el-select v-model="fieldConfigForm.fieldType" style="width: 100%">
            <el-option label="文本" value="text" />
            <el-option label="数字" value="number" />
            <el-option label="日期" value="date" />
            <el-option label="下拉" value="select" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fieldConfigForm.fieldType === 'select'" label="下拉选项">
          <el-input v-model="fieldConfigForm.fieldOptionsRaw" type="textarea" :rows="3" placeholder='每行一个选项，如：&#10;选项A&#10;选项B' />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">每行一个选项</div>
        </el-form-item>
        <el-form-item label="列表显示">
          <el-switch v-model="fieldConfigForm.showInList" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #606266; font-size: 13px">在列表中显示为列</span>
        </el-form-item>
        <el-form-item label="表单显示">
          <el-switch v-model="fieldConfigForm.showInForm" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #606266; font-size: 13px">在新增/编辑表单中显示</span>
        </el-form-item>
        <el-form-item label="查询条件">
          <el-switch v-model="fieldConfigForm.showInSearch" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #606266; font-size: 13px">在列表搜索区显示为查询条件</span>
        </el-form-item>
        <el-form-item label="是否必填">
          <el-switch v-model="fieldConfigForm.required" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="fieldConfigForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="fieldConfigForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldConfigDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="fieldConfigSaving" @click="handleFieldConfigSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 高级配置弹窗 -->
    <el-dialog v-model="configDialogVisible" :title="configIsEdit ? '编辑配置' : '新增配置'" width="520px" destroy-on-close>
      <el-form :model="configForm" :rules="configRules" ref="configFormRef" label-width="80px">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="configForm.configKey" :disabled="configIsEdit" placeholder="请输入配置键" />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input v-model="configForm.configValue" type="textarea" :rows="4" placeholder="请输入配置值" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="configForm.configGroup" placeholder="请输入分组" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="configForm.remark" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="handleConfigSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getConfigByGroup, saveConfigBatch, getConfigList, createConfig, updateConfig, deleteConfig, getRoleList } from '@/api/system'
import { getNumberingRules, updateNumberingRule, getNotificationConfigs, updateNotificationConfig, getBackupRecords, createBackup, restoreBackup, deleteBackup, downloadBackup, getBackupConfig, saveBackupConfig } from '@/api/system'
import { getExtFieldConfigsAll, createExtFieldConfig, updateExtFieldConfig, deleteExtFieldConfig } from '@/api/extField'

const uploadUrl = (import.meta.env.VITE_API_BASE || '/api') + '/common/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + (localStorage?.getItem('token') || '') }))

const activeTab = ref('company')

// ===================== 企业信息 =====================
const companyLoading = ref(false)
const companySaving = ref(false)
const companyForm = reactive({
  company_name: '',
  company_short_name: '',
  company_industry: '',
  company_scale: '',
  company_credit_code: '',
  company_legal_person: '',
  company_phone: '',
  company_address: '',
  company_logo: ''
})

function handleLogoSuccess(res) {
  if (res.data) companyForm.company_logo = res.data
}

async function loadCompany() {
  companyLoading.value = true
  try {
    const res = await getConfigByGroup('company')
    const list = res.data || []
    list.forEach(c => {
      if (companyForm.hasOwnProperty(c.configKey)) {
        companyForm[c.configKey] = c.configValue || ''
      }
    })
  } catch {} finally { companyLoading.value = false }
}

async function saveCompany() {
  companySaving.value = true
  try {
    const data = {}
    Object.keys(companyForm).forEach(k => { data[k] = companyForm[k] || '' })
    await saveConfigBatch('company', data)
    ElMessage.success('企业信息保存成功')
  } catch {} finally { companySaving.value = false }
}

// ===================== 审批流配置 =====================
const approvalLoading = ref(false)
const approvalRules = ref([
  { key: 'approval_sales_order', businessName: '销售订单', description: '销售订单提交后需要审批', enabled: true, hasAmountLimit: true, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_purchase_order', businessName: '采购订单', description: '采购订单提交后需要审批', enabled: true, hasAmountLimit: true, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_outsource_order', businessName: '外协订单', description: '外协订单提交后需要审批', enabled: true, hasAmountLimit: true, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_bom_version_activation', businessName: 'BOM版本激活', description: 'BOM版本激活前需要审批', enabled: true, hasAmountLimit: false, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_wage', businessName: '工资审批', description: '工资确认后需要审批才能发放', enabled: true, hasAmountLimit: false, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_stock_out', businessName: '出库审批', description: '大额出库需要审批', enabled: false, hasAmountLimit: true, autoApproveLimit: 5000, timeoutHours: 0 },
  { key: 'approval_order_change', businessName: '订单变更', description: '订单变更需要审批', enabled: false, hasAmountLimit: false, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_wo_change', businessName: '工单变更', description: '工单变更需要审批', enabled: false, hasAmountLimit: false, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_inventory_adjust', businessName: '库存调整', description: '库存手动调整需要审批', enabled: false, hasAmountLimit: true, autoApproveLimit: 0, timeoutHours: 0 },
  { key: 'approval_scrap', businessName: '报废申请', description: '物料报废需要审批', enabled: true, hasAmountLimit: true, autoApproveLimit: 0, timeoutHours: 0 }
])

async function loadApproval() {
  approvalLoading.value = true
  try {
    const res = await getConfigByGroup('approval')
    const list = res.data || []
    const configMap = {}
    list.forEach(c => { configMap[c.configKey] = c.configValue })
    approvalRules.value.forEach(rule => {
      if (configMap[rule.key]) {
        try {
          const saved = JSON.parse(configMap[rule.key])
          rule.enabled = saved.enabled !== false
          rule.autoApproveLimit = saved.autoApproveLimit || 0
          rule.timeoutHours = saved.timeoutHours || 0
        } catch {}
      }
    })
  } catch {} finally { approvalLoading.value = false }
}

async function saveApprovalRule(rule) {
  try {
    const data = {}
    data[rule.key] = JSON.stringify({
      enabled: rule.enabled,
      autoApproveLimit: rule.autoApproveLimit,
      timeoutHours: rule.timeoutHours
    })
    await saveConfigBatch('approval', data)
    ElMessage.success('审批规则已保存')
  } catch {}
}

// ===================== 编号规则 SYS04 =====================
const numberLoading = ref(false)
const numberRuleSaving = ref(false)
const numberRuleDialogVisible = ref(false)
const numberRuleForm = reactive({
  id: null,
  businessName: '',
  prefix: '',
  hasDate: true,
  dateFormat: 'YYYYMMDD',
  seqLength: 3
})
const numberRules = ref([
  { id: 'num_sales_order', businessName: '销售订单', prefix: 'SO', dateFormat: 'YYYYMMDD', seqLength: 3 },
  { id: 'num_work_order', businessName: '生产工单', prefix: 'WO', dateFormat: 'YYYYMMDD', seqLength: 3 },
  { id: 'num_outsource_apply', businessName: '外协申请', prefix: 'OR', dateFormat: 'YYYYMMDD', seqLength: 3 },
  { id: 'num_outsource_order', businessName: '外协加工', prefix: 'OS', dateFormat: 'YYYYMMDD', seqLength: 3 },
  { id: 'num_purchase_order', businessName: '采购订单', prefix: 'PO', dateFormat: 'YYYYMMDD', seqLength: 3 },
  { id: 'num_customer', businessName: '客户编号', prefix: 'C', dateFormat: 'none', seqLength: 6 },
  { id: 'num_product', businessName: '物料编号(成品)', prefix: 'P', dateFormat: 'none', seqLength: 6 },
  { id: 'num_material', businessName: '物料编号(原料)', prefix: 'M', dateFormat: 'none', seqLength: 6 },
  { id: 'num_supplier', businessName: '供应商编号', prefix: 'S', dateFormat: 'none', seqLength: 4 }
])

function generatePreview(rule) {
  const now = new Date()
  let dateStr = ''
  const fmt = rule.hasDate === false ? 'none' : (rule.dateFormat || 'none')
  if (fmt === 'YYYYMMDD') {
    dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  } else if (fmt === 'YYMMDD') {
    dateStr = `${String(now.getFullYear()).slice(2)}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
  }
  const seq = '1'.padStart(rule.seqLength || 3, '0')
  return `${rule.prefix || ''}${dateStr}${seq}`
}

async function loadNumberRules() {
  numberLoading.value = true
  try {
    const res = await getNumberingRules()
    const list = res.data || []
    if (list.length > 0) {
      numberRules.value = list.map(item => ({
        id: item.id,
        businessName: item.businessName,
        prefix: item.prefix,
        dateFormat: item.dateFormat || 'none',
        seqLength: item.seqLength || 3
      }))
    }
  } catch {
    // fallback to default rules
  } finally { numberLoading.value = false }
}

function openNumberRuleDialog(row) {
  numberRuleForm.id = row.id
  numberRuleForm.businessName = row.businessName
  numberRuleForm.prefix = row.prefix
  numberRuleForm.hasDate = row.dateFormat !== 'none'
  numberRuleForm.dateFormat = row.dateFormat !== 'none' ? row.dateFormat : 'YYYYMMDD'
  numberRuleForm.seqLength = row.seqLength
  numberRuleDialogVisible.value = true
}

async function handleNumberRuleSubmit() {
  numberRuleSaving.value = true
  try {
    const data = {
      prefix: numberRuleForm.prefix,
      dateFormat: numberRuleForm.hasDate ? numberRuleForm.dateFormat : 'none',
      seqLength: numberRuleForm.seqLength
    }
    await updateNumberingRule(numberRuleForm.id, data)
    // update local data
    const rule = numberRules.value.find(r => r.id === numberRuleForm.id)
    if (rule) {
      rule.prefix = data.prefix
      rule.dateFormat = data.dateFormat
      rule.seqLength = data.seqLength
    }
    numberRuleDialogVisible.value = false
    ElMessage.success('编号规则已保存')
  } catch {} finally { numberRuleSaving.value = false }
}

// ===================== 通知配置 SYS05 =====================
const notifyLoading = ref(false)
const notifySaving = ref(false)
const notifyDialogVisible = ref(false)
const notifyDialogForm = reactive({
  id: null,
  scenarioKey: '',
  scenarioName: '',
  webEnabled: true,
  wechatEnabled: false,
  receiverRoles: [],
  pushTime: '08:00',
  maxDailyPush: 10
})
const notifyScenarios = ref([
  { id: 'notify_new_order', scenarioKey: 'new_order', scenarioName: '新订单通知', webEnabled: true, wechatEnabled: false, receiverRoles: ['boss', 'sales'], pushTime: '', maxDailyPush: 10 },
  { id: 'notify_approval_todo', scenarioKey: 'approval_todo', scenarioName: '审批待办', webEnabled: true, wechatEnabled: true, receiverRoles: ['boss'], pushTime: '', maxDailyPush: 10 },
  { id: 'notify_inventory_alert', scenarioKey: 'inventory_alert', scenarioName: '库存预警', webEnabled: true, wechatEnabled: false, receiverRoles: ['warehouse'], pushTime: '', maxDailyPush: 10 },
  { id: 'notify_delivery_alert', scenarioKey: 'delivery_alert', scenarioName: '交期预警', webEnabled: true, wechatEnabled: false, receiverRoles: ['production_manager', 'sales'], pushTime: '', maxDailyPush: 10 },
  { id: 'notify_completion', scenarioKey: 'completion', scenarioName: '完工通知', webEnabled: true, wechatEnabled: false, receiverRoles: ['production_manager'], pushTime: '', maxDailyPush: 10 },
  { id: 'notify_payment_remind', scenarioKey: 'payment_remind', scenarioName: '收款提醒', webEnabled: true, wechatEnabled: false, receiverRoles: ['finance', 'boss'], pushTime: '', maxDailyPush: 10 },
  { id: 'notify_exception', scenarioKey: 'exception', scenarioName: '异常上报', webEnabled: true, wechatEnabled: true, receiverRoles: ['boss', 'production_manager'], pushTime: '', maxDailyPush: 10 },
  { id: 'notify_ai_brief', scenarioKey: 'ai_brief', scenarioName: 'AI简报推送', webEnabled: true, wechatEnabled: true, receiverRoles: ['boss'], pushTime: '08:00', maxDailyPush: 1 },
  { id: 'notify_work_order_cancelled', scenarioKey: 'work_order_cancelled', scenarioName: '生产工单取消', webEnabled: true, wechatEnabled: false, receiverRoles: ['production_manager', 'sales'], pushTime: '', maxDailyPush: 10 }
])

// 动态加载: 从"角色管理"取所有真实角色, 通知配置里用来勾选 / 展示
const allRoles = ref([])
const roleNameMap = computed(() => {
  const m = {}
  for (const r of allRoles.value) {
    if (r.roleCode) m[r.roleCode] = r.roleName || r.roleCode
  }
  return m
})
async function loadAllRoles() {
  try {
    const res = await getRoleList()
    // 仅排除明确禁用的, 其他状态(active/normal/null 等)都显示
    const disabled = new Set(['0', 0, 'disabled', 'inactive', 'forbidden'])
    allRoles.value = (res.data || []).filter(r => !disabled.has(r.status))
  } catch {
    allRoles.value = []
  }
}

function getRoleName(role) {
  return roleNameMap.value[role] || role
}

async function loadNotify() {
  notifyLoading.value = true
  // 顺便加载真实角色列表给"接收角色" checkbox 用
  if (allRoles.value.length === 0) loadAllRoles()
  try {
    const res = await getNotificationConfigs()
    const list = res.data || []
    if (list.length > 0) {
      notifyScenarios.value = list.map(item => ({
        id: item.id,
        scenarioKey: item.scenarioKey,
        scenarioName: item.scenarioName,
        webEnabled: item.webEnabled !== false,
        wechatEnabled: item.wechatEnabled === true,
        receiverRoles: item.receiverRoles || [],
        pushTime: item.pushTime || '',
        maxDailyPush: item.maxDailyPush || 10
      }))
    }
  } catch {
    // fallback to default scenarios
  } finally { notifyLoading.value = false }
}

function openNotifyDialog(row) {
  notifyDialogForm.id = row.id
  notifyDialogForm.scenarioKey = row.scenarioKey
  notifyDialogForm.scenarioName = row.scenarioName
  notifyDialogForm.webEnabled = row.webEnabled
  notifyDialogForm.wechatEnabled = row.wechatEnabled
  notifyDialogForm.receiverRoles = [...(row.receiverRoles || [])]
  notifyDialogForm.pushTime = row.pushTime || '08:00'
  notifyDialogForm.maxDailyPush = row.maxDailyPush || 10
  notifyDialogVisible.value = true
}

async function handleNotifyScenarioChange(row) {
  try {
    await updateNotificationConfig(row.id, {
      webEnabled: row.webEnabled,
      wechatEnabled: row.wechatEnabled,
      receiverRoles: row.receiverRoles,
      pushTime: row.pushTime,
      maxDailyPush: row.maxDailyPush
    })
    ElMessage.success('通知配置已更新')
  } catch {}
}

async function handleNotifyDialogSubmit() {
  notifySaving.value = true
  try {
    const data = {
      webEnabled: notifyDialogForm.webEnabled,
      wechatEnabled: notifyDialogForm.wechatEnabled,
      receiverRoles: notifyDialogForm.receiverRoles,
      pushTime: notifyDialogForm.pushTime,
      maxDailyPush: notifyDialogForm.maxDailyPush
    }
    await updateNotificationConfig(notifyDialogForm.id, data)
    // update local data
    const scenario = notifyScenarios.value.find(s => s.id === notifyDialogForm.id)
    if (scenario) {
      scenario.webEnabled = data.webEnabled
      scenario.wechatEnabled = data.wechatEnabled
      scenario.receiverRoles = [...data.receiverRoles]
      scenario.pushTime = data.pushTime
      scenario.maxDailyPush = data.maxDailyPush
    }
    notifyDialogVisible.value = false
    ElMessage.success('通知配置已保存')
  } catch {} finally { notifySaving.value = false }
}

// ===================== 数据备份 SYS07 =====================
const backupConfigLoading = ref(false)
const backupConfigSaving = ref(false)
const backupCreating = ref(false)
const backupRecordsLoading = ref(false)
const backupConfigForm = reactive({
  enabled: false,
  frequency: 'daily',
  backupTime: '02:00',
  keepCount: 7
})
const backupRecords = ref([])
const backupPage = reactive({ current: 1, size: 10, total: 0 })

async function loadBackupConfig() {
  backupConfigLoading.value = true
  try {
    const res = await getBackupConfig()
    const config = res.data || {}
    backupConfigForm.enabled = config.enabled !== false
    backupConfigForm.frequency = config.frequency || 'daily'
    backupConfigForm.backupTime = config.backupTime || '02:00'
    backupConfigForm.keepCount = config.keepCount || 7
  } catch {} finally { backupConfigLoading.value = false }
}

async function handleSaveBackupConfig() {
  backupConfigSaving.value = true
  try {
    await saveBackupConfig({
      enabled: backupConfigForm.enabled,
      frequency: backupConfigForm.frequency,
      backupTime: backupConfigForm.backupTime,
      keepCount: backupConfigForm.keepCount
    })
    ElMessage.success('备份配置保存成功')
  } catch {} finally { backupConfigSaving.value = false }
}

async function loadBackupRecords() {
  backupRecordsLoading.value = true
  try {
    const res = await getBackupRecords({ current: backupPage.current, size: backupPage.size })
    const data = res.data || {}
    backupRecords.value = data.records || []
    backupPage.total = data.total || 0
  } catch {} finally { backupRecordsLoading.value = false }
}

async function loadBackup() {
  await Promise.all([loadBackupConfig(), loadBackupRecords()])
}

function handleManualBackup() {
  ElMessageBox.confirm('确定立即执行数据备份？备份过程中系统可正常使用。', '立即备份', { type: 'info', confirmButtonText: '开始备份', cancelButtonText: '取消' }).then(async () => {
    backupCreating.value = true
    try {
      await createBackup()
      ElMessage.success('备份任务已提交，请稍后刷新查看结果')
      loadBackupRecords()
    } catch {} finally { backupCreating.value = false }
  }).catch(() => {})
}

function handleDownloadBackup(row) {
  downloadBackup(row.id).then(res => {
    const blob = new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `backup_${row.backupNo}.sql`
    link.click()
    URL.revokeObjectURL(url)
  }).catch(() => {
    ElMessage.error('下载失败')
  })
}

function handleRestoreBackup(row) {
  ElMessageBox.prompt(
    '恢复操作将覆盖当前所有数据，此操作不可撤销！请输入企业名称以确认恢复：',
    '危险操作确认',
    {
      type: 'warning',
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
      inputPlaceholder: '请输入企业名称',
      inputValidator: (val) => {
        if (!val || val.trim() === '') return '请输入企业名称'
        return true
      }
    }
  ).then(async ({ value }) => {
    try {
      await restoreBackup(row.id)
      ElMessage.success('恢复任务已提交，系统将在恢复完成后自动重启')
    } catch {}
  }).catch(() => {})
}

async function handleDeleteBackup(row) {
  await ElMessageBox.confirm(`确定删除备份「${row.backupNo}」？删除后不可恢复。`, '删除备份', { type: 'warning' })
  try {
    await deleteBackup(row.id)
    ElMessage.success('备份已删除')
    loadBackupRecords()
  } catch {}
}

// ===================== 高级配置 =====================
const advancedLoading = ref(false)
const advancedConfigs = ref([])
const configDialogVisible = ref(false)
const configIsEdit = ref(false)
const configSaving = ref(false)
const configFormRef = ref(null)
const configForm = reactive({ configKey: '', configValue: '', configGroup: '', remark: '' })
const configRules = {
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }]
}

async function loadAdvanced() {
  advancedLoading.value = true
  try {
    const res = await getConfigList()
    const list = res.data || []
    advancedConfigs.value = Array.isArray(list) ? list : (list.records || [])
  } catch {} finally { advancedLoading.value = false }
}

function openConfigDialog(row) {
  if (row) {
    configIsEdit.value = true
    configForm.configKey = row.configKey
    configForm.configValue = row.configValue
    configForm.configGroup = row.configGroup || ''
    configForm.remark = row.remark || ''
  } else {
    configIsEdit.value = false
    configForm.configKey = ''
    configForm.configValue = ''
    configForm.configGroup = ''
    configForm.remark = ''
  }
  configDialogVisible.value = true
}

async function handleConfigSubmit() {
  await configFormRef.value.validate()
  configSaving.value = true
  try {
    const payload = { configKey: configForm.configKey, configValue: configForm.configValue, configGroup: configForm.configGroup, remark: configForm.remark }
    if (configIsEdit.value) {
      await updateConfig(configForm.configKey, payload)
      ElMessage.success('修改成功')
    } else {
      await createConfig(payload)
      ElMessage.success('新增成功')
    }
    configDialogVisible.value = false
    loadAdvanced()
  } catch {} finally { configSaving.value = false }
}

async function handleDeleteConfig(row) {
  await ElMessageBox.confirm(`确定删除配置「${row.configKey}」？`, '提示', { type: 'warning' })
  try {
    await deleteConfig(row.configKey)
    ElMessage.success('删除成功')
    loadAdvanced()
  } catch {}
}

// ===================== 扩展字段配置 =====================
const extFieldModules = [
  { value: 'sales_order', label: '销售订单' },
  { value: 'sales_order_item', label: '销售订单明细' },
  { value: 'customer', label: '客户' },
  { value: 'supplier', label: '供应商' },
  { value: 'purchase_order', label: '采购订单' },
  { value: 'production_plan', label: '车间计划' },
  { value: 'work_order', label: '生产工单' }
]
const extFieldModule = ref('sales_order')
const fieldConfigs = ref([])
const fieldConfigLoading = ref(false)
const fieldConfigDialogVisible = ref(false)
const fieldConfigSaving = ref(false)
const fieldConfigFormRef = ref()
const fieldConfigForm = reactive({
  id: null, fieldKey: '', fieldLabel: '', fieldType: 'text',
  fieldOptionsRaw: '', showInList: 1, showInForm: 1, showInSearch: 0,
  required: 0, sortOrder: 0, enabled: 1
})
const fieldConfigRules = {
  fieldKey: [{ required: true, message: '请输入字段Key' }, { pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/, message: '只能包含字母、数字、下划线，且不能以数字开头' }],
  fieldLabel: [{ required: true, message: '请输入显示名称' }],
  fieldType: [{ required: true, message: '请选择类型' }]
}

async function loadFieldConfigs() {
  fieldConfigLoading.value = true
  try {
    const res = await getExtFieldConfigsAll(extFieldModule.value)
    fieldConfigs.value = res.data || []
  } catch {} finally { fieldConfigLoading.value = false }
}

function openFieldConfigDialog(row) {
  if (row) {
    Object.assign(fieldConfigForm, {
      id: row.id, fieldKey: row.fieldKey, fieldLabel: row.fieldLabel,
      fieldType: row.fieldType || 'text',
      fieldOptionsRaw: row.fieldOptions ? JSON.parse(row.fieldOptions).join('\n') : '',
      showInList: row.showInList ?? 1, showInForm: row.showInForm ?? 1, showInSearch: row.showInSearch ?? 0,
      required: row.required ?? 0, sortOrder: row.sortOrder ?? 0,
      enabled: row.enabled ?? 1
    })
  } else {
    Object.assign(fieldConfigForm, {
      id: null, fieldKey: '', fieldLabel: '', fieldType: 'text',
      fieldOptionsRaw: '', showInList: 1, showInForm: 1, showInSearch: 0,
      required: 0, sortOrder: 0, enabled: 1
    })
  }
  fieldConfigDialogVisible.value = true
}

async function handleFieldConfigSubmit() {
  await fieldConfigFormRef.value?.validate()
  fieldConfigSaving.value = true
  try {
    const data = {
      module: extFieldModule.value,
      fieldKey: fieldConfigForm.fieldKey,
      fieldLabel: fieldConfigForm.fieldLabel,
      fieldType: fieldConfigForm.fieldType,
      fieldOptions: fieldConfigForm.fieldType === 'select' && fieldConfigForm.fieldOptionsRaw
        ? JSON.stringify(fieldConfigForm.fieldOptionsRaw.split('\n').map(s => s.trim()).filter(Boolean))
        : null,
      showInList: fieldConfigForm.showInList,
      showInForm: fieldConfigForm.showInForm,
      showInSearch: fieldConfigForm.showInSearch,
      required: fieldConfigForm.required,
      sortOrder: fieldConfigForm.sortOrder,
      enabled: fieldConfigForm.enabled
    }
    if (fieldConfigForm.id) {
      await updateExtFieldConfig(fieldConfigForm.id, data)
    } else {
      await createExtFieldConfig(data)
    }
    ElMessage.success('保存成功')
    fieldConfigDialogVisible.value = false
    loadFieldConfigs()
  } catch {} finally { fieldConfigSaving.value = false }
}

async function handleDeleteFieldConfig(row) {
  await ElMessageBox.confirm(`确定删除字段「${row.fieldLabel}」吗？已有数据中该字段的值不会被清除。`, '删除字段', { type: 'warning' })
  await deleteExtFieldConfig(row.id)
  ElMessage.success('删除成功')
  loadFieldConfigs()
}

// ===================== Tab切换 =====================
function handleTabChange(tab) {
  if (tab === 'company') loadCompany()
  else if (tab === 'approval') loadApproval()
  else if (tab === 'numbering') loadNumberRules()
  else if (tab === 'notification') loadNotify()
  else if (tab === 'extFields') loadFieldConfigs()
  else if (tab === 'backup') loadBackup()
  else if (tab === 'advanced') loadAdvanced()
}

onMounted(() => { loadCompany() })
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.text-muted { color: #c0c4cc; }
.number-preview {
  font-family: monospace;
  color: #409eff;
  font-weight: bold;
}
</style>
