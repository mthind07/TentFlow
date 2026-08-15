package com.mthind.tentflow.api.dto;

//customer JSON returned by the API
public record CustomerResponse(
        long id,
        String fullName,
        String email,
        String phone
) {
}