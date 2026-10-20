package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.AlbumRef;
import io.github.lemonjelly76.musicplayer.domain.port.AlbumWishlist;

import java.util.Optional;

public class WishlistService {

    private final AlbumWishlist wishlist;
    private final AlbumResolver resolver;

    public WishlistService(AlbumWishlist wishlist, AlbumResolver resolver) {
        this.wishlist = wishlist;
        this.resolver = resolver;
    }

    public String want(String reference) {
        if (!wishlist.configured()) {
            throw new IllegalArgumentException("Lidarr is not set up; set LIDARR_URL and LIDARR_API_KEY");
        }
        AlbumRef album = resolver.resolve(reference, Optional.empty()).album()
                .orElseThrow(() -> new IllegalArgumentException("No album " + reference + " could be found"));
        return wishlist.want(album);
    }
}
