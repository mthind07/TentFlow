package com.mthind.tentflow.service;

import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.persistence.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerReservationQueryService {

    private final ReservationRepository reservationRepository;
    private final BookingService bookingService;
    private final PersistenceViewMapper mapper;

    public CustomerReservationQueryService(
            ReservationRepository reservationRepository,
            BookingService bookingService,
            PersistenceViewMapper mapper
    ) {
        this.reservationRepository = reservationRepository;
        this.bookingService = bookingService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ReservationView> findForCustomer(long customerId) {
        bookingService.getCustomer(customerId);
        return reservationRepository
                .findAllByCustomerIdOrderByIdAsc(customerId)
                .stream()
                .map(mapper::toReservationView)
                .toList();
    }
}