package com.clayboicardi.game2048.ios

import okio.FileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

/** The app's Documents directory — writable, backed up, and private to the app. */
actual fun gameStoreDirectory(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return (paths.firstOrNull() as? String) ?: NSTemporaryDirectory()
}

actual fun gameStoreFileSystem(): FileSystem = FileSystem.SYSTEM
