/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.authentication.web.scheme;

import org.apache.commons.lang.StringUtils;
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Authenticated;
import org.openmrs.api.context.BasicAuthenticated;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.api.context.Credentials;
import org.openmrs.module.authentication.credentials.AuthenticationCredentials;
import org.openmrs.module.authentication.credentials.SecretQuestionAuthenticationCredentials;
import org.openmrs.module.authentication.web.AuthenticationSession;

import java.util.Properties;

/**
 * This is an implementation of a WebAuthenticationScheme that is intended to be used as a secondary authentication
 * scheme, and validates that the provided answer matches the stored answer for the secret question for the user
 * This scheme supports configuration parameters that enable implementations to utilize it with their own pages
 * This includes the ability to configure the `loginPage` that the user should be taken to, as well as the
 * `questionParam` and `answerParam` that should be read from the http request submission to authenticate.
 */
public class SecretQuestionAuthenticationScheme implements WebAuthenticationScheme {

    public static final String LOGIN_PAGE = "loginPage";
    public static final String QUESTION_PARAM = "questionParam";
    public static final String ANSWER_PARAM = "answerParam";

    private String schemeId;
    private String loginPage;
    private String questionParam;
    private String answerParam;

    public SecretQuestionAuthenticationScheme() {
        this.schemeId = getClass().getName();
    }

    @Override
    public String getSchemeId() {
        return schemeId;
    }

    @Override
    public void configure(String schemeId, Properties config) {
        this.schemeId = schemeId;
        loginPage = config.getProperty(LOGIN_PAGE, "/module/authentication/secretQuestion.htm");
        questionParam = config.getProperty(QUESTION_PARAM, "question");
        answerParam = config.getProperty(ANSWER_PARAM, "answer");
    }

    @Override
    public AuthenticationCredentials getCredentials(AuthenticationSession session) {
        SecretQuestionAuthenticationCredentials credentials = null;
        String question = session.getRequestParam(questionParam);
        String answer = session.getRequestParam(answerParam);
        if (StringUtils.isNotBlank(question) && StringUtils.isNotBlank(answer)) {
            User candidateUser = session.getAuthenticationContext().getCandidateUser();
            credentials = new SecretQuestionAuthenticationCredentials(schemeId, candidateUser, question, answer);
        }
        return credentials;
    }

    @Override
    public String getChallengeUrl(AuthenticationSession session) {
        if (session.getAuthenticationContext().getCredentials(schemeId) == null) {
            return loginPage;
        }
        return null;
    }

    @Override
    public Authenticated authenticate(Credentials credentials) throws ContextAuthenticationException {

        // Ensure the credentials provided are of the expected type
        if (!(credentials instanceof SecretQuestionAuthenticationCredentials)) {
            throw new ContextAuthenticationException("The credentials provided are invalid.");
        }
        SecretQuestionAuthenticationCredentials ac = (SecretQuestionAuthenticationCredentials) credentials;

        if (ac.getUser() == null) {
            throw new ContextAuthenticationException("User missing from credentials");
        }
        if (StringUtils.isBlank(ac.getQuestion())) {
            throw new ContextAuthenticationException("Question missing from credentials");
        }
        if (StringUtils.isBlank(ac.getAnswer())) {
            throw new ContextAuthenticationException("Answer missing from credentials");
        }

        String expectedQuestion = getSecretQuestion(ac.getUser());
        if (StringUtils.isBlank(expectedQuestion)) {
            throw new ContextAuthenticationException("User does not have a secret question configured");
        }
        if (!expectedQuestion.equalsIgnoreCase(ac.getQuestion())) {
            throw new ContextAuthenticationException("Invalid question submitted");
        }

        if (!isSecretAnswer(ac.getUser(), ac.getAnswer())) {
            throw new ContextAuthenticationException("Incorrect secret answer");
        }

        return new BasicAuthenticated(ac.getUser(), credentials.getAuthenticationScheme());
    }

    /**
     * @see UserService#getSecretQuestion(User)
     */
    protected String getSecretQuestion(User user) {
        return Context.getUserService().getSecretQuestion(user);
    }

    /**
     * @see UserService#isSecretAnswer(User, String)
     */
    protected boolean isSecretAnswer(User user, String answer) {
        return Context.getUserService().isSecretAnswer(user, answer);
    }
}