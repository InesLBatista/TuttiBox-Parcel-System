package tuttibox.hw1.port;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class HardwareException extends RuntimeException {
    public HardwareException(String message) {
        super(message);
    }
}