package com.knowledgebase.service;

import com.knowledgebase.dto.TagRequest;
import com.knowledgebase.dto.TagResponse;
import com.knowledgebase.entity.Tag;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.repository.TagRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标签业务服务。
 */
@Service
public class TagService {

    private final TagRepository tagRepository;

    /**
     * 创建标签业务服务。
     *
     * @param tagRepository 标签仓库
     */
    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * 查询所有标签。
     *
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    public List<TagResponse> findAll() {
        return tagRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(TagResponse::from)
                .toList();
    }

    /**
     * 创建标签。
     *
     * @param request 标签请求
     * @return 标签响应
     */
    @Transactional
    public TagResponse create(TagRequest request) {
        String normalizedName = normalizeName(request.name());
        if (tagRepository.existsByName(normalizedName)) {
            throw new BusinessException("标签已存在：" + normalizedName);
        }
        Tag tag = tagRepository.save(new Tag(normalizedName));
        return TagResponse.from(tag);
    }

    /**
     * 标准化标签名称。
     *
     * @param name 原始名称
     * @return 标准名称
     */
    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
