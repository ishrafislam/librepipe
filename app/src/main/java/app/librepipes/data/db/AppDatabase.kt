package app.librepipes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubscriptionEntity::class,
        GroupEntity::class,
        GroupChannelCrossRef::class,
        HistoryEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistItemEntity::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun groupDao(): GroupDao
    abstract fun groupChannelDao(): GroupChannelDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "librepipes.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
