package nisse.SlimeRecords.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "species_reference",
        indices = {@Index(value = {"name"}), @Index(value = {"dyntaxaID"})}
)
public class SpeciesReferenceEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    @ColumnInfo(name = "dyntaxaID")
    public Integer dyntaxaID;

    @NonNull
    public String name;

    @NonNull
    public String language;

    public String taxonCategory; // e.g., 'species', 'genus'

    @ColumnInfo(name = "isSynonym")
    public int isSynonym; // 0 for preferred, 1 for synonym

    public String taxonGroup; // e.g., 'Birds', 'Insects'
}