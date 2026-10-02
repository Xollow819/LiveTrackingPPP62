begin;

alter table public.live_positions
  add column if not exists marker_type text not null default 'motorcycle';

do $$ begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.live_positions'::regclass
      and conname = 'live_positions_marker_type_check'
  ) then
    alter table public.live_positions
      add constraint live_positions_marker_type_check
      check (marker_type in ('motorcycle','car','horse','bicycle','bus','walking'));
  end if;
end $$;

drop policy if exists positions_read on public.live_positions;
create policy positions_read on public.live_positions for select to authenticated
  using (public.owns_field_session(session_id) or public.active_field_member(session_id));

commit;
