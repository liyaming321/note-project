export type NoteType = 'MARKDOWN' | 'CODE' | 'TEXT'
export type NoteStatus = 'DRAFT' | 'PUBLISHED'
export type SearchScope = 'all' | 'title' | 'code'
export type SearchMode = 'exact' | 'semantic' | 'hybrid'

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

export interface NoteKind {
  id: number
  name: string
  sortOrder: number
  builtIn: boolean
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
  noteKind?: NoteKind
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
  noteKindId?: number
  noteKindName?: string
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
  noteKindId?: number
  categoryId?: number
  tags: string[]
  pinned?: boolean
  favorite?: boolean
}

export interface NoteQuery {
  page?: number
  size?: number
  categoryId?: number
  noteKindId?: number
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
  searchMode?: SearchMode
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
  keywordScore?: number
  semanticSimilarity?: number
  hybridScore?: number
  rankExplanation?: string
  matchReason?: string
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

export interface LinkImportPayload {
  url: string
  provider?: string
  useLlm?: boolean
}

export interface BatchLinkImportPayload {
  urls: string[]
  provider?: string
  useLlm?: boolean
}

export interface LinkImportPreview {
  sourceUrl: string
  sourceTitle: string
  provider: string
  model: string
  title: string
  summary: string
  tags: string[]
  categoryName?: string
  categoryId?: number
  content: string
}

export interface LinkImportDraft extends LinkImportPreview {
  createdAt: string
}

export interface BatchLinkImportItem {
  url: string
  success: boolean
  message: string
  preview?: LinkImportPreview
}

export interface BatchLinkImportResult {
  totalCount: number
  successCount: number
  failedCount: number
  items: BatchLinkImportItem[]
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

export interface AdminIndexHealth {
  databaseActiveCount: number
  searchIndexedCount: number
  vectorIndexedCount: number
  searchHealthy: boolean
  vectorHealthy: boolean
  message: string
}

export interface AdminVectorCleanupResult {
  removedCount: number
  indexedCount: number
  message: string
}

export interface SearchTuningSettings {
  keywordWeight: number
  semanticWeight: number
  titleHitBoost: number
  tagHitBoost: number
  pinnedBoost: number
  favoriteBoost: number
  recentSevenDaysBoost: number
  recentThirtyDaysBoost: number
  updatedAt: string
  configPath: string
}

export interface SearchFeedbackPayload {
  noteId: number
  keyword?: string
  mode?: SearchMode
  useful: boolean
  reason?: string
}

export interface SearchFeedbackResult {
  totalCount: number
  usefulCount: number
  irrelevantCount: number
  message: string
  recordedAt: string
}

export interface SearchFeedbackItem {
  noteId: number
  noteTitle: string
  keyword: string
  mode: string
  useful: boolean
  reason: string
  createdAt: string
}

export interface SearchFeedbackSummary {
  totalCount: number
  usefulCount: number
  irrelevantCount: number
  recentItems: SearchFeedbackItem[]
}

export interface KnowledgeOrganizeCandidate {
  note: NoteListItem
  reasons: string[]
  suggestedTags: string[]
  suggestedCategory: string
  suggestedSummary: string
}

export interface OrganizeApplyItem {
  noteId: number
  summary?: string
  tags?: string[]
  categoryId?: number
}

export interface OrganizeApplyResult {
  updatedCount: number
  notes: NoteListItem[]
  message: string
}

export interface AdminBackupInfo {
  lastBackupFileName: string
  lastBackupSize: number
  lastBackupCreatedAt: string
  lastBackupChecksum: string
  dataDirectoryReady: boolean
  databaseFilesPresent: boolean
  indexDirectoryReady: boolean
  vectorIndexDirectoryReady: boolean
  imagesDirectoryReady: boolean
  healthy: boolean
  message: string
}

export interface ConfigCheckItem {
  key: string
  label: string
  configured: boolean
  status: string
  detail: string
}

export interface AdminConfigurationChecklist {
  items: ConfigCheckItem[]
  offlineReady: boolean
  llmReady: boolean
  embeddingReady: boolean
  message: string
}

export interface LlmProviderTestResult {
  provider: string
  model: string
  success: boolean
  message: string
  testedAt: string
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

export interface KnowledgeQaPayload {
  question: string
  provider?: string
  topK?: number
  tag?: string
  category?: string
  language?: string
  status?: NoteStatus
  updatedFrom?: string
  updatedTo?: string
  conversationContext?: string[]
  citationNoteIds?: number[]
  strictMode?: boolean
}

export interface KnowledgeQaCitation {
  noteId: number
  title: string
  snippet: string
  url: string
}

export interface KnowledgeQaResult {
  answer: string
  provider: string
  model: string
  citations: KnowledgeQaCitation[]
}

export interface SimilarNote {
  id: number
  title: string
  summary: string
  similarityScore: number
  reason: string
  source: string
  type: NoteType
  status: NoteStatus
  language?: string
  noteKind?: NoteKind
  category?: SimpleCategory
  tags: Tag[]
  pinned: boolean
  favorite: boolean
  updatedAt: string
}
