package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IGRPUserRepository extends JpaRepository<IGRPUser, String> {

    // Método para buscar todos os usuários
    List<IGRPUser> findAll();
}
