package com.ingesta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "status")
public class StatusEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_status")
    private Integer idStatus;

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    protected StatusEntity() {
        // JPA
    }

    public StatusEntity(String name) {
        this.name = name;
    }

    public Integer getIdStatus() {
        return idStatus;
    }

    public String getName() {
        return name;
    }
}
