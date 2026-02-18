import struct
import os
import sqlite3
from qgis.core import QgsProject

# --- SETTINGS ---
EXPORT_FOLDER = "C:/SockenExport/"
DB_NAME = "spatial_lookup.db"

LAYERS_CONFIG = [
    {
        "layer_name": "landskap", 
        "table_prefix": "province", 
        "bin_name": "province_coords.bin",
        "name_attr": "FlProvins",
        "id_attr": "fid"
    },
    {
        "layer_name": "Socknar", 
        "table_prefix": "district", 
        "bin_name": "district_coords.bin",
        "name_attr": "name",
        "id_attr": "fid"
    }
]

if not os.path.exists(EXPORT_FOLDER): 
    os.makedirs(EXPORT_FOLDER)

db_path = os.path.join(EXPORT_FOLDER, DB_NAME)
conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# 1. Setup Database Schema
for config in LAYERS_CONFIG:
    p = config["table_prefix"]
    cursor.execute(f"DROP TABLE IF EXISTS {p}s")
    cursor.execute(f"DROP TABLE IF EXISTS {p}_geometries")
    
    # UPDATED: Added NOT NULL to name to match Room's @NonNull annotation
    cursor.execute(f"CREATE TABLE {p}s ("
                   f"id INTEGER PRIMARY KEY NOT NULL, "
                   f"name TEXT NOT NULL)") # Match @NonNull
    
    cursor.execute(f"CREATE TABLE {p}_geometries ("
                   f"uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                   f"parentId INTEGER NOT NULL, "
                   f"minN INTEGER NOT NULL, minE INTEGER NOT NULL, "
                   f"maxN INTEGER NOT NULL, maxE INTEGER NOT NULL, "
                   f"byteOffset INTEGER NOT NULL, vertexCount INTEGER NOT NULL, "
                   f"FOREIGN KEY(parentId) REFERENCES {p}s(id) ON UPDATE NO ACTION ON DELETE CASCADE)")

# 2. Process Layers
for config in LAYERS_CONFIG:
    prefix = config["table_prefix"]
    binary_path = os.path.join(EXPORT_FOLDER, config["bin_name"])
    
    layers = QgsProject.instance().mapLayersByName(config["layer_name"])
    if not layers:
        print(f"Error: Layer '{config['layer_name']}' not found. Skipping.")
        continue
    
    layer = layers[0]
    processed_ids = set()
    byte_offset = 0

    print(f"Starting export for: {config['layer_name']}...")

    with open(binary_path, "wb") as bin_file:
        for feature in layer.getFeatures():
            f_id = feature[config["id_attr"]]
            
            # Insert Metadata
            if f_id not in processed_ids:
                # UPDATED: Fallback to empty string if name is Null in QGIS
                raw_name = feature[config["name_attr"]]
                name_val = str(raw_name) if raw_name is not None else ""
                
                cursor.execute(f"INSERT INTO {prefix}s (id, name) VALUES (?, ?)", 
                               (f_id, name_val))
                processed_ids.add(f_id)

            geom = feature.geometry()
            if geom.isEmpty(): continue

            polygons = geom.asMultiPolygon() if geom.isMultipart() else [geom.asPolygon()]

            for poly in polygons:
                for ring in poly:
                    if len(ring) == 0: continue
                    
                    min_n = int(min(p.y() for p in ring))
                    max_n = int(max(p.y() for p in ring))
                    min_e = int(min(p.x() for p in ring))
                    max_e = int(max(p.x() for p in ring))

                    cursor.execute(f"INSERT INTO {prefix}_geometries "
                                   f"(parentId, minN, minE, maxN, maxE, byteOffset, vertexCount) "
                                   f"VALUES (?, ?, ?, ?, ?, ?, ?)", 
                                   (f_id, min_n, min_e, max_n, max_e, byte_offset, len(ring)))

                    for point in ring:
                        bin_file.write(struct.pack("ii", int(round(point.y())), int(round(point.x()))))
                        byte_offset += 8

    print(f"Finished {prefix}. Binary saved to {config['bin_name']}")

conn.commit()
conn.close()
print(f"\nSUCCESS: Combined database with NOT NULL constraints created at {db_path}")