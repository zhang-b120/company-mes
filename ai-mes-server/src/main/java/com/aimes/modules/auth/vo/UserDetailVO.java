package com.aimes.modules.auth.vo;

import lombok.Data;
import java.util.List;

@Data
public class UserDetailVO {
    private Long id;
    private String username;
    private String realName;
    private String avatar;
    private List<String> roles;
    private List<String> permissions;
    private List<MenuVO> menus;

    @Data
    public static class MenuVO {
        private Long id;
        private Long parentId;
        private String menuName;
        private String menuType;
        private String platform;
        private String path;
        private String component;
        private String icon;
        private String permission;
        private Integer sortOrder;
        private List<MenuVO> children;
    }
}
