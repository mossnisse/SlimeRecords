import sys
import os
import sqlite3
import traceback

# dyntaxa is downloaded at https://artfakta.se/metadata/dyntaxa

# Resolve sibling processors relative to this script so the project is relocatable.
try:
    script_dir = os.path.dirname(os.path.abspath(__file__))
except NameError:
    # __file__ is undefined in the QGIS Python console; fall back to the
    # working directory (open the console from the qgis/ folder).
    script_dir = os.getcwd()
if script_dir not in sys.path:
    sys.path.append(script_dir)

print("--- SCRIPT STARTING ---", flush=True)

try:
    import geography_processor
    import taxonomy_processor
    import country_processor # NEW IMPORT
    import importlib
    importlib.reload(geography_processor)
    importlib.reload(taxonomy_processor)
    importlib.reload(country_processor)
except Exception as e:
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
        # 1. Geography
        geography_processor.process_geography(conn, EXPORT_FOLDER, LAYERS_CONFIG)
        
        # 2. Taxonomy
        taxonomy_processor.process_taxonomy(
            conn, 
            os.path.join(EXPORT_FOLDER, "Taxon.csv"),
            os.path.join(EXPORT_FOLDER, "vernacularName.csv"),
            os.path.join(EXPORT_FOLDER, "groupDef.csv")
        )

        # 3. NEW: Countries
        print("\n--- Starting Country Export ---", flush=True)
        country_processor.process_countries(
            conn,
            os.path.join(EXPORT_FOLDER, "countries.csv") # Path to your prepared CSV
        )
        
        conn.commit()
        print("\nBUILD SUCCESSFUL!", flush=True)
    except Exception:
        print("\n!!! BUILD FAILED !!!")
        print(traceback.format_exc())
        conn.rollback()
        raise
    finally:
        conn.close()

# The QGIS Python console runs scripts with __name__ == "__console__",
# so checking only "__main__" would silently skip the build there.
if __name__ in ("__main__", "__console__"):
    main()
