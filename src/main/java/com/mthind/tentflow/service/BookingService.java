package com.mthind.tentflow.service;

import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.MaintenanceBlock;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.model.WaitlistEntryView;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingService {

    Customer registerCustomer(String fullName, String email, String phone);

    TentType addTentType(int widthFeet, int lengthFeet, int totalQuantity);

    ReservationView requestReservation(
            long customerId,
            long tentTypeId,
            int quantity,
            LocalDateTime eventStart,
            LocalDateTime eventEnd,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil,
            String location
    );

    int getAvailableQuantity(
            long tentTypeId,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil
    );

    ReservationView confirmReservation(long reservationId);

    ReservationView rejectReservation(long reservationId);

    ReservationView cancelReservation(long reservationId);

    ReservationView completeReservation(long reservationId);

    MaintenanceBlock addMaintenanceBlock(
            long tentTypeId,
            int quantityUnavailable,
            LocalDateTime from,
            LocalDateTime until,
            String reason
    );

    void removeMaintenanceBlock(long maintenanceBlockId);

    Customer getCustomer(long customerId);

    List<Customer> getAllCustomers();

    TentType getTentType(long tentTypeId);

    List<TentType> getAllTentTypes();

    ReservationView getReservation(long reservationId);

    List<ReservationView> getAllReservations();

    List<MaintenanceBlock> getAllMaintenanceBlocks();

    MaintenanceBlock getMaintenanceBlock(long maintenanceBlockId);

    List<WaitlistEntryView> getWaitlistForTent(long tentTypeId);
}