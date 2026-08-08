package org.example.thrift;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class Request implements Serializable {

    private String id;

    private String name;

    private Map<String, String> metadata;
}