package com.wkaiser.riotapimcpserver.riot.account.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@AllArgsConstructor
public class RiotAccountService {
    private RestClient riotRestClient;


}
