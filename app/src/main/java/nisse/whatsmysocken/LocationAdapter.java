package nisse.whatsmysocken;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.LocationViewHolder> {

    private List<LocationWithPhotos> locationList = new ArrayList<>();
    private OnItemLongClickListener longClickListener;
    private OnItemClickListener clickListener;


    public interface OnItemClickListener {
        void onItemClick(LocationWithPhotos item);
    }
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }
    public interface OnItemLongClickListener {
        void onItemLongClick(LocationRecord location);
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

        // Bind Location Data
        holder.tvNote.setText(current.location.note.isEmpty() ? "No Note" : current.location.note);
        holder.tvCoords.setText(String.format("Lat: %.4f, Lon: %.4f",
                current.location.latitude, current.location.longitude));

        // Bind the Date
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
        String dateString = sdf.format(new java.util.Date(current.location.timestamp));
        holder.tvDate.setText(dateString);

        // Handle regular click
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(current);
        });

        // Handle Photos (Show the first one as a thumbnail if it exists)
        if (current.photos != null && !current.photos.isEmpty()) {
            File imgFile = new File(current.photos.get(0).filePath);
            if (imgFile.exists()) {
                // In a real app, use Glide or Picasso here to avoid UI lag!
                holder.ivThumbnail.setImageURI(android.net.Uri.fromFile(imgFile));
                holder.ivThumbnail.setVisibility(View.VISIBLE);
            }
        } else {
            holder.ivThumbnail.setVisibility(View.GONE);
        }
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(current.location);
                return true; // true means we "consumed" the click
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return locationList.size();
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        TextView tvNote, tvCoords, tvDate;;
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

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).equals(newList.get(newPos));
        }
    }
}