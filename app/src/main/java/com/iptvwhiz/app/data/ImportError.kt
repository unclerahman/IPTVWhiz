package com.iptvwhiz.app.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Why adding or refreshing a playlist failed, in terms the UI can explain to anyone. */
sealed interface ImportError {
    data object NoConnection : ImportError
    data object Timeout : ImportError
    data object SecureConnectionFailed : ImportError
    data object AccessDenied : ImportError
    data object NotFound : ImportError
    data class HttpError(val code: Int) : ImportError
    data object NotAPlaylist : ImportError
    data object Empty : ImportError
    data object WrongLogin : ImportError
    data class AccountInactive(val status: String) : ImportError
    data object FileUnreadable : ImportError
    data object InvalidAddress : ImportError
    data class Unknown(val detail: String?) : ImportError
}

class ImportException(val error: ImportError, cause: Throwable? = null) : Exception(error.toString(), cause)

fun httpError(code: Int): ImportError = when (code) {
    401, 403 -> ImportError.AccessDenied
    404 -> ImportError.NotFound
    else -> ImportError.HttpError(code)
}

fun Throwable.toImportError(): ImportError = when (this) {
    is ImportException -> error
    is UnknownHostException -> ImportError.NoConnection
    is SocketTimeoutException -> ImportError.Timeout
    is SSLException -> ImportError.SecureConnectionFailed
    is IllegalArgumentException -> ImportError.InvalidAddress
    is IOException -> ImportError.NoConnection
    else -> ImportError.Unknown(message)
}
