package nisse.SlimeRecords.data;

import androidx.annotation.NonNull;
import androidx.room.PrimaryKey;

// A simple base for geographical multi polygon metadata (ID and Name)
public abstract class NamedRegion {
    @PrimaryKey
    public int id;
    @NonNull
    public String name = "";
}