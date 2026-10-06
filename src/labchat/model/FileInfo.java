package labchat.model;

import labchat.util.JsonUtil;

public final class FileInfo {
    private final String id;
    private final String originalName;
    private final String storedName;
    private final long sizeBytes;
    private final String contentType;
    private final String uploader;
    private final boolean isImage;

    public FileInfo(String id, String originalName, String storedName, long sizeBytes, String contentType, String uploader) {
        this.id = id;
        this.originalName = originalName;
        this.storedName = storedName;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.uploader = uploader;
        this.isImage = contentType != null && contentType.startsWith("image/");
    }

    public String getId() { return id; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public long getSizeBytes() { return sizeBytes; }
    public String getContentType() { return contentType; }
    public String getUploader() { return uploader; }
    public boolean isImage() { return isImage; }

    public String toJson() {
        return "{" +
                "\"id\":\"" + JsonUtil.escape(id) + "\"," +
                "\"originalName\":\"" + JsonUtil.escape(originalName) + "\"," +
                "\"sizeBytes\":" + sizeBytes + "," +
                "\"contentType\":\"" + JsonUtil.escape(contentType) + "\"," +
                "\"uploader\":\"" + JsonUtil.escape(uploader) + "\"," +
                "\"isImage\":" + isImage +
                "}";
    }
}
