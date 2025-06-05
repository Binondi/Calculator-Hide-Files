package devs.org.calculator.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import devs.org.calculator.utils.FileManager

@Entity(tableName = "hidden_files")
data class HiddenFileEntity(
    @PrimaryKey
    val filePath: String, //absolute path of the file
    val fileName: String,  // Original filename with extension
    val encryptedFileName: String,  // Encrypted filename
    val fileType: FileManager.FileType, //type of the file
    val originalExtension: String, // original file extension
    val isEncrypted: Boolean, // is the file encrypted or not
    var dateAdded: Long = System.currentTimeMillis()
)