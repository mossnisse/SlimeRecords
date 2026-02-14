package nisse.whatsmysocken.data;

import androidx.room.PrimaryKey;
// A simple base for polygon or hole spatial data
public abstract class BaseGeometry {
    @PrimaryKey(autoGenerate = true)
    public int uid;
    public int parentId; // The ID of the District or Province
    public int minN, minE, maxN, maxE;
    public long byteOffset;
    public int vertexCount;
}