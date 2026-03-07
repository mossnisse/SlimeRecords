package nisse.SlimeRecords.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SpeciesAttributes {
    public String species;
    public Integer dyntaxaID;
    public String substrate;
    public String habitat;
    public String collector;
    public Integer quantity;
    public String life_stage;
    public String gender;
    public String activity;
    public String method;
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
                Objects.equals(quantity, that.quantity) &&
                Objects.equals(life_stage, that.life_stage) &&
                Objects.equals(gender, that.gender) &&
                Objects.equals(activity, that.activity) &&
                Objects.equals(method, that.method) &&
                Objects.equals(collector, that.collector) &&
                Objects.equals(specimenNr, that.specimenNr) &&
                Objects.equals(extraData, that.extraData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(species, dyntaxaID, substrate, habitat, collector, quantity, life_stage, gender, activity, method, collector, specimenNr, isSpecimen, extraData);
    }
}