<template>
  <div :class="['knowledge-workspace', { 'edit-focus-mode': focusMode }]">
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
              :class="['text-link-row', { active: form.categoryId === category.id }]"
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
              v-for="tag in visibleSidebarTags"
              :key="tag.id"
              :class="['workspace-chip', { active: form.tags.includes(tag.name) }]"
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
            @click="router.push({ path: '/settings', query: { panel: 'tags' } })"
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
          <span class="topbar-helper">{{ isEdit ? '正在编辑笔记' : '正在新建笔记' }}</span>
          <span :class="['editor-save-state', saveStateClass]">{{ saveStateText }}</span>
        </div>
        <div class="topbar-actions">
          <a-tooltip title="回收站">
            <button class="icon-circle-button" type="button" @click="goWorkspaceView('trash')">
              <DeleteOutlined />
            </button>
          </a-tooltip>
          <a-tooltip title="设置与维护">
            <button class="icon-circle-button" type="button" @click="router.push('/settings')">
              <SettingOutlined />
            </button>
          </a-tooltip>
          <a-button size="large" class="focus-mode-button" @click="toggleFocusMode">
            <template #icon>
              <FullscreenExitOutlined v-if="focusMode" />
              <FullscreenOutlined v-else />
            </template>
            {{ focusMode ? '退出专注' : '专注写作' }}
          </a-button>
          <a-button size="large" @click="goBack">返回列表</a-button>
          <a-button type="primary" size="large" :loading="saving" @click="saveNote">
            <template #icon><SaveOutlined /></template>
            保存
          </a-button>
        </div>
      </header>

      <div class="workspace-canvas edit-workspace-canvas">
        <div class="edit-page workspace-edit-page">
          <section :class="['edit-layout', { 'focus-layout': focusMode }]">
            <a-form v-show="!focusMode" layout="vertical" class="edit-form form-panel">
              <section class="edit-form-section">
                <div class="edit-section-heading">
                  <span>基础信息</span>
                  <p>标题、用途、分类和标签决定笔记在知识库里的位置。</p>
                </div>
                <a-row :gutter="[16, 12]">
                  <a-col :xs="24" :lg="12">
                    <a-form-item label="标题" required>
                      <a-input v-model:value="form.title" placeholder="输入笔记标题" />
                    </a-form-item>
                  </a-col>
                  <a-col :xs="24" :sm="12" :lg="6">
                    <a-form-item label="用途">
                      <a-select
                        v-model:value="form.noteKindId"
                        allow-clear
                        show-search
                        placeholder="日记 / 灵感 / 项目"
                        :options="noteKindOptions"
                      />
                    </a-form-item>
                  </a-col>
                  <a-col :xs="24" :sm="12" :lg="6">
                    <a-form-item label="分类">
                      <a-tree-select
                        v-model:value="form.categoryId"
                        allow-clear
                        tree-default-expand-all
                        :tree-data="categoryTreeData"
                        placeholder="选择分类"
                      />
                    </a-form-item>
                  </a-col>
                  <a-col :xs="24" :sm="12" :lg="6">
                    <a-form-item label="发布状态">
                      <a-select v-model:value="form.status">
                        <a-select-option value="PUBLISHED">已发布</a-select-option>
                        <a-select-option value="DRAFT">草稿</a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                </a-row>
                <a-form-item label="标签">
                  <a-select
                    v-model:value="form.tags"
                    mode="tags"
                    placeholder="输入标签后回车"
                    :options="tagOptions"
                  />
                </a-form-item>
              </section>

              <section class="edit-form-section ai-edit-section">
                <div class="edit-section-heading">
                  <span>AI 辅助</span>
                  <p>可以先写正文，再生成摘要、标签和分类建议。</p>
                </div>
                <a-form-item label="摘要">
                  <a-textarea
                    v-model:value="form.summary"
                    :rows="3"
                    :maxlength="500"
                    show-count
                    placeholder="可手动填写，也可以使用 LLM 自动总结生成"
                  />
                </a-form-item>
                <div class="llm-summary-panel">
                  <div class="llm-summary-copy">
                    <strong>LLM 总结</strong>
                    <span>根据正文内容生成结构化建议</span>
                  </div>
                  <div class="llm-summary-actions">
                    <a-select v-model:value="llmProvider" class="llm-provider-select">
                      <a-select-option value="bailian">阿里百炼</a-select-option>
                      <a-select-option value="deepseek">DeepSeek</a-select-option>
                    </a-select>
                    <a-button class="ghost-blue-button" :loading="summarizing" @click="summarizeWithLlm">生成总结</a-button>
                  </div>
                </div>
              </section>

              <a-collapse ghost class="edit-advanced-collapse">
                <a-collapse-panel key="advanced" header="更多属性">
                  <a-row :gutter="[16, 12]">
                    <a-col :xs="24" :sm="12" :lg="8">
                      <a-form-item label="内容格式" required>
                        <a-select v-model:value="form.type">
                          <a-select-option
                            v-for="option in contentFormatOptions"
                            :key="option.value"
                            :value="option.value"
                          >
                            {{ option.label }}
                          </a-select-option>
                        </a-select>
                      </a-form-item>
                    </a-col>
                    <a-col :xs="24" :sm="12" :lg="8">
                      <a-form-item label="语言">
                        <a-input v-model:value="form.language" placeholder="java / js / sql" />
                      </a-form-item>
                    </a-col>
                    <a-col :xs="24" :lg="8">
                      <a-form-item label="标记">
                        <div class="status-switches">
                          <a-checkbox v-model:checked="form.favorite">收藏</a-checkbox>
                          <a-checkbox v-model:checked="form.pinned">置顶</a-checkbox>
                        </div>
                      </a-form-item>
                    </a-col>
                  </a-row>
                </a-collapse-panel>
              </a-collapse>
            </a-form>

            <div class="editor-panel">
              <div class="editor-panel-header">
                <div>
                  <span class="editor-eyebrow">写作台</span>
                  <h2>正文编辑区</h2>
                  <p>支持 Markdown、代码、普通文本、图片粘贴上传和实时预览。</p>
                </div>
                <div class="editor-feature-list" aria-label="编辑器能力">
                  <span>Markdown</span>
                  <span>普通文本</span>
                  <span>图片粘贴</span>
                  <span>实时预览</span>
                </div>
              </div>
              <div
                :class="['editor-shell', { 'dragging-image': imageDragActive }]"
                @dragover.prevent="handleEditorDragOver"
                @dragleave="handleEditorDragLeave"
                @drop.prevent="handleEditorDrop"
              >
                <div id="editor" class="editor"></div>
                <div v-if="imageDragActive" class="editor-drop-overlay">
                  <PictureOutlined />
                  <strong>释放图片，插入到正文</strong>
                  <span>支持 PNG、JPG、GIF、WebP 等图片文件</span>
                </div>
              </div>
              <div class="editor-status-bar">
                <div class="editor-stat-group" aria-label="正文统计">
                  <span>{{ editorStats.characters }} 字符</span>
                  <span>{{ editorStats.lines }} 行</span>
                  <span>{{ editorStats.images }} 张图片</span>
                </div>
                <div class="editor-save-summary">
                  <span :class="['save-dot', saveStateClass]"></span>
                  <span>{{ saveStateText }}</span>
                  <span>{{ lastSavedLabel }}</span>
                </div>
              </div>
              <div v-if="imageUploadState.visible" :class="['image-upload-banner', { error: imageUploadState.error }]">
                <div>
                  <strong>{{ imageUploadState.title }}</strong>
                  <span>{{ imageUploadState.detail }}</span>
                </div>
                <div class="image-upload-actions">
                  <a-button
                    v-if="imageUploadState.error && imageUploadState.retryFiles.length > 0"
                    size="small"
                    @click="retryImageUpload"
                  >
                    重试
                  </a-button>
                  <a-button size="small" type="text" @click="dismissImageUploadState">关闭</a-button>
                </div>
              </div>
            </div>
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
  ClockCircleOutlined,
  ClusterOutlined,
  DeleteOutlined,
  FileTextOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  InboxOutlined,
  PictureOutlined,
  PlusOutlined,
  SaveOutlined,
  SettingOutlined,
  StarOutlined,
  TagsOutlined
} from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type Vditor from 'vditor'

import {
  createCategory,
  createNote,
  fetchCategories,
  fetchLlmProviders,
  fetchNote,
  fetchNoteKinds,
  fetchTags,
  summarizeNoteWithLlm,
  updateNote,
  uploadImage
} from '@/api/knowledgeBase'
import type { ApiResponse, ImageUploadResult } from '@/types/api'
import type { Category, LinkImportDraft, LlmProviderInfo, NoteKind, NotePayload, NoteType, Tag } from '@/types/api'
import { CONTENT_FORMAT_OPTIONS } from '@/utils/noteFormat'

const route = useRoute()
const router = useRouter()
type CategoryTreeNode = {
  title: string
  value: number
  key: number
  children: CategoryTreeNode[]
}
type WorkspaceView = 'all' | 'recent' | 'favorite' | 'archived' | 'trash'
type ImageUploadState = {
  visible: boolean
  title: string
  detail: string
  error: boolean
  uploading: boolean
  retryFiles: File[]
}

const LINK_IMPORT_DRAFT_PREFIX = 'people-wiki-link-import-draft:'
const SIDEBAR_TAG_LIMIT = 12
const contentFormatOptions = CONTENT_FORMAT_OPTIONS

const isEdit = computed(() => Boolean(route.params.id))
const saving = ref(false)
const summarizing = ref(false)
const savingCategory = ref(false)
const categoryManagerVisible = ref(false)
const focusMode = ref(false)
const hasUnsavedChanges = ref(false)
const hydrationComplete = ref(false)
const importedDraftLoaded = ref(false)
const lastSavedAt = ref<Date>()
const imageDragActive = ref(false)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const noteKinds = ref<NoteKind[]>([])
const llmProviders = ref<LlmProviderInfo[]>([])
const llmProvider = ref<'bailian' | 'deepseek'>('bailian')
const categoryForm = reactive<{
  name: string
  parentId?: number
}>({
  name: ''
})
const form = reactive<NotePayload>({
  title: '',
  content: '',
  summary: '',
  type: 'MARKDOWN',
  status: 'PUBLISHED',
  language: '',
  tags: [],
  favorite: false,
  pinned: false
})
const imageUploadState = reactive<ImageUploadState>({
  visible: false,
  title: '图片上传',
  detail: '等待选择图片',
  error: false,
  uploading: false,
  retryFiles: []
})
let editor: Vditor | undefined
let VditorConstructor: typeof import('vditor')['default'] | undefined

const categoryTreeData = computed(() => toTreeData(categories.value))
const flatCategories = computed(() => flattenCategories(categories.value))
const visibleSidebarTags = computed(() => {
  const selectedTagNames = new Set(form.tags)
  const selectedTags = tags.value.filter(tag => selectedTagNames.has(tag.name))
  const remainingTags = tags.value.filter(tag => !selectedTagNames.has(tag.name))
  return [...selectedTags, ...remainingTags].slice(0, SIDEBAR_TAG_LIMIT)
})
const hiddenSidebarTagCount = computed(() => Math.max(tags.value.length - visibleSidebarTags.value.length, 0))
const tagOptions = computed(() => tags.value.map((tag: Tag) => ({ value: tag.name, label: tag.name })))
const noteKindOptions = computed(() => noteKinds.value.map((noteKind: NoteKind) => ({ value: noteKind.id, label: noteKind.name })))
const editorStats = computed(() => calculateEditorStats(form.content))
const saveStateClass = computed(() => {
  if (saving.value) {
    return 'saving'
  }
  return hasUnsavedChanges.value ? 'dirty' : 'saved'
})
const saveStateText = computed(() => {
  if (saving.value) {
    return '保存中'
  }
  if (hasUnsavedChanges.value) {
    return '有未保存修改'
  }
  return lastSavedAt.value ? '已保存' : '等待输入'
})
const lastSavedLabel = computed(() => {
  if (lastSavedAt.value) {
    return `最近保存 ${formatClockTime(lastSavedAt.value)}`
  }
  return isEdit.value ? '已载入历史内容' : '尚未保存'
})

watch(form, () => {
  if (hydrationComplete.value) {
    hasUnsavedChanges.value = true
  }
}, { deep: true })

onMounted(async () => {
  await Promise.all([loadCategories(), loadTags(), loadNoteKinds(), loadLlmProviders()])
  await nextTick()
  const VditorClass = await loadVditor()
  editor = new VditorClass('editor', {
    height: 'calc(100vh - 340px)',
    mode: 'ir',
    placeholder: '从一个标题、一个想法，或一段粘贴的资料开始...',
    cache: {
      enable: false
    },
    preview: {
      hljs: {
        enable: true,
        style: 'github'
      }
    },
    toolbar: [
      'emoji',
      'headings',
      'bold',
      'italic',
      'strike',
      'link',
      '|',
      'list',
      'ordered-list',
      'check',
      'quote',
      'line',
      'code',
      'inline-code',
      '|',
      'upload',
      'table',
      'undo',
      'redo',
      'fullscreen',
      'preview'
    ],
    upload: {
      url: '/api/assets/images',
      fieldName: 'file',
      accept: 'image/*',
      multiple: false,
      max: 10 * 1024 * 1024,
      format: (files, responseText) => formatVditorUpload(files, responseText),
      error: value => message.error(value || '图片上传失败')
    },
    input: value => {
      form.content = value
    }
  })
  document.addEventListener('keydown', handleSaveShortcut)
  document.getElementById('editor')?.addEventListener('paste', handlePasteImage, true)
  if (isEdit.value) {
    await loadExistingNote()
  } else {
    loadImportedLinkDraft()
  }
  await nextTick()
  hasUnsavedChanges.value = importedDraftLoaded.value
  hydrationComplete.value = true
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleSaveShortcut)
  document.getElementById('editor')?.removeEventListener('paste', handlePasteImage, true)
  editor?.destroy()
})

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadTags() {
  tags.value = await fetchTags()
}

async function loadNoteKinds() {
  noteKinds.value = await fetchNoteKinds()
}

async function loadVditor() {
  if (!VditorConstructor) {
    await import('vditor/dist/index.css')
    VditorConstructor = (await import('vditor')).default
  }
  return VditorConstructor
}

async function loadExistingNote() {
  const note = await fetchNote(Number(route.params.id))
  form.title = note.title
  form.content = note.content
  form.summary = note.summary ?? ''
  form.type = note.type as NoteType
  form.status = note.status
  form.language = note.language ?? ''
  form.noteKindId = note.noteKind?.id
  form.categoryId = note.category?.id
  form.tags = note.tags.map((tag: Tag) => tag.name)
  form.favorite = note.favorite
  form.pinned = note.pinned
  lastSavedAt.value = new Date(note.updatedAt)
  editor?.setValue(note.content)
}

function loadImportedLinkDraft() {
  const draftId = typeof route.query.draftId === 'string' ? route.query.draftId : ''
  if (!draftId) {
    return
  }
  const storageKey = `${LINK_IMPORT_DRAFT_PREFIX}${draftId}`
  const rawDraft = window.localStorage.getItem(storageKey)
  if (!rawDraft) {
    message.warning('未找到链接导入草稿，请重新导入')
    return
  }
  try {
    const draft = JSON.parse(rawDraft) as LinkImportDraft
    if (!isLinkImportDraft(draft)) {
      message.warning('链接导入草稿格式异常，请重新导入')
      return
    }
    form.title = draft.title
    form.content = draft.content
    form.summary = draft.summary
    form.type = 'MARKDOWN'
    form.status = 'PUBLISHED'
    form.language = 'markdown'
    form.noteKindId = undefined
    form.categoryId = draft.categoryId
    form.tags = draft.tags
    editor?.setValue(draft.content)
    window.localStorage.removeItem(storageKey)
    importedDraftLoaded.value = true
    hasUnsavedChanges.value = true
    message.success('链接内容已生成草稿，请预览后保存')
  } catch {
    message.error('读取链接导入草稿失败，请重新导入')
  }
}

function isLinkImportDraft(value: unknown): value is LinkImportDraft {
  if (!value || typeof value !== 'object') {
    return false
  }
  const draft = value as Partial<LinkImportDraft>
  return typeof draft.title === 'string' &&
    typeof draft.content === 'string' &&
    typeof draft.summary === 'string' &&
    Array.isArray(draft.tags)
}

async function saveNote() {
  if (!form.title.trim()) {
    message.warning('请输入标题')
    return
  }
  form.content = editor?.getValue() ?? form.content
  if (!form.content.trim()) {
    message.warning('请输入内容')
    return
  }
  saving.value = true
  try {
    const payload: NotePayload = {
      ...form,
      title: form.title.trim(),
      summary: form.summary?.trim() || undefined,
      language: form.language?.trim() || undefined,
      categoryId: form.categoryId,
      tags: form.tags.map((tag: string) => tag.trim()).filter(Boolean)
    }
    const savedNote = isEdit.value
      ? await updateNote(Number(route.params.id), payload)
      : await createNote(payload)
    hasUnsavedChanges.value = false
    lastSavedAt.value = new Date(savedNote.updatedAt)
    message.success('保存成功')
    await router.push(`/notes/${savedNote.id}`)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function loadLlmProviders() {
  try {
    llmProviders.value = await fetchLlmProviders()
    const configuredProvider = llmProviders.value.find(provider => provider.configured)
    if (configuredProvider) {
      llmProvider.value = configuredProvider.name
    }
  } catch {
    llmProviders.value = []
  }
}

async function summarizeWithLlm() {
  form.content = editor?.getValue() ?? form.content
  if (!form.content.trim()) {
    message.warning('请先输入正文内容')
    return
  }
  summarizing.value = true
  try {
    const result = await summarizeNoteWithLlm({
      provider: llmProvider.value,
      title: form.title,
      content: form.content,
      type: form.type,
      language: form.language,
      categoryNames: flatCategoryNames(categories.value)
    })
    if (result.title && !form.title.trim()) {
      form.title = result.title
    }
    form.summary = result.summary
    if (result.tags.length > 0) {
      form.tags = Array.from(new Set([...form.tags, ...result.tags]))
    }
    if (result.categoryId) {
      form.categoryId = result.categoryId
    }
    message.success(`已使用 ${providerLabel(result.provider)} 生成总结建议`)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    summarizing.value = false
  }
}

/**
 * 切换专注写作模式。
 */
async function toggleFocusMode() {
  focusMode.value = !focusMode.value
  await nextTick()
  window.dispatchEvent(new Event('resize'))
}

function goBack() {
  if (isEdit.value) {
    router.push(`/notes/${route.params.id}`)
    return
  }
  router.push('/')
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

function handleSaveShortcut(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void saveNote()
  }
}

async function handlePasteImage(event: Event) {
  const clipboardEvent = event as ClipboardEvent
  const files = extractImageFiles(clipboardEvent.clipboardData?.files)
  if (files.length === 0) {
    return
  }
  clipboardEvent.preventDefault()
  clipboardEvent.stopPropagation()
  await uploadImagesToEditor(files, '粘贴上传')
}

/**
 * 标记图片拖拽状态。
 *
 * @param event 拖拽事件
 */
function handleEditorDragOver(event: DragEvent) {
  if (!hasImageFile(event.dataTransfer?.items, event.dataTransfer?.files)) {
    return
  }
  event.dataTransfer!.dropEffect = 'copy'
  imageDragActive.value = true
}

/**
 * 离开编辑器拖拽区域时隐藏提示层。
 *
 * @param event 拖拽事件
 */
function handleEditorDragLeave(event: DragEvent) {
  const target = event.currentTarget as HTMLElement
  const relatedTarget = event.relatedTarget as Node | null
  if (!relatedTarget || !target.contains(relatedTarget)) {
    imageDragActive.value = false
  }
}

/**
 * 处理图片拖拽上传。
 *
 * @param event 拖拽事件
 */
async function handleEditorDrop(event: DragEvent) {
  imageDragActive.value = false
  const files = extractImageFiles(event.dataTransfer?.files)
  if (files.length === 0) {
    message.warning('请拖入图片文件')
    return
  }
  await uploadImagesToEditor(files, '拖拽上传')
}

/**
 * 重新上传上次失败的图片。
 */
async function retryImageUpload() {
  if (imageUploadState.retryFiles.length === 0) {
    return
  }
  await uploadImagesToEditor([...imageUploadState.retryFiles], '重试上传')
}

/**
 * 关闭图片上传状态提示。
 */
function dismissImageUploadState() {
  imageUploadState.visible = false
}

/**
 * 上传图片并插入编辑器。
 *
 * @param files 图片文件
 * @param sourceLabel 上传来源文案
 */
async function uploadImagesToEditor(files: File[], sourceLabel: string) {
  imageUploadState.visible = true
  imageUploadState.error = false
  imageUploadState.uploading = true
  imageUploadState.retryFiles = files
  for (const [index, file] of files.entries()) {
    const currentIndex = index + 1
    imageUploadState.title = `${sourceLabel}图片中`
    imageUploadState.detail = `${file.name} (${formatFileSize(file.size)})，${currentIndex}/${files.length}`
    try {
      const image = await uploadImage(file)
      editor?.insertValue(`![${image.fileName}](${image.url})\n`)
      form.content = editor?.getValue() ?? form.content
      hasUnsavedChanges.value = true
    } catch (error) {
      imageUploadState.error = true
      imageUploadState.uploading = false
      imageUploadState.title = '图片上传失败'
      imageUploadState.detail = `${file.name} 上传失败：${(error as Error).message}`
      message.error((error as Error).message)
      return
    }
  }
  imageUploadState.uploading = false
  imageUploadState.retryFiles = []
  imageUploadState.title = '图片已插入'
  imageUploadState.detail = `已插入 ${files.length} 张图片，文件保存在本地图片资源目录`
}

/**
 * 判断拖拽内容里是否包含图片。
 *
 * @param items 拖拽项目
 * @param files 文件列表
 * @return 是否包含图片
 */
function hasImageFile(items?: DataTransferItemList | null, files?: FileList | null) {
  const itemList = Array.from(items ?? [])
  if (itemList.some(item => item.kind === 'file' && item.type.startsWith('image/'))) {
    return true
  }
  return extractImageFiles(files).length > 0
}

/**
 * 从文件列表中过滤图片文件。
 *
 * @param fileList 文件列表
 * @return 图片文件数组
 */
function extractImageFiles(fileList?: FileList | null) {
  return Array.from(fileList ?? []).filter(file => file.type.startsWith('image/'))
}

/**
 * 计算编辑器正文统计信息。
 *
 * @param content Markdown 正文
 * @return 统计信息
 */
function calculateEditorStats(content: string) {
  const normalizedContent = content.trim()
  return {
    characters: normalizedContent.replace(/\s/g, '').length,
    lines: normalizedContent ? normalizedContent.split(/\r?\n/).length : 0,
    images: (content.match(/!\[[^\]]*]\([^)]*\)/g) ?? []).length
  }
}

/**
 * 格式化文件大小。
 *
 * @param size 文件字节数
 * @return 可读文件大小
 */
function formatFileSize(size: number) {
  if (size < 1024 * 1024) {
    return `${Math.max(1, Math.round(size / 1024))} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 格式化本地时间。
 *
 * @param value 时间
 * @return HH:mm:ss
 */
function formatClockTime(value: Date) {
  return value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function formatVditorUpload(files: File[], responseText: string) {
  const response = JSON.parse(responseText) as ApiResponse<ImageUploadResult>
  if (!response.success || !response.data) {
    return JSON.stringify({
      code: 1,
      msg: response.message || '图片上传失败',
      data: {
        errFiles: files.map(file => file.name),
        succMap: {}
      }
    })
  }
  return JSON.stringify({
    code: 0,
    msg: '',
    data: {
      errFiles: [],
      succMap: {
        [response.data.fileName]: response.data.url
      }
    }
  })
}

function toTreeData(items: Category[]): CategoryTreeNode[] {
  return items.map(item => ({
    title: item.name,
    value: item.id,
    key: item.id,
    children: toTreeData(item.children ?? [])
  }))
}

function flatCategoryNames(items: Category[]): string[] {
  return items.flatMap(item => [item.name, ...flatCategoryNames(item.children ?? [])])
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

function providerLabel(provider: string) {
  return provider === 'deepseek' ? 'DeepSeek' : '阿里百炼'
}
</script>
