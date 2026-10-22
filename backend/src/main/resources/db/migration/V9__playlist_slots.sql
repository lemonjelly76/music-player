-- Up to three playlists per person and activity stay in their account, as slots 1 to 3, named with the number.
-- name_in_account is what the playlist there is called, so one named before it had a number is renamed later.
alter table playlist
    add column slot            smallint check (slot between 1 and 3),
    add column name_in_account text,
    add constraint playlist_slot_is_written check (slot is null or provider_playlist_id is not null);

-- The playlist each person has in their account for each activity now: the newest one written there.
update playlist p
set slot = 1, name_in_account = p.name
where p.provider_playlist_id is not null
  and p.created_at = (select max(q.created_at) from playlist q
                      where q.person_id = p.person_id
                        and q.category_id is not distinct from p.category_id
                        and q.provider_playlist_id is not null);
