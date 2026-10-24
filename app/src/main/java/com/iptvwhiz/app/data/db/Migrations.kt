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

    /** v3: TV guide: programmes, guide-to-channel map and update status. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `programs` (`playlistId` INTEGER NOT NULL, `epgId` TEXT NOT NULL, `start` INTEGER NOT NULL, `stop` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `importedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `epgId`, `start`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_programs_playlistId_stop` ON `programs` (`playlistId`, `stop`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `channel_epg` (`playlistId` INTEGER NOT NULL, `stableKey` TEXT NOT NULL, `epgId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `stableKey`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `epg_status` (`playlistId` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, `matchedChannels` INTEGER NOT NULL, `programmes` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`playlistId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
