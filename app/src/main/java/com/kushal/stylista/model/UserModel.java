package com.kushal.stylista.model;

import com.google.firebase.firestore.FieldValue;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class UserModel {
    private String uid;
    private String email;
    private String firstName;
    private String lastName;
    private String dob;
    private String gender;
    private String preferences; // Initial preferences from ClothPreferenceActivity
    private boolean profileCompleted;
    private Object createdAt;

    // Recommendation-related fields
    private Map<String, Integer> categoryPreferences; // category -> score
    private Map<String, Integer> brandPreferences; // brand -> score
    private Map<String, Integer> colorPreferences; // color -> score
    private Map<String, Integer> materialPreferences; // material -> score
    private Map<String, Integer> tagPreferences; // tag -> score
    private List<String> likedItems; // Items user swiped right/favorited
    private List<String> dislikedItems; // Items user swiped left
    private List<String> viewedItems; // Items user has seen
    private int totalSwipes;
    private int totalLikes;
    private int totalDislikes;
    private Object lastActivity;

    // Constructors
    public UserModel() {
        this.categoryPreferences = new HashMap<>();
        this.brandPreferences = new HashMap<>();
        this.colorPreferences = new HashMap<>();
        this.materialPreferences = new HashMap<>();
        this.tagPreferences = new HashMap<>();
        this.likedItems = new ArrayList<>();
        this.dislikedItems = new ArrayList<>();
        this.viewedItems = new ArrayList<>();
        this.totalSwipes = 0;
        this.totalLikes = 0;
        this.totalDislikes = 0;
    }

    // Getters and Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPreferences() {
        return preferences;
    }

    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public void setProfileCompleted(boolean profileCompleted) {
        this.profileCompleted = profileCompleted;
    }

    public Object getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Object createdAt) {
        this.createdAt = createdAt;
    }

    // Recommendation-related getters and setters
    public Map<String, Integer> getCategoryPreferences() {
        return categoryPreferences;
    }

    public void setCategoryPreferences(Map<String, Integer> categoryPreferences) {
        this.categoryPreferences = categoryPreferences;
    }

    public Map<String, Integer> getBrandPreferences() {
        return brandPreferences;
    }

    public void setBrandPreferences(Map<String, Integer> brandPreferences) {
        this.brandPreferences = brandPreferences;
    }

    public Map<String, Integer> getColorPreferences() {
        return colorPreferences;
    }

    public void setColorPreferences(Map<String, Integer> colorPreferences) {
        this.colorPreferences = colorPreferences;
    }

    public Map<String, Integer> getMaterialPreferences() {
        return materialPreferences;
    }

    public void setMaterialPreferences(Map<String, Integer> materialPreferences) {
        this.materialPreferences = materialPreferences;
    }

    public Map<String, Integer> getTagPreferences() {
        return tagPreferences;
    }

    public void setTagPreferences(Map<String, Integer> tagPreferences) {
        this.tagPreferences = tagPreferences;
    }

    public List<String> getLikedItems() {
        return likedItems;
    }

    public void setLikedItems(List<String> likedItems) {
        this.likedItems = likedItems;
    }

    public List<String> getDislikedItems() {
        return dislikedItems;
    }

    public void setDislikedItems(List<String> dislikedItems) {
        this.dislikedItems = dislikedItems;
    }

    public List<String> getViewedItems() {
        return viewedItems;
    }

    public void setViewedItems(List<String> viewedItems) {
        this.viewedItems = viewedItems;
    }

    public int getTotalSwipes() {
        return totalSwipes;
    }

    public void setTotalSwipes(int totalSwipes) {
        this.totalSwipes = totalSwipes;
    }

    public int getTotalLikes() {
        return totalLikes;
    }

    public void setTotalLikes(int totalLikes) {
        this.totalLikes = totalLikes;
    }

    public int getTotalDislikes() {
        return totalDislikes;
    }

    public void setTotalDislikes(int totalDislikes) {
        this.totalDislikes = totalDislikes;
    }

    public Object getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Object lastActivity) {
        this.lastActivity = lastActivity;
    }

    // Utility methods for recommendation system
    public void addLikedItem(String itemId) {
        if (!likedItems.contains(itemId)) {
            likedItems.add(itemId);
        }
        // Remove from disliked if it was there
        dislikedItems.remove(itemId);
        totalLikes++;
        totalSwipes++;
    }

    public void addDislikedItem(String itemId) {
        if (!dislikedItems.contains(itemId)) {
            dislikedItems.add(itemId);
        }
        // Remove from liked if it was there
        likedItems.remove(itemId);
        totalDislikes++;
        totalSwipes++;
    }

    public void addViewedItem(String itemId) {
        if (!viewedItems.contains(itemId)) {
            viewedItems.add(itemId);
        }
    }

    public void updatePreferenceScore(Map<String, Integer> preferenceMap, String key, int score) {
        preferenceMap.put(key, preferenceMap.getOrDefault(key, 0) + score);
    }

    public double getLikeRatio() {
        return totalSwipes > 0 ? (double) totalLikes / totalSwipes : 0.0;
    }
}