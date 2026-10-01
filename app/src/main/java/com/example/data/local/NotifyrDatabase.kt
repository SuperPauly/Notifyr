package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.Converters
import com.example.data.model.NotificationEntity
import com.example.data.model.OutboxEntity
import com.example.data.model.ServerEntity

@Database(
    entities = [
        NotificationEntity::class,
        ServerEntity::class,
        OutboxEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NotifyrDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun serverDao(): ServerDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile
        private var INSTANCE: NotifyrDatabase? = null

        fun getInstance(context: Context): NotifyrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotifyrDatabase::class.java,
                    "notifyr_database"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
