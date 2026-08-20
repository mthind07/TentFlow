package com.mthind.tentflow.automation;

import com.mthind.tentflow.exception.InvalidReservationStateException;
import com.mthind.tentflow.exception.ResourceNotFoundException;
import com.mthind.tentflow.persistence.repository.ReservationRepository;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.service.BookingWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(
        name = "tentflow.automation.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationAutomationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ReservationAutomationJob.class
    );

    private final ReservationRepository reservationRepository;
    private final BookingWorkflowService workflowService;
    private final Clock clock;

    public ReservationAutomationJob(
            ReservationRepository reservationRepository,
            BookingWorkflowService workflowService,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.workflowService = workflowService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tentflow.automation.fixed-delay}")
    public void completeDueReservations() {
        LocalDateTime now = LocalDateTime.now(clock);

        for (long reservationId
                : reservationRepository.findDueConfirmedIds(
                ReservationStatus.CONFIRMED,
                now,
                PageRequest.of(0, 100)
        )) {
            try {
                workflowService.completeDueReservation(reservationId, now);
            } catch (InvalidReservationStateException
                     | ResourceNotFoundException exception) {
                LOGGER.debug(
                        "Reservation {} was already reconciled by another worker.",
                        reservationId
                );
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Could not reconcile reservation {}.",
                        reservationId,
                        exception
                );
            }
        }
    }
}