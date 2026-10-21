-- ADR-015: one 1-3 rating per album per activity (no category = General) replaces the 1-5 library rating and the
-- verdicts on picks.
create table album_rating (
    library_album_id uuid        not null references library_album (id) on delete cascade,
    category_id      uuid references category (id) on delete cascade,
    rating           smallint    not null check (rating between 1 and 3),
    comment          text        not null default '',
    rated_at         timestamptz not null
);
create unique index album_rating_once
    on album_rating (library_album_id, coalesce(category_id, '00000000-0000-0000-0000-000000000000'));

-- Old ratings become General ones: 4-5 love, 3 ok, 1-2 dislike. Unrated albums were added as liked, so love.
insert into album_rating (library_album_id, category_id, rating, rated_at)
select id, null, case when rating is null or rating >= 4 then 3 when rating = 3 then 2 else 1 end, added_on
from library_album;

-- Verdicts on picks carry over for albums in the library (liked picks always joined it): liked is love, not for me
-- is dislike, already know is ok, for the list's activity.
insert into album_rating (library_album_id, category_id, rating, comment, rated_at)
select distinct on (a.id, l.category_id)
       a.id, l.category_id,
       case r.verdict when 'LIKED' then 3 when 'NOT_FOR_ME' then 1 else 2 end,
       coalesce(r.comment, ''), r.feedback_at
from recommendation r
         join recommendation_list l on l.id = r.list_id
         join library_album a on a.person_id = l.person_id
    and lower(a.artist) = lower(r.artist) and lower(a.title) = lower(r.title)
where r.verdict is not null
order by a.id, l.category_id, r.feedback_at desc
on conflict (library_album_id, coalesce(category_id, '00000000-0000-0000-0000-000000000000'))
    do update set rating = excluded.rating, comment = excluded.comment, rated_at = excluded.rated_at;

alter table library_album drop column rating;
alter table recommendation drop column verdict;
alter table recommendation drop column comment;
alter table recommendation drop column feedback_at;

-- Cover art for the app, when a catalogue gives one.
alter table library_album add column cover_url text;
alter table recommendation add column cover_url text;
alter table play add column cover_url text;
