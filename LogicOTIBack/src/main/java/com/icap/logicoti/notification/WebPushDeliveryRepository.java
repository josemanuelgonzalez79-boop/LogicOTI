package com.icap.logicoti.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class WebPushDeliveryRepository {

    private static final String PENDING_STATUSES =
            "('QUEUED', 'RETRY_PENDING')";

    private static final String FAILED = "FAILED";


    private static final String TARGET_COLUMNS = """
            SELECT
                delivery.id AS delivery_id,
                subscription.id AS subscription_id,
                delivery.user_id,
                subscription.endpoint,
                subscription.p256dh,
                subscription.auth,
                delivery.title,
                delivery.body,
                delivery.notification_tag,
                delivery.target_url,
                delivery.require_interaction,
                delivery.attempt_count
            FROM web_push_delivery delivery
            INNER JOIN web_push_subscription subscription
                ON subscription.id = delivery.subscription_id
               AND subscription.active = TRUE
            INNER JOIN app_user app_user
                ON app_user.id = subscription.user_id
               AND app_user.active = TRUE
            """;

    private final JdbcTemplate jdbcTemplate;

    public WebPushDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String enqueue(
            List<WebPushSubscription> subscriptions,
            String title,
            String body,
            String tag,
            String targetUrl,
            boolean requireInteraction
    ) {
        String batchId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        subscriptions.forEach(subscription -> jdbcTemplate.update("""
                        INSERT INTO web_push_delivery (
                            batch_id,
                            subscription_id,
                            user_id,
                            title,
                            body,
                            notification_tag,
                            target_url,
                            require_interaction,
                            status,
                            attempt_count,
                            next_attempt_at,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, ?, ?, ?)
                        """,
                batchId,
                subscription.getId(),
                subscription.getUserId(),
                title,
                body,
                tag,
                targetUrl,
                requireInteraction,
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now)
        ));

        return batchId;
    }

    public List<WebPushDeliveryTarget> findBatch(
            String batchId
    ) {
        return jdbcTemplate.query(
                TARGET_COLUMNS
                        + " WHERE delivery.batch_id = ?"
                        + " AND delivery.status IN "
                        + PENDING_STATUSES
                        + " ORDER BY delivery.id",
                this::mapTarget,
                batchId
        );
    }

    public List<WebPushDeliveryTarget> findDue(
            Instant now,
            int limit
    ) {
        return jdbcTemplate.query(
                TARGET_COLUMNS
                        + " WHERE delivery.status IN "
                        + PENDING_STATUSES
                        + " AND delivery.next_attempt_at <= ?"
                        + " ORDER BY delivery.next_attempt_at, delivery.id"
                        + " LIMIT ?",
                this::mapTarget,
                toDatabaseTimestamp(now),
                limit
        );
    }

    public int markUndeliverableSubscriptions(Instant now) {
        return jdbcTemplate.update("""
                UPDATE web_push_delivery delivery
                SET status = 'FAILED',
                    last_error = ?,
                    next_attempt_at = NULL,
                    updated_at = ?,
                    completed_at = ?
                WHERE delivery.status IN ('QUEUED', 'RETRY_PENDING')
                  AND NOT EXISTS (
                        SELECT 1
                        FROM web_push_subscription subscription
                        INNER JOIN app_user app_user
                            ON app_user.id = subscription.user_id
                           AND app_user.active = TRUE
                        WHERE subscription.id = delivery.subscription_id
                          AND subscription.active = TRUE
                  )
                """,
                "La suscripción o el usuario ya no están activos.",
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now)
        );
    }

    public void recordAccepted(
            WebPushDeliveryTarget target,
            int httpStatus,
            Instant now
    ) {
        int attemptNumber = target.attemptCount() + 1;
        insertAttempt(
                target.deliveryId(),
                attemptNumber,
                "ACCEPTED",
                httpStatus,
                null,
                now
        );

        jdbcTemplate.update("""
                UPDATE web_push_delivery
                SET status = 'ACCEPTED',
                    attempt_count = ?,
                    last_http_status = ?,
                    last_error = NULL,
                    next_attempt_at = NULL,
                    updated_at = ?,
                    last_attempt_at = ?,
                    accepted_at = ?,
                    completed_at = ?
                WHERE id = ?
                """,
                attemptNumber,
                httpStatus,
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now),
                target.deliveryId()
        );
    }

    public void recordRetryableFailure(
            WebPushDeliveryTarget target,
            Integer httpStatus,
            String error,
            Instant nextAttemptAt,
            boolean exhausted,
            Instant now
    ) {
        int attemptNumber = target.attemptCount() + 1;
        String outcome = exhausted
                ? FAILED
                : "RETRY_SCHEDULED";
        String status = exhausted
                ? FAILED
                : "RETRY_PENDING";

        insertAttempt(
                target.deliveryId(),
                attemptNumber,
                outcome,
                httpStatus,
                error,
                now
        );

        jdbcTemplate.update("""
                UPDATE web_push_delivery
                SET status = ?,
                    attempt_count = ?,
                    last_http_status = ?,
                    last_error = ?,
                    next_attempt_at = ?,
                    updated_at = ?,
                    last_attempt_at = ?,
                    completed_at = ?
                WHERE id = ?
                """,
                status,
                attemptNumber,
                httpStatus,
                error,
                toDatabaseTimestamp(
                        exhausted ? null : nextAttemptAt
                ),
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(exhausted ? now : null),
                target.deliveryId()
        );
    }

    public void recordPermanentFailure(
            WebPushDeliveryTarget target,
            Integer httpStatus,
            String error,
            boolean expiredSubscription,
            Instant now
    ) {
        int attemptNumber = target.attemptCount() + 1;
        String outcome = expiredSubscription
                ? "EXPIRED_SUBSCRIPTION"
                : FAILED;

        insertAttempt(
                target.deliveryId(),
                attemptNumber,
                outcome,
                httpStatus,
                error,
                now
        );

        jdbcTemplate.update("""
                UPDATE web_push_delivery
                SET status = ?,
                    attempt_count = ?,
                    last_http_status = ?,
                    last_error = ?,
                    next_attempt_at = NULL,
                    updated_at = ?,
                    last_attempt_at = ?,
                    completed_at = ?
                WHERE id = ?
                """,
                outcome,
                attemptNumber,
                httpStatus,
                error,
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now),
                toDatabaseTimestamp(now),
                target.deliveryId()
        );
    }

    public long countBatchByStatus(
            String batchId,
            String status
    ) {
        Long value = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM web_push_delivery
                WHERE batch_id = ?
                  AND status = ?
                """,
                Long.class,
                batchId,
                status
        );

        return value == null ? 0 : value;
    }

    public WebPushDeliveryPageResponse findRecent(
            int limit,
            int offset
    ) {
        List<WebPushDeliveryResponse> items = jdbcTemplate.query("""
                        SELECT
                            id,
                            batch_id,
                            subscription_id,
                            user_id,
                            title,
                            notification_tag,
                            status,
                            attempt_count,
                            last_http_status,
                            last_error,
                            next_attempt_at,
                            created_at,
                            last_attempt_at,
                            accepted_at,
                            completed_at
                        FROM web_push_delivery
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """,
                this::mapResponse,
                limit,
                offset
        );

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM web_push_delivery",
                Long.class
        );

        return new WebPushDeliveryPageResponse(
                items,
                total == null ? 0 : total,
                Instant.now()
        );
    }

    private void insertAttempt(
            Long deliveryId,
            int attemptNumber,
            String outcome,
            Integer httpStatus,
            String error,
            Instant now
    ) {
        jdbcTemplate.update("""
                INSERT INTO web_push_delivery_attempt (
                    delivery_id,
                    attempt_number,
                    outcome,
                    http_status,
                    error_message,
                    attempted_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                deliveryId,
                attemptNumber,
                outcome,
                httpStatus,
                error,
                toDatabaseTimestamp(now)
        );
    }

    private WebPushDeliveryTarget mapTarget(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new WebPushDeliveryTarget(
                resultSet.getLong("delivery_id"),
                resultSet.getLong("subscription_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("endpoint"),
                resultSet.getString("p256dh"),
                resultSet.getString("auth"),
                resultSet.getString("title"),
                resultSet.getString("body"),
                resultSet.getString("notification_tag"),
                resultSet.getString("target_url"),
                resultSet.getBoolean("require_interaction"),
                resultSet.getInt("attempt_count")
        );
    }

    private WebPushDeliveryResponse mapResponse(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new WebPushDeliveryResponse(
                resultSet.getLong("id"),
                resultSet.getString("batch_id"),
                nullableLong(resultSet, "subscription_id"),
                nullableLong(resultSet, "user_id"),
                resultSet.getString("title"),
                resultSet.getString("notification_tag"),
                resultSet.getString("status"),
                resultSet.getInt("attempt_count"),
                nullableInteger(resultSet, "last_http_status"),
                resultSet.getString("last_error"),
                toInstant(resultSet.getTimestamp("next_attempt_at")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("last_attempt_at")),
                toInstant(resultSet.getTimestamp("accepted_at")),
                toInstant(resultSet.getTimestamp("completed_at"))
        );
    }

    private Long nullableLong(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInteger(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    static OffsetDateTime toDatabaseTimestamp(Instant instant) {
        return instant == null
                ? null
                : instant.atOffset(ZoneOffset.UTC);
    }
}
