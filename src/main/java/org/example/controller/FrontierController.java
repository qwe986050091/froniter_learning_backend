package org.example.controller;

import org.example.service.FrontierService;
import org.example.thrift.Request;
import org.example.thrift.Response;
import org.example.thrift.ServiceException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class FrontierController {

    private final FrontierService frontierService;

    public FrontierController(FrontierService frontierService) {
        this.frontierService = frontierService;
    }

    @PostMapping("/request")
    public Response processRequest(@RequestBody Request req) throws Exception {
        return frontierService.processRequest(req);
    }

    @GetMapping("/health")
    public Response healthCheck() {
        return frontierService.healthCheck();
    }

    @ExceptionHandler(ServiceException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Response handleServiceException(ServiceException e) {
        return Response.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    public Response handleException(Exception e) {
        return Response.error("500", e.getMessage());
    }
}