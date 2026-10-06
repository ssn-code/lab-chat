package labchat.service;

import labchat.model.Poll;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PollService {
    private final ConcurrentHashMap<String, Poll> polls = new ConcurrentHashMap<>();

    public Poll createPoll(String question, List<String> options, String creator) {
        if (question == null || question.isBlank() || options == null || options.size() < 2) {
            return null;
        }
        String id = UUID.randomUUID().toString();
        Poll poll = new Poll(id, question.trim(), options, creator);
        polls.put(id, poll);
        return poll;
    }

    public Poll getPoll(String id) {
        return polls.get(id);
    }

    public List<Poll> getAllPolls() {
        return new ArrayList<>(polls.values());
    }

    public boolean vote(String pollId, String username, int optionIndex) {
        Poll poll = polls.get(pollId);
        if (poll == null) return false;
        return poll.vote(username, optionIndex);
    }

    public boolean closePoll(String pollId, String username, boolean isAdmin) {
        Poll poll = polls.get(pollId);
        if (poll == null) return false;
        if (!isAdmin && !poll.getCreator().equalsIgnoreCase(username)) {
            return false;
        }
        poll.setClosed(true);
        return true;
    }
}
