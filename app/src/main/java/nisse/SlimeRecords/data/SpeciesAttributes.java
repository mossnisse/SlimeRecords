package nisse.SlimeRecords.data;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SpeciesAttributes {
    @SerializedName("taxonName")
    public String taxonName;  // name of taxon, can be other taxon ranks and scientific or vernacular name. DwC scientificName and? Artportalen: Artnamn
    @SerializedName("dyntaxaID")
    public Integer dyntaxaID;  // the taxon id used by dyntaxa / Artfakta
    @SerializedName("substrate")
    public String substrate;   // not in DwC. Artportalen has it divided between substrat och art som substrat DwC associatedTaxa.
    @SerializedName("habitat")
    public String habitat;  // DwC OK. Artportalen: Biotop
    @SerializedName("collector")  // DwC recordedBy. Arportalen: the user that reports and Medobservatör
    public String collector;
    @SerializedName("organismQuantity")
    public Integer organismQuantity;   // DwC OK. don't have organismQuantityType. Artportalen: Antal
    @SerializedName("lifeStage")
    public String lifeStage;  // DwC OK. Artportalen: Ålder-Stadium
    @SerializedName("sex")
    public String sex;  // including DwC caste. Artportalen: Kön
    @SerializedName("activity")
    public String activity;    // DwC behavior, vitality and causeOfDeath. Artportalen: Aktivitet
    @SerializedName("samplingProtocol")
    public String samplingProtocol; // DwC OK. Arportalen: Metod
    @SerializedName("specimenNr")
    public String specimenNr;  // DwC recordNumber / fieldNumber but is only used for collected specimens.
    @SerializedName("isSpecimen")
    public boolean isSpecimen;  //  DwC eventType but is only true / false
    public Map<String, String> extraData = new HashMap<>();

    // Standard equals implementation for DiffUtil to work correctly
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpeciesAttributes that = (SpeciesAttributes) o;
        return isSpecimen == that.isSpecimen &&
                Objects.equals(taxonName, that.taxonName) &&
                Objects.equals(dyntaxaID, that.dyntaxaID) && // Add this
                Objects.equals(substrate, that.substrate) &&
                Objects.equals(habitat, that.habitat) &&
                Objects.equals(organismQuantity, that.organismQuantity) &&
                Objects.equals(lifeStage, that.lifeStage) &&
                Objects.equals(sex, that.sex) &&
                Objects.equals(activity, that.activity) &&
                Objects.equals(samplingProtocol, that.samplingProtocol) &&
                Objects.equals(collector, that.collector) &&
                Objects.equals(specimenNr, that.specimenNr) &&
                Objects.equals(extraData, that.extraData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taxonName, dyntaxaID, substrate, habitat, collector, organismQuantity, lifeStage, sex, activity, samplingProtocol, specimenNr, isSpecimen, extraData);
    }
}