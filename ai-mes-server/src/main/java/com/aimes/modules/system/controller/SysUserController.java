package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.system.dto.SysUserDTO;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.service.SysUserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(userService.getPage(realName, phone, username, roleId, status, pageNum, pageSize));
    }

    @PostMapping
    public R<Void> create(@Valid @RequestBody SysUserDTO dto) {
        userService.createUser(dto);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysUserDTO dto) {
        userService.updateUser(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return R.ok();
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> selectList() {
        return R.ok(userService.getSelectList());
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        List<SysUser> list = userService.list();
        List<String> headers = Arrays.asList("账号", "姓名", "手机号", "状态", "入职日期", "备注");
        List<Function<SysUser, Object>> extractors = Arrays.asList(
                SysUser::getUsername, SysUser::getRealName, SysUser::getPhone,
                SysUser::getStatus, SysUser::getHireDate, SysUser::getRemark);
        ExcelUtil.export(response, "用户列表", headers, list, extractors);
    }

    @PostMapping("/import")
    public R<String> importUsers(@RequestParam("file") MultipartFile file) {
        List<String> headers = Arrays.asList("账号", "姓名", "手机号", "备注");
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, headers);
        int count = 0;
        for (Map<String, String> row : rows) {
            SysUserDTO dto = new SysUserDTO();
            dto.setUsername(row.get("账号"));
            dto.setRealName(row.get("姓名"));
            dto.setPhone(row.get("手机号"));
            dto.setRemark(row.get("备注"));
            dto.setPassword("123456");
            userService.createUser(dto);
            count++;
        }
        return R.ok("成功导入" + count + "条用户数据");
    }

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<String> headers = Arrays.asList("账号", "姓名", "手机号", "备注");
        ExcelUtil.exportTemplate(response, "用户导入模板", headers);
    }
}
