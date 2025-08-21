package com.agent.service.api;

import com.agent.model.ApiSpecification;

public interface StateService {
    void saveSpecification(String alias, ApiSpecification spec);
    ApiSpecification getSpecification(String alias);
    void saveCredential(String alias, String token);
    String getCredential(String alias);
}