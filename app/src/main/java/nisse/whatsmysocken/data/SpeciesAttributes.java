package nisse.whatsmysocken.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SpeciesAttributes {
    public String species;
    public Integer dyntaxaID;
    public String substrate;
    public String habitat;
    public String collector;
    public String specimenNr;
    public boolean isSpecimen;
    public Map<String, String> extraData = new HashMap<>();

    // Standard equals implementation for DiffUtil to work correctly
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpeciesAttributes that = (SpeciesAttributes) o;
        return isSpecimen == that.isSpecimen &&
                Objects.equals(species, that.species) &&
                Objects.equals(dyntaxaID, that.dyntaxaID) && // Add this
                Objects.equals(substrate, that.substrate) &&
                Objects.equals(habitat, that.habitat) &&
                Objects.equals(collector, that.collector) &&
                Objects.equals(specimenNr, that.specimenNr) &&
                Objects.equals(extraData, that.extraData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(species, dyntaxaID, substrate, habitat, collector, specimenNr, isSpecimen, extraData);
    }
}