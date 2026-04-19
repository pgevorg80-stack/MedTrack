package com.example.gevorgpetrosyan;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "medicines")
public class Medicine {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String userId; // Added to separate accounts
    public String name;
    public String times;
    public int dosage;
    public String dosageUnit = "pills"; // Default unit
    public int stock;
    public String expiryDate;
    public int expiryWarningDays;
    public String batches = "";
    public String history = "";
    public long lastUpdated;
    public String imagePath;

    public Medicine() {
        // Required for Firestore toObject()
    }

    public Medicine(String userId, String name, String times) {
        this.userId = userId;
        this.name = name;
        this.times = times;
        this.dosage = 1;
        this.dosageUnit = "pills";
        this.stock = 0;
        this.expiryWarningDays = 0;
        this.history = "";
        this.batches = "";
        this.lastUpdated = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Medicine medicine = (Medicine) o;
        return id == medicine.id;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }
}
