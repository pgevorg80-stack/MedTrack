package com.example.gevorgpetrosyan;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "medicines")
public class Medicine {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String times;
    public int dosage;
    public int stock;
    public String expiryDate;
    public int expiryWarningDays;
    public String batches = "";
    public String history = "";
    public long lastUpdated;

    public Medicine(String name, String times) {
        this.name = name;
        this.times = times;
        this.dosage = 1;
        this.stock = 0;
        this.expiryWarningDays = 0;
        this.history = "";
        this.batches = "";
        this.lastUpdated = System.currentTimeMillis();
    }
}