-- An activity's newest playlist kept on a second service too (ADR-005); a null category is General.
create table playlist_copy (
    person_id            uuid not null references person (id) on delete cascade,
    category_id          uuid references category (id) on delete cascade,
    provider             text not null,
    provider_playlist_id text,
    unique nulls not distinct (person_id, category_id)
);

create index playlist_copy_by_playlist on playlist_copy (provider_playlist_id);
