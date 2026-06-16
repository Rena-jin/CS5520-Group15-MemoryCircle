package com.cs5520group15.memorycircle.common

/**
 * What: A wrapper that represents the outcome of any async operation.
 * Why: ViewModels need to know if an operation is loading, succeeded,
 *      or failed — this sealed class captures all three states cleanly.
 *
 * Usage example:
 *   when (result) {
 *       is Result.Loading -> show spinner
 *       is Result.Success -> show data
 *       is Result.Error   -> show error message
 *   }
 */
sealed class Result<out T> {
    object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}