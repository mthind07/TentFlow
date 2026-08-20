package com.mthind.tentflow.security;

import com.mthind.tentflow.persistence.repository.ReservationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("bookingAuthorization")
public class BookingAuthorization {

    private final SecurityIdentity securityIdentity;
    private final ReservationRepository reservationRepository;

    public BookingAuthorization(
            SecurityIdentity securityIdentity,
            ReservationRepository reservationRepository
    ) {
        this.securityIdentity = securityIdentity;
        this.reservationRepository = reservationRepository;
    }

    public boolean canAccessCustomer(
            Authentication authentication,
            long customerId
    ) {
        if (securityIdentity.isStaffOrAdmin(authentication)) {
            return true;
        }
        return customerId == customerId(authentication);
    }

    public boolean canCreateReservation(
            Authentication authentication,
            long customerId
    ) {
        return canAccessCustomer(authentication, customerId);
    }

    @Transactional(readOnly = true)
    public boolean canAccessReservation(
            Authentication authentication,
            long reservationId
    ) {
        if (securityIdentity.isStaffOrAdmin(authentication)) {
            return true;
        }

        return reservationRepository
                .findCustomerIdByReservationId(reservationId)
                .map(id -> id == customerId(authentication))
                .orElse(false);
    }

    public boolean isStaffOrAdmin(Authentication authentication) {
        return securityIdentity.isStaffOrAdmin(authentication);
    }

    public long customerId(Authentication authentication) {
        return securityIdentity.from(authentication)
                .map(SecurityIdentity.CurrentUser::customerId)
                .filter(id -> id != null)
                .orElseThrow(() -> new AccessDeniedException(
                        "A customer identity is required."
                ));
    }
}
