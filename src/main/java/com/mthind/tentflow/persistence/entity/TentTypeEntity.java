package com.mthind.tentflow.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tent_types")
public class TentTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "width_feet", nullable = false)
    private int widthFeet;

    @Column(name = "length_feet", nullable = false)
    private int lengthFeet;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    protected TentTypeEntity() {
        //required by JPA
    }

    public TentTypeEntity(
            int widthFeet,
            int lengthFeet,
            int totalQuantity
    ) {
        this.widthFeet = widthFeet;
        this.lengthFeet = lengthFeet;
        this.totalQuantity = totalQuantity;
    }

    public Long getId() {
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

    public String getSizeLabel() {
        return widthFeet + "x" + lengthFeet;
    }
}