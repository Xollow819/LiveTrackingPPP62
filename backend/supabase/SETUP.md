# LiveTrackingPPP62 — Free Backend Setup (Supabase)

Everything here is **free** and needs **no credit card**. The app works fully
offline without any of this; the backend only adds live multi-device tracking
(students' GPS visible on the lecturer's phone) and shared evidence photos.

## 1. Create the free Supabase project (~5 minutes)

1. Go to https://supabase.com and sign up (email or GitHub — no card asked).
2. **New project** → name it e.g. `livetracking-ppp62`, set a database password
   (save it somewhere), pick the region closest to you (e.g. Singapore).
3. Wait ~2 minutes for the project to spin up.

Free-tier limits (checked 2026-10-01, verify on supabase.com/pricing):
500 MB database · 1 GB storage · 5 GB egress/month · 50k auth users/month ·
2M realtime messages · 200 peak realtime connections. Plenty for class use.
Note: free projects pause after 7 days of *project* inactivity — open the
dashboard once a week during the semester, or just before each field day.

## 2. Create the tables (copy-paste, ~3 minutes)

1. In the Supabase dashboard open **SQL Editor** → **New query**.
2. Open `schema.sql` in this folder, copy the whole file, paste it, press **Run**.
   This creates the tables, the realtime publication, the row-level-security
   policies, and the `evidence` photo bucket.

## 3. Enable anonymous sign-in (~1 minute)

The app signs students in anonymously — no email/password for anyone.

1. Open **Authentication** → **Providers**.
2. Find **Anonymous** and turn it **on**.

## 4. Copy the two keys into the app (~1 minute)

1. Open **Project Settings** (gear icon) → **API**.
2. Copy the **Project URL** (looks like `https://xyzcompany.supabase.co`)
   and the **anon public** key (the long `eyJ…` string — this one is safe to
   put in the app; never use the `service_role` key in the app).
3. On the lecturer phone (and every student phone), open the app →
   **Online backend settings**, paste both values, tap **Save & test connection**.
   You should see “Connected ✓”.

Keys are stored only on the device (DataStore) — they are **not** in the APK
and **not** committed to git.

## 5. Run a session

**Lecturer phone:**
1. Lecturer → enter session code (e.g. `PPP6201`) + a lecturer PIN → **Start / join online session**.
   (First lecturer to use a code creates it; the PIN protects the lecturer view.)
2. Keep the dashboard open — student dots appear live on the map.
3. **Submissions** → **Refresh** shows students' checkpoint evidence with photos.

**Student phones:**
1. Student → join code + name + team → **Join active session**.
2. **Start tracking** — GPS is published every ~15 s while the backend is reachable.
   If the network drops, everything keeps working locally and positions resume
   publishing automatically when connectivity returns.

## Troubleshooting

| Symptom | Fix |
|---|---|
| “Anonymous sign-in failed” | Step 3 — the Anonymous provider must be enabled. |
| “relation does not exist” | Step 2 — the schema.sql was not run (or not fully). |
| Map shows no students | Check all phones joined the *same code*; lecturer tapped Start/join; students tapped Start tracking. |
| Photos don’t upload | The `evidence` bucket is created by schema.sql; uploads need network. |
| Project paused | Free projects pause after 7 days idle — press **Restore** in the dashboard. |

## Files

- `schema.sql` — all tables, policies, realtime, and the storage bucket. Re-running it is safe (uses `if not exists` / drops policies first).
