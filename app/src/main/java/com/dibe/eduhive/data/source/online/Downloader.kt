package com.dibe.eduhive.data.source.online

interface Downloader {
    fun downloadFile(url: String, name: String, allowMobileData: Boolean = true): Long
}