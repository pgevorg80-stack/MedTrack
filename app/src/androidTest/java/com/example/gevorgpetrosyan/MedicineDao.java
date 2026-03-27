package com.example.gevorgpetrosyan;

import androidx.room.*;
import java.util.List;

@Dao
public interface MedicineDao {
    @Query("SELECT * FROM medicines")
    List<Medicine> getAll();

    @Insert    void insert(Medicine medicine);

    @Update
    void update(Medicine medicine);

    @Delete
    void delete(Medicine medicine);
}