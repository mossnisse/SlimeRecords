package nisse.whatsmysocken;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import com.bumptech.glide.Glide;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.LocationViewHolder> {
    private final List<LocationWithPhotos> locationList = new ArrayList<>();
    private OnItemLongClickListener longClickListener;
    private OnItemClickListener clickListener;


    public interface OnItemClickListener {
        void onItemClick(LocationWithPhotos item);
    }
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }
    public interface OnItemLongClickListener {
        void onItemLongClick(LocationWithPhotos item);
    }
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.location_item, parent, false);
        return new LocationViewHolder(view);
    }

    // High-performance list updating
    public void setLocations(List<LocationWithPhotos> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new LocationDiffCallback(this.locationList, newList));
        this.locationList.clear();
        this.locationList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        LocationWithPhotos current = locationList.get(position);

        // FIX 1: Explicitly use Locale.US to avoid decimal point/comma bugs
        holder.tvNote.setText(current.location.note.isEmpty() ? "No Note" : current.location.note);
        holder.tvCoords.setText(String.format(java.util.Locale.US, "Lat: %.4f, Lon: %.4f (±%.0fm)",
                current.location.latitude,
                current.location.longitude,
                current.location.accuracy));
        holder.tvDate.setText(current.location.localTime);

        if (current.photos != null && !current.photos.isEmpty()) {
            String photoPath = current.photos.get(0).filePath;
            holder.ivThumbnail.setVisibility(View.VISIBLE);

            // FIX 2: Modern thumbnail loading (sizeMultiplier)
            Glide.with(holder.itemView.getContext())
                    .load(photoPath)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .error(android.R.drawable.stat_notify_error)
                    .override(200, 200)
                    .thumbnail(Glide.with(holder.itemView.getContext())
                            .load(photoPath)
                            .sizeMultiplier(0.1f)) // This replaces thumbnail(0.1f)
                    .into(holder.ivThumbnail);
        } else {
            Glide.with(holder.itemView.getContext()).clear(holder.ivThumbnail);
            holder.ivThumbnail.setVisibility(View.GONE);
        }

        // Click Listeners
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(current);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(current); // 'current' is LocationWithPhotos
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return locationList.size();
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        TextView tvNote, tvCoords, tvDate;
        ImageView ivThumbnail;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNote = itemView.findViewById(R.id.tv_note);
            tvCoords = itemView.findViewById(R.id.tv_coords);
            tvDate = itemView.findViewById(R.id.tv_date);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
        }
    }

    // Callback for DiffUtil to calculate changes
    private static class LocationDiffCallback extends DiffUtil.Callback {
        private final List<LocationWithPhotos> oldList, newList;

        public LocationDiffCallback(List<LocationWithPhotos> oldList, List<LocationWithPhotos> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() { return oldList.size(); }
        @Override
        public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).location.id == newList.get(newPos).location.id;
        }

        @Override public boolean areContentsTheSame(int oldPos, int newPos) {
            LocationWithPhotos oldItem = oldList.get(oldPos);
            LocationWithPhotos newItem = newList.get(newPos); // Compare LocationRecord fields
            LocationRecord o = oldItem.location;
            LocationRecord n = newItem.location;
            if (Double.compare(o.latitude, n.latitude) != 0) return false;
            if (Double.compare(o.longitude, n.longitude) != 0) return false;
            if (Float.compare(o.accuracy, n.accuracy) != 0) return false;
            if (o.timestamp != n.timestamp) return false;
            if (!safeEquals(o.note, n.note)) return false;
            if (!safeEquals(o.localTime, n.localTime)) return false;
            // Compare photo list sizes
            if (oldItem.photos == null && newItem.photos != null) return false;
            if (oldItem.photos != null && newItem.photos == null) return false;
            if (oldItem.photos != null) {
                if (oldItem.photos.size() != newItem.photos.size()) return false;
                // Compare each photo path
                for (int i = 0; i < oldItem.photos.size(); i++) {
                    String oldPath = oldItem.photos.get(i).filePath;
                    String newPath = newItem.photos.get(i).filePath;
                    if (!safeEquals(oldPath, newPath)) return false;
                }
            }
            return true;
        }
        private boolean safeEquals(String a, String b) {
            return (a == null && b == null) || (a != null && a.equals(b));
        }
    }
}