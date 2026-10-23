package tuttibox.hw1.port;

public interface NotificationPort {
    void sendPickupCode(String recipientContact, String trackingCode, String pickupCode);
}
