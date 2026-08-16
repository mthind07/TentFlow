package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.CreateMaintenanceBlockRequest;
import com.mthind.tentflow.api.dto.MaintenanceBlockResponse;
import com.mthind.tentflow.model.MaintenanceBlock;
import com.mthind.tentflow.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

//HTTP boundary for inventory maintenance windows
@RestController
@RequestMapping("/api/maintenance-blocks")
public class MaintenanceBlockController {

    private final BookingService bookingService;
    private final ApiMapper mapper;

    public MaintenanceBlockController(
            BookingService bookingService,
            ApiMapper mapper
    ) {
        this.bookingService = bookingService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<MaintenanceBlockResponse> createMaintenanceBlock(
            @Valid @RequestBody CreateMaintenanceBlockRequest request
    ) {
        MaintenanceBlock block = bookingService.addMaintenanceBlock(
                request.tentId(),
                request.quantityUnavailable(),
                request.from(),
                request.until(),
                request.reason()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(block.getId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(mapper.toMaintenanceBlockResponse(block));
    }

    @GetMapping
    public List<MaintenanceBlockResponse> getMaintenanceBlocks() {
        return bookingService.getAllMaintenanceBlocks()
                .stream()
                .map(mapper::toMaintenanceBlockResponse)
                .toList();
    }

    @GetMapping("/{maintenanceBlockId}")
    public MaintenanceBlockResponse getMaintenanceBlock(
            @PathVariable long maintenanceBlockId
    ) {
        return mapper.toMaintenanceBlockResponse(
                bookingService.getMaintenanceBlock(maintenanceBlockId)
        );
    }

    @DeleteMapping("/{maintenanceBlockId}")
    public ResponseEntity<Void> removeMaintenanceBlock(
            @PathVariable long maintenanceBlockId
    ) {
        bookingService.removeMaintenanceBlock(maintenanceBlockId);
        return ResponseEntity.noContent().build();
    }
}
