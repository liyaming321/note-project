<template>
  <div class="knowledge-workspace">
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
        <button :class="['nav-item', { active: activeWorkspaceView === 'all' }]" type="button" @click="goWorkspaceView('all')">
          <FileTextOutlined />
          <span>所有笔记</span>
        </button>
        <button :class="['nav-item', { active: activeWorkspaceView === 'recent' }]" type="button" @click="goWorkspaceView('recent')">
          <ClockCircleOutlined />
          <span>最近</span>
        </button>
        <button :class="['nav-item', { active: activeWorkspaceView === 'favorite' }]" type="button" @click="goWorkspaceView('favorite')">
          <StarOutlined />
          <span>收藏</span>
        </button>
        <button :class="['nav-item', { active: activeWorkspaceView === 'archived' }]" type="button" @click="goWorkspaceView('archived')">
          <InboxOutlined />
          <span>归档</span>
        </button>

        <div class="sidenav-section">
          <h3>分类</h3>
          <div v-if="categories.length > 0" class="category-links">
            <button
              v-for="category in flatCategories"
              :key="category.id"
              :class="['text-link-row', { active: note?.category?.id === category.id }]"
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
              :class="['workspace-chip', { active: isCurrentNoteTag(tag.name) }]"
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
          <h1>知识库</h1>
          <span class="topbar-helper">{{ note ? '正在阅读笔记' : '正在加载笔记' }}</span>
        </div>
        <div class="topbar-actions">
          <a-button size="large" @click="goWorkspaceView('all')">
            <template #icon><ArrowLeftOutlined /></template>
            返回列表
          </a-button>
          <a-button v-if="note" type="primary" size="large" @click="router.push(`/notes/${note.id}/edit`)">
            <template #icon><EditOutlined /></template>
            编辑
          </a-button>
          <a-dropdown v-if="note" trigger="click">
            <button class="topbar-text-button more-actions-button" type="button">
              更多
              <DownOutlined />
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="export" @click="exportCurrentNote">
                  <DownloadOutlined />
                  <span>导出笔记</span>
                </a-menu-item>
                <a-menu-item key="pin" @click="togglePinned">
                  <PushpinFilled v-if="note.pinned" />
                  <PushpinOutlined v-else />
                  <span>{{ note.pinned ? '取消置顶' : '置顶' }}</span>
                </a-menu-item>
                <a-menu-item key="favorite" @click="toggleFavorite">
                  <StarFilled v-if="note.favorite" />
                  <StarOutlined v-else />
                  <span>{{ note.favorite ? '取消收藏' : '收藏' }}</span>
                </a-menu-item>
                <a-menu-item key="status" @click="toggleStatus">
                  <span>{{ note.status === 'DRAFT' ? '发布' : '转为草稿' }}</span>
                </a-menu-item>
                <a-menu-item key="archive" @click="toggleArchived">
                  <span>{{ note.archived ? '取消归档' : '归档' }}</span>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item v-if="!note.deleted" key="delete" danger @click="confirmRemoveNote">
                  <DeleteOutlined />
                  <span>删除</span>
                </a-menu-item>
                <a-menu-item v-if="note.deleted" key="restore" @click="restoreCurrentNote">
                  <RollbackOutlined />
                  <span>恢复</span>
                </a-menu-item>
                <a-menu-item v-if="note.deleted" key="permanent-delete" danger @click="confirmPermanentRemoveCurrentNote">
                  <DeleteOutlined />
                  <span>永久删除</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-tooltip title="回收站">
            <button :class="['icon-circle-button', { active: activeWorkspaceView === 'trash' }]" type="button" @click="goWorkspaceView('trash')">
              <DeleteOutlined />
            </button>
          </a-tooltip>
          <a-tooltip title="设置与维护">
            <button class="icon-circle-button" type="button" @click="router.push('/settings')">
              <SettingOutlined />
            </button>
          </a-tooltip>
        </div>
      </header>

      <div class="workspace-canvas detail-workspace-canvas">
        <div class="detail-page workspace-detail-page">
          <section v-if="note" class="detail-hero">
            <div class="detail-hero-left">
              <div>
                <p class="hero-eyebrow">知识笔记</p>
                <h1>{{ note.title }}</h1>
                <div class="note-meta detail-meta">
                  <a-tag class="blue-tag">{{ note.type === 'CODE' ? note.language || '代码' : 'Markdown' }}</a-tag>
                  <a-tag :class="['blue-tag', note.status === 'DRAFT' ? 'draft-tag' : 'subtle']">
                    {{ note.status === 'DRAFT' ? '草稿' : '已发布' }}
                  </a-tag>
                  <a-tag v-if="note.category" class="blue-tag subtle">{{ note.category.name }}</a-tag>
                  <a-tag v-if="note.archived" color="blue">已归档</a-tag>
                  <a-tag v-if="note.deleted" color="red">已删除</a-tag>
                </div>
              </div>
            </div>
          </section>

          <section class="detail-layout">
            <aside class="detail-sidebar" v-if="note">
              <div class="detail-summary-card">
                <div class="summary-item">
                  <span>更新时间</span>
                  <strong>{{ formatTime(note.updatedAt) }}</strong>
                </div>
                <div class="summary-item">
                  <span>创建时间</span>
                  <strong>{{ formatTime(note.createdAt) }}</strong>
                </div>
                <div class="summary-item">
                  <span>内容状态</span>
                  <strong>{{ resolveContentState() }}</strong>
                </div>
                <div v-if="note.summary" class="summary-item">
                  <span>笔记摘要</span>
                  <p>{{ note.summary }}</p>
                </div>
              </div>

              <div class="history-panel">
                <div class="history-panel-heading">
                  <div>
                    <span>历史版本</span>
                    <small>{{ historyItems.length }}</small>
                  </div>
                  <a-button type="text" size="small" :disabled="!selectedHistory" @click="clearHistoryPreview">
                    当前
                  </a-button>
                </div>
                <a-spin :spinning="historyLoading">
                  <div v-if="historyItems.length > 0" class="history-list">
                    <button
                      v-for="history in historyItems"
                      :key="history.version"
                      :class="['history-item', { active: selectedHistory?.version === history.version }]"
                      @click="loadHistoryVersion(history.version)"
                    >
                      <span class="history-version">
                        <HistoryOutlined />
                        V{{ history.version }}
                      </span>
                      <strong>{{ history.title }}</strong>
                      <time>{{ formatTime(history.createdAt) }}</time>
                    </button>
                  </div>
                  <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无历史版本" />
                </a-spin>
              </div>
            </aside>

            <a-spin :spinning="loading" class="detail-main-card">
              <template v-if="note">
                <div v-if="selectedHistory" class="history-preview-banner">
                  <div>
                    <span>正在预览历史版本 V{{ selectedHistory.version }}</span>
                    <strong>{{ selectedHistory.title }}</strong>
                    <small>{{ formatTime(selectedHistory.createdAt) }}</small>
                  </div>
                  <div class="history-preview-actions">
                    <a-button class="ghost-blue-button" @click="clearHistoryPreview">查看当前</a-button>
                    <a-popconfirm title="确认恢复到这个历史版本？" ok-text="确认" cancel-text="取消" @confirm="restoreSelectedHistory">
                      <a-button type="primary" :loading="reverting">
                        <template #icon><RollbackOutlined /></template>
                        恢复此版本
                      </a-button>
                    </a-popconfirm>
                  </div>
                </div>

                <div class="note-tags detail-tags">
                  <template v-if="selectedHistory">
                    <a-tag
                      v-for="tag in selectedHistory.tags"
                      :key="tag"
                      class="blue-tag clickable-tag"
                      @click="goTagFilter(tag)"
                    >
                      {{ tag }}
                    </a-tag>
                    <a-tag v-if="selectedHistory.categoryName" class="blue-tag subtle">
                      {{ selectedHistory.categoryName }}
                    </a-tag>
                  </template>
                  <template v-else>
                    <a-tag
                      v-for="tag in note.tags"
                      :key="tag.id"
                      class="blue-tag clickable-tag"
                      @click="goTagFilter(tag.name)"
                    >
                      {{ tag.name }}
                    </a-tag>
                  </template>
                </div>
                <div ref="previewRef" class="markdown-body"></div>
              </template>
              <a-empty v-else description="笔记不存在" />
            </a-spin>
          </section>
        </div>
      </div>
    </main>

    <nav class="mobile-bottom-nav">
      <button :class="{ active: activeWorkspaceView === 'all' }" type="button" @click="goWorkspaceView('all')">
        <FileTextOutlined />
        <span>笔记</span>
      </button>
      <button :class="{ active: activeWorkspaceView === 'favorite' }" type="button" @click="goWorkspaceView('favorite')">
        <StarOutlined />
        <span>收藏</span>
      </button>
      <button type="button" @click="showTagHint">
        <TagsOutlined />
        <span>标签</span>
      </button>
      <button type="button" @click="router.push('/settings')">
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
  DownOutlined,
  DownloadOutlined,
  EditOutlined,
  FileTextOutlined,
  HistoryOutlined,
  InboxOutlined,
  PlusOutlined,
  PushpinFilled,
  PushpinOutlined,
  RollbackOutlined,
  SettingOutlined,
  StarFilled,
  StarOutlined,
  TagsOutlined
} from '@ant-design/icons-vue'
import { Empty, message, Modal } from 'ant-design-vue'
import Vditor from 'vditor'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  changeArchived,
  changeFavorite,
  changePinned,
  changeStatus,
  createCategory,
  deleteNote,
  exportNoteMarkdownUrl,
  fetchCategories,
  fetchNote,
  fetchNoteHistory,
  fetchNoteHistoryVersion,
  fetchTags,
  permanentlyDeleteNote,
  restoreNote,
  revertNoteToHistory
} from '@/api/knowledgeBase'
import type { Category, NoteDetail, NoteHistoryDetail, NoteHistorySummary, NoteStatus, Tag } from '@/types/api'

const route = useRoute()
const router = useRouter()
type CategoryTreeNode = {
  title: string
  value: number
  key: number
  children: CategoryTreeNode[]
}
type WorkspaceView = 'all' | 'recent' | 'favorite' | 'archived' | 'trash'

const loading = ref(false)
const historyLoading = ref(false)
const reverting = ref(false)
const savingCategory = ref(false)
const categoryManagerVisible = ref(false)
const activeWorkspaceView = ref<WorkspaceView>('all')
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const note = ref<NoteDetail>()
const historyItems = ref<NoteHistorySummary[]>([])
const selectedHistory = ref<NoteHistoryDetail>()
const previewRef = ref<HTMLDivElement>()
const categoryForm = reactive<{
  name: string
  parentId?: number
}>({
  name: ''
})
const categoryTreeData = computed(() => toTreeData(categories.value))
const flatCategories = computed(() => flattenCategories(categories.value))
const previewContent = computed(() => selectedHistory.value?.content ?? note.value?.content ?? '')

onMounted(async () => {
  await Promise.all([loadCategories(), loadTags(), loadNote()])
})
watch(() => route.params.id, () => {
  void loadNote()
})
watch(previewContent, renderMarkdown)

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadTags() {
  tags.value = await fetchTags()
}

async function loadNote() {
  loading.value = true
  selectedHistory.value = undefined
  try {
    note.value = await fetchNote(Number(route.params.id))
    await loadHistoryItems()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadHistoryItems() {
  if (!note.value) {
    return
  }
  historyLoading.value = true
  try {
    historyItems.value = await fetchNoteHistory(note.value.id)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    historyLoading.value = false
  }
}

async function loadHistoryVersion(version: number) {
  if (!note.value) {
    return
  }
  if (selectedHistory.value?.version === version) {
    clearHistoryPreview()
    return
  }
  historyLoading.value = true
  try {
    selectedHistory.value = await fetchNoteHistoryVersion(note.value.id, version)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    historyLoading.value = false
  }
}

function clearHistoryPreview() {
  selectedHistory.value = undefined
}

async function renderMarkdown() {
  await nextTick()
  if (!previewRef.value) {
    return
  }
  if (!previewContent.value) {
    previewRef.value.innerHTML = ''
    return
  }
  await Vditor.preview(previewRef.value, previewContent.value, {
    mode: 'light',
    hljs: {
      enable: true,
      style: 'github'
    }
  })
  addCodeCopyButtons()
}

async function toggleFavorite() {
  if (!note.value) {
    return
  }
  note.value = await changeFavorite(note.value.id, !note.value.favorite)
  message.success(note.value.favorite ? '已收藏' : '已取消收藏')
}

async function togglePinned() {
  if (!note.value) {
    return
  }
  note.value = await changePinned(note.value.id, !note.value.pinned)
  message.success(note.value.pinned ? '已置顶' : '已取消置顶')
}

async function toggleStatus() {
  if (!note.value) {
    return
  }
  const nextStatus: NoteStatus = note.value.status === 'DRAFT' ? 'PUBLISHED' : 'DRAFT'
  note.value = await changeStatus(note.value.id, nextStatus)
  message.success(nextStatus === 'PUBLISHED' ? '已发布' : '已转为草稿')
}

async function toggleArchived() {
  if (!note.value) {
    return
  }
  note.value = await changeArchived(note.value.id, !note.value.archived)
  message.success(note.value.archived ? '已归档' : '已取消归档')
}

function exportCurrentNote() {
  if (!note.value) {
    return
  }
  window.location.href = exportNoteMarkdownUrl(note.value.id)
}

async function removeNote() {
  if (!note.value) {
    return
  }
  await deleteNote(note.value.id)
  message.success('已删除')
  await loadNote()
}

function confirmRemoveNote() {
  Modal.confirm({
    title: '确认删除这篇笔记？',
    okText: '确认',
    cancelText: '取消',
    okType: 'danger',
    onOk: () => removeNote()
  })
}

async function restoreCurrentNote() {
  if (!note.value) {
    return
  }
  note.value = await restoreNote(note.value.id)
  message.success('已恢复')
}

async function permanentRemoveCurrentNote() {
  if (!note.value) {
    return
  }
  await permanentlyDeleteNote(note.value.id)
  message.success('已永久删除')
  goWorkspaceView('all')
}

function confirmPermanentRemoveCurrentNote() {
  Modal.confirm({
    title: '永久删除后无法恢复，确认继续？',
    okText: '确认',
    cancelText: '取消',
    okType: 'danger',
    onOk: () => permanentRemoveCurrentNote()
  })
}

async function restoreSelectedHistory() {
  if (!note.value || !selectedHistory.value) {
    return
  }
  reverting.value = true
  try {
    note.value = await revertNoteToHistory(note.value.id, selectedHistory.value.version)
    selectedHistory.value = undefined
    await loadHistoryItems()
    message.success('已恢复到历史版本')
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    reverting.value = false
  }
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function resolveContentState() {
  if (!note.value) {
    return '未知'
  }
  if (note.value.deleted) {
    return '已删除'
  }
  if (note.value.archived) {
    return '已归档'
  }
  return note.value.status === 'DRAFT' ? '草稿' : '已发布'
}

function goWorkspaceView(view: WorkspaceView) {
  activeWorkspaceView.value = view
  router.push({ path: '/', query: view === 'all' ? {} : { view } })
}

function goCategoryFilter(categoryId: number) {
  router.push({ path: '/', query: { mode: 'search', category: String(categoryId) } })
}

function goTagFilter(tagName: string) {
  router.push({ path: '/', query: { mode: 'search', tag: tagName } })
}

function showTagHint() {
  message.info('请选择侧边栏标签云中的标签进行筛选')
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

function isCurrentNoteTag(tagName: string) {
  return note.value?.tags.some(tag => tag.name === tagName) ?? false
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

function addCodeCopyButtons() {
  if (!previewRef.value) {
    return
  }
  previewRef.value.querySelectorAll('pre').forEach(preElement => {
    if (preElement.querySelector('.code-copy-button')) {
      return
    }
    const codeElement = preElement.querySelector('code')
    if (!codeElement) {
      return
    }
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'code-copy-button'
    button.textContent = '复制'
    button.addEventListener('click', async event => {
      event.stopPropagation()
      try {
        await navigator.clipboard.writeText(codeElement.textContent ?? '')
        button.textContent = '已复制'
        window.setTimeout(() => {
          button.textContent = '复制'
        }, 1600)
      } catch {
        message.error('复制失败')
      }
    })
    preElement.classList.add('copyable-code-block')
    preElement.appendChild(button)
  })
}
</script>
