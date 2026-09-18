package com.example.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.demo.data.local.entity.Plan
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    /** 全部计划，按创建时间升序（计划页列表） */
    @Query("SELECT * FROM plan ORDER BY created_at ASC")
    fun observeAll(): Flow<List<Plan>>

    @Query("SELECT * FROM plan WHERE id = :id")
    suspend fun getById(id: Long): Plan?

    @Insert
    suspend fun insert(plan: Plan): Long

    @Update
    suspend fun update(plan: Plan): Int

    @Delete
    suspend fun delete(plan: Plan): Int
}
