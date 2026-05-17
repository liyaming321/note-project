import { httpClient, unwrapData } from './client'

import type {
  AdminBackupInfo,
  AdminConfigurationChecklist,
  AdminIndexHealth,
  AdminReindexResult,
  AdminVectorCleanupResult,
  AdminVectorIndexInfo,
  AdminVectorReindexResult,
  AdminWorkspaceInfo,
  BatchLinkImportPayload,
  BatchLinkImportResult,
  BookmarkImportResult,
  Category,
  LlmProviderInfo,
  LlmSummaryPayload,
  LlmSummaryResult,
  NoteDetail,
  NoteHistoryDetail,
  NoteHistorySummary,
  MarkdownImportResult,
  ImageUploadResult,
  EmbeddingProviderInfo,
  KnowledgeOrganizeCandidate,
  KnowledgeQaPayload,
  KnowledgeQaResult,
  LinkImportPayload,
  LinkImportPreview,
  LlmProviderTestResult,
  NoteListItem,
  NoteKind,
  NotePayload,
  NoteQuery,
  NoteStatus,
  OrganizeApplyItem,
  OrganizeApplyResult,
  PageResponse,
  SearchFeedbackPayload,
  SearchFeedbackResult,
  SearchFeedbackSummary,
  SearchQuery,
  SearchResult,
  SearchTuningSettings,
  SimilarNote,
  Tag
} from '@/types/api'

export function fetchNotes(query: NoteQuery = {}) {
  return unwrapData<PageResponse<NoteListItem>>(httpClient.get('/notes', { params: query }))
}

export function fetchNote(id: number) {
  return unwrapData<NoteDetail>(httpClient.get(`/notes/${id}`))
}

export function fetchLlmProviders() {
  return unwrapData<LlmProviderInfo[]>(httpClient.get('/notes/llm-providers'))
}

export function summarizeNoteWithLlm(payload: LlmSummaryPayload) {
  return unwrapData<LlmSummaryResult>(httpClient.post('/notes/llm-summary', payload, {
    timeout: 90000
  }))
}

export function createNote(payload: NotePayload) {
  return unwrapData<NoteDetail>(httpClient.post('/notes', payload))
}

export function updateNote(id: number, payload: NotePayload) {
  return unwrapData<NoteDetail>(httpClient.put(`/notes/${id}`, payload))
}

export function deleteNote(id: number) {
  return unwrapData<void>(httpClient.delete(`/notes/${id}`))
}

export function restoreNote(id: number) {
  return unwrapData<NoteDetail>(httpClient.post(`/notes/${id}/restore`))
}

export function fetchNoteHistory(id: number) {
  return unwrapData<NoteHistorySummary[]>(httpClient.get(`/notes/${id}/history`))
}

export function fetchNoteHistoryVersion(id: number, version: number) {
  return unwrapData<NoteHistoryDetail>(httpClient.get(`/notes/${id}/history/${version}`))
}

export function revertNoteToHistory(id: number, version: number) {
  return unwrapData<NoteDetail>(httpClient.post(`/notes/${id}/revert/${version}`))
}

export function changeFavorite(id: number, value: boolean) {
  return unwrapData<NoteDetail>(httpClient.patch(`/notes/${id}/favorite`, { value }))
}

export function changePinned(id: number, value: boolean) {
  return unwrapData<NoteDetail>(httpClient.patch(`/notes/${id}/pinned`, { value }))
}

export function changeStatus(id: number, status: NoteStatus) {
  return unwrapData<NoteDetail>(httpClient.patch(`/notes/${id}/status`, { status }))
}

export function changeArchived(id: number, value: boolean) {
  return unwrapData<NoteDetail>(httpClient.patch(`/notes/${id}/archived`, { value }))
}

export function permanentlyDeleteNote(id: number) {
  return unwrapData<void>(httpClient.delete(`/notes/${id}/permanent`))
}

export function batchRestoreNotes(noteIds: number[]) {
  return unwrapData<NoteListItem[]>(httpClient.post('/notes/batch/restore', { noteIds }))
}

export function reorderNotes(noteIds: number[]) {
  return unwrapData<NoteListItem[]>(httpClient.post('/notes/reorder', { noteIds }))
}

export function importMarkdown(files: File[]) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return unwrapData<MarkdownImportResult>(httpClient.post('/import/markdown', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  }))
}

export function importBookmarks(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrapData<BookmarkImportResult>(httpClient.post('/import/bookmarks', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  }))
}

export function importLink(payload: LinkImportPayload) {
  return unwrapData<LinkImportPreview>(httpClient.post('/import/link', payload, {
    timeout: 120000
  }))
}

export function importLinks(payload: BatchLinkImportPayload) {
  return unwrapData<BatchLinkImportResult>(httpClient.post('/import/links', payload, {
    timeout: 300000
  }))
}

export async function exportBackup() {
  const response = await httpClient.get('/admin/backup', {
    responseType: 'blob',
    timeout: 60000
  })
  downloadBlob(response.data, resolveDownloadFileName(response.headers['content-disposition'], 'knowledge-base-backup.zip'))
}

export function exportNoteMarkdownUrl(id: number) {
  return `/api/notes/${id}/export/markdown`
}

export async function exportNotesZip(noteIds: number[]) {
  const response = await httpClient.post('/export/zip', { noteIds }, {
    responseType: 'blob',
    timeout: 60000
  })
  downloadBlob(response.data, resolveDownloadFileName(response.headers['content-disposition'], 'knowledge-base.zip'))
}

export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return unwrapData<ImageUploadResult>(httpClient.post('/assets/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  }))
}

export function searchNotes(query: SearchQuery = {}) {
  const { searchMode, ...params } = query
  return unwrapData<PageResponse<SearchResult>>(httpClient.get('/search', {
    params: {
      ...params,
      mode: searchMode
    }
  }))
}

export function sendSearchFeedback(payload: SearchFeedbackPayload) {
  return unwrapData<SearchFeedbackResult>(httpClient.post('/search/feedback', payload))
}

export function askKnowledgeBase(payload: KnowledgeQaPayload) {
  return unwrapData<KnowledgeQaResult>(httpClient.post('/knowledge-qa', payload, {
    timeout: 120000
  }))
}

export function fetchSimilarNotes(noteId: number, limit = 6) {
  return unwrapData<SimilarNote[]>(httpClient.get(`/notes/${noteId}/similar`, {
    params: { limit }
  }))
}

export function fetchCategories() {
  return unwrapData<Category[]>(httpClient.get('/categories'))
}

export function createCategory(name: string, parentId?: number) {
  return unwrapData<Category>(httpClient.post('/categories', { name, parentId }))
}

export function updateCategory(id: number, name: string, parentId?: number) {
  return unwrapData<Category>(httpClient.put(`/categories/${id}`, { name, parentId }))
}

export function deleteCategory(id: number) {
  return unwrapData<void>(httpClient.delete(`/categories/${id}`))
}

export function fetchTags() {
  return unwrapData<Tag[]>(httpClient.get('/tags'))
}

export function createTag(name: string) {
  return unwrapData<Tag>(httpClient.post('/tags', { name }))
}

export function deleteTag(id: number) {
  return unwrapData<void>(httpClient.delete(`/tags/${id}`))
}

export function fetchNoteKinds() {
  return unwrapData<NoteKind[]>(httpClient.get('/note-kinds'))
}

export function createNoteKind(name: string, sortOrder?: number) {
  return unwrapData<NoteKind>(httpClient.post('/note-kinds', { name, sortOrder }))
}

export function updateNoteKind(id: number, name: string, sortOrder?: number) {
  return unwrapData<NoteKind>(httpClient.put(`/note-kinds/${id}`, { name, sortOrder }))
}

export function deleteNoteKind(id: number) {
  return unwrapData<void>(httpClient.delete(`/note-kinds/${id}`))
}

export function fetchWorkspaceInfo() {
  return unwrapData<AdminWorkspaceInfo>(httpClient.get('/admin/workspace'))
}

export function fetchConfigurationChecklist() {
  return unwrapData<AdminConfigurationChecklist>(httpClient.get('/admin/configuration-checklist'))
}

export function testLlmProvider(provider: string) {
  return unwrapData<LlmProviderTestResult>(httpClient.post(`/admin/llm-providers/${provider}/test`, undefined, {
    timeout: 90000
  }))
}

export function rebuildSearchIndex() {
  return unwrapData<AdminReindexResult>(httpClient.post('/admin/reindex'))
}

export function fetchEmbeddingProvider() {
  return unwrapData<EmbeddingProviderInfo>(httpClient.get('/admin/embedding-provider'))
}

export function fetchVectorIndexInfo() {
  return unwrapData<AdminVectorIndexInfo>(httpClient.get('/admin/vector-index'))
}

export function rebuildVectorIndex() {
  return unwrapData<AdminVectorReindexResult>(httpClient.post('/admin/vector-index/rebuild', undefined, {
    timeout: 300000
  }))
}

export function fetchIndexHealth() {
  return unwrapData<AdminIndexHealth>(httpClient.get('/admin/index-health'))
}

export function cleanupVectorIndex() {
  return unwrapData<AdminVectorCleanupResult>(httpClient.post('/admin/vector-index/cleanup'))
}

export function fetchSearchTuning() {
  return unwrapData<SearchTuningSettings>(httpClient.get('/admin/search-tuning'))
}

export function updateSearchTuning(payload: Partial<SearchTuningSettings>) {
  return unwrapData<SearchTuningSettings>(httpClient.put('/admin/search-tuning', payload))
}

export function fetchSearchFeedbackSummary() {
  return unwrapData<SearchFeedbackSummary>(httpClient.get('/admin/search-feedback-summary'))
}

export function fetchOrganizeCandidates(page = 0, size = 20) {
  return unwrapData<PageResponse<KnowledgeOrganizeCandidate>>(httpClient.get('/admin/organize-candidates', {
    params: { page, size }
  }))
}

export function applyOrganizeCandidates(items: OrganizeApplyItem[]) {
  return unwrapData<OrganizeApplyResult>(httpClient.post('/admin/organize-candidates/apply', { items }))
}

export function fetchBackupInfo() {
  return unwrapData<AdminBackupInfo>(httpClient.get('/admin/backup-info'))
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function resolveDownloadFileName(disposition: string | undefined, fallback: string) {
  if (!disposition) {
    return fallback
  }
  const utf8NameMatch = disposition.match(/filename\\*=UTF-8''([^;]+)/i)
  if (utf8NameMatch?.[1]) {
    return decodeURIComponent(utf8NameMatch[1])
  }
  const nameMatch = disposition.match(/filename="?([^";]+)"?/i)
  return nameMatch?.[1] ?? fallback
}
