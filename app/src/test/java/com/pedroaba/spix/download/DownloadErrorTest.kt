package com.pedroaba.spix.download

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class DownloadErrorTest {
    @Test fun errorsProvideRecoveryWithoutExposingRawCommandOutput() {
        assertTrue(downloadError(SecurityException("raw command")).contains("pasta"))
        assertTrue(downloadError(IOException("ENOSPC no space left")).contains("espaço"))
        assertTrue(downloadError(IOException("HTTP Error 403: Forbidden")).contains("bloqueou"))
        assertTrue(downloadError(IOException("This video is private")).contains("privado"))
        assertTrue(downloadError(IOException("larger than max-filesize")).contains("2 GB"))
        assertFalse(downloadError(IOException("raw command with internals")).contains("raw command"))
    }
}
