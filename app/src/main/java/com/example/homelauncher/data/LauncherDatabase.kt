package com.example.homelauncher.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A home screen page. Pages are shown in [rank] order. */
@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rank: Int,
)

/**
 * Anything placed on the home screen or dock. Stage 1 stores apps only; folders and widgets
 * reuse this table in later stages ([type]).
 */
@Entity(tableName = "items", indices = [Index("pageId")])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int = TYPE_APP,
    /** [com.example.homelauncher.model.Container]. */
    val container: Int,
    /** Page id for desktop items, 0 for the dock. */
    val pageId: Long,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val packageName: String,
    val className: String,
    val userSerial: Long,
    /** When the app was found uninstalled; null while installed. */
    val missingSince: Long? = null,
) {
    companion object {
        const val TYPE_APP = 0
    }
}

@Dao
interface LauncherDao {
    @Query("SELECT * FROM pages ORDER BY rank")
    fun pages(): Flow<List<PageEntity>>

    @Query("SELECT * FROM items")
    fun items(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM pages ORDER BY rank")
    suspend fun pagesNow(): List<PageEntity>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun itemCount(): Int

    @Query("SELECT * FROM items")
    suspend fun itemsNow(): List<ItemEntity>

    @Insert
    suspend fun insertPage(page: PageEntity): Long

    @Insert
    suspend fun insertItem(item: ItemEntity): Long

    @Insert
    suspend fun insertItems(items: List<ItemEntity>)

    @Update
    suspend fun updateItems(items: List<ItemEntity>)

    @Query("SELECT * FROM items WHERE id IN (:ids)")
    suspend fun itemsById(ids: List<Long>): List<ItemEntity>

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteItems(ids: List<Long>)

    /** Appends pages until there are at least [count]; returns all page ids in rank order. */
    @Transaction
    suspend fun ensurePages(count: Int): List<Long> {
        val existing = pagesNow()
        var rank = (existing.maxOfOrNull { it.rank } ?: -1) + 1
        val ids = existing.map { it.id }.toMutableList()
        while (ids.size < count) ids += insertPage(PageEntity(rank = rank++))
        return ids
    }
}

@Database(entities = [PageEntity::class, ItemEntity::class], version = 1, exportSchema = true)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun dao(): LauncherDao

    companion object {
        /** One database per layout mode, so each mode keeps its own pages and icons. */
        fun create(context: Context, mode: LayoutMode): LauncherDatabase {
            val name = when (mode) {
                LayoutMode.HOME_AND_DRAWER -> "launcher.db" // Name kept from Stage 1 so layouts survive.
                LayoutMode.HOME_ONLY -> "launcher_home_only.db"
            }
            return Room.databaseBuilder(context, LauncherDatabase::class.java, name).build()
        }
    }
}
