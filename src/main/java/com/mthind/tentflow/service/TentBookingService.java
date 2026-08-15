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


public final class TentBookingService {

    //these maps act as temporary in-memory databases, the Long key is the object's ID
    private final Map<Long, Customer> customers =
            new LinkedHashMap<>();

    private final Map<Long, TentType> tentTypes =
            new LinkedHashMap<>();

    private final Map<Long, Reservation> reservations =
            new LinkedHashMap<>();

    private final Map<Long, MaintenanceBlock> maintenanceBlocks =
            new LinkedHashMap<>();

    //each tent type receives its own ordered waitlist
    private final Map<Long, PriorityQueue<WaitlistEntry>>
            waitlistsByTentType = new LinkedHashMap<>();

    //lock supplies the current time
    //tests can replace the real clock with a fixed one, making waitlist ordering predictable
    private final Clock clock;

    //these counters temporarily generate IDs
    //the database will generate IDs in a later milestone
    private long nextCustomerId = 1;
    private long nextTentTypeId = 1;
    private long nextReservationId = 1;
    private long nextMaintenanceBlockId = 1;
    private long nextWaitlistEntryId = 1;

    //cormal constructor used by the real application
    public TentBookingService() {
        this(Clock.systemDefaultZone());
    }

    //constructor used when a specific clock is supplied
    public TentBookingService(Clock clock) {
        this.clock = Objects.requireNonNull(
                clock,
                "Clock cannot be null."
        );
    }

    //creates and stores a customer
    public synchronized Customer registerCustomer(
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

    //adds a tent size and its physical inventory quantity
    public synchronized TentType addTentType(
            int widthFeet,
            int lengthFeet,
            int totalQuantity
    ) {
        //check whether the size already exists
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

    //creates either:
    //PENDING reservation when inventory fits or
    //WAITLISTED reservation when it doesn't fit
    public synchronized ReservationView requestReservation(
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

        //a waitlisted reservation must also receive an entry in the ordered waitlist
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

    //public availability method
    public synchronized int getAvailableQuantity(
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

    //calculates the smallest quantity available throughout the complete requested range
    //it uses a timeline of inventory changes
    private int getAvailableQuantity(
            TentType tentType,
            TimeRange requestedRange
    ) {
        //TreeMap automatically sorts its keys by date/time
        // Example: 08:00 -> +2     12:00 -> -2
        TreeMap<LocalDateTime, Integer>
                inventoryChanges = new TreeMap<>();

        //add active reservation usage
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

        //add maintenance usage
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

        //walk through the timeline in time order
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

        //availability should never display below zero
        return Math.max(0, available);
    }

    //adds a reservation or maintenance range to the inventory-change timeline
    private void addUsageToTimeline(
            TreeMap<LocalDateTime, Integer> inventoryChanges,
            TimeRange requestedRange,
            TimeRange usedRange,
            int quantity
    ) {
        //non-overlapping ranges have no effect
        if (!requestedRange.overlaps(usedRange)) {
            return;
        }

        //limit the usage to the part that overlaps the requested range
        LocalDateTime overlapStart = laterOf(
                requestedRange.getStart(),
                usedRange.getStart()
        );

        LocalDateTime overlapEnd = earlierOf(
                requestedRange.getEnd(),
                usedRange.getEnd()
        );

        //inventory becomes unavailable at the start
        inventoryChanges.merge(
                overlapStart,
                quantity,
                Integer::sum
        );

        //inventory becomes available again at the end
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

    //staff confirms a pending reservation
    public synchronized ReservationView confirmReservation(
            long reservationId
    ) {
        Reservation reservation =
                requireReservation(reservationId);

        reservation.confirm();

        return toView(reservation);
    }

    //staff rejects a pending or waitlisted reservation
    public synchronized ReservationView rejectReservation(
            long reservationId
    ) {
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

        return toView(reservation);
    }

    //cancels a reservation and rechecks the waitlist when inventory was released
    public synchronized ReservationView cancelReservation(
            long reservationId
    ) {
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

        return toView(reservation);
    }

    //completes a confirmed reservation and releases inventory
    public synchronized ReservationView completeReservation(
            long reservationId
    ) {
        Reservation reservation =
                requireReservation(reservationId);

        reservation.complete();

        promoteEligibleWaitlistEntries(
                reservation
                        .getTentType()
                        .getId()
        );

        return toView(reservation);
    }

    //temporarily removes tents from availability
    public synchronized MaintenanceBlock addMaintenanceBlock(
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

        //don't let maintenance consume inventory that is already promised to customers
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

    //removes a maintenance block and rechecks waiting reservations
    public synchronized void removeMaintenanceBlock(
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

    //rechecks waitlisted reservations in request order
    //a request that now fits becomes PENDING
    //a request that still does not fit remains waiting
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

            //ignore an entry if its reservation is no longer waitlisted
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

    //removes one reservation from its tent waitlist
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

    public synchronized Customer getCustomer(
            long customerId
    ) {
        return requireCustomer(customerId);
    }

    //returns a read-only customer snapshot for the REST API
    public synchronized List<Customer> getAllCustomers() {
        return List.copyOf(customers.values());
    }

    public synchronized TentType getTentType(
            long tentTypeId
    ) {
        return requireTentType(tentTypeId);
    }

    //returns a read-only tent snapshot for the REST API
    public synchronized List<TentType> getAllTentTypes() {
        return List.copyOf(tentTypes.values());
    }

    public synchronized ReservationView getReservation(
            long reservationId
    ) {
        return toView(
                requireReservation(reservationId)
        );
    }

    //returns a safe copy of the reservation list
    public synchronized List<ReservationView>
    getAllReservations() {
        return reservations
                .values()
                .stream()
                .map(this::toView)
                .toList();
    }

    //returns a read-only maintenance snapshot for the REST API
    public synchronized List<MaintenanceBlock>
    getAllMaintenanceBlocks() {
        return List.copyOf(
                maintenanceBlocks.values()
        );
    }

    public synchronized MaintenanceBlock getMaintenanceBlock(
            long maintenanceBlockId
    ) {
        MaintenanceBlock block =
                maintenanceBlocks.get(
                        maintenanceBlockId
                );

        if (block == null) {
            throw new ResourceNotFoundException(
                    "Maintenance block "
                            + maintenanceBlockId
                            + " was not found."
            );
        }

        return block;
    }

    //returns the waitlist in the correct order
    public synchronized List<WaitlistEntryView>
    getWaitlistForTent(
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

    //copies an internal mutable reservation into an immutable snapshot
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

    //copies an internal waitlist entry and its reservation into immutable snapshots
    private WaitlistEntryView toView(
            WaitlistEntry entry
    ) {
        return new WaitlistEntryView(
                entry.getId(),
                toView(entry.getReservation()),
                entry.getJoinedAt()
        );
    }

    //finds a customer or throws a meaningful error
    private Customer requireCustomer(
            long customerId
    ) {
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

    //finds a tent type or throws a meaningful error
    private TentType requireTentType(
            long tentTypeId
    ) {
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

    //finds a reservation or throws a meaningful error
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