create table person (
    id            uuid primary key,
    name          text    not null,
    provider      text,
    play_tracking boolean not null,
    lastfm_user   text
);
create unique index person_name on person (lower(name));

-- OAuth tokens for each person's own streaming account (ADR-008)
create table provider_account (
    person_id        uuid not null references person (id) on delete cascade,
    provider         text not null,
    provider_user_id text,
    access_token     text not null,
    refresh_token    text,
    expires_at       timestamptz not null,
    primary key (person_id, provider)
);

create table library_album (
    id             uuid primary key,
    person_id      uuid     not null references person (id) on delete cascade,
    album_key      text     not null,
    artist         text     not null,
    title          text     not null,
    year           integer,
    tidal_id       text,
    spotify_id     text,
    musicbrainz_id text,
    rating         smallint check (rating between 1 and 5),
    notes          text     not null,
    tags           text[]   not null,
    added_on       date     not null,
    unique (person_id, album_key)
);

create table taste_profile (
    person_id       uuid primary key references person (id) on delete cascade,
    summary         text   not null,
    never_recommend text[] not null,
    updated_at      timestamptz
);

create table category (
    id    uuid primary key,
    name  text not null,
    rules text not null
);
create unique index category_name on category (lower(name));

create table play_tracking_override (
    person_id   uuid    not null references person (id) on delete cascade,
    category_id uuid    not null references category (id) on delete cascade,
    enabled     boolean not null,
    primary key (person_id, category_id)
);

-- One row per track played, rolled up to albums when read (ADR-010)
create table play (
    person_id      uuid        not null references person (id) on delete cascade,
    source         text        not null,
    played_at      timestamptz not null,
    album_key      text        not null,
    artist         text        not null,
    title          text        not null,
    year           integer,
    tidal_id       text,
    spotify_id     text,
    musicbrainz_id text,
    category_id    uuid references category (id) on delete set null,
    primary key (person_id, source, played_at)
);
create index play_by_album on play (person_id, album_key);

create table listening_cursor (
    person_id uuid not null references person (id) on delete cascade,
    source    text not null,
    cursor    text not null,
    primary key (person_id, source)
);

create table recommendation_list (
    id                   uuid primary key,
    person_id            uuid        not null references person (id) on delete cascade,
    category_id          uuid references category (id) on delete set null,
    name                 text        not null,
    created_at           timestamptz not null,
    provider             text,
    provider_playlist_id text
);
create index recommendation_list_by_person on recommendation_list (person_id, created_at desc);
create index recommendation_list_by_playlist on recommendation_list (provider_playlist_id);

create table recommendation (
    id             uuid primary key,
    list_id        uuid    not null references recommendation_list (id) on delete cascade,
    position       integer not null,
    artist         text    not null,
    title          text    not null,
    year           integer,
    tidal_id       text,
    spotify_id     text,
    musicbrainz_id text,
    reason         text    not null,
    source         text    not null,
    verdict        text,
    comment        text,
    feedback_at    timestamptz
);
create index recommendation_by_list on recommendation (list_id, position);
