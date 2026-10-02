begin;

alter table public.checkpoints alter column requires_weight set default false;
update public.checkpoints set requires_weight=false where requires_weight;
alter table public.submissions add column if not exists total_fish integer;
alter table public.submissions add column if not exists ph double precision;
alter table public.submissions add column if not exists dissolved_oxygen double precision;

create or replace function public.submit_field_evidence(p_record jsonb) returns void
language plpgsql security definer set search_path=public as $$
declare
 s uuid:=(p_record->>'session_id')::uuid;
 cp checkpoints;
 rec_id uuid:=(p_record->>'id')::uuid;
 reason text;
 distance_m float8;
 temperature_c float8:=nullif(p_record->>'temperature_c','')::float8;
 legacy_weight_kg float8:=nullif(p_record->>'weight_kg','')::float8;
 ph_value float8:=nullif(p_record->>'ph','')::float8;
 oxygen_value float8:=nullif(p_record->>'dissolved_oxygen','')::float8;
 fish_count integer:=nullif(p_record->>'total_fish','')::integer;
begin
 if auth.uid() is null or not public.is_field_member(s) or (p_record->>'user_id')::uuid<>auth.uid() then raise exception 'Membership required'; end if;
 if exists(select 1 from submissions where id=rec_id and session_id=s and user_id=auth.uid()) then return; end if;
 if not public.active_field_member(s) then raise exception 'Session closed'; end if;
 select * into cp from checkpoints where id=(p_record->>'checkpoint_id')::uuid and session_id=s;
 if cp.id is null then raise exception 'Checkpoint unavailable'; end if;
 if p_record->>'condition' not in ('GOOD','STRESSED','MORTALITY') then raise exception 'Invalid fish condition'; end if;
 if cp.requires_photo and nullif(p_record->>'photo_path','') is null then raise exception 'Photo required'; end if;
 if p_record->>'photo_path' is not null and p_record->>'photo_path'<>s::text||'/'||auth.uid()::text||'/'||rec_id::text||'.jpg' then raise exception 'Invalid evidence path'; end if;
 if p_record->>'photo_path' is not null and not exists(select 1 from storage.objects where bucket_id='evidence' and name=p_record->>'photo_path') then raise exception 'Upload photo first'; end if;
 if temperature_c is not null and (temperature_c in ('NaN'::float8,'Infinity'::float8,'-Infinity'::float8) or temperature_c not between -2 and 60) then raise exception 'Invalid water temperature'; end if;
 if legacy_weight_kg is not null and (legacy_weight_kg<0 or legacy_weight_kg in ('NaN'::float8,'Infinity'::float8,'-Infinity'::float8)) then raise exception 'Invalid legacy weight'; end if;
 if ph_value is not null and (ph_value in ('NaN'::float8,'Infinity'::float8,'-Infinity'::float8) or ph_value not between 0 and 14) then raise exception 'Invalid pH'; end if;
 if oxygen_value is not null and (oxygen_value in ('NaN'::float8,'Infinity'::float8,'-Infinity'::float8) or oxygen_value not between 0 and 60) then raise exception 'Invalid dissolved oxygen'; end if;
 if fish_count is not null and fish_count<0 then raise exception 'Invalid total fish'; end if;
 if cp.requires_temperature and temperature_c is null then raise exception 'Water temperature required'; end if;
 if p_record->>'lat' is null or p_record->>'lng' is null then reason:='GPS unavailable';
 else
  if (p_record->>'lat')::float8 not between -90 and 90 or (p_record->>'lng')::float8 not between -180 and 180 then raise exception 'Invalid coordinates'; end if;
  distance_m:=6371000*2*asin(sqrt(least(1,power(sin(radians((p_record->>'lat')::float8-cp.lat)/2),2)+cos(radians(cp.lat))*cos(radians((p_record->>'lat')::float8))*power(sin(radians((p_record->>'lng')::float8-cp.lng)/2),2))));
  if distance_m>cp.radius_m then reason:='Outside checkpoint radius'; end if;
 end if;
 reason:=coalesce(reason,nullif(p_record->>'exception_reason',''));
 insert into submissions(id,session_id,user_id,display_name,team,checkpoint_id,checkpoint_name,note,photo_path,lat,lng,temperature_c,weight_kg,total_fish,ph,dissolved_oxygen,condition,exception_reason,created_at)
 values(rec_id,s,auth.uid(),(select display_name from session_participants where session_id=s and user_id=auth.uid()),(select team from session_participants where session_id=s and user_id=auth.uid()),cp.id,cp.name,coalesce(p_record->>'note',''),p_record->>'photo_path',(p_record->>'lat')::float8,(p_record->>'lng')::float8,temperature_c,legacy_weight_kg,fish_count,ph_value,oxygen_value,p_record->>'condition',reason,coalesce((p_record->>'created_at')::timestamptz,now()))
 on conflict(id) do nothing;
 if not exists(select 1 from submissions where id=rec_id and user_id=auth.uid() and session_id=s) then raise exception 'Record identity conflict'; end if;
end $$;

commit;
