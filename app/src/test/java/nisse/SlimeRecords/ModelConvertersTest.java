package nisse.SlimeRecords;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import nisse.SlimeRecords.data.ObservationRecord;
import nisse.SlimeRecords.data.SpeciesAttributes;

public class ModelConvertersTest {
    @Test
    public void attributesRoundTripAndLegacyJsonGetsExtraDataMap() {
        SpeciesAttributes attributes = new SpeciesAttributes();
        attributes.taxonName = "Lycopodium clavatum";
        attributes.isSpecimen = true;
        attributes.extraData.put("voucher", "A-1");

        SpeciesAttributes restored = Converters.fromString(Converters.fromAttributes(attributes));
        assertEquals(attributes, restored);
        assertEquals(attributes.hashCode(), restored.hashCode());

        SpeciesAttributes legacy = Converters.fromString("{\"taxonName\":\"Legacy\",\"extraData\":null}");
        assertNotNull(legacy.extraData);
        assertTrue(legacy.extraData.isEmpty());
        assertNull(Converters.fromString(null));
        assertNull(Converters.fromAttributes(null));
    }

    @Test
    public void altitudeHandlesLegacyAndRealSeaLevelMeasurements() {
        ObservationRecord record = new ObservationRecord();
        assertFalse(record.hasKnownAltitude());
        record.altitude = 12;
        assertTrue(record.hasKnownAltitude());
        record.altitude = 0;
        record.hasAltitude = true;
        assertTrue(record.hasKnownAltitude());
    }
}
