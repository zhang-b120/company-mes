package com.aimes.modules.dashboard.controller;

import com.aimes.common.result.R;
import com.aimes.modules.dashboard.service.Tv2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * TV2 大屏聚合接口(免登录)
 * 一次性返回 5 个 Tab(生产/销售/库存/采购/质量)的 KPI + 图表数据
 */
@RestController
@RequestMapping("/api/tv2")
@RequiredArgsConstructor
public class Tv2Controller {

    private final Tv2Service tv2Service;

    @GetMapping("/all")
    public R<Map<String, Object>> all() {
        return R.ok(tv2Service.getAll());
    }
}
