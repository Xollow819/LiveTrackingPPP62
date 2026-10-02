# Supabase setup and upgrade

The app uses Supabase as its shared source of truth. Room caches the active route and check-ins; WorkManager retries pending evidence. A configured project is required to create or join a session. Satellite maps need no account or API key.

## Existing projects

Back up the database and evidence bucket before upgrading. Run `migrations/20261002_secure_sessions.sql`, then `migrations/20261002_team_live_tracking.sql`, then `migrations/20261002_transport_water_quality.sql` in the project's SQL editor. The first adds account ownership, checkpoint instructions/requirements, private evidence policies and transactional app functions. The second lets members of an active session read live positions across teams and adds validated vehicle/animal marker types. The third replaces the transport weight field with total fish, pH and dissolved oxygen while retaining water temperature; existing historical weight values remain stored for compatibility. It does not expose positions to outsiders or after the session closes.

Legacy sessions have no verified account owner. They remain read-only and unavailable to new joins until an administrator explicitly associates `tracking_sessions.owner_id` with the correct lecturer's confirmed `auth.users.id`. Do not assign every legacy session to the first account that signs in. Review legacy memberships before restoring access; the old schema allowed self-assigned membership. Existing legacy public photo links stop working after the bucket becomes private; the app uses authenticated downloads.

For a fresh project, run `schema.sql`, which includes the migration. Do not rerun the legacy bootstrap against an upgraded project as an upgrade procedure.

## Authentication and configuration

Enable anonymous sign-in for students and email/password authentication for lecturers. Configure confirmation and password reset emails. Add `pppvenza://auth` and `pppvenza://auth?recovery=1` to the authorised redirect URLs; the app exchanges PKCE codes and opens its new-password form for recovery. Lecturers create an account, confirm email if required, then sign in. Students join with code, name and team.

Add the existing project's URL and public anon/client key to gitignored `local.properties`:

```
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-client-key
```

Never bundle a service-role key. Release assembly rejects missing configuration. Debug builds without configuration display an honest unavailable state and can be used to review the interface.

Realtime publication includes tracking_sessions, session_participants, checkpoints, live_positions and submissions. Students can see every participant's latest position while the session is active. Each student chooses a map marker that is shared with the session. The app reloads snapshots every 15 seconds as reconnect recovery. Evidence goes into the private `evidence` bucket, at `session/user/submission.jpg`, compressed as JPEG. Stable submission UUIDs make retries idempotent.

## Live acceptance

1. Sign in as a lecturer, create a route with instructions, and share the code.
2. Join on a separate student device, verify the route/instructions and real team in the lecturer roster.
3. Start sharing; verify movement, stationary fixes, pause/resume and fix timestamps.
4. Disable network, save a check-in with photo, reconnect and verify exactly one server record and uploaded state.
5. Verify flagged evidence uploads, authenticated photo viewing, and isolation using an unrelated account/session.
6. Close the session; new joins/check-ins fail, tracking stops, and historical records remain accessible.

Physical-device camera, GPS, background service, large-font UI and glass performance checks are required before release. This checkout cannot establish successful live acceptance without project configuration and migration access.
