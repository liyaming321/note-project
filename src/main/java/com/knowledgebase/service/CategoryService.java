package com.knowledgebase.service;

import com.knowledgebase.dto.CategoryRequest;
import com.knowledgebase.dto.CategoryResponse;
import com.knowledgebase.entity.Category;
import com.knowledgebase.exception.BusinessException;
import com.knowledgebase.exception.ResourceNotFoundException;
import com.knowledgebase.repository.CategoryRepository;
import com.knowledgebase.repository.NoteRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分类业务服务。
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final NoteRepository noteRepository;

    /**
     * 创建分类业务服务。
     *
     * @param categoryRepository 分类仓库
     * @param noteRepository 笔记仓库
     */
    public CategoryService(CategoryRepository categoryRepository, NoteRepository noteRepository) {
        this.categoryRepository = categoryRepository;
        this.noteRepository = noteRepository;
    }

    /**
     * 查询分类树。
     *
     * @return 分类树列表
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findTree() {
        List<Category> categories = categoryRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Category::getName))
                .toList();
        Map<Long, List<Category>> childrenByParentId = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        return categories.stream()
                .filter(category -> category.getParentId() == null)
                .map(category -> toTreeNode(category, childrenByParentId))
                .toList();
    }

    /**
     * 创建分类。
     *
     * @param request 分类请求
     * @return 分类响应
     */
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String normalizedName = normalizeName(request.name());
        if (categoryRepository.existsByName(normalizedName)) {
            throw new BusinessException("分类已存在：" + normalizedName);
        }
        validateParent(request.parentId());
        Category category = categoryRepository.save(new Category(normalizedName, request.parentId()));
        return CategoryResponse.from(category, List.of());
    }

    /**
     * 更新分类。
     *
     * @param id 分类ID
     * @param request 分类请求
     * @return 分类响应
     */
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String normalizedName = normalizeName(request.name());
        if (categoryRepository.existsByNameAndIdNot(normalizedName, id)) {
            throw new BusinessException("分类已存在：" + normalizedName);
        }
        validateParentForUpdate(id, request.parentId());
        category.update(normalizedName, request.parentId());
        return CategoryResponse.from(category, List.of());
    }

    /**
     * 删除分类。
     *
     * @param id 分类ID
     */
    @Transactional
    public void delete(Long id) {
        Category category = findCategory(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException("分类下存在子分类，不能删除");
        }
        if (noteRepository.existsByCategoryId(id)) {
            throw new BusinessException("分类下存在笔记，不能删除");
        }
        categoryRepository.delete(category);
    }

    /**
     * 将分类实体转换为树节点。
     *
     * @param category 分类实体
     * @param childrenByParentId 子分类映射
     * @return 分类树节点
     */
    private CategoryResponse toTreeNode(Category category, Map<Long, List<Category>> childrenByParentId) {
        List<CategoryResponse> children = childrenByParentId.getOrDefault(category.getId(), new ArrayList<>())
                .stream()
                .map(child -> toTreeNode(child, childrenByParentId))
                .toList();
        return CategoryResponse.from(category, children);
    }

    /**
     * 校验父分类是否存在。
     *
     * @param parentId 父分类ID
     */
    private void validateParent(Long parentId) {
        if (parentId != null && categoryRepository.findById(parentId).isEmpty()) {
            throw new ResourceNotFoundException("父分类不存在：" + parentId);
        }
    }

    /**
     * 校验更新分类时的父分类。
     *
     * @param categoryId 当前分类ID
     * @param parentId 父分类ID
     */
    private void validateParentForUpdate(Long categoryId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (Objects.equals(categoryId, parentId)) {
            throw new BusinessException("分类不能将自身设为父级");
        }
        validateParent(parentId);
        if (isDescendant(parentId, categoryId)) {
            throw new BusinessException("分类不能移动到自己的子分类下");
        }
    }

    /**
     * 判断候选父分类是否为当前分类的后代。
     *
     * @param candidateParentId 候选父分类ID
     * @param categoryId 当前分类ID
     * @return 是否为后代
     */
    private boolean isDescendant(Long candidateParentId, Long categoryId) {
        Long currentParentId = candidateParentId;
        while (currentParentId != null) {
            if (Objects.equals(currentParentId, categoryId)) {
                return true;
            }
            currentParentId = categoryRepository.findById(currentParentId)
                    .map(Category::getParentId)
                    .orElse(null);
        }
        return false;
    }

    /**
     * 查询分类实体。
     *
     * @param id 分类ID
     * @return 分类实体
     */
    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("分类不存在：" + id));
    }

    /**
     * 标准化分类名称。
     *
     * @param name 原始名称
     * @return 标准名称
     */
    private String normalizeName(String name) {
        return Objects.requireNonNullElse(name, "").trim();
    }
}
