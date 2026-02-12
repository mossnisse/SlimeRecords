package nisse.whatsmysocken;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide; // Import Glide

public class LocationAdapter extends PagingDataAdapter<LocationWithPhotos, LocationAdapter.LocationViewHolder> {

    public static final DiffUtil.ItemCallback<LocationWithPhotos> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<LocationWithPhotos>() {
                @Override
                public boolean areItemsTheSame(@NonNull LocationWithPhotos oldItem, @NonNull LocationWithPhotos newItem) {
                    return oldItem.location.id == newItem.location.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull LocationWithPhotos oldItem, @NonNull LocationWithPhotos newItem) {
                    return oldItem.location.note.equals(newItem.location.note) &&
                            oldItem.location.timestamp == newItem.location.timestamp &&
                            oldItem.photos.size() == newItem.photos.size();
                }
            };

    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

    public LocationAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 1. Inflate the nicer "location_item" layout (CardView)
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.location_item, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        LocationWithPhotos item = getItem(position);

        if (item != null) {
            // Set Note
            holder.tvNote.setText((item.location.note != null && !item.location.note.isEmpty())
                    ? item.location.note : "No Note");

            // Set Coordinates
            holder.tvCoords.setText(String.format(java.util.Locale.US, "%.4f, %.4f",
                    item.location.latitude, item.location.longitude));

            // Set Date
            holder.tvDate.setText(item.location.localTime);

            // --- Handle Photo Visibility ---
            if (item.photos != null && !item.photos.isEmpty()) {
                // Show the image and load the photo
                holder.ivThumbnail.setVisibility(View.VISIBLE);
                Glide.with(holder.itemView.getContext())
                        .load(item.photos.get(0).filePath)
                        .centerCrop()
                        .into(holder.ivThumbnail);
            } else {
                holder.ivThumbnail.setVisibility(View.GONE);

                // from previous rows in the list
                Glide.with(holder.itemView.getContext()).clear(holder.ivThumbnail);
            }

            // Listeners
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onItemClick(item);
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) longClickListener.onItemLongClick(item);
                return true;
            });
        }
    }

    // --- Interfaces ---
    public interface OnItemClickListener { void onItemClick(LocationWithPhotos item); }
    public interface OnItemLongClickListener { void onItemLongClick(LocationWithPhotos item); }
    public void setOnItemClickListener(OnItemClickListener listener) { this.clickListener = listener; }
    public void setOnItemLongClickListener(OnItemLongClickListener listener) { this.longClickListener = listener; }

    // --- ViewHolder ---
    static class LocationViewHolder extends RecyclerView.ViewHolder {
        // Define the views from location_item.xml
        TextView tvNote;
        TextView tvCoords;
        TextView tvDate;
        ImageView ivThumbnail;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            // Connect them to the IDs in the XML
            tvNote = itemView.findViewById(R.id.tv_note);
            tvCoords = itemView.findViewById(R.id.tv_coords);
            tvDate = itemView.findViewById(R.id.tv_date);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
        }
    }
}