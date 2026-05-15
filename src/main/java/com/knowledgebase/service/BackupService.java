package com.knowledgebase.service;

import com.knowledgebase.config.KnowledgeBaseProperties;
import com.knowledgebase.exception.BusinessException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/**
 * 知识库数据备份与恢复服务。
 */
@Service
public class BackupService {

    private static final DateTimeFormatter BACKUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> BACKUP_ROOTS = List.of("data", "index", "vector-index", "images");

    private final Path dataPath;
    private final Path indexPath;
    private final Path vectorIndexPath;
    private final Path imagesPath;

    /**
     * 创建备份服务。
     *
     * @param properties 知识库配置
     */
    public BackupService(KnowledgeBaseProperties properties) {
        this.dataPath = resolveDataDirectory(properties.getDataPath());
        this.indexPath = Paths.get(properties.getIndexPath()).toAbsolutePath().normalize();
        this.vectorIndexPath = Paths.get(properties.getVectorIndexPath()).toAbsolutePath().normalize();
        this.imagesPath = Paths.get(properties.getImagesPath()).toAbsolutePath().normalize();
    }

    /**
     * 生成知识库完整备份 ZIP。
     *
     * @return 备份文件
     */
    public ExportedBackup createBackup() {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            addDirectory(zipOutputStream, dataPath, "data");
            addDirectory(zipOutputStream, indexPath, "index");
            addDirectory(zipOutputStream, vectorIndexPath, "vector-index");
            addDirectory(zipOutputStream, imagesPath, "images");
            zipOutputStream.finish();
            return new ExportedBackup(
                    "knowledge-base-backup-" + BACKUP_TIME_FORMATTER.format(LocalDateTime.now()) + ".zip",
                    outputStream.toByteArray()
            );
        } catch (IOException ex) {
            throw new BusinessException("创建备份失败：" + ex.getMessage());
        }
    }

    /**
     * 恢复备份 ZIP 到当前配置的数据目录。
     *
     * @param backupPath 备份 ZIP 路径
     */
    public void restoreFromBackup(Path backupPath) {
        Path normalizedBackupPath = backupPath.toAbsolutePath().normalize();
        if (Files.notExists(normalizedBackupPath) || !Files.isRegularFile(normalizedBackupPath)) {
            throw new BusinessException("备份文件不存在：" + normalizedBackupPath);
        }
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("people-wiki-restore-");
            unzipBackup(normalizedBackupPath, tempDirectory);
            replaceDirectory(tempDirectory.resolve("data"), dataPath);
            replaceDirectory(tempDirectory.resolve("index"), indexPath);
            replaceDirectory(tempDirectory.resolve("vector-index"), vectorIndexPath);
            replaceDirectory(tempDirectory.resolve("images"), imagesPath);
        } catch (IOException ex) {
            throw new BusinessException("恢复备份失败：" + ex.getMessage());
        } finally {
            if (tempDirectory != null) {
                deleteQuietly(tempDirectory);
            }
        }
    }

    /**
     * 将目录写入 ZIP。
     *
     * @param zipOutputStream ZIP 输出流
     * @param sourceDirectory 源目录
     * @param rootName ZIP 根目录名
     * @throws IOException 文件写入异常
     */
    private void addDirectory(ZipOutputStream zipOutputStream, Path sourceDirectory, String rootName) throws IOException {
        if (Files.notExists(sourceDirectory)) {
            zipOutputStream.putNextEntry(new ZipEntry(rootName + "/"));
            zipOutputStream.closeEntry();
            return;
        }
        Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                String entryName = toZipEntryName(sourceDirectory, directory, rootName);
                if (!entryName.isEmpty()) {
                    zipOutputStream.putNextEntry(new ZipEntry(entryName + "/"));
                    zipOutputStream.closeEntry();
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (shouldSkipRuntimeFile(file)) {
                    return FileVisitResult.CONTINUE;
                }
                String entryName = toZipEntryName(sourceDirectory, file, rootName);
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zipOutputStream);
                zipOutputStream.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 解压备份 ZIP 到临时目录。
     *
     * @param backupPath 备份 ZIP
     * @param targetDirectory 临时目录
     * @throws IOException 解压异常
     */
    private void unzipBackup(Path backupPath, Path targetDirectory) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(backupPath), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                validateBackupEntry(entry);
                Path targetPath = targetDirectory.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(targetDirectory)) {
                    throw new BusinessException("备份文件包含非法路径：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }
    }

    /**
     * 校验备份条目是否属于允许目录。
     *
     * @param entry ZIP 条目
     */
    private void validateBackupEntry(ZipEntry entry) {
        String entryName = entry.getName().replace("\\", "/");
        boolean allowed = BACKUP_ROOTS.stream().anyMatch(root -> entryName.equals(root)
                || entryName.startsWith(root + "/"));
        if (!allowed || entryName.contains("../")) {
            throw new BusinessException("备份文件包含非法条目：" + entry.getName());
        }
    }

    /**
     * 替换目标目录。
     *
     * @param sourceDirectory 源目录
     * @param targetDirectory 目标目录
     * @throws IOException 文件替换异常
     */
    private void replaceDirectory(Path sourceDirectory, Path targetDirectory) throws IOException {
        deleteQuietly(targetDirectory);
        if (Files.notExists(sourceDirectory)) {
            Files.createDirectories(targetDirectory);
            return;
        }
        Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(targetDirectory.resolve(sourceDirectory.relativize(directory)).normalize());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, targetDirectory.resolve(sourceDirectory.relativize(file)).normalize(),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 删除目录或文件，忽略不存在的路径。
     *
     * @param path 路径
     */
    private void deleteQuietly(Path path) {
        if (path == null || Files.notExists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException ex) throws IOException {
                    if (ex != null) {
                        throw ex;
                    }
                    try {
                        Files.deleteIfExists(directory);
                    } catch (DirectoryNotEmptyException ignored) {
                        // 并发运行时文件可能仍被占用，保留该目录不影响后续启动恢复。
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // 清理临时目录失败不应覆盖主流程异常。
        }
    }

    /**
     * 构建 ZIP 条目名称。
     *
     * @param sourceDirectory 源目录
     * @param path 当前路径
     * @param rootName 根目录名
     * @return ZIP 条目名称
     */
    private String toZipEntryName(Path sourceDirectory, Path path, String rootName) {
        Path relativePath = sourceDirectory.relativize(path);
        if (relativePath.toString().isBlank()) {
            return rootName;
        }
        return rootName + "/" + relativePath.toString().replace("\\", "/");
    }

    /**
     * 判断是否跳过运行时锁文件。
     *
     * @param file 文件
     * @return 是否跳过
     */
    private boolean shouldSkipRuntimeFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".lock.db") || "write.lock".equals(fileName);
    }

    /**
     * 推导 H2 数据文件所在目录。
     *
     * @return 数据目录
     */
    private Path resolveDataDirectory(String configuredDataPath) {
        String dataPathValue = configuredDataPath;
        if (dataPathValue == null || dataPathValue.isBlank()) {
            dataPathValue = Paths.get(System.getProperty("user.home"), ".knowledge-base", "data", "knowledge-base").toString();
        }
        Path dataFilePath = Paths.get(dataPathValue).toAbsolutePath().normalize();
        return dataFilePath.getParent();
    }

    /**
     * 导出的备份文件。
     *
     * @param fileName 文件名
     * @param content 文件内容
     */
    public record ExportedBackup(String fileName, byte[] content) {
    }
}
