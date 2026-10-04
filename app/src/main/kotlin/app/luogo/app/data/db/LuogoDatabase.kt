package app.luogo.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence.
 *
 * Enums are stored as their `name` string rather than ordinals, so reordering an enum in
 * Kotlin can never silently reinterpret existing rows.
 */

@Entity(tableName = "items", indices = [Index("ownerId"), Index("currentRotatingBleIdHex")])
data class RegisteredItemEntity(
    @PrimaryKey val id: String,
    val friendlyName: String,
    val itemType: String,
    val ownerId: String,
    val createdAtMs: Long,
    val ephemeralIdentitySeedBase64: String,
    val currentRotatingBleIdHex: String,
    val rotationEpoch: Long,
    val lastSeenLatitude: Double? = null,
    val lastSeenLongitude: Double? = null,
    val lastSeenAccuracyMeters: Float? = null,
    val lastSeenTimestampMs: Long? = null,
    val lastSeenSource: String = "BLE Finding Network",
    val batteryPercent: Int? = null,
    val rssiDbm: Int? = null,
    val uwbDistanceMeters: Float? = null,
    val uwbAzimuthDegrees: Float? = null,
    val isLostMode: Boolean = false,
    val isRinging: Boolean = false
)

@Entity(tableName = "users")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val colorArgb: Long,
    val publicKeyBase64: String,
    val sharingEnabled: Boolean = true,
    val temporarySharingUntilMs: Long? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "groups", indices = [Index("ownerId")])
data class PeerGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val ownerId: String,
    val createdAtMs: Long,
    val memberCount: Int = 1,
    val shareMyLocation: Boolean = true,
    val temporarySharingUntilMs: Long? = null
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "userId"],
    indices = [Index("userId")]
)
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val displayName: String,
    val colorArgb: Long,
    val isOwner: Boolean = false,
    val joinedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "location_history", indices = [Index("subjectId"), Index("timestampMs")])
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: String,
    val subjectName: String,
    val isItem: Boolean = false,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val activityState: String,
    val sourceSummary: String,
    val timestampMs: Long,
    val tripId: String? = null
)

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 120f,
    val enabled: Boolean = true,
    val notifyOnArrival: Boolean = true,
    val notifyOnDeparture: Boolean = true,
    val allowedGroupIdsCsv: String = "",
    val currentlyInside: Boolean = false,
    val lastTransitionMs: Long? = null
)

/** Pending encrypted crowdsourced sightings awaiting upload. Never stored in the clear. */
@Entity(tableName = "pending_sightings", indices = [Index("isUploaded"), Index("timestampMs")])
data class PendingSightingEntity(
    @PrimaryKey val reportId: String,
    val rotatingBleIdHex: String,
    val timestampMs: Long,
    val helperLatitude: Double,
    val helperLongitude: Double,
    val helperAccuracyMeters: Float,
    val rssiDbm: Int,
    val encryptedPayloadBase64: String,
    val authTagBase64: String,
    val protocolVersion: Int = 1,
    val isUploaded: Boolean = false,
    val retryCount: Int = 0,
    val lastAttemptMs: Long? = null
)

@Entity(tableName = "tracker_alerts")
data class TrackerAlertEntity(
    @PrimaryKey val trackerSignatureId: String,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val sightingCount: Int,
    val distinctLocationsCount: Int,
    val latestRssiDbm: Int,
    val riskLevel: String,
    val sampleRotatingIdsCsv: String,
    val isDismissed: Boolean = false
)

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val minZoom: Int = 10,
    val maxZoom: Int = 16,
    val styleId: String = "STANDARD_OSM",
    val status: String,
    val progressPercent: Int = 100,
    val downloadedTiles: Int = 0,
    val totalTiles: Int = 0,
    val sizeBytes: Long = 0L,
    val updatedAtMs: Long = System.currentTimeMillis()
)

/** Single-row-per-key store for server configuration and user settings. */
@Entity(tableName = "key_value_store")
data class KeyValueEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Dao
interface LuogoDao {

    // ------------------------------------------------------------------ items

    @Upsert
    suspend fun upsertRegisteredItem(item: RegisteredItemEntity)

    @Query("SELECT * FROM items ORDER BY createdAtMs DESC")
    fun observeRegisteredItems(): Flow<List<RegisteredItemEntity>>

    @Query("SELECT * FROM items ORDER BY createdAtMs DESC")
    suspend fun getRegisteredItems(): List<RegisteredItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getRegisteredItem(id: String): RegisteredItemEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeRegisteredItem(id: String): Flow<RegisteredItemEntity?>

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteRegisteredItem(id: String)

    @Query("UPDATE items SET friendlyName = :friendlyName WHERE id = :id")
    suspend fun renameRegisteredItem(id: String, friendlyName: String)

    @Query("UPDATE items SET isLostMode = :isLost WHERE id = :id")
    suspend fun setRegisteredItemLost(id: String, isLost: Boolean)

    @Query("UPDATE items SET isRinging = :ringing WHERE id = :id")
    suspend fun setRegisteredItemRinging(id: String, ringing: Boolean)

    /**
     * Records a sighting only when it is newer than what we already hold, so out-of-order
     * uploads cannot move an item's last-seen position backwards in time.
     */
    @Query(
        """
        UPDATE items SET
            lastSeenLatitude = :latitude,
            lastSeenLongitude = :longitude,
            lastSeenAccuracyMeters = :accuracyMeters,
            lastSeenTimestampMs = :timestampMs,
            lastSeenSource = :source,
            rssiDbm = COALESCE(:rssiDbm, rssiDbm)
        WHERE id = :id
          AND (lastSeenTimestampMs IS NULL OR lastSeenTimestampMs <= :timestampMs)
        """
    )
    suspend fun recordItemSighting(
        id: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        timestampMs: Long,
        source: String,
        rssiDbm: Int?
    )

    @Query("UPDATE items SET batteryPercent = :batteryPercent WHERE id = :id")
    suspend fun setRegisteredItemBattery(id: String, batteryPercent: Int?)

    // -------------------------------------------------------------- profiles

    @Upsert
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM users LIMIT 1")
    fun observeLocalUser(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getLocalUser(): UserProfileEntity?

    // ---------------------------------------------------------------- groups

    @Upsert
    suspend fun upsertGroup(group: PeerGroupEntity)

    @Query("SELECT * FROM groups ORDER BY createdAtMs ASC")
    fun observeGroups(): Flow<List<PeerGroupEntity>>

    @Query("SELECT * FROM groups ORDER BY createdAtMs ASC")
    suspend fun getGroups(): List<PeerGroupEntity>

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    @Upsert
    suspend fun upsertGroupMember(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearGroupMembers(groupId: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun removeGroupMember(groupId: String, userId: String)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getGroupMembers(groupId: String): List<GroupMemberEntity>

    @Transaction
    suspend fun replaceGroupMembers(groupId: String, members: List<GroupMemberEntity>) {
        clearGroupMembers(groupId)
        insertGroupMembers(members)
    }

    // --------------------------------------------------------------- history

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryPoint(point: LocationHistoryEntity): Long

    @Query("SELECT * FROM location_history WHERE subjectId = :subjectId ORDER BY timestampMs ASC")
    fun observeHistoryForSubject(subjectId: String): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history WHERE subjectId = :subjectId ORDER BY timestampMs ASC")
    suspend fun getHistoryForSubject(subjectId: String): List<LocationHistoryEntity>

    @Query("SELECT * FROM location_history ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int): List<LocationHistoryEntity>

    @Query("SELECT * FROM location_history WHERE timestampMs BETWEEN :startMs AND :endMs")
    suspend fun getHistoryInRange(startMs: Long, endMs: Long): List<LocationHistoryEntity>

    @Query("DELETE FROM location_history WHERE timestampMs BETWEEN :startMs AND :endMs")
    suspend fun deleteHistoryInRange(startMs: Long, endMs: Long)

    @Query("DELETE FROM location_history")
    suspend fun deleteAllHistory()

    @Query("SELECT COUNT(*) FROM location_history")
    suspend fun countHistory(): Int

    @Query("SELECT MIN(timestampMs) FROM location_history")
    suspend fun oldestHistoryTimestamp(): Long?

    // --------------------------------------------------------------- places

    @Upsert
    suspend fun upsertSavedPlace(place: SavedPlaceEntity)

    @Query("SELECT * FROM saved_places ORDER BY name ASC")
    fun observeSavedPlaces(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places ORDER BY name ASC")
    suspend fun getSavedPlaces(): List<SavedPlaceEntity>

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun deleteSavedPlace(id: String)

    @Update
    suspend fun updateSavedPlaces(places: List<SavedPlaceEntity>)

    // -------------------------------------------------- pending BLE reports

    @Upsert
    suspend fun upsertPendingSighting(sighting: PendingSightingEntity)

    @Query("SELECT * FROM pending_sightings WHERE isUploaded = 0 ORDER BY timestampMs ASC LIMIT :limit")
    suspend fun getPendingSightings(limit: Int): List<PendingSightingEntity>

    @Query("SELECT COUNT(*) FROM pending_sightings WHERE isUploaded = 0")
    fun observePendingSightingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sightings WHERE isUploaded = 0")
    suspend fun countPendingSightings(): Int

    @Query("DELETE FROM pending_sightings WHERE reportId = :reportId")
    suspend fun deletePendingSighting(reportId: String)

    @Query("UPDATE pending_sightings SET retryCount = retryCount + 1, lastAttemptMs = :nowMs WHERE reportId = :reportId")
    suspend fun markSightingAttempt(reportId: String, nowMs: Long)

    @Query("DELETE FROM pending_sightings WHERE isUploaded = 1 AND timestampMs < :beforeMs")
    suspend fun purgeUploadedSightings(beforeMs: Long)

    // -------------------------------------------------------- tracker alerts

    @Upsert
    suspend fun upsertTrackerAlert(alert: TrackerAlertEntity)

    @Query("SELECT * FROM tracker_alerts WHERE isDismissed = 0 ORDER BY lastSeenMs DESC")
    fun observeActiveTrackerAlerts(): Flow<List<TrackerAlertEntity>>

    @Query("SELECT * FROM tracker_alerts WHERE isDismissed = 0 ORDER BY lastSeenMs DESC")
    suspend fun getActiveTrackerAlerts(): List<TrackerAlertEntity>

    @Query("UPDATE tracker_alerts SET isDismissed = 1 WHERE trackerSignatureId = :signatureId")
    suspend fun dismissTrackerAlert(signatureId: String)

    // -------------------------------------------------------- offline regions

    @Upsert
    suspend fun upsertOfflineRegion(region: OfflineRegionEntity)

    @Query("SELECT * FROM offline_regions ORDER BY updatedAtMs DESC")
    fun observeOfflineRegions(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions ORDER BY updatedAtMs DESC")
    suspend fun getOfflineRegions(): List<OfflineRegionEntity>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun getOfflineRegion(id: String): OfflineRegionEntity?

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun deleteOfflineRegion(id: String)

    @Query("SELECT SUM(sizeBytes) FROM offline_regions")
    suspend fun totalOfflineRegionBytes(): Long?

    // ------------------------------------------------------------ key/value

    @Upsert
    suspend fun putKeyValue(entry: KeyValueEntity)

    @Query("SELECT value FROM key_value_store WHERE key = :key")
    suspend fun getKeyValue(key: String): String?

    @Query("SELECT value FROM key_value_store WHERE key = :key")
    fun observeKeyValue(key: String): Flow<String?>

    @Query("DELETE FROM key_value_store WHERE key = :key")
    suspend fun deleteKeyValue(key: String)
}

@Database(
    entities = [
        RegisteredItemEntity::class,
        UserProfileEntity::class,
        PeerGroupEntity::class,
        GroupMemberEntity::class,
        LocationHistoryEntity::class,
        SavedPlaceEntity::class,
        PendingSightingEntity::class,
        TrackerAlertEntity::class,
        OfflineRegionEntity::class,
        KeyValueEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LuogoDatabase : RoomDatabase() {

    abstract fun luogoDao(): LuogoDao

    companion object {
        private const val DB_NAME = "luogo_foss.db"

        @Volatile
        private var instance: LuogoDatabase? = null

        fun getInstance(context: android.content.Context): LuogoDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: android.content.Context): LuogoDatabase =
            Room.databaseBuilder(context, LuogoDatabase::class.java, DB_NAME)
                // No destructive fallback: user data must survive an upgrade. Future versions
                // must ship a real Migration, and a missing one should fail loudly in tests.
                .build()

        /** Test seam so instrumentation can install an in-memory database. */
        fun setInstanceForTesting(database: LuogoDatabase?) {
            instance = database
        }
    }
}