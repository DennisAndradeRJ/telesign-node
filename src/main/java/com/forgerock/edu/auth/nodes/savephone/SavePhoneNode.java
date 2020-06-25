/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */


package com.forgerock.edu.auth.nodes.savephone;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.idm.IdUtils;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;


/**
 * A node that checks to see if zero-page login headers have specified username and whether that username is in a group
 * permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider  = SingleOutcomeNode.OutcomeProvider.class,
               configClass      = SavePhoneNode.Config.class)
public class SavePhoneNode extends SingleOutcomeNode {

    private final Logger logger = LoggerFactory.getLogger("amAuth");
    private static final String BUNDLE = SavePhoneNode.class.getName().replace(".", "/");
    private final SavePhoneNode.Config config;

    /**
     * Configuration for the node.
     */
    public interface Config {
    }

    @Inject
    public SavePhoneNode(@Assisted SavePhoneNode.Config config) {
        this.config = config;
    }

    @Override
    public Action process(TreeContext context) {

        String username = context.sharedState.get(USERNAME).asString();
        String realm = context.sharedState.get(REALM).asString();
        AMIdentity userIdentity = IdUtils.getIdentity(username, realm);

        String phone = context.sharedState.get("phonenumber").asString();
        String mobilePhoneAttributeName = context.sharedState.get("phoneattributename").asString();

        Map<String, Set<String>> attributes = new HashMap<>();
        attributes.put(mobilePhoneAttributeName, Collections.singleton(phone));

        try {
            userIdentity.setAttributes(attributes);
            userIdentity.store();
        } catch (IdRepoException | SSOException ex) {
            logger.error("Unable to update user {} in realm {} with attributes {}", username, realm, attributes, ex);
        }

        return goToNext().build();
    }

}
