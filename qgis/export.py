import struct
import os
from qgis.core import QgsProject

# script to export data from polygon files to the whats  my socken app
# the coordinates should be a system that works with 32 bit integers as most transverse mercator do 
# run check geometry and fix errors as self intersections


# --- SETTINGS ---
EXPORT_FOLDER = "C:/SockenExport/"
NAME_ATTRIBUTE = "name"  # Change to 'NAMN' or similar for Socken layer
ID_ATTRIBUTE = "fid"
FILE_PREFIX = "district"      # This will define your CSV names (e.g. province_districts.csv)

# Ensure folder exists
if not os.path.exists(EXPORT_FOLDER): 
    os.makedirs(EXPORT_FOLDER)

# Define file paths
binary_path = os.path.join(EXPORT_FOLDER, f"{FILE_PREFIX}_coords.bin")
geom_csv_path = os.path.join(EXPORT_FOLDER, f"{FILE_PREFIX}_geometries.csv")
dist_csv_path = os.path.join(EXPORT_FOLDER, f"{FILE_PREFIX}_metadata.csv")

layer = iface.activeLayer()

if not layer:
    print("Error: No layer selected!")
else:
    processed_ids = set()
    byte_offset = 0

    # Open all files using 'with' for safety
    with open(binary_path, "wb") as bin_file, \
         open(geom_csv_path, "w", encoding="utf-8") as geom_csv, \
         open(dist_csv_path, "w", encoding="utf-8") as dist_csv:

        # Headers
        geom_csv.write("id,minN,minE,maxN,maxE,byteOffset,vertexCount\n")
        dist_csv.write("id,name\n")

        for feature in layer.getFeatures():
            # 1. Metadata handling
            d_id = feature[ID_ATTRIBUTE]
            if d_id not in processed_ids:
                # Clean name of quotes to avoid CSV breaking
                raw_name = str(feature[NAME_ATTRIBUTE])
                clean_name = raw_name.replace('"', '""')
                dist_csv.write(f'{d_id},"{clean_name}"\n')
                processed_ids.add(d_id)

            # 2. Geometry handling
            geom = feature.geometry()
            if geom.isEmpty(): continue

            # Standardize to a list of polygons
            polygons = geom.asMultiPolygon() if geom.isMultipart() else [geom.asPolygon()]

            for poly in polygons:
                for ring in poly:
                    vertex_count = len(ring)
                    if vertex_count == 0: continue
                    
                    # Bounding Box calculation
                    min_n = int(min(p.y() for p in ring))
                    max_n = int(max(p.y() for p in ring))
                    min_e = int(min(p.x() for p in ring))
                    max_e = int(max(p.x() for p in ring))

                    # Write index entry
                    geom_csv.write(f"{d_id},{min_n},{min_e},{max_n},{max_e},{byte_offset},{vertex_count}\n")

                    # 3. Write Binary Coordinates
                    for point in ring:
                        # 'i' is 32-bit signed int. Sweref99 fits perfectly.
                        bin_file.write(struct.pack("ii", int(round(point.y())), int(round(point.x()))))
                        byte_offset += 8

    print(f"Successfully exported {len(processed_ids)} features to {EXPORT_FOLDER}")