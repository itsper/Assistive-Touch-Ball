package com.example.assistive

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class ImageSortOption(val title: String) {
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)")
}

data class ImageModel(
    val id: Long,
    val displayName: String,
    val path: String,
    val folderName: String,
    val uri: Uri,
    val dateModified: Long,
    val size: Long
) {
    companion object {
        suspend fun scanLocalImages(context: Context): List<ImageModel> = withContext(Dispatchers.IO) {
            val list = mutableListOf<ImageModel>()
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )
            } else {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.SIZE
                )
            }

            val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = if (nameCol != -1) cursor.getString(nameCol) ?: "Image_$id" else "Image_$id"
                        val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                        val date = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                        val folderName = if (bucketCol != -1) {
                            cursor.getString(bucketCol) ?: try {
                                File(path).parentFile?.name ?: "Photos"
                            } catch (_: Exception) { "Photos" }
                        } else {
                            try {
                                File(path).parentFile?.name ?: "Photos"
                            } catch (_: Exception) { "Photos" }
                        }

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        list.add(
                            ImageModel(
                                id = id,
                                displayName = name,
                                path = path,
                                folderName = folderName,
                                uri = contentUri,
                                dateModified = date,
                                size = size
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list
        }
    }
}

data class ImageFolderModel(
    val name: String,
    val images: List<ImageModel>,
    val coverUri: Uri?
) {
    companion object {
        suspend fun scanLocalFolders(context: Context): List<ImageFolderModel> = withContext(Dispatchers.IO) {
            val allImages = ImageModel.scanLocalImages(context)
            if (allImages.isEmpty()) return@withContext emptyList()

            val folderList = mutableListOf<ImageFolderModel>()

            // 1. "All Photos" album containing every image
            folderList.add(
                ImageFolderModel(
                    name = "All Photos",
                    images = allImages,
                    coverUri = allImages.firstOrNull()?.uri
                )
            )

            // 2. Specific folders (Camera, Screenshots, etc.)
            val grouped = allImages.groupBy { it.folderName }
                .map { (folder, images) ->
                    ImageFolderModel(
                        name = folder,
                        images = images,
                        coverUri = images.firstOrNull()?.uri
                    )
                }
                .sortedBy { it.name.lowercase() }

            folderList.addAll(grouped)
            folderList
        }
    }
}

fun List<ImageModel>.sortedByOption(option: ImageSortOption): List<ImageModel> {
    return when (option) {
        ImageSortOption.DATE_DESC -> sortedByDescending { it.dateModified }
        ImageSortOption.DATE_ASC -> sortedBy { it.dateModified }
        ImageSortOption.NAME_ASC -> sortedBy { it.displayName.lowercase() }
        ImageSortOption.NAME_DESC -> sortedByDescending { it.displayName.lowercase() }
    }
}
