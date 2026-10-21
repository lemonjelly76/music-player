-- Playlists of single songs, separate from lists of albums to try. Each replaces its activity's playlist in the
-- person's account; album lists no longer write one.
create table playlist (
    id                   uuid primary key,
    person_id            uuid        not null references person (id) on delete cascade,
    category_id          uuid references category (id) on delete set null,
    name                 text        not null,
    description          text        not null default '',
    created_at           timestamptz not null,
    provider             text,
    provider_playlist_id text
);
create index playlist_by_person on playlist (person_id, created_at desc);
create index playlist_by_synced on playlist (provider_playlist_id);

create table playlist_track (
    playlist_id uuid    not null references playlist (id) on delete cascade,
    position    integer not null,
    provider    text    not null,
    track_id    text    not null,
    artist      text    not null,
    title       text    not null,
    album       text    not null,
    cover_url   text,
    primary key (playlist_id, position)
);
