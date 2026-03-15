package nisse.SlimeRecords;
import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import nisse.SlimeRecords.data.LocationRecord;
import nisse.SlimeRecords.data.SpeciesAttributes;

public class LabelHtmlGenerator {

    public static String generateFullReport(Context context, List<LocationRecord> items) {
        StringBuilder allLabels = new StringBuilder();

        for (LocationRecord item : items) {
            if (item.attributes != null && item.attributes.isSpecimen) {
                // Use stored fields instead of coordinate lookups
                allLabels.append(generateSingleLabelHtml(item));
            }
        }

        if (allLabels.length() == 0) {
            return "<html><body><h1>No specimens found to print.</h1></body></html>";
        }

        String template = getTemplate(context);
        return template.replace("{{LABELS_HERE}}", allLabels.toString());
    }

    private static String generateSingleLabelHtml(LocationRecord item) {
        SpeciesAttributes attrs = item.attributes;
        String dateOnly = (item.localTime != null && item.localTime.length() >= 10)
                ? item.localTime.substring(0, 10) : "____-____-____";

        // Dynamic Header based on Country
        String header = "Flora";
        if ("Sverige".equalsIgnoreCase(item.country) || "Sweden".equalsIgnoreCase(item.country) || "SE".equalsIgnoreCase(item.countryCode)) {
            header = "Flora Suecica";
        } else if (item.country != null && !item.country.isEmpty()) {
            header = "Flora of " + item.country;
        }

        // Handle "Socken" (District) label logic - only append "socken" if it's Sweden
        String districtDisplay = clean(item.district);
        if ("SE".equalsIgnoreCase(item.countryCode)) {
            districtDisplay += " socken";
        }

        return String.format(Locale.US,
                "<div class='label-container' data-uid='%d'>" +
                        "  <div class='header'>%s</div>" + // Dynamic Header
                        "  <div class='species' data-label='Species Name'>%s</div>" +
                        "  <div> " +
                        "      <span class='province' data-label='province'>%s</span>, <span class='district' data-label='district'>%s</span>" +
                        "  </div>" +
                        "  <div class='description'>" +
                        "    <div class='locality' data-label='Locality Description'>%s</div>" +
                        "    <div class='substrate' data-label='Substrate'>%s</div>" +
                        "    <div class='habitat' data-label='Habitat'>%s</div>" +
                        "    <div class='coordinates'>wgs84: %.5f, %.5f</div>" +
                        "  </div>" +
                        "  <div class='spacer'></div>" +
                        "  <div class='footer'>" +
                        "    <span>Leg. <span class='collector' data-label='Collector'>%s</span> <span class='nr' data-label='Collection Nr'>%s</span></span>" +
                        "    <span class='date' data-label='Date'>%s</span>" +
                        "  </div>" +
                        "</div>",
                item.id,
                header,
                clean(attrs.taxonName),
                clean(item.province),
                districtDisplay,
                clean(item.locality),
                clean(attrs.substrate),
                clean(attrs.habitat),
                item.latitude,
                item.longitude,
                clean(attrs.collector),
                (attrs.specimenNr != null && !attrs.specimenNr.isEmpty() ? "nr " + attrs.specimenNr : ""),
                dateOnly
        );
    }

    private static String clean(String input) {
        if (input == null || input.isEmpty()) return "_____";
        // Basic HTML escaping
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
            return "<html><body>{{LABELS_HERE}}</body></html>";
        }
        return sb.toString();
    }
}