-- The latest chart as found on each service, and each person's weekly chart playlist.
create table chart_entry (
    provider   text        not null,
    kind       text        not null check (kind in ('SONG', 'ALBUM')),
    position   integer     not null,
    country    text        not null,
    fetched_at timestamptz not null,
    item_id    text,
    artist     text        not null,
    title      text        not null,
    album      text        not null default '',
    cover_url  text,
    primary key (provider, kind, position)
);

create table chart_playlist (
    person_id   uuid primary key references person (id) on delete cascade,
    wanted      boolean     not null,
    provider    text,
    playlist_id text,
    written_at  timestamptz
);
