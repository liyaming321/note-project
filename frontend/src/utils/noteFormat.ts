import type { NoteType } from '@/types/api'

export type ContentFormatOption = {
  value: NoteType
  label: string
}

export const CONTENT_FORMAT_OPTIONS: ContentFormatOption[] = [
  { value: 'MARKDOWN', label: 'Markdown' },
  { value: 'CODE', label: '代码' },
  { value: 'TEXT', label: '普通文本' }
]

/**
 * 获取内容格式展示名称。
 *
 * @param type 内容格式
 * @param language 代码语言
 * @return 展示名称
 */
export function resolveContentFormatLabel(type?: NoteType, language?: string) {
  if (type === 'CODE') {
    return language?.trim() || '代码'
  }
  if (type === 'TEXT') {
    return '普通文本'
  }
  return 'Markdown'
}

/**
 * 判断是否为普通文本格式。
 *
 * @param type 内容格式
 * @return 是否普通文本
 */
export function isPlainTextFormat(type?: NoteType) {
  return type === 'TEXT'
}
