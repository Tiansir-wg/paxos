package com.maximilianmichels.paxos;

import akka.actor.ActorRef;
import akka.actor.Cancellable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class Proposer extends Actor {

    private final ActorRef[] acceptors;

    private long value;
    private long proposalNo;

    private List<Messages.Promise> promises;
    private ActorRef client;
    private Cancellable timeoutScheduler;

    Proposer(int idx, ActorRef[] acceptors) {
        super(idx);
        this.acceptors = acceptors;
        this.promises = new ArrayList<>();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Messages.Request.class,
                        (req) -> {
                            LOG("Received " + req);
                            value = req.value;
                            proposalNo++;
                            promises.clear();
                            client = sender();
                            for (ActorRef acceptor : acceptors) {
                                acceptor.tell(new Messages.Prepare(proposalNo), self());
                            }
                            timeoutScheduler = context().system().scheduler().scheduleOnce(
                                    Duration.ofSeconds(5), self(), req, context().dispatcher(), sender());
                        })
                .match(Messages.Promise.class,
                        (promise) -> {
                            LOG("Received " + promise);
                            if (promise.proposalNo == proposalNo) {
                                promises.add(promise);
                                sendAcceptIfQuorumNumberOfResponses();
                            } else if (promise.previousProposalNo > proposalNo) {
                                proposalNo = promise.previousProposalNo;
                                value = promise.previousProposalNo;
                            }
                        })
                .build();
    }

    private void sendAcceptIfQuorumNumberOfResponses() {
        if (promises.size() >= acceptors.length / 2 + 1) {
            LOG("I'm the leader");
            timeoutScheduler.cancel();
            for (ActorRef acceptor : acceptors) {
                // send to all acceptors, the ones who didn't promise simply ignore it
                acceptor.tell(new Messages.Accept(proposalNo, value, client), self());
            }

            proposalNo++;
        }
    }
}
