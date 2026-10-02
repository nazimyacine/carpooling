-- Run after the Flyway migrations with psql -v ON_ERROR_STOP=1. All fixtures are rolled back.
BEGIN;

CREATE FUNCTION pg_temp.assert_rejected(statement TEXT, expected_state TEXT, expected_constraint TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    actual_state TEXT;
    actual_constraint TEXT;
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE,
            actual_constraint = CONSTRAINT_NAME;
        IF actual_state = expected_state AND actual_constraint = expected_constraint THEN
            RETURN;
        END IF;
        RAISE EXCEPTION 'Unexpected rejection: % / %, expected % / %',
            actual_state, actual_constraint, expected_state, expected_constraint;
    END;
    RAISE EXCEPTION 'Invalid statement was accepted: %', statement;
END;
$$;

INSERT INTO users (id, email, password_hash, first_name, last_name) VALUES
    (-101, 'schema-driver@example.test', 'test-hash', 'Test', 'Driver'),
    (-102, 'schema-passenger@example.test', 'test-hash', 'Test', 'Passenger');
INSERT INTO cities (id, name, latitude, longitude) VALUES
    (-101, 'Test departure', 48.856600, 2.352200),
    (-102, 'Test arrival', 45.764000, 4.835700);
INSERT INTO trips (id, driver_id, departure_city_id, arrival_city_id, meeting_point,
    departure_at, seats_total, seats_available, price_per_seat)
VALUES (-101, -101, -101, -102, 'Station', CURRENT_TIMESTAMP + INTERVAL '1 day', 2, 2, 12.50);
INSERT INTO bookings (id, trip_id, passenger_id, seats) VALUES (-101, -101, -102, 1);
INSERT INTO messages (id, trip_id, sender_id, content) VALUES (-101, -101, -102, 'Hello');
INSERT INTO ratings (id, trip_id, rater_id, rated_id, score) VALUES (-101, -101, -102, -101, 5);
INSERT INTO reports (id, reporter_id, target_type, target_id, reason)
VALUES (-101, -102, 'MESSAGE', -101, 'Test reason');

SELECT pg_temp.assert_rejected($q$UPDATE users SET email = 'schema-driver@example.test' WHERE id = -102$q$, '23505', 'uq_users_email');
SELECT pg_temp.assert_rejected($q$UPDATE users SET role = 'DRIVER' WHERE id = -101$q$, '23514', 'ck_users_role');
SELECT pg_temp.assert_rejected($q$UPDATE users SET status = 'UNKNOWN' WHERE id = -101$q$, '23514', 'ck_users_status');
SELECT pg_temp.assert_rejected($q$UPDATE cities SET latitude = 91 WHERE id = -101$q$, '23514', 'ck_cities_latitude');
SELECT pg_temp.assert_rejected($q$UPDATE cities SET longitude = -181 WHERE id = -101$q$, '23514', 'ck_cities_longitude');
SELECT pg_temp.assert_rejected($q$UPDATE cities SET name = 'Test departure' WHERE id = -102$q$, '23505', 'uq_cities_name');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET seats_total = 0, seats_available = 0 WHERE id = -101$q$, '23514', 'ck_trips_seats_total');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET seats_total = 9 WHERE id = -101$q$, '23514', 'ck_trips_seats_total');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET seats_available = -1 WHERE id = -101$q$, '23514', 'ck_trips_seats_available');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET seats_available = 3 WHERE id = -101$q$, '23514', 'ck_trips_seats_available');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET price_per_seat = 0 WHERE id = -101$q$, '23514', 'ck_trips_price');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET price_per_seat = -1 WHERE id = -101$q$, '23514', 'ck_trips_price');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET arrival_city_id = departure_city_id WHERE id = -101$q$, '23514', 'ck_trips_cities');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET status = 'UNKNOWN' WHERE id = -101$q$, '23514', 'ck_trips_status');
SELECT pg_temp.assert_rejected($q$UPDATE trips SET driver_id = -999 WHERE id = -101$q$, '23503', 'trips_driver_id_fkey');
SELECT pg_temp.assert_rejected($q$UPDATE bookings SET seats = 0 WHERE id = -101$q$, '23514', 'ck_bookings_seats');
SELECT pg_temp.assert_rejected($q$UPDATE bookings SET seats = 9 WHERE id = -101$q$, '23514', 'ck_bookings_seats');
SELECT pg_temp.assert_rejected($q$UPDATE bookings SET status = 'UNKNOWN' WHERE id = -101$q$, '23514', 'ck_bookings_status');
SELECT pg_temp.assert_rejected($q$INSERT INTO bookings (trip_id, passenger_id, seats) VALUES (-101, -102, 1)$q$, '23505', 'uq_bookings_confirmed_passenger_trip');
SELECT pg_temp.assert_rejected($q$UPDATE ratings SET score = 0 WHERE id = -101$q$, '23514', 'ck_ratings_score');
SELECT pg_temp.assert_rejected($q$UPDATE ratings SET score = 6 WHERE id = -101$q$, '23514', 'ck_ratings_score');
SELECT pg_temp.assert_rejected($q$UPDATE ratings SET rated_id = rater_id WHERE id = -101$q$, '23514', 'ck_ratings_different_users');
SELECT pg_temp.assert_rejected($q$INSERT INTO ratings (trip_id, rater_id, rated_id, score) VALUES (-101, -102, -101, 4)$q$, '23505', 'uq_ratings_trip_rater_rated');
SELECT pg_temp.assert_rejected($q$UPDATE reports SET target_type = 'RATING' WHERE id = -101$q$, '23514', 'ck_reports_target_type');
SELECT pg_temp.assert_rejected($q$UPDATE reports SET status = 'UNKNOWN' WHERE id = -101$q$, '23514', 'ck_reports_status');

-- A cancelled booking does not prevent a new confirmed booking or more history.
UPDATE bookings SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP WHERE id = -101;
INSERT INTO bookings (id, trip_id, passenger_id, seats) VALUES (-102, -101, -102, 1);
INSERT INTO bookings (id, trip_id, passenger_id, seats, status, cancelled_at)
VALUES (-103, -101, -102, 1, 'CANCELLED', CURRENT_TIMESTAMP);
SELECT pg_temp.assert_rejected($q$UPDATE bookings SET status = 'CONFIRMED' WHERE id = -101$q$, '23505', 'uq_bookings_confirmed_passenger_trip');

-- Both ends of the seat range and distinct rating directions remain valid.
UPDATE trips SET seats_total = 1, seats_available = 0, status = 'FULL' WHERE id = -101;
UPDATE trips SET seats_total = 8, seats_available = 8, status = 'OPEN' WHERE id = -101;
UPDATE ratings SET score = 1 WHERE id = -101;
INSERT INTO ratings (id, trip_id, rater_id, rated_id, score) VALUES (-102, -101, -101, -102, 5);

ROLLBACK;
