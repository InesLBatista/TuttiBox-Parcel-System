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
import tuttibox.hw1.exception.InvalidTransitionException;
import tuttibox.hw1.exception.NoAvailableCompartmentException;
import tuttibox.hw1.model.Compartment;
import tuttibox.hw1.model.CompartmentSize;
import tuttibox.hw1.model.CompartmentStatus;
import tuttibox.hw1.model.Parcel;
import tuttibox.hw1.model.ParcelState;
import tuttibox.hw1.model.ParcelTransition;
import tuttibox.hw1.port.HardwareException;
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
        ensureParcelCanBeDroppedOff(parcel);
        Compartment compartment = resolveAssignedCompartment(parcel, stationId, compartmentId);
        validateCompartmentSize(parcel, compartment);

        String assignedStation = compartment.getStation().getCode();
        hardwarePort.openCompartment(assignedStation, compartment.getCode());
        log.info("compartimento aberto para drop-off: trackingCode={}, estação={}, compartimento={}, courier={}",
            trackingCode, assignedStation, compartment.getCode(), courierId);

        return parcel;
    }

    @Transactional
    public Parcel confirmCourierDropOff(String trackingCode, String stationId, String compartmentId, String courierId) {
        Parcel parcel = findByTrackingCode(trackingCode);
        ensureParcelCanBeDroppedOff(parcel);
        Compartment compartment = resolveAssignedCompartment(parcel, stationId, compartmentId);
        validateCompartmentSize(parcel, compartment);

        String assignedStation = compartment.getStation().getCode();
        hardwarePort.confirmDoorClosed(assignedStation, compartment.getCode());
        if (!hardwarePort.isDoorClosed(assignedStation, compartment.getCode())) {
            throw new HardwareException("a porta do compartimento " + compartment.getCode() + " não foi confirmada como fechada");
        }

        compartment.occupy();
        parcel.markAsReadyForPickup(courierId);

        String pickupCode = PickupCode.generate();
        parcel.issuePickupCode(PickupCode.hash(pickupCode));

        Parcel saved = parcelRepository.save(parcel);
        compartmentRepository.save(compartment);
        notificationPort.sendPickupCode(
            saved.getRecepientContact(), saved.getTrackingCode(), pickupCode);

        log.info("fecho da porta confirmado, compartimento ocupado e código de recolha emitido: trackingCode={}, estação={}, compartimento={}, destinatário={}",
            saved.getTrackingCode(), assignedStation, compartment.getCode(), saved.getRecepientContact());

        return saved;
    }


    @Transactional
    public Parcel processRecipientPickUp(String pickupCode) {
        String normalizedCode = PickupCode.normalize(pickupCode);
        if (!PickupCode.isValidFormat(normalizedCode)) {
            throw invalidPickupCode();
        }

        String codeHash = PickupCode.hash(normalizedCode);
        Parcel parcel = parcelRepository.findByPickupCodeHash(codeHash)
            .orElseThrow(this::invalidPickupCode);
        if (!PickupCode.matches(normalizedCode, parcel.getPickupCodeHash())) {
            throw invalidPickupCode();
        }

        ensureParcelCanBePickedUp(parcel);
        Compartment compartment = resolveAssignedCompartment(parcel);
        String station = compartment.getStation().getCode();

        hardwarePort.openCompartment(station, compartment.getCode());
        parcel.pickUp("RECIPIENT");
        compartment.release();

        Parcel saved = parcelRepository.save(parcel);
        compartmentRepository.save(compartment);

        log.info("pickup concluído e compartimento libertado: trackingCode={}, estação={}, compartimento={}",
            saved.getTrackingCode(), station, compartment.getCode());

        return saved;
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

    @Transactional(readOnly = true)
    public Parcel findByTrackingCode(String trackingCode) { 
        return parcelRepository.findByTrackingCode(trackingCode) 
            .orElseThrow(() -> new EntityNotFoundException("encomenda não foi encontrada: " + trackingCode));
    }


    private void ensureParcelCanBeDroppedOff(Parcel parcel) {
        if (parcel.getCurrentState() != ParcelState.CREATED) {
            throw new InvalidTransitionException("não é possível fazer drop-off de uma encomenda em " + parcel.getCurrentState());
        }
    }

    private void ensureParcelCanBePickedUp(Parcel parcel) {
        if (parcel.getCurrentState() != ParcelState.READY_FOR_PICKUP) {
            throw new InvalidTransitionException("não é possível fazer pickup de uma encomenda em " + parcel.getCurrentState());
        }
    }

    private IllegalArgumentException invalidPickupCode() {
        return new IllegalArgumentException("código de recolha inválido");
    }

    private Compartment resolveAssignedCompartment(Parcel parcel) {
        Compartment assigned = parcel.getAssignedCompartment();
        if (assigned == null) {
            throw new IllegalStateException("encomenda " + parcel.getTrackingCode() + " não tem compartimento atribuído");
        }
        return assigned;
    }

    private Compartment resolveAssignedCompartment(Parcel parcel, String stationId, String compartmentId) {
        Compartment assigned = resolveAssignedCompartment(parcel);
        String assignedStation = assigned.getStation().getCode();
        if (!assignedStation.equals(stationId) || !assigned.getCode().equals(compartmentId)) {
            throw new IllegalArgumentException(
                "compartimento " + compartmentId + " da estação " + stationId
                    + " não corresponde ao compartimento atribuído " + assigned.getCode()
                    + " da estação " + assignedStation);
        }
        return assigned;
    }

    private void validateCompartmentSize(Parcel parcel, Compartment compartment) {
        String compartmentSize = compartment.getSize().name();
        String parcelSize = parcel.getCompartmentSize().toUpperCase();
        if (!isSizeCompatible(parcelSize, compartmentSize)) {
            throw new IllegalArgumentException("tamanho do compartimento " + compartmentSize + " é muito pequeno para o tamanho da encomenda " + parcelSize);
        }
    }

    private boolean isSizeCompatible(String parcelSize, String compartmentSize) {
        if (parcelSize.equals("S")) return true;
        if (parcelSize.equals("M")) return !compartmentSize.equals("S");
        if (parcelSize.equals("L")) return compartmentSize.equals("L");
        return false;
    }
}
