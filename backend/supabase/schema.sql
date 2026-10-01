-- ============================================================================
-- LiveTrackingPPP62 — Supabase schema (free tier, no credit card required)
-- Run this in the Supabase Dashboard: SQL Editor -> New query -> paste -> Run.
-- Also enable "Allow anonymous sign-ins" under Authentication -> Providers.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Tables
-- ----------------------------------------------------------------------------

-- A tracking session, e.g. code PPP6201. Students join with the code,
-- the lecturer creates it and guards the lecturer view with a PIN.
create table if not exists public.tracking_sessions (
  id           uuid primary key default gen_random_uuid(),
  code         text not null unique,          -- e.g. 'PPP6201'
  title        text not null default '',
  lecturer_pin text not null default '1234',  -- simple shared PIN for the lecturer view
  is_active    boolean not null default true,
  created_at   timestamptz not null default now()
);

-- Who joined a session (students + lecturers). One row per (session, user).
create table if not exists public.session_participants (
  id           uuid primary key default gen_random_uuid(),
  session_id   uuid not null references public.tracking_sessions(id) on delete cascade,
  user_id      uuid not null,                 -- auth.uid() from anonymous sign-in
  display_name text not null default '',
  role         text not null default 'student' check (role in ('student','lecturer')),
  joined_at    timestamptz not null default now(),
  unique (session_id, user_id)
);

-- Latest live position per student per session (upserted by the app).
-- History stays on the phone (Room); the backend only needs "where is everyone now".
create table if not exists public.live_positions (
  session_id   uuid not null references public.tracking_sessions(id) on delete cascade,
  user_id      uuid not null,
  display_name text not null default '',
  lat          double precision not null,
  lng          double precision not null,
  accuracy     double precision,
  recorded_at  timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  primary key (session_id, user_id)
);

-- Checkpoint evidence submitted by students (photo lives in Storage).
create table if not exists public.submissions (
  id             uuid primary key default gen_random_uuid(),
  session_id     uuid not null references public.tracking_sessions(id) on delete cascade,
  user_id        uuid not null,
  display_name   text not null default '',
  checkpoint_name text not null default '',
  note           text not null default '',
  photo_path     text,                        -- path inside the 'evidence' bucket, e.g. '<session_id>/<uuid>.jpg'
  lat            double precision,
  lng            double precision,
  temperature_c  double precision,
  weight_kg      double precision,
  condition      text,                        -- GOOD | STRESSED | MORTALITY
  created_at     timestamptz not null default now()
);

-- Checkpoints defined by the lecturer for a session.
create table if not exists public.checkpoints (
  id         uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.tracking_sessions(id) on delete cascade,
  name       text not null,
  lat        double precision not null,
  lng        double precision not null,
  radius_m   double precision not null default 50,
  created_at timestamptz not null default now()
);

-- ----------------------------------------------------------------------------
-- 2. Realtime: broadcast row changes so the lecturer map updates live
-- ----------------------------------------------------------------------------
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and tablename = 'live_positions'
  ) then
    alter publication supabase_realtime add table public.live_positions;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and tablename = 'submissions'
  ) then
    alter publication supabase_realtime add table public.submissions;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and tablename = 'checkpoints'
  ) then
    alter publication supabase_realtime add table public.checkpoints;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and tablename = 'session_participants'
  ) then
    alter publication supabase_realtime add table public.session_participants;
  end if;
end $$;

-- ----------------------------------------------------------------------------
-- 3. Row Level Security
-- Everyone signs in anonymously (no email/password needed), so policies are
-- keyed on auth.uid(). Students can only write their own rows; anyone in the
-- app can read session data (join codes are shared out-of-band with the class).
-- ----------------------------------------------------------------------------
alter table public.tracking_sessions    enable row level security;
alter table public.session_participants enable row level security;
alter table public.live_positions       enable row level security;
alter table public.submissions          enable row level security;
alter table public.checkpoints          enable row level security;

-- tracking_sessions: any signed-in user can read (to join by code) and create.
drop policy if exists "sessions_read"   on public.tracking_sessions;
drop policy if exists "sessions_insert" on public.tracking_sessions;
create policy "sessions_read"   on public.tracking_sessions for select using (auth.uid() is not null);
create policy "sessions_insert" on public.tracking_sessions for insert with check (auth.uid() is not null);

-- session_participants: read all (roster), insert/update own row.
drop policy if exists "participants_read"   on public.session_participants;
drop policy if exists "participants_insert" on public.session_participants;
drop policy if exists "participants_update" on public.session_participants;
create policy "participants_read"   on public.session_participants for select using (auth.uid() is not null);
create policy "participants_insert" on public.session_participants for insert with check (auth.uid() is not null and user_id = auth.uid());
create policy "participants_update" on public.session_participants for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- live_positions: read all in the app, upsert only your own row.
drop policy if exists "positions_read"   on public.live_positions;
drop policy if exists "positions_write" on public.live_positions;
create policy "positions_read"  on public.live_positions for select using (auth.uid() is not null);
create policy "positions_write" on public.live_positions for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- submissions: read all, insert own.
drop policy if exists "submissions_read"   on public.submissions;
drop policy if exists "submissions_insert" on public.submissions;
create policy "submissions_read"   on public.submissions for select using (auth.uid() is not null);
create policy "submissions_insert" on public.submissions for insert with check (auth.uid() is not null and user_id = auth.uid());

-- checkpoints: read all, any signed-in user may manage (lecturer does this in-app).
drop policy if exists "checkpoints_read"   on public.checkpoints;
drop policy if exists "checkpoints_write"  on public.checkpoints;
create policy "checkpoints_read"  on public.checkpoints for select using (auth.uid() is not null);
create policy "checkpoints_write" on public.checkpoints for all using (auth.uid() is not null) with check (auth.uid() is not null);

-- ----------------------------------------------------------------------------
-- 4. Storage bucket for evidence photos
-- ----------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('evidence', 'evidence', true)
on conflict (id) do nothing;

-- Public read (lecturer + students can view photos via public URL),
-- authenticated users may upload/overwrite/delete.
drop policy if exists "evidence_public_read"  on storage.objects;
drop policy if exists "evidence_auth_insert"  on storage.objects;
drop policy if exists "evidence_auth_update"  on storage.objects;
drop policy if exists "evidence_auth_delete"  on storage.objects;
create policy "evidence_public_read" on storage.objects for select using (bucket_id = 'evidence');
create policy "evidence_auth_insert" on storage.objects for insert with check (bucket_id = 'evidence' and auth.uid() is not null);
create policy "evidence_auth_update" on storage.objects for update using (bucket_id = 'evidence' and auth.uid() is not null);
create policy "evidence_auth_delete" on storage.objects for delete using (bucket_id = 'evidence' and auth.uid() is not null);
