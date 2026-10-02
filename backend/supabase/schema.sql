-- ============================================================================
-- LiveTrackingPPP62 — Supabase schema (free tier, no credit card required)
-- Run this in the Supabase Dashboard: SQL Editor -> New query -> paste -> Run.
-- Also enable "Allow anonymous sign-ins" under Authentication -> Providers.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Tables
-- ----------------------------------------------------------------------------

-- A tracking session, e.g. code PPP6201. Students join with the code,
-- the lecturer owns it through an authenticated account.
create table if not exists public.tracking_sessions (
  id           uuid primary key default gen_random_uuid(),
  code         text not null unique,          -- e.g. 'PPP6201'
  title        text not null default '',
  lecturer_pin text not null default '',  -- legacy column; unused and cleared during migration
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
  marker_type  text not null default 'motorcycle' check (marker_type in ('motorcycle','car','horse','bicycle','bus','walking')),
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
-- 2. Realtime: broadcast position changes so lecturer and student maps update live
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


-- Enable RLS before applying the secure account/membership migration.
alter table public.tracking_sessions enable row level security;
alter table public.session_participants enable row level security;
alter table public.live_positions enable row level security;
alter table public.submissions enable row level security;
alter table public.checkpoints enable row level security;
insert into storage.buckets(id,name,public) values('evidence','evidence',false) on conflict(id) do nothing;
begin;
alter table public.tracking_sessions add column if not exists owner_id uuid references auth.users(id);
alter table public.tracking_sessions alter column lecturer_pin set default '';
update public.tracking_sessions set lecturer_pin = '';
alter table public.session_participants add column if not exists team text not null default '';
alter table public.live_positions add column if not exists event_at timestamptz not null default now();
alter table public.live_positions add column if not exists team text not null default '';
alter table public.live_positions add column if not exists tracking_state text not null default 'LIVE';
alter table public.live_positions add column if not exists marker_type text not null default 'motorcycle';
do $$ begin
 if not exists(select 1 from pg_constraint where conrelid='public.live_positions'::regclass and conname='live_positions_marker_type_check') then
  alter table public.live_positions add constraint live_positions_marker_type_check check(marker_type in ('motorcycle','car','horse','bicycle','bus','walking'));
 end if;
end $$;
alter table public.checkpoints add column if not exists order_index integer not null default 0;
alter table public.checkpoints add column if not exists instructions text not null default '';
alter table public.checkpoints add column if not exists requires_photo boolean not null default true;
alter table public.checkpoints add column if not exists requires_temperature boolean not null default true;
alter table public.checkpoints add column if not exists requires_weight boolean not null default true;
alter table public.submissions add column if not exists checkpoint_id uuid references public.checkpoints(id);
alter table public.submissions add column if not exists exception_reason text;
alter table public.submissions add column if not exists team text not null default '';

-- Security-definer predicates avoid recursive membership policies.
create or replace function public.owns_field_session(s uuid) returns boolean
language sql stable security definer set search_path = public
as $$ select exists(select 1 from tracking_sessions where id=s and owner_id=auth.uid()) $$;
create or replace function public.is_field_member(s uuid) returns boolean
language sql stable security definer set search_path = public
as $$ select public.owns_field_session(s) or exists(select 1 from session_participants where session_id=s and user_id=auth.uid()) $$;
create or replace function public.active_field_member(s uuid) returns boolean
language sql stable security definer set search_path = public
as $$ select public.is_field_member(s) and exists(select 1 from tracking_sessions where id=s and is_active and owner_id is not null) $$;

-- Remove all old permissive policies from these application tables.
do $$ declare p record; begin
 for p in select schemaname,tablename,policyname from pg_policies where schemaname='public' and tablename in ('tracking_sessions','session_participants','live_positions','checkpoints','submissions') loop
 execute format('drop policy %I on %I.%I',p.policyname,p.schemaname,p.tablename);
 end loop;
end $$;
create policy sessions_read on public.tracking_sessions for select to authenticated using(public.is_field_member(id));
create policy participants_read on public.session_participants for select to authenticated using(public.owns_field_session(session_id) or user_id=auth.uid());
create policy positions_read on public.live_positions for select to authenticated using(public.owns_field_session(session_id) or public.active_field_member(session_id));
create policy positions_insert on public.live_positions for insert to authenticated with check(user_id=auth.uid() and public.active_field_member(session_id));
create policy positions_update on public.live_positions for update to authenticated using(user_id=auth.uid() and public.active_field_member(session_id)) with check(user_id=auth.uid() and public.active_field_member(session_id));
create policy checkpoints_read on public.checkpoints for select to authenticated using(public.is_field_member(session_id));
create policy checkpoints_manage on public.checkpoints for all to authenticated using(public.owns_field_session(session_id) and public.active_field_member(session_id)) with check(public.owns_field_session(session_id) and public.active_field_member(session_id));
create policy submissions_read on public.submissions for select to authenticated using(public.owns_field_session(session_id) or user_id=auth.uid());

create or replace function public.create_field_session(p_code text,p_title text,p_checkpoints jsonb)
returns setof public.tracking_sessions language plpgsql security definer set search_path=public as $$
declare s tracking_sessions; cp jsonb; n integer:=0;
begin
 if auth.uid() is null or coalesce((auth.jwt()->>'is_anonymous')::boolean,true) then raise exception 'Lecturer account required'; end if;
 if length(trim(p_title))=0 or p_code !~ '^[A-Z2-9]{6}$' or jsonb_array_length(p_checkpoints)=0 then raise exception 'Title, six-character code and checkpoints required'; end if;
 insert into tracking_sessions(code,title,owner_id) values(p_code,trim(p_title),auth.uid()) returning * into s;
 insert into session_participants(session_id,user_id,display_name,role) values(s.id,auth.uid(),'Lecturer','lecturer');
 for cp in select * from jsonb_array_elements(p_checkpoints) loop
 n:=n+1;
 if length(trim(cp->>'name'))=0 or (cp->>'lat')::float8 not between -90 and 90 or (cp->>'lng')::float8 not between -180 and 180 or (cp->>'radius_m')::float8 not between 20 and 500 then raise exception 'Invalid checkpoint'; end if;
 insert into checkpoints(session_id,name,lat,lng,radius_m,order_index,instructions,requires_photo,requires_temperature,requires_weight)
 values(s.id,trim(cp->>'name'),(cp->>'lat')::float8,(cp->>'lng')::float8,(cp->>'radius_m')::float8,n,coalesce(cp->>'instructions',''),coalesce((cp->>'requires_photo')::boolean,true),coalesce((cp->>'requires_temperature')::boolean,true),coalesce((cp->>'requires_weight')::boolean,true));
 end loop;
 return next s;
end $$;
create or replace function public.join_field_session(p_code text,p_name text,p_team text)
returns setof public.tracking_sessions language plpgsql security definer set search_path=public as $$
declare s tracking_sessions;
begin
 if auth.uid() is null or length(trim(p_name))=0 or length(trim(p_team))=0 then raise exception 'Name and team required'; end if;
 select * into s from tracking_sessions where code=upper(trim(p_code)) and is_active and owner_id is not null for share;
 if s.id is null then raise exception 'Session code unavailable or session closed'; end if;
 if s.owner_id=auth.uid() then raise exception 'Open your session from the lecturer workspace'; end if;
 insert into session_participants(session_id,user_id,display_name,team,role) values(s.id,auth.uid(),trim(p_name),trim(p_team),'student')
 on conflict(session_id,user_id) do update set display_name=excluded.display_name,team=excluded.team;
 return next s;
end $$;

create or replace function public.save_field_checkpoint(p_checkpoint jsonb) returns void
language plpgsql security definer set search_path=public as $$
declare s uuid:=(p_checkpoint->>'session_id')::uuid; cp_id uuid:=(p_checkpoint->>'id')::uuid;
begin
 if not public.owns_field_session(s) or not public.active_field_member(s) then raise exception 'Active session owner required'; end if;
 if length(trim(p_checkpoint->>'name'))=0 or (p_checkpoint->>'lat')::float8 not between -90 and 90 or (p_checkpoint->>'lng')::float8 not between -180 and 180 or (p_checkpoint->>'radius_m')::float8 not between 20 and 500 or (p_checkpoint->>'order_index')::int<1 then raise exception 'Invalid checkpoint'; end if;
 if exists(select 1 from checkpoints where id=cp_id and session_id<>s) then raise exception 'Checkpoint identity conflict'; end if;
 insert into checkpoints(id,session_id,name,lat,lng,radius_m,order_index,instructions,requires_photo,requires_temperature,requires_weight)
 values(cp_id,s,trim(p_checkpoint->>'name'),(p_checkpoint->>'lat')::float8,(p_checkpoint->>'lng')::float8,(p_checkpoint->>'radius_m')::float8,(p_checkpoint->>'order_index')::int,coalesce(p_checkpoint->>'instructions',''),coalesce((p_checkpoint->>'requires_photo')::boolean,true),coalesce((p_checkpoint->>'requires_temperature')::boolean,true),coalesce((p_checkpoint->>'requires_weight')::boolean,true))
 on conflict(id) do update set name=excluded.name,lat=excluded.lat,lng=excluded.lng,radius_m=excluded.radius_m,order_index=excluded.order_index,instructions=excluded.instructions,requires_photo=excluded.requires_photo,requires_temperature=excluded.requires_temperature,requires_weight=excluded.requires_weight;
end $$;
revoke all on function public.save_field_checkpoint(jsonb) from public,anon;
grant execute on function public.save_field_checkpoint(jsonb) to authenticated;

create or replace function public.close_field_session(p_session uuid) returns void
language plpgsql security definer set search_path=public as $$
begin
 if not public.owns_field_session(p_session) then raise exception 'Owner required'; end if;
 update tracking_sessions set is_active=false where id=p_session;
 update live_positions set tracking_state='FINISHED',updated_at=now() where session_id=p_session;
end $$;
create or replace function public.submit_field_evidence(p_record jsonb) returns void
language plpgsql security definer set search_path=public as $$
declare s uuid:=(p_record->>'session_id')::uuid; cp checkpoints; rec_id uuid:=(p_record->>'id')::uuid; reason text; distance_m float8;
begin
 if auth.uid() is null or not public.is_field_member(s) or (p_record->>'user_id')::uuid<>auth.uid() then raise exception 'Membership required'; end if;
 -- A retry may safely acknowledge an existing record, including a closed session.
 if exists(select 1 from submissions where id=rec_id and session_id=s and user_id=auth.uid()) then return; end if;
 if not public.active_field_member(s) then raise exception 'Session closed'; end if;
 select * into cp from checkpoints where id=(p_record->>'checkpoint_id')::uuid and session_id=s;
 if cp.id is null then raise exception 'Checkpoint unavailable'; end if;
 if p_record->>'condition' not in ('GOOD','STRESSED','MORTALITY') then raise exception 'Invalid condition'; end if;
 if cp.requires_photo and nullif(p_record->>'photo_path','') is null then raise exception 'Photo required'; end if;
 if p_record->>'photo_path' is not null and p_record->>'photo_path'<>s::text||'/'||auth.uid()::text||'/'||rec_id::text||'.jpg' then raise exception 'Invalid evidence path'; end if;
 if p_record->>'photo_path' is not null and not exists(select 1 from storage.objects where bucket_id='evidence' and name=p_record->>'photo_path') then raise exception 'Upload photo first'; end if;
 if (p_record->>'weight_kg')::float8 < 0 or (p_record->>'weight_kg')::float8 in ('NaN'::float8,'Infinity'::float8,'-Infinity'::float8) or (p_record->>'temperature_c')::float8 in ('NaN'::float8,'Infinity'::float8,'-Infinity'::float8) then raise exception 'Invalid measurements'; end if;
 if cp.requires_temperature and p_record->>'temperature_c' is null or cp.requires_weight and p_record->>'weight_kg' is null then raise exception 'Required measurement missing'; end if;
 if p_record->>'lat' is null or p_record->>'lng' is null then reason:='GPS unavailable';
 else
 if (p_record->>'lat')::float8 not between -90 and 90 or (p_record->>'lng')::float8 not between -180 and 180 then raise exception 'Invalid coordinates'; end if;
 distance_m:=6371000*2*asin(sqrt(least(1,power(sin(radians((p_record->>'lat')::float8-cp.lat)/2),2)+cos(radians(cp.lat))*cos(radians((p_record->>'lat')::float8))*power(sin(radians((p_record->>'lng')::float8-cp.lng)/2),2))));
 if distance_m>cp.radius_m then reason:='Outside checkpoint radius'; end if;
 end if;
 reason:=coalesce(reason,nullif(p_record->>'exception_reason',''));
 insert into submissions(id,session_id,user_id,display_name,team,checkpoint_id,checkpoint_name,note,photo_path,lat,lng,temperature_c,weight_kg,condition,exception_reason,created_at)
 values(rec_id,s,auth.uid(),(select display_name from session_participants where session_id=s and user_id=auth.uid()),(select team from session_participants where session_id=s and user_id=auth.uid()),cp.id,cp.name,coalesce(p_record->>'note',''),p_record->>'photo_path',(p_record->>'lat')::float8,(p_record->>'lng')::float8,(p_record->>'temperature_c')::float8,(p_record->>'weight_kg')::float8,p_record->>'condition',reason,coalesce((p_record->>'created_at')::timestamptz,now()))
 on conflict(id) do nothing;
 if not exists(select 1 from submissions where id=rec_id and user_id=auth.uid() and session_id=s) then raise exception 'Record identity conflict'; end if;
end $$;
create or replace function public.touch_position() returns trigger language plpgsql security definer set search_path=public as $$
begin
 new.event_at=least(new.event_at,now());
 if TG_OP='UPDATE' and new.event_at<old.event_at then return null; end if;
 if new.lat not between -90 and 90 or new.lng not between -180 and 180 or new.accuracy<0 or new.accuracy in ('NaN'::float8,'Infinity'::float8) or new.tracking_state not in ('LIVE','PAUSED','FINISHED','STALE') then raise exception 'Invalid position'; end if;
 select display_name,team into new.display_name,new.team from session_participants where session_id=new.session_id and user_id=new.user_id;
 new.updated_at=now(); return new;
end $$;
drop trigger if exists touch_position on public.live_positions;
create trigger touch_position before insert or update on public.live_positions for each row execute function public.touch_position();

update storage.buckets set public=false,file_size_limit=10485760,allowed_mime_types=array['image/jpeg'] where id='evidence';
drop policy if exists evidence_public_read on storage.objects;
drop policy if exists evidence_auth_insert on storage.objects;
drop policy if exists evidence_auth_update on storage.objects;
drop policy if exists evidence_auth_delete on storage.objects;
drop policy if exists evidence_read on storage.objects;
drop policy if exists evidence_insert on storage.objects;
drop policy if exists evidence_update on storage.objects;
create policy evidence_read on storage.objects for select to authenticated using(bucket_id='evidence' and exists(select 1 from public.submissions s where s.photo_path=name and (s.user_id=auth.uid() or public.owns_field_session(s.session_id))) or bucket_id='evidence' and (storage.foldername(name))[2]=auth.uid()::text);
create policy evidence_insert on storage.objects for insert to authenticated with check(bucket_id='evidence' and (storage.foldername(name))[2]=auth.uid()::text and exists(select 1 from public.tracking_sessions s where s.id::text=(storage.foldername(name))[1] and public.active_field_member(s.id)));
create policy evidence_update on storage.objects for update to authenticated using(bucket_id='evidence' and (storage.foldername(name))[2]=auth.uid()::text and not exists(select 1 from public.submissions where photo_path=name)) with check(bucket_id='evidence' and (storage.foldername(name))[2]=auth.uid()::text and not exists(select 1 from public.submissions where photo_path=name));
-- RPCs are authenticated-only. No client may directly insert memberships or evidence rows.
revoke all on function public.create_field_session(text,text,jsonb),public.join_field_session(text,text,text),public.close_field_session(uuid),public.submit_field_evidence(jsonb) from public,anon;
grant execute on function public.create_field_session(text,text,jsonb),public.join_field_session(text,text,text),public.close_field_session(uuid),public.submit_field_evidence(jsonb) to authenticated;
do $$ begin if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='tracking_sessions') then alter publication supabase_realtime add table public.tracking_sessions; end if; end $$;
commit;
