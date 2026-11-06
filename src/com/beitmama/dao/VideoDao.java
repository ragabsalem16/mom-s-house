package com.beitmama.dao;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class VideoDao {
    public List<String> findVideosBySeller(int sellerId) {
        try {
            QuerySnapshot query = FirestoreDatabase.await(FirestoreDatabase.get().collection("seller_videos")
                    .whereEqualTo("seller_id", sellerId).get());
            List<QueryDocumentSnapshot> snapshots = new ArrayList<>(query.getDocuments());
            snapshots.sort(Comparator.comparing(
                    snapshot -> FirestoreDatabase.timestamp(snapshot.get("created_at")),
                    Comparator.nullsLast(Comparator.reverseOrder())));
            List<String> videos = new ArrayList<>();
            for (QueryDocumentSnapshot snapshot : snapshots) {
                String url = snapshot.getString("video_url");
                if (url != null) videos.add(url);
            }
                return videos;
            } catch (SQLException e) {
                throw new IllegalStateException("تعذر تحميل فيديوهات الأم.", e);
            }
    }

    public boolean addVideo(int sellerId, String videoUrl) throws SQLException {
        int id = FirestoreDatabase.nextId("seller_videos");
        Firestore firestore = FirestoreDatabase.get();
        Query sellerVideos = firestore.collection("seller_videos").whereEqualTo("seller_id", sellerId);
        DocumentReference video = FirestoreDatabase.document("seller_videos", id);
        try {
            return FirestoreDatabase.await(firestore.runTransaction(transaction -> {
                int count = transaction.get(sellerVideos).get().size();
                if (count >= 4) return false;
                transaction.set(video, Map.of(
                        "id", id,
                        "seller_id", sellerId,
                        "video_url", videoUrl,
                        "created_at", new Date()));
                return true;
            }));
        } catch (SQLException e) {
            throw new IllegalStateException("تعذر حفظ فيديو الأم.", e);
        }
    }

    public void deleteVideo(int videoId) throws SQLException {
        DocumentReference reference = FirestoreDatabase.document("seller_videos", videoId);
        try {
            DocumentSnapshot snapshot = FirestoreDatabase.await(reference.get());
            if (snapshot.exists()) FirestoreDatabase.await(reference.delete());
        } catch (SQLException e) {
            throw new IllegalStateException("تعذر حذف فيديو الأم.", e);
        }
    }
}
