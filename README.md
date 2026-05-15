# People Wiki 个人知识库

People Wiki 是一个运行在个人电脑上的本地知识库系统，用来管理 Markdown 笔记、代码片段、浏览器书签和图片资源。系统采用 Spring Boot + Vue 3 构建，数据默认持久化到用户目录下的 H2 文件数据库，并通过 Apache Lucene 提供本地全文检索能力。

项目目标是“开箱即用、离线可用、可备份恢复”：开发阶段可以前后端分离运行，部署阶段可以打成单一可执行 Jar，启动后直接访问浏览器页面。

## 功能概览

- 笔记管理：创建、编辑、详情查看、逻辑删除、恢复、永久删除。
- Markdown 编辑：Vditor 编辑器、Markdown 预览、代码高亮、图片粘贴上传。
- 分类与标签：分类树、标签云、分类管理、标签筛选。
- 草稿与发布：草稿使用蓝色卡片，已发布使用白色卡片，支持发布 / 转草稿。
- 收藏与置顶：列表和详情页均可快速切换收藏、置顶状态。
- 归档与回收站：归档笔记默认不进入全部列表，回收站支持恢复和永久删除。
- 全文检索：基于 Lucene 支持标题、正文、代码、标签、分类和语言筛选。
- LLM 总结：支持阿里百炼和 DeepSeek，为笔记生成摘要、标签和分类建议。
- 版本历史：编辑自动保存历史版本，支持查看与恢复。
- 导入导出：Markdown 导入、书签 HTML 导入、Markdown / ZIP 导出。
- 备份维护：数据、索引、图片目录一键备份，支持启动时恢复备份。

## 页面预览

### 工作台首页

首页采用蓝白色工作台布局，左侧为导航、分类和标签，顶部提供搜索、导入导出、备份和设置入口。筛选区默认只保留分类、标签、语言，更多条件可展开。

![工作台首页](docs/images/workspace-overview.png)

### 笔记卡片

蓝色卡片表示草稿状态，白色卡片表示已发布状态。卡片底部展示更新时间、分类、发布/归档操作。

![笔记卡片](docs/images/note-cards.png)

### 新建笔记

新建笔记页提供标题、类型、语言、发布状态、分类、标签、收藏和置顶配置，下方是 Vditor 正文编辑区，支持 Markdown、代码块、图片粘贴上传和实时预览。

![新建笔记](docs/images/note-editor.png)

### 完整筛选面板

展开后可使用类型、搜索范围、排序、发布状态、更新时间范围、置顶、收藏、已删除等高级筛选。

![完整筛选面板](docs/images/filter-panel.png)

## 技术架构

### 后端

- Java 17
- Spring Boot 3.5.x
- Spring Web
- Spring Data JPA
- Hibernate
- H2 文件数据库
- Apache Lucene 9.12.2
- Bean Validation
- Maven

后端包名为 `com.knowledgebase`，主要分层如下：

```text
controller   REST API 与 SPA 路由转发
service      笔记、搜索、索引、备份、导入导出等业务逻辑
repository   Spring Data JPA 数据访问
entity       Note、Category、Tag、NoteHistory 等实体
dto          请求与响应对象
config       数据目录、索引、初始化、迁移与备份恢复配置
exception    统一异常处理
util         Markdown 文本提取、Lucene 字段定义等工具
```

### 前端

- Vue 3
- Vite
- TypeScript
- Vue Router
- Axios
- Ant Design Vue
- Ant Design Icons Vue
- Vditor
- highlight.js

前端位于 `frontend/`，核心页面包括：

```text
NoteListView.vue    工作台首页、搜索、筛选、列表、归档、回收站
NoteDetailView.vue  笔记详情、Markdown 渲染、历史版本、状态操作
NoteEditView.vue    新建和编辑笔记、Vditor 编辑器、图片上传
SettingsView.vue    设置维护、索引重建、备份恢复说明
```

### 数据与存储

默认数据目录：

```text
~/.knowledge-base/data/knowledge-base.mv.db   H2 数据库文件
~/.knowledge-base/index                       Lucene 索引目录
~/.knowledge-base/vector-index                Lucene 向量索引目录
~/.knowledge-base/images                      图片资源目录
```

可通过环境变量覆盖：

```bash
export KNOWLEDGE_BASE_DATA_PATH=/your/path/data/knowledge-base
export KNOWLEDGE_BASE_INDEX_PATH=/your/path/index
export KNOWLEDGE_BASE_VECTOR_INDEX_PATH=/your/path/vector-index
export KNOWLEDGE_BASE_IMAGES_PATH=/your/path/images
```

### 本地 Embedding 向量索引配置

第七阶段向量化默认使用 `local-cli`，通过本地 llama.cpp `llama-embedding` 命令行工具生成向量。项目不内置也不自动下载模型文件，需要先自行准备 llama.cpp 可执行文件和 GGUF Embedding 模型。

```bash
export KNOWLEDGE_BASE_EMBEDDING_PROVIDER=local-cli
export KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_EXECUTABLE=/your/path/llama-embedding
export KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_MODEL=/your/path/model.gguf
export KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_POOLING=mean
export KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_NORMALIZE=true
export KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_TIMEOUT_SECONDS=120
```

未配置本地模型时，系统会自动降级为现有 Lucene 全文搜索；设置页会展示向量索引配置状态，并提供手动重建向量索引入口。

### LLM 总结配置

LLM 总结接口使用 OpenAI 兼容的 Chat Completions 协议，当前支持阿里百炼和 DeepSeek。默认供应商为阿里百炼，可通过环境变量切换。

```bash
# 默认供应商：bailian 或 deepseek
export KNOWLEDGE_BASE_LLM_PROVIDER=bailian

# 阿里百炼
export BAILIAN_API_KEY=你的百炼APIKey
export BAILIAN_MODEL=qwen-plus
export BAILIAN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

# DeepSeek
export DEEPSEEK_API_KEY=你的DeepSeekAPIKey
export DEEPSEEK_MODEL=deepseek-v4-flash
export DEEPSEEK_BASE_URL=https://api.deepseek.com
```

未配置 API Key 时，前端仍会显示入口，但调用总结会返回配置提示。

## 本地开发

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 20.x 或更高版本
- npm

### 后端运行

在项目根目录执行：

```bash
mvn spring-boot:run
```

启动成功后访问：

```text
http://localhost:8080/
```

API 地址以 `/api` 开头，例如：

```text
http://localhost:8080/api/notes
```

### 前后端分离开发

后端：

```bash
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

然后访问 Vite 输出的地址，通常是：

```text
http://localhost:5173/
```

开发服务会通过 Vite 代理把 `/api` 请求转发到 `http://localhost:8080`。

## 部署方式

### 方式一：直接使用 Spring Boot 运行

如果已经执行过前端构建和资源拷贝，可以直接运行：

```bash
mvn spring-boot:run
```

访问：

```text
http://localhost:8080/
```

如果页面不是最新版本，先执行：

```bash
mvn clean process-resources
mvn spring-boot:run
```

### 方式二：打包为单一 Jar

执行：

```bash
mvn clean package
```

打包完成后运行：

```bash
java -jar target/people-wiki-0.0.1-SNAPSHOT.jar
```

访问：

```text
http://localhost:8080/
```

### 方式三：使用启动脚本

macOS / Linux：

```bash
mvn clean package
./scripts/start.sh
```

Windows：

```bat
mvn clean package
scripts\start.bat
```

脚本会自动设置默认数据、索引和图片目录。如果要自定义目录，可先设置环境变量再启动。

## 常用命令

```bash
# 后端测试
mvn test

# 前端构建
cd frontend && npm run build

# 完整打包
mvn clean package

# 启动应用
mvn spring-boot:run
```

## 备份与恢复

系统提供管理接口和前端按钮下载完整备份，备份内容包含：

- H2 数据库目录
- Lucene 索引目录
- 图片资源目录

也可以通过启动参数指定备份 ZIP 进行恢复：

```bash
export KNOWLEDGE_BASE_RESTORE_BACKUP_PATH=/path/to/backup.zip
java -jar target/people-wiki-0.0.1-SNAPSHOT.jar
```

## 项目文档

- `task.md`：阶段任务清单与完成状态。
- `docs/implementation-log.md`：每次实现和验证记录。
- `docs/sql-changes.sql`：数据库结构变更记录。
- `scripts/start.sh` / `scripts/start.bat`：部署启动脚本。

## 当前状态

第一至第六阶段核心能力已完成，包括基础 CRUD、全文搜索、版本历史、导入导出、备份恢复、草稿发布、归档回收站、设置维护和帮助面板。后续可继续扩展代码片段优化、CLI 工具、相似笔记推荐等增强能力。
