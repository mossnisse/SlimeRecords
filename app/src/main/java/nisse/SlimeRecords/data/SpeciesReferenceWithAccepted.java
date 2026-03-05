package nisse.SlimeRecords.data;

import androidx.room.Embedded;

public class SpeciesReferenceWithAccepted {
    // This tells Room to fill the standard entity fields from s1.*
    @Embedded
    public SpeciesReferenceEntity species;

    // This matches the "AS acceptedName" in your SQL query
    public String acceptedName;

    /**
     * Helper methods to make your Activity code cleaner
     * so you don't have to type .species.name everywhere
     */
    public String getName() { return species.name; }
    public int getTaxonID() { return species.dyntaxaID; }
    public String getGroup() { return species.taxonGroup; }
    public int getIsSynonym() { return species.isSynonym; }
}