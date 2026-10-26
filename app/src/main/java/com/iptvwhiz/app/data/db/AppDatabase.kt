package com.iptvwhiz.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class, ChannelEntity::class, ChannelOverride::class, GroupSetting::class,
        ProgramEntity::class, ChannelEpg::class, EpgStatus::class,
        WatchlistEntity::class, WatchlistItem::class, ChannelHealth::class,
    ],
    views = [Channel::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun channels(): ChannelDao
    abstract fun overlay(): OverlayDao
    abstract fun epg(): EpgDao
    abstract fun watchlists(): WatchlistDao
    abstract fun health(): HealthDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iptvwhiz.db")
                .addMigrations(*Migrations.ALL)
                .addCallback(object : Callback() {
                    // Migrations create Favorites for upgraded installs; this covers fresh ones.
                    override fun onCreate(db: SupportSQLiteDatabase) = Migrations.createFavoritesWatchlist(db)
                })
                .build()
    }
}
