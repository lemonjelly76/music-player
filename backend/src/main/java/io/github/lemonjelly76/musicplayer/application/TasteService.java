package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.TasteProfileRepository;
import io.github.lemonjelly76.musicplayer.domain.taste.TasteProfile;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class TasteService {

    private final TasteProfileRepository repository;
    private final Clock clock;

    public TasteService(TasteProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public TasteProfile profile(PersonId person) {
        return repository.find(person).orElseGet(() -> TasteProfile.empty(person));
    }

    public TasteProfile rewrite(PersonId person, String summary, Optional<List<String>> neverRecommend) {
        TasteProfile rewritten = profile(person).rewritten(summary, neverRecommend, clock.instant());
        repository.save(rewritten);
        return rewritten;
    }
}
