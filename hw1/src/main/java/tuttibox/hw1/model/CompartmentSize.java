package tuttibox.hw1.model;

public enum CompartmentSize {
    S, M, L;

    public boolean canHold(CompartmentSize parcelSize) {
        return this.ordinal() >= parcelSize.ordinal();
    }

    public static CompartmentSize parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("tamanho de compartimento inválido: " + raw);
        }
    }
}
