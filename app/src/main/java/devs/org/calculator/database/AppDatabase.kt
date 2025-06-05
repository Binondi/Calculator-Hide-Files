package devs.org.calculator.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HiddenFileEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hiddenFileDao(): HiddenFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calculator_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 