package com.aimes.modules.auth.vo;

import lombok.Data;
import java.util.List;

@Data
public class LoginVO {
    private String accessToken;
    private String refreshToken;
    private UserInfoVO userInfo;

    @Data
    public static class UserInfoVO {
        private Long id;
        private String username;
        private String realName;
        private String avatar;
        private List<String> roles;
        private List<String> permissions;
    }
}
