package tuttibox.hw1.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import tuttibox.hw1.config.DeadlineProperties;
import tuttibox.hw1.dto.CompartmentResponseDto;
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
    private final Clock clock;
    private final DeadlineProperties deadlineProperties;

    public ParcelService(
            HardwarePort hardwarePort,
            ParcelRepository parcelRepository,
            StationRepository stationRepository,
            CompartmentRepository compartmentRepository,
            NotificationPort notificationPort,
            Clock clock,
            DeadlineProperties deadlineProperties) {
        this.hardwarePort=hardwarePort;
        this.parcelRepository=parcelRepository;
        this.stationRepository=stationRepository; 
        this.compartmentRepository=compartmentRepository; 
        this.notificationPort=notificationPort;
        this.clock=clock;
        this.deadlineProperties=deadlineProperties;
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
        LocalDateTime registeredAt = LocalDateTime.now(clock);
        String trackingCode = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Parcel parcel = new Parcel(trackingCode, request.getRecipientContact(), request.getCompartmentSize().toUpperCase());
        parcel.setPartnerId(request.getPartnerId());
        parcel.setStation(station);
        parcel.setAssignedCompartment(compartment);
        parcel.setRegisteredAt(registeredAt);
        parcel.setDropOffDeadline(registeredAt.plus(deadlineProperties.getDropOff()));

        Parcel saved = parcelRepository.save(parcel);

        log.info("encomenda registada: trackingCode={}, estação={}, compartimento={}, tamanho={}, partner={}, dropOffDeadline={}", 
            trackingCode, station.getCode(), compartment.getCode(),
            requestedSize, request.getPartnerId(), parcel.getDropOffDeadline());

        return ParcelResponseDto.from(saved);
    }


    @Transactional
    public Parcel processCourierDropOff(String trackingCode, String stationId, String compartmentId, String courierId) {
        Parcel parcel = findByTrackingCode(trackingCode);
        ensureParcelCanBeDroppedOff(parcel);
        ensureDropOffDeadlineNotExceeded(parcel);
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
        ensureDropOffDeadlineNotExceeded(parcel);
        Compartment compartment = resolveAssignedCompartment(parcel, stationId, compartmentId);
        validateCompartmentSize(parcel, compartment);

        String assignedStation = compartment.getStation().getCode();
        hardwarePort.confirmDoorClosed(assignedStation, compartment.getCode());
        if (!hardwarePort.isDoorClosed(assignedStation, compartment.getCode())) {
            throw new HardwareException("a porta do compartimento " + compartment.getCode() + " não foi confirmada como fechada");
        }

        compartment.occupy();
        LocalDateTime dropOffConfirmedAt = LocalDateTime.now(clock);
        parcel.markAsReadyForPickup(courierId, dropOffConfirmedAt.plus(deadlineProperties.getPickup()));

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
        ensurePickupDeadlineNotExceeded(parcel);
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
        requireActor(actor);
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
        requireActor(actor);
        Parcel parcel = findByTrackingCode(trackingCode);
        parcel.expire(actor);
        return parcelRepository.save(parcel);
    }

    @Transactional
    public Parcel processParcelReturn(String trackingCode, String actor) {
        requireActor(actor);
        Parcel parcel = findByTrackingCode(trackingCode);
        parcel.returnParcel(actor);
        releaseAssignedCompartment(parcel);
        return parcelRepository.save(parcel);
    }

    @Transactional
    public void applyDeadlines() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Parcel> dropOffExpired = parcelRepository
            .findByCurrentStateAndDropOffDeadlineBefore(ParcelState.CREATED, now);
        for (Parcel parcel : dropOffExpired) {
            parcel.cancel("SYSTEM");
            releaseAssignedCompartment(parcel);
            parcelRepository.save(parcel);
            log.info("prazo de drop-off expirado, encomenda cancelada: trackingCode={}, deadline={}",
                parcel.getTrackingCode(), parcel.getDropOffDeadline());
        }

        List<Parcel> pickupExpired = parcelRepository
            .findByCurrentStateAndPickupDeadlineBefore(ParcelState.READY_FOR_PICKUP, now);
        for (Parcel parcel : pickupExpired) {
            parcel.expire("SYSTEM");
            parcelRepository.save(parcel);
            log.info("prazo de recolha expirado: trackingCode={}, deadline={}",
                parcel.getTrackingCode(), parcel.getPickupDeadline());
        }

        if (!dropOffExpired.isEmpty() || !pickupExpired.isEmpty()) {
            log.info("aplicação de deadlines concluída: canceladas={}, expiradas={}",
                dropOffExpired.size(), pickupExpired.size());
        }
    }

    @Transactional(readOnly = true)
    public List<ParcelResponseDto> listParcels(String stationCode, ParcelState state) {
        List<Parcel> parcels;
        if (stationCode != null && !stationCode.isBlank()) {
            stationRepository.findByCode(stationCode)
                .orElseThrow(() -> new EntityNotFoundException("estação não encontrada: " + stationCode));
            parcels = state == null
                ? parcelRepository.findByStation_CodeOrderByIdAsc(stationCode)
                : parcelRepository.findByStation_CodeAndCurrentStateOrderByIdAsc(stationCode, state);
        } else {
            parcels = state == null
                ? parcelRepository.findAllByOrderByIdAsc()
                : parcelRepository.findByCurrentStateOrderByIdAsc(state);
        }

        log.debug("operação de supervisão: listagem de encomendas estação={}, estado={}, total={}",
            stationCode, state, parcels.size());

        return parcels.stream().map(ParcelResponseDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ParcelResponseDto> listExpiredParcels(String stationCode) {
        return listParcels(stationCode, ParcelState.EXPIRED);
    }

    @Transactional
    public ParcelResponseDto removeExpiredParcel(String trackingCode, String actor) {
        requireActor(actor);
        Parcel parcel = findByTrackingCode(trackingCode);
        parcel.returnParcel(actor);
        Compartment compartment = releaseAssignedCompartment(parcel);
        Parcel saved = parcelRepository.save(parcel);

        log.info("operação de supervisão: encomenda removida trackingCode={}, compartimento={}, ator={}",
            saved.getTrackingCode(),
            compartment != null ? compartment.getCode() : null,
            actor);

        return ParcelResponseDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CompartmentResponseDto> listCompartments(String stationCode) {
        stationRepository.findByCode(stationCode)
            .orElseThrow(() -> new EntityNotFoundException("estação não encontrada: " + stationCode));

        return compartmentRepository.findByStation_CodeOrderByCodeAsc(stationCode).stream()
            .map(CompartmentResponseDto::from)
            .toList();
    }

    @Transactional
    public CompartmentResponseDto putCompartmentOutOfService(
            String stationCode, String compartmentCode, String actor) {
        requireActor(actor);
        Compartment compartment = findCompartmentForUpdate(stationCode, compartmentCode);
        compartment.putOutOfService();
        Compartment saved = compartmentRepository.save(compartment);

        log.info("operação de supervisão: compartimento fora de serviço estação={}, compartimento={}, ator={}",
            stationCode, compartmentCode, actor);

        return CompartmentResponseDto.from(saved);
    }

    @Transactional
    public CompartmentResponseDto putCompartmentInService(
            String stationCode, String compartmentCode, String actor) {
        requireActor(actor);
        Compartment compartment = findCompartmentForUpdate(stationCode, compartmentCode);
        compartment.putInService();
        Compartment saved = compartmentRepository.save(compartment);

        log.info("operação de supervisão: compartimento de volta ao serviço estação={}, compartimento={}, ator={}",
            stationCode, compartmentCode, actor);

        return CompartmentResponseDto.from(saved);
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

    private void ensureDropOffDeadlineNotExceeded(Parcel parcel) {
        if (parcel.isDropOffDeadlineExceeded(LocalDateTime.now(clock))) {
            throw new InvalidTransitionException(
                "o prazo de drop-off expirou em " + parcel.getDropOffDeadline()
                    + "; a encomenda será cancelada pelo sistema");
        }
    }

    private void ensurePickupDeadlineNotExceeded(Parcel parcel) {
        if (parcel.isPickupDeadlineExceeded(LocalDateTime.now(clock))) {
            throw new InvalidTransitionException(
                "o prazo de recolha expirou em " + parcel.getPickupDeadline()
                    + "; a encomenda será expirada pelo sistema");
        }
    }

    private IllegalArgumentException invalidPickupCode() {
        return new IllegalArgumentException("código de recolha inválido");
    }

    private Compartment releaseAssignedCompartment(Parcel parcel) {
        Compartment compartment = parcel.getAssignedCompartment();
        if (compartment == null) {
            return null;
        }
        compartment.release();
        compartmentRepository.save(compartment);
        log.info("compartimento libertado: trackingCode={}, compartimento={}",
            parcel.getTrackingCode(), compartment.getCode());
        return compartment;
    }

    private Compartment findCompartmentForUpdate(String stationCode, String compartmentCode) {
        return compartmentRepository.findByStationCodeAndCodeForUpdate(stationCode, compartmentCode)
            .orElseThrow(() -> new EntityNotFoundException(
                "compartimento não encontrado: " + compartmentCode + " na estação " + stationCode));
    }

    private void requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("o ator é obrigatório");
        }
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
