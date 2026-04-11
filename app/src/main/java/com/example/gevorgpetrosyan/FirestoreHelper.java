package com.example.gevorgpetrosyan;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.List;
import java.util.concurrent.Executors;

public class FirestoreHelper {
    private static final String COLLECTION_NAME = "medicines";

    public static void uploadMedicine(Medicine medicine) {
        if (medicine.userId == null) return;
        String docId = medicine.userId + "_" + medicine.name.replace("/", "_");
        FirebaseFirestore.getInstance().collection(COLLECTION_NAME).document(docId)
                .set(medicine, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d("Firestore", "Uploaded: " + medicine.name))
                .addOnFailureListener(e -> Log.e("Firestore", "Upload failed", e));
    }

    public static void deleteMedicine(Medicine medicine) {
        if (medicine.userId == null) return;
        String docId = medicine.userId + "_" + medicine.name.replace("/", "_");
        FirebaseFirestore.getInstance().collection(COLLECTION_NAME).document(docId).delete();
    }

    public static void syncFromFirestore(String userId, AppDatabase localDb, Runnable onComplete) {
        FirebaseFirestore.getInstance().collection(COLLECTION_NAME)
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Medicine> remoteMeds = queryDocumentSnapshots.toObjects(Medicine.class);
                    Log.d("Sync", "Found " + remoteMeds.size() + " medicines in cloud.");

                    Executors.newSingleThreadExecutor().execute(() -> {
                        for (Medicine remote : remoteMeds) {
                            Medicine local = localDb.medicineDao().getByNameAndUserId(remote.name, userId);
                            if (local == null) {
                                localDb.medicineDao().insert(remote);
                            } else if (remote.lastUpdated > local.lastUpdated) {
                                remote.id = local.id;
                                localDb.medicineDao().update(remote);
                            }
                        }
                        if (onComplete != null) onComplete.run();
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("Sync", "Sync failed", e);
                    if (onComplete != null) onComplete.run();
                });
    }
}
