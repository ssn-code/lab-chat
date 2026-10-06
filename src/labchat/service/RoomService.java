package labchat.service;

import labchat.model.ChatRoom;
import labchat.util.ValidationUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomService {
    private final ConcurrentHashMap<String, ChatRoom> rooms = new ConcurrentHashMap<>();

    public RoomService() {
        createDefaultRoom("#general");
        createDefaultRoom("#lab");
        createDefaultRoom("#random");
        createDefaultRoom("#projects");
        createDefaultRoom("#help");
    }

    private void createDefaultRoom(String name) {
        rooms.put(name, new ChatRoom(name, "system", true));
    }

    public List<ChatRoom> getAllRooms() {
        List<ChatRoom> list = new ArrayList<>(rooms.values());
        list.sort((a, b) -> {
            if (a.isDefaultRoom() && !b.isDefaultRoom()) return -1;
            if (!a.isDefaultRoom() && b.isDefaultRoom()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return list;
    }

    public ChatRoom getRoom(String name) {
        if (name == null) return null;
        return rooms.get(name);
    }

    public synchronized boolean createRoom(String name, String creator) {
        String problem = ValidationUtil.validateRoomName(name);
        if (problem != null) return false;
        String formatted = name.startsWith("#") ? name : "#" + name;
        if (rooms.containsKey(formatted)) return false;

        ChatRoom r = new ChatRoom(formatted, creator, false);
        r.addMember(creator);
        rooms.put(formatted, r);
        return true;
    }

    public synchronized boolean deleteRoom(String name, String requester, boolean isAdmin) {
        if (name == null) return false;
        ChatRoom room = rooms.get(name);
        if (room == null || room.isDefaultRoom()) return false;
        if (isAdmin || room.getCreator().equalsIgnoreCase(requester)) {
            rooms.remove(name);
            return true;
        }
        return false;
    }

    public boolean joinRoom(String name, String username) {
        ChatRoom room = rooms.get(name);
        if (room == null) return false;
        room.addMember(username);
        return true;
    }

    public boolean leaveRoom(String name, String username) {
        ChatRoom room = rooms.get(name);
        if (room == null) return false;
        room.removeMember(username);
        return true;
    }
}
