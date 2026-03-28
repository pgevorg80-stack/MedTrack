package com.example.gevorgpetrosyan;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MedicineDao {

    @Query("SELECT * FROM medicines WHERE userId = :userId ORDER BY lastUpdated DESC, id DESC")
    List<Medicine> getAllByUserId(String userId);

    @Query("SELECT * FROM medicines")
    List<Medicine> getAll();

    @Insert
    void insert(Medicine medicine);

    @Update
    void update(Medicine medicine);

    @Delete
    void delete(Medicine medicine);

    @Query("SELECT * FROM medicines WHERE name = :medicineName AND userId = :userId LIMIT 1")
    Medicine getByNameAndUserId(String medicineName, String userId);
}
