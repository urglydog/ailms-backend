import mysql.connector

try:
    conn = mysql.connector.connect(user='root', password='root', host='127.0.0.1', database='lms_db')
    cursor = conn.cursor()
    cursor.execute("SELECT id, mermaid_code FROM mindmaps ORDER BY id DESC LIMIT 1;")
    row = cursor.fetchone()
    if row:
        id, code = row
        
        # Replace missing quotes in S1[(1) ...] -> S1["(1) ..."]
        import re
        # Find nodes that look like ID[text] where text does NOT start with "
        # and has (1) or something inside. Actually, it's safer to just wrap everything inside [] that isn't quoted.
        # But wait, [ text ] can be replaced by [" text "].
        
        def quote_repl(match):
            inner = match.group(1)
            if inner.startswith('"') and inner.endswith('"'):
                return f'[{inner}]'
            return f'["{inner}"]'
            
        fixed_code = re.sub(r'\[(.*?)\]', quote_repl, code)
        
        print("OLD CODE:\n", code)
        print("NEW CODE:\n", fixed_code)
        
        cursor.execute("UPDATE mindmaps SET mermaid_code = %s WHERE id = %s", (fixed_code, id))
        conn.commit()
        print("Fixed.")
except Exception as e:
    print(e)
