import csv
import os

def process_countries(conn, country_csv):
    cursor = conn.cursor()
    print(f"Parsing Countries from {os.path.basename(country_csv)}...")
    
    cursor.execute("DROP TABLE IF EXISTS countries")
    cursor.execute("""
        CREATE TABLE countries (
            code TEXT PRIMARY KEY NOT NULL,
            name_en TEXT,
            name_sv TEXT
        )
    """)

    # Open with utf-8 and no BOM handling as requested
    with open(country_csv, 'r', encoding='utf-8') as f:
        # Using delimiter=';' as per your CSV format
        reader = csv.DictReader(f, delimiter=';')
        
        # Clean headers to ensure english/svenska/code match
        if reader.fieldnames is None:
            raise ValueError("The country CSV has no header row")
        reader.fieldnames = [field.strip().lower() for field in reader.fieldnames]
        required_columns = {'code', 'english', 'svenska'}
        missing_columns = required_columns.difference(reader.fieldnames)
        if missing_columns:
            raise ValueError(
                "Missing required country columns: " + ", ".join(sorted(missing_columns))
            )

        count = 0
        for row in reader:
            # Map CSV columns: english -> name_en, svenska -> name_sv, code -> code
            code = row['code'].strip().upper()
            if not code: continue # Skip empty codes

            cursor.execute("""
                INSERT OR REPLACE INTO countries (code, name_en, name_sv)
                VALUES (?, ?, ?)
            """, (
                code,
                row['english'].strip(),
                row['svenska'].strip()
            ))
            count += 1

        if count == 0:
            raise ValueError("Country CSV contains no usable country rows.")

    print(f"Successfully imported {count} countries.")
