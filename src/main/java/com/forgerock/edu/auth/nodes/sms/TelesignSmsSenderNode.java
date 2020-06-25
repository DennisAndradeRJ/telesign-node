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


package com.forgerock.edu.auth.nodes.sms;

import com.forgerock.edu.auth.nodes.utilities.TelesignHelper;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.ONE_TIME_PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

import com.sun.identity.sm.RequiredValueValidator;
import com.telesign.MessagingClient;
import com.telesign.RestClient;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.*;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.utils.StringUtils;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.forgerock.openam.auth.node.api.Action.ActionBuilder;


import javax.inject.Inject;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A node that checks to see if zero-page login headers have specified username and whether that username is in a group
 * permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider  = TelesignSmsSenderNode.TelesignOutcomeProvider.class,
               configClass      = TelesignSmsSenderNode.Config.class)
public class TelesignSmsSenderNode implements Node {

    private final Logger logger = LoggerFactory.getLogger("amAuth");
    private static final String BUNDLE = TelesignSmsSenderNode.class.getName().replace(".", "/");
    private final CoreWrapper coreWrapper;
    private ResourceBundle resourceBundle;
    private String mobilePhoneAttributeName;
    private String customerId;
    private String apiKey;
    private String message;

    /**
     * Configuration for the node.
     */
    public interface Config {
        @Attribute(order = 100, validators = {RequiredValueValidator.class})
        default String mobilePhoneAttributeName() { return "telephoneNumber"; }

        @Attribute(order = 200)
        default String message() { return "Your one time passcode is"; }

        @Attribute(order = 300)
        default String telesign_CUSTOMER_ID() { return "CUSTOMER_ID"; }

        @Attribute(order = 400)
        default String telesign_apiKey() { return "apiKey"; }
    }


    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of other classes
     * from the plugin.
     *
     * @param config The service config.
     * @param coreWrapper
     * @throws NodeProcessException If the configuration was not valid.
     */
    @Inject
    public TelesignSmsSenderNode(@Assisted Config config, CoreWrapper coreWrapper) throws NodeProcessException {
        this.coreWrapper = coreWrapper;

        this.mobilePhoneAttributeName = config.mobilePhoneAttributeName();
        this.customerId = config.telesign_CUSTOMER_ID();
        this.message = config.message();
        this.apiKey = config.telesign_apiKey();


        if (StringUtils.isBlank(mobilePhoneAttributeName)) {
            mobilePhoneAttributeName = "telephoneNumber";
        }
        logger.debug("Mobile phone attribute " + mobilePhoneAttributeName);
        logger.debug("Message is " + message);
        logger.debug("Telesign Customer ID " + customerId);
        logger.debug("Telesign apiKey " + apiKey);
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        logger.debug("TelesignSmsSenderNode started");
        ActionBuilder action;
        action = goTo(TelesignOutcome.PASS);
        this.resourceBundle = context.request.locales.getBundleInPreferredLocale(BUNDLE, getClass().getClassLoader());

        String username = context.sharedState.get(USERNAME).asString();
        TelesignHelper telesignHelper = new TelesignHelper();
        String phoneNumber = telesignHelper.getTelephoneNumber(coreWrapper.getIdentity(username,coreWrapper.convertRealmPathToRealmDn(context.sharedState.get(REALM).asString())),mobilePhoneAttributeName);
        switch (phoneNumber) {
            case "phoneNumberNotFound":
                action = goTo(TelesignOutcome.REGISTER);
                break;
            case "attributeNotFound":
                action = goTo(TelesignOutcome.FAIL);
                break;
            default:
                logger.debug(username + " Phone Number " + phoneNumber);

                String customerId = this.customerId;
                String apiKey = this.apiKey;

                String verifyCode = context.sharedState.get(ONE_TIME_PASSWORD).asString();
                String message = String.format("%s %s", this.message, verifyCode);
                String messageType = "OTP";

                try {
                    MessagingClient messagingClient = new MessagingClient(customerId, apiKey);
                    RestClient.TelesignResponse telesignResponse = messagingClient.message(phoneNumber, message, messageType, null);
                    logger.debug("Telesign response: " + telesignResponse);
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
        JsonValue copyState = context.sharedState.copy().put("phoneattributename",this.mobilePhoneAttributeName);
        return action.replaceSharedState(copyState).build();
       //return action.replaceSharedState(copyState).build();
    }

    /**
     * The possible outcomes for the TelesignSmsSenderNode.
     */
    public enum TelesignOutcome {
        /**
         * Phone returned from user store and SMS was sent successfully.
         */
        PASS,
        /**
         * No phone number stored for this user. Need to register the phone number
         */
        REGISTER,
        /**
         * SMS failed.
         */
        FAIL
    }

    /**
     * Defines the possible outcomes from this Ldap node.
     */
    public static class TelesignOutcomeProvider implements OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            ResourceBundle bundle = locales.getBundleInPreferredLocale(TelesignSmsSenderNode.BUNDLE,
                    TelesignOutcomeProvider.class.getClassLoader());
            return ImmutableList.of(
                    new Outcome(TelesignOutcome.PASS.name(), bundle.getString("passOutcome")),
                    new Outcome(TelesignOutcome.REGISTER.name(), bundle.getString("registerOutcome")),
                    new Outcome(TelesignOutcome.FAIL.name(), bundle.getString("failOutcome")));
        }
    }

    private Action.ActionBuilder goTo(TelesignOutcome outcome) {
        return Action.goTo(outcome.name());
    }
}
