package nisse.SlimeRecords.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Durable source of the next specimen number. */
@Entity(tableName = "specimen_counter")
public class SpecimenCounter {
    /** There is one counter for the application. */
    @PrimaryKey
    public int id = 1;

    public int nextNumber;

    public SpecimenCounter(int nextNumber) {
        this.nextNumber = nextNumber;
    }
}
