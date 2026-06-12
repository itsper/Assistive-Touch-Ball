package com.example.assistive

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AudioModel(
    val id: Long,
    val title: String,
    val artist: String,
    val path: String,
    val folderName: String, // Tracks the parent directory name
    val uri: Uri,
    val duration: Long
) {
    companion object {
        suspend fun scanLocalAudioFiles(context: Context): List<AudioModel> = withContext(Dispatchers.IO) {
            val audioList = mutableListOf<AudioModel>()
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
            )
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn) ?: "Unknown Track"
                        val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                        val path = cursor.getString(dataColumn) ?: ""
                        val duration = cursor.getLong(durationColumn)
                        
                        // EXTRACT THE FOLDER NAME SAFELY FROM THE SYSTEM FILE PATH
                        val folderName = try {
                            val file = File(path)
                            file.parentFile?.name ?: "Internal Storage"
                        } catch (e: Exception) {
                            "Unknown Folder"
                        }
                        
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        audioList.add(
                            AudioModel(
                                id = id,
                                title = title,
                                artist = artist,
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
            
            audioList
        }
    }
}

// Container model for grouping folders
data class FolderModel(
    val name: String,
    val songs: List<AudioModel>
) {
    companion object {
        suspend fun scanLocalFolders(context: Context): List<FolderModel> = withContext(Dispatchers.IO) {
            val songs = AudioModel.scanLocalAudioFiles(context)
            
            // Group the flat song list by the folderName property dynamically
            songs.groupBy { it.folderName }
                .map { (folderName, songList) -> FolderModel(folderName, songList) }
                .sortedBy { it.name.lowercase() }
        }
    }
}
