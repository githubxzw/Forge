package com.forge.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<Credential, String> {
    Optional<Credential> findByName(String name);
}
