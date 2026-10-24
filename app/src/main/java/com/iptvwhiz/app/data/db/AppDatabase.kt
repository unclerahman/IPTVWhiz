package com.iptvwhiz.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, ChannelEntity::class, ChannelOverride::class, GroupSetting::class],
    views = [Channel::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun channels(): ChannelDao
    abstract fun overlay(): OverlayDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iptvwhiz.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
