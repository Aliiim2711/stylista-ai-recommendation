package com.kushal.stylista.services;

import android.util.Log;
import com.kushal.stylista.model.ClothModel;
import com.kushal.stylista.model.UserModel;
import java.util.*;
import java.util.stream.Collectors;

public class RecommendationEngine {
    private static final String TAG = "RecommendationEngine";

    // Scoring weights for different attributes
    private static final int CATEGORY_WEIGHT = 10;
    private static final int BRAND_WEIGHT = 5;
    private static final int COLOR_WEIGHT = 7;
    private static final int MATERIAL_WEIGHT = 6;
    private static final int TAG_WEIGHT = 4;
    private static final int PRICE_WEIGHT = 3;

    // Minimum interactions before using collaborative filtering
    private static final int MIN_INTERACTIONS = 10;

    public static class RecommendationResult {
        private List<ClothModel> recommendedItems;
        private String reasoning;
        private double confidenceScore;

        public RecommendationResult(List<ClothModel> recommendedItems, String reasoning, double confidenceScore) {
            this.recommendedItems = recommendedItems;
            this.reasoning = reasoning;
            this.confidenceScore = confidenceScore;
        }

        public List<ClothModel> getRecommendedItems() {
            return recommendedItems;
        }

        public String getReasoning() {
            return reasoning;
        }

        public double getConfidenceScore() {
            return confidenceScore;
        }
    }

    /**
     * Main recommendation method that combines multiple strategies
     */
    public static RecommendationResult getRecommendations(
            UserModel user,
            List<ClothModel> allClothes,
            int maxRecommendations) {

        Log.d(TAG, "Generating recommendations for user: " + user.getUid());

        // Filter out items user has already seen/interacted with
        List<ClothModel> unseenClothes = filterUnseenItems(allClothes, user);

        if (unseenClothes.isEmpty()) {
            return new RecommendationResult(new ArrayList<>(), "No new items available", 0.0);
        }

        // Choose recommendation strategy based on user's interaction history
        RecommendationResult result;

        if (user.getTotalSwipes() < MIN_INTERACTIONS) {
            // Cold start: Use initial preferences and demographics
            result = getColdStartRecommendations(user, unseenClothes, maxRecommendations);
        } else {
            // Warm start: Use behavioral data
            result = getPersonalizedRecommendations(user, unseenClothes, maxRecommendations);
        }

        Log.d(TAG, "Generated " + result.getRecommendedItems().size() + " recommendations with confidence: " + result.getConfidenceScore());
        return result;
    }

    /**
     * Cold start recommendations for new users with minimal interaction data
     */
    private static RecommendationResult getColdStartRecommendations(
            UserModel user,
            List<ClothModel> clothesList,
            int maxRecommendations) {

        Log.d(TAG, "Using cold start strategy");

        Map<ClothModel, Double> scoreMap = new HashMap<>();

        // Parse initial preferences
        Set<String> initialPreferences = parseInitialPreferences(user.getPreferences());

        for (ClothModel cloth : clothesList) {
            double score = 0.0;

            // Score based on initial preferences
            if (initialPreferences.contains(cloth.getCategory())) {
                score += 50; // High boost for selected categories
            }

            // Score based on gender appropriateness (if applicable)
            score += getGenderScore(user.getGender(), cloth);

            // Add some randomness to ensure diversity
            score += Math.random() * 10;

            scoreMap.put(cloth, score);
        }

        List<ClothModel> recommendations = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<ClothModel, Double>comparingByValue().reversed())
                .limit(maxRecommendations)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String reasoning = "Based on your initial preferences: " + String.join(", ", initialPreferences);
        double confidence = initialPreferences.isEmpty() ? 0.3 : 0.7;

        return new RecommendationResult(recommendations, reasoning, confidence);
    }

    /**
     * Personalized recommendations based on user's swipe history
     */
    private static RecommendationResult getPersonalizedRecommendations(
            UserModel user,
            List<ClothModel> clothesList,
            int maxRecommendations) {

        Log.d(TAG, "Using personalized strategy");

        Map<ClothModel, Double> scoreMap = new HashMap<>();
//        Calculate score for each item
        for (ClothModel cloth : clothesList) {
            double score = calculatePersonalizedScore(user, cloth);
            scoreMap.put(cloth, score);
        }
//THIS CODE: Sorts items by score, returns top ones
        List<ClothModel> recommendations = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<ClothModel, Double>comparingByValue().reversed())
                .limit(maxRecommendations)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String reasoning = buildReasoningString(user);
        double confidence = Math.min(0.95, 0.5 + (user.getTotalSwipes() * 0.01));

        return new RecommendationResult(recommendations, reasoning, confidence);
    }

    /**
     * Calculate personalized score for a clothing item based on user preferences
     */
    private static double calculatePersonalizedScore(UserModel user, ClothModel cloth) {
        double score = 0.0;

        //Main Algorithm : Adding up preference points

        // Category preference
        Integer categoryScore = user.getCategoryPreferences().get(cloth.getCategory());
        if (categoryScore != null) {
            score += categoryScore * CATEGORY_WEIGHT;
        }

        // Brand preference
        Integer brandScore = user.getBrandPreferences().get(cloth.getBrand());
        if (brandScore != null) {
            score += brandScore * BRAND_WEIGHT;
        }

        // Color preferences
        if (cloth.getColors() != null) {
            for (ClothModel.Color color : cloth.getColors()) {
                Integer colorScore = user.getColorPreferences().get(color.getName());
                if (colorScore != null) {
                    score += colorScore * COLOR_WEIGHT;
                }
            }
        }

        // Material preferences
        if (cloth.getMaterials() != null) {
            for (String material : cloth.getMaterials()) {
                Integer materialScore = user.getMaterialPreferences().get(material);
                if (materialScore != null) {
                    score += materialScore * MATERIAL_WEIGHT;
                }
            }
        }

        // Tag preferences
        if (cloth.getTags() != null) {
            for (String tag : cloth.getTags()) {
                Integer tagScore = user.getTagPreferences().get(tag);
                if (tagScore != null) {
                    score += tagScore * TAG_WEIGHT;
                }
            }
        }

        // Price preference (favor items in similar price range to liked items)
        score += getPriceCompatibilityScore(user, cloth);

        // Add diversity factor to avoid too similar recommendations
        score += Math.random() * 5;

        return score;
    }

    /**
     * Get price compatibility score based on user's liked items
     */
    private static double getPriceCompatibilityScore(UserModel user, ClothModel cloth) {
        // This would require access to liked items' prices
        // For now, return a neutral score
        return 0.0;
    }

    /**
     * Filter out items the user has already seen or interacted with
     */
    private static List<ClothModel> filterUnseenItems(List<ClothModel> allClothes, UserModel user) {
        Set<String> seenItems = new HashSet<>();
        seenItems.addAll(user.getViewedItems());
        seenItems.addAll(user.getLikedItems());
        seenItems.addAll(user.getDislikedItems());

        return allClothes.stream()
                .filter(cloth -> !seenItems.contains(cloth.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Parse initial preferences string into a set
     */
    private static Set<String> parseInitialPreferences(String preferences) {
        if (preferences == null || preferences.trim().isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(preferences.split(",")));
    }

    /**
     * Get gender-based score for clothing item
     */
    private static double getGenderScore(String userGender, ClothModel cloth) {
        // This would require gender-specific categorization of clothes
        // For now, return neutral score
        return 0.0;
    }

    /**
     * Build reasoning string for recommendations
     */
    private static String buildReasoningString(UserModel user) {
        StringBuilder reasoning = new StringBuilder("Based on your preferences: ");

        // Find top preferences
        String topCategory = getTopPreference(user.getCategoryPreferences());
        String topBrand = getTopPreference(user.getBrandPreferences());
        String topColor = getTopPreference(user.getColorPreferences());

        List<String> reasonParts = new ArrayList<>();
        if (topCategory != null) reasonParts.add("you like " + topCategory);
        if (topBrand != null) reasonParts.add(topBrand + " brand");
        if (topColor != null) reasonParts.add(topColor + " colors");

        if (reasonParts.isEmpty()) {
            return "Exploring new styles for you";
        }

        reasoning.append(String.join(", ", reasonParts));
        return reasoning.toString();
    }

    /**
     * Get the preference with highest score from a preference map
     */
    private static String getTopPreference(Map<String, Integer> preferenceMap) {
        return preferenceMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Update user preferences based on interaction with a clothing item
     */
    public static void updateUserPreferences(UserModel user, ClothModel cloth, boolean liked) {
        int score = liked ? 1 : -1;

        // Update category preference
        user.updatePreferenceScore(user.getCategoryPreferences(), cloth.getCategory(), score);

        // Update brand preference
        if (cloth.getBrand() != null) {
            user.updatePreferenceScore(user.getBrandPreferences(), cloth.getBrand(), score);
        }

        // Update color preferences
        if (cloth.getColors() != null) {
            for (ClothModel.Color color : cloth.getColors()) {
                user.updatePreferenceScore(user.getColorPreferences(), color.getName(), score);
            }
        }

        // Update material preferences
        if (cloth.getMaterials() != null) {
            for (String material : cloth.getMaterials()) {
                user.updatePreferenceScore(user.getMaterialPreferences(), material, score);
            }
        }

        // Update tag preferences
        if (cloth.getTags() != null) {
            for (String tag : cloth.getTags()) {
                user.updatePreferenceScore(user.getTagPreferences(), tag, score);
            }
        }

        Log.d(TAG, "Updated preferences for user interaction: " + (liked ? "LIKE" : "DISLIKE"));
    }
}