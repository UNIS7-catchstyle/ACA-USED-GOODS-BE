package com.example.app.global.paging;

import java.util.List;

public record CursorPageResponse<T>(long totalCount, List<T> items, String nextCursor, boolean hasNext) {
}
