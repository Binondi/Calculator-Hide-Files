package devs.org.calculator.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenFileDao {
    @Query("SELECT * FROM hidden_files")
    fun getAllHiddenFiles(): Flow<List<HiddenFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenFile(hiddenFile: HiddenFileEntity)

    @Delete
    suspend fun deleteHiddenFile(hiddenFile: HiddenFileEntity)

    @Query("SELECT * FROM hidden_files WHERE filePath = :filePath")
    suspend fun getHiddenFileByPath(filePath: String): HiddenFileEntity?

    @Query("SELECT * FROM hidden_files WHERE fileName = :fileName")
    suspend fun getHiddenFileByOriginalName(fileName: String): HiddenFileEntity?

    @Query("UPDATE hidden_files SET isEncrypted = :isEncrypted, filePath = :newFilePath, encryptedFileName = :encryptedFileName WHERE filePath = :filePath")
    suspend fun updateEncryptionStatus(
        filePath: String,
        newFilePath: String,
        encryptedFileName: String?,
        isEncrypted: Boolean
    )

    @Update
    suspend fun updateHiddenFile(hiddenFile: HiddenFileEntity)
} 