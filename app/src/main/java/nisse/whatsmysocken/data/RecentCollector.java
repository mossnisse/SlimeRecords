package nisse.whatsmysocken.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recent_collectors")
public class RecentCollector {

    @PrimaryKey
    @NonNull  // This is mandatory for String Primary Keys
    public String name;

    public long lastUsed;

    public RecentCollector(@NonNull String name, long lastUsed) {
        this.name = name;
        this.lastUsed = lastUsed;
    }
}
