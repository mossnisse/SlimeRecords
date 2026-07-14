package nisse.SlimeRecords.data;

/** Identity of a stored record, used by the importer for duplicate detection. */
public class RecordFingerprint {
    public long id;
    public double latitude;
    public double longitude;
    public String localTime;
}
