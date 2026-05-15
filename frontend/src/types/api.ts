export type NoteType = 'MARKDOWN' | 'CODE'
export type NoteStatus = 'DRAFT' | 'PUBLISHED'
export type SearchScope = 'all' | 'title' | 'code'

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface Tag {
  id: number
  name: string
  createdAt?: string
  updatedAt?: string
}

export interface Category {
  id: number
  name: string
  parentId?: number
  children: Category[]
  createdAt?: string
  updatedAt?: string
}

export interface SimpleCategory {
  id: number
  name: string
  parentId?: number
}

export interface NoteListItem {
  id: number
  title: string
  summary?: string
  type: NoteType
  status: NoteStatus
  language?: string
  category?: SimpleCategory
  tags: Tag[]
  pinned: boolean
  favorite: boolean
  sortOrder?: number
  archived: boolean
  deleted: boolean
  createdAt: string
  updatedAt: string
}

export interface NoteDetail extends NoteListItem {
  content: string
  contentText: string
}

export interface NoteHistorySummary {
  noteId: number
  version: number
  title: string
  createdAt: string
}

export interface NoteHistoryDetail {
  noteId: number
  version: number
  title: string
  content: string
  contentText: string
  type: NoteType
  language?: string
  categoryId?: number
  categoryName?: string
  tags: string[]
  createdAt: string
}

export interface NotePayload {
  title: string
  content: string
  summary?: string
  type: NoteType
  status?: NoteStatus
  language?: string
  categoryId?: number
  tags: string[]
  pinned?: boolean
  favorite?: boolean
}

export interface NoteQuery {
  page?: number
  size?: number
  categoryId?: number
  tag?: string
  type?: NoteType
  status?: NoteStatus
  favorite?: boolean
  pinned?: boolean
  archived?: boolean
  includeDeleted?: boolean
  onlyDeleted?: boolean
  updatedFrom?: string
  updatedTo?: string
  sort?: 'createdAt' | 'updatedAt' | 'title' | 'sortOrder'
  direction?: 'asc' | 'desc'
}

export interface SearchQuery {
  q?: string
  scope?: SearchScope
  tag?: string
  category?: string
  language?: string
  status?: NoteStatus
  updatedFrom?: string
  updatedTo?: string
  page?: number
  size?: number
}

export interface SearchResult extends NoteListItem {
  highlight: string
  hitFields: string[]
}

export interface MarkdownImportItem {
  fileName: string
  noteId?: number
  title?: string
  success: boolean
  message: string
}

export interface MarkdownImportResult {
  importedCount: number
  failedCount: number
  items: MarkdownImportItem[]
}

export interface BookmarkImportItem {
  title: string
  url: string
  noteId?: number
  success: boolean
  message: string
}

export interface BookmarkImportResult {
  importedCount: number
  failedCount: number
  items: BookmarkImportItem[]
}

export interface ImageUploadResult {
  fileName: string
  url: string
  size: number
}

export interface AdminWorkspaceInfo {
  dataPath: string
  indexPath: string
  vectorIndexPath: string
  imagesPath: string
  historyMaxVersions: number
  version: string
}

export interface AdminReindexResult {
  indexedCount: number
  indexPath: string
}

export interface EmbeddingProviderInfo {
  name: string
  model: string
  configured: boolean
  message: string
}

export interface AdminVectorIndexInfo {
  vectorIndexPath: string
  provider: string
  model: string
  dimension?: number
  pooling: string
  normalize: boolean
  indexedCount: number
  configured: boolean
  available: boolean
  lastRebuiltAt?: string
  message: string
}

export interface AdminVectorReindexResult {
  indexedCount: number
  vectorIndexPath: string
  provider: string
  model: string
  dimension: number
}

export interface LlmProviderInfo {
  name: 'bailian' | 'deepseek'
  model: string
  configured: boolean
}

export interface LlmSummaryPayload {
  provider?: string
  title?: string
  content: string
  type: NoteType
  language?: string
  categoryNames?: string[]
}

export interface LlmSummaryResult {
  provider: string
  model: string
  title?: string
  summary: string
  tags: string[]
  categoryName?: string
  categoryId?: number
}
