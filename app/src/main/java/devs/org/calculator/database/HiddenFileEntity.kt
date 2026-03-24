package devs.org.calculator.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import devs.org.calculator.utils.FileManager

@Entity(tableName = "hidden_files")
data class HiddenFileEntity(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val encryptedFileName: String,
    val fileType: FileManager.FileType,
    val originalExtension: String,
    val isEncrypted: Boolean,
    var dateAdded: Long = System.currentTimeMillis()
)