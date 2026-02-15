package com.example.walletservice.persistence;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WalletRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WalletRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BigDecimal> findBalance(UUID walletId) {
        String sql = "SELECT balance FROM wallets WHERE id = :id";
        var params = new MapSqlParameterSource("id", walletId);

        try {
            BigDecimal balance = jdbc.queryForObject(sql, params, BigDecimal.class);
            return Optional.ofNullable(balance);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean exists(UUID walletId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM wallets WHERE id = :id)";
        var params = new MapSqlParameterSource("id", walletId);
        Boolean exists = jdbc.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Atomic update:
     * - один SQL UPDATE
     * - Postgres сам делает row-level lock
     * - нет read-modify-write в Java => нет lost update под высокой конкуренцией
     */
    public Optional<BigDecimal> applyDelta(UUID walletId, BigDecimal delta) {
        String sql = """
                UPDATE wallets
                   SET balance = balance + :delta
                 WHERE id = :id
                   AND balance + :delta >= 0
                RETURNING balance
                """;

        var params = new MapSqlParameterSource()
                .addValue("id", walletId)
                .addValue("delta", delta);

        return jdbc.query(sql, params, rs -> rs.next()
                ? Optional.of(rs.getBigDecimal("balance"))
                : Optional.empty());
    }
}