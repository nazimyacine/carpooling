-- Demo data, loaded only with the "demo" Spring profile (see application-demo.properties).
-- Repeatable migration: Flyway runs it after the schema scripts, and again whenever this file changes.
-- It first removes the previous demo data (accounts ending in @demo.poolup.fr), then inserts it again.
-- Dates are relative to the loading day, so upcoming trips are always in the future.
-- Every account uses the password "demo1234".

-- ---------------------------------------------------------------------------
-- Helpers (session-local, dropped with the connection)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION pg_temp.demo_user(login TEXT) RETURNS BIGINT
LANGUAGE sql STABLE AS $$ SELECT id FROM users WHERE email = login || '@demo.poolup.fr' $$;

CREATE OR REPLACE FUNCTION pg_temp.demo_city(city_name TEXT) RETURNS BIGINT
LANGUAGE sql STABLE AS $$ SELECT id FROM cities WHERE name = city_name $$;

-- Local time in Paris, days_from_today days from the loading day.
CREATE OR REPLACE FUNCTION pg_temp.demo_at(days_from_today INTEGER, local_time TIME) RETURNS TIMESTAMPTZ
LANGUAGE sql STABLE AS $$
    SELECT ((CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Paris')::date + days_from_today + local_time)
        AT TIME ZONE 'Europe/Paris'
$$;

-- ---------------------------------------------------------------------------
-- 1. Remove the previous demo data
-- ---------------------------------------------------------------------------

CREATE TEMP TABLE demo_user_ids ON COMMIT DROP AS
    SELECT id FROM users WHERE email LIKE '%@demo.poolup.fr';
CREATE TEMP TABLE demo_trip_ids ON COMMIT DROP AS
    SELECT id FROM trips WHERE driver_id IN (SELECT id FROM demo_user_ids);

-- A demo account may have booked a trip created by someone else during a demo:
-- give the seats back before deleting the booking, so that trip stays consistent.
UPDATE trips t
SET seats_available = t.seats_available + b.seats,
    status = CASE WHEN t.status = 'FULL' THEN 'OPEN' ELSE t.status END
FROM (
    SELECT trip_id, SUM(seats) AS seats
    FROM bookings
    WHERE status = 'CONFIRMED'
      AND passenger_id IN (SELECT id FROM demo_user_ids)
      AND trip_id NOT IN (SELECT id FROM demo_trip_ids)
    GROUP BY trip_id
) b
WHERE t.id = b.trip_id AND t.status IN ('OPEN', 'FULL');

DELETE FROM reports
WHERE reporter_id IN (SELECT id FROM demo_user_ids)
   OR (target_type = 'USER' AND target_id IN (SELECT id FROM demo_user_ids))
   OR (target_type = 'TRIP' AND target_id IN (SELECT id FROM demo_trip_ids))
   OR (target_type = 'MESSAGE' AND target_id IN (
        SELECT id FROM messages
        WHERE trip_id IN (SELECT id FROM demo_trip_ids)
           OR sender_id IN (SELECT id FROM demo_user_ids)));
DELETE FROM ratings
WHERE trip_id IN (SELECT id FROM demo_trip_ids)
   OR rater_id IN (SELECT id FROM demo_user_ids)
   OR rated_id IN (SELECT id FROM demo_user_ids);
DELETE FROM messages
WHERE trip_id IN (SELECT id FROM demo_trip_ids)
   OR sender_id IN (SELECT id FROM demo_user_ids);
DELETE FROM bookings
WHERE trip_id IN (SELECT id FROM demo_trip_ids)
   OR passenger_id IN (SELECT id FROM demo_user_ids);
DELETE FROM trips WHERE id IN (SELECT id FROM demo_trip_ids);
DELETE FROM users WHERE id IN (SELECT id FROM demo_user_ids);

-- ---------------------------------------------------------------------------
-- 2. Users (people from the mockups)
-- ---------------------------------------------------------------------------

INSERT INTO users (email, password_hash, first_name, last_name, car_model, role, status, created_at) VALUES
    ('admin@demo.poolup.fr',  '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Alice',   'Admin',    NULL,                'ADMIN', 'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '200 days'),
    ('karim@demo.poolup.fr',  '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Karim',   'Benali',   'Peugeot 208 grise', 'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '180 days'),
    ('ines@demo.poolup.fr',   '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Inès',    'Robert',   'Renault Clio',      'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '150 days'),
    ('hugo@demo.poolup.fr',   '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Hugo',    'Lefèvre',  'Toyota Yaris',      'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '140 days'),
    ('sofia@demo.poolup.fr',  '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Sofia',   'Durand',   'Citroën C3',        'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '120 days'),
    ('camille@demo.poolup.fr','$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Camille', 'Petit',    'Dacia Sandero',     'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '100 days'),
    ('lea@demo.poolup.fr',    '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Léa',     'Martin',   NULL,                'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '90 days'),
    ('theo@demo.poolup.fr',   '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Théo',    'Dubois',   NULL,                'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '80 days'),
    ('paul@demo.poolup.fr',   '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Paul',    'Vincent',  NULL,                'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '60 days'),
    ('max@demo.poolup.fr',    '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Max',     'Thomas',   NULL,                'USER',  'ACTIVE',    CURRENT_TIMESTAMP - INTERVAL '40 days'),
    ('nina@demo.poolup.fr',   '$2a$10$HLuwrw6XL6cvsVc5u80Me.CMgZTJa9DHbUDawtX47MuE3AJ5hnQPi', 'Nina',    'Simon',    NULL,                'USER',  'SUSPENDED', CURRENT_TIMESTAMP - INTERVAL '30 days');

-- ---------------------------------------------------------------------------
-- 3. Trips (seats_available and FULL status are computed in step 5)
-- ---------------------------------------------------------------------------

-- Short labels used below to attach bookings, messages, ratings and reports to trips.
CREATE TEMP TABLE demo_trip (label TEXT PRIMARY KEY, id BIGINT NOT NULL) ON COMMIT DROP;

CREATE OR REPLACE FUNCTION pg_temp.demo_trip(trip_label TEXT) RETURNS BIGINT
LANGUAGE sql STABLE AS $$ SELECT id FROM demo_trip WHERE label = trip_label $$;

WITH new_trips (label, driver, departure, arrival, meeting_point, departure_at, seats_total, price, status, description) AS (
    VALUES
    -- Upcoming
    ('defense-lille',  'karim',   'La Défense', 'Lille',      'Parvis, sortie 4 du métro',            pg_temp.demo_at(1, '08:15'),  3, 18.00, 'OPEN',
        'Un bagage cabine par personne. Je peux faire un arrêt à la sortie de Paris si besoin.'),
    ('paris-lille',    'ines',    'Paris',      'Lille',      'Gare du Nord, devant l''entrée principale', pg_temp.demo_at(1, '10:30'), 2, 16.00, 'OPEN', NULL),
    ('nanterre-lille', 'hugo',    'Nanterre',   'Lille',      'Nanterre Université, sortie du RER A', pg_temp.demo_at(1, '14:00'),  3, 20.00, 'OPEN', NULL),
    ('defense-lille-evening', 'sofia', 'La Défense', 'Lille', 'Centre commercial, entrée principale', pg_temp.demo_at(1, '18:45'),  3, 16.00, 'OPEN', NULL),
    ('lille-defense',  'karim',   'Lille',      'La Défense', 'Gare Lille Europe, dépose-minute',     pg_temp.demo_at(2, '17:00'),  3, 18.00, 'OPEN', NULL),
    ('nanterre-rouen', 'camille', 'Nanterre',   'Rouen',      'Nanterre Université, parking visiteurs', pg_temp.demo_at(7, '17:30'), 4, 14.00, 'OPEN', NULL),
    ('paris-lyon',     'karim',   'Paris',      'Lyon',       'Porte d''Italie',                     pg_temp.demo_at(10, '07:00'), 4, 95.00, 'OPEN', NULL),
    -- Past
    ('paris-reims',    'karim',   'Paris',      'Reims',      'Porte de Bagnolet',                   pg_temp.demo_at(-6, '09:00'), 3, 12.00, 'COMPLETED', NULL),
    ('paris-orleans',  'ines',    'Paris',      'Orléans',    'Porte d''Orléans',                    pg_temp.demo_at(-9, '18:00'), 3, 10.00, 'CANCELLED', NULL),
    ('defense-amiens', 'hugo',    'La Défense', 'Amiens',     'Parvis, sortie 4 du métro',           pg_temp.demo_at(-12, '10:00'), 3, 13.00, 'COMPLETED', NULL)
),
inserted AS (
    INSERT INTO trips (driver_id, departure_city_id, arrival_city_id, meeting_point, departure_at,
                       seats_total, seats_available, price_per_seat, status, description, created_at)
    SELECT pg_temp.demo_user(driver), pg_temp.demo_city(departure), pg_temp.demo_city(arrival), meeting_point,
           departure_at, seats_total, seats_total, price, status, description,
           LEAST(departure_at, CURRENT_TIMESTAMP) - INTERVAL '5 days'
    FROM new_trips
    RETURNING id, driver_id, departure_at, meeting_point
)
INSERT INTO demo_trip (label, id)
SELECT n.label, i.id
FROM new_trips n
JOIN inserted i ON i.driver_id = pg_temp.demo_user(n.driver)
               AND i.departure_at = n.departure_at
               AND i.meeting_point = n.meeting_point;

-- ---------------------------------------------------------------------------
-- 4. Bookings (only on trips the passenger does not drive)
-- ---------------------------------------------------------------------------

INSERT INTO bookings (trip_id, passenger_id, seats, status, created_at, cancelled_at)
SELECT pg_temp.demo_trip(trip), pg_temp.demo_user(passenger), seats, status,
       CURRENT_TIMESTAMP - booked_days_ago * INTERVAL '1 day',
       CASE WHEN status = 'CANCELLED' THEN CURRENT_TIMESTAMP - cancelled_days_ago * INTERVAL '1 day' END
FROM (VALUES
    ('defense-lille',          'lea',     1, 'CONFIRMED', 2,  NULL),
    ('paris-lille',            'theo',    1, 'CONFIRMED', 3,  NULL),
    ('defense-lille-evening',  'paul',    2, 'CONFIRMED', 4,  NULL),
    ('defense-lille-evening',  'theo',    1, 'CONFIRMED', 2,  NULL),
    ('lille-defense',          'theo',    2, 'CONFIRMED', 3,  NULL),
    ('lille-defense',          'paul',    1, 'CONFIRMED', 1,  NULL),
    ('nanterre-rouen',         'lea',     2, 'CONFIRMED', 3,  NULL),
    ('nanterre-rouen',         'paul',    1, 'CONFIRMED', 3,  NULL),
    ('paris-reims',            'lea',     1, 'CONFIRMED', 10, NULL),
    ('paris-reims',            'hugo',    1, 'CONFIRMED', 9,  NULL),
    -- Trip cancelled by the driver: its bookings were cancelled with it (rule 6)
    ('paris-orleans',          'lea',     1, 'CANCELLED', 12, 10),
    ('defense-amiens',         'paul',    1, 'CONFIRMED', 15, NULL),
    -- Cancelled by the passenger before departure
    ('defense-amiens',         'lea',     1, 'CANCELLED', 15, 14)
) AS b (trip, passenger, seats, status, booked_days_ago, cancelled_days_ago);

-- ---------------------------------------------------------------------------
-- 5. Seat counters derived from the bookings (rule 1: never more bookings than seats)
-- ---------------------------------------------------------------------------

UPDATE trips t
SET seats_available = t.seats_total - COALESCE((
        SELECT SUM(b.seats) FROM bookings b WHERE b.trip_id = t.id AND b.status = 'CONFIRMED'), 0)
WHERE t.id IN (SELECT id FROM demo_trip);

UPDATE trips
SET status = 'FULL'
WHERE id IN (SELECT id FROM demo_trip) AND status = 'OPEN' AND seats_available = 0;

-- ---------------------------------------------------------------------------
-- 6. Messages (sender is the driver or a confirmed passenger, rule 5)
-- ---------------------------------------------------------------------------

INSERT INTO messages (trip_id, sender_id, content, sent_at)
SELECT pg_temp.demo_trip(trip), pg_temp.demo_user(sender), content,
       CURRENT_TIMESTAMP - minutes_ago * INTERVAL '1 minute'
FROM (VALUES
    ('defense-lille',  'lea',   'Bonjour ! Je serai au point de rendez-vous dix minutes avant. J''ai juste un petit sac à dos.', 30),
    ('defense-lille',  'karim', 'Parfait. Je serai garé devant la sortie 4, Peugeot 208 grise. Je vous écris en arrivant.', 22),
    ('defense-lille',  'lea',   'Super, merci !', 20),
    ('lille-defense',  'karim', 'Départ devant la gare.', 120),
    ('nanterre-rouen', 'camille', 'Bonjour à tous, je pars à l''heure pile.', 300),
    ('nanterre-rouen', 'paul',  'Profitez de -30 % sur nos cours de conduite, lien dans ma bio !', 240),
    ('paris-reims',    'karim', 'Merci à vous deux, bon week-end à Reims !', 8000)
) AS m (trip, sender, content, minutes_ago);

-- ---------------------------------------------------------------------------
-- 7. Ratings (completed trips only, between people who travelled together, rule 7)
-- ---------------------------------------------------------------------------

INSERT INTO ratings (trip_id, rater_id, rated_id, score, comment, created_at)
SELECT pg_temp.demo_trip(trip), pg_temp.demo_user(rater), pg_temp.demo_user(rated), score, comment,
       CURRENT_TIMESTAMP - days_ago * INTERVAL '1 day'
FROM (VALUES
    ('paris-reims',    'lea',   'karim', 5, 'Conducteur ponctuel et prudent.', 5),
    ('paris-reims',    'hugo',  'karim', 4, NULL, 5),
    ('paris-reims',    'karim', 'lea',   5, 'Passagère très sympathique.', 4),
    ('defense-amiens', 'paul',  'hugo',  5, 'Trajet agréable, je recommande.', 11),
    ('defense-amiens', 'hugo',  'paul',  4, NULL, 11)
) AS r (trip, rater, rated, score, comment, days_ago);

-- ---------------------------------------------------------------------------
-- 8. Reports (admin screen)
-- ---------------------------------------------------------------------------

INSERT INTO reports (reporter_id, target_type, target_id, reason, status, created_at)
VALUES
    (pg_temp.demo_user('lea'), 'MESSAGE',
        (SELECT id FROM messages WHERE trip_id = pg_temp.demo_trip('nanterre-rouen') AND sender_id = pg_temp.demo_user('paul')),
        'Publicité sans rapport avec le trajet', 'OPEN', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    (pg_temp.demo_user('hugo'), 'USER', pg_temp.demo_user('max'),
        'Propos insultants dans une discussion', 'OPEN', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    (pg_temp.demo_user('lea'), 'TRIP', pg_temp.demo_trip('paris-lyon'),
        'Prix anormalement élevé', 'OPEN', CURRENT_TIMESTAMP - INTERVAL '3 days'),
    (pg_temp.demo_user('ines'), 'USER', pg_temp.demo_user('nina'),
        'Ne s''est pas présentée et a insulté le conducteur', 'RESOLVED', CURRENT_TIMESTAMP - INTERVAL '20 days');
