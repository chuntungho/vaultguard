package com.vaultguard.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/users")
    public ResponseEntity<List<Object>> listUsers() {
        return ResponseEntity.ok(List.of());
    }
}
