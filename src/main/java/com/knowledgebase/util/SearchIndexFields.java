package com.knowledgebase.util;

/**
 * Lucene 索引字段常量。
 */
public final class SearchIndexFields {

    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String CONTENT_PLAIN = "contentPlain";
    public static final String CONTENT_CODE = "contentCode";
    public static final String TAGS = "tags";
    public static final String TAG_NAMES = "tagNames";
    public static final String CATEGORY = "category";
    public static final String CATEGORY_ID = "categoryId";
    public static final String CATEGORY_EXACT = "categoryExact";
    public static final String LANGUAGE = "language";
    public static final String TYPE = "type";
    public static final String STATUS = "status";
    public static final String CREATED_TIME = "createdTime";
    public static final String CREATED_TIME_SORT = "createdTimeSort";
    public static final String UPDATED_TIME = "updatedTime";
    public static final String UPDATED_TIME_SORT = "updatedTimeSort";

    private SearchIndexFields() {
    }
}
