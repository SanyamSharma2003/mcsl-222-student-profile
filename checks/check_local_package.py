"""Portable checks only; these are not a Docker/Java/MySQL integration test."""
from pathlib import Path
import hashlib
import hmac
import json
import re
import sqlite3
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
for path in sorted(root.rglob('*.xml')):
    ET.parse(path)
    print('PASS XML:', path.relative_to(root))
ET.parse(root/'config/student-profile.xml.template')
print('PASS XML: config/student-profile.xml.template')
dev = json.loads((root/'.devcontainer/devcontainer.json').read_text())
assert dev['forwardPorts'] == [8443, 8025]
assert dev['portsAttributes']['8443']['protocol'] == 'https'
assert dev['portsAttributes']['8025']['protocol'] == 'http'
assert all('visibility' not in a for a in dev['portsAttributes'].values())
print('PASS Codespaces port configuration JSON.')

seed = (root/'database/03-local-login.sql').read_text()
records = re.findall(r"INSERT INTO lab_credentials VALUES \('([^']+)','([^']+)',UNHEX\('([0-9a-f]+)'\),UNHEX\('([0-9a-f]+)'\),(\d+)\);", seed)
assert len(records) == 2
for username, subject, salt_hex, expected_hex, iterations in records:
    salt, expected = bytes.fromhex(salt_hex), bytes.fromhex(expected_hex)
    assert len(salt) == 16 and len(expected) == 32 and int(iterations) == 600000
    actual = hashlib.pbkdf2_hmac('sha256', b'LabDemo!2026', salt, int(iterations), 32)
    wrong = hashlib.pbkdf2_hmac('sha256', b'wrong-password', salt, int(iterations), 32)
    assert hmac.compare_digest(actual, expected) and not hmac.compare_digest(wrong, expected)
    print('PASS PBKDF2 seed:', username)

source = (root/'app/src/main/java/edu/university/profile/ProfileDao.java').read_text()
body = source.split('public Profile lookup(', 1)[1].split('try (Connection', 1)[0]
expression = re.search(r'String sql\s*=\s*(.*?);', body, re.S).group(1)
query = ''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', expression))
c = sqlite3.connect(':memory:')
c.execute('CREATE TABLE students (student_id INTEGER PRIMARY KEY, sso_subject TEXT UNIQUE, enrollment_no TEXT UNIQUE, mobile_e164 TEXT UNIQUE)')
c.executemany('INSERT INTO students VALUES (?,?,?,?)', [
    (1,'student-demo-1','6000000001','+919000000001'),
    (2,'student-demo-2','240000000002','+916000000001'),
    (3,'student-demo-3','0000000003','+917777777777'),
])
cases = [
    ('student-demo-1','6000000001','+916000000001',[1]),
    ('student-demo-2','6000000001','+916000000001',[2]),
    ('student-demo-1','+919000000001','+919000000001',[1]),
    ('student-demo-1','240000000002',None,[]),
    ('student-demo-2','9000000001','+919000000001',[]),
    ('unknown','6000000001','+916000000001',[]),
    ('student-demo-1',"' OR 1=1 --","' OR 1=1 --",[]),
    ('student-demo-3','0000000003',None,[3]),
    ('student-demo-3','3',None,[]),
    ('STUDENT-DEMO-1','6000000001','+916000000001',[]),
]
for subject, token, mobile, expected in cases:
    assert [r[0] for r in c.execute(query,(subject,token,mobile))] == expected
print('PASS 10 actual lookup-SQL cases on a portable SQLite fixture.')
print('Docker build, Java compilation, JSP translation and live integration are NOT tested here.')
