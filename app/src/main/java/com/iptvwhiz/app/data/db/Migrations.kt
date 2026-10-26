package com.iptvwhiz.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations. Keep every version: a phone that skipped updates must still upgrade
 * without losing playlists or edits. Schema SQL comes from [SchemaMigrations] (generated);
 * data moves are written here.
 */
object Migrations {
    /** v2: user edits (channel overrides, group settings) and the channel_view that applies them. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = SchemaMigrations.schema1to2(db)
    }

    /** v3: TV guide (programmes, guide-to-channel map, update status). */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) = SchemaMigrations.schema2to3(db)
    }

    /** v4: watchlists, channel health, channel country. Favorites move into the built-in watchlist. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SchemaMigrations.schema3to4(db)
            createFavoritesWatchlist(db)
            // Favorites used to be a flag per channel; their order follows when they were last edited.
            db.execSQL(
                """
                INSERT OR IGNORE INTO watchlist_items (watchlistId, playlistId, stableKey, position, addedAt)
                SELECT ${WatchlistEntity.FAVORITES_ID}, playlistId, stableKey, updatedAt, updatedAt
                FROM channel_overrides WHERE favorite = 1
                """.trimIndent(),
            )
            db.execSQL("UPDATE channel_overrides SET favorite = 0 WHERE favorite = 1")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    /** The built-in Favorites watchlist; also created on a fresh install (see AppDatabase). */
    fun createFavoritesWatchlist(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO watchlists (id, name, builtIn, position, updatedAt) VALUES (?, ?, 1, 0, ?)",
            arrayOf<Any>(WatchlistEntity.FAVORITES_ID, "Favorites", System.currentTimeMillis()),
        )
    }
}
