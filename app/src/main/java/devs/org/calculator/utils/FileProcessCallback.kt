package devs.org.calculator.utils

import java.io.File

interface FileProcessCallback {
    fun onFilesProcessedSuccessfully(copiedFiles: List<File>)
    fun onFileProcessFailed()
}
