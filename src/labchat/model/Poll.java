package labchat.model;

import labchat.util.JsonUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class Poll {
    private final String id;
    private final String question;
    private final List<String> options;
    private final String creator;
    private volatile boolean closed;
    // username -> optionIndex
    private final ConcurrentHashMap<String, Integer> votes = new ConcurrentHashMap<>();

    public Poll(String id, String question, List<String> options, String creator) {
        this.id = id;
        this.question = question;
        this.options = new ArrayList<>(options);
        this.creator = creator;
        this.closed = false;
    }

    public String getId() { return id; }
    public String getQuestion() { return question; }
    public List<String> getOptions() { return Collections.unmodifiableList(options); }
    public String getCreator() { return creator; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public boolean vote(String username, int optionIndex) {
        if (closed || optionIndex < 0 || optionIndex >= options.size()) {
            return false;
        }
        votes.put(username, optionIndex);
        return true;
    }

    public String toJson() {
        int[] counts = new int[options.size()];
        for (int opt : votes.values()) {
            if (opt >= 0 && opt < counts.length) {
                counts[opt]++;
            }
        }

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(JsonUtil.escape(id)).append("\",");
        sb.append("\"question\":\"").append(JsonUtil.escape(question)).append("\",");
        sb.append("\"creator\":\"").append(JsonUtil.escape(creator)).append("\",");
        sb.append("\"closed\":").append(closed).append(",");
        sb.append("\"totalVotes\":").append(votes.size()).append(",");

        sb.append("\"options\":[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"text\":\"").append(JsonUtil.escape(options.get(i))).append("\",");
            sb.append("\"votes\":").append(counts[i]);
            sb.append("}");
        }
        sb.append("],");

        sb.append("\"userVotes\":{");
        boolean first = true;
        for (var entry : votes.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(JsonUtil.escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }
}
