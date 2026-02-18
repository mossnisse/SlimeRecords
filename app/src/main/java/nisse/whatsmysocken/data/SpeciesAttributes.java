package nisse.whatsmysocken.data;

import java.util.HashMap;
import java.util.Map;

public class SpeciesAttributes {
    public String species;
    public String substrate;
    public String habitat;
    public String collector;
    public String specimenNr;
    public String localityDescription;
    public boolean isSpecimen; // true = collection/specimen, false = observation

    // Optional: a map for any additional "unexpected" fields
    public Map<String, String> extraData = new HashMap<>();
}