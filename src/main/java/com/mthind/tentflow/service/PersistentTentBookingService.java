package com.mthind.tentflow.service;

import com.mthind.tentflow.exception.DuplicateResourceException;
import com.mthind.tentflow.exception.InsufficientAvailabilityException;
import com.mthind.tentflow.exception.ResourceNotFoundException;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.MaintenanceBlock;
import com.mthind.tentflow.model.Reservation;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.model.TimeRange;
import com.mthind.tentflow.model.WaitlistEntryView;
import com.mthind.tentflow.persistence.entity.CustomerEntity;
import com.mthind.tentflow.persistence.entity.MaintenanceBlockEntity;
import com.mthind.tentflow.persistence.entity.ReservationEntity;
import com.mthind.tentflow.persistence.entity.TentTypeEntity;
import com.mthind.tentflow.persistence.entity.WaitlistEntryEntity;
import com.mthind.tentflow.persistence.repository.CustomerRepository;
import com.mthind.tentflow.persistence.repository.MaintenanceBlockRepository;
import com.mthind.tentflow.persistence.repository.ReservationRepository;
import com.mthind.tentflow.persistence.repository.TentTypeRepository;
import com.mthind.tentflow.persistence.repository.WaitlistEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

@Service
@Transactional
public class PersistentTentBookingService implements BookingService {

    private static final EnumSet<ReservationStatus> BLOCKING_STATUSES =
            EnumSet.of(
                    ReservationStatus.PENDING,
                    ReservationStatus.CONFIRMED
            );

    private final CustomerRepository customerRepository;
    private final TentTypeRepository tentTypeRepository;
    private final ReservationRepository reservationRepository;
    private final MaintenanceBlockRepository maintenanceBlockRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final BusinessTimeConverter businessTime;
    private final Clock clock;

    public PersistentTentBookingService(
            CustomerRepository customerRepository,
            TentTypeRepository tentTypeRepository,
            ReservationRepository reservationRepository,
            MaintenanceBlockRepository maintenanceBlockRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            BusinessTimeConverter businessTime,
            Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.tentTypeRepository = tentTypeRepository;
        this.reservationRepository = reservationRepository;
        this.maintenanceBlockRepository = maintenanceBlockRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.businessTime = businessTime;
        this.clock = clock;
    }

    @Override
    public Customer registerCustomer(
            String fullName,
            String email,
            String phone
    ) {
        Customer validated = new Customer(1, fullName, email, phone);

        if (customerRepository.existsByNormalizedEmail(validated.getEmail())) {
            throw duplicateEmail(validated.getEmail());
        }

        CustomerEntity entity = new CustomerEntity(
                validated.getFullName(),
                validated.getEmail(),
                validated.getPhone()
        );

        try {
            return toCustomer(customerRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw duplicateEmail(validated.getEmail());
            }
            throw exception;
        }
    }

    @Override
    public TentType addTentType(
            int widthFeet,
            int lengthFeet,
            int totalQuantity
    ) {
        TentType validated = new TentType(
                1,
                widthFeet,
                lengthFeet,
                totalQuantity
        );

        if (tentTypeRepository.existsByWidthFeetAndLengthFeet(
                widthFeet,
                lengthFeet
        )) {
            throw duplicateTentSize(widthFeet, lengthFeet);
        }

        TentTypeEntity entity = new TentTypeEntity(
                validated.getWidthFeet(),
                validated.getLengthFeet(),
                validated.getTotalQuantity()
        );

        try {
            return toTentType(tentTypeRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw duplicateTentSize(widthFeet, lengthFeet);
            }
            throw exception;
        }
    }

    @Override
    public ReservationView requestReservation(
            long customerId,
            long tentTypeId,
            int quantity,
            LocalDateTime eventStart,
            LocalDateTime eventEnd,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil,
            String location
    ) {
        CustomerEntity customer = requireCustomerEntity(customerId);
        TentTypeEntity tentType = requireTentTypeForUpdate(tentTypeId);

        validateBusinessTimes(
                eventStart,
                eventEnd,
                reservedFrom,
                reservedUntil
        );

        TimeRange eventTime = new TimeRange(eventStart, eventEnd);
        TimeRange reservedTime = new TimeRange(
                reservedFrom,
                reservedUntil
        );

        if (quantity <= 0 || quantity > tentType.getTotalQuantity()) {
            throw new IllegalArgumentException(
                    "Requested quantity must be between 1 and "
                            + tentType.getTotalQuantity()
                            + "."
            );
        }

        Reservation validated = new Reservation(
                1,
                toCustomer(customer),
                toTentType(tentType),
                quantity,
                eventTime,
                reservedTime,
                location,
                ReservationStatus.PENDING
        );

        int available = calculateAvailableQuantity(
                tentType,
                reservedTime
        );

        ReservationStatus startingStatus =
                available >= quantity
                        ? ReservationStatus.PENDING
                        : ReservationStatus.WAITLISTED;

        ReservationEntity entity = new ReservationEntity(
                customer,
                tentType,
                validated.getQuantity(),
                validated.getEventTime().getStart(),
                validated.getEventTime().getEnd(),
                validated.getReservedTime().getStart(),
                validated.getReservedTime().getEnd(),
                validated.getLocation(),
                startingStatus
        );

        reservationRepository.saveAndFlush(entity);

        if (startingStatus == ReservationStatus.WAITLISTED) {
            waitlistEntryRepository.saveAndFlush(
                    new WaitlistEntryEntity(
                            entity,
                            Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
                    )
            );
        }

        return toReservationView(entity);
    }

    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public int getAvailableQuantity(
            long tentTypeId,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil
    ) {
        TentTypeEntity tentType = requireTentTypeEntity(tentTypeId);
        businessTime.requireUnambiguous(reservedFrom);
        businessTime.requireUnambiguous(reservedUntil);

        return calculateAvailableQuantity(
                tentType,
                new TimeRange(reservedFrom, reservedUntil)
        );
    }

    @Override
    public ReservationView confirmReservation(long reservationId) {
        lockTentForReservation(reservationId);
        ReservationEntity reservation =
                requireReservationEntity(reservationId);
        reservation.confirm();
        return toReservationView(reservation);
    }

    @Override
    public ReservationView rejectReservation(long reservationId) {
        long tentTypeId = lockTentForReservation(reservationId).getId();
        ReservationEntity reservation =
                requireReservationEntity(reservationId);
        boolean releasedInventory = reservation.blocksInventory();

        if (reservation.getStatus() == ReservationStatus.WAITLISTED) {
            waitlistEntryRepository.deleteByReservationId(reservationId);
        }

        reservation.reject();

        if (releasedInventory) {
            promoteEligibleWaitlistEntries(tentTypeId);
        }

        return toReservationView(reservation);
    }

    @Override
    public ReservationView cancelReservation(long reservationId) {
        long tentTypeId = lockTentForReservation(reservationId).getId();
        ReservationEntity reservation =
                requireReservationEntity(reservationId);
        boolean releasedInventory = reservation.blocksInventory();

        if (reservation.getStatus() == ReservationStatus.WAITLISTED) {
            waitlistEntryRepository.deleteByReservationId(reservationId);
        }

        reservation.cancel();

        if (releasedInventory) {
            promoteEligibleWaitlistEntries(tentTypeId);
        }

        return toReservationView(reservation);
    }

    @Override
    public ReservationView completeReservation(long reservationId) {
        long tentTypeId = lockTentForReservation(reservationId).getId();
        ReservationEntity reservation =
                requireReservationEntity(reservationId);
        reservation.complete();
        promoteEligibleWaitlistEntries(tentTypeId);
        return toReservationView(reservation);
    }

    @Override
    public MaintenanceBlock addMaintenanceBlock(
            long tentTypeId,
            int quantityUnavailable,
            LocalDateTime from,
            LocalDateTime until,
            String reason
    ) {
        TentTypeEntity tentType = requireTentTypeForUpdate(tentTypeId);
        businessTime.requireUnambiguous(from);
        businessTime.requireUnambiguous(until);
        TimeRange range = new TimeRange(from, until);

        if (quantityUnavailable <= 0
                || quantityUnavailable > tentType.getTotalQuantity()) {
            throw new IllegalArgumentException(
                    "Maintenance quantity must be between 1 and "
                            + tentType.getTotalQuantity()
                            + "."
            );
        }

        MaintenanceBlock validated = new MaintenanceBlock(
                1,
                toTentType(tentType),
                quantityUnavailable,
                range,
                reason
        );

        int available = calculateAvailableQuantity(tentType, range);

        if (available < quantityUnavailable) {
            throw new InsufficientAvailabilityException(
                    "Cannot block "
                            + quantityUnavailable
                            + " "
                            + tentType.getSizeLabel()
                            + " tent(s); only "
                            + available
                            + " are free."
            );
        }

        MaintenanceBlockEntity entity = new MaintenanceBlockEntity(
                tentType,
                validated.getQuantityUnavailable(),
                validated.getTimeRange().getStart(),
                validated.getTimeRange().getEnd(),
                validated.getReason()
        );

        return toMaintenanceBlock(
                maintenanceBlockRepository.saveAndFlush(entity)
        );
    }

    @Override
    public void removeMaintenanceBlock(long maintenanceBlockId) {
        long tentTypeId = maintenanceBlockRepository
                .findTentTypeIdByMaintenanceBlockId(maintenanceBlockId)
                .orElseThrow(() -> maintenanceNotFound(maintenanceBlockId));

        requireTentTypeForUpdate(tentTypeId);

        MaintenanceBlockEntity block = maintenanceBlockRepository
                .findDetailedById(maintenanceBlockId)
                .orElseThrow(() -> maintenanceNotFound(maintenanceBlockId));

        maintenanceBlockRepository.delete(block);
        maintenanceBlockRepository.flush();
        promoteEligibleWaitlistEntries(tentTypeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Customer getCustomer(long customerId) {
        return toCustomer(requireCustomerEntity(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        return customerRepository.findAllByOrderByIdAsc()
                .stream()
                .map(this::toCustomer)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TentType getTentType(long tentTypeId) {
        return toTentType(requireTentTypeEntity(tentTypeId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TentType> getAllTentTypes() {
        return tentTypeRepository.findAllByOrderByIdAsc()
                .stream()
                .map(this::toTentType)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationView getReservation(long reservationId) {
        return toReservationView(requireReservationEntity(reservationId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationView> getAllReservations() {
        return reservationRepository.findAllByOrderByIdAsc()
                .stream()
                .map(this::toReservationView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceBlock> getAllMaintenanceBlocks() {
        return maintenanceBlockRepository.findAllByOrderByIdAsc()
                .stream()
                .map(this::toMaintenanceBlock)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceBlock getMaintenanceBlock(long maintenanceBlockId) {
        return toMaintenanceBlock(
                maintenanceBlockRepository
                        .findDetailedById(maintenanceBlockId)
                        .orElseThrow(() -> maintenanceNotFound(
                                maintenanceBlockId
                        ))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistEntryView> getWaitlistForTent(long tentTypeId) {
        requireTentTypeEntity(tentTypeId);
        return waitlistEntryRepository
                .findOrderedByTentTypeId(tentTypeId)
                .stream()
                .map(this::toWaitlistEntryView)
                .toList();
    }

    private int calculateAvailableQuantity(
            TentTypeEntity tentType,
            TimeRange requestedRange
    ) {
        TreeMap<LocalDateTime, Integer> changes = new TreeMap<>();

        reservationRepository.findOverlappingByStatus(
                requireId(tentType.getId()),
                BLOCKING_STATUSES,
                requestedRange.getStart(),
                requestedRange.getEnd()
        ).forEach(reservation -> addUsageToTimeline(
                changes,
                requestedRange,
                new TimeRange(
                        reservation.getReservedFrom(),
                        reservation.getReservedUntil()
                ),
                reservation.getQuantity()
        ));

        maintenanceBlockRepository.findOverlapping(
                requireId(tentType.getId()),
                requestedRange.getStart(),
                requestedRange.getEnd()
        ).forEach(block -> addUsageToTimeline(
                changes,
                requestedRange,
                new TimeRange(
                        block.getBlockedFrom(),
                        block.getBlockedUntil()
                ),
                block.getQuantityUnavailable()
        ));

        int currentlyUnavailable = 0;
        int peakUnavailable = 0;

        for (int change : changes.values()) {
            currentlyUnavailable += change;
            peakUnavailable = Math.max(
                    peakUnavailable,
                    currentlyUnavailable
            );
        }

        return Math.max(
                0,
                tentType.getTotalQuantity() - peakUnavailable
        );
    }

    private void addUsageToTimeline(
            TreeMap<LocalDateTime, Integer> changes,
            TimeRange requestedRange,
            TimeRange usedRange,
            int quantity
    ) {
        if (!requestedRange.overlaps(usedRange)) {
            return;
        }

        LocalDateTime overlapStart = requestedRange
                .getStart()
                .isAfter(usedRange.getStart())
                ? requestedRange.getStart()
                : usedRange.getStart();

        LocalDateTime overlapEnd = requestedRange
                .getEnd()
                .isBefore(usedRange.getEnd())
                ? requestedRange.getEnd()
                : usedRange.getEnd();

        changes.merge(overlapStart, quantity, Integer::sum);
        changes.merge(overlapEnd, -quantity, Integer::sum);
    }

    private void promoteEligibleWaitlistEntries(long tentTypeId) {
        List<WaitlistEntryEntity> entries =
                waitlistEntryRepository.findOrderedByTentTypeId(tentTypeId);

        for (WaitlistEntryEntity entry : entries) {
            ReservationEntity reservation = entry.getReservation();

            if (reservation.getStatus() != ReservationStatus.WAITLISTED) {
                waitlistEntryRepository.delete(entry);
                continue;
            }

            TimeRange requestedRange = new TimeRange(
                    reservation.getReservedFrom(),
                    reservation.getReservedUntil()
            );

            int available = calculateAvailableQuantity(
                    reservation.getTentType(),
                    requestedRange
            );

            if (available >= reservation.getQuantity()) {
                reservation.promoteFromWaitlist();
                waitlistEntryRepository.delete(entry);
            }
        }
    }

    private TentTypeEntity lockTentForReservation(long reservationId) {
        long tentTypeId = reservationRepository
                .findTentTypeIdByReservationId(reservationId)
                .orElseThrow(() -> reservationNotFound(reservationId));
        return requireTentTypeForUpdate(tentTypeId);
    }

    private CustomerEntity requireCustomerEntity(long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer " + customerId + " was not found."
                ));
    }

    private TentTypeEntity requireTentTypeEntity(long tentTypeId) {
        return tentTypeRepository.findById(tentTypeId)
                .orElseThrow(() -> tentNotFound(tentTypeId));
    }

    private TentTypeEntity requireTentTypeForUpdate(long tentTypeId) {
        return tentTypeRepository.findByIdForUpdate(tentTypeId)
                .orElseThrow(() -> tentNotFound(tentTypeId));
    }

    private ReservationEntity requireReservationEntity(long reservationId) {
        return reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> reservationNotFound(reservationId));
    }

    private void validateBusinessTimes(
            LocalDateTime eventStart,
            LocalDateTime eventEnd,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil
    ) {
        businessTime.requireUnambiguous(eventStart);
        businessTime.requireUnambiguous(eventEnd);
        businessTime.requireUnambiguous(reservedFrom);
        businessTime.requireUnambiguous(reservedUntil);
    }

    private Customer toCustomer(CustomerEntity entity) {
        return new Customer(
                requireId(entity.getId()),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPhone()
        );
    }

    private TentType toTentType(TentTypeEntity entity) {
        return new TentType(
                requireId(entity.getId()),
                entity.getWidthFeet(),
                entity.getLengthFeet(),
                entity.getTotalQuantity()
        );
    }

    private ReservationView toReservationView(ReservationEntity entity) {
        return new ReservationView(
                requireId(entity.getId()),
                toCustomer(entity.getCustomer()),
                toTentType(entity.getTentType()),
                entity.getQuantity(),
                new TimeRange(entity.getEventStart(), entity.getEventEnd()),
                new TimeRange(
                        entity.getReservedFrom(),
                        entity.getReservedUntil()
                ),
                entity.getLocation(),
                entity.getStatus()
        );
    }

    private MaintenanceBlock toMaintenanceBlock(
            MaintenanceBlockEntity entity
    ) {
        return new MaintenanceBlock(
                requireId(entity.getId()),
                toTentType(entity.getTentType()),
                entity.getQuantityUnavailable(),
                new TimeRange(
                        entity.getBlockedFrom(),
                        entity.getBlockedUntil()
                ),
                entity.getReason()
        );
    }

    private WaitlistEntryView toWaitlistEntryView(
            WaitlistEntryEntity entity
    ) {
        return new WaitlistEntryView(
                requireId(entity.getId()),
                toReservationView(entity.getReservation()),
                businessTime.toLocalDateTime(entity.getJoinedAt())
        );
    }

    private long requireId(Long id) {
        return Objects.requireNonNull(id, "Persisted ID cannot be null.");
    }

    private DuplicateResourceException duplicateEmail(String email) {
        return new DuplicateResourceException(
                "Customer email " + email + " already exists."
        );
    }

    private boolean isUniqueViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof SQLException sqlException
                && "23505".equals(sqlException.getSQLState());
    }

    private DuplicateResourceException duplicateTentSize(
            int widthFeet,
            int lengthFeet
    ) {
        return new DuplicateResourceException(
                "Tent size "
                        + widthFeet
                        + "x"
                        + lengthFeet
                        + " already exists."
        );
    }

    private ResourceNotFoundException tentNotFound(long tentTypeId) {
        return new ResourceNotFoundException(
                "Tent type " + tentTypeId + " was not found."
        );
    }

    private ResourceNotFoundException reservationNotFound(
            long reservationId
    ) {
        return new ResourceNotFoundException(
                "Reservation " + reservationId + " was not found."
        );
    }

    private ResourceNotFoundException maintenanceNotFound(
            long maintenanceBlockId
    ) {
        return new ResourceNotFoundException(
                "Maintenance block "
                        + maintenanceBlockId
                        + " was not found."
        );
    }
}
