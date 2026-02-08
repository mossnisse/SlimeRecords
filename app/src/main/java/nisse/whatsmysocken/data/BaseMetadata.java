package nisse.whatsmysocken.data;

import androidx.room.PrimaryKey;

// A simple base for geographical multi polygon metadata (ID and Name)
public abstract class BaseMetadata {
    @PrimaryKey
    public int id;
    public String name;
}
