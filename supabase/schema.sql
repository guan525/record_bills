create table if not exists public.ledger_entries (
  id text primary key,
  owner_key text not null,
  type text not null,
  status text not null,
  amount_cents bigint not null,
  currency text not null default 'CNY',
  occurred_at bigint not null,
  merchant text not null default '',
  title text not null default '',
  category_path text not null default '未分类/待确认/其他',
  account text not null default '待确认账户',
  payment_method text not null default '',
  source_kind text not null default 'SYNC',
  source_package text,
  source_app_name text not null default '',
  raw_text text not null default '',
  confidence integer not null default 50,
  note text not null default '',
  tags text not null default '',
  created_at_ms bigint not null,
  updated_at_ms bigint not null,
  synced_at_ms bigint,
  is_deleted boolean not null default false,
  updated_at timestamptz not null default now()
);

create index if not exists ledger_entries_owner_updated_idx
  on public.ledger_entries(owner_key, updated_at desc);

alter table public.ledger_entries enable row level security;

drop policy if exists ledger_entries_owner_select on public.ledger_entries;
create policy ledger_entries_owner_select
on public.ledger_entries for select
to anon
using (owner_key = coalesce((current_setting('request.headers', true)::json ->> 'x-owner-key'), ''));

drop policy if exists ledger_entries_owner_insert on public.ledger_entries;
create policy ledger_entries_owner_insert
on public.ledger_entries for insert
to anon
with check (owner_key = coalesce((current_setting('request.headers', true)::json ->> 'x-owner-key'), ''));

drop policy if exists ledger_entries_owner_update on public.ledger_entries;
create policy ledger_entries_owner_update
on public.ledger_entries for update
to anon
using (owner_key = coalesce((current_setting('request.headers', true)::json ->> 'x-owner-key'), ''))
with check (owner_key = coalesce((current_setting('request.headers', true)::json ->> 'x-owner-key'), ''));

