package com.mthind.tentflow.model;

//temporarily removes tents from available inventory
public final class MaintenanceBlock {

    private final long id;
    private final TentType tentType;
    private final int quantityUnavailable;
    private final TimeRange timeRange;
    private final String reason;

    public MaintenanceBlock(
            long id,
            TentType tentType,
            int quantityUnavailable,
            TimeRange timeRange,
            String reason
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Maintenance block ID must be positive."
            );
        }

        if (tentType == null || timeRange == null) {
            throw new IllegalArgumentException(
                    "Tent type and maintenance time are required."
            );
        }

        if (quantityUnavailable <= 0
                || quantityUnavailable
                > tentType.getTotalQuantity()) {

            throw new IllegalArgumentException(
                    "Unavailable quantity must be between 1 and "
                            + tentType.getTotalQuantity()
                            + "."
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Maintenance reason cannot be blank."
            );
        }

        this.id = id;
        this.tentType = tentType;
        this.quantityUnavailable = quantityUnavailable;
        this.timeRange = timeRange;
        this.reason = reason.trim();
    }

    public long getId() {
        return id;
    }

    public TentType getTentType() {
        return tentType;
    }

    public int getQuantityUnavailable() {
        return quantityUnavailable;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public String getReason() {
        return reason;
    }
}
