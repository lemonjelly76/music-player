-- Removing an activity takes its album lists and playlists with it, rather than leaving them to turn up in General.
-- Plays keep counting as general listening.
alter table recommendation_list drop constraint recommendation_list_category_id_fkey,
    add constraint recommendation_list_category_id_fkey
        foreign key (category_id) references category (id) on delete cascade;
alter table playlist drop constraint playlist_category_id_fkey,
    add constraint playlist_category_id_fkey
        foreign key (category_id) references category (id) on delete cascade;
