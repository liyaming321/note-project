package com.knowledgebase.config;

import com.knowledgebase.entity.Category;
import com.knowledgebase.entity.NoteKind;
import com.knowledgebase.repository.CategoryRepository;
import com.knowledgebase.repository.NoteKindRepository;
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
    private final NoteKindRepository noteKindRepository;

    /**
     * 创建基础数据初始化器。
     *
     * @param categoryRepository 分类仓库
     * @param noteKindRepository 笔记用途仓库
     */
    public DataInitializer(CategoryRepository categoryRepository, NoteKindRepository noteKindRepository) {
        this.categoryRepository = categoryRepository;
        this.noteKindRepository = noteKindRepository;
    }

    /**
     * 应用启动时初始化默认分类和默认用途。
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
        if (noteKindRepository.count() == 0) {
            List<String> defaultNoteKinds = List.of("日记", "灵感", "学习", "项目", "资料", "代码片段");
            for (int index = 0; index < defaultNoteKinds.size(); index++) {
                noteKindRepository.save(new NoteKind(defaultNoteKinds.get(index), (long) (index + 1) * 10, true));
            }
        }
    }
}
