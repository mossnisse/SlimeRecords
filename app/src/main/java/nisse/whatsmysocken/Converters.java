package nisse.whatsmysocken;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import nisse.whatsmysocken.data.SpeciesAttributes;

public class Converters {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static SpeciesAttributes fromString(String value) {
        return value == null ? null : gson.fromJson(value, SpeciesAttributes.class);
    }

    @TypeConverter
    public static String fromAttributes(SpeciesAttributes attributes) {
        return attributes == null ? null : gson.toJson(attributes);
    }
}
