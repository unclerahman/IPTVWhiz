import re
import sys

path = sys.argv[1]
text = open(path, encoding='utf-8').read()
BS = chr(92)


def fix(match):
    body = match.group(2)
    body = re.sub(r"(?<!" + re.escape(BS) + r")'", BS + "'", body)
    return match.group(1) + body + match.group(3)


fixed = re.sub(r'(<(?:string|item)[^>]*>)(.*?)(</(?:string|item)>)', fix, text, flags=re.S)
open(path, 'w', encoding='utf-8').write(fixed)
print("changed" if fixed != text else "unchanged")
