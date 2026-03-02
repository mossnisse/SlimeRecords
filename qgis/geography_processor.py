import struct
import os
from qgis.core import QgsProject

def process_geography(conn, export_folder, layers_config):
    cursor = conn.cursor()
    
    for config in layers_config:
        prefix = config["table_prefix"]
        binary_path = os.path.join(export_folder, config["bin_name"])
        
        # Setup Tables
        cursor.execute(f"DROP TABLE IF EXISTS {prefix}s")
        cursor.execute(f"DROP TABLE IF EXISTS {prefix}_geometries")
        cursor.execute(f"CREATE TABLE {prefix}s (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
        cursor.execute(f"""
            CREATE TABLE {prefix}_geometries ( uid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                parentId INTEGER NOT NULL, 
                minN INTEGER NOT NULL, 
                minE INTEGER NOT NULL, 
                maxN INTEGER NOT NULL, 
                maxE INTEGER NOT NULL, 
                byteOffset INTEGER NOT NULL, 
                vertexCount INTEGER NOT NULL, 
                FOREIGN KEY(parentId) REFERENCES {prefix}s(id) ON DELETE CASCADE
            )
        """)

        layers = QgsProject.instance().mapLayersByName(config["layer_name"])
        if not layers:
            print(f"Error: Layer '{config['layer_name']}' not found.")
            continue
        
        layer = layers[0]
        processed_ids = set()
        byte_offset = 0

        print(f"Processing: {config['layer_name']}...")

        with open(binary_path, "wb") as bin_file:
            for feature in layer.getFeatures():
                f_id = feature[config["id_attr"]]
                
                if f_id not in processed_ids:
                    raw_name = feature[config["name_attr"]]
                    name_val = str(raw_name) if raw_name is not None else ""
                    cursor.execute(f"INSERT INTO {prefix}s (id, name) VALUES (?, ?)", (f_id, name_val))
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

                        cursor.execute(f"INSERT INTO {prefix}_geometries (parentId, minN, minE, maxN, maxE, byteOffset, vertexCount) VALUES (?, ?, ?, ?, ?, ?, ?)", 
                                       (f_id, min_n, min_e, max_n, max_e, byte_offset, len(ring)))

                        for point in ring:
                            bin_file.write(struct.pack("ii", int(round(point.y())), int(round(point.x()))))
                            byte_offset += 8