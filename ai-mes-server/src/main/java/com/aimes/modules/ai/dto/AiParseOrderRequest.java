package com.aimes.modules.ai.dto;

import lombok.Data;

/**
 * AI智能接单解析请求DTO
 * 支持文本内容输入，文件通过MultipartFile[]接收
 */
@Data
public class AiParseOrderRequest {

    /** 订单文本内容（手动输入或OCR识别后的文本） */
    private String textContent;

    // 文件附件通过接口参数 MultipartFile[] files 接收，不在此DTO中定义
}
