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
        <button :class="['nav-item', { active: activeNav === 'all' }]" type="button" @click="applyNavFilter('all')">
          <FileTextOutlined />
          <span>所有笔记</span>
        </button>
        <button :class="['nav-item', { active: activeNav === 'recent' }]" type="button" @click="applyNavFilter('recent')">
          <ClockCircleOutlined />
          <span>最近</span>
        </button>
        <button :class="['nav-item', { active: activeNav === 'favorite' }]" type="button" @click="applyNavFilter('favorite')">
          <StarOutlined />
          <span>收藏</span>
        </button>
        <button :class="['nav-item', { active: activeNav === 'archived' }]" type="button" @click="applyNavFilter('archived')">
          <InboxOutlined />
          <span>归档</span>
        </button>

        <div class="sidenav-section">
          <h3>分类</h3>
          <div v-if="categories.length > 0" class="category-links">
            <button
              v-for="category in flatCategories"
              :key="category.id"
              :class="['text-link-row', { active: query.categoryId === category.id }]"
              type="button"
              @click="selectCategory(category.id)"
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
              :class="['workspace-chip', { active: sidebarTag === tag.name }]"
              type="button"
              @click="selectSidebarTag(tag.name)"
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
          <div class="topbar-search">
            <a-select
              v-model:value="searchForm.mode"
              class="search-mode-select"
              @change="handleSearchModeChange"
            >
              <a-select-option value="exact">精确全文</a-select-option>
              <a-select-option value="semantic">语义搜索</a-select-option>
              <a-select-option value="hybrid">混合搜索</a-select-option>
            </a-select>
            <a-input
              v-model:value="searchForm.keyword"
              placeholder="搜索"
              @pressEnter="executeSearch"
            />
            <a-button type="primary" @click="executeSearch">搜索</a-button>
          </div>
        </div>
        <div class="topbar-actions">
          <a-button type="primary" size="large" @click="router.push('/notes/new')">
            <template #icon><PlusOutlined /></template>
            新建笔记
          </a-button>
          <a-button size="large" @click="openKnowledgeQa">
            问知识库
          </a-button>
          <a-dropdown trigger="click">
            <button class="topbar-text-button more-actions-button" type="button">
              更多
              <DownOutlined />
            </button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="import" @click="openImportPicker">导入 Markdown</a-menu-item>
                <a-menu-item key="bookmark" @click="openBookmarkImportPicker">导入书签</a-menu-item>
                <a-menu-item key="link" @click="openLinkImport">导入链接</a-menu-item>
                <a-menu-item key="export" :disabled="exporting" @click="exportCurrentPage">导出当前页</a-menu-item>
                <a-menu-item key="backup" :disabled="backingUp" @click="backupWorkspace">下载备份</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-tooltip title="帮助与快捷键">
            <button class="icon-circle-button" type="button" @click="helpVisible = true">
              <QuestionCircleOutlined />
            </button>
          </a-tooltip>
          <a-tooltip title="回收站">
            <button :class="['icon-circle-button', { active: activeNav === 'trash' }]" type="button" @click="applyNavFilter('trash')">
              <DeleteOutlined />
            </button>
          </a-tooltip>
          <a-tooltip title="设置与维护">
            <button class="icon-circle-button" type="button" @click="openSettings">
              <SettingOutlined />
            </button>
          </a-tooltip>
          <div class="workspace-avatar">知</div>
        </div>
      </header>

      <div class="workspace-canvas">
        <section class="filter-card">
          <div class="filter-card-heading">
            <div>
              <strong>{{ currentViewTitle }}</strong>
              <span>共 {{ pageData.totalElements }} 篇 · {{ activeFilterSummaries.length ? '已筛选' : '未筛选' }}</span>
            </div>
            <button class="filter-toggle-button" type="button" @click="advancedFiltersOpen = !advancedFiltersOpen">
              {{ advancedFiltersOpen ? '收起更多筛选' : '更多筛选' }}
            </button>
          </div>
          <div class="filter-summary-row">
            <span v-if="activeFilterSummaries.length === 0">当前展示 {{ currentViewTitle }}，可通过分类、标签或更多筛选缩小范围。</span>
            <template v-else>
              <span v-for="item in activeFilterSummaries" :key="item" class="filter-summary-pill">{{ item }}</span>
              <button class="filter-clear-button" type="button" @click="copySearchConditions">复制条件</button>
              <button class="filter-clear-button" type="button" @click="resetWorkspace">清除筛选</button>
            </template>
          </div>
          <div class="search-memory-panel">
            <div class="search-memory-heading">
              <span>搜索历史</span>
              <div>
                <a-button type="link" size="small" @click="copySearchConditions">复制当前条件</a-button>
                <a-button type="link" size="small" :disabled="searchHistory.length === 0" @click="clearSearchHistory">
                  清空历史
                </a-button>
              </div>
            </div>
            <div v-if="searchHistory.length > 0" class="search-memory-groups">
              <div class="search-memory-group">
                <small>最近搜索</small>
                <div
                  v-for="item in searchHistory.slice(0, 5)"
                  :key="item.id"
                  class="search-memory-row"
                >
                  <button
                    class="search-memory-chip"
                    type="button"
                    @click="applySearchHistory(item)"
                  >
                    {{ item.label }}
                  </button>
                  <button class="search-memory-favorite" type="button" @click="toggleSearchFavorite(item)">
                    {{ item.favorite ? '取消常用' : '设为常用' }}
                  </button>
                </div>
              </div>
              <div class="search-memory-group">
                <small>常用搜索</small>
                <button
                  v-for="item in favoriteSearches"
                  :key="item.id"
                  class="search-memory-chip favorite"
                  type="button"
                  @click="applySearchHistory(item)"
                >
                  {{ item.label }}
                </button>
                <span v-if="favoriteSearches.length === 0" class="search-memory-empty">点击最近搜索右侧“设为常用”后会展示在这里</span>
              </div>
            </div>
            <div v-else class="search-memory-empty">还没有搜索历史，执行一次搜索后会自动记录。</div>
          </div>
          <div class="filter-grid primary">
            <label class="filter-control">
              <span>分类</span>
              <a-tree-select
                v-model:value="searchForm.category"
                allow-clear
                tree-default-expand-all
                placeholder="搜索分类筛选"
                :tree-data="categoryTreeData"
                @change="executeSearch"
              />
            </label>
            <label class="filter-control">
              <span>标签</span>
              <a-select
                v-model:value="searchForm.tag"
                allow-clear
                show-search
                placeholder="搜索标签筛选"
                :options="tagOptions"
                @change="executeSearch"
              />
            </label>
          </div>
          <div v-if="advancedFiltersOpen" class="advanced-filter-panel">
            <div class="filter-grid secondary">
              <label class="filter-control">
                <span>语言</span>
                <a-select
                  v-model:value="searchForm.language"
                  allow-clear
                  show-search
                  placeholder="语言筛选"
                  :options="languageOptions"
                  @change="executeSearch"
                />
              </label>
              <label class="filter-control">
                <span>类型</span>
                <a-select v-model:value="query.type" allow-clear placeholder="所有格式" @change="loadNotes">
                  <a-select-option value="MARKDOWN">Markdown</a-select-option>
                  <a-select-option value="CODE">代码</a-select-option>
                </a-select>
              </label>
              <label class="filter-control">
                <span>搜索范围</span>
                <a-select
                  v-model:value="searchForm.scope"
                  :disabled="searchForm.mode === 'semantic'"
                  @change="executeSearch"
                >
                  <a-select-option value="all">全部</a-select-option>
                  <a-select-option value="title">标题</a-select-option>
                  <a-select-option value="code">代码</a-select-option>
                </a-select>
              </label>
              <label class="filter-control">
                <span>排序方式</span>
                <a-select v-model:value="query.sort" @change="loadNotes">
                  <a-select-option value="sortOrder">自定义排序</a-select-option>
                  <a-select-option value="updatedAt">更新时间</a-select-option>
                  <a-select-option value="createdAt">创建时间</a-select-option>
                  <a-select-option value="title">标题</a-select-option>
                </a-select>
              </label>
              <label class="filter-control">
                <span>方向</span>
                <a-select v-model:value="query.direction" @change="loadNotes">
                  <a-select-option value="desc">降序</a-select-option>
                  <a-select-option value="asc">升序</a-select-option>
                </a-select>
              </label>
            </div>
            <div class="filter-grid tertiary">
              <label class="filter-control">
                <span>发布状态</span>
                <a-select
                  v-model:value="selectedStatus"
                  allow-clear
                  placeholder="发布状态"
                  @change="handleStatusFilterChange"
                >
                  <a-select-option value="PUBLISHED">已发布</a-select-option>
                  <a-select-option value="DRAFT">草稿</a-select-option>
                </a-select>
              </label>
              <label class="filter-control">
                <span>更新时间</span>
                <a-select v-model:value="updatedRangeMode" @change="applyUpdatedRange">
                  <a-select-option value="all">全部时间</a-select-option>
                  <a-select-option value="today">今天</a-select-option>
                  <a-select-option value="7d">近 7 天</a-select-option>
                  <a-select-option value="30d">近 30 天</a-select-option>
                  <a-select-option value="custom">自定义</a-select-option>
                </a-select>
              </label>
              <label class="filter-control">
                <span>开始日期</span>
                <a-date-picker
                  v-model:value="customUpdatedFrom"
                  :disabled="updatedRangeMode !== 'custom'"
                  value-format="YYYY-MM-DD"
                  placeholder="选择自定义后可用"
                  @change="applyUpdatedRange"
                />
              </label>
              <label class="filter-control">
                <span>结束日期</span>
                <a-date-picker
                  v-model:value="customUpdatedTo"
                  :disabled="updatedRangeMode !== 'custom'"
                  value-format="YYYY-MM-DD"
                  placeholder="选择自定义后可用"
                  @change="applyUpdatedRange"
                />
              </label>
            </div>
            <div class="advanced-filter-actions">
              <a-checkbox v-model:checked="query.pinned" @change="loadNotes">仅看置顶</a-checkbox>
              <a-checkbox v-model:checked="query.favorite" @change="loadNotes">仅收藏</a-checkbox>
              <a-checkbox v-model:checked="query.includeDeleted" :disabled="query.onlyDeleted" @change="loadNotes">包含已删除</a-checkbox>
              <a-button type="link" @click="resetWorkspace">重置全部筛选</a-button>
            </div>
          </div>
        </section>

        <section class="workspace-stats">
          <div class="stat-widget primary">
            <div>
              <p>当前结果</p>
              <strong>{{ pageData.totalElements }} 条笔记</strong>
            </div>
            <BarChartOutlined />
          </div>
          <div class="stat-widget">
            <div>
              <p>视图模式</p>
              <div class="view-mode-buttons">
                <button :class="{ active: viewMode === 'grid' }" type="button" @click="viewMode = 'grid'">
                  <AppstoreOutlined />
                </button>
                <button :class="{ active: viewMode === 'list' }" type="button" @click="viewMode = 'list'">
                  <BarsOutlined />
                </button>
              </div>
            </div>
            <EyeOutlined />
          </div>
          <div class="stat-widget">
            <div>
              <p>关键词</p>
              <div class="keyword-pills">
                <span v-for="keyword in topKeywords" :key="keyword">{{ keyword }}</span>
              </div>
            </div>
            <SearchOutlined />
          </div>
        </section>

        <section v-if="query.onlyDeleted && pageData.items.length > 0" class="trash-toolbar">
          <div>
            <strong>回收站</strong>
            <span>当前页 {{ pageData.items.length }} 篇已删除笔记</span>
          </div>
          <a-button class="trash-restore-button" type="primary" @click="batchRestoreCurrentPage">恢复当前页</a-button>
        </section>

        <a-spin :spinning="loading">
          <div :class="viewMode === 'grid' ? 'note-grid' : 'note-list'">
            <article
              v-for="note in pageData.items"
              :key="note.id"
              :class="[
                'note-card',
                viewMode === 'list' ? 'list-mode' : '',
                {
                  draft: note.status === 'DRAFT',
                  archived: note.archived,
                  deleted: note.deleted,
                  dragging: draggingNoteId === note.id,
                  'drag-over': dragOverNoteId === note.id
                }
              ]"
              :draggable="canDragSort"
              @dragstart="handleDragStart(note)"
              @dragover.prevent="handleDragOver(note)"
              @drop.prevent="handleDrop(note)"
              @dragend="handleDragEnd"
              @click="router.push(`/notes/${note.id}`)"
            >
              <div class="note-card-accent"></div>
              <div class="note-card-header">
                <h3>{{ note.title }}</h3>
                <div class="state-icons">
                  <HolderOutlined v-if="canDragSort" class="drag-handle" @click.stop />
                  <a-tooltip :title="note.pinned ? '取消置顶' : '置顶'">
                    <button class="state-icon-button" type="button" @click.stop="toggleNotePinned(note)">
                      <PushpinFilled v-if="note.pinned" />
                      <PushpinOutlined v-else />
                    </button>
                  </a-tooltip>
                  <a-tooltip :title="note.favorite ? '取消收藏' : '收藏'">
                    <button class="state-icon-button" type="button" @click.stop="toggleNoteFavorite(note)">
                      <StarFilled v-if="note.favorite" />
                      <StarOutlined v-else />
                    </button>
                  </a-tooltip>
                </div>
              </div>
              <p class="note-card-snippet" v-html="renderSnippet(note)"></p>
              <div class="note-tags">
                <span :class="['note-status-pill', note.status === 'DRAFT' ? 'draft' : 'published']">
                  {{ note.status === 'DRAFT' ? '草稿' : '已发布' }}
                </span>
                <span class="note-type-pill">{{ note.type === 'CODE' ? note.language || '代码' : 'Markdown' }}</span>
                <a-tag
                  v-for="tag in note.tags"
                  :key="tag.id"
                  class="blue-tag subtle clickable-tag"
                  @click.stop="searchByTag(tag.name)"
                >
                  #{{ tag.name }}
                </a-tag>
              </div>
              <div v-if="isSearchMode && resolveSearchInsights(note).length > 0" class="search-insight-row">
                <span
                  v-for="insight in resolveSearchInsights(note)"
                  :key="insight"
                  class="search-insight-pill"
                >
                  {{ insight }}
                </span>
              </div>
              <div class="note-card-footer">
                <span><CalendarOutlined /> {{ formatTime(note.updatedAt) }}</span>
                <span v-if="note.category" class="meta-line">{{ note.category.name }}</span>
                <span v-if="note.archived" class="meta-line">已归档</span>
                <span v-if="note.deleted" class="meta-line danger">已删除</span>
                <span v-if="note.hitFields?.length" class="meta-line">命中 {{ formatHitFields(note.hitFields) }}</span>
                <div class="note-card-actions">
                  <template v-if="note.deleted">
                    <a-button type="link" size="small" @click.stop="restoreDeletedNote(note)">恢复</a-button>
                    <a-popconfirm title="永久删除后无法恢复，确认继续？" ok-text="确认" cancel-text="取消" @confirm="permanentRemoveNote(note)">
                      <a-button danger type="link" size="small" @click.stop>永久删除</a-button>
                    </a-popconfirm>
                  </template>
                  <template v-else>
                    <a-button type="link" size="small" @click.stop="toggleNoteStatus(note)">
                      {{ note.status === 'DRAFT' ? '发布' : '转草稿' }}
                    </a-button>
                    <a-button type="link" size="small" @click.stop="toggleNoteArchived(note)">
                      {{ note.archived ? '取消归档' : '归档' }}
                    </a-button>
                  </template>
                </div>
                <ArrowRightOutlined class="note-arrow" />
              </div>
            </article>
          </div>

          <div v-if="pageData.items.length === 0" class="empty-panel">
            <a-empty description="暂无笔记" />
          </div>
          <a-pagination
            v-if="pageData.totalElements > 0"
            class="pager"
            :current="query.page + 1"
            :page-size="query.size"
            :total="pageData.totalElements"
            show-size-changer
            @change="handlePageChange"
          />
        </a-spin>
      </div>
    </main>

    <button class="mobile-fab" type="button" @click="router.push('/notes/new')">
      <PlusOutlined />
    </button>
    <nav class="mobile-bottom-nav">
      <button class="active" type="button" @click="applyNavFilter('all')">
        <FileTextOutlined />
        <span>笔记</span>
      </button>
      <button type="button" @click="applyNavFilter('favorite')">
        <StarOutlined />
        <span>收藏</span>
      </button>
      <button type="button" @click="showTagHint">
        <TagsOutlined />
        <span>标签</span>
      </button>
      <button type="button" @click="openSettings">
        <SettingOutlined />
        <span>设置</span>
      </button>
    </nav>

    <input
      ref="importInputRef"
      class="visually-hidden-file"
      type="file"
      accept=".md,.zip"
      multiple
      @change="handleImportFiles"
    />
    <input
      ref="bookmarkImportInputRef"
      class="visually-hidden-file"
      type="file"
      accept=".html,.htm"
      @change="handleBookmarkImportFile"
    />

    <a-modal v-model:open="categoryManagerVisible" title="分类管理" width="720px" :footer="null">
      <div class="manager-panel">
        <div class="manager-form">
          <a-input v-model:value="categoryForm.name" placeholder="分类名称" />
          <a-tree-select
            v-model:value="categoryForm.parentId"
            allow-clear
            tree-default-expand-all
            :tree-data="categoryTreeDataForNumber"
            placeholder="父级分类，可选"
          />
          <a-button type="primary" :loading="savingCategory" @click="saveCategory">
            {{ editingCategoryId ? '保存分类' : '新建分类' }}
          </a-button>
          <a-button v-if="editingCategoryId" @click="resetCategoryForm">取消编辑</a-button>
        </div>
        <div class="manager-list">
          <div v-for="category in flatCategories" :key="category.id" class="manager-row">
            <span>{{ category.name }}</span>
            <div>
              <a-button type="link" size="small" @click="editCategory(category.id)">重命名</a-button>
              <a-popconfirm
                title="确认删除这个分类？分类下有笔记或子分类时会被后端拦截。"
                ok-text="确认"
                cancel-text="取消"
                @confirm="removeCategory(category.id)"
              >
                <a-button danger type="link" size="small">删除</a-button>
              </a-popconfirm>
            </div>
          </div>
          <a-empty v-if="flatCategories.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无分类" />
        </div>
      </div>
    </a-modal>

    <a-modal v-model:open="importResultVisible" :title="importResultTitle" width="760px" :footer="null">
      <div class="import-result-panel">
        <div class="import-result-heading">
          <div>
            <strong>导入汇总</strong>
            <span>
              成功 {{ importResultItems.filter(item => item.success).length }} 个，
              失败 {{ importResultItems.filter(item => !item.success).length }} 个
            </span>
          </div>
        </div>
        <div class="import-result-list">
          <article
            v-for="item in importResultItems"
            :key="item.id"
            :class="['import-result-item', item.success ? 'success' : 'failed']"
          >
            <div class="import-result-main">
              <span class="import-result-url">{{ item.source }}</span>
              <strong>{{ item.title }}</strong>
              <p>{{ item.message }}</p>
            </div>
            <div class="import-result-actions">
              <a-button
                v-if="item.noteId"
                size="small"
                type="primary"
                @click="router.push(`/notes/${item.noteId}`)"
              >
                查看笔记
              </a-button>
            </div>
          </article>
        </div>
      </div>
    </a-modal>

    <a-modal v-model:open="linkImportVisible" title="导入链接" width="680px" :footer="null">
      <div class="link-import-panel">
        <div class="knowledge-qa-intro">
          <strong>从网页生成新笔记草稿</strong>
          <span>支持一次粘贴多个链接。系统会逐条抓取网页正文，生成预览结果；你可以选择保存为草稿，或进入新建页继续编辑。</span>
        </div>
        <a-textarea
          v-model:value="linkImportText"
          :rows="4"
          :maxlength="8000"
          show-count
          placeholder="每行一个链接，也支持用空格、逗号分隔多个 URL"
        />
        <div class="knowledge-qa-actions">
          <a-select v-model:value="linkImportProvider" class="llm-provider-select">
            <a-select-option
              v-for="provider in llmProviders"
              :key="provider.name"
              :value="provider.name"
            >
              {{ provider.name === 'bailian' ? '阿里百炼' : 'DeepSeek' }}
              {{ provider.configured ? '' : '（未配置）' }}
            </a-select-option>
          </a-select>
          <a-checkbox v-model:checked="linkImportUseLlm">使用 LLM 整理标题、摘要和标签</a-checkbox>
          <a-button type="primary" :loading="linkImporting" @click="importCurrentLink">
            批量抓取并生成预览
          </a-button>
        </div>
        <p class="settings-note">
          使用 LLM 整理时会把网页正文片段发送给所选供应商；关闭后只在本机抓取正文并生成基础草稿，请避免处理敏感网页。
        </p>
        <section v-if="linkImportResults.length > 0" class="import-result-panel">
          <div class="import-result-heading">
            <div>
              <strong>导入结果</strong>
              <span>成功 {{ linkImportSummary.success }} 条，失败 {{ linkImportSummary.failed }} 条</span>
            </div>
            <a-button
              size="small"
              :loading="savingLinkDrafts"
              :disabled="successfulLinkResults.length === 0"
              @click="saveAllLinkDrafts"
            >
              全部保存为草稿
            </a-button>
          </div>
          <div class="import-result-list">
            <article
              v-for="item in linkImportResults"
              :key="item.id"
              :class="['import-result-item', item.status]"
            >
              <div class="import-result-main">
                <span class="import-result-url">{{ item.url }}</span>
                <strong>{{ item.preview?.title || item.message }}</strong>
                <small v-if="item.preview">
                  {{ item.preview.provider === 'crawler' ? '仅抓取正文' : providerLabel(item.preview.provider) }}
                  / {{ item.preview.model }}
                </small>
                <p v-if="item.preview">{{ item.preview.summary }}</p>
                <p v-else>{{ item.message }}</p>
              </div>
              <div class="import-result-actions">
                <a-button
                  v-if="item.status === 'failed'"
                  size="small"
                  :loading="item.retrying"
                  @click="retryLinkImportItem(item)"
                >
                  重试
                </a-button>
                <a-button
                  v-if="item.preview"
                  size="small"
                  :disabled="item.saved"
                  @click="saveLinkPreviewAsDraft(item)"
                >
                  {{ item.saved ? '已保存草稿' : '保存为草稿' }}
                </a-button>
                <a-button v-if="item.preview" size="small" type="primary" @click="openLinkPreviewInEditor(item)">
                  进入新建页
                </a-button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </a-modal>

    <a-modal v-model:open="helpVisible" title="帮助与快捷键" width="680px" :footer="null">
      <div class="help-panel">
        <div>
          <h3>常用操作</h3>
          <p>新建、导入、导出、备份、收藏、置顶、发布、归档和恢复都可以在工作台直接完成。</p>
        </div>
        <div>
          <h3>快捷键</h3>
          <p>编辑页使用 Ctrl / Command + S 保存。详情页代码块右上角可一键复制。</p>
        </div>
        <div>
          <h3>状态说明</h3>
          <p>白色卡片表示已发布，蓝色卡片表示草稿；归档内容默认不出现在所有笔记，回收站展示已删除内容。</p>
        </div>
      </div>
    </a-modal>

    <a-modal v-model:open="qaVisible" title="问知识库" width="820px" :footer="null">
      <div class="knowledge-qa-panel">
        <div class="knowledge-qa-intro">
          <strong>基于当前知识库回答问题</strong>
          <span>系统会先使用混合搜索召回相关笔记，再调用已配置的阿里百炼或 DeepSeek 生成回答，并返回引用来源。</span>
        </div>
        <div class="knowledge-qa-form">
          <a-textarea
            v-model:value="qaQuestion"
            :rows="3"
            :maxlength="500"
            show-count
            placeholder="例如：这个项目的向量索引怎么配置？"
          />
          <div class="knowledge-qa-actions">
            <a-select v-model:value="qaProvider" class="llm-provider-select" placeholder="选择模型供应商">
              <a-select-option
                v-for="provider in llmProviders"
                :key="provider.name"
                :value="provider.name"
              >
                {{ provider.name === 'bailian' ? '阿里百炼' : 'DeepSeek' }}
                {{ provider.configured ? '' : '（未配置）' }}
              </a-select-option>
            </a-select>
            <a-select v-model:value="qaTopK" class="qa-topk-select">
              <a-select-option :value="3">引用 3 篇</a-select-option>
              <a-select-option :value="5">引用 5 篇</a-select-option>
              <a-select-option :value="8">引用 8 篇</a-select-option>
            </a-select>
            <a-button type="primary" :loading="qaLoading" @click="askCurrentQuestion">提问</a-button>
          </div>
          <p class="settings-note">会复用当前搜索筛选条件：{{ buildSearchConditionText() }}</p>
        </div>
        <div class="knowledge-qa-thread">
          <article v-for="item in qaThread" :key="item.id" class="knowledge-qa-answer">
            <div class="knowledge-qa-question">问：{{ item.question }}</div>
            <div v-if="item.error" class="knowledge-qa-error">{{ item.error }}</div>
            <template v-else-if="item.result">
              <p class="knowledge-qa-answer-text">{{ item.result.answer }}</p>
              <div class="knowledge-qa-meta">
                <span>{{ item.result.provider || '未调用模型' }} / {{ item.result.model || '无模型' }}</span>
                <span>{{ item.result.citations.length }} 个引用</span>
              </div>
              <div v-if="item.result.citations.length > 0" class="knowledge-qa-citations">
                <button
                  v-for="citation in item.result.citations"
                  :key="citation.noteId"
                  class="knowledge-qa-citation"
                  type="button"
                  @click="router.push(citation.url)"
                >
                  <strong>{{ citation.title }}</strong>
                  <span>{{ citation.snippet }}</span>
                </button>
              </div>
            </template>
          </article>
          <a-empty v-if="qaThread.length === 0" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有提问" />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { Empty, message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  ArrowRightOutlined,
  BarChartOutlined,
  BarsOutlined,
  CalendarOutlined,
  ClusterOutlined,
  DeleteOutlined,
  EyeOutlined,
  HolderOutlined,
  InboxOutlined,
  PlusOutlined,
  PushpinFilled,
  PushpinOutlined,
  QuestionCircleOutlined,
  SearchOutlined,
  SettingOutlined,
  StarFilled,
  StarOutlined,
  TagsOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  askKnowledgeBase,
  batchRestoreNotes,
  changeArchived,
  changeFavorite,
  changePinned,
  changeStatus,
  createCategory,
  createNote,
  deleteCategory,
  exportBackup,
  exportNotesZip,
  fetchCategories,
  fetchLlmProviders,
  fetchNotes,
  fetchTags,
  importBookmarks,
  importLink,
  importLinks,
  importMarkdown,
  permanentlyDeleteNote,
  reorderNotes,
  searchNotes,
  updateCategory
} from '@/api/knowledgeBase'
import type {
  BatchLinkImportItem,
  Category,
  KnowledgeQaResult,
  LinkImportDraft,
  LinkImportPreview,
  LlmProviderInfo,
  NoteListItem,
  NoteQuery,
  NoteStatus,
  PageResponse,
  SearchQuery,
  SearchMode,
  SearchResult,
  SearchScope,
  Tag
} from '@/types/api'

const router = useRouter()
const route = useRoute()

type CategoryTreeNode = {
  title: string
  key: number
  value: string
  children: CategoryTreeNode[]
}

type CategoryTreeNumberNode = {
  title: string
  key: number
  value: number
  children: CategoryTreeNumberNode[]
}

type NoteCardItem = NoteListItem & Partial<SearchResult>
type ViewMode = 'grid' | 'list'
type NavFilter = 'all' | 'recent' | 'favorite' | 'archived' | 'trash'
type UpdatedRangeMode = 'all' | 'today' | '7d' | '30d' | 'custom'
type SearchHistoryItem = {
  id: string
  label: string
  keyword: string
  mode: SearchMode
  scope: SearchScope
  tag?: string
  category?: string
  language?: string
  status?: NoteStatus
  updatedFrom?: string
  updatedTo?: string
  favorite: boolean
  createdAt: string
}
type KnowledgeQaThreadItem = {
  id: string
  question: string
  result?: KnowledgeQaResult
  error?: string
}
type LinkImportResultItem = {
  id: string
  url: string
  status: 'success' | 'failed'
  message: string
  preview?: LinkImportPreview
  saved: boolean
  retrying: boolean
}
type ImportResultItem = {
  id: string
  title: string
  source: string
  success: boolean
  message: string
  noteId?: number
}

const SEARCH_HISTORY_STORAGE_KEY = 'people-wiki-search-history'
const LINK_IMPORT_DRAFT_PREFIX = 'people-wiki-link-import-draft:'

const loading = ref(false)
const exporting = ref(false)
const backingUp = ref(false)
const viewMode = ref<ViewMode>('grid')
const navMode = ref<NavFilter>('all')
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const importInputRef = ref<HTMLInputElement>()
const bookmarkImportInputRef = ref<HTMLInputElement>()
const draggingNoteId = ref<number>()
const dragOverNoteId = ref<number>()
const selectedStatus = ref<NoteStatus>()
const updatedRangeMode = ref<UpdatedRangeMode>('all')
const customUpdatedFrom = ref<string>()
const customUpdatedTo = ref<string>()
const advancedFiltersOpen = ref(false)
const categoryManagerVisible = ref(false)
const helpVisible = ref(false)
const importResultVisible = ref(false)
const importResultTitle = ref('导入结果')
const importResultItems = ref<ImportResultItem[]>([])
const linkImportVisible = ref(false)
const linkImporting = ref(false)
const savingLinkDrafts = ref(false)
const linkImportText = ref('')
const linkImportProvider = ref<'bailian' | 'deepseek'>('bailian')
const linkImportUseLlm = ref(true)
const linkImportResults = ref<LinkImportResultItem[]>([])
const qaVisible = ref(false)
const qaLoading = ref(false)
const qaQuestion = ref('')
const qaProvider = ref<'bailian' | 'deepseek'>('bailian')
const qaTopK = ref(5)
const qaThread = ref<KnowledgeQaThreadItem[]>([])
const llmProviders = ref<LlmProviderInfo[]>([])
const searchHistory = ref<SearchHistoryItem[]>([])
const savingCategory = ref(false)
const editingCategoryId = ref<number>()
const categoryForm = reactive<{
  name: string
  parentId?: number
}>({
  name: ''
})
const pageData = ref<PageResponse<NoteCardItem>>({
  items: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true
})
const query = reactive<Required<Pick<NoteQuery, 'page' | 'size' | 'sort' | 'direction'>> & NoteQuery>({
  page: 0,
  size: 10,
  sort: 'updatedAt',
  direction: 'desc'
})
const searchForm = reactive<{
  keyword: string
  mode: SearchMode
  scope: SearchScope
  tag?: string
  category?: string
  language?: string
}>({
  keyword: '',
  mode: 'exact',
  scope: 'all'
})
const sidebarTag = ref<string>()

const categoryTreeData = computed(() => toTreeData(categories.value))
const categoryTreeDataForNumber = computed(() => toTreeNumberData(categories.value))
const flatCategories = computed(() => flattenCategories(categories.value))
const tagOptions = computed(() => tags.value.map((tag: Tag) => ({ value: tag.name, label: tag.name })))
const languageOptions = computed(() =>
  [
    'java',
    'javascript',
    'typescript',
    'python',
    'sql',
    'go',
    'shell',
    'markdown'
  ].map(language => ({ value: language, label: language }))
)
const isSearchMode = computed(() => {
  return (
    route.query.mode === 'search' ||
    searchForm.keyword.trim().length > 0 ||
    Boolean(searchForm.tag || searchForm.category || searchForm.language)
  )
})
const canDragSort = computed(() => !isSearchMode.value && !query.includeDeleted)
const activeNav = computed<NavFilter>(() => {
  if (query.onlyDeleted) {
    return 'trash'
  }
  if (query.archived) {
    return 'archived'
  }
  if (query.favorite) {
    return 'favorite'
  }
  return navMode.value
})
const currentViewTitle = computed(() => {
  if (activeNav.value === 'recent') {
    return '最近笔记'
  }
  if (activeNav.value === 'favorite') {
    return '收藏笔记'
  }
  if (activeNav.value === 'archived') {
    return '归档笔记'
  }
  if (activeNav.value === 'trash') {
    return '回收站'
  }
  return isSearchMode.value ? '搜索结果' : '所有笔记'
})
const activeFilterSummaries = computed(() => {
  const summaries: string[] = []
  if (searchForm.keyword.trim()) {
    summaries.push(`关键词：${searchForm.keyword.trim()}`)
  }
  if (isSearchMode.value) {
    summaries.push(`模式：${resolveSearchModeLabel(searchForm.mode)}`)
  }
  const categoryName = resolveSelectedCategoryName()
  if (categoryName) {
    summaries.push(`分类：${categoryName}`)
  }
  const selectedTag = searchForm.tag || query.tag
  if (selectedTag) {
    summaries.push(`标签：${selectedTag}`)
  }
  if (searchForm.language) {
    summaries.push(`语言：${searchForm.language}`)
  }
  if (query.type) {
    summaries.push(`类型：${query.type === 'CODE' ? '代码' : 'Markdown'}`)
  }
  if (selectedStatus.value) {
    summaries.push(`状态：${selectedStatus.value === 'DRAFT' ? '草稿' : '已发布'}`)
  }
  if (query.pinned) {
    summaries.push('仅置顶')
  }
  if (query.favorite && activeNav.value !== 'favorite') {
    summaries.push('仅收藏')
  }
  if (query.updatedFrom || query.updatedTo) {
    summaries.push(`更新：${query.updatedFrom || '不限'} 至 ${query.updatedTo || '不限'}`)
  }
  if (searchForm.scope !== 'all') {
    summaries.push(`范围：${searchForm.scope === 'title' ? '标题' : '代码'}`)
  }
  if (query.sort !== 'updatedAt' || query.direction !== 'desc') {
    summaries.push(`排序：${resolveSortLabel(query.sort)} / ${query.direction === 'asc' ? '升序' : '降序'}`)
  }
  if (query.includeDeleted && !query.onlyDeleted) {
    summaries.push('包含已删除')
  }
  return summaries
})
const topKeywords = computed(() => {
  const selectedKeywords = [
    searchForm.keyword.trim(),
    searchForm.tag,
    searchForm.language,
    query.tag
  ].filter((keyword): keyword is string => Boolean(keyword))
  if (selectedKeywords.length > 0) {
    return selectedKeywords.slice(0, 3)
  }
  const noteTagNames = pageData.value.items
    .flatMap(note => note.tags.map(tag => tag.name))
    .filter(Boolean)
  return Array.from(new Set(noteTagNames)).slice(0, 3).concat(noteTagNames.length === 0 ? ['全部'] : [])
})
const favoriteSearches = computed(() => searchHistory.value.filter(item => item.favorite).slice(0, 5))
const linkImportSummary = computed(() => ({
  success: linkImportResults.value.filter(item => item.status === 'success').length,
  failed: linkImportResults.value.filter(item => item.status === 'failed').length
}))
const successfulLinkResults = computed(() => linkImportResults.value.filter(item => item.preview && !item.saved))

onMounted(async () => {
  loadSearchHistory()
  syncQueryFromRoute()
  await Promise.all([loadCategories(), loadTags(), loadLlmProviders(), loadNotes()])
})

watch(
  () => route.query,
  async () => {
    syncQueryFromRoute()
    await loadNotes()
  }
)

async function loadNotes() {
  loading.value = true
  try {
    if (isSearchMode.value) {
      const searchQuery: SearchQuery = {
        q: searchForm.keyword.trim() || undefined,
        searchMode: searchForm.mode,
        scope: searchForm.scope,
        tag: searchForm.tag || undefined,
        category: searchForm.category || undefined,
        language: searchForm.language || undefined,
        status: selectedStatus.value,
        updatedFrom: query.updatedFrom,
        updatedTo: query.updatedTo,
        page: query.page,
        size: query.size
      }
      if (searchForm.mode === 'semantic' && !searchForm.keyword.trim()) {
        pageData.value = createEmptyPage(query.page, query.size)
        return
      }
      pageData.value = (await searchNotes(searchQuery)) as PageResponse<NoteCardItem>
      return
    }
    pageData.value = (await fetchNotes({
      ...query,
      favorite: query.favorite || undefined,
      pinned: query.pinned || undefined,
      archived: query.archived,
      includeDeleted: query.includeDeleted || undefined,
      onlyDeleted: query.onlyDeleted || undefined,
      status: selectedStatus.value,
      updatedFrom: query.updatedFrom,
      updatedTo: query.updatedTo
    })) as PageResponse<NoteCardItem>
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categories.value = await fetchCategories()
}

async function loadTags() {
  tags.value = await fetchTags()
}

async function loadLlmProviders() {
  try {
    llmProviders.value = await fetchLlmProviders()
    const configuredProvider = llmProviders.value.find(provider => provider.configured)
    if (configuredProvider) {
      qaProvider.value = configuredProvider.name
      linkImportProvider.value = configuredProvider.name
    }
  } catch {
    llmProviders.value = [
      { name: 'bailian', model: '', configured: false },
      { name: 'deepseek', model: '', configured: false }
    ]
  }
}

async function handleSearchModeChange(value: SearchMode) {
  searchForm.mode = value
  if (value === 'semantic') {
    searchForm.scope = 'all'
  }
  await executeSearch()
}

function showTagHint() {
  message.info('请选择侧边栏标签云中的标签进行筛选')
}

function selectCategory(categoryId: number) {
  if (isSearchMode.value) {
    searchForm.category = String(categoryId)
    void executeSearch()
    return
  }
  query.categoryId = query.categoryId === categoryId ? undefined : categoryId
  query.page = 0
  void loadNotes()
}

function selectSidebarTag(tagName: string) {
  sidebarTag.value = sidebarTag.value === tagName ? undefined : tagName
  if (isSearchMode.value) {
    searchForm.tag = sidebarTag.value
    void executeSearch()
    return
  }
  query.tag = sidebarTag.value
  query.page = 0
  void loadNotes()
}

async function executeSearch() {
  if (searchForm.mode === 'semantic' && !searchForm.keyword.trim()) {
    message.warning('语义搜索需要输入自然语言问题')
    return
  }
  query.page = 0
  recordSearchHistory()
  await router.replace({
    path: '/',
    query: buildSearchRouteQuery()
  })
  await loadNotes()
}

function createEmptyPage(page: number, size: number): PageResponse<NoteCardItem> {
  return {
    items: [],
    page,
    size,
    totalElements: 0,
    totalPages: 0,
    first: page === 0,
    last: true
  }
}

async function clearSearch() {
  clearSearchState()
  await router.replace({ path: '/', query: {} })
  await loadNotes()
}

function loadSearchHistory() {
  try {
    const rawValue = window.localStorage.getItem(SEARCH_HISTORY_STORAGE_KEY)
    const parsedValue = rawValue ? JSON.parse(rawValue) : []
    searchHistory.value = Array.isArray(parsedValue)
      ? parsedValue.filter(isSearchHistoryItem).slice(0, 20)
      : []
  } catch {
    searchHistory.value = []
  }
}

function persistSearchHistory() {
  window.localStorage.setItem(SEARCH_HISTORY_STORAGE_KEY, JSON.stringify(searchHistory.value.slice(0, 20)))
}

function recordSearchHistory() {
  const snapshot = currentSearchSnapshot()
  if (!hasMeaningfulSearchSnapshot(snapshot)) {
    return
  }
  const existingItem = searchHistory.value.find(item => sameSearchSnapshot(item, snapshot))
  const nextItem: SearchHistoryItem = {
    ...snapshot,
    id: existingItem?.id ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    label: buildHistoryLabel(snapshot),
    favorite: existingItem?.favorite ?? false,
    createdAt: new Date().toISOString()
  }
  searchHistory.value = [
    nextItem,
    ...searchHistory.value.filter(item => item.id !== nextItem.id)
  ].slice(0, 20)
  persistSearchHistory()
}

async function applySearchHistory(item: SearchHistoryItem) {
  searchForm.keyword = item.keyword
  searchForm.mode = item.mode
  searchForm.scope = item.scope
  searchForm.tag = item.tag
  searchForm.category = item.category
  searchForm.language = item.language
  selectedStatus.value = item.status
  query.updatedFrom = item.updatedFrom
  query.updatedTo = item.updatedTo
  query.page = 0
  await router.replace({ path: '/', query: buildSearchRouteQuery() })
  await loadNotes()
}

function toggleSearchFavorite(item: SearchHistoryItem) {
  searchHistory.value = searchHistory.value.map(historyItem =>
    historyItem.id === item.id ? { ...historyItem, favorite: !historyItem.favorite } : historyItem
  )
  persistSearchHistory()
}

function clearSearchHistory() {
  searchHistory.value = []
  persistSearchHistory()
  message.success('搜索历史已清空')
}

async function copySearchConditions() {
  const text = buildSearchConditionText()
  try {
    await navigator.clipboard.writeText(text)
    message.success('搜索条件已复制')
  } catch {
    message.error('复制失败，请检查浏览器剪贴板权限')
  }
}

function openKnowledgeQa() {
  qaVisible.value = true
  if (!qaQuestion.value.trim() && searchForm.keyword.trim()) {
    qaQuestion.value = searchForm.keyword.trim()
  }
}

async function askCurrentQuestion() {
  const question = qaQuestion.value.trim()
  if (!question) {
    message.warning('请输入要提问的问题')
    return
  }
  qaLoading.value = true
  try {
    const result = await askKnowledgeBase({
      question,
      provider: qaProvider.value,
      topK: qaTopK.value,
      tag: searchForm.tag || query.tag,
      category: searchForm.category || (query.categoryId ? String(query.categoryId) : undefined),
      language: searchForm.language,
      status: selectedStatus.value,
      updatedFrom: query.updatedFrom,
      updatedTo: query.updatedTo
    })
    qaThread.value = [
      { id: `${Date.now()}`, question, result },
      ...qaThread.value
    ]
    qaQuestion.value = ''
  } catch (error) {
    qaThread.value = [
      { id: `${Date.now()}`, question, error: (error as Error).message },
      ...qaThread.value
    ]
  } finally {
    qaLoading.value = false
  }
}

async function resetWorkspace() {
  navMode.value = 'all'
  clearSearchState()
  query.page = 0
  query.size = 10
  query.sort = 'updatedAt'
  query.direction = 'desc'
  query.categoryId = undefined
  query.tag = undefined
  query.type = undefined
  selectedStatus.value = undefined
  query.favorite = undefined
  query.pinned = undefined
  query.includeDeleted = undefined
  query.onlyDeleted = undefined
  query.archived = undefined
  query.updatedFrom = undefined
  query.updatedTo = undefined
  updatedRangeMode.value = 'all'
  customUpdatedFrom.value = undefined
  customUpdatedTo.value = undefined
  await router.replace({ path: '/', query: {} })
  await loadNotes()
}

async function applyNavFilter(filter: NavFilter) {
  navMode.value = filter
  clearSearchState()
  query.page = 0
  query.size = 10
  query.categoryId = undefined
  query.tag = undefined
  query.type = undefined
  selectedStatus.value = undefined
  query.favorite = undefined
  query.pinned = undefined
  query.includeDeleted = undefined
  query.onlyDeleted = undefined
  query.archived = undefined
  query.updatedFrom = undefined
  query.updatedTo = undefined
  updatedRangeMode.value = 'all'
  customUpdatedFrom.value = undefined
  customUpdatedTo.value = undefined
  query.sort = 'updatedAt'
  query.direction = 'desc'
  if (filter === 'favorite') {
    query.favorite = true
  }
  if (filter === 'archived') {
    query.archived = true
  }
  if (filter === 'trash') {
    query.onlyDeleted = true
  }
  await router.replace({ path: '/', query: {} })
  await loadNotes()
}

function clearSearchState() {
  searchForm.keyword = ''
  searchForm.mode = 'exact'
  searchForm.scope = 'all'
  searchForm.tag = undefined
  searchForm.category = undefined
  searchForm.language = undefined
  sidebarTag.value = undefined
  query.page = 0
  query.tag = undefined
  query.categoryId = undefined
}

async function handleStatusFilterChange(value?: NoteStatus) {
  selectedStatus.value = value
  query.page = 0
  if (isSearchMode.value) {
    await router.replace({ path: '/', query: buildSearchRouteQuery() })
  }
  await loadNotes()
}

async function applyUpdatedRange() {
  const today = new Date()
  if (updatedRangeMode.value === 'all') {
    query.updatedFrom = undefined
    query.updatedTo = undefined
    customUpdatedFrom.value = undefined
    customUpdatedTo.value = undefined
  } else if (updatedRangeMode.value === 'today') {
    const todayText = toDateText(today)
    query.updatedFrom = todayText
    query.updatedTo = todayText
    customUpdatedFrom.value = undefined
    customUpdatedTo.value = undefined
  } else if (updatedRangeMode.value === '7d') {
    query.updatedFrom = toDateText(addDays(today, -6))
    query.updatedTo = toDateText(today)
    customUpdatedFrom.value = undefined
    customUpdatedTo.value = undefined
  } else if (updatedRangeMode.value === '30d') {
    query.updatedFrom = toDateText(addDays(today, -29))
    query.updatedTo = toDateText(today)
    customUpdatedFrom.value = undefined
    customUpdatedTo.value = undefined
  } else {
    query.updatedFrom = customUpdatedFrom.value
    query.updatedTo = customUpdatedTo.value
  }
  query.page = 0
  if (isSearchMode.value) {
    await router.replace({ path: '/', query: buildSearchRouteQuery() })
  }
  await loadNotes()
}

function openImportPicker() {
  importInputRef.value?.click()
}

function openBookmarkImportPicker() {
  bookmarkImportInputRef.value?.click()
}

function openLinkImport() {
  linkImportVisible.value = true
  if (llmProviders.value.length === 0) {
    void loadLlmProviders()
  }
}

async function importCurrentLink() {
  const urls = parseLinkImportUrls(linkImportText.value)
  if (urls.length === 0) {
    message.warning('请输入要导入的网页链接')
    return
  }
  linkImporting.value = true
  try {
    const result = await importLinks({
      urls,
      provider: linkImportProvider.value,
      useLlm: linkImportUseLlm.value
    })
    linkImportResults.value = result.items.map(item => toLinkImportResultItem(
      item.url,
      item.success,
      item.message,
      item.preview
    ))
    const failedText = linkImportSummary.value.failed > 0 ? `，失败 ${linkImportSummary.value.failed} 条` : ''
    message.success(`链接预览完成 ${linkImportSummary.value.success} 条${failedText}`)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    linkImporting.value = false
  }
}

async function retryLinkImportItem(item: LinkImportResultItem) {
  item.retrying = true
  try {
    const preview = await importLink({
      url: item.url,
      provider: linkImportProvider.value,
      useLlm: linkImportUseLlm.value
    })
    Object.assign(item, toLinkImportResultItem(item.url, true, '链接解析完成', preview))
    message.success('链接已重新生成预览')
  } catch (error) {
    item.status = 'failed'
    item.message = (error as Error).message
    item.preview = undefined
    message.error((error as Error).message)
  } finally {
    item.retrying = false
  }
}

async function saveAllLinkDrafts() {
  const items = successfulLinkResults.value
  if (items.length === 0) {
    message.warning('没有可保存的链接预览')
    return
  }
  savingLinkDrafts.value = true
  try {
    for (const item of items) {
      await saveLinkPreviewAsDraft(item, false)
    }
    await Promise.all([loadCategories(), loadTags(), loadNotes()])
    message.success(`已保存 ${items.length} 条链接草稿`)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    savingLinkDrafts.value = false
  }
}

async function saveLinkPreviewAsDraft(item: LinkImportResultItem, refresh = true) {
  if (!item.preview) {
    return
  }
  const preview = item.preview
  const createdNote = await createNote({
    title: preview.title,
    content: preview.content,
    summary: preview.summary,
    type: 'MARKDOWN',
    status: 'DRAFT',
    language: 'markdown',
    categoryId: preview.categoryId,
    tags: preview.tags,
    favorite: false,
    pinned: false
  })
  item.saved = true
  item.message = `已保存为草稿：${createdNote.title}`
  if (refresh) {
    await Promise.all([loadCategories(), loadTags(), loadNotes()])
    message.success('已保存为草稿')
  }
}

async function openLinkPreviewInEditor(item: LinkImportResultItem) {
  if (!item.preview) {
    return
  }
  const draftId = saveLinkPreviewToLocalDraft(item.preview)
  linkImportVisible.value = false
  message.success('链接已整理，正在进入新建笔记预览')
  await router.push({ path: '/notes/new', query: { draftId } })
}

function parseLinkImportUrls(value: string) {
  return Array.from(new Set(value
    .split(/[\s,，]+/)
    .map(item => item.trim())
    .filter(Boolean)
  )).slice(0, 20)
}

function toLinkImportResultItem(
  url: string,
  success: boolean,
  messageText: string,
  preview?: LinkImportPreview
): LinkImportResultItem {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    url,
    status: success ? 'success' : 'failed',
    message: messageText,
    preview,
    saved: false,
    retrying: false
  }
}

function saveLinkPreviewToLocalDraft(preview: LinkImportPreview) {
  const draftId = `${Date.now()}-${Math.random().toString(16).slice(2)}`
  const draft: LinkImportDraft = {
    ...preview,
    createdAt: new Date().toISOString()
  }
  window.localStorage.setItem(`${LINK_IMPORT_DRAFT_PREFIX}${draftId}`, JSON.stringify(draft))
  return draftId
}

function providerLabel(provider: string) {
  return provider === 'deepseek' ? 'DeepSeek' : provider === 'bailian' ? '阿里百炼' : '网页抓取'
}

async function handleImportFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length === 0) {
    return
  }
  loading.value = true
  try {
    const result = await importMarkdown(files)
    await Promise.all([loadCategories(), loadTags(), loadNotes()])
    showImportResult(
      'Markdown 导入结果',
      result.items.map(item => ({
        id: `${item.fileName}-${Math.random().toString(16).slice(2)}`,
        title: item.title || item.fileName,
        source: item.fileName,
        success: item.success,
        message: item.message,
        noteId: item.noteId
      }))
    )
    const failedText = result.failedCount > 0 ? `，失败 ${result.failedCount} 个` : ''
    message.success(`导入成功 ${result.importedCount} 个${failedText}`)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function exportCurrentPage() {
  const noteIds = pageData.value.items.filter(note => !note.deleted).map(note => note.id)
  if (noteIds.length === 0) {
    message.warning('当前页没有可导出的笔记')
    return
  }
  exporting.value = true
  try {
    await exportNotesZip(noteIds)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    exporting.value = false
  }
}

async function handleBookmarkImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  loading.value = true
  try {
    const result = await importBookmarks(file)
    await Promise.all([loadCategories(), loadTags(), loadNotes()])
    showImportResult(
      '书签导入结果',
      result.items.map(item => ({
        id: `${item.url}-${Math.random().toString(16).slice(2)}`,
        title: item.title,
        source: item.url,
        success: item.success,
        message: item.message,
        noteId: item.noteId
      }))
    )
    const failedText = result.failedCount > 0 ? `，失败 ${result.failedCount} 个` : ''
    message.success(`书签导入成功 ${result.importedCount} 个${failedText}`)
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function showImportResult(title: string, items: ImportResultItem[]) {
  importResultTitle.value = title
  importResultItems.value = items
  importResultVisible.value = true
}

async function backupWorkspace() {
  backingUp.value = true
  try {
    await exportBackup()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    backingUp.value = false
  }
}

async function toggleNoteFavorite(note: NoteCardItem) {
  try {
    const updatedNote = await changeFavorite(note.id, !note.favorite)
    updateNoteInCurrentPage(updatedNote)
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function toggleNotePinned(note: NoteCardItem) {
  try {
    const updatedNote = await changePinned(note.id, !note.pinned)
    updateNoteInCurrentPage(updatedNote)
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function toggleNoteStatus(note: NoteCardItem) {
  try {
    const nextStatus: NoteStatus = note.status === 'DRAFT' ? 'PUBLISHED' : 'DRAFT'
    const updatedNote = await changeStatus(note.id, nextStatus)
    updateNoteInCurrentPage(updatedNote)
    message.success(nextStatus === 'PUBLISHED' ? '已发布' : '已转为草稿')
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function toggleNoteArchived(note: NoteCardItem) {
  try {
    const updatedNote = await changeArchived(note.id, !note.archived)
    if (activeNav.value === 'archived' || (!updatedNote.archived && query.archived)) {
      await loadNotes()
      return
    }
    if (updatedNote.archived && !query.archived) {
      pageData.value = {
        ...pageData.value,
        totalElements: Math.max(pageData.value.totalElements - 1, 0),
        items: pageData.value.items.filter(item => item.id !== note.id)
      }
      return
    }
    updateNoteInCurrentPage(updatedNote)
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function restoreDeletedNote(note: NoteCardItem) {
  try {
    await batchRestoreNotes([note.id])
    message.success('已恢复')
    await loadNotes()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function permanentRemoveNote(note: NoteCardItem) {
  try {
    await permanentlyDeleteNote(note.id)
    message.success('已永久删除')
    await loadNotes()
  } catch (error) {
    message.error((error as Error).message)
  }
}

async function batchRestoreCurrentPage() {
  const noteIds = pageData.value.items.filter(note => note.deleted).map(note => note.id)
  if (noteIds.length === 0) {
    message.warning('当前页没有可恢复的笔记')
    return
  }
  try {
    await batchRestoreNotes(noteIds)
    message.success(`已恢复 ${noteIds.length} 篇笔记`)
    await loadNotes()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function updateNoteInCurrentPage(updatedNote: NoteListItem) {
  pageData.value = {
    ...pageData.value,
    items: pageData.value.items.map(note => note.id === updatedNote.id ? { ...note, ...updatedNote } : note)
  }
}

function handleDragStart(note: NoteCardItem) {
  if (!canDragSort.value) {
    return
  }
  draggingNoteId.value = note.id
  dragOverNoteId.value = undefined
}

function handleDragOver(note: NoteCardItem) {
  if (!canDragSort.value || draggingNoteId.value === undefined || draggingNoteId.value === note.id) {
    return
  }
  dragOverNoteId.value = note.id
}

async function handleDrop(targetNote: NoteCardItem) {
  if (!canDragSort.value || draggingNoteId.value === undefined || draggingNoteId.value === targetNote.id) {
    handleDragEnd()
    return
  }
  const sourceIndex = pageData.value.items.findIndex(note => note.id === draggingNoteId.value)
  const targetIndex = pageData.value.items.findIndex(note => note.id === targetNote.id)
  if (sourceIndex < 0 || targetIndex < 0) {
    handleDragEnd()
    return
  }
  const reorderedItems = [...pageData.value.items]
  const [movedItem] = reorderedItems.splice(sourceIndex, 1)
  reorderedItems.splice(targetIndex, 0, movedItem)
  pageData.value = { ...pageData.value, items: reorderedItems }
  query.sort = 'sortOrder'
  query.direction = 'asc'
  try {
    await reorderNotes(reorderedItems.map(note => note.id))
    message.success('排序已保存')
  } catch (error) {
    message.error((error as Error).message)
    await loadNotes()
  } finally {
    handleDragEnd()
  }
}

function handleDragEnd() {
  draggingNoteId.value = undefined
  dragOverNoteId.value = undefined
}

function searchByTag(tagName: string) {
  searchForm.keyword = ''
  searchForm.mode = 'exact'
  searchForm.scope = 'all'
  searchForm.tag = tagName
  searchForm.category = undefined
  searchForm.language = undefined
  selectedStatus.value = undefined
  query.page = 0
  void executeSearch()
}

function syncQueryFromRoute() {
  const routeMode = typeof route.query.mode === 'string' ? route.query.mode : ''
  const routeView = route.query.view
  const routeSearchMode = route.query.searchMode
  const routeKeyword = route.query.q
  const routeScope = route.query.scope
  const routeTag = route.query.tag
  const routeCategory = route.query.category
  const routeLanguage = route.query.language
  const routeStatus = route.query.status
  const routeUpdatedFrom = route.query.updatedFrom
  const routeUpdatedTo = route.query.updatedTo
  const routePage = route.query.page
  const routeSize = route.query.size

  query.page = parseNonNegativeInteger(routePage, 0)
  query.size = parsePositiveInteger(routeSize, 10)

  if (routeMode === 'search') {
    searchForm.keyword = typeof routeKeyword === 'string' ? routeKeyword.trim() : ''
    searchForm.mode = isSearchModeValue(routeSearchMode) ? routeSearchMode : 'exact'
    searchForm.scope = isSearchScope(routeScope) ? routeScope : 'all'
    searchForm.tag = typeof routeTag === 'string' ? routeTag.trim() : undefined
    searchForm.category = typeof routeCategory === 'string' ? routeCategory.trim() : undefined
    searchForm.language = typeof routeLanguage === 'string' ? routeLanguage.trim() : undefined
    selectedStatus.value = isNoteStatus(routeStatus) ? routeStatus : undefined
    query.updatedFrom = typeof routeUpdatedFrom === 'string' ? routeUpdatedFrom : undefined
    query.updatedTo = typeof routeUpdatedTo === 'string' ? routeUpdatedTo : undefined
    query.tag = undefined
  } else {
    searchForm.keyword = ''
    searchForm.mode = 'exact'
    searchForm.scope = 'all'
    searchForm.tag = undefined
    searchForm.category = undefined
    searchForm.language = undefined
    selectedStatus.value = undefined
    query.tag = typeof routeTag === 'string' ? routeTag.trim() : undefined
    applyRouteView(routeView)
  }
  sidebarTag.value = typeof routeTag === 'string' ? routeTag.trim() : undefined
}

function applyRouteView(value: unknown) {
  if (!isNavFilter(value)) {
    navMode.value = 'all'
    query.favorite = undefined
    query.archived = undefined
    query.onlyDeleted = undefined
    query.includeDeleted = undefined
    return
  }
  navMode.value = value
  query.favorite = value === 'favorite' || undefined
  query.archived = value === 'archived' || undefined
  query.onlyDeleted = value === 'trash' || undefined
  query.includeDeleted = undefined
}

function buildSearchRouteQuery() {
  const queryObject: Record<string, string> = {
    mode: 'search'
  }
  if (searchForm.keyword.trim()) {
    queryObject.q = searchForm.keyword.trim()
  }
  if (searchForm.mode !== 'exact') {
    queryObject.searchMode = searchForm.mode
  }
  if (searchForm.scope !== 'all') {
    queryObject.scope = searchForm.scope
  }
  if (searchForm.tag) {
    queryObject.tag = searchForm.tag
  }
  if (searchForm.category) {
    queryObject.category = searchForm.category
  }
  if (searchForm.language) {
    queryObject.language = searchForm.language
  }
  if (selectedStatus.value) {
    queryObject.status = selectedStatus.value
  }
  if (query.updatedFrom) {
    queryObject.updatedFrom = query.updatedFrom
  }
  if (query.updatedTo) {
    queryObject.updatedTo = query.updatedTo
  }
  if (query.page > 0) {
    queryObject.page = String(query.page)
  }
  if (query.size !== 10) {
    queryObject.size = String(query.size)
  }
  return queryObject
}

function currentSearchSnapshot() {
  return {
    keyword: searchForm.keyword.trim(),
    mode: searchForm.mode,
    scope: searchForm.scope,
    tag: searchForm.tag || query.tag,
    category: searchForm.category || (query.categoryId ? String(query.categoryId) : undefined),
    language: searchForm.language,
    status: selectedStatus.value,
    updatedFrom: query.updatedFrom,
    updatedTo: query.updatedTo
  }
}

function hasMeaningfulSearchSnapshot(snapshot: ReturnType<typeof currentSearchSnapshot>) {
  return Boolean(
    snapshot.keyword ||
    snapshot.tag ||
    snapshot.category ||
    snapshot.language ||
    snapshot.status ||
    snapshot.updatedFrom ||
    snapshot.updatedTo
  )
}

function sameSearchSnapshot(item: SearchHistoryItem, snapshot: ReturnType<typeof currentSearchSnapshot>) {
  return item.keyword === snapshot.keyword &&
    item.mode === snapshot.mode &&
    item.scope === snapshot.scope &&
    item.tag === snapshot.tag &&
    item.category === snapshot.category &&
    item.language === snapshot.language &&
    item.status === snapshot.status &&
    item.updatedFrom === snapshot.updatedFrom &&
    item.updatedTo === snapshot.updatedTo
}

function buildHistoryLabel(snapshot: ReturnType<typeof currentSearchSnapshot>) {
  const parts = [
    snapshot.keyword || '无关键词',
    resolveSearchModeLabel(snapshot.mode),
    snapshot.tag ? `#${snapshot.tag}` : '',
    snapshot.category ? resolveCategoryLabel(snapshot.category) : '',
    snapshot.language || '',
    snapshot.status ? (snapshot.status === 'DRAFT' ? '草稿' : '已发布') : ''
  ].filter(Boolean)
  return parts.slice(0, 4).join(' · ')
}

function buildSearchConditionText() {
  const snapshot = currentSearchSnapshot()
  const lines = [
    `关键词：${snapshot.keyword || '无'}`,
    `搜索模式：${resolveSearchModeLabel(snapshot.mode)}`,
    `搜索范围：${snapshot.scope === 'all' ? '全部' : snapshot.scope === 'title' ? '标题' : '代码'}`,
    `标签：${snapshot.tag || '不限'}`,
    `分类：${snapshot.category ? resolveCategoryLabel(snapshot.category) : '不限'}`,
    `语言：${snapshot.language || '不限'}`,
    `发布状态：${snapshot.status ? (snapshot.status === 'DRAFT' ? '草稿' : '已发布') : '不限'}`,
    `更新时间：${snapshot.updatedFrom || '不限'} 至 ${snapshot.updatedTo || '不限'}`
  ]
  return lines.join('\n')
}

function resolveCategoryLabel(categoryValue: string) {
  const categoryId = Number(categoryValue)
  if (!Number.isFinite(categoryId)) {
    return categoryValue
  }
  return findCategoryName(categories.value, categoryId) || categoryValue
}

function isSearchHistoryItem(value: unknown): value is SearchHistoryItem {
  if (!value || typeof value !== 'object') {
    return false
  }
  const item = value as Partial<SearchHistoryItem>
  return typeof item.id === 'string' &&
    typeof item.label === 'string' &&
    typeof item.keyword === 'string' &&
    isSearchModeValue(item.mode) &&
    isSearchScope(item.scope)
}

function resolveSelectedCategoryName() {
  const selectedCategoryValue = searchForm.category || (query.categoryId ? String(query.categoryId) : '')
  if (!selectedCategoryValue) {
    return ''
  }
  const categoryId = Number(selectedCategoryValue)
  return findCategoryName(categories.value, categoryId)
}

function findCategoryName(items: Category[], categoryId: number): string {
  for (const item of items) {
    if (item.id === categoryId) {
      return item.name
    }
    const childName = findCategoryName(item.children ?? [], categoryId)
    if (childName) {
      return childName
    }
  }
  return ''
}

function resolveSearchModeLabel(mode: SearchMode) {
  if (mode === 'semantic') {
    return '语义搜索'
  }
  if (mode === 'hybrid') {
    return '混合搜索'
  }
  return '精确全文'
}

function resolveSortLabel(sort?: string) {
  if (sort === 'sortOrder') {
    return '自定义排序'
  }
  if (sort === 'createdAt') {
    return '创建时间'
  }
  if (sort === 'title') {
    return '标题'
  }
  return '更新时间'
}

async function handlePageChange(page: number, size: number) {
  query.page = page - 1
  query.size = size
  if (isSearchMode.value) {
    await router.replace({
      path: '/',
      query: buildSearchRouteQuery()
    })
    return
  }
  await loadNotes()
}

function toTreeData(items: Category[]): CategoryTreeNode[] {
  return items.map(item => ({
    title: item.name,
    key: item.id,
    value: String(item.id),
    children: toTreeData(item.children ?? [])
  }))
}

function toTreeNumberData(items: Category[]): CategoryTreeNumberNode[] {
  return items.map(item => ({
    title: item.name,
    key: item.id,
    value: item.id,
    children: toTreeNumberData(item.children ?? [])
  }))
}

function isSearchScope(value: unknown): value is SearchScope {
  return value === 'all' || value === 'title' || value === 'code'
}

function isSearchModeValue(value: unknown): value is SearchMode {
  return value === 'exact' || value === 'semantic' || value === 'hybrid'
}

function isNoteStatus(value: unknown): value is NoteStatus {
  return value === 'DRAFT' || value === 'PUBLISHED'
}

function isNavFilter(value: unknown): value is NavFilter {
  return value === 'all' || value === 'recent' || value === 'favorite' || value === 'archived' || value === 'trash'
}

function parsePositiveInteger(value: unknown, fallback: number) {
  if (typeof value !== 'string') {
    return fallback
  }
  const parsedValue = Number.parseInt(value, 10)
  return Number.isFinite(parsedValue) && parsedValue > 0 ? parsedValue : fallback
}

function parseNonNegativeInteger(value: unknown, fallback: number) {
  if (typeof value !== 'string') {
    return fallback
  }
  const parsedValue = Number.parseInt(value, 10)
  return Number.isFinite(parsedValue) && parsedValue >= 0 ? parsedValue : fallback
}

function renderSnippet(note: NoteCardItem) {
  if (isSearchMode.value && note.highlight) {
    return note.highlight
  }
  return escapeHtml(note.summary || '暂无摘要')
}

function resolveSearchInsights(note: NoteCardItem) {
  const insights: string[] = []
  if (note.hitFields?.length) {
    insights.push(`来源：${formatHitFields(note.hitFields)}`)
  }
  if (typeof note.keywordScore === 'number') {
    insights.push(`全文 ${formatSearchScore(note.keywordScore)}`)
  }
  if (typeof note.semanticSimilarity === 'number') {
    insights.push(`语义 ${formatSearchScore(note.semanticSimilarity)}`)
  }
  if (typeof note.hybridScore === 'number') {
    insights.push(`综合 ${formatSearchScore(note.hybridScore)}`)
  }
  const explanation = note.rankExplanation || note.matchReason
  if (explanation) {
    insights.push(explanation)
  }
  return insights
}

function formatHitFields(fields: string[]) {
  return Array.from(new Set(fields.map(formatHitField))).join(' / ')
}

function formatHitField(field: string) {
  if (field === 'title') {
    return '标题'
  }
  if (field === 'code') {
    return '代码'
  }
  if (field === 'category') {
    return '分类'
  }
  if (field === 'semantic') {
    return '语义'
  }
  return '正文'
}

function formatSearchScore(score: number) {
  if (!Number.isFinite(score)) {
    return '0%'
  }
  return `${Math.round(score * 100)}%`
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
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

function findCategoryById(items: Category[], categoryId: number): Category | undefined {
  for (const item of items) {
    if (item.id === categoryId) {
      return item
    }
    const child = findCategoryById(item.children ?? [], categoryId)
    if (child) {
      return child
    }
  }
  return undefined
}

function openCategoryManager() {
  categoryManagerVisible.value = true
  resetCategoryForm()
}

function editCategory(categoryId: number) {
  const category = findCategoryById(categories.value, categoryId)
  if (!category) {
    return
  }
  editingCategoryId.value = category.id
  categoryForm.name = category.name
  categoryForm.parentId = category.parentId
}

function resetCategoryForm() {
  editingCategoryId.value = undefined
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
    if (editingCategoryId.value) {
      await updateCategory(editingCategoryId.value, categoryForm.name.trim(), categoryForm.parentId)
      message.success('分类已更新')
    } else {
      await createCategory(categoryForm.name.trim(), categoryForm.parentId)
      message.success('分类已创建')
    }
    resetCategoryForm()
    await loadCategories()
  } catch (error) {
    message.error((error as Error).message)
  } finally {
    savingCategory.value = false
  }
}

async function removeCategory(categoryId: number) {
  try {
    await deleteCategory(categoryId)
    message.success('分类已删除')
    if (query.categoryId === categoryId) {
      query.categoryId = undefined
    }
    await loadCategories()
    await loadNotes()
  } catch (error) {
    message.error((error as Error).message)
  }
}

function openSettings() {
  void router.push('/settings')
}

function toDateText(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function addDays(date: Date, days: number) {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}
</script>
