package com.mthind.tentflow.api.dto;

//tent inventory type JSON returned by the API
public record TentResponse(
        long id,
        int widthFeet,
        int lengthFeet,
        String sizeLabel,
        int totalQuantity
) {
}
