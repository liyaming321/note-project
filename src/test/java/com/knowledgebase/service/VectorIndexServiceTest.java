package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.entity.Note;
import com.knowledgebase.entity.NoteType;
import com.knowledgebase.repository.NoteRepository;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 向量索引服务测试。
 */
class VectorIndexServiceTest {

    @TempDir
    private Path tempDirectory;

    /**
     * 验证本地命令行 Embedding 可批量重建 Lucene 向量索引。
     *
     * @throws Exception 测试文件创建异常
     */
    @Test
    void shouldRebuildVectorIndexWithLocalCliEmbedding() throws Exception {
        NoteRepository noteRepository = org.mockito.Mockito.mock(NoteRepository.class);
        org.mockito.Mockito.when(noteRepository.findByDeletedFalseAndArchivedFalse())
                .thenReturn(List.of(createNote(1L, "第一篇"), createNote(2L, "第二篇")));

        Path executablePath = createFakeEmbeddingExecutable();
        Path modelPath = tempDirectory.resolve("model.gguf");
        java.nio.file.Files.writeString(modelPath, "fake-model");

        KnowledgeBaseProperties properties = createProperties(executablePath, modelPath);
        LocalCliEmbeddingProvider embeddingProvider = new LocalCliEmbeddingProvider(
                properties,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
        VectorIndexService vectorIndexService = new VectorIndexService(noteRepository, embeddingProvider, properties);

        com.knowledgebase.dto.AdminVectorReindexResponse response = vectorIndexService.rebuild();
        com.knowledgebase.dto.AdminVectorIndexInfoResponse info = vectorIndexService.info();

        Assertions.assertThat(response.indexedCount()).isEqualTo(2);
        Assertions.assertThat(response.dimension()).isEqualTo(8);
        Assertions.assertThat(info.available()).isTrue();
        Assertions.assertThat(info.indexedCount()).isEqualTo(2);
        Assertions.assertThat(info.dimension()).isEqualTo(8);
    }

    /**
     * 验证批量命令返回数量异常时会自动降级为逐条生成。
     *
     * @throws Exception 测试文件创建异常
     */
    @Test
    void shouldFallbackToSingleEmbeddingWhenBatchResultCountMismatch() throws Exception {
        NoteRepository noteRepository = org.mockito.Mockito.mock(NoteRepository.class);
        org.mockito.Mockito.when(noteRepository.findByDeletedFalseAndArchivedFalse())
                .thenReturn(List.of(
                        createNote(1L, "第一篇", "第一行\n第二行"),
                        createNote(2L, "第二篇", "第三行\n第四行")
                ));

        Path executablePath = createBatchMismatchEmbeddingExecutable();
        Path modelPath = tempDirectory.resolve("mismatch-model.gguf");
        java.nio.file.Files.writeString(modelPath, "fake-model");

        KnowledgeBaseProperties properties = createProperties(executablePath, modelPath);
        LocalCliEmbeddingProvider embeddingProvider = new LocalCliEmbeddingProvider(
                properties,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );
        VectorIndexService vectorIndexService = new VectorIndexService(noteRepository, embeddingProvider, properties);

        com.knowledgebase.dto.AdminVectorReindexResponse response = vectorIndexService.rebuild();
        com.knowledgebase.dto.AdminVectorIndexInfoResponse info = vectorIndexService.info();

        Assertions.assertThat(response.indexedCount()).isEqualTo(2);
        Assertions.assertThat(info.indexedCount()).isEqualTo(2);
        Assertions.assertThat(info.dimension()).isEqualTo(8);
    }

    /**
     * 创建测试笔记。
     *
     * @param id 笔记ID
     * @param title 标题
     * @return 笔记实体
     */
    private Note createNote(Long id, String title) {
        return createNote(id, title, "正文内容");
    }

    /**
     * 创建测试笔记。
     *
     * @param id 笔记ID
     * @param title 标题
     * @param contentText 纯文本内容
     * @return 笔记实体
     */
    private Note createNote(Long id, String title, String contentText) {
        Note note = new Note(title, contentText, contentText, NoteType.MARKDOWN, "", null, new LinkedHashSet<>());
        setNoteId(note, id);
        return note;
    }

    /**
     * 为测试笔记设置 ID。
     *
     * @param note 笔记实体
     * @param id 笔记ID
     */
    private void setNoteId(Note note, Long id) {
        try {
            Field idField = Note.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(note, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("测试笔记 ID 设置失败", ex);
        }
    }

    /**
     * 创建模拟 llama-embedding 的可执行脚本。
     *
     * @return 脚本路径
     * @throws Exception 文件创建异常
     */
    private Path createFakeEmbeddingExecutable() throws Exception {
        Path executablePath = tempDirectory.resolve("fake-llama-embedding.sh");
        java.nio.file.Files.writeString(executablePath, """
                #!/usr/bin/env bash
                echo 'load_backend: loaded CPU backend' >&2
                printf '[[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8],[0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9]]'
                """);
        java.nio.file.Files.setPosixFilePermissions(
                executablePath,
                java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
                )
        );
        return executablePath;
    }

    /**
     * 创建批量返回数量异常的模拟 Embedding 脚本。
     *
     * @return 脚本路径
     * @throws Exception 文件创建异常
     */
    private Path createBatchMismatchEmbeddingExecutable() throws Exception {
        Path executablePath = tempDirectory.resolve("fake-llama-embedding-mismatch.sh");
        java.nio.file.Files.writeString(executablePath, """
                #!/usr/bin/env bash
                prompt=""
                previous=""
                separator=""
                for argument in "$@"; do
                  if [ "$previous" = "-p" ]; then
                    prompt="$argument"
                  fi
                  if [ "$previous" = "--embd-separator" ]; then
                    separator="$argument"
                  fi
                  previous="$argument"
                done
                if [ -z "$separator" ]; then
                  echo 'missing separator' >&2
                  exit 3
                fi
                if [[ "$prompt" == *"<|knowledge-base-embedding-separator|>"* ]]; then
                  printf '[[0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8]]'
                else
                  printf '[[0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9]]'
                fi
                """);
        java.nio.file.Files.setPosixFilePermissions(
                executablePath,
                java.util.Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
                )
        );
        return executablePath;
    }

    /**
     * 创建向量索引测试配置。
     *
     * @param executablePath 可执行文件路径
     * @param modelPath 模型路径
     * @return 知识库配置
     */
    private KnowledgeBaseProperties createProperties(Path executablePath, Path modelPath) {
        KnowledgeBaseProperties properties = new KnowledgeBaseProperties();
        properties.setVectorIndexPath(tempDirectory.resolve("vector-index").toString());
        properties.getEmbedding().getLocalCli().setExecutablePath(executablePath.toString());
        properties.getEmbedding().getLocalCli().setModelPath(modelPath.toString());
        properties.getEmbedding().getLocalCli().setBatchSize(2);
        properties.getEmbedding().getLocalCli().setTimeoutSeconds(5);
        return properties;
    }
}
