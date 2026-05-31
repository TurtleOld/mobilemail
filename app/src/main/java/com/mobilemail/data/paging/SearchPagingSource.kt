package com.mobilemail.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.mobilemail.data.common.fold
import com.mobilemail.domain.model.MessageListItem
import com.mobilemail.data.repository.SearchParams
import com.mobilemail.data.repository.SearchRepository

data class SearchPagingParams(
    val query: String,
    val senderQuery: String,
    val folderId: String?,
    val unreadOnly: Boolean,
    val hasAttachments: Boolean,
    val starredOnly: Boolean,
    val importantOnly: Boolean,
    val dateRange: SearchRepository.DateRange
)

class SearchPagingSource(
    private val repository: SearchRepository,
    private val params: SearchPagingParams
) : PagingSource<Int, MessageListItem>() {
    override suspend fun load(loadParams: LoadParams<Int>): LoadResult<Int, MessageListItem> {
        val position = loadParams.key ?: 0
        return repository.searchMessagesPage(
            params = SearchParams(
                query = params.query,
                folderId = params.folderId,
                unreadOnly = params.unreadOnly,
                hasAttachments = params.hasAttachments,
                starredOnly = params.starredOnly,
                importantOnly = params.importantOnly,
                senderQuery = params.senderQuery,
                dateRange = params.dateRange
            ),
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
