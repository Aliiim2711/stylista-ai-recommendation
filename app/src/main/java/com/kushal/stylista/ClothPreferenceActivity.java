package com.kushal.stylista;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.kushal.stylista.services.UserInteractionService;

import java.util.*;

public class ClothPreferenceActivity extends AppCompatActivity {

    private static final int MAX_SELECTIONS = 5;

    private List<String> selectedPreferences = new ArrayList<>();
    private int selectedCount = 0;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private UserInteractionService userInteractionService;

    private String email, password, provider, idToken, accessToken;
    private String firstName, lastName, dob, gender;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cloth_preference);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        userInteractionService = new UserInteractionService();

        // Get values from Intent
        email = getIntent().getStringExtra("email");
        password = getIntent().getStringExtra("password"); // might be null
        provider = getIntent().getStringExtra("provider"); // email, google, facebook
        idToken = getIntent().getStringExtra("idToken");
        accessToken = getIntent().getStringExtra("accessToken");

        firstName = getIntent().getStringExtra("firstName");
        lastName = getIntent().getStringExtra("lastName");
        dob = getIntent().getStringExtra("dob");
        gender = getIntent().getStringExtra("gender");

        setupPreferenceSelection();
        setupButtons();
    }

    private void setupPreferenceSelection() {
        // CLEAN: Only categories that actually exist in your database
        Map<Integer, String> preferenceMap = new HashMap<>();
        preferenceMap.put(R.id.boxTShirt, "T-Shirts");        // ✅ Database has this
        preferenceMap.put(R.id.shirt, "Shirts");              // ✅ Assuming you have this
        preferenceMap.put(R.id.hoodie, "Hoodies");            // ✅ Database has this
        preferenceMap.put(R.id.pants, "Jeans");               // ✅ Database has this
        preferenceMap.put(R.id.suit, "Blazers");              // ✅ Database has this
        preferenceMap.put(R.id.jacket, "Jackets");            // ✅ Database has this
        preferenceMap.put(R.id.skirt, "Skirts");              // ✅ Database has this
        preferenceMap.put(R.id.dress, "Dresses");             // ✅ Database has this
        preferenceMap.put(R.id.shorts, "Shorts");             // ✅ Database has this

        // ONLY include UI elements that exist in the updated layout (9 categories)
        List<LinearLayout> selectableBoxes = Arrays.asList(
                findViewById(R.id.boxTShirt), findViewById(R.id.shirt), findViewById(R.id.hoodie),
                findViewById(R.id.pants), findViewById(R.id.suit), findViewById(R.id.jacket),
                findViewById(R.id.skirt), findViewById(R.id.dress), findViewById(R.id.shorts)
        );

        for (LinearLayout box : selectableBoxes) {
            box.setOnClickListener(v -> {
                String preference = preferenceMap.get(box.getId());
                boolean isSelected = "selected".equals(box.getTag());

                if (isSelected) {
                    box.setBackground(ContextCompat.getDrawable(this, R.drawable.box_background));
                    box.setTag(null);
                    selectedCount--;
                    selectedPreferences.remove(preference);
                } else if (selectedCount < MAX_SELECTIONS) {
                    box.setBackground(ContextCompat.getDrawable(this, R.drawable.selected_box_background));
                    box.setTag("selected");
                    selectedCount++;
                    selectedPreferences.add(preference);
                } else {
                    Toast.makeText(this, "Max 5 preferences allowed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void setupButtons() {
        Button startButton = findViewById(R.id.startButton);
        Button skipButton = findViewById(R.id.skipButton);

        startButton.setOnClickListener(v -> {
            if (selectedCount > 0) {
                registerFirebaseUser();
            } else {
                Toast.makeText(this, "Please select at least one preference", Toast.LENGTH_SHORT).show();
            }
        });

        skipButton.setOnClickListener(v -> {
            selectedPreferences.clear();
            registerFirebaseUser();
        });
    }

    private void registerFirebaseUser() {
        if ("google".equals(provider)) {
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
            signInWithCredential(credential);
        } else if ("facebook".equals(provider)) {
            AuthCredential credential = FacebookAuthProvider.getCredential(accessToken);
            signInWithCredential(credential);
        } else { // Email & Password
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> saveUserProfile(auth.getCurrentUser().getUid()))
                    .addOnFailureListener(e -> Toast.makeText(this, "Email signup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void signInWithCredential(AuthCredential credential) {
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> saveUserProfile(auth.getCurrentUser().getUid()))
                .addOnFailureListener(e -> Toast.makeText(this, "Auth failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveUserProfile(String uid) {
        String preferences = String.join(",", selectedPreferences);

        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", email);
        userProfile.put("firstName", firstName);
        userProfile.put("lastName", lastName);
        userProfile.put("dob", dob);
        userProfile.put("gender", gender);
        userProfile.put("preferences", preferences);
        userProfile.put("profileCompleted", true);
        userProfile.put("createdAt", FieldValue.serverTimestamp());

        // Initialize recommendation data with CORRECT categories
        userProfile.put("categoryPreferences", initializeCategoryPreferences());
        userProfile.put("brandPreferences", new HashMap<String, Integer>());
        userProfile.put("colorPreferences", new HashMap<String, Integer>());
        userProfile.put("materialPreferences", new HashMap<String, Integer>());
        userProfile.put("tagPreferences", new HashMap<String, Integer>());
        userProfile.put("likedItems", new ArrayList<String>());
        userProfile.put("dislikedItems", new ArrayList<String>());
        userProfile.put("viewedItems", new ArrayList<String>());
        userProfile.put("totalSwipes", 0);
        userProfile.put("totalLikes", 0);
        userProfile.put("totalDislikes", 0);
        userProfile.put("lastActivity", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    Log.d("ClothPreferenceActivity", "User profile saved with recommendation data");
                    Toast.makeText(this, "Welcome to Stylista!", Toast.LENGTH_SHORT).show();

                    // Pass uid to MainActivity for recommendation loading
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.putExtra("uid", uid);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e("ClothPreferenceActivity", "Failed to save user profile", e);
                    Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Initialize category preferences based on user's initial selections
     * CLEAN: Using only categories that exist in database
     */
    private Map<String, Integer> initializeCategoryPreferences() {
        Map<String, Integer> categoryPrefs = new HashMap<>();

        // CLEAN: Only categories that actually exist in your database
        String[] allCategories = {
                "T-Shirts",           // ✅ Your database has this
                "Shirts",             // ✅ Assuming you have this
                "Hoodies",            // ✅ Your database has this
                "Jeans",              // ✅ Your database has this
                "Blazers",            // ✅ Your database has this
                "Jackets",            // ✅ Your database has this
                "Skirts",             // ✅ Your database has this
                "Dresses",            // ✅ Your database has this
                "Shorts",             // ✅ Your database has this
                "Sweaters",           // ✅ Your database has this
                "Coats"               // ✅ Your database has this
        };

        for (String category : allCategories) {
            if (selectedPreferences.contains(category)) {
                // Give selected preferences a boost
                categoryPrefs.put(category, 5);
                Log.d("ClothPreferenceActivity", "Boosted preference for: " + category);
            } else {
                // Start others at neutral
                categoryPrefs.put(category, 0);
            }
        }

        return categoryPrefs;
    }
}