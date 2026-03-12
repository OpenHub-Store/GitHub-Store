package zed.rainxch.favourites.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.favourites.presentation.model.FavouriteRepository

data class FavouritesState(
    val favouriteRepositories: ImmutableList<FavouriteRepository> = persistentListOf(),
    val isLoading: Boolean = false,
)
