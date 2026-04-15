"""Fix emoji in Django views.py to make them Windows-console safe."""
import re

files_to_fix = ['core/views.py', 'core/utils.py', 'core/cloud_simulator.py']

for fpath in files_to_fix:
    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        new_lines = []
        changed = False
        for line in lines:
            # Replace all non-ASCII chars in print() statements only
            if 'print(' in line or 'print (' in line:
                new_line = line.encode('ascii', errors='replace').decode('ascii').replace('?', '')
                # Restore f-string variable placeholders that got mangled
                # Re-encode properly by just removing non-ascii in print strings
                clean_line = ''
                for ch in line:
                    if ord(ch) < 128:
                        clean_line += ch
                    # else: skip the emoji character
                if clean_line != line:
                    new_lines.append(clean_line)
                    changed = True
                    continue
            new_lines.append(line)

        if changed:
            with open(fpath, 'w', encoding='utf-8') as f:
                f.writelines(new_lines)
            print(f"Fixed: {fpath}")
        else:
            print(f"No changes: {fpath}")
    except Exception as e:
        print(f"Error: {fpath}: {e}")

print("Done.")
