package com.example.app.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "service", "{{ values.name }}",
                "message", "Hello from {{ values.name }}!"
        );
    }
}
