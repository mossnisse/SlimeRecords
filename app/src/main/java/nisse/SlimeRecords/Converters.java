package nisse.SlimeRecords;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import nisse.SlimeRecords.data.SpeciesAttributes;

public class Converters {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static SpeciesAttributes fromString(String value) {
        if (value == null) return null;

        SpeciesAttributes attrs = gson.fromJson(value, SpeciesAttributes.class);

        // Defensive check: ensure the Map is never null after loading
        if (attrs != null && attrs.extraData == null) {
            attrs.extraData = new java.util.HashMap<>();
        }
        return attrs;
    }

    @TypeConverter
    public static String fromAttributes(SpeciesAttributes attributes) {
        return attributes == null ? null : gson.toJson(attributes);
    }
}