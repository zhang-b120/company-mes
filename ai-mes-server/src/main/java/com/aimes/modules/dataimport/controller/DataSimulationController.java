package com.aimes.modules.dataimport.controller;

import com.aimes.common.result.R;
import com.aimes.modules.dataimport.service.DataSimulationService;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据模拟生成控制器
 * 流程: 配置→生成预览→导出Excel→人工修改→导入
 */
@RestController
@RequestMapping("/api/sim")
@RequiredArgsConstructor
public class DataSimulationController {

    private final DataSimulationService simulationService;
    private final SysUserMapper sysUserMapper;

    @GetMapping("/modules")
    public R<List<Map<String, Object>>> getModules() {
        return R.ok(DataSimulationService.MODULES);
    }

    @GetMapping("/users")
    public R<List<Map<String, Object>>> getUsers() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0).eq(SysUser::getStatus, "active"));
        List<Map<String, Object>> list = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("label", u.getRealName() != null ? u.getRealName() : u.getUsername());
            return m;
        }).toList();
        return R.ok(list);
    }

    @PostMapping("/{module}/generate")
    public R<Map<String, Object>> generate(@PathVariable String module,
                                            @RequestBody Map<String, Object> config) {
        return R.ok(simulationService.generate(module, config));
    }

    @GetMapping("/{module}/export")
    public void export(@PathVariable String module, HttpServletResponse response) throws IOException {
        simulationService.export(module, response);
    }

    @PostMapping("/{module}/import")
    public R<Map<String, Object>> importData(@PathVariable String module,
                                              @RequestParam(value = "files", required = false) MultipartFile[] files) {
        return R.ok(simulationService.importData(module, files));
    }
}
