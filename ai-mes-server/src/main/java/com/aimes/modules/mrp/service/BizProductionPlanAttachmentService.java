package com.aimes.modules.mrp.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.mrp.entity.BizProductionPlanAttachment;
import com.aimes.modules.mrp.mapper.BizProductionPlanAttachmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BizProductionPlanAttachmentService extends ServiceImpl<BizProductionPlanAttachmentMapper, BizProductionPlanAttachment> {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url-prefix}")
    private String uploadUrlPrefix;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 上传附件并保存记录
     */
    public BizProductionPlanAttachment upload(Long planId, String planNo, MultipartFile file) throws IOException {
        String rawName = file.getOriginalFilename();
        String originalName = (rawName != null && !rawName.isBlank()) ? rawName : "unknown";
        String ext = "";
        if (rawName != null) {
            int dotIdx = rawName.lastIndexOf('.');
            if (dotIdx >= 0) {
                ext = rawName.substring(dotIdx);
            }
        }

        String timestamp = LocalDateTime.now().format(TS_FMT);
        String serverName = planNo + "_" + timestamp + ext;

        File dir = new File(uploadPath + "/production-plan-attachments");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("附件目录创建失败");
        }

        File dest = new File(dir, serverName);
        file.transferTo(dest);

        BizProductionPlanAttachment attachment = new BizProductionPlanAttachment();
        attachment.setPlanId(planId);
        attachment.setPlanNo(planNo);
        attachment.setOriginalName(originalName);
        attachment.setServerName(serverName);
        attachment.setFilePath(dest.getAbsolutePath());
        attachment.setFileUrl(uploadUrlPrefix + "/production-plan-attachments/" + serverName);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(ext.isEmpty() ? "" : ext.substring(1).toLowerCase());
        save(attachment);
        return attachment;
    }

    /**
     * 查询计划的所有附件
     */
    public List<BizProductionPlanAttachment> getByPlanId(Long planId) {
        return list(new LambdaQueryWrapper<BizProductionPlanAttachment>()
                .eq(BizProductionPlanAttachment::getPlanId, planId)
                .orderByDesc(BizProductionPlanAttachment::getCreateTime));
    }

    /**
     * 删除附件（同时删除磁盘文件）
     */
    public void deleteAttachment(Long id) {
        BizProductionPlanAttachment attachment = getById(id);
        if (attachment == null) throw new BusinessException("附件不存在");
        File file = new File(attachment.getFilePath());
        if (file.exists()) {
            file.delete();
        }
        removeById(id);
    }
}
