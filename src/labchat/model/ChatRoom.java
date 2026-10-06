package labchat.model;

import labchat.util.JsonUtil;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatRoom {
    private final String name;
    private final String creator;
    private final boolean defaultRoom;
    private final ConcurrentHashMap<String, Boolean> members = new ConcurrentHashMap<>();

    public ChatRoom(String name, String creator, boolean defaultRoom) {
        this.name = name;
        this.creator = creator;
        this.defaultRoom = defaultRoom;
    }

    public String getName() { return name; }
    public String getCreator() { return creator; }
    public boolean isDefaultRoom() { return defaultRoom; }

    public void addMember(String username) {
        members.put(username, Boolean.TRUE);
    }

    public void removeMember(String username) {
        members.remove(username);
    }

    public int getMemberCount() {
        return members.size();
    }

    public String toJson() {
        return "{" +
                "\"name\":\"" + JsonUtil.escape(name) + "\"," +
                "\"creator\":\"" + JsonUtil.escape(creator) + "\"," +
                "\"defaultRoom\":" + defaultRoom + "," +
                "\"memberCount\":" + getMemberCount() +
                "}";
    }
}
