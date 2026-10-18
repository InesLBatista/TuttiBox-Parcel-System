package tuttibox.hw1.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//faltava me este cenário
@ResponseStatus(HttpStatus.CONFLICT)
public class NoAvailableCompartmentException extends RuntimeException {
    public NoAvailableCompartmentException(String message) {
        super(message);
    }
}
