package fr.esilv.poolup.ratings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    boolean existsByTripIdAndRaterIdAndRatedId(Long tripId, Long raterId, Long ratedId);

    List<Rating> findByTripIdOrderByCreatedAtDesc(Long tripId);

    List<Rating> findByRatedIdOrderByCreatedAtDesc(Long ratedId);

    Optional<Rating> findByTripIdAndRaterIdAndRatedId(Long tripId, Long raterId, Long ratedId);

    @Query("select count(r.id) from Rating r where r.rated.id = :userId")
    long countByRatedId(@Param("userId") Long userId);

    @Query("select avg(r.score) from Rating r where r.rated.id = :userId")
    Double averageByRatedId(@Param("userId") Long userId);
}
