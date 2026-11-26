package com.aimes.modules.ai.service;

import com.aimes.modules.ai.prompt.PromptTemplates;
import com.aimes.modules.master.mapper.BizProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI产品场景服务
 * 提供AI辅助产品建档功能，根据自然语言描述自动生成结构化产品信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProductService {

    private final DeepSeekClient deepSeekClient;
    private final BizProductMapper productMapper;

    /**
     * AI产品建档：根据自然语言描述生成结构化产品信息
     * 用户输入产品描述文本，AI自动提取产品名称、规格型号、分类、单位等信息
     *
     * @param description 产品描述文本
     * @return AI生成的结构化产品信息（JSON字符串）
     */
    public String aiProductFiling(String description) {
        return deepSeekClient.chat(PromptTemplates.AI_PRODUCT, description);
    }
}
