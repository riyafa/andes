/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.andes.subscription;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.andes.amqp.AMQPUtils;
import org.wso2.andes.kernel.*;
import org.wso2.andes.kernel.SubscriptionListener.SubscriptionChange;
import org.wso2.andes.server.util.AndesUtils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class SubscriptionStore {
    private static final String TOPIC_PREFIX = "topic.";


    private static final String QUEUE_PREFIX = "queue.";


    private static Log log = LogFactory.getLog(SubscriptionStore.class);

    //<routing key, List of local subscriptions>
    //TODO: hasitha - wrap this list by a map to reduce cost
    private Map<String, List<AndesSubscription>> clusterTopicSubscriptionMap = new ConcurrentHashMap<String, List<AndesSubscription>>();
    private Map<String, List<AndesSubscription>> clusterQueueSubscriptionMap = new ConcurrentHashMap<String, List<AndesSubscription>>();

    //<destination, <subscriptionID,LocalSubscription>>
    private Map<String, Map<String, LocalSubscription>> localTopicSubscriptionMap = new ConcurrentHashMap<String, Map<String, LocalSubscription>>();
    private Map<String, Map<String, LocalSubscription>> localQueueSubscriptionMap = new ConcurrentHashMap<String, Map<String, LocalSubscription>>();


    private AndesContextStore andesContextStore;


    public SubscriptionStore() throws AndesException {

        andesContextStore = AndesContext.getInstance().getAndesContextStore();
    }

    /**
     * get all CLUSTER subscription entries subscribed for a queue/topic
     *
     * @param destination queue/topic name
     * @param isTopic     is requesting topic subscriptions
     * @return list of andes subscriptions
     * @throws AndesException
     */
    public List<AndesSubscription> getAllSubscribersForDestination(String destination, boolean isTopic) throws AndesException {
        return new ArrayList<AndesSubscription>(isTopic ? clusterTopicSubscriptionMap.get(destination) : clusterQueueSubscriptionMap.get(destination));
    }

    /**
     * get all CLUSTER queues/topics where subscriptions are available
     *
     * @param isTopic TRUE if checking topics
     * @return list of queues/topics
     */
    public List<String> getAllDestinationsOfSubscriptions(boolean isTopic) {
        return new ArrayList<String>(isTopic ? clusterTopicSubscriptionMap.keySet() : clusterQueueSubscriptionMap.keySet());
    }

    /**
     * get all (ACTIVE/INACTIVE) CLUSTER subscription entries subscribed for a queue/topic
     * hierarchical topic subscription mapping also happens here
     * @param destination queue/topic name
     * @param isTopic     TRUE if checking topics
     * @return list of andes subscriptions
     * @throws AndesException
     */
    public List<AndesSubscription> getActiveClusterSubscribersForDestination(String destination, boolean isTopic) throws AndesException {
        Map<String, List<AndesSubscription>> subMap = isTopic ? clusterTopicSubscriptionMap: clusterQueueSubscriptionMap;
        List<AndesSubscription> subscriptionsHavingExternalsubscriber = new ArrayList<AndesSubscription>();
        //TODO: get key entries
        for(String subDestination : subMap.keySet()) {
            if(AMQPUtils.isTargetQueueBoundByMatchingToRoutingKey(subDestination, destination)) {
                List<AndesSubscription> subscriptionsOfDestination = subMap.get(subDestination);
                if (subscriptionsOfDestination != null) {
                    for (AndesSubscription subscription : subscriptionsOfDestination) {
                        //if (subscription.hasExternalSubscriptions()) {
                            subscriptionsHavingExternalsubscriber.add(subscription);
                        //}
                    }
                }
            }
        }

        return subscriptionsHavingExternalsubscriber;
    }

    public List<AndesSubscription> getAllActiveClusterSubscriptions(boolean isTopic) throws AndesException {
        List<AndesSubscription> allActiveSubscriptions = new ArrayList<AndesSubscription>();
        Set<String> destinations = isTopic ? clusterTopicSubscriptionMap.keySet() : clusterQueueSubscriptionMap.keySet();
        for(String destination : destinations) {
           allActiveSubscriptions.addAll(getActiveClusterSubscribersForDestination(destination,isTopic));
        }
        return allActiveSubscriptions;
    }

    /**
     * get all ACTIVE LOCAL subscription entries subscribed for a destination/topic
     * Hierarchical topic mapping is NOT considered here
     * @param destination queue/topic name
     * @param isTopic     TRUE if checking topics
     * @return list of matching subscriptions
     */
    public Collection<LocalSubscription> getActiveLocalSubscribers(String destination, boolean isTopic) throws AndesException {
        Map<String, LocalSubscription> localSubscriptionMap =   getLocalSubscriptionMap(destination, isTopic);
        Collection<LocalSubscription> list = new ArrayList<LocalSubscription>();
        if(localSubscriptionMap != null) {
            list = getLocalSubscriptionMap(destination, isTopic).values();
        }
        Collection<LocalSubscription> activeLocalSubscriptionList = new ArrayList<LocalSubscription>();
        for (LocalSubscription localSubscription : list) {
            if (localSubscription.hasExternalSubscriptions()) {
                activeLocalSubscriptionList.add(localSubscription);
            }
        }
        return activeLocalSubscriptionList;
    }

    /**
     * get all ACTIVE LOCAL subscription entries subscribed for message destination/topic
     * Hierarchical topic mapping IS considered here
     * @param messageDest destination of the message (queue/topic name)
     * @param isTopic TRUE if checking topics
     * @return List of matching subscriptions
     */
    public List<LocalSubscription> getAllActiveSubscriptions4MsgDestination(String messageDest, boolean isTopic) {
        List<LocalSubscription> matchingDestinatins = new ArrayList<LocalSubscription>();
        Map<String, Map<String, LocalSubscription>> localSubscriptionMap = isTopic ? localTopicSubscriptionMap : localQueueSubscriptionMap;
        for (String destination : localSubscriptionMap.keySet()) {
            if(AMQPUtils.isTargetQueueBoundByMatchingToRoutingKey(destination,messageDest)) {
                Map<String, LocalSubscription> subMap = localSubscriptionMap.get(destination);
                for (String subID : subMap.keySet()) {
                    LocalSubscription sub = subMap.get(subID);
                    if (sub.hasExternalSubscriptions()) {
                        matchingDestinatins.add(sub);
                    }
                }
            }
        }

        return matchingDestinatins;

    }


    /**
     * Get all ACTIVE LOCAL subscription entries for destination (queue/topic)
     * hierarchical subscription mapping is NOT considered here
     * @param destination queue or topic name
     * @return list of matching subscriptions
     * @throws AndesException
     */
    public Collection<LocalSubscription> getActiveLocalSubscribersForQueuesAndTopics(String destination) throws AndesException {
        Collection<LocalSubscription> allSubscriptions = getActiveLocalSubscribers(destination, false);
        allSubscriptions.addAll(getActiveLocalSubscribers(destination, true));
        return allSubscriptions;
    }

    /**
     * Get local subscription given the subscribed destination and
     * channel subscription use to send messages
     * @param channelID  id of the channel subscriber deals with
     * @param messageDestination  destination of subscription
     * @param isTopic True if searching for topic subscriptions
     * @return subscription object. Null if no match
     * @throws AndesException
     */
    public LocalSubscription getLocalSubscriptionForChannelId(UUID channelID,
                                                              String messageDestination, boolean isTopic)

            throws AndesException {
        List<LocalSubscription> activeLocalSubscriptions =
                getAllActiveSubscriptions4MsgDestination(
                        messageDestination, isTopic);
        for (LocalSubscription sub : activeLocalSubscriptions) {
            if (sub.getChannelID().equals(channelID)) {
                return sub;
            }
        }
        return null;
    }


    public int numberOfSubscriptionsForDestinationAtNode(String destination, String nodeID) throws AndesException {
        String requestedNodeQueue = AndesUtils.getNodeQueueNameForNodeId(nodeID);
        List<AndesSubscription> subscriptions = getActiveClusterSubscribersForDestination(destination, false);
        int count = 0;
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (AndesSubscription sub : subscriptions) {
                if (sub.getSubscribedNode().equals(requestedNodeQueue)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * get all ACTIVE CLUSTER subscription entries subscribed on a given node
     *
     * @param nodeID  id of the broker node
     * @param isTopic TRUE if checking topics
     * @return list of subscriptions
     */
    public List<AndesSubscription> getActiveClusterSubscribersForNode(String nodeID, boolean isTopic) {
        List<AndesSubscription> activeQueueSubscriptions = new ArrayList<AndesSubscription>();
        String nodeQueueNameForNode = AndesUtils.getNodeQueueNameForNodeId(nodeID);
        Map<String, List<AndesSubscription>> clusterSubscriptionMap = isTopic ? clusterTopicSubscriptionMap : clusterQueueSubscriptionMap;
        for (String destination : clusterSubscriptionMap.keySet()) {
            List<AndesSubscription> subList = clusterSubscriptionMap.get(destination);
            for (AndesSubscription sub : subList) {
                if (sub.getSubscribedNode().equals(nodeQueueNameForNode) && sub.hasExternalSubscriptions()) {
                    activeQueueSubscriptions.add(sub);
                }
            }
        }

        return activeQueueSubscriptions;
    }

    /**
     * get all ACTIVE LOCAL subscriptions for any queue/topic
     *
     * @param isTopic TRUE if checking topics
     * @return list of Local subscriptions
     */
    public List<LocalSubscription> getActiveLocalSubscribers(boolean isTopic) {
        List<LocalSubscription> activeQueueSubscriptions = new ArrayList<LocalSubscription>();
        Map<String, Map<String, LocalSubscription>> localSubscriptionMap = isTopic ? localTopicSubscriptionMap : localQueueSubscriptionMap;
        for (String destination : localSubscriptionMap.keySet()) {
            Map<String, LocalSubscription> subMap = localSubscriptionMap.get(destination);
            for (String subID : subMap.keySet()) {
                LocalSubscription sub = subMap.get(subID);
                if (sub.hasExternalSubscriptions()) {
                    activeQueueSubscriptions.add(sub);
                }
            }
        }

        return activeQueueSubscriptions;
    }

    /**
     * get all ACTIVE LOCAL temporary(non-durable) subscriptions for any queue/topic
     *
     * @param isTopic TRUE if checking topics
     * @return list of local subscriptions
     */
    public List<LocalSubscription> getActiveNonDurableLocalSubscribers(boolean isTopic) {
        List<LocalSubscription> activeNonDurableLocalSubscriptions = new ArrayList<LocalSubscription>();
        List<LocalSubscription> activeLocalSubscriptions = getActiveLocalSubscribers(isTopic);
        for (LocalSubscription sub : activeLocalSubscriptions) {
            if (!sub.isDurable()) {
                activeNonDurableLocalSubscriptions.add(sub);
            }
        }
        return activeNonDurableLocalSubscriptions;
    }

    /**
     * get number of active subscribers for queue/topic in CLUSTER
     *
     * @param destination queue/topic name
     * @param isTopic     TRUE if checking topics
     * @return number of subscriptions in cluster
     * @throws AndesException
     */
    public int numberOfSubscriptionsInCluster(String destination, boolean isTopic) throws AndesException {
        return getActiveClusterSubscribersForDestination(destination, isTopic).size();
    }

    /**
     * get a copy of local subscription list for a given queue/topic
     * hierarchical topic subscription mapping is NOT considered here
     *
     * @param destination queue/topic name
     * @param isTopic     TRUE if checking topics
     * @return a map of <SubID,subscription>
     */
    public Map<String, LocalSubscription> getLocalSubscriptionMap(String destination,
                                                                  boolean isTopic) {
        Map<String, Map<String, LocalSubscription>> subscriptionMap = isTopic ? localTopicSubscriptionMap : localQueueSubscriptionMap;
        return subscriptionMap.get(destination);
    }

    /**
     * get all (active/inactive) CLUSTER subscriptions for a queue/topic
     *
     * @param destination queue/topic name
     * @param isTopic     TRUE if checking topics
     * @return list of subscriptions
     */
    public List<AndesSubscription> getClusterSubscriptionList(String destination, boolean isTopic) {
        Map<String, List<AndesSubscription>> subscriptionMap = isTopic ? clusterTopicSubscriptionMap : clusterQueueSubscriptionMap;
        return subscriptionMap.get(destination);
    }

    /**
     * get all ACTIVE CLUSTER subscriptions for a queue/topic. For topics this will return
     * subscriptions whose destination is exactly matching to the given destination only.
     * (hierarchical mapping not considered)
     * @param destination queue or topic name
     * @param isTopic is destination a topic
     * @return list of matching subscriptions
     */
    public List<AndesSubscription> getActiveClusterSubscriptionList(String destination, boolean isTopic) {
        List<AndesSubscription> activeSubscriptions = new ArrayList<AndesSubscription>();
        List<AndesSubscription> allSubscriptions = getClusterSubscriptionList(destination, isTopic);
        for(AndesSubscription sub : allSubscriptions) {
            if(sub.hasExternalSubscriptions()) {
               activeSubscriptions.add(sub);
            }
        }
        return activeSubscriptions;
    }

    /**
     * replace the whole CLUSTER subscription list for a given queue/topic
     *
     * @param destination queue/topic name
     * @param newSubList  new subscription list
     * @param isTopic     TRUE if checking topics
     * @return old CLUSTER subscription list
     */
    public List<AndesSubscription> replaceClusterSubscriptionListOfDestination(String destination, List<AndesSubscription> newSubList, boolean isTopic) {
        Map<String, List<AndesSubscription>> subscriptionMap = isTopic ? clusterTopicSubscriptionMap : clusterQueueSubscriptionMap;
        List<AndesSubscription> oldSubscriptionList = subscriptionMap.put(destination, newSubList);
        if (oldSubscriptionList != null) {
            return new ArrayList<AndesSubscription>(oldSubscriptionList);
        } else {
            return new ArrayList<AndesSubscription>();
        }
    }

    /**
     * Get ALL (ACTIVE + INACTIVE) local subscriptions whose bound queue is given
     * @param queueName Queue name to search
     * @return  List if matching subscriptions
     * @throws AndesException
     */
    public List<LocalSubscription> getListOfSubscriptionsBoundToQueue(String queueName) throws AndesException{
        List<LocalSubscription> subscriptionsOfQueue = new ArrayList<LocalSubscription>();
        Map<String, LocalSubscription> queueSubscriptionMap =  localQueueSubscriptionMap.get(queueName);
        if(queueSubscriptionMap != null) {
            subscriptionsOfQueue.addAll(queueSubscriptionMap.values());
        }
        Map<String, Map<String, LocalSubscription>> topicSubscriptionMap  =  localTopicSubscriptionMap;
        for(String destination : topicSubscriptionMap.keySet()) {
            Map<String, LocalSubscription> topicSubsOfDest = topicSubscriptionMap.get(destination);
            if(topicSubsOfDest != null) {
                for(String subID : topicSubsOfDest.keySet()) {
                    LocalSubscription sub = topicSubsOfDest.get(subID);
                    if(sub.getTargetQueue().equals(queueName)) {
                        subscriptionsOfQueue.add(sub);
                    }
                }
            }
        }
        return subscriptionsOfQueue;
    }

    /**
     * get a List of node queues having subscriptions to the given destination queue
     *
     * @param queueName destination queue name
     * @return list of node queue names
     */
    public Set<String> getNodeQueuesHavingSubscriptionsForQueue(String queueName) throws AndesException {
        List<AndesSubscription> nodesHavingSubscriptions4Queue = getActiveClusterSubscribersForDestination(queueName, false);
        HashSet<String> nodes = new HashSet<String>();
        for (AndesSubscription subscrption : nodesHavingSubscriptions4Queue) {
            nodes.add(subscrption.getSubscribedNode());
        }
        return nodes;
    }

    /**
     * get a List of nodes queues having subscriptions to the given topic
     *
     * @param topicName topic name
     * @return list of node queues
     * @throws AndesException
     */
    public Set<String> getNodeQueuesHavingSubscriptionsForTopic(String topicName) throws AndesException {
        List<AndesSubscription> nodesHavingSubscriptions4Topic = getActiveClusterSubscribersForDestination(topicName, true);
        HashSet<String> nodes = new HashSet<String>();
        for (AndesSubscription subscrption : nodesHavingSubscriptions4Topic) {
            nodes.add(subscrption.getSubscribedNode());
        }
        return nodes;
    }

    /**
     * get subscriptions of a particular node queue
     *
     * @param nodeQueueName    node queue name
     * @param subscriptionList list of subscriptions to evaluate
     * @return list of subscriptions filtered
     */
    private List<AndesSubscription> getSubscriptionsOfNode(String nodeQueueName, List<AndesSubscription> subscriptionList) {
        List<AndesSubscription> subscriptionsOfNode = new ArrayList<AndesSubscription>();
        for (AndesSubscription sub : subscriptionList) {
            if (sub.getSubscribedNode().equals(nodeQueueName)) {
                subscriptionsOfNode.add(sub);
            }
        }
        return subscriptionsOfNode;
    }

    /**
     * get a map of <nodeID,count> map of subscription counts
     *
     * @param destination queue/topic name
     * @param isTopic     TRUE if checking topics
     * @return Map of subscription counts
     * @throws AndesException
     */
    public Map<String, Integer> getSubscriptionCountInformation(String destination, boolean isTopic) throws AndesException {

        Map<String, Integer> nodeSubscriptionCountMap = new HashMap<String, Integer>();
        List<AndesSubscription> subscriptions = getActiveClusterSubscribersForDestination(destination, isTopic);
        for (AndesSubscription sub : subscriptions) {
            Integer count = nodeSubscriptionCountMap.get(sub.getSubscribedNode());
            if (count == null) {
                nodeSubscriptionCountMap.put(sub.getSubscribedNode(), 1);
            } else {
                nodeSubscriptionCountMap.put(sub.getSubscribedNode(), count + 1);
            }
        }
        return nodeSubscriptionCountMap;
    }

    /**
     * create disconnect or remove a cluster subscription entry
     *
     * @param subscription subscription to add disconnect or remove
     * @param type         type of change
     */
    public synchronized void createDisconnectOrRemoveClusterSubscription(AndesSubscription subscription, SubscriptionChange type) throws AndesException{

        boolean isTopic = subscription.isBoundToTopic();
        Map<String, List<AndesSubscription>> clusterSubscriptionMap = isTopic ? clusterTopicSubscriptionMap : clusterQueueSubscriptionMap;
        String destination = subscription.getSubscribedDestination();
        List<AndesSubscription> subscriptionList = clusterSubscriptionMap.get(destination);

        if (type == SubscriptionChange.Added) {
            if (subscriptionList != null) {
                //iterate and remove all similar subscriptions
                //TODO: hasitha - wrap this list by a map to reduce cost
                Iterator itr = subscriptionList.iterator();
                while (itr.hasNext()) {
                    AndesSubscription sub = (AndesSubscription) itr.next();
                    if (sub.equals(subscription)) {
                        itr.remove();
                    }
                }
                subscriptionList.add(subscription);

            } else {
                subscriptionList = new ArrayList<AndesSubscription>();
                subscriptionList.add(subscription);
                clusterSubscriptionMap.put(destination, subscriptionList);
            }
            log.debug("Added Subscription to map. queue name:" + subscription.getTargetQueue() + ", Type: " + subscription.getTargetQueueBoundExchangeType());

        } else if (type == SubscriptionChange.Disconnected) {
            if (subscriptionList == null) {
                subscriptionList = new ArrayList<AndesSubscription>();
            }
            Iterator itr = subscriptionList.iterator();
            while (itr.hasNext()) {
                AndesSubscription sub = (AndesSubscription) itr.next();
                if (sub.equals(subscription)) {
                    itr.remove();
                    break;
                }
            }
            subscriptionList.add(subscription);
            clusterSubscriptionMap.put(destination, subscriptionList);

            log.debug("Disconnected Subscription from map: " + subscription.encodeAsStr());

        } else if (type == SubscriptionChange.Deleted) {
            if (subscriptionList == null) {
                subscriptionList = new ArrayList<AndesSubscription>();
            }
            Iterator itr = subscriptionList.iterator();
            while (itr.hasNext()) {
                AndesSubscription sub = (AndesSubscription) itr.next();
                if (sub.equals(subscription)) {
                    itr.remove();
                    break;
                }
            }
            if (subscriptionList.size() == 0) {
                clusterSubscriptionMap.remove(destination);
            }
            log.debug("Deleted Subscription from map. queue name:" + subscription.getTargetQueue() + ", Type: " + subscription.getTargetQueueBoundExchangeType());
        }

        log.debug("+++++++++++++++++Updated cluster subscription maps++++++++++++++++");
        this.printSubscriptionMap(clusterQueueSubscriptionMap);
        this.printSubscriptionMap(clusterTopicSubscriptionMap);
        log.debug("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }


    private void printSubscriptionMap(Map<String, List<AndesSubscription>> map) {
        for (Entry<String, List<AndesSubscription>> entry : map.entrySet()) {
            log.debug("Destination: " + entry.getKey());
            for (AndesSubscription s : entry.getValue()) {
                log.debug("\t---" + s.encodeAsStr());
            }
        }
    }

    private void printLocalSubscriptionMap(Map<String, Map<String, LocalSubscription>> map) {
        for (Entry<String, Map<String, LocalSubscription>> entry : map.entrySet()) {
            log.debug("Destination: " + entry.getKey());
            Map<String, LocalSubscription> mapForDestination = entry.getValue();
            for (Entry<String, LocalSubscription> sub : mapForDestination.entrySet()) {
                log.debug("\t SubID: " + sub.getKey() + "-----" + sub.getValue().encodeAsStr());
            }
        }
    }

    /**
     * Create,disconnect or remove local subscription
     *
     * @param subscription subscription to add/disconnect or remove
     * @param type         type of change
     * @throws AndesException
     */
    public synchronized void createDisconnectOrRemoveLocalSubscription(LocalSubscription subscription, SubscriptionChange type) throws AndesException {
        //We need to handle durable topic subscriptions
        boolean hasDurableSubscriptionAlreadyInPlace = false;
        if (subscription.isBoundToTopic() && subscription.isDurable()) {
            /**
             * Check if an active durable topic subscription already in place. If so we should not accept the subscription
             */
            //scan all the destinations as the subscription can come for different topic
            for (String destination : clusterTopicSubscriptionMap.keySet()) {
                List<AndesSubscription> existingSubscriptions = clusterTopicSubscriptionMap.get(destination);
                if (existingSubscriptions != null && !existingSubscriptions.isEmpty()) {
                    for (AndesSubscription sub : existingSubscriptions) {
                        //queue is durable
                        if (sub.isDurable() &&
                                //target queues are matched
                                sub.getTargetQueue().equals(subscription.getTargetQueue()) &&
                                //target queue has a active subscriber
                                sub.hasExternalSubscriptions()) {
                            hasDurableSubscriptionAlreadyInPlace = true;
                            break;
                        }
                    }
                }
                if (hasDurableSubscriptionAlreadyInPlace) {
                    break;
                }
            }


            if (!hasDurableSubscriptionAlreadyInPlace && type == SubscriptionChange.Disconnected) {
                throw new AndesException("There is no active subscriber to close subscribed to " + subscription.getSubscribedDestination() + " with the queue " + subscription.getTargetQueue());
            } else if (hasDurableSubscriptionAlreadyInPlace && type == SubscriptionChange.Added) {
                //not permitted
                throw new AndesException("A subscription already exists for Durable subscriptions on " + subscription.getSubscribedDestination() + " with the queue " + subscription.getTargetQueue());
            }

        }

        if (type == SubscriptionChange.Added || type == SubscriptionChange.Disconnected) {

            String destinationQueue = subscription.getSubscribedDestination();
            //Store the subscription
            String destinationIdentifier = (subscription.isBoundToTopic() ? TOPIC_PREFIX : QUEUE_PREFIX) + destinationQueue;
            String subscriptionID = subscription.getSubscribedNode() + "_" + subscription.getSubscriptionID();
            andesContextStore.storeDurableSubscription(destinationIdentifier, subscriptionID, subscription.encodeAsStr());

            if (type == SubscriptionChange.Added) {
                log.info("New Local Subscription Added " + subscription.toString());
            } else {
                log.info("New Local Subscription Disconnected " + subscription.toString());
            }

            //add or update local subscription map
            if (subscription.getTargetQueueBoundExchangeName().equals(AMQPUtils.DIRECT_EXCHANGE_NAME)) {
                Map<String, LocalSubscription> localSubscriptions = localQueueSubscriptionMap.get(destinationQueue);
                if (localSubscriptions == null) {
                    localSubscriptions = new ConcurrentHashMap<String, LocalSubscription>();
                }
                localSubscriptions.put(subscriptionID, subscription);
                localQueueSubscriptionMap.put(destinationQueue, localSubscriptions);

            } else if (subscription.getTargetQueueBoundExchangeName().equals(AMQPUtils.TOPIC_EXCHANGE_NAME)) {
                Map<String, LocalSubscription> localSubscriptions = localTopicSubscriptionMap.get(destinationQueue);
                if (localSubscriptions == null) {
                    localSubscriptions = new ConcurrentHashMap<String, LocalSubscription>();
                }
                localSubscriptions.put(subscriptionID, subscription);
                localTopicSubscriptionMap.put(destinationQueue, localSubscriptions);
            }

        } else if (type == SubscriptionChange.Deleted) {
            removeLocalSubscription(subscription);
            log.info("Local Subscription Removed " + subscription.toString());
        }

        log.debug("===============Updated local subscription maps================");
        this.printLocalSubscriptionMap(localQueueSubscriptionMap);
        this.printLocalSubscriptionMap(localTopicSubscriptionMap);
        log.debug("========================================================");

    }

    private LocalSubscription removeLocalSubscription(AndesSubscription subscription) throws AndesException {
        String destination = subscription.getSubscribedDestination();
        String subscriptionID = subscription.getSubscriptionID();
        LocalSubscription subscriptionToRemove = null;
        //check queue local subscriptions
        Map<String, LocalSubscription> subscriptionList = getLocalSubscriptionMap(destination,false);
        if(subscriptionList != null) {
            Iterator<LocalSubscription> iterator = subscriptionList.values().iterator();
            while (iterator.hasNext()) {
                LocalSubscription currentSubscription = iterator.next();
                if (currentSubscription.equals(subscription)) {
                    subscriptionToRemove = currentSubscription;
                    iterator.remove();
                    break;
                }
            }
            if (subscriptionList.isEmpty()) {
                localQueueSubscriptionMap.remove(destination);
            }
        }

        //check topic local subscriptions
        if (subscriptionToRemove == null) {
            subscriptionList = getLocalSubscriptionMap(destination, true);
            if(subscriptionList != null) {
                Iterator<LocalSubscription> iterator = subscriptionList.values().iterator();
                while (iterator.hasNext()) {
                    LocalSubscription currentSubscription = iterator.next();
                    if (currentSubscription.equals(subscription)) {
                        subscriptionToRemove = currentSubscription;
                        iterator.remove();
                        break;
                    }
                }
                if (subscriptionList.isEmpty()) {
                    localTopicSubscriptionMap.remove(destination);
                }
            }
        }

        if (subscriptionToRemove != null) {
            String destinationIdentifier = new StringBuffer().append((subscriptionToRemove.isBoundToTopic() ? TOPIC_PREFIX : QUEUE_PREFIX))
                    .append(destination).toString();
            andesContextStore.removeDurableSubscription(destinationIdentifier, subscription.getSubscribedNode() + "_" + subscriptionID);
            log.debug("Subscription Removed Locally for  " + destination + "@" + subscriptionID + " " + subscriptionToRemove);
        } else {
            throw new AndesException("Could not find an subscription ID " + subscriptionID + " under destination " + destination);
        }
        return subscriptionToRemove;
    }

    /**
     * @return list of ACTIVE and INACTIVE topics in cluster
     */
    public List<String> getTopics() {
        return new ArrayList<String>(clusterTopicSubscriptionMap.keySet());
    }
}
