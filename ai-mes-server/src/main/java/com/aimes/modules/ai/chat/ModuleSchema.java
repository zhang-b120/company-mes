package com.aimes.modules.ai.chat;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 模块字段定义 — 描述一个业务模块的表单字段结构
 */
@Data
@AllArgsConstructor
public class ModuleSchema {

    private String module;
    private String moduleName;
    private List<FieldDef> fields;
    private boolean supportsBatch;

    @Data
    @AllArgsConstructor
    public static class FieldDef {
        private String name;
        private String label;
        private String type;       // string/number/date/select/text
        private boolean required;
        private String hint;       // AI提示（如"从已有客户列表匹配"）
    }

    /**
     * 生成供AI使用的字段描述prompt
     */
    public String toPromptDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("模块: ").append(moduleName).append("\n");
        sb.append("字段列表:\n");
        for (FieldDef f : fields) {
            sb.append("  - ").append(f.getName()).append("(").append(f.getLabel()).append(")")
                    .append(" [类型:").append(f.getType())
                    .append(f.isRequired() ? ", 必填" : ", 选填")
                    .append("]");
            if (f.getHint() != null && !f.getHint().isBlank()) {
                sb.append(" — ").append(f.getHint());
            }
            sb.append("\n");
        }
        if (supportsBatch) {
            sb.append("支持批量录入（如Excel多行数据）\n");
        }
        return sb.toString();
    }
}
