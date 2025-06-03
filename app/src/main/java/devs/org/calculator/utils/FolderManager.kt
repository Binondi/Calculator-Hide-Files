package devs.org.calculator.utils

import java.io.File

class FolderManager {


    fun createFolder(parentDir: File, folderName: String): Boolean {
        val newFolder = File(parentDir, folderName)
        return if (!newFolder.exists()) {
            newFolder.mkdirs()
            File(newFolder, ".nomedia").createNewFile()
            true
        } else {
            false
        }
    }

    fun deleteFolder(folder: File): Boolean {
        return try {
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
                folder.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getFoldersInDirectory(directory: File): List<File> {
        return if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.filter { it.isDirectory && it.name != ".nomedia" } ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun getFilesInFolder(folder: File): List<File> {
        return if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.filter { it.isFile && it.name != ".nomedia" } ?: emptyList()
        } else {
            emptyList()
        }
    }
} 