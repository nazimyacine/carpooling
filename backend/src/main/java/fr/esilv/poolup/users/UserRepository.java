package fr.esilv.poolup.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Emails are stored normalized (trimmed, lower case): callers must normalize before searching. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
