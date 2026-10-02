package fr.esilv.poolup;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PoolupApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsOnPostgres16WithFlywayMigrations() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        }
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class))
                .contains("users", "cities", "trips", "bookings", "messages", "ratings", "reports");
    }

    @Test
    void citiesAreLoadedByFlyway() {
        assertThat(jdbcTemplate.queryForList("SELECT name FROM cities", String.class))
                .contains("Paris", "La Défense", "Lille", "Lyon", "Rouen")
                .doesNotHaveDuplicates();
    }

    @Test
    void schemaConstraintsRejectInvalidData() throws Exception {
        String sql = new ClassPathResource("db/schema_assertions.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        // Execute the entire script: its PostgreSQL function contains internal semicolons.
        // The script owns its transaction and rolls back all fixtures.
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
