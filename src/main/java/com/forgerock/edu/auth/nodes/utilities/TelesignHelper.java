package com.forgerock.edu.auth.nodes.utilities;

import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class TelesignHelper {

    private final Logger logger = LoggerFactory.getLogger("amAuth");

    public String getTelephoneNumber(AMIdentity identity, String mobilePhoneAttributeName) throws NodeProcessException {
        Set<String> telephoneNumbers;
        try {
            telephoneNumbers = identity.getAttribute(mobilePhoneAttributeName);
        } catch (IdRepoException | SSOException e) {
            e.printStackTrace();
            return "phoneNumberNotFound";
        }

        if (telephoneNumbers != null && !telephoneNumbers.isEmpty()) {
            String phone = telephoneNumbers.iterator().next();
            if (phone != null) {
                return phone;
            }
        }
        logger.debug("No phone number found for user " + identity.getName());
        return "phoneNumberNotFound";
    }
}
