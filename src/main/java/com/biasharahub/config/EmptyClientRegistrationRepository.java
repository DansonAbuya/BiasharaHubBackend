package com.biasharahub.config;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Collections;
import java.util.Iterator;

/**
 * No-op ClientRegistrationRepository used when OAuth2 is not configured.
 * findByRegistrationId always returns null; iterator is empty.
 */
public class EmptyClientRegistrationRepository implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return null;
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return Collections.emptyIterator();
    }
}
