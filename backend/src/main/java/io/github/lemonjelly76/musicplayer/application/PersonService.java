package io.github.lemonjelly76.musicplayer.application;

import io.github.lemonjelly76.musicplayer.domain.album.Provider;
import io.github.lemonjelly76.musicplayer.domain.person.Person;
import io.github.lemonjelly76.musicplayer.domain.person.PersonId;
import io.github.lemonjelly76.musicplayer.domain.port.PersonRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PersonService {

    /**
     * Spotify's Development Mode lets five accounts use one app, and each person links their own account.
     */
    static final int MAX_PEOPLE = 5;

    private final PersonRepository repository;

    public PersonService(PersonRepository repository) {
        this.repository = repository;
    }

    public List<Person> all() {
        return repository.findAll().stream().sorted(Comparator.comparing(Person::name)).toList();
    }

    public Person add(String name, Optional<Provider> provider) {
        List<Person> everyone = repository.findAll();
        if (everyone.stream().anyMatch(person -> person.isCalled(name))) {
            throw new IllegalArgumentException("There is already someone called " + name.strip());
        }
        if (everyone.size() >= MAX_PEOPLE) {
            throw new IllegalArgumentException("There can be at most " + MAX_PEOPLE + " people");
        }
        Person person = Person.create(name, provider);
        repository.save(person);
        return person;
    }

    public Person get(PersonId id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("No person with id " + id));
    }

    /**
     * Finds someone by name, ignoring case, or by id.
     */
    public Person find(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            throw new IllegalArgumentException("Say whose music this is: a name or id from list_people");
        }
        return Ids.uuid(nameOrId).flatMap(id -> repository.findById(new PersonId(id)))
                .or(() -> repository.findAll().stream().filter(person -> person.isCalled(nameOrId)).findFirst())
                .orElseThrow(() -> new NotFoundException("No person called '" + nameOrId.strip()
                        + "'; use list_people to see who there is, or add_person"));
    }

    public Person update(Person person) {
        get(person.id());
        repository.save(person);
        return person;
    }
}
