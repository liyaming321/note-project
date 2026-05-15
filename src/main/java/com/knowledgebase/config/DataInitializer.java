package com.knowledgebase.config;

import com.knowledgebase.entity.Category;
import com.knowledgebase.repository.CategoryRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基础数据初始化器。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    /**
     * 创建基础数据初始化器。
     *
     * @param categoryRepository 分类仓库
     */
    public DataInitializer(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 应用启动时初始化默认分类。
     *
     * @param args 命令行参数
     */
    @Override
    @Transactional
    public void run(String... args) {
        List<String> defaultCategories = List.of("默认分类", "代码片段", "学习笔记");
        for (String categoryName : defaultCategories) {
            if (!categoryRepository.existsByName(categoryName)) {
                categoryRepository.save(new Category(categoryName, null));
            }
        }
    }
}
