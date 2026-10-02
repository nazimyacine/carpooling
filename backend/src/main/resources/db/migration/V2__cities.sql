-- Reference list of places a driver can choose from (no external geocoding).
-- Coordinates: approximate centre (WGS84), used to place markers on the Leaflet map.

-- The driver picks a city by name: two entries with the same name would be ambiguous.
ALTER TABLE cities ADD CONSTRAINT uq_cities_name UNIQUE (name);

INSERT INTO cities (name, latitude, longitude) VALUES
    -- Île-de-France (La Défense is listed on its own, as in the mockups)
    ('Paris', 48.856600, 2.352200),
    ('La Défense', 48.892000, 2.238000),
    ('Nanterre', 48.892400, 2.207100),
    ('Versailles', 48.804900, 2.120400),
    ('Cergy', 49.036400, 2.076100),
    -- Nord and Bassin parisien
    ('Lille', 50.629200, 3.057300),
    ('Amiens', 49.894100, 2.295800),
    ('Beauvais', 49.429500, 2.080700),
    ('Calais', 50.951300, 1.858700),
    ('Dunkerque', 51.034400, 2.376800),
    ('Rouen', 49.443100, 1.099300),
    ('Le Havre', 49.494400, 0.107900),
    ('Caen', 49.182900, -0.370700),
    ('Reims', 49.258300, 4.031700),
    ('Troyes', 48.297300, 4.074400),
    ('Chartres', 48.443900, 1.489000),
    ('Orléans', 47.903000, 1.909300),
    ('Tours', 47.394100, 0.684800),
    ('Le Mans', 48.006100, 0.199600),
    -- Ouest
    ('Rennes', 48.117300, -1.677800),
    ('Brest', 48.390400, -4.486100),
    ('Nantes', 47.218400, -1.553600),
    ('Angers', 47.478400, -0.563200),
    ('Poitiers', 46.580200, 0.340400),
    ('La Rochelle', 46.160300, -1.151100),
    -- Est
    ('Strasbourg', 48.573400, 7.752100),
    ('Metz', 49.119300, 6.175700),
    ('Nancy', 48.692100, 6.184400),
    ('Dijon', 47.322000, 5.041500),
    ('Besançon', 47.237800, 6.024100),
    -- Centre et Rhône-Alpes
    ('Lyon', 45.764000, 4.835700),
    ('Saint-Étienne', 45.439700, 4.387200),
    ('Grenoble', 45.188500, 5.724500),
    ('Annecy', 45.899200, 6.129400),
    ('Clermont-Ferrand', 45.777200, 3.087000),
    ('Limoges', 45.833600, 1.261100),
    -- Sud-Ouest
    ('Bordeaux', 44.837800, -0.579200),
    ('Toulouse', 43.604700, 1.444200),
    ('Pau', 43.295100, -0.370800),
    ('Bayonne', 43.492900, -1.474800),
    -- Sud-Est
    ('Montpellier', 43.610800, 3.876700),
    ('Nîmes', 43.836700, 4.360100),
    ('Perpignan', 42.688700, 2.894800),
    ('Avignon', 43.949300, 4.805500),
    ('Marseille', 43.296500, 5.369800),
    ('Aix-en-Provence', 43.529700, 5.447400),
    ('Toulon', 43.124200, 5.928000),
    ('Nice', 43.710200, 7.262000);
