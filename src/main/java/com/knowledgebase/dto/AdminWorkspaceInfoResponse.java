package com.knowledgebase.dto;

/**
 * 工作区维护信息响应。
 *
 * @param dataPath 数据目录
 * @param indexPath 索引目录
 * @param vectorIndexPath 向量索引目录
 * @param imagesPath 图片目录
 * @param historyMaxVersions 历史版本最大保留数量
 * @param version 应用版本
 */
public record AdminWorkspaceInfoResponse(
        String dataPath,
        String indexPath,
        String vectorIndexPath,
        String imagesPath,
        int historyMaxVersions,
        String version
) {
}
