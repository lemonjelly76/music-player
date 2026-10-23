-- Each activity has a taste profile of its own; a null category is Mainstream, which used to be called General.
alter table taste_profile drop constraint taste_profile_pkey;
alter table taste_profile add column category_id uuid references category (id) on delete cascade;
alter table taste_profile add constraint taste_profile_once unique nulls not distinct (person_id, category_id);

-- The one profile a person had described every activity. Each activity they have rated starts from a copy, with no
-- date, so Claude knows to rewrite it from that activity's ratings alone.
insert into taste_profile (person_id, category_id, summary, never_recommend, updated_at)
select profile.person_id, rated.category_id, profile.summary, profile.never_recommend, null
from taste_profile profile
join (select album.person_id, rating.category_id
      from album_rating rating join library_album album on album.id = rating.library_album_id
      union
      select song.person_id, rating.category_id
      from song_rating rating join library_song song on song.id = rating.library_song_id) rated
    on rated.person_id = profile.person_id
where rated.category_id is not null;

-- Mainstream's playlists and lists take its name, as an activity's do when it is renamed; those in a streaming
-- account follow when its playlists next change.
update playlist set name = 'AI Picks — Mainstream' where category_id is null;
update recommendation_list set name = 'AI Picks — Mainstream' where category_id is null;
