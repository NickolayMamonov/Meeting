package com.whysoezzy.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.whysoezzy.data.api.MeetingsApi
import com.whysoezzy.data.mapper.toDomain
import com.whysoezzy.domain.models.Meeting

internal class MeetingsPagingSource(
    private val api: MeetingsApi,
    private val tagId: Long?,
) : PagingSource<Int, Meeting>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Meeting> {
        val page = params.key ?: STARTING_PAGE
        return try {
            val items = api
                .getAllEvents(page = page, limit = params.loadSize, tagId = tagId)
                .map { it.toDomain() }
            LoadResult.Page(
                data = items,
                prevKey = if (page == STARTING_PAGE) null else page - 1,
                nextKey = if (items.size < params.loadSize) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Meeting>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }

    private companion object {
        const val STARTING_PAGE = 0
    }
}
