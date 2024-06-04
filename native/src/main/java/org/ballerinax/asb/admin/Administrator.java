/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KINDither express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.asb.admin;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.rest.PagedIterable;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.AccessRights;
import com.azure.messaging.servicebus.administration.models.AuthorizationRule;
import com.azure.messaging.servicebus.administration.models.EmptyRuleAction;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import com.azure.messaging.servicebus.administration.models.RuleAction;
import com.azure.messaging.servicebus.administration.models.RuleFilter;
import com.azure.messaging.servicebus.administration.models.RuleProperties;
import com.azure.messaging.servicebus.administration.models.SqlRuleAction;
import com.azure.messaging.servicebus.administration.models.SqlRuleFilter;
import com.azure.messaging.servicebus.administration.models.SubscriptionProperties;
import com.azure.messaging.servicebus.administration.models.TopicProperties;
import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import org.ballerinax.asb.util.ASBConstants;
import org.ballerinax.asb.util.ASBErrorCreator;
import org.ballerinax.asb.util.ASBUtils;
import org.ballerinax.asb.util.ModuleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.ballerina.runtime.api.creators.ValueCreator.createRecordValue;

/**
 * This facilitates the client operations of ASB Administrator client in Ballerina.
 */
public class Administrator {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool(
            new AdminNetworkThreadFactory());

    private static final Logger LOGGER = LoggerFactory.getLogger(Administrator.class);

    /**
     * Parameterized constructor for ASB Administrator (ServiceBusAdministrator).
     *
     * @param connectionString Azure service bus connection string
     * @return clientEp Azure Service Bus Administrator instance.
     */
    public static Object initializeAdministrator(BObject administratorClient, String connectionString) {
        try {
            ServiceBusAdministrationClientBuilder administratorBuilder = new ServiceBusAdministrationClientBuilder()
                    .connectionString(connectionString);
            LOGGER.debug("ServiceBusAdministrator initialized");
            setClient(administratorClient, administratorBuilder.buildClient());
            return null;
        } catch (BError e) {
            return ASBErrorCreator.fromBError(e);
        } catch (ServiceBusException e) {
            return ASBErrorCreator.fromASBException(e);
        } catch (Exception e) {
            return ASBErrorCreator.fromUnhandledException(e);
        }
    }

    /**
     * Create a Topic in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the topic
     * @param topicProperties     properties of the Topic (Requires TopicProperties object)
     * @return topicProperties
     */
    public static Object createTopic(Environment env, BObject administratorClient, BString topicName,
                                     BMap<BString, Object> topicProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            TopicProperties topicProp;
            try {
                if (topicProperties == null) {
                    topicProp = clientEp.createTopic(topicName.toString());
                } else {
                    topicProp = clientEp.createTopic(topicName.toString(),
                            ASBUtils.getCreateTopicPropertiesFromBObject(topicProperties));
                }
                BMap<BString, Object> updatedTopicProperties = constructTopicCreatedRecord(topicProp);
                future.complete(updatedTopicProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Get a Topic in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the topic
     * @return topicProperties
     */
    public static Object getTopic(Environment env, BObject administratorClient, BString topicName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                TopicProperties topicProp = clientEp.getTopic(topicName.toString());
                BMap<BString, Object> topicProperties = constructTopicCreatedRecord(topicProp);
                future.complete(topicProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Update a Topic in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the topic
     * @param topicProperties     properties of the Topic (Requires TopicProperties object)
     * @return topicProperties
     */
    public static Object updateTopic(Environment env, BObject administratorClient, BString topicName,
                                     BMap<BString, Object> topicProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                TopicProperties topicProp = clientEp.getTopic(topicName.toString());
                TopicProperties updatedTopicProps = clientEp.updateTopic(
                        ASBUtils.getUpdatedTopicPropertiesFromBObject(topicProperties, topicProp));
                BMap<BString, Object> updatedTopicProperties = constructTopicCreatedRecord(updatedTopicProps);
                future.complete(updatedTopicProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Get all Topics in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @return topicProperties
     */
    public static Object listTopics(Environment env, BObject administratorClient) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                PagedIterable<TopicProperties> topicProp = clientEp.listTopics();
                BMap<BString, Object> topicProperties = constructTopicPropertiesArray(topicProp);
                future.complete(topicProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    private static BMap<BString, Object> constructTopicPropertiesArray(PagedIterable<TopicProperties> topicProp) {
        LinkedList<Object> topicList = new LinkedList<>();
        for (TopicProperties topicProperties : topicProp) {
            Map<String, Object> map = populateTopicOptionalFieldsMap(topicProperties);
            BMap<BString, Object> topicPropertiesRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                    ASBConstants.TOPIC_CREATED_RECORD, map);
            topicList.add(topicPropertiesRecord);
        }
        BMap<BString, Object> topicRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.TOPIC_CREATED_RECORD);
        ArrayType sourceArrayType = TypeCreator.createArrayType(TypeUtils.getType(topicRecord));
        Map<String, Object> map = new HashMap<>();
        map.put(ASBConstants.LIST_OF_TOPICS, ValueCreator.createArrayValue(topicList.toArray(), sourceArrayType));
        return createRecordValue(ModuleUtils.getModule(), ASBConstants.LIST_OF_TOPICS_RECORD, map);
    }

    /**
     * Delete a Topic in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the topic
     * @return null
     */
    public static Object deleteTopic(Environment env, BObject administratorClient, BString topicName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                clientEp.deleteTopic(topicName.toString());
                future.complete(null);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Check whether a Topic exists in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @return null
     */
    public static Object topicExists(Environment env, BObject administratorClient, BString topicName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                boolean topicExists = clientEp.getTopicExists(topicName.toString());
                future.complete(topicExists);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            }  catch (HttpResponseException e) {
                if (e.getResponse().getStatusCode() == 404) {
                    future.complete(false);
                    return;
                }
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Create a Subscription in Azure Service Bus.
     *
     * @param administratorClient    Azure Service Bus Administrator Client
     * @param topicName              name of the Topic
     * @param subscriptionName       name of the Subscription
     * @param subscriptionProperties properties of the Subscription (Requires SubscriptionProperties object)
     * @return subscriptionProperties
     */
    public static Object createSubscription(Environment env, BObject administratorClient,
                                            BString topicName, BString subscriptionName,
                                            BMap<BString, Object> subscriptionProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            SubscriptionProperties subscriptionProps;
            try {
                if (subscriptionProperties.isEmpty()) {
                    subscriptionProps = clientEp.createSubscription(topicName.toString(), subscriptionName.toString());
                } else {
                    subscriptionProps = clientEp.createSubscription(topicName.toString(), subscriptionName.toString(),
                            ASBUtils.getCreateSubscriptionPropertiesFromBObject(subscriptionProperties));
                }
                BMap<BString, Object> updatedSubscriptionProperties = constructSubscriptionCreatedRecord(
                        subscriptionProps);
                future.complete(updatedSubscriptionProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            }  catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Get a Subscription in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @param subscriptionName    name of the Subscription
     * @return subscriptionProperties
     */
    public static Object getSubscription(Environment env, BObject administratorClient, BString topicName,
                                         BString subscriptionName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                SubscriptionProperties subscriptionProps = clientEp.getSubscription(topicName.toString(),
                        subscriptionName.toString());
                BMap<BString, Object> subscriptionProperties = constructSubscriptionCreatedRecord(subscriptionProps);
                future.complete(subscriptionProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            }  catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Update a Subscription in Azure Service Bus.
     *
     * @param administratorClient    Azure Service Bus Administrator Client
     * @param topicName              name of the Topic
     * @param subscriptionName       name of the Subscription
     * @param subscriptionProperties properties of the Subscription (Requires SubscriptionProperties object)
     * @return subscriptionProperties
     */
    public static Object updateSubscription(Environment env, BObject administratorClient, BString topicName,
                                            BString subscriptionName, BMap<BString, Object> subscriptionProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                SubscriptionProperties subscriptionProps = clientEp.getSubscription(topicName.toString(),
                        subscriptionName.toString());
                SubscriptionProperties updatedSubscriptionProps = clientEp.updateSubscription(
                        ASBUtils.getUpdatedSubscriptionPropertiesFromBObject(
                                subscriptionProperties, subscriptionProps));
                BMap<BString, Object> updatedSubscriptionProperties = constructSubscriptionCreatedRecord(
                        updatedSubscriptionProps);
                future.complete(updatedSubscriptionProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Get all Subscriptions in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @return subscriptionProperties
     */
    public static Object listSubscriptions(Environment env, BObject administratorClient, BString topicName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                PagedIterable<SubscriptionProperties> subscriptionProp = clientEp.listSubscriptions(
                        topicName.toString());
                BMap<BString, Object> subscriptionProperties = constructSubscriptionPropertiesArray(subscriptionProp);
                future.complete(subscriptionProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    private static BMap<BString, Object> constructSubscriptionPropertiesArray(
            PagedIterable<SubscriptionProperties> subscriptionProp) {
        LinkedList<Object> subscriptionList = new LinkedList<>();
        for (SubscriptionProperties subscriptionProperties : subscriptionProp) {
            Map<String, Object> map = populateSubscriptionOptionalFieldsMap(subscriptionProperties);
            BMap<BString, Object> subscriptionPropertiesRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                    ASBConstants.SUBSCRIPTION_CREATED_RECORD, map);
            subscriptionList.add(subscriptionPropertiesRecord);
        }
        BMap<BString, Object> subscriptionRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.SUBSCRIPTION_CREATED_RECORD);
        ArrayType sourceArrayType = TypeCreator.createArrayType(TypeUtils.getType(subscriptionRecord));
        Map<String, Object> map = new HashMap<>();
        map.put(ASBConstants.LIST_OF_SUBSCRIPTIONS, ValueCreator.createArrayValue(subscriptionList.toArray(),
                sourceArrayType));
        return createRecordValue(ModuleUtils.getModule(), ASBConstants.LIST_OF_SUBSCRIPTIONS_RECORD, map);
    }

    /**
     * Delete a Subscription in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @param subscriptionName    name of the Subscription
     * @return null
     */
    public static Object deleteSubscription(Environment env, BObject administratorClient, BString topicName,
                                            BString subscriptionName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                clientEp.deleteSubscription(topicName.toString(), subscriptionName.toString());
                future.complete(null);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Check whether a Subscription exists in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @param subscriptionName    name of the Subscription
     * @return null
     */
    public static Object subscriptionExists(Environment env, BObject administratorClient, BString topicName,
                                            BString subscriptionName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                boolean subscriptionExists = clientEp.getSubscriptionExists(
                        topicName.toString(), subscriptionName.toString());
                future.complete(subscriptionExists);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                if (e.getResponse().getStatusCode() == 404) {
                    future.complete(false);
                }
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Create a Rule with properties in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @param subscriptionName    name of the Subscription
     * @param ruleName            name of the Rule
     * @param ruleProperties      properties of the Rule (Requires RuleProperties object)
     * @return ruleProperties object
     */
    public static Object createRule(Environment env, BObject administratorClient,
                                    BString topicName, BString subscriptionName, BString ruleName,
                                    BMap<BString, Object> ruleProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            RuleProperties ruleProp;
            try {
                if (ruleProperties.isEmpty()) {
                    ruleProp = clientEp.createRule(
                            topicName.toString(), subscriptionName.toString(), ruleName.toString());
                } else {
                    ruleProp = clientEp.createRule(topicName.toString(), ruleName.toString(),
                            subscriptionName.toString(), ASBUtils.getCreateRulePropertiesFromBObject(ruleProperties));
                }
                BMap<BString, Object> updatedRuleProperties = constructRuleCreatedRecord(ruleProp);
                future.complete(updatedRuleProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Get a Rule in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @param subscriptionName    name of the Subscription
     * @param ruleName            name of the Rule
     * @return ruleProperties object
     */
    public static Object getRule(Environment env, BObject administratorClient, BString topicName,
                                 BString subscriptionName, BString ruleName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                RuleProperties ruleProp = clientEp.getRule(topicName.toString(), subscriptionName.toString(),
                        ruleName.toString());
                BMap<BString, Object> ruleProperties = constructRuleCreatedRecord(ruleProp);
                future.complete(ruleProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Update a Rule in Azure Service Bus.
     *
     * @param administratorClient  Azure Service Bus Administrator Client
     * @param topicName            name of the Topic
     * @param subscriptionName     name of the Subscription
     * @param ruleName             name of the Rule
     * @param updateRuleProperties properties of the Rule (Requires RuleProperties object)
     * @return ruleProperties object
     */
    public static Object updateRule(Environment env, BObject administratorClient, BString topicName,
                                    BString subscriptionName, BString ruleName,
                                    BMap<BString, Object> updateRuleProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                RuleProperties currentRuleProperties = clientEp.getRule(
                        topicName.toString(), subscriptionName.toString(), ruleName.toString());
                RuleProperties updatedRuleProperties = clientEp.updateRule(topicName.toString(),
                        subscriptionName.toString(),
                        ASBUtils.getUpdatedRulePropertiesFromBObject(updateRuleProperties, currentRuleProperties));
                future.complete(constructRuleCreatedRecord(updatedRuleProperties));
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Get all Rules in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @param subscriptionName    name of the Subscription
     * @return ruleProperties object
     */
    public static Object listRules(Environment env, BObject administratorClient, BString topicName,
                                   BString subscriptionName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                PagedIterable<RuleProperties> ruleProp = clientEp.listRules(topicName.toString(),
                        subscriptionName.toString());
                LOGGER.debug("Retrieved all rules successfully");
                BMap<BString, Object> ruleProperties = constructRulePropertiesArray(ruleProp);
                future.complete(ruleProperties);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (HttpResponseException e) {
                BError bError = ASBErrorCreator.fromASBHttpResponseException(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    private static BMap<BString, Object> constructRulePropertiesArray(PagedIterable<RuleProperties> ruleProp) {
        LinkedList<Object> ruleList = new LinkedList<>();
        for (RuleProperties ruleProperties : ruleProp) {
            Map<String, Object> map = populateRuleOptionalFieldsMap(ruleProperties);
            BMap<BString, Object> rulePropertiesRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                    ASBConstants.CREATED_RULE_RECORD, map);
            ruleList.add(rulePropertiesRecord);
        }
        BMap<BString, Object> ruleRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.CREATED_RULE_RECORD);
        ArrayType sourceArrayType = TypeCreator.createArrayType(TypeUtils.getType(ruleRecord));
        Map<String, Object> map = new HashMap<>();
        map.put(ASBConstants.LIST_OF_RULES, ValueCreator.createArrayValue(ruleList.toArray(), sourceArrayType));
        return createRecordValue(ModuleUtils.getModule(), ASBConstants.LIST_OF_RULES_RECORD, map);
    }

    /**
     * Delete a Rule in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param topicName           name of the Topic
     * @param subscriptionName    name of the Subscription
     * @param ruleName            name of the Rule
     * @return null
     */
    public static Object deleteRule(Environment env, BObject administratorClient, BString topicName,
                                    BString subscriptionName, BString ruleName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        Future future = env.markAsync();
        EXECUTOR_SERVICE.execute(() -> {
            try {
                clientEp.deleteRule(topicName.toString(), subscriptionName.toString(), ruleName.toString());
                future.complete(null);
            } catch (BError e) {
                BError bError = ASBErrorCreator.fromBError(e);
                future.complete(bError);
            } catch (ServiceBusException e) {
                BError bError = ASBErrorCreator.fromASBException(e);
                future.complete(bError);
            } catch (Exception e) {
                BError bError = ASBErrorCreator.fromUnhandledException(e);
                future.complete(bError);
            }
        });
        return null;
    }

    /**
     * Create a Queue with properties in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param queueName           name of the Queue
     * @param queueProperties     properties of the Queue (Requires QueueProperties object)
     * @return queueProperties object
     */
    public static Object createQueue(BObject administratorClient,
                                     BString queueName, BMap<BString, Object> queueProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        try {
            QueueProperties queueProp;
            if (queueProperties.isEmpty()) {
                queueProp = clientEp.createQueue(queueName.toString());
            } else {
                queueProp = clientEp.createQueue(queueName.toString(),
                        ASBUtils.getQueuePropertiesFromBObject(queueProperties));
            }
            LOGGER.debug("Created queue successfully with name: " + queueProp.getName());
            return constructQueueCreatedRecord(queueProp);
        } catch (BError e) {
            return ASBErrorCreator.fromBError(e);
        } catch (ServiceBusException e) {
            return ASBErrorCreator.fromASBException(e);
        } catch (Exception e) {
            return ASBErrorCreator.fromUnhandledException(e);
        }
    }

    /**
     * Get a Queue in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param queueName           name of the Queue
     * @return queueProperties object
     */
    public static Object getQueue(BObject administratorClient, BString queueName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        try {
            QueueProperties queueProp = clientEp.getQueue(queueName.toString());
            LOGGER.debug("Retrieved queue successfully with name: " + queueProp.getName());
            return constructQueueCreatedRecord(queueProp);
        } catch (BError e) {
            return ASBErrorCreator.fromBError(e);
        } catch (ServiceBusException e) {
            return ASBErrorCreator.fromASBException(e);
        } catch (Exception e) {
            return ASBErrorCreator.fromUnhandledException(e);
        }
    }

    /**
     * Update a Queue in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param queueName           name of the Queue
     * @param queueProperties     properties of the Queue (Requires QueueProperties object)
     * @return queueProperties object
     */
    public static Object updateQueue(BObject administratorClient, BString queueName,
                                     BMap<BString, Object> queueProperties) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        try {
            QueueProperties queueProp = clientEp.getQueue(queueName.toString());
            QueueProperties updatedQueueProps = clientEp.updateQueue(
                    ASBUtils.getUpdatedQueuePropertiesFromBObject(queueProperties, queueProp));
            LOGGER.debug("Updated queue successfully with name: " + updatedQueueProps.getName());
            return constructQueueCreatedRecord(updatedQueueProps);
        } catch (BError e) {
            return ASBErrorCreator.fromBError(e);
        } catch (HttpResponseException e) {
            return ASBErrorCreator.fromASBHttpResponseException(e);
        } catch (ServiceBusException e) {
            return ASBErrorCreator.fromASBException(e);
        } catch (Exception e) {
            return ASBErrorCreator.fromUnhandledException(e);

        }
    }

    /**
     * Get all Queues in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @return queueProperties object
     */
    public static Object listQueues(BObject administratorClient) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        try {
            PagedIterable<QueueProperties> queueProp = clientEp.listQueues();
            LOGGER.debug("Retrieved all queues successfully");
            return constructQueuePropertiesArray(queueProp);
        } catch (BError e) {
            return ASBErrorCreator.fromBError(e);
        } catch (HttpResponseException e) {
            return ASBErrorCreator.fromASBHttpResponseException(e);
        } catch (ServiceBusException e) {
            return ASBErrorCreator.fromASBException(e);
        } catch (Exception e) {
            return ASBErrorCreator.fromUnhandledException(e);
        }
    }

    private static BMap<BString, Object> constructQueuePropertiesArray(PagedIterable<QueueProperties> queueProp) {
        LinkedList<Object> queueList = new LinkedList<>();
        for (QueueProperties queueProperties : queueProp) {
            Map<String, Object> map = populateQueueOptionalFieldsMap(queueProperties);
            BMap<BString, Object> queuePropertiesRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                    ASBConstants.QUEUE_CREATED_RECORD, map);
            queueList.add(queuePropertiesRecord);
        }
        BMap<BString, Object> queueRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.QUEUE_CREATED_RECORD);
        ArrayType sourceArrayType = TypeCreator.createArrayType(TypeUtils.getType(queueRecord));
        Map<String, Object> map = new HashMap<>();
        map.put(ASBConstants.LIST_OF_QUEUES, ValueCreator.createArrayValue(queueList.toArray(), sourceArrayType));
        return createRecordValue(ModuleUtils.getModule(), ASBConstants.LIST_OF_QUEUES_RECORD, map);
    }

    /**
     * Delete a Queue in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param queueName           name of the Queue
     * @return null
     */
    public static Object deleteQueue(BObject administratorClient, BString queueName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        try {
            clientEp.deleteQueue(queueName.toString());
            LOGGER.debug("Deleted queue successfully with name: " + queueName);
            return null;
        } catch (BError e) {
            return ASBErrorCreator.fromBError(e);
        } catch (ServiceBusException e) {
            return ASBErrorCreator.fromASBException(e);
        } catch (Exception e) {
            return ASBErrorCreator.fromUnhandledException(e);
        }
    }

    /**
     * Check whether a Queue exists in Azure Service Bus.
     *
     * @param administratorClient Azure Service Bus Administrator Client
     * @param queueName           name of the Queue
     * @return null
     */
    public static Object queueExists(BObject administratorClient, BString queueName) {
        ServiceBusAdministrationClient clientEp = getAdminFromBObject(administratorClient);
        try {
            return clientEp.getQueueExists(queueName.toString());
        } catch (HttpResponseException e) {
            if (e.getResponse().getStatusCode() == 404) {
                return false;
            }
            return ASBErrorCreator.fromASBHttpResponseException(e);
        } catch (BError e) {
            return ASBErrorCreator.fromBError(e);
        } catch (ServiceBusException e) {
            return ASBErrorCreator.fromASBException(e);
        } catch (Exception e) {
            return ASBErrorCreator.fromUnhandledException(e);
        }
    }

    private static BMap<BString, Object> constructRuleCreatedRecord(RuleProperties properties) {
        Map<String, Object> map = populateRuleOptionalFieldsMap(properties);
        return ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.CREATED_RULE_RECORD, map);
    }

    private static BMap<BString, Object> constructQueueCreatedRecord(QueueProperties properties) {
        Map<String, Object> map = populateQueueOptionalFieldsMap(properties);
        return ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.QUEUE_CREATED_RECORD, map);
    }

    private static Map<String, Object> populateTopicOptionalFieldsMap(TopicProperties properties) {
        Map<String, Object> map = new HashMap<>();
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_NAME,
                StringUtils.fromString(properties.getName()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_STATUS,
                StringUtils.fromString(properties.getStatus().toString()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_USER_METADATA,
                StringUtils.fromString(properties.getUserMetadata()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_AUTO_DELETE_ON_IDLE,
                fromDuration(properties.getAutoDeleteOnIdle()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_DEFAULT_MESSAGE_TIME_TO_LIVE,
                fromDuration(properties.getDefaultMessageTimeToLive()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_DUPLICATE_DETECTION_HISTORY_TIME_WINDOW,
                fromDuration(properties.getDuplicateDetectionHistoryTimeWindow()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_ENABLE_BATCHED_OPERATIONS,
                properties.isBatchedOperationsEnabled());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_ENABLE_PARTITIONING,
                properties.isPartitioningEnabled());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_SUPPORT_ORDERING,
                properties.isOrderingSupported());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_REQUIRES_DUPLICATE_DETECTION,
                properties.isDuplicateDetectionRequired());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_MAX_SIZE_IN_MEGABYTES,
                properties.getMaxSizeInMegabytes());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_MAX_MESSAGE_SIZE_IN_KILOBYTES,
                properties.getMaxMessageSizeInKilobytes());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_TOPIC_RECORD_FIELD_AUTHORIZATION_RULES,
                constructAuthorizationRuleArray(properties.getAuthorizationRules()));

        return map;
    }

    private static Map<String, Object> populateRuleOptionalFieldsMap(RuleProperties properties) {
        Map<String, Object> map = new HashMap<>();
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_RULE_RECORD_FIELD_NAME,
                StringUtils.fromString(properties.getName()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_RULE_RECORD_FIELD_TYPE_NAME,
                formRule(properties.getAction(), properties.getFilter()));
        return map;
    }

    private static BMap<BString, Object> constructSubscriptionCreatedRecord(SubscriptionProperties properties) {
        Map<String, Object> map = populateSubscriptionOptionalFieldsMap(properties);
        return ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.SUBSCRIPTION_CREATED_RECORD, map);
    }

    private static BMap<BString, Object> constructTopicCreatedRecord(TopicProperties properties) {
        Map<String, Object> map = populateTopicOptionalFieldsMap(properties);
        return ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.TOPIC_CREATED_RECORD, map);
    }

    private static Map<String, Object> populateSubscriptionOptionalFieldsMap(SubscriptionProperties properties) {
        Map<String, Object> map = new HashMap<>();
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_AUTO_DELETE_ON_IDLE,
                fromDuration(properties.getAutoDeleteOnIdle()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_DEFAULT_MESSAGE_TIME_TO_LIVE,
                fromDuration(properties.getDefaultMessageTimeToLive()));
        ASBUtils.addFieldIfPresent(map,
                ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_FORWARD_DEAD_LETTERED_MESSAGES_TO,
                StringUtils.fromString(properties.getForwardDeadLetteredMessagesTo()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_FORWARD_TO,
                StringUtils.fromString(properties.getForwardTo()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_LOCK_DURATION,
                fromDuration(properties.getLockDuration()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_MAX_DELIVERY_COUNT,
                properties.getMaxDeliveryCount());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_STATUS,
                StringUtils.fromString(properties.getStatus().toString()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_SUBSCRIPTION_NAME,
                StringUtils.fromString(properties.getSubscriptionName()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_TOPIC_NAME,
                StringUtils.fromString(properties.getTopicName()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_USER_METADATA,
                StringUtils.fromString(properties.getUserMetadata()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_ENABLE_BATCHED_OPERATIONS,
                properties.isBatchedOperationsEnabled());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_SESSION_REQUIRED,
                properties.isSessionRequired());
        ASBUtils.addFieldIfPresent(map,
                ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_DEAD_LETTERING_ON_MESSAGE_EXPIRATION,
                properties.isDeadLetteringOnMessageExpiration());
        ASBUtils.addFieldIfPresent(map,
                ASBConstants.CREATED_SUBSCRIPTION_RECORD_FIELD_DEAD_LETTERING_ON_FILTER_EVALUATION_EXCEPTIONS,
                properties.isDeadLetteringOnFilterEvaluationExceptions());
        return map;
    }

    private static Map<String, Object> populateQueueOptionalFieldsMap(QueueProperties properties) {
        Map<String, Object> map = new HashMap<>();
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_NAME,
                StringUtils.fromString(properties.getName()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_STATUS,
                StringUtils.fromString(properties.getStatus().toString()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_USER_METADATA,
                StringUtils.fromString(properties.getUserMetadata()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_FORWARD_DEAD_LETTERED_MESSAGES_TO,
                StringUtils.fromString(properties.getForwardDeadLetteredMessagesTo()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_FORWARD_TO,
                StringUtils.fromString(properties.getForwardTo()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTO_DELETE_ON_IDLE,
                fromDuration(properties.getAutoDeleteOnIdle()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_DEFAULT_MESSAGE_TIME_TO_LIVE,
                fromDuration(properties.getDefaultMessageTimeToLive()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_DUPLICATE_DETECTION_HISTORY_TIME_WINDOW,
                fromDuration(properties.getDuplicateDetectionHistoryTimeWindow()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_REQUIRES_DUPLICATE_DETECTION,
                properties.isDuplicateDetectionRequired());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_ENABLE_BATCHED_OPERATIONS,
                properties.isBatchedOperationsEnabled());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_ENABLE_PARTITIONING,
                properties.isPartitioningEnabled());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_MAX_SIZE_IN_MEGABYTES,
                properties.getMaxSizeInMegabytes());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_MAX_MESSAGE_SIZE_IN_KILOBYTES,
                properties.getMaxMessageSizeInKilobytes());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULE,
                constructAuthorizationRuleArray(properties.getAuthorizationRules()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_MAX_DELIVERY_COUNT,
                properties.getMaxDeliveryCount());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_REQUIRE_SESSION,
                properties.isSessionRequired());
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_LOCK_DURATION,
                fromDuration(properties.getLockDuration()));
        ASBUtils.addFieldIfPresent(map, ASBConstants.CREATED_QUEUE_RECORD_FIELD_DEAD_LETTERING_ON_MESSAGE_EXPIRATION,
                properties.isDeadLetteringOnMessageExpiration());
        return map;
    }

    private static ServiceBusAdministrationClient getAdminFromBObject(BObject adminObject) {
        return (ServiceBusAdministrationClient) adminObject.getNativeData(ASBConstants.ADMINISTRATOR_CLIENT);
    }

    private static BMap<BString, Object> fromDuration(Duration duration) {
        BMap<BString, Object> durationRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.CREATED_QUEUE_RECORD_FIELD_DURATION);
        durationRecord.put(StringUtils.fromString(ASBConstants.CREATED_QUEUE_RECORD_FIELD_DURATION_SECONDS),
                duration.getSeconds());
        durationRecord.put(StringUtils.fromString(ASBConstants.CREATED_QUEUE_RECORD_FIELD_DURATION_NANOSECONDS),
                duration.getNano());
        return durationRecord;
    }

    private static BMap<BString, Object> formRule(RuleAction action, RuleFilter filter) {
        BMap<BString, Object> ruleRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.CREATED_RULE_RECORD_FIELD);
        if (action instanceof SqlRuleAction) {
            ruleRecord.put(StringUtils.fromString(ASBConstants.CREATED_RULE_RECORD_FIELD_ACTION),
                    StringUtils.fromString(((SqlRuleAction) action).getSqlExpression()));
        } else if (action instanceof EmptyRuleAction) {
            ruleRecord.put(StringUtils.fromString(ASBConstants.CREATED_RULE_RECORD_FIELD_ACTION),
                    StringUtils.fromString("EmptyRuleAction"));
        } else {
            ruleRecord.put(StringUtils.fromString(ASBConstants.CREATED_RULE_RECORD_FIELD_ACTION),
                    StringUtils.fromString(action.toString()));
        }
        if (filter instanceof SqlRuleFilter) {
            ruleRecord.put(StringUtils.fromString(ASBConstants.CREATED_RULE_RECORD_FIELD_FILTER),
                    StringUtils.fromString(((SqlRuleFilter) filter).getSqlExpression()));
        } else {
            ruleRecord.put(StringUtils.fromString(ASBConstants.CREATED_RULE_RECORD_FIELD_FILTER),
                    StringUtils.fromString(filter.toString()));
        }
        return ruleRecord;
    }

    private static BArray constructAuthorizationRuleArray(List<AuthorizationRule> authorizationRules) {
        BMap<BString, Object> authRulesRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULE);
        BArray authorizationRuleArray = ValueCreator.createArrayValue(
                TypeCreator.createArrayType(TypeUtils.getType(authRulesRecord)));
        for (AuthorizationRule authorizationRule : authorizationRules) {
            BMap<BString, Object> authorizationRuleRecord = ValueCreator.createRecordValue(ModuleUtils.getModule(),
                    ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULE);
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_CLAIM_TYPE),
                    StringUtils.fromString(authorizationRule.getClaimType()));
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_CLAIM_VALUE),
                    StringUtils.fromString(authorizationRule.getClaimValue()));
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_ACCESS_RIGHTS),
                    constructAccessRightsArray(authorizationRule.getAccessRights()));
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_KEY_NAME),
                    StringUtils.fromString(authorizationRule.getKeyName()));
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_MODIFIED_AT),
                    StringUtils.fromString(authorizationRule.getModifiedAt().toString()));
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_CREATED_AT),
                    StringUtils.fromString(authorizationRule.getCreatedAt().toString()));
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_PRIMARY_KEY),
                    StringUtils.fromString(authorizationRule.getPrimaryKey()));
            authorizationRuleRecord.put(StringUtils.fromString(
                            ASBConstants.CREATED_QUEUE_RECORD_FIELD_AUTHORIZATION_RULES_SECONDARY_KEY),
                    StringUtils.fromString(authorizationRule.getSecondaryKey()));
            authorizationRuleArray.append(authorizationRuleRecord);
        }
        return authorizationRuleArray;
    }

    private static void setClient(BObject administratorObject, ServiceBusAdministrationClient client) {
        administratorObject.addNativeData(ASBConstants.ADMINISTRATOR_CLIENT, client);
    }

    private static BArray constructAccessRightsArray(List<AccessRights> accessRights) {
        BArray accessRightsArray = ValueCreator.createArrayValue(
                TypeCreator.createArrayType(PredefinedTypes.TYPE_STRING));
        for (AccessRights accessRight : accessRights) {
            accessRightsArray.append(StringUtils.fromString(accessRight.toString()));
        }
        return accessRightsArray;
    }
}
