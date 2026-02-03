package nisse.whatsmysocken;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    private final List<String> photoPaths;
    private final OnPhotoListener listener;

    public interface OnPhotoListener {
        void onPhotoClick(String path);
        void onPhotoLongClick(int position);
    }

    public PhotoAdapter(List<String> photoPaths, OnPhotoListener listener) {
        this.photoPaths = photoPaths;
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
        String path = photoPaths.get(position);
        Glide.with(holder.itemView.getContext()).load(path).centerCrop().into(holder.ivGalleryPhoto);

        holder.itemView.setOnClickListener(v -> listener.onPhotoClick(path));
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                // Use getAdapterPosition() instead of the 'position' variable
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    listener.onPhotoLongClick(currentPos);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return photoPaths.size(); }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGalleryPhoto;
        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGalleryPhoto = itemView.findViewById(R.id.iv_gallery_photo);
        }
    }
}