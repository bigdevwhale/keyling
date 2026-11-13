package app.keyling.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A book being written. [startWords] were written before the app (they count for the book, but fed nobody). */
@Entity
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String = "novel",
    val goal: Int = 80000,
    val deadline: Long = 0,          // epoch day, 0 = none
    val startWords: Int = 0,
    val planned: Int = 0,            // chapters in the plan
    val color: Int = 0,              // cover colour index
    val status: String = "active",   // active · paused · done
    val created: Long = 0,           // epoch day
    val finished: Long = 0,          // epoch day, 0 = not yet
    val drafts: Int = 1,             // passes: 2 = second draft …
)

/**
 * One sitting. [kind]: add (words written), edit (minutes of editing), sprint (words in a timed sprint),
 * rest (an honest day off), past (words written before the app, no food).
 */
@Entity
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val day: Long,                   // epoch day
    val at: Long,                    // millis
    val kind: String = "add",
    val words: Int = 0,
    val minutes: Int = 0,
    val chapter: Boolean = false,
)

/** A typo bug caught on the keyboard. */
@Entity
data class Caught(@PrimaryKey(autoGenerate = true) val id: Long = 0, val kind: Int, val at: Long)

@Dao
interface KeyDao {
    @Query("SELECT * FROM Book ORDER BY status = 'done', id") fun books(): Flow<List<Book>>
    @Query("SELECT * FROM Book ORDER BY id") suspend fun bookList(): List<Book>
    @Query("SELECT * FROM Entry ORDER BY at") fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM Entry ORDER BY at") suspend fun entryList(): List<Entry>
    @Query("SELECT * FROM Caught ORDER BY at") fun caught(): Flow<List<Caught>>
    @Query("SELECT * FROM Caught ORDER BY at") suspend fun caughtList(): List<Caught>
    @Insert suspend fun insert(b: Book): Long
    @Update suspend fun update(b: Book)
    @Insert suspend fun insert(e: Entry): Long
    @Update suspend fun update(e: Entry)
    @Insert suspend fun insert(c: Caught): Long
    @Query("DELETE FROM Entry WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("DELETE FROM Book WHERE id = :id") suspend fun deleteBook(id: Long)
    @Query("DELETE FROM Entry WHERE bookId = :id") suspend fun deleteEntriesOf(id: Long)
    @Query("DELETE FROM Book") suspend fun clearBooks()
    @Query("DELETE FROM Entry") suspend fun clearEntries()
    @Query("DELETE FROM Caught") suspend fun clearCaught()
}

@Database(entities = [Book::class, Entry::class, Caught::class], version = 1, exportSchema = true)
abstract class KeyDb : RoomDatabase() {
    abstract fun dao(): KeyDao

    companion object {
        fun create(ctx: Context) = Room.databaseBuilder(ctx, KeyDb::class.java, "keyling.db").build()
    }
}
