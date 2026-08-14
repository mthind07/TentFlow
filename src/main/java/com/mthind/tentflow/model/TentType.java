package com.mthind.tentflow.model;

//represents one tent size and how many the company owns
public final class TentType {

    private static final int MINIMUM_DIMENSION_FEET = 10;
    private static final int MAXIMUM_DIMENSION_FEET = 40;

    private final long id;
    private final int widthFeet;
    private final int lengthFeet;
    private final int totalQuantity;

    public TentType(
            long id,
            int widthFeet,
            int lengthFeet,
            int totalQuantity
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Tent type ID must be positive."
            );
        }

        validateDimension(widthFeet, "Width");
        validateDimension(lengthFeet, "Length");

        if (widthFeet > lengthFeet) {
            throw new IllegalArgumentException(
                    "Enter the smaller dimension first. "
                            + "Use 20x30 instead of 30x20."
            );
        }

        if (totalQuantity <= 0) {
            throw new IllegalArgumentException(
                    "Total tent quantity must be positive."
            );
        }

        this.id = id;
        this.widthFeet = widthFeet;
        this.lengthFeet = lengthFeet;
        this.totalQuantity = totalQuantity;
    }

    private static void validateDimension(
            int dimension,
            String fieldName
    ) {
        if (dimension < MINIMUM_DIMENSION_FEET
                || dimension > MAXIMUM_DIMENSION_FEET) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must be between 10 and 40 feet."
            );
        }
    }

    public String getSizeLabel() {
        return widthFeet + "x" + lengthFeet;
    }

    public long getId() {
        return id;
    }

    public int getWidthFeet() {
        return widthFeet;
    }

    public int getLengthFeet() {
        return lengthFeet;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }
}
