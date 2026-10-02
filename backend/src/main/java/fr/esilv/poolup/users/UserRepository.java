package fr.esilv.poolup.users;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Emails are stored normalized (trimmed, lower case): callers must normalize before searching. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Checked on every request carrying a token: a suspended account loses access at once. */
    boolean existsByIdAndStatus(Long id, UserStatus status);

    /** Admin list, newest accounts first. */
    List<User> findAllByOrderByCreatedAtDescIdDesc();

    /** Admin search: {@code pattern} is a lower-case LIKE pattern, e.g. {@code %martin%}. */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.email) LIKE :pattern
               OR LOWER(u.firstName) LIKE :pattern
               OR LOWER(u.lastName) LIKE :pattern
            ORDER BY u.createdAt DESC, u.id DESC
            """)
    List<User> search(@Param("pattern") String pattern);
}
