package tuttibox.hw1.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "compartments", uniqueConstraints = @UniqueConstraint(columnNames = {"station_id", "code"}))
@Data 
@NoArgsConstructor
public class Compartment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;          

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id")
    private Station station;

    @Enumerated(EnumType.STRING)
    private CompartmentSize size;

    @Enumerated(EnumType.STRING)
    private CompartmentStatus status = CompartmentStatus.FREE;

    public Compartment(String code, Station station, CompartmentSize size) {
        this.code = code;
        this.station = station;
        this.size = size;
        this.status = CompartmentStatus.FREE;
    }

    public void reserve() {
        if (status != CompartmentStatus.FREE) {
            throw new IllegalStateException("compartimento " + code + " não está disponível (" + status + ")");
        }
        status = CompartmentStatus.RESERVED;
    }

    public void occupy() {
        if (status != CompartmentStatus.RESERVED) {
            throw new IllegalStateException("compartimento " + code + " não está reservado (" + status + ")");
        }
        status = CompartmentStatus.OCCUPIED;
    }

    public void release() {
        if (status == CompartmentStatus.OUT_OF_SERVICE) return; 
        status = CompartmentStatus.FREE;
    }
}