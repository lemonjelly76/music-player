-- What each service answered when asked for a song or album by name; no item_id means it had none.
create table catalogue_lookup (
    provider     text        not null,
    kind         text        not null check (kind in ('SONG', 'ALBUM')),
    lookup_key   text        not null,
    looked_up_at timestamptz not null,
    item_id      text,
    artist       text,
    title        text,
    album        text,
    year         integer,
    cover_url    text,
    primary key (provider, kind, lookup_key)
);
