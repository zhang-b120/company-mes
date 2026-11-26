package com.aimes.modules.order.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.order.entity.BizOrderAttachment;
import com.aimes.modules.order.mapper.BizOrderAttachmentMapper;
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
public class BizOrderAttachmentService extends ServiceImpl<BizOrderAttachmentMapper, BizOrderAttachment> {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url-prefix}")
    private String uploadUrlPrefix;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 上传附件并保存记录
     *
     * @param orderId  订单ID
     * @param orderNo  订单号（用于生成服务器文件名）
     * @param file     上传的文件
     * @return 保存后的附件记录
     */
    public BizOrderAttachment upload(Long orderId, String orderNo, MultipartFile file) throws IOException {
        String rawName = file.getOriginalFilename();
        String originalName = (rawName != null && !rawName.isBlank()) ? rawName : "unknown";
        String ext = "";
        if (rawName != null) {
            int dotIdx = rawName.lastIndexOf('.');
            if (dotIdx >= 0) {
                ext = rawName.substring(dotIdx); // 含点，如 ".pdf"
            }
        }

        // 服务器文件名：订单号_时间戳.ext
        String timestamp = LocalDateTime.now().format(TS_FMT);
        String serverName = orderNo + "_" + timestamp + ext;

        // 存储目录
        File dir = new File(uploadPath + "/order-attachments");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("附件目录创建失败");
        }

        File dest = new File(dir, serverName);
        file.transferTo(dest);

        BizOrderAttachment attachment = new BizOrderAttachment();
        attachment.setOrderId(orderId);
        attachment.setOrderNo(orderNo);
        attachment.setOriginalName(originalName);
        attachment.setServerName(serverName);
        attachment.setFilePath(dest.getAbsolutePath());
        attachment.setFileUrl(uploadUrlPrefix + "/order-attachments/" + serverName);
        attachment.setFileSize(file.getSize());
        attachment.setFileType(ext.isEmpty() ? "" : ext.substring(1).toLowerCase()); // 不含点
        save(attachment);
        return attachment;
    }

    /**
     * 查询订单的所有附件
     */
    public List<BizOrderAttachment> getByOrderId(Long orderId) {
        return list(new LambdaQueryWrapper<BizOrderAttachment>()
                .eq(BizOrderAttachment::getOrderId, orderId)
                .orderByDesc(BizOrderAttachment::getCreateTime));
    }

    /**
     * 删除附件（同时删除磁盘文件）
     */
    public void deleteAttachment(Long id) {
        BizOrderAttachment attachment = getById(id);
        if (attachment == null) throw new BusinessException("附件不存在");
        // 删除磁盘文件
        File file = new File(attachment.getFilePath());
        if (file.exists()) {
            file.delete();
        }
        removeById(id);
    }
}
