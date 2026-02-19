package nisse.whatsmysocken;
import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import nisse.whatsmysocken.coords.Coordinates;
import nisse.whatsmysocken.data.SpatialResolver;
import nisse.whatsmysocken.data.SpeciesAttributes;

public class LabelHtmlGenerator {

    public static String generateFullReport(Context context, List<LocationWithPhotos> items) {
        StringBuilder allLabels = new StringBuilder();
        SpatialResolver resolver = SpatialResolver.getInstance(context);

        for (LocationWithPhotos item : items) {
            if (item.location.attributes != null && item.location.attributes.isSpecimen) {
                Coordinates sweref = new Coordinates(item.location.latitude, item.location.longitude).convertToSweref99TMFromWGS84();
                String province = resolver.getRegionName((int)Math.round(sweref.getNorth()), (int)Math.round(sweref.getEast()), false);
                String district = resolver.getRegionName((int)Math.round(sweref.getNorth()), (int)Math.round(sweref.getEast()), true);
                allLabels.append(generateSingleLabelHtml(item, province, district));
            }
        }

        if (allLabels.length() == 0) {
            return "<html><body><h1>No specimens found to print.</h1></body></html>";
        }

        String template = getTemplate(context);
        return template.replace("{{LABELS_HERE}}", allLabels.toString());
    }

    private static String generateSingleLabelHtml(LocationWithPhotos item, String prov, String dist) {
        SpeciesAttributes attrs = item.location.attributes;
        String dateOnly = (item.location.localTime != null && item.location.localTime.length() >= 10)
                ? item.location.localTime.substring(0, 10) : "____-____-____";

        return String.format(Locale.US,
                "<div class='label-container'>" +
                        "  <div class='header'>Flora Suecica</div>" +
                        "  <div class='species'>%s</div>" +
                        "  <div class='location'>%s, %s socken</div>" +
                        "  <div class='description'>" +
                        "    <div>%s</div>" + // Locality Description
                        "    <div>%s</div>" + // Substrate
                        "    <div>%s</div>" +   // Habitat
                        "    <div class='coordinates'>wgs84: %.5f, %.5f</div>" +
                        "  </div>" +
                        "  <div class='spacer'></div>" +
                        "  <div class='footer'>" +
                        "    <span>Leg. %s %s</span>" +
                        "    <span>%s</span>" +
                        "  </div>" +
                        "</div>",
                clean(attrs.species),
                clean(prov),
                clean(dist),
                clean(attrs.localityDescription),
                clean(attrs.substrate),
                clean(attrs.habitat),
                item.location.latitude,
                item.location.longitude,
                clean(attrs.collector),
                (attrs.specimenNr != null && !attrs.specimenNr.isEmpty() ? "nr " + attrs.specimenNr : ""),
                dateOnly
        );
    }

    private static String clean(String input) {
        return (input == null || input.isEmpty()) ? "_____" : input;
    }

    private static String getTemplate(Context context) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("label_template.html")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            Log.e("LabelHtmlGenerator", "Error reading template", e);
            return "<html><body>%s</body></html>";
        }
        return sb.toString();
    }
}