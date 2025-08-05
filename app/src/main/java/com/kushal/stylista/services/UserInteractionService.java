package com.kushal.stylista.services;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.kushal.stylista.model.ClothModel;
import com.kushal.stylista.model.UserModel;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * Service to handle user interactions and update Firebase with recommendation data
 */
public class UserInteractionService {
    private static final String TAG = "UserInteractionService";
    private static final String USERS_COLLECTION = "users";
    private static final String INTERACTIONS_COLLECTION = "user_interactions";

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    public UserInteractionService() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /**
     * Record a swipe interaction (like or dislike)
     */
    public void recordSwipeInteraction(ClothModel cloth, boolean liked, ApiSaveResponseCallback callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        Log.d(TAG, "Recording swipe interaction: " + (liked ? "LIKE" : "DISLIKE") + " for item: " + cloth.getId());

        // Create interaction record
        Map<String, Object> interaction = new HashMap<>();
        interaction.put("userId", userId);
        interaction.put("itemId", cloth.getId());
        interaction.put("action", liked ? "like" : "dislike");
        interaction.put("timestamp", FieldValue.serverTimestamp());
        interaction.put("category", cloth.getCategory());
        interaction.put("brand", cloth.getBrand());
        interaction.put("price", cloth.getPrice());

        // Add color information
        if (cloth.getColors() != null && !cloth.getColors().isEmpty()) {
            interaction.put("primaryColor", cloth.getColors().get(0).getName());
        }

        // Save interaction record
        db.collection(INTERACTIONS_COLLECTION)
                .add(interaction)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Interaction recorded with ID: " + documentReference.getId());

                    // Update user preferences
                    updateUserPreferences(userId, cloth, liked, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error recording interaction", e);
                    callback.onFailure(e);
                });
    }

    /**
     * Record when user views an item
     */
    public void recordItemView(ClothModel cloth) {
        String userId = getCurrentUserId();
        if (userId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("viewedItems", FieldValue.arrayUnion(cloth.getId()));
        updates.put("lastActivity", FieldValue.serverTimestamp());

        db.collection(USERS_COLLECTION).document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Item view recorded"))
                .addOnFailureListener(e -> Log.e(TAG, "Error recording item view", e));
    }

    /**
     * Update user preferences based on interaction
     */
    private void updateUserPreferences(String userId, ClothModel cloth, boolean liked, ApiSaveResponseCallback callback) {
        db.collection(USERS_COLLECTION).document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    UserModel user = documentSnapshot.toObject(UserModel.class);
                    if (user == null) {
                        user = new UserModel();
                        user.setUid(userId);
                    }

                    // Update interaction tracking
                    if (liked) {
                        user.addLikedItem(cloth.getId());
                    } else {
                        user.addDislikedItem(cloth.getId());
                    }
                    user.addViewedItem(cloth.getId());

                    // Update preference scores using RecommendationEngine
                    RecommendationEngine.updateUserPreferences(user, cloth, liked);

                    // Prepare updates for Firebase
                    Map<String, Object> updates = createUserUpdates(user, cloth, liked);

                    // Save updated preferences
                    db.collection(USERS_COLLECTION).document(userId)
                            .update(updates)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "User preferences updated successfully");
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating user preferences", e);
                                callback.onFailure(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user data", e);
                    callback.onFailure(e);
                });
    }

    /**
     * Create Firebase update map from UserModel
     */
    private Map<String, Object> createUserUpdates(UserModel user, ClothModel cloth, boolean liked) {
        Map<String, Object> updates = new HashMap<>();

        // Update interaction lists
        updates.put("likedItems", user.getLikedItems());
        updates.put("dislikedItems", user.getDislikedItems());
        updates.put("viewedItems", user.getViewedItems());

        // Update counters
        updates.put("totalSwipes", user.getTotalSwipes());
        updates.put("totalLikes", user.getTotalLikes());
        updates.put("totalDislikes", user.getTotalDislikes());

        // Update preference maps
        updates.put("categoryPreferences", user.getCategoryPreferences());
        updates.put("brandPreferences", user.getBrandPreferences());
        updates.put("colorPreferences", user.getColorPreferences());
        updates.put("materialPreferences", user.getMaterialPreferences());
        updates.put("tagPreferences", user.getTagPreferences());

        // Update timestamp
        updates.put("lastActivity", FieldValue.serverTimestamp());

        return updates;
    }

    /**
     * Get user model with recommendation data
     */
    public void getUserWithRecommendationData(ApiResponseCallback<UserModel> callback) {
        String userId = getCurrentUserId();
        if (userId == null) {
            callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        db.collection(USERS_COLLECTION).document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    UserModel user = documentSnapshot.toObject(UserModel.class);
                    if (user == null) {
                        user = new UserModel();
                        user.setUid(userId);
                    }
                    callback.onSuccess(user);
                })
                .addOnFailureListener(callback::onFailure);
    }

    /**
     * Initialize recommendation data for new users
     */
    public void initializeRecommendationData(String userId, ApiSaveResponseCallback callback) {
        Map<String, Object> recommendationData = new HashMap<>();
        recommendationData.put("categoryPreferences", new HashMap<String, Integer>());
        recommendationData.put("brandPreferences", new HashMap<String, Integer>());
        recommendationData.put("colorPreferences", new HashMap<String, Integer>());
        recommendationData.put("materialPreferences", new HashMap<String, Integer>());
        recommendationData.put("tagPreferences", new HashMap<String, Integer>());
        recommendationData.put("likedItems", new ArrayList<String>());
        recommendationData.put("dislikedItems", new ArrayList<String>());
        recommendationData.put("viewedItems", new ArrayList<String>());
        recommendationData.put("totalSwipes", 0);
        recommendationData.put("totalLikes", 0);
        recommendationData.put("totalDislikes", 0);
        recommendationData.put("lastActivity", FieldValue.serverTimestamp());

        db.collection(USERS_COLLECTION).document(userId)
                .update(recommendationData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Recommendation data initialized for user: " + userId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error initializing recommendation data", e);
                    callback.onFailure(e);
                });
    }

    /**
     * Get current authenticated user ID
     */
    private String getCurrentUserId() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    /**
     * Callback interface for API responses with data
     */
    public interface ApiResponseCallback<T> {
        void onSuccess(T data);
        void onFailure(Exception exception);
    }
}