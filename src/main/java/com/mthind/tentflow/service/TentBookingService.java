package com.mthind.tentflow.service;

import com.mthind.tentflow.exception.InsufficientAvailabilityException;
import com.mthind.tentflow.exception.ResourceNotFoundException;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.MaintenanceBlock;
import com.mthind.tentflow.model.Reservation;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.model.TimeRange;
import com.mthind.tentflow.model.WaitlistEntry;
import com.mthind.tentflow.model.WaitlistEntryView;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.TreeMap;

//coordinates TentFlow's Milestone 1 booking operations
//this service stores everything in memory
public final class TentBookingService {

    private final Map<Long, Customer> customers =
            new LinkedHashMap<>();

    private final Map<Long, TentType> tentTypes =
            new LinkedHashMap<>();

    private final Map<Long, Reservation> reservations =
            new LinkedHashMap<>();

    private final Map<Long, MaintenanceBlock> maintenanceBlocks =
            new LinkedHashMap<>();

    private final Map<Long, PriorityQueue<WaitlistEntry>>
            waitlistsByTentType = new LinkedHashMap<>();

    private final Clock clock;

    private long nextCustomerId = 1;
    private long nextTentTypeId = 1;
    private long nextReservationId = 1;
    private long nextMaintenanceBlockId = 1;
    private long nextWaitlistEntryId = 1;

    public TentBookingService() {
        this(Clock.systemDefaultZone());
    }

    public TentBookingService(Clock clock) {
        this.clock = Objects.requireNonNull(
                clock,
                "Clock cannot be null."
        );
    }

    public Customer registerCustomer(
            String fullName,
            String email,
            String phone
    ) {
        Customer customer = new Customer(
                nextCustomerId,
                fullName,
                email,
                phone
        );

        nextCustomerId++;

        customers.put(
                customer.getId(),
                customer
        );

        return customer;
    }

    public TentType addTentType(
            int widthFeet,
            int lengthFeet,
            int totalQuantity
    ) {
        boolean sizeAlreadyExists =
                tentTypes.values()
                        .stream()
                        .anyMatch(type ->
                                type.getWidthFeet() == widthFeet
                                        && type.getLengthFeet()
                                        == lengthFeet
                        );

        if (sizeAlreadyExists) {
            throw new IllegalArgumentException(
                    "Tent size "
                            + widthFeet
                            + "x"
                            + lengthFeet
                            + " already exists."
            );
        }

        TentType tentType = new TentType(
                nextTentTypeId,
                widthFeet,
                lengthFeet,
                totalQuantity
        );

        nextTentTypeId++;

        tentTypes.put(
                tentType.getId(),
                tentType
        );

        return tentType;
    }

    //creates a pending reservation when inventory fits
    //Otherwise, creates a waitlisted reservation
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
        Customer customer =
                requireCustomer(customerId);

        TentType tentType =
                requireTentType(tentTypeId);

        TimeRange eventTime = new TimeRange(
                eventStart,
                eventEnd
        );

        TimeRange reservedTime = new TimeRange(
                reservedFrom,
                reservedUntil
        );

        if (quantity <= 0
                || quantity > tentType.getTotalQuantity()) {

            throw new IllegalArgumentException(
                    "Requested quantity must be between 1 and "
                            + tentType.getTotalQuantity()
                            + "."
            );
        }

        int availableQuantity =
                getAvailableQuantity(
                        tentType,
                        reservedTime
                );

        ReservationStatus startingStatus;

        if (availableQuantity >= quantity) {
            startingStatus = ReservationStatus.PENDING;
        } else {
            startingStatus = ReservationStatus.WAITLISTED;
        }

        Reservation reservation = new Reservation(
                nextReservationId,
                customer,
                tentType,
                quantity,
                eventTime,
                reservedTime,
                location,
                startingStatus
        );

        nextReservationId++;

        reservations.put(
                reservation.getId(),
                reservation
        );

        if (startingStatus == ReservationStatus.WAITLISTED) {
            WaitlistEntry entry = new WaitlistEntry(
                    nextWaitlistEntryId,
                    reservation,
                    LocalDateTime.now(clock)
            );

            nextWaitlistEntryId++;

            waitlistsByTentType
                    .computeIfAbsent(
                            tentTypeId,
                            ignored -> new PriorityQueue<>()
                    )
                    .add(entry);
        }

        return toView(reservation);
    }

    public int getAvailableQuantity(
            long tentTypeId,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil
    ) {
        TentType tentType =
                requireTentType(tentTypeId);

        TimeRange requestedRange =
                new TimeRange(
                        reservedFrom,
                        reservedUntil
                );

        return getAvailableQuantity(
                tentType,
                requestedRange
        );
    }

    //computes peak simultaneous usage during the requested range
    private int getAvailableQuantity(
            TentType tentType,
            TimeRange requestedRange
    ) {
        TreeMap<LocalDateTime, Integer>
                inventoryChanges = new TreeMap<>();

        for (Reservation reservation
                : reservations.values()) {

            boolean sameTentType =
                    reservation
                            .getTentType()
                            .getId()
                            == tentType.getId();

            if (sameTentType
                    && reservation.blocksInventory()) {

                addUsageToTimeline(
                        inventoryChanges,
                        requestedRange,
                        reservation.getReservedTime(),
                        reservation.getQuantity()
                );
            }
        }

        for (MaintenanceBlock block
                : maintenanceBlocks.values()) {

            boolean sameTentType =
                    block
                            .getTentType()
                            .getId()
                            == tentType.getId();

            if (sameTentType) {
                addUsageToTimeline(
                        inventoryChanges,
                        requestedRange,
                        block.getTimeRange(),
                        block.getQuantityUnavailable()
                );
            }
        }

        int currentlyUnavailable = 0;
        int peakUnavailable = 0;

        for (int quantityChange
                : inventoryChanges.values()) {

            currentlyUnavailable += quantityChange;

            peakUnavailable = Math.max(
                    peakUnavailable,
                    currentlyUnavailable
            );
        }

        int available =
                tentType.getTotalQuantity()
                        - peakUnavailable;

        return Math.max(0, available);
    }

    private void addUsageToTimeline(
            TreeMap<LocalDateTime, Integer> inventoryChanges,
            TimeRange requestedRange,
            TimeRange usedRange,
            int quantity
    ) {
        if (!requestedRange.overlaps(usedRange)) {
            return;
        }

        LocalDateTime overlapStart = laterOf(
                requestedRange.getStart(),
                usedRange.getStart()
        );

        LocalDateTime overlapEnd = earlierOf(
                requestedRange.getEnd(),
                usedRange.getEnd()
        );

        inventoryChanges.merge(
                overlapStart,
                quantity,
                Integer::sum
        );

        inventoryChanges.merge(
                overlapEnd,
                -quantity,
                Integer::sum
        );
    }

    private LocalDateTime laterOf(
            LocalDateTime first,
            LocalDateTime second
    ) {
        if (first.isAfter(second)) {
            return first;
        }

        return second;
    }

    private LocalDateTime earlierOf(
            LocalDateTime first,
            LocalDateTime second
    ) {
        if (first.isBefore(second)) {
            return first;
        }

        return second;
    }

    public void confirmReservation(long reservationId) {
        Reservation reservation =
                requireReservation(reservationId);

        reservation.confirm();
    }

    public void rejectReservation(long reservationId) {
        Reservation reservation =
                requireReservation(reservationId);

        boolean releasedInventory =
                reservation.blocksInventory();

        if (reservation.getStatus()
                == ReservationStatus.WAITLISTED) {

            removeWaitlistEntry(reservation);
        }

        reservation.reject();

        if (releasedInventory) {
            promoteEligibleWaitlistEntries(
                    reservation
                            .getTentType()
                            .getId()
            );
        }
    }

    public void cancelReservation(long reservationId) {
        Reservation reservation =
                requireReservation(reservationId);

        boolean releasedInventory =
                reservation.blocksInventory();

        if (reservation.getStatus()
                == ReservationStatus.WAITLISTED) {

            removeWaitlistEntry(reservation);
        }

        reservation.cancel();

        if (releasedInventory) {
            promoteEligibleWaitlistEntries(
                    reservation
                            .getTentType()
                            .getId()
            );
        }
    }

    public void completeReservation(long reservationId) {
        Reservation reservation =
                requireReservation(reservationId);

        reservation.complete();

        promoteEligibleWaitlistEntries(
                reservation
                        .getTentType()
                        .getId()
        );
    }

    public MaintenanceBlock addMaintenanceBlock(
            long tentTypeId,
            int quantityUnavailable,
            LocalDateTime from,
            LocalDateTime until,
            String reason
    ) {
        TentType tentType =
                requireTentType(tentTypeId);

        TimeRange timeRange =
                new TimeRange(from, until);

        if (quantityUnavailable <= 0
                || quantityUnavailable
                > tentType.getTotalQuantity()) {

            throw new IllegalArgumentException(
                    "Maintenance quantity must be between 1 and "
                            + tentType.getTotalQuantity()
                            + "."
            );
        }

        int availableQuantity =
                getAvailableQuantity(
                        tentType,
                        timeRange
                );

        if (availableQuantity < quantityUnavailable) {
            throw new InsufficientAvailabilityException(
                    "Cannot block "
                            + quantityUnavailable
                            + " "
                            + tentType.getSizeLabel()
                            + " tent(s); only "
                            + availableQuantity
                            + " are free."
            );
        }

        MaintenanceBlock block =
                new MaintenanceBlock(
                        nextMaintenanceBlockId,
                        tentType,
                        quantityUnavailable,
                        timeRange,
                        reason
                );

        nextMaintenanceBlockId++;

        maintenanceBlocks.put(
                block.getId(),
                block
        );

        return block;
    }

    public void removeMaintenanceBlock(
            long maintenanceBlockId
    ) {
        MaintenanceBlock removed =
                maintenanceBlocks.remove(
                        maintenanceBlockId
                );

        if (removed == null) {
            throw new ResourceNotFoundException(
                    "Maintenance block "
                            + maintenanceBlockId
                            + " was not found."
            );
        }

        promoteEligibleWaitlistEntries(
                removed
                        .getTentType()
                        .getId()
        );
    }

    //promotes the oldest requests that currently fit
    //a larger older request may remain waitlisted while
    //a smaller later request is promoted
    private void promoteEligibleWaitlistEntries(
            long tentTypeId
    ) {
        PriorityQueue<WaitlistEntry> queue =
                waitlistsByTentType.get(tentTypeId);

        if (queue == null || queue.isEmpty()) {
            return;
        }

        List<WaitlistEntry> stillWaiting =
                new ArrayList<>();

        int entriesToCheck = queue.size();

        for (int index = 0;
             index < entriesToCheck;
             index++) {

            WaitlistEntry entry = queue.poll();

            if (entry == null) {
                break;
            }

            Reservation reservation =
                    entry.getReservation();

            if (reservation.getStatus()
                    != ReservationStatus.WAITLISTED) {

                continue;
            }

            int availableQuantity =
                    getAvailableQuantity(
                            reservation.getTentType(),
                            reservation.getReservedTime()
                    );

            if (availableQuantity
                    >= reservation.getQuantity()) {

                reservation.promoteFromWaitlist();
            } else {
                stillWaiting.add(entry);
            }
        }

        queue.addAll(stillWaiting);

        if (queue.isEmpty()) {
            waitlistsByTentType.remove(tentTypeId);
        }
    }

    private void removeWaitlistEntry(
            Reservation reservation
    ) {
        long tentTypeId =
                reservation
                        .getTentType()
                        .getId();

        PriorityQueue<WaitlistEntry> queue =
                waitlistsByTentType.get(tentTypeId);

        if (queue == null) {
            return;
        }

        queue.removeIf(entry ->
                entry
                        .getReservation()
                        .getId()
                        == reservation.getId()
        );

        if (queue.isEmpty()) {
            waitlistsByTentType.remove(tentTypeId);
        }
    }

    public Customer getCustomer(long customerId) {
        return requireCustomer(customerId);
    }

    public TentType getTentType(long tentTypeId) {
        return requireTentType(tentTypeId);
    }

    public ReservationView getReservation(
            long reservationId
    ) {
        return toView(requireReservation(reservationId));
    }

    public List<ReservationView> getAllReservations() {
        return reservations
                .values()
                .stream()
                .map(this::toView)
                .toList();
    }

    public List<WaitlistEntryView> getWaitlistForTent(
            long tentTypeId
    ) {
        requireTentType(tentTypeId);

        PriorityQueue<WaitlistEntry> queue =
                waitlistsByTentType.get(tentTypeId);

        if (queue == null) {
            return List.of();
        }

        return queue
                .stream()
                .sorted()
                .map(this::toView)
                .toList();
    }

    private ReservationView toView(
            Reservation reservation
    ) {
        return new ReservationView(
                reservation.getId(),
                reservation.getCustomer(),
                reservation.getTentType(),
                reservation.getQuantity(),
                reservation.getEventTime(),
                reservation.getReservedTime(),
                reservation.getLocation(),
                reservation.getStatus()
        );
    }

    private WaitlistEntryView toView(
            WaitlistEntry entry
    ) {
        return new WaitlistEntryView(
                entry.getId(),
                toView(entry.getReservation()),
                entry.getJoinedAt()
        );
    }

    private Customer requireCustomer(long customerId) {
        Customer customer =
                customers.get(customerId);

        if (customer == null) {
            throw new ResourceNotFoundException(
                    "Customer "
                            + customerId
                            + " was not found."
            );
        }

        return customer;
    }

    private TentType requireTentType(long tentTypeId) {
        TentType tentType =
                tentTypes.get(tentTypeId);

        if (tentType == null) {
            throw new ResourceNotFoundException(
                    "Tent type "
                            + tentTypeId
                            + " was not found."
            );
        }

        return tentType;
    }

    private Reservation requireReservation(
            long reservationId
    ) {
        Reservation reservation =
                reservations.get(reservationId);

        if (reservation == null) {
            throw new ResourceNotFoundException(
                    "Reservation "
                            + reservationId
                            + " was not found."
            );
        }

        return reservation;
    }
}
