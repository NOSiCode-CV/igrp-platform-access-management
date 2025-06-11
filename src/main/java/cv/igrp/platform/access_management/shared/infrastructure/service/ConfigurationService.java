package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class ConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);

    private IGRPUserRepository userRepository;

    public ConfigurationService(IGRPUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void createSuperAdminUser() {

        var admin = userRepository.findByUsername("superadmin");

        if(admin.isEmpty()) {

            var newAdmin = new IGRPUser();

            newAdmin.setName("iGRP Super Admin");
            newAdmin.setUsername("superadmin");
            newAdmin.setEmail("superadmin@igrp.cv");
            newAdmin.setRoles(new ArrayList<>());

            userRepository.save(newAdmin);

            LOGGER.info("Super admin user created");

        } else {

            LOGGER.info("Super admin user already exists");

        }

    }

}
