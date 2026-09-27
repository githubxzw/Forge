package com.forge.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnvironmentRepository extends JpaRepository<Environment, String> {
    Optional<Environment> findByName(String name);
}
