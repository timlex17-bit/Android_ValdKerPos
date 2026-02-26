package com.example.valdker.offline.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.valdker.offline.db.entities.ShiftEntity;

import java.util.List;

@Dao
public interface ShiftDao {

    @Insert
    long insert(ShiftEntity shift);

    @Update
    void update(ShiftEntity shift);

    @Query("SELECT * FROM shifts WHERE closed = 0 ORDER BY id DESC LIMIT 1")
    ShiftEntity getActiveShift();

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    ShiftEntity getById(long id);

    @Query("SELECT * FROM shifts ORDER BY id DESC LIMIT :limit")
    List<ShiftEntity> listRecent(int limit);

    @Query("UPDATE shifts SET synced = 1, server_id = :serverId WHERE id = :localId")
    void markSynced(long localId, String serverId);

    @Query("UPDATE shifts SET sync_error = :err WHERE id = :localId")
    void setSyncError(long localId, String err);
}