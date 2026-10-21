package io.github.lemonjelly76.musicplayer.domain.port;

import io.github.lemonjelly76.musicplayer.domain.category.CategoryId;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationList;
import io.github.lemonjelly76.musicplayer.domain.recommendation.RecommendationListId;

import java.util.List;
import java.util.Optional;

public interface RecommendationRepository {

    void save(RecommendationList list);

    Optional<RecommendationList> findById(RecommendationListId id);

    /**
     * A person's lists, newest first; with a category, only that category's lists, else every list.
     */
    List<RecommendationList> findByPerson(PersonId person, Optional<CategoryId> category, int limit);

    Optional<RecommendationList> findByPlaylist(String playlistId);
}
