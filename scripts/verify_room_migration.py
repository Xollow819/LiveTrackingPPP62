"""Verify the shipped SQLite migration against exported Room schemas."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
schemas = root / "app/schemas/com.ppp62.livetracking.data.AppDatabase"
old = json.loads((schemas / "1.json").read_text())["database"]
new = json.loads((schemas / "2.json").read_text())["database"]
db = sqlite3.connect(":memory:")
for entity in old["entities"]:
    db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
db.execute("INSERT INTO sessions VALUES ('legacy','Legacy route','ABC234','Lecturer','ACTIVE',100,NULL)")
db.execute("INSERT INTO checkpoints VALUES ('cp','legacy','Dock',-6.2,106.8,75,1,'Check oxygen',1,1,1)")
db.execute("INSERT INTO check_ins VALUES ('r','cp','legacy','A','Team',25,4,'GOOD','',NULL,-6.2,106.8,0,100,'FLAGGED','test')")
db.execute("INSERT INTO locations VALUES ('this-device','legacy','A','Team',-6.2,106.8,10,0,0,100,'LIVE',80)")
source = (root / "app/src/main/java/com/ppp62/livetracking/data/DatabaseMigrations.kt").read_text()
for sql in re.findall(r'db.execSQL\("([^"\n]*)"\)', source):
    db.execute(sql)
assert db.execute("SELECT id,userId,syncState FROM check_ins").fetchone() == ("r", "", "FLAGGED")
assert db.execute("SELECT instructions FROM checkpoints").fetchone()[0] == "Check oxygen"
expected = sqlite3.connect(":memory:")
for entity in new["entities"]:
    table = entity["tableName"]
    expected.execute(entity["createSql"].replace("${TABLE_NAME}", table))
    assert db.execute(f"PRAGMA table_info({table})").fetchall() == expected.execute(f"PRAGMA table_info({table})").fetchall(), table
# The same device can occur in different sessions without replacing the old fix.
db.execute("INSERT INTO locations VALUES ('this-device','other','A','Team',-6.2,106.8,10,0,0,100,'LIVE',80)")
assert db.execute("SELECT COUNT(*) FROM locations").fetchone()[0] == 2
# An older acknowledgement must not delete a newer queued state.
db.execute("INSERT INTO position_outbox VALUES ('other','u','A','Team',-6.2,106.8,10,100,'PAUSED',200)")
db.execute("DELETE FROM position_outbox WHERE sessionId='other' AND userId='u' AND eventAt=100")
assert db.execute("SELECT COUNT(*) FROM position_outbox").fetchone()[0] == 1
print("PASS: Room migration matches schema, preserves evidence/instructions, isolates locations, and protects newer outbox state")
