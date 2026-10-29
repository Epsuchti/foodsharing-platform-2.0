-- Load-test volume (D17.1): multiplies the seed rows of the hot tables so list queries run against a realistic
-- amount of data instead of a dozen rows. Run by e2e/load/run.sh after Liquibase (psql in the postgres container).
-- load_clone(table, copies, overrides): inserts `copies` copies of every row; `id` is always a new UUID, the
-- overrides map column -> SQL expression over the source row `t` and the copy number `g` (1..copies).
create or replace function pg_temp.load_clone(tbl text, copies int, overrides jsonb default '{}'::jsonb)
returns bigint language plpgsql as $$
declare
  cols text;
  exprs text;
  n bigint;
begin
  select string_agg(quote_ident(column_name), ', ' order by ordinal_position),
         string_agg(case when column_name = 'id' then 'gen_random_uuid()'
                         when overrides ? column_name then overrides ->> column_name
                         else 't.' || quote_ident(column_name) end, ', ' order by ordinal_position)
    into cols, exprs
    from information_schema.columns
   where table_schema = current_schema() and table_name = tbl;
  execute format('insert into %I (%s) select %s from %I t cross join generate_series(1, %s) g',
                 tbl, cols, exprs, tbl, copies);
  get diagnostics n = row_count;
  return n;
end $$;

-- stores (region store lists, map markers): 60 copies of each seed store, without memberships
select 'store' as tbl, pg_temp.load_clone('store', 60, '{"name": "t.name || '' #'' || g"}');
-- messages: 300 more per seed message, later sequence numbers
select 'message', pg_temp.load_clone('message', 300,
  '{"seq": "t.seq + g * 10000", "created_at": "t.created_at - make_interval(mins => g)"}');
-- bells: 200 per seed bell, older and read
select 'bell', pg_temp.load_clone('bell', 200, ('{"identifier": "t.identifier || ''-load-'' || g",'
  || ' "created_at": "t.created_at - make_interval(hours => g)", "read_at": "t.created_at"}')::jsonb);
-- forum posts: 100 per seed post
select 'forum_post', pg_temp.load_clone('forum_post', 100,
  '{"created_at": "t.created_at - make_interval(mins => g)"}');
-- history: 50 per seed entry
select 'history_entry', pg_temp.load_clone('history_entry', 50,
  '{"occurred_at": "t.occurred_at - make_interval(days => g)"}');
-- past pickup sign-ups: 52 weekly copies a year earlier (the extra seconds keep (store, user, pickup_at) unique)
select 'pickup_signup', pg_temp.load_clone('pickup_signup', 52,
  '{"pickup_at": "t.pickup_at - make_interval(years => 1, weeks => g, secs => g)", "confirmed": "true"}');
analyze;
