package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.CreateCustomerRequest;
import com.mthind.tentflow.api.dto.CustomerResponse;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.service.BookingService;
import com.mthind.tentflow.service.BookingWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

//HTTP boundary for customer registration and lookup
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final BookingService bookingService;
    private final ApiMapper mapper;
    private final BookingWorkflowService workflowService;

    public CustomerController(
            BookingService bookingService,
            ApiMapper mapper,
            BookingWorkflowService workflowService
    ) {
        this.bookingService = bookingService;
        this.mapper = mapper;
        this.workflowService = workflowService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        Customer customer = workflowService.registerCustomer(
                request.fullName(),
                request.email(),
                request.phone()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(customer.getId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(mapper.toCustomerResponse(customer));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public List<CustomerResponse> getCustomers() {
        return bookingService.getAllCustomers()
                .stream()
                .map(mapper::toCustomerResponse)
                .toList();
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("@bookingAuthorization.canAccessCustomer(authentication, #customerId)")
    public CustomerResponse getCustomer(
            @PathVariable long customerId
    ) {
        return mapper.toCustomerResponse(
                bookingService.getCustomer(customerId)
        );
    }
}