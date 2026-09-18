package com.example.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.demo.data.local.entity.UserSetting
import kotlinx.coroutines.flow.Flow

/**
 * 用户配置 DAO。
 *
 * 全表只有一行（id = 1）。写入统一用 REPLACE 策略的 upsert，
 * 调用方不需要关心"第一次是 insert、之后是 update"这个区别。
 */
@Dao
interface UserSettingDao {

    /**
     * 订阅配置。首次启动时表是空的，会发射 null，
     * 因此 Repository 层要做 `?: UserSetting()` 兜底成默认值。
     */
    @Query("SELECT * FROM user_setting WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserSetting?>

    @Query("SELECT * FROM user_setting WHERE id = 1 LIMIT 1")
    suspend fun get(): UserSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: UserSetting)
}
