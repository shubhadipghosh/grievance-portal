package com.shubha.grievanceportal;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class HashGen {
    @Test
    void printHash() {
        System.out.println("HASH: {bcrypt}" + new BCryptPasswordEncoder().encode("devpass123"));
    }
}

