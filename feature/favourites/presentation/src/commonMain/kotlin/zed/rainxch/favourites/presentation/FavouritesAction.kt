package zed.rainxch.favourites.presentation

import zed.rainxch.favourites.presentation.model.FavouriteRepository

sealed interface FavouritesAction {
    data object OnNavigateBackClick : FavouritesAction

    data class OnToggleFavorite(
        val favouriteRepository: FavouriteRepository,
    ) : FavouritesAction

    data class OnRepositoryClick(
        val favouriteRepository: FavouriteRepository,
    ) : FavouritesAction

    data class OnDeveloperProfileClick(
        val username: String,
    ) : FavouritesAction
}
