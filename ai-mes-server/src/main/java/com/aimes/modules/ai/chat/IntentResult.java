package com.aimes.modules.ai.chat;

import lombok.Data;

/**
 * 意图识别结果
 */
@Data
public class IntentResult {

    /** 识别到的意图: daily_briefing / quality_trend / production_efficiency / delivery_risk /
     *  purchase_suggestion / cost_analysis / scheduling / smart_order / product_filing /
     *  change_assessment / exception_analysis / query / input / chat */
    private String intent;

    /** 识别到的业务模块 (nullable) */
    private String module;

    /** 是否需要用户补充更多信息 */
    private boolean needsMoreInput;

    /** 从用户消息中提取的关键输入文本 */
    private String extractedInput;

    /** 当 needsMoreInput=true 时，向用户追问的提示语 */
    private String clarifyPrompt;

    public static IntentResult of(String intent) {
        IntentResult r = new IntentResult();
        r.setIntent(intent);
        return r;
    }

    public static IntentResult of(String intent, String module) {
        IntentResult r = new IntentResult();
        r.setIntent(intent);
        r.setModule(module);
        return r;
    }

    public static IntentResult needInput(String intent, String clarifyPrompt) {
        IntentResult r = new IntentResult();
        r.setIntent(intent);
        r.setNeedsMoreInput(true);
        r.setClarifyPrompt(clarifyPrompt);
        return r;
    }
}
