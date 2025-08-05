package com.kushal.stylista.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.kushal.stylista.R;
import com.kushal.stylista.model.ClothModel;
import java.util.List;

public class SwipeCardStackAdapter extends RecyclerView.Adapter<SwipeCardStackAdapter.ViewHolder> {
    private List<ClothModel> clothModelList;

    public SwipeCardStackAdapter(List<ClothModel> clothModelList) {
        this.clothModelList = clothModelList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_cloth_swipe_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClothModel cloth = clothModelList.get(position);

        // Safe image URL generation
        String imageUrl = generateSafeImageUrl(cloth);

        Glide.with(holder.itemView.getContext())
                .load(imageUrl)
                .transform(new CenterCrop(), new RoundedCorners(32))
                .placeholder(R.drawable.sample_cloth)
                .error(R.drawable.sample_cloth)
                .into(holder.image);
    }

    @Override
    public int getItemCount() {
        return clothModelList != null ? clothModelList.size() : 0;
    }

    public void setClothModelList(List<ClothModel> clothModelList) {
        this.clothModelList = clothModelList;
        notifyDataSetChanged();
    }

    public List<ClothModel> getClothModelList() {
        return clothModelList;
    }

    /**
     * Generate safe image URL with null checks
     */
    private String generateSafeImageUrl(ClothModel cloth) {
        // Try to get image from cloth data
        if (cloth.getColors() != null && !cloth.getColors().isEmpty()) {
            ClothModel.Color firstColor = cloth.getColors().get(0);
            if (firstColor != null && firstColor.getImages() != null && !firstColor.getImages().isEmpty()) {
                String imageUrl = firstColor.getImages().get(0);
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    // Check if it's a realistic generated URL
                    if (imageUrl.contains("via.placeholder.com")) {
                        return imageUrl; // Use the realistic color URL
                    }
                }
            }
        }

        // Fallback to generating a simple colored placeholder
        return generateSimpleColoredImage(cloth);
    }

    /**
     * Generate simple colored image with null safety
     */
    private String generateSimpleColoredImage(ClothModel cloth) {
        String color = "CCCCCC"; // Default gray
        String category = "Item";   // Default text

        // Safely get color
        if (cloth.getColors() != null && !cloth.getColors().isEmpty()) {
            ClothModel.Color firstColor = cloth.getColors().get(0);
            if (firstColor != null && firstColor.getHex() != null) {
                String hex = firstColor.getHex();
                if (hex.startsWith("#") && hex.length() == 7) {
                    color = hex.substring(1); // Remove #
                }
            }
        }

        // Safely get category
        if (cloth.getCategory() != null && !cloth.getCategory().trim().isEmpty()) {
            category = cloth.getCategory().replace(" ", "+");
        }

        // Generate safe placeholder URL
        return String.format("https://via.placeholder.com/400x600/%s/333333?text=%s", color, category);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;

        public ViewHolder(@NonNull View view) {
            super(view);
            image = view.findViewById(R.id.imageCloth);
        }
    }
}