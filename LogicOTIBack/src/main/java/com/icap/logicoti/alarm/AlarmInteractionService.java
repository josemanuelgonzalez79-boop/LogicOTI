package com.icap.logicoti.alarm;

import com.icap.logicoti.exception.ConflictException;
import com.icap.logicoti.exception.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Service
public class AlarmInteractionService {

    public static final String ATTENTION_TOPIC =
            "/topic/alerts/attention";

    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public AlarmInteractionService(
            JdbcTemplate jdbcTemplate,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public AlarmActivityResponse findActivity(long eventId) {
        ensureAlarmActivation(eventId);
        return findActivityInternal(eventId);
    }

    @Transactional
    public AlarmActivityResponse acknowledge(
            long eventId,
            AlarmAcknowledgeRequest request,
            String username
    ) {
        ensureAlarmActivation(eventId);

        try {
            jdbcTemplate.update("""
                    INSERT INTO alarm_acknowledgement (
                        event_id,
                        acknowledged_by
                    )
                    VALUES (?, ?)
                    """,
                    eventId,
                    username
            );
        } catch (DuplicateKeyException exception) {
            throw new ConflictException(
                    "Esta alarma ya fue reconocida."
            );
        }

        String comment = normalizeComment(request.comment());
        if (comment != null) {
            insertComment(eventId, comment, username);
        }

        AlarmActivityResponse response =
                findActivityInternal(eventId);

        publish(response);
        return response;
    }

    @Transactional
    public AlarmActivityResponse addComment(
            long eventId,
            AlarmCommentRequest request,
            String username
    ) {
        ensureAlarmActivation(eventId);
        insertComment(
                eventId,
                request.comment().trim(),
                username
        );

        AlarmActivityResponse response =
                findActivityInternal(eventId);

        publish(response);
        return response;
    }

    private void ensureAlarmActivation(long eventId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM device_event_history
                WHERE id = ?
                  AND device_type = 'SMOKE'
                  AND event_type = 'ACTIVATED'
                  AND current_state = TRUE
                """,
                Integer.class,
                eventId
        );

        if (count == null || count == 0) {
            throw new ResourceNotFoundException(
                    "No se encontró una activación de alarma de humo con id "
                            + eventId
                            + "."
            );
        }
    }

    private void insertComment(
            long eventId,
            String comment,
            String username
    ) {
        jdbcTemplate.update("""
                INSERT INTO alarm_comment (
                    event_id,
                    comment_text,
                    created_by
                )
                VALUES (?, ?, ?)
                """,
                eventId,
                comment,
                username
        );
    }

    private AlarmActivityResponse findActivityInternal(long eventId) {
        List<AlarmAcknowledgementResponse> acknowledgements =
                jdbcTemplate.query("""
                        SELECT
                            id,
                            event_id,
                            acknowledged_by,
                            acknowledged_at
                        FROM alarm_acknowledgement
                        WHERE event_id = ?
                        """,
                        this::mapAcknowledgement,
                        eventId
                );

        List<AlarmCommentResponse> comments =
                jdbcTemplate.query("""
                        SELECT
                            id,
                            event_id,
                            comment_text,
                            created_by,
                            created_at
                        FROM alarm_comment
                        WHERE event_id = ?
                        ORDER BY created_at ASC, id ASC
                        """,
                        this::mapComment,
                        eventId
                );

        return new AlarmActivityResponse(
                eventId,
                acknowledgements.isEmpty()
                        ? null
                        : acknowledgements.getFirst(),
                comments,
                comments.size(),
                Instant.now()
        );
    }

    private AlarmAcknowledgementResponse mapAcknowledgement(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new AlarmAcknowledgementResponse(
                resultSet.getLong("id"),
                resultSet.getLong("event_id"),
                resultSet.getString("acknowledged_by"),
                resultSet
                        .getTimestamp("acknowledged_at")
                        .toInstant()
        );
    }

    private AlarmCommentResponse mapComment(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new AlarmCommentResponse(
                resultSet.getLong("id"),
                resultSet.getLong("event_id"),
                resultSet.getString("comment_text"),
                resultSet.getString("created_by"),
                resultSet
                        .getTimestamp("created_at")
                        .toInstant()
        );
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }

        return comment.trim();
    }

    private void publish(AlarmActivityResponse response) {
        messagingTemplate.convertAndSend(
                ATTENTION_TOPIC,
                response
        );
    }
}
