import csv
import os

def process_taxonomy(conn, taxon_csv, vernacular_csv, group_def_csv):
    cursor = conn.cursor()
    cursor.execute("DROP TABLE IF EXISTS species_reference")
    cursor.execute("""
        CREATE TABLE species_reference (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            dyntaxaID INTEGER NOT NULL,
            name TEXT NOT NULL,
            language TEXT NOT NULL,
            taxonCategory TEXT,
            isSynonym INTEGER NOT NULL,
            taxonGroup TEXT
        )
    """)

    # Load groupMap
    group_map = {}
    delete_set = set()
    print("Parsing GroupDef.csv...")
    with open(group_def_csv, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f, delimiter='\t')
        for row in reader:
            key = (row['rank'].strip(), row['latin'].strip())
            group_map[key] = row['group']
            if row['delete'].lower() == 'true': delete_set.add(key)

    # Process Taxon.csv
    taxon_info = {}
    print("Parsing Taxon.csv...")
    # Use utf-8-sig to handle potential Byte Order Marks (BOM)
    with open(taxon_csv, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f, delimiter='\t')
        
        # Clean headers just in case of trailing spaces
        reader.fieldnames = [field.strip() for field in reader.fieldnames]

        for row in reader:
            try:
                # Use acceptedNameUsageID as requested
                # We split by ':' and take the last part (e.g., '6004805')
                t_id = int(row['acceptedNameUsageID'].split(':')[-1])
                
                rank = row['taxonRank']
                status = row['taxonomicStatus']
                
                t_group, is_deleted = "Other", False
                for level in ['kingdom', 'phylum', 'class', 'order']:
                    val = row.get(level)
                    if val:
                        val = val.strip()
                        if (level, val) in delete_set: 
                            is_deleted = True
                        if (level, val) in group_map: 
                            t_group = group_map[(level, val)]

                if is_deleted: 
                    continue
                
                # Store info for the vernacular lookup later
                taxon_info[t_id] = {'cat': rank, 'grp': t_group}

                # Insert into DB
                cursor.execute("""
                    INSERT INTO species_reference 
                    (dyntaxaID, name, language, taxonCategory, isSynonym, taxonGroup) 
                    VALUES (?,?,?,?,?,?)
                """, (
                    t_id, 
                    row['scientificName'], 
                    'la', 
                    rank, 
                    1 if "synonym" in status.lower() else 0, 
                    t_group
                ))
            except KeyError as e:
                available = ", ".join(row.keys())
                raise KeyError(f"Column {e} not found. Available: {available}")

    # Process Vernacular
    print("Parsing VernacularName.csv...")
    with open(vernacular_csv, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f, delimiter='\t')
        reader.fieldnames = [field.strip() for field in reader.fieldnames]
        
        for row in reader:
            try:
                # CHANGE THIS LINE: Use 'taxonId' to match your CSV header
                raw_id = row['taxonId'] 
                
                t_id = int(raw_id.split(':')[-1])
                
                if t_id in taxon_info and row['language'] in ['sv', 'en']:
                    info = taxon_info[t_id]
                    
                    # Check isPreferredName logic
                    # Note: SANT is Swedish for TRUE. 
                    is_pref = row['isPreferredName'].strip().upper()
                    is_synonym_val = 0 if (is_pref == "SANT" or is_pref == "TRUE") else 1
                    
                    cursor.execute("""
                        INSERT INTO species_reference 
                        (dyntaxaID, name, language, taxonCategory, isSynonym, taxonGroup) 
                        VALUES (?,?,?,?,?,?)
                    """, (
                        t_id, 
                        row['vernacularName'], 
                        row['language'], 
                        info['cat'], 
                        is_synonym_val, 
                        info['grp']
                    ))
            except (ValueError, KeyError, IndexError) as e:
                # Optional: print(f"Skipping row due to: {e}") 
                continue
