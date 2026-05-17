<template>
  <div class="knowledge-workspace settings-workspace-root">
    <aside class="workspace-sidenav">
      <div class="workspace-brand">
        <div class="brand-mark">
          <ClusterOutlined />
        </div>
        <div>
          <h2>工作空间</h2>
          <p>个人笔记知识库</p>
        </div>
      </div>

      <nav class="workspace-nav">
        <button class="nav-item" type="button" @click="goWorkspaceView('all')">
          <FileTextOutlined />
          <span>所有笔记</span>
        </button>
        <button class="nav-item" type="button" @click="goWorkspaceView('recent')">
          <ClockCircleOutlined />
          <span>最近</span>
        </button>
        <button class="nav-item" type="button" @click="goWorkspaceView('favorite')">
          <StarOutlined />
          <span>收藏</span>
        </button>
        <button class="nav-item" type="button" @click="goWorkspaceView('archived')">
          <InboxOutlined />
          <span>归档</span>
        </button>

        <div class="sidenav-section">
          <h3>分类</h3>
          <div v-if="categories.length > 0" class="category-links">
            <button
              v-for="category in flatCategories"
              :key="category.id"
              class="text-link-row"
              type="button"
              @click="goCategoryFilter(category.id)"
            >
              {{ category.name }}
            </button>
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无分类" />
        </div>

        <div class="sidenav-section">
          <div class="sidenav-section-title">
            <h3>标签</h3>
            <button v-if="tags.length > 0" type="button" @click="activePanel = 'tags'">管理</button>
          </div>
          <div class="sidebar-tag-cloud">
            <button
              v-for="tag in visibleSidebarTags"
              :key="tag.id"
              class="workspace-chip"
              type="button"
              @click="goTagFilter(tag.name)"
            >
              {{ tag.name }}
            </button>
            <a-empty v-if="tags.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无标签" />
          </div>
          <button
            v-if="hiddenSidebarTagCount > 0"
            class="sidenav-more-button"
            type="button"
            @click="activePanel = 'tags'"
          >
            还有 {{ hiddenSidebarTagCount }} 个标签
          </button>
        </div>
      </nav>

      <div class="sidenav-footer">
        <button class="new-collection-button" type="button" @click="openCategoryManager">
          <PlusOutlined />
          <span>新建分类</span>
        </button>
      </div>
    </aside>

    <main class="workspace-main-shell">
      <header class="workspace-topbar">
        <div class="topbar-left">
          <span class="topbar-helper">设置与维护</span>
        </div>
        <div class="topbar-actions">
          <a-tooltip title="回收站">
            <button class="icon-circle-button" type="button" @click="goWorkspaceView('trash')">
              <DeleteOutlined />
            </button>
          </a-tooltip>
          <a-tooltip title="设置与维护">
            <button class="icon-circle-button active" type="button">
              <SettingOutlined />
            </button>
          </a-tooltip>
          <a-button size="large" @click="goWorkspaceView('all')">
            <template #icon><ArrowLeftOutlined /></template>
            返回工作台
          </a-button>
        </div>
      </header>

      <div class="workspace-canvas settings-workspace-canvas">
        <div class="detail-page workspace-settings-page">
          <section class="detail-hero settings-hero">
            <div class="detail-hero-left">
              <div>
                <p class="hero-eyebrow">设置与维护</p>
                <h1>工作区维护</h1>
                <p class="hero-description">通过左侧功能列表进入对应维护区，管理本地路径、索引、备份和帮助说明。</p>
              </div>
            </div>
          </section>

    <section class="settings-page-layout">
      <aside class="settings-function-list">
        <button
          :class="['settings-function-item', { active: activePanel === 'overview' }]"
          type="button"
          @click="activePanel = 'overview'"
        >
          <SettingOutlined />
          <span>
            <strong>工作区概览</strong>
            <small>路径与版本</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'index' }]"
          type="button"
          @click="activePanel = 'index'"
        >
          <SearchOutlined />
          <span>
            <strong>索引维护</strong>
            <small>重建全文索引</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'vector' }]"
          type="button"
          @click="activePanel = 'vector'"
        >
          <ClusterOutlined />
          <span>
            <strong>向量索引</strong>
            <small>本地 Embedding</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'tuning' }]"
          type="button"
          @click="activePanel = 'tuning'"
        >
          <BarChartOutlined />
          <span>
            <strong>搜索调优</strong>
            <small>权重与反馈</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'config' }]"
          type="button"
          @click="activePanel = 'config'"
        >
          <ApiOutlined />
          <span>
            <strong>配置中心</strong>
            <small>模型与路径检查</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'tags' }]"
          type="button"
          @click="activePanel = 'tags'"
        >
          <TagsOutlined />
          <span>
            <strong>标签管理</strong>
            <small>删除与筛选</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'noteKinds' }]"
          type="button"
          @click="activePanel = 'noteKinds'"
        >
          <AppstoreOutlined />
          <span>
            <strong>用途管理</strong>
            <small>个人用途</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'organize' }]"
          type="button"
          @click="activePanel = 'organize'"
        >
          <InboxOutlined />
          <span>
            <strong>待整理</strong>
            <small>元数据建议</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'backup' }]"
          type="button"
          @click="activePanel = 'backup'"
        >
          <DownloadOutlined />
          <span>
            <strong>备份恢复</strong>
            <small>下载与恢复说明</small>
          </span>
        </button>
        <button
          :class="['settings-function-item', { active: activePanel === 'help' }]"
          type="button"
          @click="activePanel = 'help'"
        >
          <QuestionCircleOutlined />
          <span>
            <strong>帮助说明</strong>
            <small>快捷键与状态</small>
          </span>
        </button>
      </aside>

      <a-spin :spinning="loading" class="detail-main-card settings-page-card">
        <template v-if="workspaceInfo">
          <div v-if="activePanel === 'overview'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>工作区概览</span>
              <h2>本地运行信息</h2>
            </div>
            <div class="settings-grid">
              <div>
                <span>数据目录</span>
                <strong>{{ workspaceInfo.dataPath }}</strong>
              </div>
              <div>
                <span>索引目录</span>
                <strong>{{ workspaceInfo.indexPath }}</strong>
              </div>
              <div>
                <span>向量索引目录</span>
                <strong>{{ workspaceInfo.vectorIndexPath }}</strong>
              </div>
              <div>
                <span>图片目录</span>
                <strong>{{ workspaceInfo.imagesPath }}</strong>
              </div>
              <div>
                <span>历史版本保留数</span>
                <strong>{{ workspaceInfo.historyMaxVersions }}</strong>
              </div>
              <div>
                <span>应用版本</span>
                <strong>{{ workspaceInfo.version }}</strong>
              </div>
            </div>
          </div>

          <div v-else-if="activePanel === 'index'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>索引维护</span>
              <h2>全文索引</h2>
              <p>当搜索结果和笔记内容不一致时，可以手动重建索引。</p>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>重建 Lucene 索引</strong>
                <span>{{ workspaceInfo.indexPath }}</span>
              </div>
              <a-button type="primary" :loading="reindexing" @click="rebuildIndex">重建索引</a-button>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>检查索引健康状态</strong>
                <span>{{ indexHealth?.message || '对比数据库有效笔记、全文索引和向量索引数量' }}</span>
              </div>
              <a-button :loading="healthChecking" @click="checkIndexHealth">检查健康状态</a-button>
            </div>
            <div v-if="indexHealth" class="settings-grid health-grid">
              <div>
                <span>数据库有效笔记</span>
                <strong>{{ indexHealth.databaseActiveCount }}</strong>
              </div>
              <div>
                <span>全文索引数量</span>
                <strong>{{ indexHealth.searchIndexedCount }}</strong>
              </div>
              <div>
                <span>向量索引数量</span>
                <strong>{{ indexHealth.vectorIndexedCount }}</strong>
              </div>
              <div>
                <span>健康状态</span>
                <strong>{{ indexHealth.searchHealthy && indexHealth.vectorHealthy ? '健康' : '需维护' }}</strong>
              </div>
            </div>
          </div>

          <div v-else-if="activePanel === 'vector'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>向量索引</span>
              <h2>本地 Embedding</h2>
              <p>第七阶段使用 llama.cpp 命令行生成向量，模型文件由用户手动下载并通过环境变量配置。</p>
            </div>
            <div class="settings-grid" v-if="vectorIndexInfo">
              <div>
                <span>供应商</span>
                <strong>{{ vectorIndexInfo.provider }}</strong>
              </div>
              <div>
                <span>配置状态</span>
                <strong>{{ vectorIndexInfo.configured ? '已配置' : '未配置' }}</strong>
              </div>
              <div>
                <span>索引状态</span>
                <strong>{{ vectorIndexInfo.available ? '可用' : '未创建' }}</strong>
              </div>
              <div>
                <span>已索引笔记</span>
                <strong>{{ vectorIndexInfo.indexedCount }}</strong>
              </div>
              <div>
                <span>向量维度</span>
                <strong>{{ vectorIndexInfo.dimension || '待生成' }}</strong>
              </div>
              <div>
                <span>池化 / 归一化</span>
                <strong>{{ vectorIndexInfo.pooling }} / {{ vectorIndexInfo.normalize ? '是' : '否' }}</strong>
              </div>
              <div>
                <span>模型路径</span>
                <strong>{{ vectorIndexInfo.model || '未配置' }}</strong>
              </div>
              <div>
                <span>最近重建</span>
                <strong>{{ vectorIndexInfo.lastRebuiltAt || '暂无' }}</strong>
              </div>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>重建 Lucene 向量索引</strong>
                <span>{{ vectorIndexInfo?.message || embeddingProvider?.message || workspaceInfo.vectorIndexPath }}</span>
              </div>
              <a-button type="primary" :loading="vectorReindexing" :disabled="!vectorIndexInfo?.configured" @click="rebuildVector">
                重建向量索引
              </a-button>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>清理无效向量</strong>
                <span>{{ vectorCleanupMessage || '删除数据库中已不存在或已归档笔记对应的向量文档' }}</span>
              </div>
              <a-button :loading="vectorCleaning" @click="cleanupVectors">清理无效向量</a-button>
            </div>
            <p class="settings-note">
              需要配置 KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_EXECUTABLE 和 KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_MODEL。
            </p>
          </div>

          <div v-else-if="activePanel === 'tuning'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>搜索调优</span>
              <h2>混合搜索权重</h2>
              <p>调整关键词、语义和轻量业务加权。反馈会保存在本地数据目录，方便之后复盘排序质量。</p>
            </div>
            <div v-if="searchTuning" class="tuning-grid">
              <label v-for="item in tuningFields" :key="item.key" class="tuning-field">
                <span>{{ item.label }}</span>
                <a-input-number
                  v-model:value="searchTuning[item.key]"
                  :min="0"
                  :max="item.max"
                  :step="0.01"
                />
              </label>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>保存搜索权重</strong>
                <span>{{ searchTuning?.configPath || '本地搜索调优配置文件' }}</span>
              </div>
              <a-button type="primary" :loading="savingSearchTuning" @click="saveSearchTuning">保存权重</a-button>
            </div>
            <div v-if="searchFeedbackSummary" class="settings-grid health-grid">
              <div>
                <span>反馈总数</span>
                <strong>{{ searchFeedbackSummary.totalCount }}</strong>
              </div>
              <div>
                <span>有用</span>
                <strong>{{ searchFeedbackSummary.usefulCount }}</strong>
              </div>
              <div>
                <span>不相关</span>
                <strong>{{ searchFeedbackSummary.irrelevantCount }}</strong>
              </div>
              <div>
                <span>最近反馈</span>
                <strong>{{ searchFeedbackSummary.recentItems.length }}</strong>
              </div>
            </div>
          </div>

          <div v-else-if="activePanel === 'config'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>配置中心</span>
              <h2>本地模型与路径检查</h2>
              <p>{{ configurationChecklist?.message || '检查 LLM、Embedding、数据目录和索引目录。' }}</p>
            </div>
            <div v-if="configurationChecklist" class="settings-grid config-grid">
              <div v-for="item in configurationChecklist.items" :key="item.key">
                <span>{{ item.label }}</span>
                <strong>{{ item.status }}</strong>
                <small>{{ item.detail }}</small>
              </div>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>LLM Provider 连接测试</strong>
                <span>{{ llmTestResult?.message || '会发送一条极短测试消息到所选供应商' }}</span>
              </div>
              <a-select v-model:value="llmTestProvider" class="llm-provider-select">
                <a-select-option value="bailian">阿里百炼</a-select-option>
                <a-select-option value="deepseek">DeepSeek</a-select-option>
              </a-select>
              <a-button :loading="testingLlm" @click="runLlmProviderTest">测试连接</a-button>
            </div>
            <p class="settings-note">
              本地 Embedding 向导：准备 llama.cpp 的 embedding 可执行文件和 GGUF 模型后，配置 KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_EXECUTABLE、KNOWLEDGE_BASE_EMBEDDING_LOCAL_CLI_MODEL、维度与超时参数，再回到本页重建向量索引。
            </p>
          </div>

          <div v-else-if="activePanel === 'tags'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>标签管理</span>
              <h2>清理不再使用的标签</h2>
              <p>删除标签会从所有笔记中移除该标签，但不会删除笔记本身。适合把临时、重复或误生成的标签清掉。</p>
            </div>
            <div class="tag-management-toolbar">
              <div>
                <strong>{{ tags.length }} 个标签</strong>
                <span>侧栏只展示前 {{ SIDEBAR_TAG_LIMIT }} 个，完整列表在这里管理。</span>
              </div>
              <a-button @click="loadTags">刷新标签</a-button>
            </div>
            <div class="manager-list compact">
              <div v-for="tag in tags" :key="tag.id" class="manager-row tag-manager-row">
                <button class="workspace-chip" type="button" @click="goTagFilter(tag.name)">
                  #{{ tag.name }}
                </button>
                <div>
                  <a-button type="link" size="small" @click="goTagFilter(tag.name)">筛选</a-button>
                  <a-popconfirm
                    title="删除后会从所有笔记中移除此标签，确认继续？"
                    ok-text="确认删除"
                    cancel-text="取消"
                    @confirm="removeTag(tag)"
                  >
                    <a-button danger type="link" size="small" :loading="deletingTagId === tag.id">删除</a-button>
                  </a-popconfirm>
                </div>
              </div>
              <a-empty v-if="tags.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无标签" />
            </div>
          </div>

          <div v-else-if="activePanel === 'noteKinds'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>用途管理</span>
              <h2>管理个人用途</h2>
              <p>这里管理的是“日记、灵感、项目、资料”这类用途；Markdown / 代码 / 普通文本仍作为内容格式保留在编辑页更多属性里。</p>
            </div>
            <div class="manager-form note-kind-form">
              <a-input v-model:value="noteKindForm.name" placeholder="用途名称，例如 日记 / 灵感 / 读书" />
              <a-input-number v-model:value="noteKindForm.sortOrder" :min="0" placeholder="排序" />
              <a-button type="primary" :loading="savingNoteKind" @click="saveNoteKind">
                {{ editingNoteKindId ? '保存用途' : '新建用途' }}
              </a-button>
              <a-button v-if="editingNoteKindId" @click="resetNoteKindForm">取消编辑</a-button>
            </div>
            <div class="manager-list compact">
              <div v-for="noteKind in noteKinds" :key="noteKind.id" class="manager-row tag-manager-row">
                <span>
                  {{ noteKind.name }}
                  <small v-if="noteKind.builtIn">默认</small>
                </span>
                <div>
                  <a-button type="link" size="small" @click="goNoteKindFilter(noteKind.id)">筛选</a-button>
                  <a-button type="link" size="small" @click="editNoteKind(noteKind)">重命名</a-button>
                  <a-popconfirm
                    title="删除后会从所有笔记中移除此用途，笔记本身不会删除，确认继续？"
                    ok-text="确认删除"
                    cancel-text="取消"
                    @confirm="removeNoteKind(noteKind)"
                  >
                    <a-button danger type="link" size="small" :loading="deletingNoteKindId === noteKind.id">删除</a-button>
                  </a-popconfirm>
                </div>
              </div>
              <a-empty v-if="noteKinds.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无用途" />
            </div>
          </div>

          <div v-else-if="activePanel === 'organize'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>待整理</span>
              <h2>知识整理工作流</h2>
              <p>集中查看链接导入草稿、未分类、无标签、缺摘要和长期未更新的笔记，先给出建议，确认后再保存。</p>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>打开待整理视图</strong>
                <span>在工作台中逐篇确认、编辑或发布。</span>
              </div>
              <a-button type="primary" @click="goWorkspaceView('organize')">进入待整理</a-button>
            </div>
            <div class="organize-candidate-list">
              <article v-for="candidate in organizeCandidates" :key="candidate.note.id" class="organize-candidate-card">
                <div>
                  <strong>{{ candidate.note.title }}</strong>
                  <p>{{ candidate.reasons.join('，') }}</p>
                  <small>建议标签：{{ candidate.suggestedTags.join('、') }} · 建议分类：{{ candidate.suggestedCategory }}</small>
                </div>
                <a-button size="small" @click="router.push(`/notes/${candidate.note.id}`)">查看</a-button>
              </article>
              <a-empty v-if="organizeCandidates.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无待整理候选" />
            </div>
            <p class="settings-note">
              合并与拆分先按产品方案推进：合并前保存两个来源快照，拆分生成新笔记并保留原笔记历史，所有危险操作都需要二次确认与可撤销窗口。
            </p>
          </div>

          <div v-else-if="activePanel === 'backup'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>备份恢复</span>
              <h2>数据备份</h2>
              <p>备份文件会包含数据库、索引和图片目录。</p>
            </div>
            <div v-if="backupInfo" class="settings-grid health-grid">
              <div>
                <span>最近备份</span>
                <strong>{{ backupInfo.lastBackupFileName || '暂无' }}</strong>
              </div>
              <div>
                <span>文件大小</span>
                <strong>{{ formatFileSize(backupInfo.lastBackupSize) }}</strong>
              </div>
              <div>
                <span>校验状态</span>
                <strong>{{ backupInfo.lastBackupChecksum ? '已记录' : '待备份' }}</strong>
              </div>
              <div>
                <span>目录健康</span>
                <strong>{{ backupInfo.healthy ? '健康' : '需检查' }}</strong>
              </div>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>下载完整备份</strong>
                <span>{{ backupInfo?.message || '生成当前知识库的 ZIP 备份文件' }}</span>
              </div>
              <a-button type="primary" :loading="backingUp" @click="downloadBackup">下载备份</a-button>
            </div>
            <p class="settings-note">
              备份恢复可通过启动参数 KNOWLEDGE_BASE_RESTORE_BACKUP_PATH 指定备份 ZIP 文件路径完成，应用启动时会自动尝试恢复。
            </p>
          </div>

          <div v-else class="settings-section-panel">
            <div class="settings-section-heading">
              <span>帮助说明</span>
              <h2>常用操作</h2>
            </div>
            <div class="help-panel">
              <div>
                <h3>快捷键</h3>
                <p>编辑页使用 Ctrl / Command + S 保存，详情页代码块右上角可以复制代码。</p>
              </div>
              <div>
                <h3>状态</h3>
                <p>白色卡片表示已发布，蓝色卡片表示草稿；归档笔记默认不出现在所有笔记中。</p>
              </div>
              <div>
                <h3>回收站</h3>
                <p>回收站展示已删除内容，支持恢复当前页或永久删除单篇笔记。</p>
              </div>
            </div>
          </div>
        </template>
        <a-empty v-else description="暂无维护信息" />
      </a-spin>
    </section>

        </div>
      </div>
    </main>

    <nav class="mobile-bottom-nav">
      <button type="button" @click="goWorkspaceView('all')">
        <FileTextOutlined />
        <span>笔记</span>
      </button>
      <button type="button" @click="goWorkspaceView('favorite')">
        <StarOutlined />
        <span>收藏</span>
      </button>
      <button type="button" @click="goWorkspaceView('trash')">
        <DeleteOutlined />
        <span>回收站</span>
      </button>
      <button class="active" type="button">
        <SettingOutlined />
        <span>设置</span>
      </button>
    </nav>

    <a-modal v-model:open="categoryManagerVisible" title="新建分类" width="560px" :footer="null">
      <div class="manager-panel">
        <div class="manager-form">
          <a-input v-model:value="categoryForm.name" placeholder="分类名称" />
          <a-tree-select
            v-model:value="categoryForm.parentId"
            allow-clear
            tree-default-expand-all
            :tree-data="categoryTreeData"
            placeholder="父级分类，可选"
          />
          <a-button type="primary" :loading="savingCategory" @click="saveCategory">
            保存分类
          </a-button>
          <a-button @click="resetCategoryForm">清空</a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {
  ApiOutlined,
  AppstoreOutlined,
  ArrowLeftOutlined,
  BarChartOutlined,
  ClockCircleOutlined,
  ClusterOutlined,
  DeleteOutlined,
  DownloadOutlined,
  FileTextOutlined,
  InboxOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  SearchOutlined,
  SettingOutlined,
  StarOutlined,
  TagsOutlined
} from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  cleanupVectorIndex,
  createCategory,
  createNoteKind,
  deleteNoteKind,
  deleteTag,
  exportBackup,
  fetchBackupInfo,
  fetchCategories,
  fetchConfigurationChecklist,
  fetchEmbeddingProvider,
  fetchIndexHealth,
  fetchNoteKinds,
  fetchOrganizeCandidates,
  fetchSearchFeedbackSummary,
  fetchSearchTuning,
  fetchTags,
  fetchVectorIndexInfo,
  fetchWorkspaceInfo,
  rebuildSearchIndex,
  rebuildVectorIndex,
  testLlmProvider,
  updateNoteKind,
  updateSearchTuning
} from '@/api/knowledgeBase'
import type {
  AdminBackupInfo,
  AdminConfigurationChecklist,
  AdminIndexHealth,
  AdminVectorIndexInfo,
  AdminWorkspaceInfo,
  Category,
  EmbeddingProviderInfo,
  KnowledgeOrganizeCandidate,
  LlmProviderTestResult,
  NoteKind,
  SearchFeedbackSummary,
  SearchTuningSettings,
  Tag
} from '@/types/api'

const router = useRouter()
const route = useRoute()
type CategoryTreeNode = {
  title: string
  value: number
  key: number
  children: CategoryTreeNode[]
}
type WorkspaceView = 'all' | 'recent' | 'favorite' | 'archived' | 'trash' | 'organize'
type SearchTuningNumberKey = 'keywordWeight' | 'semanticWeight' | 'titleHitBoost' | 'tagHitBoost' | 'pinnedBoost' | 'favoriteBoost' | 'recentSevenDaysBoost' | 'recentThirtyDaysBoost'
const SIDEBAR_TAG_LIMIT = 12

const loading = ref(false)
const backingUp = ref(false)
const reindexing = ref(false)
const vectorReindexing = ref(false)
const healthChecking = ref(false)
const vectorCleaning = ref(false)
const savingSearchTuning = ref(false)
const testingLlm = ref(false)
const savingCategory = ref(false)
const savingNoteKind = ref(false)
const deletingTagId = ref<number>()
const deletingNoteKindId = ref<number>()
const categoryManagerVisible = ref(false)
const workspaceInfo = ref<AdminWorkspaceInfo>()
const vectorIndexInfo = ref<AdminVectorIndexInfo>()
const indexHealth = ref<AdminIndexHealth>()
const embeddingProvider = ref<EmbeddingProviderInfo>()
const searchTuning = ref<SearchTuningSettings>()
const searchFeedbackSummary = ref<SearchFeedbackSummary>()
const backupInfo = ref<AdminBackupInfo>()
const configurationChecklist = ref<AdminConfigurationChecklist>()
const llmTestResult = ref<LlmProviderTestResult>()
const organizeCandidates = ref<KnowledgeOrganizeCandidate[]>([])
const vectorCleanupMessage = ref('')
const llmTestProvider = ref<'bailian' | 'deepseek'>('bailian')
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const noteKinds = ref<NoteKind[]>([])
const editingNoteKindId = ref<number>()
const activePanel = ref<'overview' | 'index' | 'vector' | 'tuning' | 'config' | 'tags' | 'noteKinds' | 'organize' | 'backup' | 'help'>('overview')
const categoryForm = reactive<{
  name: string
  parentId?: number
}>({
  name: ''
})
const noteKindForm = reactive<{
  name: string
  sortOrder?: number
}>({
  name: ''
})
const categoryTreeData = computed(() => toTreeData(categories.value))
const flatCategories = computed(() => flattenCategories(categories.value))
const visibleSidebarTags = computed(() => tags.value.slice(0, SIDEBAR_TAG_LIMIT))
const hiddenSidebarTagCount = computed(() => Math.max(tags.value.length - visibleSidebarTags.value.length, 0))
const tuningFields: Array<{ key: SearchTuningNumberKey; label: string; max: number }> = [
  { key: 'keywordWeight', label: '关键词权重', max: 1 },
  { key: 'semanticWeight', label: '语义权重', max: 1 },
  { key: 'titleHitBoost', label: '标题命中', max: 0.5 },
  { key: 'tagHitBoost', label: '标签命中', max: 0.5 },
  { key: 'pinnedBoost', label: '置顶', max: 0.5 },
  { key: 'favoriteBoost', label: '收藏', max: 0.5 },
  { key: 'recentSevenDaysBoost', label: '近7天更新', max: 0.5 },
  { key: 'recentThirtyDaysBoost', label: '近30天更新', max: 0.5 }
]

onMounted(async () => {
  syncPanelFromRoute()
  await Promise.all([loadWorkspaceInfo(), loadCategories(), loadTags(), loadNoteKinds()])
})

function syncPanelFromRoute() {
  if (route.query.panel === 'tags') {
    activePanel.value = 'tags'
  } else if (route.query.panel === 'noteKinds') {
    activePanel.value = 'noteKinds'
  }
}

async function loadWorkspaceInfo() {
  loading.value = true
  try {
    const [workspace, provider, vectorIndex, tuning, feedback, backup, checklist, organizePage] = await Promise.all([
      fetchWorkspaceInfo(),
      fetchEmbeddingProvider(),
      fetchVectorIndexInfo(),
      fetchSearchTuning(),
      fetchSearchFeedbackSummary(),
      fetchBackupInfo(),
      fetchConfigurationChecklist(),
      fetchOrganizeCandidates(0, 6)
    ])
    workspaceInfo.value = workspace
    embeddingProvider.value = provider
    vectorIndexInfo.value = vectorIndex
    searchTuning.value = tuning
    searchFeedbackSummary.value = feedback
    backupInfo.value = backup
    configurationChecklist.value = checklist
    organizeCandidates.value = organizePage.items
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function rebuildVector() {
  vectorReindexing.value = true
  try {
    const result = await rebuildVectorIndex()
    message.success(`向量索引已重建，共 ${result.indexedCount} 篇笔记，维度 ${result.dimension}`)
    vectorIndexInfo.value = await fetchVectorIndexInfo()
    indexHealth.value = await fetchIndexHealth()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    vectorReindexing.value = false
  }
}

async function cleanupVectors() {
  vectorCleaning.value = true
  try {
    const result = await cleanupVectorIndex()
    vectorCleanupMessage.value = `${result.message}，清理 ${result.removedCount} 条，当前 ${result.indexedCount} 条`
    message.success(vectorCleanupMessage.value)
    vectorIndexInfo.value = await fetchVectorIndexInfo()
    indexHealth.value = await fetchIndexHealth()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    vectorCleaning.value = false
  }
}

async function checkIndexHealth() {
  healthChecking.value = true
  try {
    indexHealth.value = await fetchIndexHealth()
    message.success(indexHealth.value.message)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    healthChecking.value = false
  }
}

async function rebuildIndex() {
  reindexing.value = true
  try {
    const result = await rebuildSearchIndex()
    message.success(`索引已重建，共 ${result.indexedCount} 篇笔记`)
    indexHealth.value = await fetchIndexHealth()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    reindexing.value = false
  }
}

async function downloadBackup() {
  backingUp.value = true
  try {
    await exportBackup()
    backupInfo.value = await fetchBackupInfo()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    backingUp.value = false
  }
}

async function saveSearchTuning() {
  if (!searchTuning.value) {
    return
  }
  savingSearchTuning.value = true
  try {
    searchTuning.value = await updateSearchTuning(searchTuning.value)
    message.success('搜索调优配置已保存')
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    savingSearchTuning.value = false
  }
}

async function runLlmProviderTest() {
  testingLlm.value = true
  try {
    llmTestResult.value = await testLlmProvider(llmTestProvider.value)
    if (llmTestResult.value.success) {
      message.success(llmTestResult.value.message)
    } else {
      message.warning(llmTestResult.value.message)
    }
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    testingLlm.value = false
  }
}

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadTags() {
  tags.value = await fetchTags()
}

async function loadNoteKinds() {
  noteKinds.value = await fetchNoteKinds()
}

async function removeTag(tag: Tag) {
  deletingTagId.value = tag.id
  try {
    await deleteTag(tag.id)
    message.success(`已删除标签：${tag.name}`)
    await loadTags()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    deletingTagId.value = undefined
  }
}

function editNoteKind(noteKind: NoteKind) {
  editingNoteKindId.value = noteKind.id
  noteKindForm.name = noteKind.name
  noteKindForm.sortOrder = noteKind.sortOrder
}

function resetNoteKindForm() {
  editingNoteKindId.value = undefined
  noteKindForm.name = ''
  noteKindForm.sortOrder = undefined
}

async function saveNoteKind() {
  if (!noteKindForm.name.trim()) {
    message.warning('请输入用途名称')
    return
  }
  savingNoteKind.value = true
  try {
    if (editingNoteKindId.value) {
      await updateNoteKind(editingNoteKindId.value, noteKindForm.name.trim(), noteKindForm.sortOrder)
      message.success('用途已更新')
    } else {
      await createNoteKind(noteKindForm.name.trim(), noteKindForm.sortOrder)
      message.success('用途已创建')
    }
    resetNoteKindForm()
    await loadNoteKinds()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    savingNoteKind.value = false
  }
}

async function removeNoteKind(noteKind: NoteKind) {
  deletingNoteKindId.value = noteKind.id
  try {
    await deleteNoteKind(noteKind.id)
    message.success(`已删除用途：${noteKind.name}`)
    if (editingNoteKindId.value === noteKind.id) {
      resetNoteKindForm()
    }
    await loadNoteKinds()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    deletingNoteKindId.value = undefined
  }
}

function goWorkspaceView(view: WorkspaceView) {
  router.push({ path: '/', query: view === 'all' ? {} : { view } })
}

function formatFileSize(size: number) {
  if (!size) {
    return '暂无'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function goCategoryFilter(categoryId: number) {
  router.push({ path: '/', query: { mode: 'search', category: String(categoryId) } })
}

function goTagFilter(tagName: string) {
  router.push({ path: '/', query: { mode: 'search', tag: tagName } })
}

function goNoteKindFilter(noteKindId: number) {
  router.push({ path: '/', query: { noteKindId: String(noteKindId) } })
}

function openCategoryManager() {
  categoryManagerVisible.value = true
  resetCategoryForm()
}

function resetCategoryForm() {
  categoryForm.name = ''
  categoryForm.parentId = undefined
}

async function saveCategory() {
  if (!categoryForm.name.trim()) {
    message.warning('请输入分类名称')
    return
  }
  savingCategory.value = true
  try {
    await createCategory(categoryForm.name.trim(), categoryForm.parentId)
    message.success('分类已创建')
    resetCategoryForm()
    categoryManagerVisible.value = false
    await loadCategories()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    savingCategory.value = false
  }
}

function toTreeData(items: Category[]): CategoryTreeNode[] {
  return items.map(item => ({
    title: item.name,
    value: item.id,
    key: item.id,
    children: toTreeData(item.children ?? [])
  }))
}

function flattenCategories(items: Category[], level = 0): Array<{ id: number; name: string; level: number }> {
  return items.flatMap(item => [
    {
      id: item.id,
      name: `${'　'.repeat(level)}${item.name}`,
      level
    },
    ...flattenCategories(item.children ?? [], level + 1)
  ])
}
</script>
