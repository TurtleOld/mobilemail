package com.mobilemail.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.mobilemail.data.common.fold
import com.mobilemail.data.repository.SearchRepository
import com.mobilemail.domain.model.MessageListItem
import com.mobilemail.domain.repository.SearchQuery

class SearchPagingSource(
    private val repository: SearchRepository,
    private val searchQuery: SearchQuery
) : PagingSource<Int, MessageListItem>() {
    override suspend fun load(loadParams: LoadParams<Int>): LoadResult<Int, MessageListItem> {
        val position = loadParams.key ?: 0
        return repository.searchMessagesPage(
            searchQuery = searchQuery,
            position = position,
            limit = loadParams.loadSize
        ).fold(
            onError = { LoadResult.Error(it) },
            onSuccess = { page ->
                LoadResult.Page(
                    data = page.items,
                    prevKey = if (position == 0) null else maxOf(0, position - loadParams.loadSize),
                    nextKey = page.nextPosition.takeIf { page.hasMore }
                )
            }
        )
    }

    override fun getRefreshKey(state: PagingState<Int, MessageListItem>): Int? {
        return state.anchorPosition?.let { anchor ->
            val page = state.closestPageToPosition(anchor)
            page?.prevKey?.plus(state.config.pageSize) ?: page?.nextKey?.minus(state.config.pageSize)
        }
    }
}
