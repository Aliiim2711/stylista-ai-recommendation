package com.kushal.stylista;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;

import com.google.firebase.firestore.FirebaseFirestore;
import com.kushal.stylista.adapter.SwipeCardStackAdapter;
import com.kushal.stylista.model.ClothModel;
import com.kushal.stylista.model.UserModel;
import com.kushal.stylista.services.ApiListResponseCallback;
import com.kushal.stylista.services.ApiSaveResponseCallback;
import com.kushal.stylista.services.CartRepository;
import com.kushal.stylista.services.ClothRepository;
import com.kushal.stylista.services.RecommendationEngine;
import com.kushal.stylista.services.UserInteractionService;
import com.kushal.stylista.utils.SnackbarHelper;
import com.kushal.stylista.utils.SnackbarType;

import com.yuyakaido.android.cardstackview.CardStackLayoutManager;
import com.yuyakaido.android.cardstackview.CardStackListener;
import com.yuyakaido.android.cardstackview.CardStackView;
import com.yuyakaido.android.cardstackview.Direction;
import com.yuyakaido.android.cardstackview.Duration;
import com.yuyakaido.android.cardstackview.StackFrom;
import com.yuyakaido.android.cardstackview.SwipeAnimationSetting;
import com.yuyakaido.android.cardstackview.SwipeableMethod;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements CardStackListener {
    public static final String TAG = MainActivity.class.getSimpleName();

    // Repositories and Services
    private final ClothRepository repository = new ClothRepository();
    private final CartRepository cartRepository = new CartRepository();
    private final UserInteractionService userInteractionService = new UserInteractionService();

    // Data
    private List<ClothModel> clothModelList = new ArrayList<>();
    private List<ClothModel> recommendedItems = new ArrayList<>();
    private List<String> favorites = new ArrayList<>();
    private UserModel currentUser;
    private ClothModel currentCloth;
    private String uid;

    // UI Components
    private CardStackView cardStackView;
    private SwipeCardStackAdapter swipeCardStackAdapter;
    private CardStackLayoutManager cardStackLayoutManager;
    private AppCompatImageButton btnUndo, btnReject, btnInfo, btnFavorite, btnAddToCart, btnCart;
    private TextView categoryTextView, titleTextView, priceTextView, recommendationReasonTextView;
    private ProgressBar progressBar;

    // Recommendation settings
    private static final int RECOMMENDATION_BATCH_SIZE = 50;
    private static final int MIN_CARDS_BEFORE_RELOAD = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "MainActivity onCreate started");
        uid = getIntent().getStringExtra("uid");
        Log.d(TAG, "Received UID: " + uid);

        initializeViews();
        initializeSwipeCard();
        setupClickListeners();

        progressBar.setVisibility(VISIBLE);

        // Load data in sequence: user -> favorites -> recommendations
        loadUserData();
    }

    private void initializeViews() {
        categoryTextView = findViewById(R.id.category);
        titleTextView = findViewById(R.id.title);
        priceTextView = findViewById(R.id.tvPrice);
        cardStackView = findViewById(R.id.swipeCardStack);
        progressBar = findViewById(R.id.progressBar);

        btnUndo = findViewById(R.id.btnUndo);
        btnReject = findViewById(R.id.btnReject);
        btnInfo = findViewById(R.id.btnInfo);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnAddToCart = findViewById(R.id.btnAddToCart);
        btnCart = findViewById(R.id.btnCart);
    }

    private void initializeSwipeCard() {
        cardStackLayoutManager = new CardStackLayoutManager(this, this);
        swipeCardStackAdapter = new SwipeCardStackAdapter(clothModelList);
        cardStackLayoutManager.setStackFrom(StackFrom.Top);
        cardStackLayoutManager.setVisibleCount(3);
        cardStackLayoutManager.setTranslationInterval(8.0f);
        cardStackLayoutManager.setScaleInterval(0.95f);
        cardStackLayoutManager.setSwipeThreshold(0.3f);
        cardStackLayoutManager.setMaxDegree(20.0f);
        cardStackLayoutManager.setDirections(Direction.HORIZONTAL);
        cardStackLayoutManager.setCanScrollHorizontal(true);
        cardStackLayoutManager.setCanScrollVertical(true);
        cardStackLayoutManager.setSwipeableMethod(SwipeableMethod.AutomaticAndManual);
        cardStackLayoutManager.setOverlayInterpolator(new LinearInterpolator());
        cardStackView.setLayoutManager(cardStackLayoutManager);
        cardStackView.setAdapter(swipeCardStackAdapter);
    }

    private void setupClickListeners() {
        findViewById(R.id.btnSetting).setOnClickListener(v -> showSettings());
        btnUndo.setOnClickListener(v -> onUndoClicked());
        btnReject.setOnClickListener(v -> onRejectClicked());
        btnFavorite.setOnClickListener(v -> onFavoriteClicked());
        btnInfo.setOnClickListener(v -> showDetailActivity());
        btnAddToCart.setOnClickListener(v -> onAddToCartClicked());
        btnCart.setOnClickListener(v -> showCartList());

        // Long press settings button to upload more data (with protection)
        findViewById(R.id.btnSetting).setOnLongClickListener(v -> {
            bulkAddClothingItems();
            return true;
        });
    }

    // ===========================================
    // BULK CLOTHING UPLOAD METHODS
    // ===========================================

    private void bulkAddClothingItems() {
        // Check current item count first to prevent duplicates
        repository.getClothes(new ApiListResponseCallback<ClothModel>() {
            @Override
            public void onSuccess(List<ClothModel> allClothes) {
                if (allClothes.size() >= 500) {
                    Toast.makeText(MainActivity.this,
                            "✅ Database already has " + allClothes.size() + " items. That's plenty! Upload canceled.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                int remainingNeeded = 500 - allClothes.size();
                int batchesNeeded = (int) Math.ceil(remainingNeeded / 100.0);

                Log.d(TAG, "Current items: " + allClothes.size() + ", uploading " + remainingNeeded + " more items...");
                Toast.makeText(MainActivity.this,
                        "Uploading " + remainingNeeded + " items to reach 500 total... This will take " + (batchesNeeded * 2) + " minutes",
                        Toast.LENGTH_LONG).show();

                uploadBatch(0, batchesNeeded, remainingNeeded);
            }

            @Override
            public void onFailure(Exception exception) {
                Toast.makeText(MainActivity.this, "Could not check item count. Upload canceled for safety.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void uploadBatch(int currentBatch, int totalBatches, int remainingItems) {
        if (currentBatch >= totalBatches) {
            Toast.makeText(this, "✅ Upload complete! Database now has ~500 items. Restart app for amazing recommendations!", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Smart batch upload completed successfully!");
            return;
        }

        // Calculate items for this batch
        int itemsForThisBatch = Math.min(100, remainingItems - (currentBatch * 100));

        List<Map<String, Object>> clothingItems = generateClothingItems(itemsForThisBatch);

        AtomicInteger uploadCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Toast.makeText(this, "Uploading batch " + (currentBatch + 1) + "/" + totalBatches + " (" + itemsForThisBatch + " items)...", Toast.LENGTH_SHORT).show();

        for (Map<String, Object> item : clothingItems) {
            // Add small delay to respect Firebase rate limits
            new android.os.Handler().postDelayed(() -> {
                db.collection("clothes")
                        .add(item)
                        .addOnSuccessListener(documentReference -> {
                            int completed = uploadCount.incrementAndGet();

                            if (completed % 20 == 0) {
                                Log.d(TAG, "Batch " + (currentBatch + 1) + ": Uploaded " + completed + "/" + itemsForThisBatch + " items");
                            }

                            if (completed + errorCount.get() == clothingItems.size()) {
                                // Current batch complete, start next batch after delay
                                new android.os.Handler().postDelayed(() -> {
                                    uploadBatch(currentBatch + 1, totalBatches, remainingItems);
                                }, 2000); // 2 second delay between batches
                            }
                        })
                        .addOnFailureListener(e -> {
                            int errors = errorCount.incrementAndGet();
                            Log.e(TAG, "Failed to upload item in batch " + (currentBatch + 1) + ": " + e.getMessage());

                            if (uploadCount.get() + errors == clothingItems.size()) {
                                // Continue to next batch even with errors
                                new android.os.Handler().postDelayed(() -> {
                                    uploadBatch(currentBatch + 1, totalBatches, remainingItems);
                                }, 2000);
                            }
                        });
            }, 50 * (uploadCount.get() + errorCount.get())); // Small staggered delay
        }
    }

    private List<Map<String, Object>> generateClothingItems(int count) {
        List<Map<String, Object>> items = new ArrayList<>();

        // Expanded categories and brands for more variety
        String[] categories = {
                "T-Shirts", "Hoodies", "Jeans", "Jackets", "Dresses", "Shorts",
                "Sweaters", "Shirts", "Blazers", "Coats", "Skirts", "Women's Clothing"
        };
        String[] brands = {
                "Nike", "Adidas", "Zara", "H&M", "CK", "FashionHub", "DenimPro",
                "CozyWear", "UrbanWear", "GlamourWear", "Gucci", "Prada", "Levi's",
                "Tommy Hilfiger", "Ralph Lauren", "Uniqlo", "Gap", "Forever21"
        };
        String[] colors = {"Black", "White", "Blue", "Red", "Gray", "Navy", "Green", "Pink", "Brown", "Beige", "Purple", "Orange"};
        String[] colorHex = {"#000000", "#FFFFFF", "#0000FF", "#FF0000", "#808080", "#000080", "#008000", "#FFC0CB", "#8B4513", "#F5F5DC", "#800080", "#FFA500"};
        String[] subcategories = {"Men", "Women", "Unisex"};

        Random random = new Random();

        for (int i = 0; i < count; i++) {
            String category = categories[random.nextInt(categories.length)];
            String brand = brands[random.nextInt(brands.length)];

            // Fixed: Ensure color name matches hex code
            int colorIndex = random.nextInt(colors.length);
            String color = colors[colorIndex];
            String hex = colorHex[colorIndex];

            String subcategory = subcategories[random.nextInt(subcategories.length)];

            Map<String, Object> item = new HashMap<>();
            item.put("brand", brand);
            item.put("category", category);
            item.put("name", generateName(category, brand, color, i));
            item.put("price", generatePrice(category));
            item.put("description", generateDescription(category));
            item.put("favorite", false);
            item.put("subcategory", subcategory);

            // Colors array with realistic images
            List<Map<String, Object>> colorsList = new ArrayList<>();
            Map<String, Object> colorMap = new HashMap<>();
            colorMap.put("hex", hex);
            colorMap.put("name", color);
            colorMap.put("images", Arrays.asList(
                    generateRealisticImageUrl(category, color, hex, "front"),
                    generateRealisticImageUrl(category, color, hex, "back")
            ));

            // Sizes array
            List<Map<String, Object>> sizes = new ArrayList<>();
            if (category.equals("Jeans")) {
                sizes.add(createSize("30", random.nextInt(15) + 5));
                sizes.add(createSize("32", random.nextInt(15) + 5));
                sizes.add(createSize("34", random.nextInt(15) + 5));
                sizes.add(createSize("36", random.nextInt(15) + 5));
            } else {
                sizes.add(createSize("S", random.nextInt(15) + 5));
                sizes.add(createSize("M", random.nextInt(20) + 10));
                sizes.add(createSize("L", random.nextInt(15) + 5));
                sizes.add(createSize("XL", random.nextInt(10) + 3));
            }
            colorMap.put("sizes", sizes);
            colorsList.add(colorMap);
            item.put("colors", colorsList);

            // Materials
            item.put("materials", generateMaterials(category));

            // Tags
            item.put("tags", generateTags(category, brand));

            // Rating
            Map<String, Object> rating = new HashMap<>();
            rating.put("average", Math.round((3.5 + random.nextDouble() * 1.5) * 10) / 10.0);
            rating.put("count", random.nextInt(200) + 50);
            item.put("rating", rating);

            items.add(item);
        }

        return items;
    }

    // ===========================================
    // IMAGE AND DATA GENERATION HELPERS
    // ===========================================

    /**
     * Generate realistic image URLs that match the actual color and category
     */
    private String generateRealisticImageUrl(String category, String color, String hex, String view) {
        // Remove # from hex and get clean color code
        String colorCode = hex.startsWith("#") ? hex.substring(1) : hex;

        // Determine text color for contrast
        String textColor = isDarkColor(hex) ? "FFFFFF" : "333333";

        // Clean category name for URL
        String cleanCategory = category.replace("'", "").replace(" ", "+");

        // Generate realistic placeholder URL with actual colors
        return String.format("https://via.placeholder.com/400x600/%s/%s?text=%s+%s",
                colorCode, textColor, cleanCategory, view.toUpperCase());
    }

    /**
     * Check if color is dark (for text contrast)
     */
    private boolean isDarkColor(String hexColor) {
        try {
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
            int color = Integer.parseInt(hex, 16);
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            // Calculate luminance
            double luminance = (0.299 * red + 0.587 * green + 0.114 * blue);
            return luminance < 128;
        } catch (Exception e) {
            return false; // Default to light
        }
    }

    // Helper methods for data generation
    private String generateName(String category, String brand, String color, int index) {
        String[] adjectives = {"Classic", "Premium", "Modern", "Stylish", "Casual", "Elegant", "Comfort", "Essential"};
        String adjective = adjectives[new Random().nextInt(adjectives.length)];
        return adjective + " " + color + " " + category.substring(0, category.length()-1) + " #" + (index + 1);
    }

    private double generatePrice(String category) {
        Random random = new Random();
        double basePrice;

        switch (category) {
            case "T-Shirts": basePrice = 25; break;
            case "Jeans": basePrice = 50; break;
            case "Hoodies": basePrice = 45; break;
            case "Jackets": basePrice = 80; break;
            case "Dresses": basePrice = 65; break;
            case "Sweaters": basePrice = 55; break;
            case "Shorts": basePrice = 30; break;
            default: basePrice = 40;
        }

        double variation = 0.7 + (random.nextDouble() * 0.6); // ±30%
        return Math.round(basePrice * variation * 100) / 100.0;
    }

    private String generateDescription(String category) {
        switch (category) {
            case "T-Shirts": return "Premium cotton t-shirt with comfortable fit and modern style.";
            case "Jeans": return "Classic denim jeans with perfect fit and durable construction.";
            case "Hoodies": return "Cozy hoodie with soft interior, perfect for casual wear.";
            case "Jackets": return "Stylish jacket with premium materials and contemporary design.";
            case "Dresses": return "Beautiful dress with elegant design for special occasions.";
            case "Sweaters": return "Warm sweater with soft fabric, ideal for cooler weather.";
            case "Shorts": return "Comfortable shorts perfect for summer and active lifestyle.";
            default: return "High-quality " + category.toLowerCase() + " with premium materials.";
        }
    }

    private List<String> generateMaterials(String category) {
        switch (category) {
            case "T-Shirts": return Arrays.asList("100% Cotton");
            case "Jeans": return Arrays.asList("98% Cotton", "2% Elastane");
            case "Hoodies": return Arrays.asList("Cotton", "Polyester");
            case "Jackets": return Arrays.asList("Polyester", "Nylon");
            case "Dresses": return Arrays.asList("Polyester", "Spandex");
            case "Sweaters": return Arrays.asList("Wool", "Acrylic");
            case "Shorts": return Arrays.asList("Cotton", "Elastane");
            default: return Arrays.asList("Cotton", "Polyester");
        }
    }

    private List<String> generateTags(String category, String brand) {
        List<String> tags = new ArrayList<>();

        // Category-based tags
        switch (category) {
            case "T-Shirts": tags.addAll(Arrays.asList("casual", "basic", "cotton")); break;
            case "Jeans": tags.addAll(Arrays.asList("denim", "casual", "classic")); break;
            case "Hoodies": tags.addAll(Arrays.asList("hoodie", "warm", "casual")); break;
            case "Jackets": tags.addAll(Arrays.asList("jacket", "outerwear", "stylish")); break;
            case "Dresses": tags.addAll(Arrays.asList("dress", "elegant", "formal")); break;
            case "Sweaters": tags.addAll(Arrays.asList("sweater", "warm", "cozy")); break;
            case "Shorts": tags.addAll(Arrays.asList("shorts", "summer", "casual")); break;
        }

        // Brand-based tags
        if (Arrays.asList("Nike", "Adidas").contains(brand)) {
            tags.add("sporty");
        }

        return tags;
    }

    private Map<String, Object> createSize(String size, int stock) {
        Map<String, Object> sizeMap = new HashMap<>();
        sizeMap.put("size", size);
        sizeMap.put("stock", stock);
        return sizeMap;
    }

    // ===========================================
    // RECOMMENDATION SYSTEM METHODS
    // ===========================================

    private void loadUserData() {
        Log.d(TAG, "loadUserData called");
        userInteractionService.getUserWithRecommendationData(new UserInteractionService.ApiResponseCallback<UserModel>() {
            @Override
            public void onSuccess(UserModel user) {
                Log.d(TAG, "User data loaded successfully");
                currentUser = user;
                Log.d(TAG, "User data loaded. Total swipes: " + user.getTotalSwipes());
                loadFavorites();
            }

            @Override
            public void onFailure(Exception exception) {
                Log.e(TAG, "Failed to load user data: " + exception.getMessage());
                // Create basic user model and continue
                currentUser = new UserModel();
                currentUser.setUid(uid);
                loadFavorites();
            }
        });
    }

    private void loadFavorites() {
        repository.getFavoriteClothesId(new ApiListResponseCallback<String>() {
            @Override
            public void onSuccess(List<String> dataList) {
                favorites = dataList;
                loadRecommendations();
            }

            @Override
            public void onFailure(Exception exception) {
                Log.e(TAG, "Failed to load favorites", exception);
                favorites = new ArrayList<>();
                loadRecommendations();
            }
        });
    }

    private void loadRecommendations() {
        // Get all clothes first
        repository.getClothes(new ApiListResponseCallback<ClothModel>() {
            @Override
            public void onSuccess(List<ClothModel> allClothes) {
                // Set favorite status
                for (ClothModel cloth : allClothes) {
                    cloth.setFavorite(favorites.contains(cloth.getId()));
                }

                // Generate recommendations
                RecommendationEngine.RecommendationResult result =
                        RecommendationEngine.getRecommendations(currentUser, allClothes, RECOMMENDATION_BATCH_SIZE);

                recommendedItems = result.getRecommendedItems();

                Log.d(TAG, "Generated " + recommendedItems.size() + " recommendations");
                Log.d(TAG, "Recommendation reasoning: " + result.getReasoning());

                // Update adapter with recommendations
                swipeCardStackAdapter.setClothModelList(recommendedItems);
                progressBar.setVisibility(GONE);

                // Show message if no recommendations
                if (recommendedItems.isEmpty()) {
                    SnackbarHelper.showSnackbar(findViewById(android.R.id.content),
                            "No new recommendations available. Try adjusting your preferences!",
                            SnackbarType.GENERAL);
                }
            }

            @Override
            public void onFailure(Exception exception) {
                Log.e(TAG, "Failed to load clothes for recommendations", exception);
                progressBar.setVisibility(GONE);
                SnackbarHelper.showSnackbar(findViewById(android.R.id.content),
                        "Failed to load recommendations", SnackbarType.ERROR);
            }
        });
    }

    private void checkAndReloadRecommendations() {
        int remainingCards = swipeCardStackAdapter.getItemCount() - cardStackLayoutManager.getTopPosition();

        if (remainingCards <= MIN_CARDS_BEFORE_RELOAD) {
            Log.d(TAG, "Low on cards, loading more recommendations...");

            // Show loading indicator
            progressBar.setVisibility(VISIBLE);

            repository.getClothes(new ApiListResponseCallback<ClothModel>() {
                @Override
                public void onSuccess(List<ClothModel> allClothes) {
                    // Set favorite status
                    for (ClothModel cloth : allClothes) {
                        cloth.setFavorite(favorites.contains(cloth.getId()));
                    }

                    // Generate new recommendations
                    RecommendationEngine.RecommendationResult result =
                            RecommendationEngine.getRecommendations(currentUser, allClothes, RECOMMENDATION_BATCH_SIZE);

                    List<ClothModel> newRecommendations = result.getRecommendedItems();

                    // Add new recommendations to existing list
                    recommendedItems.addAll(newRecommendations);
                    swipeCardStackAdapter.setClothModelList(recommendedItems);

                    progressBar.setVisibility(GONE);

                    Log.d(TAG, "Added " + newRecommendations.size() + " new recommendations");
                }

                @Override
                public void onFailure(Exception exception) {
                    Log.e(TAG, "Failed to reload recommendations", exception);
                    progressBar.setVisibility(GONE);
                }
            });
        }
    }

    // ===========================================
    // UI EVENT HANDLERS
    // ===========================================

    private void showDetailActivity() {
        ClothDetailActivity.start(this, currentCloth);
    }

    private void showSettings() {
        Intent intent = (new Intent(this, SettingsActivity.class).putExtra("uid", uid));
        startActivity(intent);
    }

    private void showCartList() {
        Intent intent = new Intent(this, CartListActivity.class);
        startActivity(intent);
    }

    private void onUndoClicked() {
        cardStackView.rewind();
    }

    private void onRejectClicked() {
        swipeCard(Direction.Left);
    }

    private void onFavoriteClicked() {
        swipeCard(Direction.Top);
        addToFavorite();
    }

    private void onAddToCartClicked() {
        swipeCard(Direction.Right);
    }

    private void swipeCard(Direction direction) {
        SwipeAnimationSetting setting = new SwipeAnimationSetting.Builder()
                .setDirection(direction)
                .setDuration(Duration.Normal.duration)
                .setInterpolator(new AccelerateInterpolator())
                .build();
        cardStackLayoutManager.setSwipeAnimationSetting(setting);
        cardStackView.swipe();
    }

    // ===========================================
    // CARD STACK LISTENER METHODS
    // ===========================================

    @Override
    public void onCardAppeared(@Nullable View view, int position) {
        Log.d(TAG, "onCardAppeared: " + position);

        if (position < swipeCardStackAdapter.getItemCount()) {
            currentCloth = swipeCardStackAdapter.getClothModelList().get(position);

            // UPDATED: Show brand + category for better user info
            categoryTextView.setText(currentCloth.getBrand() + " " + currentCloth.getCategory());
            titleTextView.setText(currentCloth.getName());
            priceTextView.setText(getString(R.string.price, currentCloth.getPrice()));

            btnFavorite.setImageResource(favorites.contains(currentCloth.getId()) ?
                    R.drawable.ic_favorite : R.drawable.ic_favorite_outline);

            // Record item view for recommendations
            userInteractionService.recordItemView(currentCloth);

            // Check if we need to load more recommendations
            checkAndReloadRecommendations();
        }
    }

    @Override
    public void onCardDragging(@Nullable Direction direction, float ratio) {
        // Optional: Add visual feedback based on swipe direction
    }

    @Override
    public void onCardSwiped(@Nullable Direction direction) {
        Log.d(TAG, "onCardSwiped: " + direction);

        if (currentCloth != null) {
            boolean liked = (direction == Direction.Right || direction == Direction.Top);

            // Record the interaction for recommendations
            userInteractionService.recordSwipeInteraction(currentCloth, liked, new ApiSaveResponseCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Swipe interaction recorded successfully");
                }

                @Override
                public void onFailure(Exception exception) {
                    Log.e(TAG, "Failed to record swipe interaction", exception);
                }
            });

            // Handle specific swipe actions
            if (direction == Direction.Right) {
                addToCart();
            } else if (direction == Direction.Top) {
                // Favorite action already handled in onFavoriteClicked
            }
        }
    }

    @Override
    public void onCardRewound() {
        Log.d(TAG, "onCardRewound");
    }

    @Override
    public void onCardCanceled() {
        Log.d(TAG, "onCardCanceled");
    }

    @Override
    public void onCardDisappeared(@Nullable View view, int position) {
        if (position == swipeCardStackAdapter.getItemCount() - 1) {
            categoryTextView.setText("");
            titleTextView.setText("");
            priceTextView.setText("");
            currentCloth = null;
        }
    }

    // ===========================================
    // FAVORITE AND CART METHODS
    // ===========================================

    private void addToFavorite() {
        if (currentCloth != null) {
            repository.updateFavoriteCloth(currentCloth.getId(), new ApiSaveResponseCallback() {
                @Override
                public void onSuccess() {
                    favorites.add(currentCloth.getId());
                    Log.d(TAG, "Added to favorites: " + currentCloth.getName());
                }

                @Override
                public void onFailure(Exception exception) {
                    exception.printStackTrace();
                    SnackbarHelper.showSnackbar(findViewById(android.R.id.content),
                            exception.getMessage(), SnackbarType.ERROR);
                    onUndoClicked();
                }
            });
        }
    }

    private void addToCart() {
        if (currentCloth != null && currentCloth.getColors() != null &&
                !currentCloth.getColors().isEmpty() &&
                currentCloth.getColors().get(0).getSizes() != null &&
                !currentCloth.getColors().get(0).getSizes().isEmpty()) {

            cartRepository.addToCart(
                    currentCloth,
                    1,
                    currentCloth.getColors().get(0).getName(),
                    currentCloth.getColors().get(0).getSizes().get(0).getSize(),
                    new ApiSaveResponseCallback() {
                        @Override
                        public void onSuccess() {
                            SnackbarHelper.showSnackbar(findViewById(android.R.id.content),
                                    "Added to cart successfully", SnackbarType.SUCCESS);
                        }

                        @Override
                        public void onFailure(Exception exception) {
                            exception.printStackTrace();
                            cardStackView.rewind();
                            SnackbarHelper.showSnackbar(findViewById(android.R.id.content),
                                    exception.getMessage(), SnackbarType.ERROR);
                        }
                    });
        }
    }
}