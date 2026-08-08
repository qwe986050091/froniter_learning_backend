package org.example.service;

import org.example.thrift.Request;
import org.example.thrift.Response;

public interface FrontierService {

    Response processRequest(Request req) throws Exception;

    Response healthCheck();
}