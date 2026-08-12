package com.icap.logicoti.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository
        extends JpaRepository<WebPushSubscription, Long> {

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    Optional<WebPushSubscription> findByEndpointAndUserId(
            String endpoint,
            Long userId
    );

    @Query("""
            SELECT subscription
            FROM WebPushSubscription subscription
            WHERE subscription.active = TRUE
              AND EXISTS (
                    SELECT user.id
                    FROM AppUser user
                    WHERE user.id = subscription.userId
                      AND user.active = TRUE
              )
            """)
    List<WebPushSubscription> findActiveForEnabledUsers();

    List<WebPushSubscription> findByUserIdAndActiveTrue(Long userId);

    long countByUserIdAndActiveTrue(Long userId);
}
