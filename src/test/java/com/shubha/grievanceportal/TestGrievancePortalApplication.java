package com.shubha.grievanceportal;

import org.springframework.boot.SpringApplication;

public class TestGrievancePortalApplication {

    public static void main(String[] args) {
        SpringApplication.from(GrievancePortalApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
