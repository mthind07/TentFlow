package com.mthind.tentflow.security;

//application roles stored in PostgreSQL and embedded in signed JWTs
public enum Role {
    CUSTOMER,
    STAFF,
    ADMIN
}
