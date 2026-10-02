package fr.esilv.poolup.trips;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Every night, trips whose departure time has passed go from OPEN/FULL to COMPLETED,
 * which opens the ratings (rule 7). Until then, a departed trip can already no longer be
 * booked, edited or cancelled: those actions check the departure time themselves.
 */
@Component
@RequiredArgsConstructor
public class TripCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(TripCompletionJob.class);

    private final TripService tripService;

    @Scheduled(cron = "${poolup.trips.completion-cron}", zone = "Europe/Paris")
    public void completeDepartedTrips() {
        int completed = tripService.completeDepartedTrips();
        log.info("{} departed trip(s) marked COMPLETED", completed);
    }
}
