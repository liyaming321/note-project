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
          <h3>标签</h3>
          <div class="sidebar-tag-cloud">
            <button
              v-for="tag in tags"
              :key="tag.id"
              class="workspace-chip"
              type="button"
              @click="goTagFilter(tag.name)"
            >
              {{ tag.name }}
            </button>
            <a-empty v-if="tags.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无标签" />
          </div>
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

          <div v-else-if="activePanel === 'backup'" class="settings-section-panel">
            <div class="settings-section-heading">
              <span>备份恢复</span>
              <h2>数据备份</h2>
              <p>备份文件会包含数据库、索引和图片目录。</p>
            </div>
            <div class="settings-action-card">
              <div>
                <strong>下载完整备份</strong>
                <span>生成当前知识库的 ZIP 备份文件</span>
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
  ArrowLeftOutlined,
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
  StarOutlined
} from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import {
  cleanupVectorIndex,
  createCategory,
  exportBackup,
  fetchCategories,
  fetchEmbeddingProvider,
  fetchIndexHealth,
  fetchTags,
  fetchVectorIndexInfo,
  fetchWorkspaceInfo,
  rebuildSearchIndex,
  rebuildVectorIndex
} from '@/api/knowledgeBase'
import type { AdminIndexHealth, AdminVectorIndexInfo, AdminWorkspaceInfo, Category, EmbeddingProviderInfo, Tag } from '@/types/api'

const router = useRouter()
type CategoryTreeNode = {
  title: string
  value: number
  key: number
  children: CategoryTreeNode[]
}
type WorkspaceView = 'all' | 'recent' | 'favorite' | 'archived' | 'trash'

const loading = ref(false)
const backingUp = ref(false)
const reindexing = ref(false)
const vectorReindexing = ref(false)
const healthChecking = ref(false)
const vectorCleaning = ref(false)
const savingCategory = ref(false)
const categoryManagerVisible = ref(false)
const workspaceInfo = ref<AdminWorkspaceInfo>()
const vectorIndexInfo = ref<AdminVectorIndexInfo>()
const indexHealth = ref<AdminIndexHealth>()
const embeddingProvider = ref<EmbeddingProviderInfo>()
const vectorCleanupMessage = ref('')
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const activePanel = ref<'overview' | 'index' | 'vector' | 'backup' | 'help'>('overview')
const categoryForm = reactive<{
  name: string
  parentId?: number
}>({
  name: ''
})
const categoryTreeData = computed(() => toTreeData(categories.value))
const flatCategories = computed(() => flattenCategories(categories.value))

onMounted(async () => {
  await Promise.all([loadWorkspaceInfo(), loadCategories(), loadTags()])
})

async function loadWorkspaceInfo() {
  loading.value = true
  try {
    const [workspace, provider, vectorIndex] = await Promise.all([
      fetchWorkspaceInfo(),
      fetchEmbeddingProvider(),
      fetchVectorIndexInfo()
    ])
    workspaceInfo.value = workspace
    embeddingProvider.value = provider
    vectorIndexInfo.value = vectorIndex
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
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    backingUp.value = false
  }
}

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadTags() {
  tags.value = await fetchTags()
}

function goWorkspaceView(view: WorkspaceView) {
  router.push({ path: '/', query: view === 'all' ? {} : { view } })
}

function goCategoryFilter(categoryId: number) {
  router.push({ path: '/', query: { mode: 'search', category: String(categoryId) } })
}

function goTagFilter(tagName: string) {
  router.push({ path: '/', query: { mode: 'search', tag: tagName } })
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
