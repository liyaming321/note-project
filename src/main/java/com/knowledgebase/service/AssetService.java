package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.dto.ImageUploadResponse;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地资源文件服务。
 */
@Service
public class AssetService {

    private static final DateTimeFormatter IMAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg");

    private final Path imagesPath;

    /**
     * 创建资源文件服务。
     *
     * @param properties 知识库配置
     */
    public AssetService(KnowledgeBaseProperties properties) {
        this.imagesPath = Paths.get(properties.getImagesPath()).toAbsolutePath().normalize();
    }

    /**
     * 上传图片到本地图片目录。
     *
     * @param file 图片文件
     * @return 图片访问信息
     */
    public ImageUploadResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("图片文件不能为空");
        }
        String extension = resolveImageExtension(file);
        String fileName = IMAGE_TIME_FORMATTER.format(LocalDateTime.now())
                + "-"
                + UUID.randomUUID().toString().substring(0, 8)
                + extension;
        Path targetPath = imagesPath.resolve(fileName).normalize();
        if (!targetPath.startsWith(imagesPath)) {
            throw new BusinessException("图片文件名非法");
        }
        try {
            Files.createDirectories(imagesPath);
            file.transferTo(targetPath);
            return new ImageUploadResponse(fileName, "/api/assets/images/" + fileName, file.getSize());
        } catch (IOException ex) {
            throw new BusinessException("保存图片失败：" + ex.getMessage());
        }
    }

    /**
     * 读取本地图片文件。
     *
     * @param fileName 图片文件名
     * @return 图片文件
     */
    public LocalAsset readImage(String fileName) {
        String safeFileName = sanitizeFileName(fileName);
        Path imagePath = imagesPath.resolve(safeFileName).normalize();
        if (!imagePath.startsWith(imagesPath) || Files.notExists(imagePath) || !Files.isRegularFile(imagePath)) {
            throw new ResourceNotFoundException("图片不存在：" + safeFileName);
        }
        try {
            String contentType = Files.probeContentType(imagePath);
            return new LocalAsset(
                    safeFileName,
                    contentType == null ? "application/octet-stream" : contentType,
                    Files.readAllBytes(imagePath)
            );
        } catch (IOException ex) {
            throw new BusinessException("读取图片失败：" + ex.getMessage());
        }
    }

    /**
     * 解析并校验图片扩展名。
     *
     * @param file 上传文件
     * @return 图片扩展名
     */
    private String resolveImageExtension(MultipartFile file) {
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        for (String extension : ALLOWED_IMAGE_EXTENSIONS) {
            if (fileName.endsWith(extension)) {
                return extension;
            }
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.startsWith("image/")) {
            return "." + contentType.substring("image/".length()).replace("jpeg", "jpg");
        }
        throw new BusinessException("仅支持图片文件上传");
    }

    /**
     * 清理文件名路径字符。
     *
     * @param fileName 原始文件名
     * @return 安全文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ResourceNotFoundException("图片不存在");
        }
        return fileName.replace("\\", "/").replaceAll("^.*/", "");
    }

    /**
     * 本地资源文件。
     *
     * @param fileName 文件名
     * @param contentType 内容类型
     * @param content 文件内容
     */
    public record LocalAsset(String fileName, String contentType, byte[] content) {
    }
}
