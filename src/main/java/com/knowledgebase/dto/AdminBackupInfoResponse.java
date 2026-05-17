package com.knowledgebase.dto;

/**
 * 备份健康信息响应。
 *
 * @param lastBackupFileName 最近备份文件名
 * @param lastBackupSize 最近备份大小
 * @param lastBackupCreatedAt 最近备份创建时间
 * @param lastBackupChecksum 最近备份 SHA-256 校验值
 * @param dataDirectoryReady 数据目录是否可用
 * @param databaseFilesPresent 数据库文件是否存在
 * @param indexDirectoryReady 全文索引目录是否可用
 * @param vectorIndexDirectoryReady 向量索引目录是否可用
 * @param imagesDirectoryReady 图片目录是否可用
 * @param healthy 是否健康
 * @param message 状态说明
 */
public record AdminBackupInfoResponse(
        String lastBackupFileName,
        long lastBackupSize,
        String lastBackupCreatedAt,
        String lastBackupChecksum,
        boolean dataDirectoryReady,
        boolean databaseFilesPresent,
        boolean indexDirectoryReady,
        boolean vectorIndexDirectoryReady,
        boolean imagesDirectoryReady,
        boolean healthy,
        String message
) {
}
