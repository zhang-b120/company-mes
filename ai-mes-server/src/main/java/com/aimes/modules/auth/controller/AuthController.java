package com.aimes.modules.auth.controller;

import com.aimes.common.result.R;
import com.aimes.modules.auth.dto.ChangePasswordDTO;
import com.aimes.modules.auth.dto.LoginDTO;
import com.aimes.modules.auth.dto.RefreshTokenDTO;
import com.aimes.modules.auth.service.AuthService;
import com.aimes.modules.auth.vo.LoginVO;
import com.aimes.modules.auth.vo.UserDetailVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /**
     * 工位机刷卡/手动免密登录
     * - 刷卡: 传 icCardNo (读卡器原始卡号 → 后端按 sys_user.ic_card_no 反查员工)
     * - 手动: 传 username (员工账号 → 后端直接按 sys_user.username 查找)
     */
    @PostMapping("/card-login")
    public R<LoginVO> cardLogin(@RequestBody Map<String, String> body) {
        String icCardNo = body.get("icCardNo");
        String username = body.get("username");
        if (icCardNo != null && !icCardNo.isBlank()) {
            return R.ok(authService.cardLogin(icCardNo));
        }
        if (username != null && !username.isBlank()) {
            return R.ok(authService.cardLoginByUsername(username));
        }
        return R.fail("IC卡号或员工账号不能为空");
    }

    @PostMapping("/refresh")
    public R<Map<String, String>> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return R.ok(authService.refresh(dto));
    }

    @GetMapping("/userinfo")
    public R<UserDetailVO> getUserInfo() {
        return R.ok(authService.getUserInfo());
    }

    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok();
    }
}
