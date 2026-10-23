package tuttibox.hw1.fake;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tuttibox.hw1.port.NotificationPort;

@Component
public class InMemoryNotificationFake implements NotificationPort {
    private static final Logger log = LoggerFactory.getLogger(InMemoryNotificationFake.class);
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    @Override
    public void sendPickupCode(String recipientContact, String trackingCode, String pickupCode) {
        Notification notification = new Notification(recipientContact, trackingCode, pickupCode);
        notifications.add(notification);
        log.info("código de recolha enviado: trackingCode={}, destinatário={}, código={}",
            trackingCode, recipientContact, pickupCode);
    }

    public List<Notification> getNotifications() {
        return List.copyOf(notifications);
    }

    public Optional<Notification> findLastByTrackingCode(String trackingCode) {
        return notifications.stream()
            .filter(notification -> notification.trackingCode().equals(trackingCode))
            .reduce((first, second) -> second);
    }

    public void clear() {
        notifications.clear();
    }

    public record Notification(String recipientContact, String trackingCode, String pickupCode) {
    }
}
