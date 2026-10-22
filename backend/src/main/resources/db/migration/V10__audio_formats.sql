-- The qualities beyond the ordinary each album and song can be played in, as Tidal reports them: a comma-separated
-- list such as 'LOSSLESS,HI_RES,DOLBY_ATMOS', or null when no service said.
alter table library_album add column formats text;
alter table recommendation add column formats text;
alter table play add column formats text;
alter table library_song add column formats text;
alter table playlist_track add column formats text;
alter table chart_entry add column formats text;
alter table catalogue_lookup add column formats text;
