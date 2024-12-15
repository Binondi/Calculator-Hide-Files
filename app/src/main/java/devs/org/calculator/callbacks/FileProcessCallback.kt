package devs.org.calculator.callbacks

import java.io.File

interface FileProcessCallback {
    fun onFilesProcessedSuccessfully(copiedFiles: List<File>)
    fun onFileProcessFailed()
}
