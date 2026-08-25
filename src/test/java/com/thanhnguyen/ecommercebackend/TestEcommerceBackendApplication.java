package com.thanhnguyen.ecommercebackend;

import org.springframework.boot.SpringApplication;

public class TestEcommerceBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(EcommerceBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
