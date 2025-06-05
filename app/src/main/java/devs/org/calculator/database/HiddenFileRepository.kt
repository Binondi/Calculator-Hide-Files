package devs.org.calculator.database

import kotlinx.coroutines.flow.Flow

class HiddenFileRepository(private val hiddenFileDao: HiddenFileDao) {

    fun getAllHiddenFiles(): Flow<List<HiddenFileEntity>> {
        return hiddenFileDao.getAllHiddenFiles()
    }

    suspend fun insertHiddenFile(hiddenFile: HiddenFileEntity) {
        hiddenFileDao.insertHiddenFile(hiddenFile)
    }

    suspend fun deleteHiddenFile(hiddenFile: HiddenFileEntity) {
        hiddenFileDao.deleteHiddenFile(hiddenFile)
    }

    suspend fun getHiddenFileByPath(filePath: String): HiddenFileEntity? {
        return hiddenFileDao.getHiddenFileByPath(filePath)
    }

    suspend fun updateEncryptionStatus(filePath: String, newFilePath: String,encryptedFileName: String, isEncrypted: Boolean) {
        hiddenFileDao.updateEncryptionStatus(filePath,newFilePath, encryptedFileName = encryptedFileName, isEncrypted)
    }

}