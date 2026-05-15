import { httpClient, unwrapData } from './client'

import type {
  AdminReindexResult,
  AdminVectorIndexInfo,
  AdminVectorReindexResult,
  AdminWorkspaceInfo,
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
  NoteListItem,
  NotePayload,
  NoteQuery,
  NoteStatus,
  PageResponse,
  SearchQuery,
  SearchResult,
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
  return unwrapData<PageResponse<SearchResult>>(httpClient.get('/search', { params: query }))
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

export function fetchWorkspaceInfo() {
  return unwrapData<AdminWorkspaceInfo>(httpClient.get('/admin/workspace'))
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
