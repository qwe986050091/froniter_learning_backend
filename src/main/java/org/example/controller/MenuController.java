package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.example.annotation.RequireAuth;
import org.example.service.FrontierServiceImpl;
import org.example.thrift.MenuItem;
import org.example.thrift.ServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final FrontierServiceImpl frontierService;

    public MenuController(FrontierServiceImpl frontierService) {
        this.frontierService = frontierService;
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<List<MenuItem>> getMenu() {
        try {
            List<MenuItem> menu = frontierService.getMenu();
            return ResponseEntity.ok(menu);
        } catch (ServiceException e) {
            log.warn("Failed to get menu: code={}, description={}", e.getCode(), e.getDescription());
            return ResponseEntity.badRequest().build();
        } catch (TException e) {
            log.error("Thrift error during getMenu", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
