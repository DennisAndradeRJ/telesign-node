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


package com.forgerock.edu.auth.nodes.registerphone;

import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;
import com.google.inject.assistedinject.Assisted;

import com.telesign.PhoneIdClient;
import com.telesign.RestClient;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;

import javax.security.auth.callback.*;

import java.util.*;

import static javax.security.auth.callback.TextOutputCallback.ERROR;
import static org.forgerock.openam.auth.node.api.Action.send;
import com.forgerock.edu.auth.nodes.utilities.ClientScriptUtilities;


/**
 * A node that checks to see if zero-page login headers have specified username and whether that username is in a group
 * permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider  = SingleOutcomeNode.OutcomeProvider.class,
               configClass      = RegisterPhoneNode.Config.class)
public class RegisterPhoneNode extends SingleOutcomeNode {

    private final Logger logger = LoggerFactory.getLogger("amAuth");
    private static final String BUNDLE = RegisterPhoneNode.class.getName().replace(".", "/");
    private static final String SCRIPT = "com/forgerock/edu/auth/nodes/registerphone/RegisterPhoneNode.js";


    private final RegisterPhoneNode.Config config;

    private static final String ENROLLMENT_PAGE_TITLE = "enrollmentPageTitle";
    private static final String ENROLLMENT_PAGE_INTRO = "enrollmentPageIntroduction";

    private final ClientScriptUtilities scriptUtils;


    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order=100)
        default String message() {return "It looks like your phone number is not in our system. Please enter it below. NUMBERS ONLY."; }

        @Attribute(order = 200)
        default String prompt() { return "What's your phone number with the area code?"; }

    }

    @Inject
    public RegisterPhoneNode(@Assisted RegisterPhoneNode.Config config, ClientScriptUtilities scriptUtils) {
        this.config = config;
        this.scriptUtils = scriptUtils;
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        // Text for the label with the explanation of the enrollment page so the user can enter and register
        // their phone number
        //Text for the phone textbox prompt
        String prompt = config.prompt();

        // To handle the case where the admin used a shared state variable name in the prompt using {{ and }}
        if ((prompt.indexOf("{{") == 0) && (prompt.indexOf("}}") == (prompt.length()-2))) {
            prompt = context.sharedState.get(prompt.substring(2,prompt.length()-2)).asString();
            logger.debug("[RegisterPhoneNode]: Found existing shared state attribute " + prompt);
        }

        final String promptName = prompt;

        // Informational text on top of the page to give some information about the page to the user.
        ResourceBundle bundle = context.request.locales.getBundleInPreferredLocale(BUNDLE,
                RegisterPhoneNode.OutcomeProvider.class.getClassLoader());
        List<String> entries = new ArrayList<>();
        entries.add(bundle.getString(ENROLLMENT_PAGE_TITLE));
        entries.add(bundle.getString(ENROLLMENT_PAGE_INTRO));

        String dehydratedScript = scriptUtils.getScriptAsString(SCRIPT);
        String script = String.format(dehydratedScript, entries.toArray());

        ScriptTextOutputCallback scriptCallback = new ScriptTextOutputCallback(script);
        NameCallback nameCallBack = new NameCallback(promptName);

        List<Callback> callbacks = new ArrayList<>();
        callbacks.add(scriptCallback);
        callbacks.add(nameCallBack);

        Optional <String> result = context.getCallback(NameCallback.class)
                .map(NameCallback::getName);
        if (result.isPresent()) {
            String phoneNumber = result.get();
            // Get a valid Telesign phone number with no special characters
            phoneNumber = fixPhoneNumber(phoneNumber);
            if (isPhoneNumberValid(phoneNumber)) {
                JsonValue copyState = context.sharedState.copy().put("phonenumber",phoneNumber);
                return goToNext().replaceSharedState(copyState).build();
            } else
                callbacks.add(getErrorCallback(bundle.getString("error.phone.format")));
        }
        return send(callbacks).build();

    }

    private TextOutputCallback getErrorCallback(String message) {
        return new TextOutputCallback(ERROR, message);
    }

    private boolean isPhoneNumberValid (String number) {
        if (number.length() == 11)
            return true;
        return false;
    }

    private String fixPhoneNumber (String number) {
        number = number.replaceAll("[\\D]", "");
        number = "1" + number; // Only support for US number
        return number;
    }

}
