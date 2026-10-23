package tuttibox.hw1.port;

public class GeocodingException extends RuntimeException {
    public enum Reason {
        UNAVAILABLE,
        TIMEOUT,
        RATE_LIMITED,
        INVALID_RESPONSE,
        PROVIDER_ERROR
    }

    private final Reason reason;

    public GeocodingException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GeocodingException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
