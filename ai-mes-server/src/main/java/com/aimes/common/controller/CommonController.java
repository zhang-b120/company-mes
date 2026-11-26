package com.aimes.common.controller;

import com.aimes.common.result.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/common")
public class CommonController {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url-prefix}")
    private String uploadUrlPrefix;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return R.fail("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        // 按日期分目录
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String serverName = UUID.randomUUID().toString().replace("-", "") + ext;

        File dir = new File(uploadPath + "/common/" + datePath);
        if (!dir.exists() && !dir.mkdirs()) {
            return R.fail("目录创建失败");
        }

        File dest = new File(dir, serverName);
        file.transferTo(dest);

        String url = uploadUrlPrefix + "/common/" + datePath + "/" + serverName;
        return R.ok(Map.of("url", url, "name", originalName != null ? originalName : serverName));
    }
}
