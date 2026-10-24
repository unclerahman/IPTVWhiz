"""Generates Migrations.kt from Room's exported schemas: one Migration per version step.
Only additive steps are generated (new tables, their indices, new views); a changed table aborts."""
import glob
import json
import os
import sys

root = sys.argv[1]
schema_dir = root + '/app/schemas/com.iptvwhiz.app.data.db.AppDatabase'
versions = sorted(int(os.path.basename(p)[:-5]) for p in glob.glob(schema_dir + '/*.json'))
schemas = {v: json.load(open(f'{schema_dir}/{v}.json'))['database'] for v in versions}

BS = chr(92)


def kt(s):
    s = s.replace(BS, BS + BS).replace('"', BS + '"').replace('$', BS + '$').replace('\n', BS + 'n')
    return '"' + s + '"'


notes = {
    (1, 2): "user edits (channel overrides, group settings) and the channel_view that applies them.",
    (2, 3): "TV guide: programmes, guide-to-channel map and update status.",
}

blocks = []
names = []
for old, new in zip(versions, versions[1:]):
    a, b = schemas[old], schemas[new]
    old_tables = {e['tableName']: e for e in a['entities']}
    stmts = []
    for e in b['entities']:
        t = e['tableName']
        if t in old_tables:
            if old_tables[t]['createSql'] != e['createSql']:
                sys.exit(f'Table {t} changed between {old} and {new}: write this migration by hand.')
            continue
        stmts.append(e['createSql'].replace('${TABLE_NAME}', t))
        for i in e.get('indices', []):
            stmts.append(i['createSql'].replace('${TABLE_NAME}', t))
    old_views = {v['viewName']: v for v in a.get('views', [])}
    for v in b.get('views', []):
        if v['viewName'] in old_views:
            if old_views[v['viewName']]['createSql'] != v['createSql']:
                sys.exit(f"View {v['viewName']} changed between {old} and {new}: write this migration by hand.")
            continue
        stmts.append(v['createSql'].replace('${VIEW_NAME}', v['viewName']))
    body = "\n".join("            db.execSQL(\n                " + kt(s) + ",\n            )" for s in stmts)
    name = f'MIGRATION_{old}_{new}'
    names.append(name)
    note = notes.get((old, new), "additive schema change.")
    blocks.append(f'''    /** v{new}: {note} */
    val {name} = object : Migration({old}, {new}) {{
        override fun migrate(db: SupportSQLiteDatabase) {{
{body}
        }}
    }}''')

src = '''package com.iptvwhiz.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations. Keep every version: a phone that skipped updates must still upgrade
 * without losing playlists or edits. SQL is copied verbatim from app/schemas (Room compares it).
 */
object Migrations {
''' + "\n\n".join(blocks) + f'''

    val ALL = arrayOf({", ".join(names)})
}}
'''
open(root + '/app/src/main/java/com/iptvwhiz/app/data/db/Migrations.kt', 'w', encoding='utf-8').write(src)
print("generated", names)
