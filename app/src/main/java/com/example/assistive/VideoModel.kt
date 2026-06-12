package com.example.assistive

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class VideoModel(
    val id: Long,
    val title: String,
    val path: String,
    val folderName: String,
    val uri: Uri,
    val duration: Long
) {
    companion object {
        suspend fun scanLocalVideoFiles(context: Context): List<VideoModel> = withContext(Dispatchers.IO) {
            val videoList = mutableListOf<VideoModel>()
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION
            )
            
            val sortOrder = "${MediaStore.Video.Media.TITLE} ASC"
            
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown Video"
                        val path = cursor.getString(dataColumn) ?: ""
                        val duration = cursor.getLong(durationColumn)
                        
                        val folderName = try {
                            val file = File(path)
                            file.parentFile?.name ?: "Internal Storage"
                        } catch (e: Exception) {
                            "Unknown Folder"
                        }
                        
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        videoList.add(
                            VideoModel(
                                id = id,
                                title = title,
                                path = path,
                                folderName = folderName,
                                uri = contentUri,
                                duration = duration
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            videoList
        }
    }
}

data class VideoFolderModel(
    val name: String,
    val videos: List<VideoModel>
) {
    companion object {
        suspend fun scanLocalFolders(context: Context): List<VideoFolderModel> = withContext(Dispatchers.IO) {
            val videos = VideoModel.scanLocalVideoFiles(context)
            
            videos.groupBy { it.folderName }
                .map { (folderName, videoList) -> VideoFolderModel(folderName, videoList) }
                .sortedBy { it.name.lowercase() }
        }
    }
}
