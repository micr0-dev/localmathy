package dev.micr0.localmathy

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Local, on-device history stored in a SQLite database in the app's private
 * data dir. Nothing leaves the device. All DB work happens off the main
 * thread; the in-memory [entries] flow is what the UI observes.
 */
class AndroidHistoryStore(context: Context) : HistoryStore {

    private val helper = Helper(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    override val entries: StateFlow<List<HistoryEntry>> = _entries

    init {
        scope.launch { reload() }
    }

    override fun add(question: String, answer: String, thinking: String, elapsedSeconds: Int) {
        scope.launch {
            val values = ContentValues().apply {
                put(COL_QUESTION, question)
                put(COL_ANSWER, answer)
                put(COL_THINKING, thinking)
                put(COL_ELAPSED, elapsedSeconds)
                put(COL_CREATED_AT, System.currentTimeMillis())
            }
            helper.writableDatabase.insert(TABLE, null, values)
            reload()
        }
    }

    override fun delete(id: Long) {
        scope.launch {
            helper.writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
            reload()
        }
    }

    override fun clear() {
        scope.launch {
            helper.writableDatabase.delete(TABLE, null, null)
            reload()
        }
    }

    private fun reload() {
        val list = ArrayList<HistoryEntry>()
        helper.readableDatabase.query(
            TABLE,
            arrayOf(COL_ID, COL_QUESTION, COL_ANSWER, COL_THINKING, COL_ELAPSED, COL_CREATED_AT),
            null, null, null, null,
            "$COL_CREATED_AT DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    HistoryEntry(
                        id = cursor.getLong(0),
                        question = cursor.getString(1),
                        answer = cursor.getString(2),
                        thinking = cursor.getString(3),
                        elapsedSeconds = cursor.getInt(4),
                        createdAtMillis = cursor.getLong(5),
                    ),
                )
            }
        }
        _entries.value = list
    }

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, "localmathy_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE (" +
                    "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COL_QUESTION TEXT NOT NULL, " +
                    "$COL_ANSWER TEXT NOT NULL, " +
                    "$COL_THINKING TEXT NOT NULL, " +
                    "$COL_ELAPSED INTEGER NOT NULL, " +
                    "$COL_CREATED_AT INTEGER NOT NULL)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Only v1 exists so far; nothing to migrate.
        }
    }

    private companion object {
        const val TABLE = "history"
        const val COL_ID = "id"
        const val COL_QUESTION = "question"
        const val COL_ANSWER = "answer"
        const val COL_THINKING = "thinking"
        const val COL_ELAPSED = "elapsed_seconds"
        const val COL_CREATED_AT = "created_at"
    }
}
