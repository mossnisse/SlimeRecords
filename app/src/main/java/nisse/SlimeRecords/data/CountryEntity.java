package nisse.SlimeRecords.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "countries")
public class CountryEntity {
    @PrimaryKey
    @NonNull
    public String code; // The 2-letter ISO code (e.g., "SE", "AF")

    @ColumnInfo(name = "name_en")
    public String nameEn;

    @ColumnInfo(name = "name_sv")
    public String nameSv;
}
