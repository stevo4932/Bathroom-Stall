package us.ststephens.bathroomstall.net

import java.lang.Exception

sealed class NetworkState<T>(val data: T? = null, val error: Exception? = null) {
    class Success<T>(data: T?) : NetworkState<T>(data)
    class Loading<T>(data: T? = null) : NetworkState<T>(data)
    class Error<T>(error: Exception, data: T? = null) : NetworkState<T>(data, error)
}