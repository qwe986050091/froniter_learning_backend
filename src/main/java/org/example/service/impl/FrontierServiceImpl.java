package org.example.service.impl;

import org.example.service.FrontierService;
import org.example.thrift.Request;
import org.example.thrift.Response;
import org.example.thrift.ServiceException;
import org.springframework.stereotype.Service;

@Service
public class FrontierServiceImpl implements FrontierService {

    @Override
    public Response processRequest(Request req) throws Exception {
        if (req == null || req.getId() == null || req.getId().isEmpty()) {
            throw new ServiceException("400", "id is required");
        }

        String result = "Processed request: id=" + req.getId()
                + ", name=" + (req.getName() != null ? req.getName() : "N/A");
        return Response.success(result);
    }

    @Override
    public Response healthCheck() {
        return Response.success("OK");
    }
}