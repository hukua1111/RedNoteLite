package com.rednote.lite.ui.common

/** 通用加载状态。 */
sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

fun <T> LoadState<out List<T>>?.itemsOrEmpty(): List<T> = when (this) {
    is LoadState.Success -> data
    else -> emptyList()
}
