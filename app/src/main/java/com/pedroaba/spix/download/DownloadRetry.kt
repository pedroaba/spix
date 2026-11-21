package com.pedroaba.spix.download

internal class RetryDownloadException(cause: Exception) : Exception("Transferência aguardando nova tentativa", cause)

/** ConnectivityManager can still report validated Wi-Fi while Doze blocks sockets. */
internal fun isRetryableDownloadError(error: Exception): Boolean {
    val messages = generateSequence<Throwable>(error) { it.cause }.take(8)
        .joinToString(" ") { it.message.orEmpty() }.lowercase()
    val serviceBlocked = generateSequence<Throwable>(error) { it.cause }.take(8).any {
        it.javaClass.simpleName in setOf("BackgroundServiceStartNotAllowedException", "ForegroundServiceStartNotAllowedException")
    }
    return serviceBlocked || error is java.net.SocketException || error is java.net.SocketTimeoutException ||
        error is java.net.UnknownHostException || listOf(
        "timed out", "timeout", "network is unreachable", "network unreachable", "connection",
        "unable to resolve", "name resolution", "remote end closed", "temporary failure",
        "http error 429", "http error 500", "http error 502", "http error 503", "http error 504",
        "http error 403", "confirm you’re not a bot", "confirm you're not a bot",
    ).any { it in messages }
}
