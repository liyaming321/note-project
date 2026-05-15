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
              v-for="tag in tags"
              :key="tag.id"
              :class="['workspace-chip', { active: form.tags.includes(tag.name) }]"
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
          <span class="topbar-helper">{{ isEdit ? '正在编辑笔记' : '正在新建笔记' }}</span>
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
          <a-button size="large" @click="goBack">返回列表</a-button>
          <a-button type="primary" size="large" :loading="saving" @click="saveNote">
            <template #icon><SaveOutlined /></template>
            保存
          </a-button>
        </div>
      </header>

      <div class="workspace-canvas edit-workspace-canvas">
        <div class="edit-page workspace-edit-page">
          <section class="edit-layout">
            <a-form layout="vertical" class="edit-form form-panel">
              <section class="edit-form-section">
                <div class="edit-section-heading">
                  <span>基础信息</span>
                  <p>标题、分类和标签决定笔记在知识库里的位置。</p>
                </div>
                <a-row :gutter="[16, 12]">
                  <a-col :xs="24" :lg="12">
                    <a-form-item label="标题" required>
                      <a-input v-model:value="form.title" placeholder="输入笔记标题" />
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
                      <a-form-item label="类型" required>
                        <a-select v-model:value="form.type">
                          <a-select-option value="MARKDOWN">Markdown</a-select-option>
                          <a-select-option value="CODE">代码</a-select-option>
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
                  <h2>正文编辑区</h2>
                  <p>支持 Markdown、代码块语言、图片粘贴上传和实时预览。</p>
                </div>
              </div>
              <div id="editor" class="editor"></div>
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
  InboxOutlined,
  PlusOutlined,
  SaveOutlined,
  SettingOutlined,
  StarOutlined,
  TagsOutlined
} from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import Vditor from 'vditor'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  createCategory,
  createNote,
  fetchCategories,
  fetchLlmProviders,
  fetchNote,
  fetchTags,
  summarizeNoteWithLlm,
  updateNote,
  uploadImage
} from '@/api/knowledgeBase'
import type { ApiResponse, ImageUploadResult } from '@/types/api'
import type { Category, LlmProviderInfo, NotePayload, NoteType, Tag } from '@/types/api'

const route = useRoute()
const router = useRouter()
type CategoryTreeNode = {
  title: string
  value: number
  key: number
  children: CategoryTreeNode[]
}
type WorkspaceView = 'all' | 'recent' | 'favorite' | 'archived' | 'trash'

const isEdit = computed(() => Boolean(route.params.id))
const saving = ref(false)
const summarizing = ref(false)
const savingCategory = ref(false)
const categoryManagerVisible = ref(false)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
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
let editor: Vditor | undefined

const categoryTreeData = computed(() => toTreeData(categories.value))
const flatCategories = computed(() => flattenCategories(categories.value))
const tagOptions = computed(() => tags.value.map((tag: Tag) => ({ value: tag.name, label: tag.name })))

onMounted(async () => {
  await Promise.all([loadCategories(), loadTags(), loadLlmProviders()])
  await nextTick()
  editor = new Vditor('editor', {
    height: 'calc(100vh - 340px)',
    mode: 'ir',
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
  }
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

async function loadExistingNote() {
  const note = await fetchNote(Number(route.params.id))
  form.title = note.title
  form.content = note.content
  form.summary = note.summary ?? ''
  form.type = note.type as NoteType
  form.status = note.status
  form.language = note.language ?? ''
  form.categoryId = note.category?.id
  form.tags = note.tags.map((tag: Tag) => tag.name)
  form.favorite = note.favorite
  form.pinned = note.pinned
  editor?.setValue(note.content)
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
  const files = Array.from(clipboardEvent.clipboardData?.files ?? []).filter(file => file.type.startsWith('image/'))
  if (files.length === 0) {
    return
  }
  clipboardEvent.preventDefault()
  clipboardEvent.stopPropagation()
  for (const file of files) {
    try {
      const image = await uploadImage(file)
      editor?.insertValue(`![${image.fileName}](${image.url})\n`)
      form.content = editor?.getValue() ?? form.content
    } catch (error) {
      message.error((error as Error).message)
    }
  }
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
