package com.mobilemail.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.mobilemail.data.common.fold
import com.mobilemail.domain.common.Result
import com.mobilemail.domain.model.MessageListItem
import com.mobilemail.data.repository.MailRepository

class MailPagingSource(
    private val repository: MailRepository,
    private val folderId: String
) : PagingSource<Int, MessageListItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageListItem> {
        val position = params.key ?: 0
        return repository.getMessagesPage(folderId, position, params.loadSize).fold(
            onError = { LoadResult.Error(it) },
            onSuccess = { page ->
                LoadResult.Page(
                    data = page.items,
                    prevKey = if (position == 0) null else maxOf(0, position - params.loadSize),
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
