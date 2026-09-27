package com.forge.domain;

import jakarta.persistence.*;
import com.forge.common.Converters;
import org.hibernate.annotations.UuidGenerator;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** bcrypt */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
