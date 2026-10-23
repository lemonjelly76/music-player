-- Artists rated per activity, as albums and songs are (ADR-015).
create table library_artist (
    id        uuid primary key,
    person_id uuid not null references person (id) on delete cascade,
    name      text not null,
    added_on  date not null
);
create index library_artist_by_person on library_artist (person_id);

create table artist_rating (
    library_artist_id uuid        not null references library_artist (id) on delete cascade,
    category_id       uuid references category (id) on delete cascade,
    rating            smallint    not null check (rating between 1 and 3),
    comment           text        not null default '',
    rated_at          timestamptz not null,
    unique nulls not distinct (library_artist_id, category_id)
);
