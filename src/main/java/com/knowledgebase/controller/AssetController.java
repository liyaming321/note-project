package com.knowledgebase.controller;

import com.knowledgebase.dto.ApiResponse;
import com.knowledgebase.dto.ImageUploadResponse;
import com.knowledgebase.service.AssetService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地资源文件控制器。
 */
@RestController
public class AssetController {

    private final AssetService assetService;

    /**
     * 创建本地资源控制器。
     *
     * @param assetService 资源服务
     */
    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /**
     * 上传图片。
     *
     * @param file 图片文件
     * @return 图片访问信息
     */
    @PostMapping(value = "/api/assets/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success("图片上传成功", assetService.uploadImage(file));
    }

    /**
     * 读取图片。
     *
     * @param fileName 图片文件名
     * @return 图片内容
     */
    @GetMapping("/api/assets/images/{fileName:.+}")
    public ResponseEntity<byte[]> readImage(@PathVariable String fileName) {
        AssetService.LocalAsset asset = assetService.readImage(fileName);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .body(asset.content());
    }
}
