package tuttibox.hw1.service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import tuttibox.hw1.dto.ParcelRequestDto;
import tuttibox.hw1.dto.ParcelResponseDto;
import tuttibox.hw1.exception.NoAvailableCompartmentException;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelTransition;
import tuttibox.hw1.port.HardwarePort;
import tuttibox.hw1.port.NotificationPort;
import tuttibox.hw1.repository.CompartmentRepository;
import tuttibox.hw1.repository.ParcelRepository;
import tuttibox.hw1.repository.StationRepository;

@Service 
public class ParcelService {
    private static final Logger log = LoggerFactory.getLogger(ParcelService.class);
    private final HardwarePort hardwarePort;
    private final ParcelRepository parcelRepository;
    private final StationRepository stationRepository;
    private final CompartmentRepository compartmentRepository;
    private final NotificationPort notificationPort;

    public ParcelService(HardwarePort hardwarePort, ParcelRepository parcelRepository, StationRepository stationRepository, CompartmentRepository compartmentRepository, NotificationPort notificationPort) {
        this.hardwarePort=hardwarePort;
        this.parcelRepository=parcelRepository;
        this.stationRepository=stationRepository; 
        this.compartmentRepository=compartmentRepository; 
        this.notificationPort=notificationPort;
    }


    //registo da encomenda
    @Transactional
    public ParcelResponseDto registerParcel(ParcelRequestDto request) {
        var station = stationRepository.findByCode(request.getStationId())
            .orElseThrow(() -> new EntityNotFoundException(
                "estação não encontrada: " + request.getStationId()));

        CompartmentSize requestedSize = CompartmentSize.parse(request.getCompartmentSize());

        //tamanhos de compartimento compatíveis
        List<CompartmentSize> compatibleSizes = Arrays.stream(CompartmentSize.values())
            .filter(s -> s.canHold(requestedSize))
            .collect(Collectors.toList());

        
        List<Compartment> available = compartmentRepository.findAvailableByStationAndSizes(
            request.getStationId(), CompartmentStatus.FREE, compatibleSizes);

        if (available.isEmpty()) {
            throw new NoAvailableCompartmentException(
                "sem compartimentos disponíveis de tamanho >= " + requestedSize + " na estação " + request.getStationId());
        }

        //tentar sempre reservar o compartimento menor possível para guardar compartimentos maiores para encomendas de tamanho maior que esta
        Compartment compartment = available.get(0);
        compartment.reserve();
        compartmentRepository.save(compartment);

        //gerar o tracking code e criar encomenda
        String trackingCode = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Parcel parcel = new Parcel(trackingCode, request.getRecipientContact(), request.getCompartmentSize().toUpperCase());
        parcel.setPartnerId(request.getPartnerId());
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);

        Parcel saved = parcelRepository.save(parcel);

        log.info("encomenda registada: trackingCode={}, estação={}, compartimento={}, tamanho={}, partner={}", 
            trackingCode, station.getCode(), compartment.getCode(),
            requestedSize, request.getPartnerId());

        return ParcelResponseDto.from(saved);
    }


    @Transactional
    public Parcel processCourierDropOff(String trackingCode, String stationId, String compartmentId, String courierId) {
        Parcel parcel = findByTrackingCode(trackingCode);

        //inferir tamanho do compartimento pelo id
        String compIdUpper = compartmentId.toUpperCase();

        String compartmentSize;
        if (compIdUpper.contains("_S") || compIdUpper.endsWith("S") || compIdUpper.contains("SMALL")) {
            compartmentSize = "S";
        } else if (compIdUpper.contains("_M") || compIdUpper.endsWith("M") || compIdUpper.contains("MEDIUM")) {
            compartmentSize = "M";
        } else if (compIdUpper.contains("_L") || compIdUpper.endsWith("L") || compIdUpper.contains("LARGE")) {
            compartmentSize = "L";
        } else {
            throw new IllegalArgumentException(
                "compartimento não especifica um tamanho válido: " + compartmentId);
        }

        String parcelSize = parcel.getCompartmentSize().toUpperCase();
        if (!isSizeCompatible(parcelSize, compartmentSize)) {
            throw new IllegalArgumentException(
                "tamanho do compartimento " + compartmentSize + " é muito pequeno para o tamanho da encomenda " + parcelSize);
        }

        hardwarePort.openCompartment(stationId, compartmentId);
        parcel.markAsReadyForPickup(courierId);

        String pickupCode = PickupCode.generate();
        parcel.issuePickupCode(PickupCode.hash(pickupCode));

        Parcel saved = parcelRepository.save(parcel);
        notificationPort.sendPickupCode(
            saved.getRecepientContact(), saved.getTrackingCode(), pickupCode);

        log.info("código de recolha emitido: trackingCode={}, destinatário={}",
            saved.getTrackingCode(), saved.getRecepientContact());

        return saved;
    }


    @Transactional
    public Parcel processRecipientPickUp(String trackingCode, String stationId, String compartmentId, String recipientId) {
        Parcel parcel = findByTrackingCode(trackingCode);

        hardwarePort.openCompartment(stationId, compartmentId);
        parcel.pickUp(recipientId);
        return parcelRepository.save(parcel);
    }

    @Transactional(readOnly = true)
    public List<ParcelTransition> getParcelHistory(String trackingCode) {
        return findByTrackingCode(trackingCode).getTransitions();
    }

    @Transactional
    public Parcel processParcelCancellation(String trackingCode, String actor) {
        Parcel parcel = findByTrackingCode(trackingCode);
        parcel.cancel(actor);

        //libertar o compartimento reservado
        if (parcel.getAssignedCompartment() != null) {
            Compartment c = parcel.getAssignedCompartment();
            c.release();
            compartmentRepository.save(c);
            log.info("Compartimento {} será libertado após cancelamento de {}", c.getCode(), trackingCode);
        }

        return parcelRepository.save(parcel);
    }

    @Transactional
    public Parcel processParcelExpiration(String trackingCode, String actor) {
        Parcel parcel = findByTrackingCode(trackingCode);
        parcel.expire(actor);
        return parcelRepository.save(parcel);
    }

    @Transactional
    public Parcel processParcelReturn(String trackingCode, String actor) {
        Parcel parcel = findByTrackingCode(trackingCode);
        parcel.returnParcel(actor);
        return parcelRepository.save(parcel);
    }

    private Parcel findByTrackingCode(String trackingCode) { 
        return parcelRepository.findByTrackingCode(trackingCode) 
            .orElseThrow(() -> new EntityNotFoundException( 
                "encomenda não foi encontrada: " + trackingCode));
    }


    private boolean isSizeCompatible(String parcelSize, String compartmentSize) {
        if (parcelSize.equals("S")) return true;
        if (parcelSize.equals("M")) return !compartmentSize.equals("S");
        if (parcelSize.equals("L")) return compartmentSize.equals("L");
        return false;
    }
}
