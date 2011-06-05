/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.openejb.tools.twitter;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.openejb.tools.twitter.util.RetweetAppUtil;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * We should monitor this feed http://twitter.com/#!/OpenEJB/contributors
 * and retweet anything that mentions OpenEJB
 * <p/>
 * So if anyone in the contributors list tweeted about OpenEJB, the OpenEJB twitter account would retweet it
 * <p/>
 * Two things will happen as a result:
 * -  The more activity on the OpenEJB twitter account the more followers it will get
 * -  The more @joe and other contributors are seen on the account, the more followers they will get
 * <p/>
 * The OpenEJB twitter account has more followers than most everyone else so getting it
 * to retweet is a good way to expose people to all our wonderful contributors
 * and get them some followers and help the project at the same time.
 * <p/>
 * The result is we as a community will have more ability overall to get the word out!
 * <p/>
 * Implemented using :http://code.google.com/p/oauth-signpost/wiki/GettingStarted
 * list - HTTP GET http://api.twitter.com/1/lists/statuses.xml?slug=contributors&owner_screen_name=OpenEJB
 * retweet - HTTP POST http://api.twitter.com/1/statuses/retweet/<statusid>.xml
 *
 * @version $Rev$ $Date$
 */
public class Retweet {

    public static Properties retweetToolProperties = RetweetAppUtil.getTwitterAppProperties();
    public static OAuthConsumer consumer;

    @SuppressWarnings("rawtypes")
    public static void main(String[] args) {
        HttpResponse response = ContribListStatusRetriever.getStatusesFromOpenEJBContributorsList();
        String responseBody = JsonResponseParser.getResponseBody(response);
        StringReader jsonDataReader = new StringReader(responseBody);
        //Each status is a entry in the list. Each status has various properties in the form of key-value pairs
        List<Map> listFromJson = JsonResponseParser.getListFromJson(jsonDataReader);
        List<String> nonRetweetedOpenEJBStatusIDs = OpenEJBMessageFilterUtil.getNonRetweetedOpenEJBStatusIDs(listFromJson);

        System.out.println("About to retweet:" + nonRetweetedOpenEJBStatusIDs);
        retweetIfListIsNotEmpty(nonRetweetedOpenEJBStatusIDs);

    }

    private static void retweetIfListIsNotEmpty(List<String> nonRetweetedOpenEJBStatusIDs) {

        if (!nonRetweetedOpenEJBStatusIDs.isEmpty()) {
            retweetThisListOfStatuses(nonRetweetedOpenEJBStatusIDs);
        } else {
            System.out.println("No message to retweet.");
        }
    }

    private static void retweetThisListOfStatuses(List<String> nonRetweetedOpenEJBStatusIDs) {

        for (String statusIDToRetweet : nonRetweetedOpenEJBStatusIDs) {
            try {
                retweet(statusIDToRetweet);
                pauseBeforeTheNextRetweet();
            } catch (OAuthMessageSignerException e) {
                e.printStackTrace();
            } catch (OAuthExpectationFailedException e) {
                e.printStackTrace();
            } catch (OAuthCommunicationException e) {
                e.printStackTrace();
            }
        }
    }

    public static void initConsumer() {

        consumer = new CommonsHttpOAuthConsumer(
                retweetToolProperties.getProperty("retweetApp.consumer.key"),
                retweetToolProperties
                        .getProperty("retweetApp.consumerSecret.key"));


        consumer.setTokenWithSecret(retweetToolProperties.getProperty("retweetApp.authorizedUser.consumer.token"),
                retweetToolProperties.getProperty("retweetApp.authorizedUser.consumer.tokenSecret"));

    }

    public static HttpResponse retweet(String statusIDToRetweet) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException {
        HttpPost httpPost = new HttpPost("http://api.twitter.com/1/statuses/retweet/" + statusIDToRetweet + ".json");
        initConsumer();
        consumer.sign(httpPost);
        HttpResponse response = null;
        try {
            response = getHttpClient().execute(httpPost);
            System.out.println(response.getStatusLine());
            System.out.println("Retweeted " + statusIDToRetweet);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    public static HttpClient getHttpClient() {
        return new DefaultHttpClient();
    }

    private static void pauseBeforeTheNextRetweet() {
        try {
            Thread.sleep(1000 * 60 * 5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
