
from pathlib import Path
import re
import shutil

folder = Path(__file__).resolve().parent
path = folder / "Ui.kt"

if not path.is_file():
    raise FileNotFoundError(f"Не найден файл: {path}")

backup = folder / "Ui_backup_before_encoding_fix2.kt"
shutil.copy2(path, backup)

text = path.read_text(encoding="utf-8")

# Ищем обычные строки Kotlin в двойных кавычках.
pattern = re.compile(r'"(?:\\.|[^"\\])*"')

markers = ("Рџ", "Рѕ", "Рµ", "Р°", "СЃ", "С‚", "СЂ", "РЅ", "Рё")

def repair_string(match):
    original = match.group(0)
    value = original[1:-1]
    current = value

    for _ in range(3):
        if not any(mark in current for mark in markers):
            break
        try:
            candidate = current.encode("cp1251").decode("utf-8")
        except (UnicodeEncodeError, UnicodeDecodeError):
            break

        if candidate == current:
            break
        current = candidate

    return '"' + current + '"'

fixed = pattern.sub(repair_string, text)
path.write_text(fixed, encoding="utf-8")

print(f"Готово. Резервная копия: {backup}")
print("Проверь русские строки в Ui.kt перед сборкой.")
