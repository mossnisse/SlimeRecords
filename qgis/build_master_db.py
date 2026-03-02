import sys
import os
import sqlite3
import traceback

# 1. FORCE THE PATH - Use the exact folder where your scripts live
script_dir = r"C:/Users/NissE/AndroidStudioProjects/WhatsMySocken/qgis"
if script_dir not in sys.path:
    sys.path.append(script_dir)

print("--- SCRIPT STARTING ---", flush=True)

try:
    import geography_processor
    import taxonomy_processor
    import importlib
    importlib.reload(geography_processor)
    importlib.reload(taxonomy_processor)
except Exception:
    print("CRITICAL: Failed to import sub-modules!")
    print(traceback.format_exc())
    raise RuntimeError("Import failed. Halting script.") from e

EXPORT_FOLDER = "C:/SockenExport/"
DB_NAME = "spatial_lookup.db"

LAYERS_CONFIG = [
    {"layer_name": "landskap", "table_prefix": "province", "bin_name": "province_coords.bin", "name_attr": "FlProvins", "id_attr": "fid"},
    {"layer_name": "Socknar", "table_prefix": "district", "bin_name": "district_coords.bin", "name_attr": "name", "id_attr": "fid"}
]

def main():
    db_path = os.path.join(EXPORT_FOLDER, DB_NAME)
    if not os.path.exists(EXPORT_FOLDER): os.makedirs(EXPORT_FOLDER)
    
    conn = sqlite3.connect(db_path)
    try:
        print("--- Starting Geography Export ---", flush=True)
        geography_processor.process_geography(conn, EXPORT_FOLDER, LAYERS_CONFIG)
        
        print("\n--- Starting Taxonomy Export ---", flush=True)
        taxonomy_processor.process_taxonomy(
            conn, 
            os.path.join(EXPORT_FOLDER, "Taxon.csv"),
            os.path.join(EXPORT_FOLDER, "vernacularName.csv"),
            os.path.join(EXPORT_FOLDER, "groupDef.csv")
        )
        
        conn.commit()
        print("\nBUILD SUCCESSFUL!", flush=True)
    except Exception:
        print("\n!!! BUILD FAILED !!!")
        print(traceback.format_exc())
    finally:
        conn.close()

main()