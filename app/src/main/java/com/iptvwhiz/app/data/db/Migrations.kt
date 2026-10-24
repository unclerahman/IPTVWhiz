package com.iptvwhiz.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations. Keep every version: a phone that skipped updates must still upgrade
 * without losing playlists or edits. SQL is copied verbatim from app/schemas (Room compares it).
 */
object Migrations {
    /** v2: user edits (channel overrides, group settings) and the channel_view that applies them. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `channel_overrides` (`playlistId` INTEGER NOT NULL, `stableKey` TEXT NOT NULL, `customName` TEXT, `customLogo` TEXT, `customGroup` TEXT, `hidden` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, `sortOrder` REAL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `stableKey`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `group_settings` (`playlistId` INTEGER NOT NULL, `name` TEXT NOT NULL, `customName` TEXT, `hidden` INTEGER NOT NULL, `sortOrder` REAL, `isCustom` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `name`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE VIEW `channel_view` AS SELECT c.playlistId, c.stableKey, c.number,\n            COALESCE(o.customName, c.name) AS name, c.name AS originalName,\n            COALESCE(o.customLogo, c.logoUrl) AS logoUrl,\n            c.streamUrl, c.tvgId,\n            COALESCE(o.customGroup, c.groupTitle) AS groupTitle, c.groupTitle AS originalGroup,\n            c.userAgent, c.referrer,\n            COALESCE(o.hidden, 0) AS hidden, COALESCE(o.favorite, 0) AS favorite,\n            COALESCE(o.sortOrder, c.number) AS sortOrder\n        FROM channels c\n        LEFT JOIN channel_overrides o ON o.playlistId = c.playlistId AND o.stableKey = c.stableKey",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
