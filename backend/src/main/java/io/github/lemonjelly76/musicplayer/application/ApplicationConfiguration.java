package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.port.AlbumCatalogue;
import io.github.lemonjelly76.musicplayer.domain.port.AlbumWishlist;
import io.github.lemonjelly76.musicplayer.domain.port.CategoryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.ChartRepository;
import io.github.lemonjelly76.musicplayer.domain.port.ChartSource;
import io.github.lemonjelly76.musicplayer.domain.port.LibraryRepository;
import io.github.lemonjelly76.musicplayer.domain.port.ListeningHistory;
import io.github.lemonjelly76.musicplayer.domain.port.LookupCache;
import io.github.lemonjelly76.musicplayer.domain.port.MusicProvider;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlayRepository;
import io.github.lemonjelly76.musicplayer.domain.port.PlaylistRepository;
import io.github.lemonjelly76.musicplayer.domain.port.SongRepository;
import io.github.lemonjelly76.musicplayer.domain.port.RecommendationRepository;
import io.github.lemonjelly76.musicplayer.domain.port.SimilaritySource;
import io.github.lemonjelly76.musicplayer.domain.port.TasteProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

@Configuration
class ApplicationConfiguration {

    @Bean
    Clock clock(@Value("${app.zone}") ZoneId zone) {
        return Clock.system(zone);
    }

    @Bean
    PersonService personService(PersonRepository people) {
        return new PersonService(people);
    }

    @Bean
    CategoryService categoryService(CategoryRepository categories) {
        return new CategoryService(categories);
    }

    @Bean
    TasteService tasteService(TasteProfileRepository profiles, Clock clock) {
        return new TasteService(profiles, clock);
    }

    @Bean
    MusicProviders musicProviders(List<MusicProvider> providers, LookupCache lookups, Clock clock) {
        return new MusicProviders(providers.stream()
                .<MusicProvider>map(service -> new CachingMusicProvider(service, lookups, clock))
                .toList());
    }

    @Bean
    AlbumResolver albumResolver(MusicProviders providers, AlbumCatalogue catalogue) {
        return new AlbumResolver(providers, catalogue);
    }

    @Bean
    LibraryService libraryService(LibraryRepository library, AlbumResolver resolver, MusicProviders providers,
            Clock clock) {
        return new LibraryService(library, resolver, providers, clock);
    }

    @Bean
    RecommendationService recommendationService(RecommendationRepository recommendations, AlbumResolver resolver,
            LibraryService library, MusicProviders providers, Clock clock) {
        return new RecommendationService(recommendations, resolver, library, providers, clock);
    }

    @Bean
    ChartService chartService(ChartSource source, ChartRepository charts, MusicProviders providers,
            PersonRepository people, Clock clock, @Value("${charts.lookup-pause}") Duration lookupPause) {
        return new ChartService(source, charts, providers, people, clock, lookupPause);
    }

    @Bean
    SongService songService(SongRepository songs, MusicProviders providers, Clock clock) {
        return new SongService(songs, providers, clock);
    }

    @Bean
    PlaylistService playlistService(PlaylistRepository playlists, RecommendationRepository recommendations,
            LibraryService library, SongService songs, MusicProviders providers, Clock clock) {
        return new PlaylistService(playlists, recommendations, library, songs, providers, clock);
    }

    @Bean
    PlayService playService(PlayRepository plays, PersonRepository people, PlaylistRepository playlists,
            RecommendationRepository recommendations, List<ListeningHistory> histories, Clock clock) {
        return new PlayService(plays, people, playlists, recommendations, histories, clock);
    }

    @Bean
    CandidateService candidateService(LibraryService library, PlayService plays,
            RecommendationService recommendations, List<SimilaritySource> sources, MusicProviders providers,
            Clock clock) {
        return new CandidateService(library, plays, recommendations, sources, providers, clock);
    }

    @Bean
    WishlistService wishlistService(AlbumWishlist wishlist, AlbumResolver resolver) {
        return new WishlistService(wishlist, resolver);
    }
}
