"""Legacy entry point that exports only the geography layers.

The actual schema and export logic live in geography_processor.py, which is
shared with build_master_db.py, so the two scripts can no longer drift apart.
Prefer build_master_db.py for a full rebuild (geography + taxonomy + countries):
this script drops and rebuilds only the geography tables inside the combined
database, so the species tables keep whatever build they came from.
"""
import importlib
import os
import sqlite3
import sys

# Resolve the sibling processor relative to this script so the project is relocatable.
try:
    script_dir = os.path.dirname(os.path.abspath(__file__))
except NameError:
    # __file__ is undefined in the QGIS Python console; fall back to the
    # working directory (open the console from the qgis/ folder).
    script_dir = os.getcwd()
if script_dir not in sys.path:
    sys.path.append(script_dir)

import geography_processor
importlib.reload(geography_processor)

# --- SETTINGS ---
EXPORT_FOLDER = "C:/SockenExport/"
DB_NAME = "spatial_lookup.db"

LAYERS_CONFIG = [
    {"layer_name": "landskap", "table_prefix": "province", "bin_name": "province_coords.bin", "name_attr": "FlProvins", "id_attr": "fid"},
    {"layer_name": "Socknar", "table_prefix": "district", "bin_name": "district_coords.bin", "name_attr": "name", "id_attr": "fid"}
]


def main():
    if not os.path.exists(EXPORT_FOLDER):
        os.makedirs(EXPORT_FOLDER)

    conn = sqlite3.connect(os.path.join(EXPORT_FOLDER, DB_NAME))
    try:
        geography_processor.process_geography(conn, EXPORT_FOLDER, LAYERS_CONFIG)
        conn.commit()
        print(f"\nSUCCESS: Geography layers exported to {os.path.join(EXPORT_FOLDER, DB_NAME)}")
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


# The QGIS Python console runs scripts with __name__ == "__console__".
if __name__ in ("__main__", "__console__"):
    main()
