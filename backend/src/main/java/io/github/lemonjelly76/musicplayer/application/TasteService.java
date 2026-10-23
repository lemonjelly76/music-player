package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.TasteProfileRepository;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Each person's taste, one profile per activity, none counting for another.
 */
public class TasteService {

    private final TasteProfileRepository repository;
    private final Clock clock;

    public TasteService(TasteProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public TasteProfile profile(PersonId person, Optional<CategoryId> category) {
        return repository.find(person, category).orElseGet(() -> TasteProfile.empty(person, category));
    }

    public TasteProfile rewrite(PersonId person, Optional<CategoryId> category, String summary,
            Optional<List<String>> neverRecommend) {
        TasteProfile rewritten = profile(person, category).rewritten(summary, neverRecommend, clock.instant());
        repository.save(rewritten);
        return rewritten;
    }
}
