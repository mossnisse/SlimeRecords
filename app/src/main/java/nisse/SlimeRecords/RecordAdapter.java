package nisse.SlimeRecords;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.Objects;

public class RecordAdapter extends PagingDataAdapter<RecordWithPhotos, RecordAdapter.LocationViewHolder> {

    public static final DiffUtil.ItemCallback<RecordWithPhotos> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<RecordWithPhotos>() {
                @Override
                public boolean areItemsTheSame(@NonNull RecordWithPhotos oldItem, @NonNull RecordWithPhotos newItem) {
                    return oldItem.location.id == newItem.location.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull RecordWithPhotos oldItem, @NonNull RecordWithPhotos newItem) {
                    return Objects.equals(oldItem.location.note, newItem.location.note) &&
                            oldItem.location.timestamp == newItem.location.timestamp &&
                            Objects.equals(oldItem.location.attributes, newItem.location.attributes) && // Check attributes!
                            oldItem.photos.size() == newItem.photos.size();
                }
            };

    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;

    public RecordAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 1. Inflate the nicer "location_item" layout (CardView)
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.record_item, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        RecordWithPhotos item = getItem(position);

        if (item != null) {
            // Set Note
            String title = "Unknown Species";
            if (item.location.attributes != null && item.location.attributes.taxonName != null && !item.location.attributes.taxonName.isEmpty()) {
                title = item.location.attributes.taxonName;
            } else if (item.location.note != null && !item.location.note.isEmpty()) {
                title = item.location.note;
            }
            holder.tvNote.setText(title);

            // Set Coordinates
            holder.tvCoords.setText(String.format(java.util.Locale.US, "%.4f, %.4f",
                    item.location.latitude, item.location.longitude));

            // Set Date
            holder.tvDate.setText(item.location.localTime);

            // --- Handle Photo Visibility ---
            if (item.photos != null && !item.photos.isEmpty()) {
                holder.ivThumbnail.setVisibility(View.VISIBLE);

                Glide.with(holder.itemView.getContext())
                        .load(item.photos.get(0).filePath)
                        .placeholder(android.R.color.darker_gray)
                        .thumbnail(Glide.with(holder.itemView.getContext())
                                .load(item.photos.get(0).filePath)
                                .override(100, 100)) // Force a tiny version for the thumbnail pass
                        .centerCrop()
                        .into(holder.ivThumbnail);
            } else {
                holder.ivThumbnail.setVisibility(View.GONE);
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
    public interface OnItemClickListener { void onItemClick(RecordWithPhotos item); }
    public interface OnItemLongClickListener { void onItemLongClick(RecordWithPhotos item); }
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