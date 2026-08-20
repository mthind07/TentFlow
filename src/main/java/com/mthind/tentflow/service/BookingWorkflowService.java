package com.mthind.tentflow.service;

import com.mthind.tentflow.audit.AuditService;
import com.mthind.tentflow.model.MaintenanceBlock;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.WaitlistEntryView;
import com.mthind.tentflow.exception.ResourceNotFoundException;
import com.mthind.tentflow.persistence.repository.MaintenanceBlockRepository;
import com.mthind.tentflow.persistence.repository.ReservationRepository;
import com.mthind.tentflow.persistence.repository.TentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongFunction;

@Service
public class BookingWorkflowService {

    private static final String SYSTEM_ACTOR = "system:automation";

    private final BookingService bookingService;
    private final AuditService auditService;
    private final ReservationRepository reservationRepository;
    private final MaintenanceBlockRepository maintenanceBlockRepository;
    private final TentTypeRepository tentTypeRepository;
    private final Clock clock;

    public BookingWorkflowService(
            BookingService bookingService,
            AuditService auditService,
            ReservationRepository reservationRepository,
            MaintenanceBlockRepository maintenanceBlockRepository,
            TentTypeRepository tentTypeRepository,
            Clock clock
    ) {
        this.bookingService = bookingService;
        this.auditService = auditService;
        this.reservationRepository = reservationRepository;
        this.maintenanceBlockRepository = maintenanceBlockRepository;
        this.tentTypeRepository = tentTypeRepository;
        this.clock = clock;
    }

    @Transactional
    public Customer registerCustomer(
            String fullName,
            String email,
            String phone
    ) {
        Customer customer = bookingService.registerCustomer(
                fullName,
                email,
                phone
        );
        auditService.success(
                "CREATE_CUSTOMER",
                "CUSTOMER",
                customer.getId(),
                null
        );
        return customer;
    }

    @Transactional
    public TentType addTentType(
            int widthFeet,
            int lengthFeet,
            int totalQuantity
    ) {
        TentType tentType = bookingService.addTentType(
                widthFeet,
                lengthFeet,
                totalQuantity
        );
        auditService.success(
                "CREATE_TENT_TYPE",
                "TENT_TYPE",
                tentType.getId(),
                null
        );
        return tentType;
    }

    @Transactional
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
        ReservationView created = bookingService.requestReservation(
                customerId,
                tentTypeId,
                quantity,
                eventStart,
                eventEnd,
                reservedFrom,
                reservedUntil,
                location
        );
        auditService.lifecycleSuccess(
                "REQUEST_RESERVATION",
                created.id(),
                null,
                created.status(),
                null
        );
        return created;
    }

    @Transactional
    public ReservationView requestCustomerReservation(
            long customerId,
            long tentTypeId,
            int quantity,
            LocalDateTime eventStart,
            LocalDateTime eventEnd,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil,
            String location
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (reservedFrom == null || !reservedFrom.isAfter(now)) {
            throw new IllegalArgumentException(
                    "Setup time must be in the future."
            );
        }
        return requestReservation(
                customerId,
                tentTypeId,
                quantity,
                eventStart,
                eventEnd,
                reservedFrom,
                reservedUntil,
                location
        );
    }

    @Transactional
    public ReservationView confirmReservation(long reservationId) {
        return transition(
                reservationId,
                "CONFIRM_RESERVATION",
                bookingService::confirmReservation,
                false
        );
    }

    @Transactional
    public ReservationView rejectReservation(long reservationId) {
        return transition(
                reservationId,
                "REJECT_RESERVATION",
                bookingService::rejectReservation,
                false
        );
    }

    @Transactional
    public ReservationView cancelReservation(long reservationId) {
        return transition(
                reservationId,
                "CANCEL_RESERVATION",
                bookingService::cancelReservation,
                false
        );
    }

    @Transactional
    public ReservationView completeReservation(long reservationId) {
        return transition(
                reservationId,
                "COMPLETE_RESERVATION",
                bookingService::completeReservation,
                false
        );
    }

    @Transactional
    public boolean completeDueReservation(
            long reservationId,
            LocalDateTime now
    ) {
        lockReservationTent(reservationId);
        ReservationStatus status = reservationRepository
                .findStatusByReservationId(reservationId)
                .orElse(null);
        LocalDateTime reservedUntil = reservationRepository
                .findReservedUntilByReservationId(reservationId)
                .orElse(null);

        if (status != ReservationStatus.CONFIRMED
                || reservedUntil == null
                || reservedUntil.isAfter(now)) {
            return false;
        }

        transition(
                reservationId,
                "AUTO_COMPLETE_RESERVATION",
                bookingService::completeReservation,
                true
        );
        return true;
    }

    @Transactional
    public MaintenanceBlock addMaintenanceBlock(
            long tentTypeId,
            int quantityUnavailable,
            LocalDateTime from,
            LocalDateTime until,
            String reason
    ) {
        MaintenanceBlock block = bookingService.addMaintenanceBlock(
                tentTypeId,
                quantityUnavailable,
                from,
                until,
                reason
        );
        auditService.success(
                "CREATE_MAINTENANCE_BLOCK",
                "MAINTENANCE_BLOCK",
                block.getId(),
                null
        );
        return block;
    }

    @Transactional
    public void removeMaintenanceBlock(long maintenanceBlockId) {
        long tentTypeId = maintenanceBlockRepository
                .findTentTypeIdByMaintenanceBlockId(maintenanceBlockId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Maintenance block "
                                + maintenanceBlockId
                                + " was not found."
                ));
        lockTent(tentTypeId);
        MaintenanceBlock block = bookingService
                .getMaintenanceBlock(maintenanceBlockId);
        List<Long> waitingBefore = waitlistedIds(
                block.getTentType().getId()
        );
        bookingService.removeMaintenanceBlock(maintenanceBlockId);
        auditService.success(
                "REMOVE_MAINTENANCE_BLOCK",
                "MAINTENANCE_BLOCK",
                maintenanceBlockId,
                null
        );
        auditPromotions(waitingBefore, false);
    }

    private ReservationView transition(
            long reservationId,
            String action,
            LongFunction<ReservationView> transition,
            boolean systemActor
    ) {
        lockReservationTent(reservationId);
        ReservationView before = bookingService.getReservation(reservationId);
        List<Long> waitingBefore = waitlistedIds(before.tentType().getId());
        ReservationView after = transition.apply(reservationId);

        if (systemActor) {
            auditService.lifecycleSuccessAs(
                    SYSTEM_ACTOR,
                    action,
                    reservationId,
                    before.status(),
                    after.status(),
                    null
            );
        } else {
            auditService.lifecycleSuccess(
                    action,
                    reservationId,
                    before.status(),
                    after.status(),
                    null
            );
        }

        auditPromotions(waitingBefore, systemActor);
        return after;
    }

    private void lockReservationTent(long reservationId) {
        long tentTypeId = reservationRepository
                .findTentTypeIdByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation " + reservationId + " was not found."
                ));
        lockTent(tentTypeId);
    }

    private void lockTent(long tentTypeId) {
        tentTypeRepository.findByIdForUpdate(tentTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tent type " + tentTypeId + " was not found."
                ));
    }

    private List<Long> waitlistedIds(long tentTypeId) {
        return bookingService.getWaitlistForTent(tentTypeId)
                .stream()
                .map(WaitlistEntryView::reservation)
                .map(ReservationView::id)
                .toList();
    }

    private void auditPromotions(
            List<Long> waitingBefore,
            boolean systemActor
    ) {
        for (long reservationId : waitingBefore) {
            ReservationView after = bookingService.getReservation(
                    reservationId
            );
            if (after.status() != ReservationStatus.PENDING) {
                continue;
            }

            if (systemActor) {
                auditService.lifecycleSuccessAs(
                        SYSTEM_ACTOR,
                        "PROMOTE_WAITLIST",
                        reservationId,
                        ReservationStatus.WAITLISTED,
                        ReservationStatus.PENDING,
                        null
                );
            } else {
                auditService.lifecycleSuccess(
                        "PROMOTE_WAITLIST",
                        reservationId,
                        ReservationStatus.WAITLISTED,
                        ReservationStatus.PENDING,
                        null
                );
            }
        }
    }
}