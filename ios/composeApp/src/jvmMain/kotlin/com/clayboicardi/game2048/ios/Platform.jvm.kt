package com.clayboicardi.game2048.ios

import okio.FileSystem

/** Desktop/CI harness storage: a temp directory. The JVM target exists only for local validation. */
actual fun gameStoreDirectory(): String =
    (System.getProperty("java.io.tmpdir") ?: ".").trimEnd('/') + "/game2048-ios"

actual fun gameStoreFileSystem(): FileSystem = FileSystem.SYSTEM
