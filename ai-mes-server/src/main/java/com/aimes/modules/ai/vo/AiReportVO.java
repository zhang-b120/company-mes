package com.aimes.modules.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * AI报告结果VO
 * 用于每日简报、异常分析报告等场景，包含多个报告段落和总结
 */
@Data
public class AiReportVO {

    /** 报告各段落内容 */
    private List<Section> sections;

    /** 报告总结摘要 */
    private String summary;

    /** 报告日期（格式：yyyy-MM-dd） */
    private String date;

    /**
     * 报告段落
     */
    @Data
    public static class Section {
        /** 段落标题 */
        private String title;

        /** 段落内容 */
        private String content;
    }
}
