package com.aimes.modules.production.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionCommonController {

    private final SysUserMapper userMapper;

    @GetMapping("/assignee-options")
    public R<List<Map<String, Object>>> assigneeOptions() {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, "active")
                        .orderByAsc(SysUser::getId));
        List<Map<String, Object>> options = users.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("realName", u.getRealName());
            map.put("workshop", u.getWorkshop());
            return map;
        }).collect(Collectors.toList());
        return R.ok(options);
    }
}
