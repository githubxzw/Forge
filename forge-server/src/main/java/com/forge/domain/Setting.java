package com.forge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "setting")
public class Setting {
    @Id
    @Column(length = 64)
    private String key;

    @Column(columnDefinition = "text")
    private String value;
}
