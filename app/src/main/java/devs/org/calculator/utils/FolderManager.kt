package devs.org.calculator.utils

import android.content.Context
import android.os.Environment
import java.io.File

class FolderManager(private val context: Context) {
    companion object {
        const val HIDDEN_DIR = ".CalculatorHide"
    }

    fun createFolder(parentDir: File, folderName: String): Boolean {
        val newFolder = File(parentDir, folderName)
        return if (!newFolder.exists()) {
            newFolder.mkdirs()
            // Create .nomedia file to hide from media scanners
            File(newFolder, ".nomedia").createNewFile()
            true
        } else {
            false
        }
    }

    fun deleteFolder(folder: File): Boolean {
        return try {
            if (folder.exists() && folder.isDirectory) {
                // Delete all files in the folder first
                folder.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
                // Then delete the folder itself
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

    fun moveFileToFolder(file: File, targetFolder: File): Boolean {
        return try {
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
                File(targetFolder, ".nomedia").createNewFile()
            }
            val newFile = File(targetFolder, file.name)
            file.copyTo(newFile, overwrite = true)
            file.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
} 