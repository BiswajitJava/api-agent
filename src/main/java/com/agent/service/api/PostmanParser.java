package com.agent.service.api;

import com.agent.model.ApiSpecification;
import com.agent.model.ParsedPostmanCollection;

import java.io.File;

public interface PostmanParser {
    /**
     * Parses a Postman collection from a file and transforms it into our internal ApiSpecification.
     *
     * @param file The Postman collection JSON file.
     * @return A simplified ApiSpecification object.
     */
    ParsedPostmanCollection parse(File file);
}