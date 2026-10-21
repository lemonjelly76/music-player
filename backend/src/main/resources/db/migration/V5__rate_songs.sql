-- Songs rated per activity, as albums are (ADR-015).
create table library_song (
    id        uuid primary key,
    person_id uuid not null references person (id) on delete cascade,
    provider  text not null,
    track_id  text not null,
    artist    text not null,
    title     text not null,
    album     text not null,
    cover_url text,
    added_on  date not null
);
create index library_song_by_person on library_song (person_id);

create table song_rating (
    library_song_id uuid        not null references library_song (id) on delete cascade,
    category_id     uuid references category (id) on delete cascade,
    rating          smallint    not null check (rating between 1 and 3),
    comment         text        not null default '',
    rated_at        timestamptz not null
);
create unique index song_rating_once
    on song_rating (library_song_id, coalesce(category_id, '00000000-0000-0000-0000-000000000000'));
