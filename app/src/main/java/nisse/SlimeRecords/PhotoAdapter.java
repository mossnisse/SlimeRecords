package nisse.SlimeRecords;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

import nisse.SlimeRecords.data.PhotoRecord;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    private final List<PhotoRecord> photos;
    private final OnPhotoListener listener;

    public interface OnPhotoListener {
        void onPhotoClick(PhotoRecord photo); // Pass the object
        void onPhotoLongClick(int position);
    }

    public PhotoAdapter(List<PhotoRecord> photos, OnPhotoListener listener) {
        this.photos = photos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.photo_item, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        PhotoRecord photo = photos.get(position);
        String path = photo.filePath;

        Glide.with(holder.itemView.getContext())
                .load(path)
                .placeholder(android.R.color.darker_gray)
                .centerCrop()
                .into(holder.ivGalleryPhoto);

        holder.itemView.setOnClickListener(v -> listener.onPhotoClick(photo));

        holder.itemView.setOnLongClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                listener.onPhotoLongClick(currentPos);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return photos.size(); }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGalleryPhoto;
        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGalleryPhoto = itemView.findViewById(R.id.iv_gallery_photo);
        }
    }
}