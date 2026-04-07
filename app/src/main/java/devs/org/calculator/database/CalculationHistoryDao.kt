package devs.org.calculator.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalculationHistoryDao {
    @Insert
    suspend fun insert(history: CalculationHistory)

    @Query("SELECT * FROM calculation_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CalculationHistory>>

    @Query("DELETE FROM calculation_history")
    suspend fun clearHistory()
}
