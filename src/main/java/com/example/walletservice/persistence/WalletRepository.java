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

    public enum ApplyDeltaStatus {
        UPDATED,
        WALLET_NOT_FOUND,
        INSUFFICIENT_FUNDS
    }

    public record ApplyDeltaResult(ApplyDeltaStatus status, BigDecimal balance) {
        public static ApplyDeltaResult updated(BigDecimal balance) {
            return new ApplyDeltaResult(ApplyDeltaStatus.UPDATED, balance);
        }

        public static ApplyDeltaResult walletNotFound() {
            return new ApplyDeltaResult(ApplyDeltaStatus.WALLET_NOT_FOUND, null);
        }

        public static ApplyDeltaResult insufficientFunds() {
            return new ApplyDeltaResult(ApplyDeltaStatus.INSUFFICIENT_FUNDS, null);
        }
    }

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

    /**
     * Один SQL-стейтмент, который отличает:
     * - кошелёк не найден (404)
     * - недостаточно средств (409)
     * - успешное обновление (200)
     *
     * Работает атомарно: UPDATE делает row-level lock внутри Postgres.
     */
    public ApplyDeltaResult applyDelta(UUID walletId, BigDecimal delta) {
        String sql = """
            WITH wallet AS (
                SELECT 1 AS exists
                FROM wallets
                WHERE id = :id
            ),
            upd AS (
                UPDATE wallets
                SET balance = balance + :delta
                WHERE id = :id
                  AND balance + :delta >= 0
                RETURNING balance
            )
            SELECT
              (SELECT balance FROM upd)   AS balance,
              (SELECT exists  FROM wallet) AS exists
            """;

        var params = new MapSqlParameterSource()
                .addValue("id", walletId)
                .addValue("delta", delta);

        return jdbc.query(sql, params, rs -> {
            rs.next(); // SELECT без FROM всегда возвращает 1 строку

            BigDecimal balance = rs.getBigDecimal("balance");
            Object exists = rs.getObject("exists");

            if (exists == null) {
                return ApplyDeltaResult.walletNotFound();
            }
            if (balance == null) {
                return ApplyDeltaResult.insufficientFunds();
            }
            return ApplyDeltaResult.updated(balance);
        });
    }
}