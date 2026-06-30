package com.act.geomapper.security

import java.io.File

object RootDetector {

    fun isRooted(): Boolean = checkSuBinaries() || checkBuildTags()

    private fun checkSuBinaries(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkBuildTags(): Boolean {
        val tags = android.os.Build.TAGS ?: return false
        return tags.contains("test-keys")
    }
}
