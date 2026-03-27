package com.dibe.eduhive.data.source.online

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.net.toUri

class ModelDownloader(
    private val context: Context
): Downloader {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    override fun downloadFile(url: String, name: String, allowMobileData: Boolean): Long {
        val allowedNetworks = if (allowMobileData) {
            DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
        } else {
            DownloadManager.Request.NETWORK_WIFI
        }

        val request = DownloadManager.Request(url.toUri())
            .setMimeType("application/octet-stream")
            .setAllowedNetworkTypes(allowedNetworks)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setTitle("Downloading AI Model: $name")
            .setDescription("EduHive is downloading the required AI resources.")
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                name
            )

        return downloadManager.enqueue(request)
    }
}
