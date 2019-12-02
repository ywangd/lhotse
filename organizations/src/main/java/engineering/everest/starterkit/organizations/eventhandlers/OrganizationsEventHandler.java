package engineering.everest.starterkit.organizations.eventhandlers;

import engineering.everest.starterkit.organizations.OrganizationAddress;
import engineering.everest.starterkit.organizations.domain.events.OrganizationAddressUpdatedByAdminEvent;
import engineering.everest.starterkit.organizations.domain.events.OrganizationContactDetailsUpdatedByAdminEvent;
import engineering.everest.starterkit.organizations.domain.events.OrganizationDeregisteredByAdminEvent;
import engineering.everest.starterkit.organizations.domain.events.OrganizationNameUpdatedByAdminEvent;
import engineering.everest.starterkit.organizations.domain.events.OrganizationRegisteredByAdminEvent;
import engineering.everest.starterkit.organizations.domain.events.OrganizationReregisteredByAdminEvent;
import engineering.everest.starterkit.organizations.persistence.Address;
import engineering.everest.starterkit.organizations.persistence.OrganizationsRepository;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OrganizationsEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrganizationsEventHandler.class);

    private final OrganizationsRepository organizationsRepository;

    @Autowired
    public OrganizationsEventHandler(OrganizationsRepository organizationsRepository) {
        this.organizationsRepository = organizationsRepository;
    }

    @EventHandler
    void on(OrganizationRegisteredByAdminEvent event, @Timestamp Instant creationTime) {
        LOGGER.info("Creating new organization: {}", event.getOrganizationId());
        var organizationAddress = new OrganizationAddress(event.getStreet(), event.getCity(), event.getState(),
                event.getCountry(), event.getPostalCode());
        organizationsRepository.createOrganization(event.getOrganizationId(), event.getOrganizationName(),
                organizationAddress, event.getWebsiteUrl(), event.getContactName(), event.getContactPhoneNumber(),
                event.getContactEmail(), creationTime);
    }

    @EventHandler
    void on(OrganizationDeregisteredByAdminEvent event) {
        var persistableOrganization = organizationsRepository.findById(event.getOrganizationId()).orElseThrow();
        persistableOrganization.setDeregistered(true);
        organizationsRepository.save(persistableOrganization);
    }

    @EventHandler
    void on(OrganizationReregisteredByAdminEvent event) {
        var persistableOrganization = organizationsRepository.findById(event.getOrganizationId()).orElseThrow();
        persistableOrganization.setDeregistered(false);
        organizationsRepository.save(persistableOrganization);
    }

    @EventHandler
    void on(OrganizationNameUpdatedByAdminEvent event) {
        var organization = organizationsRepository.findById(event.getOrganizationId()).orElseThrow();
        organization.setOrganizationName(selectDesiredState(event.getOrganizationName(), organization.getOrganizationName()));
        organizationsRepository.save(organization);
    }

    @EventHandler
    void on(OrganizationContactDetailsUpdatedByAdminEvent event) {
        var organization = organizationsRepository.findById(event.getOrganizationId()).orElseThrow();
        organization.setContactName(selectDesiredState(event.getContactName(), organization.getContactName()));
        organization.setPhoneNumber(selectDesiredState(event.getPhoneNumber(), organization.getPhoneNumber()));
        organization.setEmailAddress(selectDesiredState(event.getEmailAddress(), organization.getEmailAddress()));
        organization.setWebsiteUrl(selectDesiredState(event.getWebsiteUrl(), organization.getWebsiteUrl()));
        organizationsRepository.save(organization);
    }

    @EventHandler
    void on(OrganizationAddressUpdatedByAdminEvent event) {
        var organization = organizationsRepository.findById(event.getOrganizationId()).orElseThrow();
        var organizationAddress = organization.getAddress();
        String city = selectDesiredState(event.getCity(), organizationAddress.getCity());
        String street = selectDesiredState(event.getStreet(), organizationAddress.getStreet());
        String state = selectDesiredState(event.getState(), organizationAddress.getState());
        String country = selectDesiredState(event.getCountry(), organizationAddress.getCountry());
        String postalCode = selectDesiredState(event.getPostalCode(), organizationAddress.getPostalCode());
        Address address = new Address(street, city, state, country, postalCode);
        organization.setAddress(address);
        organizationsRepository.save(organization);
    }

    private String selectDesiredState(String desiredState, String currentState) {
        return desiredState == null ? currentState : desiredState;
    }
}